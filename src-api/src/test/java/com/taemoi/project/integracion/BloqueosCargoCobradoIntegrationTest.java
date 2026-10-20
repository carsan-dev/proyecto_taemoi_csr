package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.CobroService;
import com.taemoi.project.services.GrupoService;
import com.taemoi.project.services.JwtService;

import jakarta.servlet.http.Cookie;

/**
 * Pieza 6c-1 contra MySQL: bloqueos sin excepciones de las decisiones 186 (quitar de una convocatoria a un alumno con
 * el examen pagado), 187 (precio o cantidad de un cargo con cobro activo) y 194 (cuantía de un examen pagado desde la
 * convocatoria). Escuela sintética de {@link EscuelaConSedesDePrueba} (no Moiskimdo), con propietario (ROLE_ADMIN) y
 * gestor (ROLE_MANAGER). Las entregas de caja se crean por SQL, como en {@code CobrosIntegrationTest}.
 *
 * <p>«Examen pagado» (186, 194): el cargo del examen, leído con la fila bloqueada, tiene {@code pagado = true}; o no hay
 * cargo y la inscripción tiene {@code pagado = true}. Cubre los pagos anteriores a la 6b-1 sin fila {@code cobro}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BloqueosCargoCobradoIntegrationTest extends MySqlIntegrationTest {

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired DataSource dataSource;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;

	@MockitoBean JavaMailSender mailSender;

	EscuelaConSedesDePrueba e;
	long propietario;
	String tokenGestor;
	String tokenPropietario;
	long producto;

	@BeforeAll
	void preparar() {
		e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-6c1-bloqueos");
		propietario = e.cuenta(usuarios, "propietario");
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, 'PROPIETARIO')",
				propietario, e.org);
		tokenGestor = e.token(jwtService, usuarios, e.gestor);
		tokenPropietario = e.token(jwtService, usuarios, propietario);
		jdbc.update("INSERT INTO producto (concepto, precio, codigo, organizacion_id) VALUES ('CAMISETA 6C1', 10.5, NULL, ?)",
				e.org);
		producto = jdbc.queryForObject("SELECT MAX(id) FROM producto WHERE organizacion_id = ?", Long.class, e.org);
		// Con fecha de grado, marcar el pago de un examen no la rellena (la disciplina de prueba no tiene grados).
		jdbc.update("UPDATE alumno_deporte SET fecha_grado = '2024-01-01' WHERE organizacion_id = ?", e.org);
	}

	@AfterEach
	void sinFugas() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
		SecurityContextHolder.clearContext();
	}

	// ================================================================== 186: quitar de una convocatoria

	@Test
	void quitarDeConvocatoriaConExamenPagado_409ConAviso_sinCambios() throws Exception {
		for (String token : List.of(tokenPropietario, tokenGestor)) {
			long inscripcion = inscripcionConCargo(e.aA);
			long cargo = cargoDe(inscripcion);
			assertEquals(200, estado(pagoConvocatoria(inscripcion, "{\"cuantiaExamen\":20.0,\"pagado\":true}"), token));
			long cobro = idCobro(cargo);

			MvcResult r = peticion(quitar(inscripcion, e.aA), token);
			assertEquals(409, r.getResponse().getStatus(), r.getResponse().getContentAsString());
			// Pieza 6c-6 (D6c-15): el texto completo de la 186.
			assertEquals("El examen está pagado: haz la devolución o revierte el cobro antes", mensaje(r));
			assertEquals(CobroService.EXAMEN_PAGADO_REVIERTE_ANTES, mensaje(r));

			assertTrue(pagado(cargo), "el cargo sigue y pagado");
			assertEquals(1L, cuenta("SELECT COUNT(*) FROM alumno_convocatoria WHERE id = ?", inscripcion));
			assertEquals((Long) cargo, cargoDe(inscripcion));
			Map<String, Object> fila = filaCobro(cobro);
			assertEquals("ACTIVO", fila.get("estado"));
			assertEquals(cargo, numero(fila, "producto_alumno_id"));
		}
	}

	@Test
	void quitarDeConvocatoriaConCobroEntregado_409_sinCambios() throws Exception {
		for (String token : List.of(tokenPropietario, tokenGestor)) {
			long inscripcion = inscripcionConCargo(e.aA);
			long cargo = cargoDe(inscripcion);
			assertEquals(200, estado(pagoConvocatoria(inscripcion, "{\"cuantiaExamen\":20.0,\"pagado\":true}"), token));
			long cobro = idCobro(cargo);
			meterEnEntrega(cobro);
			Map<String, Object> inscripcionAntes = filaInscripcion(inscripcion);
			Map<String, Object> cargoAntes = filaCargo(cargo);
			Map<String, Object> cobroAntes = filaCobro(cobro);

			MvcResult r = peticion(quitar(inscripcion, e.aA), token);
			assertEquals(409, r.getResponse().getStatus(), "antes daba 204 y dejaba el cobro ACTIVO sin cargo");
			assertEquals(CobroService.EXAMEN_PAGADO_REVIERTE_ANTES, mensaje(r));
			assertEquals(inscripcionAntes, filaInscripcion(inscripcion));
			assertEquals(cargoAntes, filaCargo(cargo));
			assertEquals(cobroAntes, filaCobro(cobro));
		}
	}

	@Test
	void quitarDeConvocatoriaPagadoSinCobroAntesDe6b1_409() throws Exception {
		long inscripcion = inscripcionConCargo(e.aA);
		long cargo = cargoDe(inscripcion);
		jdbc.update("UPDATE producto_alumno SET pagado = 1, fecha_pago = NOW(6) WHERE id = ?", cargo);
		jdbc.update("UPDATE alumno_convocatoria SET pagado = 1 WHERE id = ?", inscripcion);

		MvcResult r = peticion(quitar(inscripcion, e.aA), tokenGestor);
		assertEquals(409, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		assertEquals(CobroService.EXAMEN_PAGADO_REVIERTE_ANTES, mensaje(r));
		assertTrue(cobros(cargo).isEmpty(), "no se crea ningún cobro");
		assertTrue(pagado(cargo));
		assertEquals(1L, cuenta("SELECT COUNT(*) FROM alumno_convocatoria WHERE id = ?", inscripcion));
	}

	@Test
	void quitarDeConvocatoriaInscripcionPagadaSinCargo_409() throws Exception {
		long inscripcion = inscripcionSinCargo(e.aA, true);
		Map<String, Object> antes = filaInscripcion(inscripcion);

		MvcResult r = peticion(quitar(inscripcion, e.aA), tokenPropietario);
		assertEquals(409, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		assertEquals(CobroService.EXAMEN_PAGADO_REVIERTE_ANTES, mensaje(r));
		assertEquals(antes, filaInscripcion(inscripcion), "la inscripción sigue");
	}

	@Test
	void quitarDeConvocatoriaSinPagar_204_comoHoy() throws Exception {
		long inscripcion = inscripcionConCargo(e.aA);
		long cargo = cargoDe(inscripcion);
		assertEquals(204, estado(quitar(inscripcion, e.aA), tokenGestor));
		assertEquals(0L, cuenta("SELECT COUNT(*) FROM producto_alumno WHERE id = ?", cargo));
		assertEquals(0L, cuenta("SELECT COUNT(*) FROM alumno_convocatoria WHERE id = ?", inscripcion));
		assertTrue(cobros(cargo).isEmpty());

		// Y una inscripción sin cargo y sin pagar, también como hoy.
		long sinCargo = inscripcionSinCargo(e.aA, false);
		assertEquals(204, estado(quitar(sinCargo, e.aA), tokenGestor));
		assertEquals(0L, cuenta("SELECT COUNT(*) FROM alumno_convocatoria WHERE id = ?", sinCargo));
	}

	@Test
	void quitarTrasDesmarcarElPago_204() throws Exception {
		long inscripcion = inscripcionConCargo(e.aA);
		long cargo = cargoDe(inscripcion);
		assertEquals(200, estado(pagoConvocatoria(inscripcion, "{\"cuantiaExamen\":20.0,\"pagado\":true}"),
				tokenGestor));
		long cobro = idCobro(cargo);
		// Decisión 154: desmarcar desde la convocatoria sigue siendo directo, también para la gestión.
		assertEquals(200, estado(pagoConvocatoria(inscripcion, "{\"cuantiaExamen\":20.0,\"pagado\":false}"),
				tokenGestor));

		assertEquals(204, estado(quitar(inscripcion, e.aA), tokenGestor));
		assertEquals(0L, cuenta("SELECT COUNT(*) FROM alumno_convocatoria WHERE id = ?", inscripcion));
		assertEquals(0L, cuenta("SELECT COUNT(*) FROM producto_alumno WHERE id = ?", cargo));
		Map<String, Object> fila = filaCobro(cobro);
		assertEquals("ANULADO", fila.get("estado"));
		assertEquals("pago revertido desde la convocatoria", fila.get("motivo_anulacion"));
	}

	@Test
	void pagarYQuitarALaVez_nuncaCobroActivoSinCargo() throws Exception {
		Set<List<Integer>> validos = Set.of(List.of(200, 409), List.of(204, 400), List.of(204, 404));
		for (int vuelta = 0; vuelta < 5; vuelta++) {
			long inscripcion = inscripcionConCargo(e.aA);
			List<Integer> estados = enParalelo(
					() -> estado(pagoConvocatoria(inscripcion, "{\"cuantiaExamen\":20.0,\"pagado\":true}"),
							tokenGestor),
					() -> estado(quitar(inscripcion, e.aA), tokenGestor));
			assertTrue(validos.contains(estados), "vuelta " + vuelta + ": " + estados);
			assertEquals(0L, cuenta("""
					SELECT COUNT(*) FROM cobro WHERE organizacion_id = ? AND estado = 'ACTIVO'
					AND producto_alumno_id IS NULL""", e.org), "vuelta " + vuelta);
		}
	}

	/**
	 * El DELETE espera al bloqueo del cargo y, tras él, ve el pago que otro confirmó mientras esperaba (lee con READ
	 * COMMITTED: con REPEATABLE READ vería la foto de antes de esperar y borraría un examen pagado).
	 */
	@Test
	void quitarEsperaAlBloqueoDelCargo_yVeElPagoConfirmado_409() throws Exception {
		long inscripcion = inscripcionConCargo(e.aA);
		long cargo = cargoDe(inscripcion);
		ExecutorService hilo = Executors.newSingleThreadExecutor();
		try (Connection otra = dataSource.getConnection()) {
			otra.setAutoCommit(false);
			try (PreparedStatement bloqueo = otra.prepareStatement(
					"SELECT id FROM producto_alumno WHERE id = ? FOR UPDATE")) {
				bloqueo.setLong(1, cargo);
				bloqueo.executeQuery().close();
			}
			Future<Integer> quitar = hilo.submit(() -> estado(quitar(inscripcion, e.aA), tokenGestor));
			Thread.sleep(1500);
			assertFalse(quitar.isDone(), "el DELETE debe esperar al bloqueo de la fila del cargo");
			try (PreparedStatement pagar = otra.prepareStatement(
					"UPDATE producto_alumno SET pagado = 1, fecha_pago = NOW(6) WHERE id = ?");
					PreparedStatement registrar = otra.prepareStatement("""
							INSERT INTO cobro (organizacion_id, producto_alumno_id, sede_id, importe, metodo, fecha_cobro,
							  origen, estado) VALUES (?, ?, ?, 20.00, 'EFECTIVO', NOW(6), 'CONVOCATORIA', 'ACTIVO')""")) {
				pagar.setLong(1, cargo);
				pagar.executeUpdate();
				registrar.setLong(1, e.org);
				registrar.setLong(2, cargo);
				registrar.setLong(3, e.sA);
				registrar.executeUpdate();
			}
			otra.commit();
			assertEquals(409, quitar.get(60, TimeUnit.SECONDS));
		} finally {
			hilo.shutdownNow();
		}
		assertTrue(pagado(cargo));
		assertEquals("ACTIVO", cobros(cargo).get(0).get("estado"));
		assertEquals(1L, cuenta("SELECT COUNT(*) FROM alumno_convocatoria WHERE id = ?", inscripcion));
	}

	// ================================================================== 187: precio o cantidad con cobro activo

	@Test
	void cambiarPrecioConCobroActivo_409_sinCambios() throws Exception {
		for (String token : List.of(tokenPropietario, tokenGestor)) {
			long cargo = cargoCobrado(e.aA);
			MvcResult r = peticion(pago(cargo, "{\"precio\":12.0}"), token);
			assertEquals(409, r.getResponse().getStatus(), r.getResponse().getContentAsString());
			assertEquals(CobroService.PRECIO_CON_COBRO_ACTIVO, mensaje(r));
			assertEquals(
					"El cargo está cobrado: no se puede cambiar el precio ni la cantidad. Revierte el cobro antes.",
					mensaje(r));
			assertEquals(10.5, precio(cargo));
			Map<String, Object> cobro = cobros(cargo).get(0);
			assertEquals("ACTIVO", cobro.get("estado"));
			assertEquals(0, new BigDecimal("10.50").compareTo((BigDecimal) cobro.get("importe")));
		}
	}

	@Test
	void cambiarCantidadConCobroActivo_409_sinCambios() throws Exception {
		long cargo = cargoCobrado(e.aA);
		MvcResult r = peticion(pago(cargo, "{\"cantidad\":2}"), tokenGestor);
		assertEquals(409, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		assertEquals(CobroService.PRECIO_CON_COBRO_ACTIVO, mensaje(r));
		assertEquals(1, ((Number) filaCargo(cargo).get("cantidad")).intValue());
	}

	@Test
	void mismoPrecioYCantidadConCobroActivo_200() throws Exception {
		// La ficha envía el DTO entero: reenviar los mismos valores no es un cambio.
		long cargo = cargoCobrado(e.aA);
		assertEquals(200, estado(pago(cargo, "{\"precio\":10.5,\"cantidad\":1,\"pagado\":true}"), tokenGestor));
		assertEquals(1, cobros(cargo).size());
		assertEquals("ACTIVO", cobros(cargo).get(0).get("estado"));
	}

	@Test
	void conceptoFechaYNotasConCobroActivo_200() throws Exception {
		long cargo = cargoCobrado(e.aA);
		assertEquals(200, estado(pago(cargo, "{\"concepto\":\"CAMISETA M\",\"notas\":\"talla M\","
				+ "\"fechaAsignacion\":\"2026-10-01T10:00:00.000+00:00\"}"), tokenGestor));
		Map<String, Object> fila = filaCargo(cargo);
		assertEquals("CAMISETA M", fila.get("concepto"));
		assertEquals("talla M", fila.get("notas"));
		// El conector devuelve la hora local guardada (zona de la JVM, la misma con la que Hibernate la escribió).
		assertEquals(Instant.parse("2026-10-01T10:00:00Z"),
				((LocalDateTime) fila.get("fecha_asignacion")).atZone(ZoneId.systemDefault()).toInstant());
		assertEquals("ACTIVO", cobros(cargo).get(0).get("estado"));
	}

	@Test
	void revertirYCambiarPrecioALaVez_409_sinRevertir() throws Exception {
		long cargo = cargoCobrado(e.aA);
		MvcResult r = peticion(pago(cargo, "{\"pagado\":false,\"precio\":12.0}"), tokenPropietario);
		assertEquals(409, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		assertEquals(CobroService.PRECIO_CON_COBRO_ACTIVO, mensaje(r));
		assertTrue(pagado(cargo));
		assertEquals(10.5, precio(cargo));
		assertEquals("ACTIVO", cobros(cargo).get(0).get("estado"));
	}

	@Test
	void cambiarPrecioConCobroEntregado_409() throws Exception {
		long cargo = cargoCobrado(e.aA);
		meterEnEntrega(idCobro(cargo));
		MvcResult r = peticion(pago(cargo, "{\"precio\":12.0}"), tokenPropietario);
		assertEquals(409, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		assertEquals(CobroService.PRECIO_CON_COBRO_ACTIVO, mensaje(r));
		assertEquals(10.5, precio(cargo));
	}

	@Test
	void cambiarPrecioPagadoAntesDe6b1SinCobro_200() throws Exception {
		// §9, fila 5: sin cobro ACTIVO la 187 no bloquea (legado).
		long cargo = cargo(e.aA);
		jdbc.update("UPDATE producto_alumno SET pagado = 1, fecha_pago = NOW(6) WHERE id = ?", cargo);
		assertEquals(200, estado(pago(cargo, "{\"precio\":12.0}"), tokenGestor));
		assertEquals(12.0, precio(cargo));
		assertTrue(cobros(cargo).isEmpty());
	}

	@Test
	void cobrarConPrecioNuevoSinCobroPrevio_200() throws Exception {
		long cargo = cargo(e.aA);
		assertEquals(200, estado(pago(cargo, "{\"pagado\":true,\"precio\":12.0}"), tokenGestor));
		Map<String, Object> cobro = cobros(cargo).get(0);
		assertEquals("ACTIVO", cobro.get("estado"));
		assertEquals(0, new BigDecimal("12.00").compareTo((BigDecimal) cobro.get("importe")));
	}

	@Test
	void cambiarPrecioTrasRevertir_200() throws Exception {
		long cargo = cargoCobrado(e.aA);
		assertEquals(200, estado(pago(cargo, "{\"pagado\":false}"), tokenPropietario));
		assertEquals(200, estado(pago(cargo, "{\"precio\":12.0}"), tokenPropietario));
		assertEquals(12.0, precio(cargo));
	}

	// ================================================================== 194: cuantía del examen pagado

	@Test
	void cuantiaDistintaConExamenPagado_409_sinCambios() throws Exception {
		for (String token : List.of(tokenPropietario, tokenGestor)) {
			long inscripcion = inscripcionConCargo(e.aA);
			long cargo = cargoDe(inscripcion);
			assertEquals(200, estado(pagoConvocatoria(inscripcion, "{\"cuantiaExamen\":20.0,\"pagado\":true}"), token));
			Map<String, Object> inscripcionAntes = filaInscripcion(inscripcion);

			MvcResult r = peticion(pagoConvocatoria(inscripcion, "{\"cuantiaExamen\":25.0,\"pagado\":true}"), token);
			assertEquals(409, r.getResponse().getStatus(), r.getResponse().getContentAsString());
			assertEquals(CobroService.CUANTIA_EXAMEN_PAGADO, mensaje(r));
			assertEquals("El examen está pagado: no se puede cambiar la cuantía. Revierte el cobro antes.", mensaje(r));
			assertEquals(inscripcionAntes, filaInscripcion(inscripcion));
			assertEquals(20.0, ((Number) filaInscripcion(inscripcion).get("cuantia_examen")).doubleValue());
			assertEquals(20.0, precio(cargo));
			List<Map<String, Object>> filas = cobros(cargo);
			assertEquals(1, filas.size());
			assertEquals("ACTIVO", filas.get(0).get("estado"));
			assertEquals(0, new BigDecimal("20.00").compareTo((BigDecimal) filas.get(0).get("importe")));
		}
	}

	@Test
	void mismaCuantiaConExamenPagado_200() throws Exception {
		long inscripcion = inscripcionConCargo(e.aA);
		long cargo = cargoDe(inscripcion);
		assertEquals(200, estado(pagoConvocatoria(inscripcion, "{\"cuantiaExamen\":20.0,\"pagado\":true}"),
				tokenGestor));
		assertEquals(200, estado(pagoConvocatoria(inscripcion, "{\"cuantiaExamen\":20.0,\"pagado\":true}"),
				tokenGestor));
		assertEquals(1, cobros(cargo).size());
	}

	@Test
	void cuantiaDistintaSinPagar_200_comoHoy() throws Exception {
		long inscripcion = inscripcionConCargo(e.aA);
		long cargo = cargoDe(inscripcion);
		assertEquals(200, estado(pagoConvocatoria(inscripcion, "{\"cuantiaExamen\":25.0,\"pagado\":false}"),
				tokenGestor));
		assertEquals(25.0, precio(cargo));
		assertEquals(25.0, ((Number) filaInscripcion(inscripcion).get("cuantia_examen")).doubleValue());
		assertTrue(cobros(cargo).isEmpty());
	}

	@Test
	void pagarConCuantiaNueva_200() throws Exception {
		long inscripcion = inscripcionConCargo(e.aA);
		long cargo = cargoDe(inscripcion);
		assertEquals(200, estado(pagoConvocatoria(inscripcion, "{\"cuantiaExamen\":25.0,\"pagado\":true}"),
				tokenGestor));
		Map<String, Object> cobro = cobros(cargo).get(0);
		assertEquals("ACTIVO", cobro.get("estado"));
		assertEquals(0, new BigDecimal("25.00").compareTo((BigDecimal) cobro.get("importe")));
	}

	@Test
	void desmarcarConCuantiaDistinta_200_decision154() throws Exception {
		long inscripcion = inscripcionConCargo(e.aA);
		long cargo = cargoDe(inscripcion);
		assertEquals(200, estado(pagoConvocatoria(inscripcion, "{\"cuantiaExamen\":20.0,\"pagado\":true}"),
				tokenGestor));
		assertEquals(200, estado(pagoConvocatoria(inscripcion, "{\"cuantiaExamen\":25.0,\"pagado\":false}"),
				tokenGestor));
		Map<String, Object> cobro = cobros(cargo).get(0);
		assertEquals("ANULADO", cobro.get("estado"));
		assertEquals("pago revertido desde la convocatoria", cobro.get("motivo_anulacion"));
		assertFalse(pagado(cargo));
	}

	@Test
	void cuantiaDistintaPagadoSinCobroAntesDe6b1_409() throws Exception {
		long inscripcion = inscripcionConCargo(e.aA);
		long cargo = cargoDe(inscripcion);
		jdbc.update("UPDATE producto_alumno SET pagado = 1, fecha_pago = NOW(6) WHERE id = ?", cargo);
		jdbc.update("UPDATE alumno_convocatoria SET pagado = 1 WHERE id = ?", inscripcion);
		Map<String, Object> inscripcionAntes = filaInscripcion(inscripcion);
		Map<String, Object> cargoAntes = filaCargo(cargo);

		MvcResult r = peticion(pagoConvocatoria(inscripcion, "{\"cuantiaExamen\":25.0,\"pagado\":true}"),
				tokenGestor);
		assertEquals(409, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		assertEquals(CobroService.CUANTIA_EXAMEN_PAGADO, mensaje(r));
		assertEquals(inscripcionAntes, filaInscripcion(inscripcion));
		assertEquals(cargoAntes, filaCargo(cargo));
		assertTrue(cobros(cargo).isEmpty());
	}

	/**
	 * Legado de Moiskimdo: examen pagado cuya cuantía (la que se ve en la convocatoria) no coincide con el precio del
	 * cargo. Guardar sin tocar la cuantía vale y no mueve el precio del cargo pagado; se compara solo con la cuantía.
	 */
	@Test
	void legadoPagadoConCuantiaDistintaDelPrecio_mismaCuantia_200_precioIntacto() throws Exception {
		// Sin cobro (pagado antes de la 6b-1).
		long inscripcion = inscripcionConCargo(e.aA);
		long cargo = cargoDe(inscripcion);
		jdbc.update("UPDATE producto_alumno SET pagado = 1, fecha_pago = NOW(6), precio = 18.0 WHERE id = ?", cargo);
		jdbc.update("UPDATE alumno_convocatoria SET pagado = 1 WHERE id = ?", inscripcion);
		assertEquals(200, estado(pagoConvocatoria(inscripcion, "{\"cuantiaExamen\":20.0,\"pagado\":true}"),
				tokenGestor));
		assertEquals(18.0, precio(cargo), "el precio del cargo pagado no se mueve");
		assertTrue(pagado(cargo));
		assertEquals(20.0, ((Number) filaInscripcion(inscripcion).get("cuantia_examen")).doubleValue());
		assertTrue(cobros(cargo).isEmpty());

		// Con cobro ACTIVO.
		long otra = inscripcionConCargo(e.aB);
		long cargoOtra = cargoDe(otra);
		assertEquals(200, estado(pagoConvocatoria(otra, "{\"cuantiaExamen\":20.0,\"pagado\":true}"), tokenGestor));
		jdbc.update("UPDATE producto_alumno SET precio = 18.0 WHERE id = ?", cargoOtra);
		assertEquals(200, estado(pagoConvocatoria(otra, "{\"cuantiaExamen\":20.0,\"pagado\":true}"), tokenPropietario));
		assertEquals(18.0, precio(cargoOtra));
		List<Map<String, Object>> filas = cobros(cargoOtra);
		assertEquals(1, filas.size());
		assertEquals("ACTIVO", filas.get(0).get("estado"));
		assertEquals(0, new BigDecimal("20.00").compareTo((BigDecimal) filas.get(0).get("importe")));
	}

	@Test
	void legadoPagadoConCuantiaDistintaDelPrecio_otraCuantia_409_sinCambios() throws Exception {
		long inscripcion = inscripcionConCargo(e.aA);
		long cargo = cargoDe(inscripcion);
		jdbc.update("UPDATE producto_alumno SET pagado = 1, fecha_pago = NOW(6), precio = 18.0 WHERE id = ?", cargo);
		jdbc.update("UPDATE alumno_convocatoria SET pagado = 1 WHERE id = ?", inscripcion);
		Map<String, Object> inscripcionAntes = filaInscripcion(inscripcion);
		Map<String, Object> cargoAntes = filaCargo(cargo);

		// Otra cuantía, aunque coincida con el precio del cargo: es un cambio de lo que se ve.
		for (String cuerpo : List.of("{\"cuantiaExamen\":18.0,\"pagado\":true}",
				"{\"cuantiaExamen\":25.0,\"pagado\":true}")) {
			MvcResult r = peticion(pagoConvocatoria(inscripcion, cuerpo), tokenGestor);
			assertEquals(409, r.getResponse().getStatus(), cuerpo);
			assertEquals(CobroService.CUANTIA_EXAMEN_PAGADO, mensaje(r));
			assertEquals(inscripcionAntes, filaInscripcion(inscripcion));
			assertEquals(cargoAntes, filaCargo(cargo));
		}
		assertTrue(cobros(cargo).isEmpty());
	}

	/**
	 * Dos PUT con la misma cuantía nueva a la vez: el segundo lee la inscripción al empezar (cuantía 20), espera al
	 * bloqueo del cargo y, mientras, el primero paga con 25. Tras el bloqueo la cuantía se relee (25): no es un cambio,
	 * 200 y no un 409 de más.
	 */
	@Test
	void cuantiaSeReleeTrasElBloqueoDelCargo_sin409DeMas() throws Exception {
		long inscripcion = inscripcionConCargo(e.aA);
		long cargo = cargoDe(inscripcion);
		ExecutorService hilo = Executors.newSingleThreadExecutor();
		try (Connection otra = dataSource.getConnection()) {
			otra.setAutoCommit(false);
			try (PreparedStatement bloqueo = otra.prepareStatement(
					"SELECT id FROM producto_alumno WHERE id = ? FOR UPDATE")) {
				bloqueo.setLong(1, cargo);
				bloqueo.executeQuery().close();
			}
			Future<Integer> put = hilo.submit(() -> estado(
					pagoConvocatoria(inscripcion, "{\"cuantiaExamen\":25.0,\"pagado\":true}"), tokenGestor));
			Thread.sleep(1500);
			assertFalse(put.isDone(), "el PUT debe esperar al bloqueo de la fila del cargo");
			try (PreparedStatement pagar = otra.prepareStatement(
					"UPDATE producto_alumno SET pagado = 1, fecha_pago = NOW(6), precio = 25.0 WHERE id = ?");
					PreparedStatement inscribir = otra.prepareStatement(
							"UPDATE alumno_convocatoria SET pagado = 1, cuantia_examen = 25.0 WHERE id = ?");
					PreparedStatement registrar = otra.prepareStatement("""
							INSERT INTO cobro (organizacion_id, producto_alumno_id, sede_id, importe, metodo, fecha_cobro,
							  origen, estado) VALUES (?, ?, ?, 25.00, 'EFECTIVO', NOW(6), 'CONVOCATORIA', 'ACTIVO')""")) {
				pagar.setLong(1, cargo);
				pagar.executeUpdate();
				inscribir.setLong(1, inscripcion);
				inscribir.executeUpdate();
				registrar.setLong(1, e.org);
				registrar.setLong(2, cargo);
				registrar.setLong(3, e.sA);
				registrar.executeUpdate();
			}
			otra.commit();
			assertEquals(200, put.get(60, TimeUnit.SECONDS));
		} finally {
			hilo.shutdownNow();
		}
		assertEquals(25.0, precio(cargo));
		assertEquals(1, cobros(cargo).size(), "ya estaba pagado: no hay otro cobro");
	}

	// ================================================================== responsable de sede (su sede)

	@Test
	void responsableDeSede_quitarConExamenPagado_409_sinCambios() throws Exception {
		String tokenResponsable = e.token(jwtService, usuarios, e.respA);
		long inscripcion = inscripcionConCargo(e.aA);
		long cargo = cargoDe(inscripcion);
		assertEquals(200, estado(pagoConvocatoria(inscripcion, "{\"cuantiaExamen\":20.0,\"pagado\":true}"),
				tokenGestor));
		long cobro = idCobro(cargo);
		Map<String, Object> inscripcionAntes = filaInscripcion(inscripcion);
		Map<String, Object> cargoAntes = filaCargo(cargo);
		Map<String, Object> cobroAntes = filaCobro(cobro);

		MvcResult r = peticion(quitar(inscripcion, e.aA), tokenResponsable);
		assertEquals(409, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		assertEquals(CobroService.EXAMEN_PAGADO_REVIERTE_ANTES, mensaje(r));
		assertEquals(inscripcionAntes, filaInscripcion(inscripcion));
		assertEquals(cargoAntes, filaCargo(cargo));
		assertEquals(cobroAntes, filaCobro(cobro));
	}

	@Test
	void responsableDeSede_cuantiaDistintaConExamenPagado_409_sinCambios() throws Exception {
		String tokenResponsable = e.token(jwtService, usuarios, e.respA);
		long inscripcion = inscripcionConCargo(e.aA);
		long cargo = cargoDe(inscripcion);
		assertEquals(200, estado(pagoConvocatoria(inscripcion, "{\"cuantiaExamen\":20.0,\"pagado\":true}"),
				tokenGestor));
		long cobro = idCobro(cargo);
		Map<String, Object> inscripcionAntes = filaInscripcion(inscripcion);
		Map<String, Object> cargoAntes = filaCargo(cargo);
		Map<String, Object> cobroAntes = filaCobro(cobro);

		MvcResult r = peticion(pagoConvocatoria(inscripcion, "{\"cuantiaExamen\":25.0,\"pagado\":true}"),
				tokenResponsable);
		assertEquals(409, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		assertEquals(CobroService.CUANTIA_EXAMEN_PAGADO, mensaje(r));
		assertEquals(inscripcionAntes, filaInscripcion(inscripcion));
		assertEquals(cargoAntes, filaCargo(cargo));
		assertEquals(cobroAntes, filaCobro(cobro));
	}

	@Test
	void cuantiaDistintaInscripcionPagadaSinCargo_409() throws Exception {
		long inscripcion = inscripcionSinCargo(e.aA, true);
		Map<String, Object> antes = filaInscripcion(inscripcion);

		MvcResult r = peticion(pagoConvocatoria(inscripcion, "{\"cuantiaExamen\":25.0,\"pagado\":true}"),
				tokenGestor);
		assertEquals(409, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		assertEquals(CobroService.CUANTIA_EXAMEN_PAGADO, mensaje(r));
		assertEquals(antes, filaInscripcion(inscripcion), "la inscripción no cambia");
	}

	// ------------------------------------------------------------------ utilidades (de CobrosIntegrationTest)

	private long cargo(long alumno) {
		try {
			MvcResult r = peticion(post("/api/productos-alumno/alumno/" + alumno + "/producto/" + producto)
					.contentType(MediaType.APPLICATION_JSON).content("{\"cantidad\":1,\"pagado\":false}"), tokenGestor);
			assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
			return json.readTree(r.getResponse().getContentAsString()).get("id").asLong();
		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	/** Cargo del producto de la preparación (10.5) cobrado por el gestor: un cobro ACTIVO. */
	private long cargoCobrado(long alumno) throws Exception {
		long cargo = cargo(alumno);
		assertEquals(200, estado(pago(cargo, "{\"pagado\":true}"), tokenGestor));
		assertEquals("ACTIVO", cobros(cargo).get(0).get("estado"));
		return cargo;
	}

	/** Inscripción a una convocatoria nueva con su cargo de derecho a examen pendiente (como al apuntarlo). */
	private long inscripcionConCargo(long alumno) {
		long convocatoria = convocatoriaNueva();
		jdbc.update("""
				INSERT INTO producto_alumno (alumno_id, concepto, precio, cantidad, pagado, fecha_asignacion,
				  organizacion_id, sede_id)
				VALUES (?, 'DERECHO A EXAMEN 6C1', 20.0, 1, 0, NOW(), ?,
				  (SELECT sede_principal_id FROM alumno WHERE id = ?))""", alumno, e.org, alumno);
		long cargo = jdbc.queryForObject("SELECT MAX(id) FROM producto_alumno WHERE organizacion_id = ?", Long.class,
				e.org);
		jdbc.update("""
				INSERT INTO alumno_convocatoria (cuantia_examen, pagado, alumno_id, convocatoria_id, grado_actual,
				  grado_siguiente, organizacion_id, producto_alumno_id) VALUES (20, 0, ?, ?, NULL, NULL, ?, ?)""",
				alumno, convocatoria, e.org, cargo);
		return inscripcionDe(alumno, convocatoria);
	}

	/** Inscripción antigua sin cargo ({@code producto_alumno_id} NULL), pagada o no. */
	private long inscripcionSinCargo(long alumno, boolean pagado) {
		long convocatoria = convocatoriaNueva();
		jdbc.update("""
				INSERT INTO alumno_convocatoria (cuantia_examen, pagado, alumno_id, convocatoria_id, grado_actual,
				  grado_siguiente, organizacion_id, producto_alumno_id) VALUES (20, ?, ?, ?, NULL, NULL, ?, NULL)""",
				pagado, alumno, convocatoria, e.org);
		return inscripcionDe(alumno, convocatoria);
	}

	private long convocatoriaNueva() {
		jdbc.update("INSERT INTO convocatoria (fecha_convocatoria, organizacion_id, disciplina_id) VALUES "
				+ "('2026-06-01', ?, ?)", e.org, e.disciplina);
		return jdbc.queryForObject("SELECT MAX(id) FROM convocatoria WHERE organizacion_id = ?", Long.class, e.org);
	}

	private long inscripcionDe(long alumno, long convocatoria) {
		return jdbc.queryForObject("SELECT id FROM alumno_convocatoria WHERE alumno_id = ? AND convocatoria_id = ?",
				Long.class, alumno, convocatoria);
	}

	private long convocatoriaDe(long inscripcion) {
		return jdbc.queryForObject("SELECT convocatoria_id FROM alumno_convocatoria WHERE id = ?", Long.class,
				inscripcion);
	}

	private Long cargoDe(long inscripcion) {
		return jdbc.queryForObject("SELECT producto_alumno_id FROM alumno_convocatoria WHERE id = ?", Long.class,
				inscripcion);
	}

	private long idCobro(long cargo) {
		List<Map<String, Object>> filas = cobros(cargo);
		assertEquals(1, filas.size());
		return numero(filas.get(0), "id");
	}

	/** Entrega de caja sintética con el cobro dentro. */
	private void meterEnEntrega(long cobro) {
		jdbc.update("""
				INSERT INTO entrega_caja (organizacion_id, sede_id, entregado_por_usuario_id, recibido_por_usuario_id,
				  importe_declarado, importe_contado, fecha) VALUES (?, ?, ?, ?, 10.50, 10.50, NOW(6))""",
				e.org, e.sA, e.gestor, propietario);
		long entrega = jdbc.queryForObject("SELECT MAX(id) FROM entrega_caja WHERE organizacion_id = ?", Long.class,
				e.org);
		jdbc.update("UPDATE cobro SET entrega_caja_id = ? WHERE id = ?", entrega, cobro);
	}

	private List<Map<String, Object>> cobros(long cargo) {
		return jdbc.queryForList("SELECT * FROM cobro WHERE producto_alumno_id = ? ORDER BY id", cargo);
	}

	private Map<String, Object> filaCobro(long cobro) {
		return jdbc.queryForMap("SELECT * FROM cobro WHERE id = ?", cobro);
	}

	private Map<String, Object> filaCargo(long cargo) {
		return jdbc.queryForMap("SELECT * FROM producto_alumno WHERE id = ?", cargo);
	}

	private Map<String, Object> filaInscripcion(long inscripcion) {
		return jdbc.queryForMap("SELECT * FROM alumno_convocatoria WHERE id = ?", inscripcion);
	}

	private boolean pagado(long cargo) {
		return Boolean.TRUE.equals(
				jdbc.queryForObject("SELECT pagado FROM producto_alumno WHERE id = ?", Boolean.class, cargo));
	}

	private double precio(long cargo) {
		return jdbc.queryForObject("SELECT precio FROM producto_alumno WHERE id = ?", Double.class, cargo);
	}

	private long cuenta(String sql, Object... argumentos) {
		return jdbc.queryForObject(sql, Long.class, argumentos);
	}

	private String mensaje(MvcResult r) throws Exception {
		return json.readTree(r.getResponse().getContentAsString()).path("mensaje").asText(null);
	}

	private static long numero(Map<String, Object> fila, String columna) {
		return ((Number) fila.get(columna)).longValue();
	}

	private MockHttpServletRequestBuilder quitar(long inscripcion, long alumno) {
		return delete("/api/alumnos/" + convocatoriaDe(inscripcion) + "/alumno/" + alumno);
	}

	private static MockHttpServletRequestBuilder pago(long cargo, String cuerpo) {
		return put("/api/productos-alumno/" + cargo).contentType(MediaType.APPLICATION_JSON).content(cuerpo);
	}

	private static MockHttpServletRequestBuilder pagoConvocatoria(long inscripcion, String cuerpo) {
		return put("/api/convocatorias/alumno/" + inscripcion).contentType(MediaType.APPLICATION_JSON).content(cuerpo);
	}

	private int estado(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		return peticion(peticion, token).getResponse().getStatus();
	}

	private MvcResult peticion(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		return mvc.perform(peticion.cookie(new Cookie("jwt", token))).andReturn();
	}

	private static List<Integer> enParalelo(Callable<Integer> primera, Callable<Integer> segunda) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch preparados = new CountDownLatch(2);
		CountDownLatch salida = new CountDownLatch(1);
		try {
			List<Future<Integer>> futuros = new ArrayList<>();
			for (Callable<Integer> accion : List.of(primera, segunda)) {
				futuros.add(executor.submit(() -> {
					preparados.countDown();
					if (!salida.await(10, TimeUnit.SECONDS)) {
						throw new IllegalStateException("No arrancaron ambas peticiones.");
					}
					return accion.call();
				}));
			}
			assertTrue(preparados.await(10, TimeUnit.SECONDS));
			salida.countDown();
			List<Integer> estados = new ArrayList<>();
			for (Future<Integer> futuro : futuros) {
				estados.add(futuro.get(60, TimeUnit.SECONDS));
			}
			estados.sort(Integer::compareTo);
			return estados;
		} finally {
			salida.countDown();
			executor.shutdownNow();
		}
	}
}
