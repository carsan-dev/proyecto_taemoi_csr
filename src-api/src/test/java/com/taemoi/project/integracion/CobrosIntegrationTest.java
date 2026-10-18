package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.dtos.ProductoAlumnoDTO;
import com.taemoi.project.entities.MetodoPago;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.CobroRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.GrupoService;
import com.taemoi.project.services.JwtService;
import com.taemoi.project.services.ProductoAlumnoService;

import jakarta.servlet.http.Cookie;

/**
 * Pieza 6b-1 contra MySQL: registro de cobros por las dos puertas (PUT del cargo y PUT de la inscripción a una
 * convocatoria), cargos que nacen pagados, revertir, borrar, concurrencia y aislamiento por escuela. Escuela sintética
 * de {@link EscuelaConSedesDePrueba} (no Moiskimdo): el gestor (ADMINISTRADOR → ROLE_MANAGER) cobra y un propietario
 * (ROLE_ADMIN) revierte. Las entregas de caja se crean por SQL (en la 6b-1 nadie las crea todavía).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CobrosIntegrationTest extends MySqlIntegrationTest {

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired DataSource dataSource;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;
	@Autowired CobroRepository cobroRepository;
	@Autowired ProductoAlumnoService productoAlumnoService;

	@MockitoBean JavaMailSender mailSender;

	EscuelaConSedesDePrueba e;
	long propietario;
	String tokenGestor;
	String tokenPropietario;
	long producto;

	@BeforeAll
	void preparar() {
		e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-6b1-cobros");
		propietario = e.cuenta(usuarios, "propietario");
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, 'PROPIETARIO')",
				propietario, e.org);
		tokenGestor = e.token(jwtService, usuarios, e.gestor);
		tokenPropietario = e.token(jwtService, usuarios, propietario);
		jdbc.update("INSERT INTO producto (concepto, precio, codigo, organizacion_id) VALUES ('CAMISETA 6B1', 10.5, NULL, ?)",
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

	// ------------------------------------------------------------------ PUT del cargo

	@Test
	void cobrar_creaUnCobroConUsuarioMetodoSedeImporteYFecha() throws Exception {
		long cargo = cargo(e.aA);
		MvcResult r = peticion(pago(cargo, "{\"pagado\":true,\"metodoPago\":\"BIZUM\"}"), tokenGestor);
		assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		JsonNode respuesta = json.readTree(r.getResponse().getContentAsString());
		assertFalse(respuesta.has("metodoPago"), "la respuesta no cambia: sin campos nuevos");
		assertTrue(respuesta.get("pagado").asBoolean());

		List<Map<String, Object>> filas = cobros(cargo);
		assertEquals(1, filas.size());
		Map<String, Object> cobro = filas.get(0);
		assertEquals("ACTIVO", cobro.get("estado"));
		assertEquals("BIZUM", cobro.get("metodo"));
		assertEquals("TESORERIA", cobro.get("origen"));
		assertEquals(e.gestor, numero(cobro, "cobrado_por_usuario_id"));
		assertEquals(e.sA, numero(cobro, "sede_id"));
		assertEquals(e.aA, numero(cobro, "alumno_id"));
		assertEquals(e.org, numero(cobro, "organizacion_id"));
		assertEquals("CAMISETA 6B1", cobro.get("concepto"));
		assertEquals(0, new BigDecimal("10.50").compareTo((BigDecimal) cobro.get("importe")));
		assertNull(cobro.get("entrega_caja_id"));
		assertNotNull(cobro.get("creado_en"));
		assertEquals(1L, jdbc.queryForObject("""
				SELECT COUNT(*) FROM cobro c JOIN producto_alumno pa ON pa.id = c.producto_alumno_id
				WHERE c.id = ? AND c.fecha_cobro = pa.fecha_pago""", Long.class, numero(cobro, "id")),
				"fecha_cobro es la fecha_pago del cargo");
	}

	@Test
	void sinMetodo_oMetodoNulo_SIN_INDICAR() throws Exception {
		long sinMetodo = cargo(e.aA);
		long metodoNulo = cargo(e.aA);
		assertEquals(200, estado(pago(sinMetodo, "{\"pagado\":true}"), tokenGestor));
		assertEquals(200, estado(pago(metodoNulo, "{\"pagado\":true,\"metodoPago\":null}"), tokenGestor));
		assertEquals("SIN_INDICAR", cobros(sinMetodo).get(0).get("metodo"));
		assertEquals("SIN_INDICAR", cobros(metodoNulo).get(0).get("metodo"));
	}

	@Test
	void metodoDesconocido_400_sinCambios() throws Exception {
		long cargo = cargo(e.aA);
		assertEquals(400, estado(pago(cargo, "{\"pagado\":true,\"metodoPago\":\"CHEQUE\"}"), tokenGestor));
		assertFalse(pagado(cargo));
		assertTrue(cobros(cargo).isEmpty());
		// También en la puerta de la convocatoria.
		long inscripcion = inscripcionConCargo(e.aA);
		assertEquals(400, estado(pagoConvocatoria(inscripcion, "{\"cuantiaExamen\":25.0,\"pagado\":true,"
				+ "\"metodoPago\":\"cheque\"}"), tokenGestor));
		assertTrue(cobros(cargoDe(inscripcion)).isEmpty());
	}

	@Test
	void volverAMarcarPagado_noCreaOtro() throws Exception {
		long cargo = cargo(e.aA);
		assertEquals(200, estado(pago(cargo, "{\"pagado\":true,\"metodoPago\":\"EFECTIVO\"}"), tokenGestor));
		assertEquals(200, estado(pago(cargo, "{\"pagado\":true,\"metodoPago\":\"TARJETA\"}"), tokenGestor));
		assertEquals(200, estado(pago(cargo, "{\"notas\":\"solo notas\"}"), tokenGestor));
		List<Map<String, Object>> filas = cobros(cargo);
		assertEquals(1, filas.size());
		assertEquals("EFECTIVO", filas.get(0).get("metodo"), "el cobro no cambia al volver a marcarlo");
	}

	@Test
	void cargoPagadoAntesDeLa6b1_sinCobro_noSeRellenaAlTocarlo() throws Exception {
		// Pagado sin fila de cobro (como los anteriores a V31_5): volver a marcarlo no crea cobro; revertirlo, tampoco.
		long cargo = cargo(e.aA);
		jdbc.update("UPDATE producto_alumno SET pagado = 1, fecha_pago = NOW(6) WHERE id = ?", cargo);
		assertEquals(200, estado(pago(cargo, "{\"pagado\":true}"), tokenGestor));
		assertTrue(cobros(cargo).isEmpty());
		assertEquals(200, estado(pago(cargo, "{\"pagado\":false}"), tokenPropietario));
		assertTrue(cobros(cargo).isEmpty());
		assertFalse(pagado(cargo));
	}

	@Test
	void revertir_anulaElCobro_yVolverACobrar_creaOtroActivo() throws Exception {
		long cargo = cargo(e.aA);
		assertEquals(200, estado(pago(cargo, "{\"pagado\":true}"), tokenGestor));
		// La regla de siempre: el gestor no revierte (403) y no cambia nada.
		assertEquals(403, estado(pago(cargo, "{\"pagado\":false}"), tokenGestor));
		assertEquals("ACTIVO", cobros(cargo).get(0).get("estado"));

		assertEquals(200, estado(pago(cargo, "{\"pagado\":false,\"motivoCambio\":\"cobrado por error\"}"),
				tokenPropietario));
		Map<String, Object> anulado = cobros(cargo).get(0);
		assertEquals("ANULADO", anulado.get("estado"));
		assertEquals(propietario, numero(anulado, "anulado_por_usuario_id"));
		assertEquals("cobrado por error", anulado.get("motivo_anulacion"));
		assertNotNull(anulado.get("anulado_en"));
		assertFalse(pagado(cargo));

		assertEquals(200, estado(pago(cargo, "{\"pagado\":true,\"metodoPago\":\"TRANSFERENCIA\"}"), tokenGestor));
		List<Map<String, Object>> filas = cobros(cargo);
		assertEquals(List.of("ANULADO", "ACTIVO"), filas.stream().map(f -> f.get("estado")).toList());
		assertEquals("TRANSFERENCIA", filas.get(1).get("metodo"));
	}

	@Test
	void revertirOBorrarConEntrega_409SinCambios() throws Exception {
		long cargo = cargo(e.aA);
		assertEquals(200, estado(pago(cargo, "{\"pagado\":true}"), tokenGestor));
		Map<String, Object> cargoAntes = filaCargo(cargo);
		meterEnEntrega(numero(cobros(cargo).get(0), "id"));
		List<Map<String, Object>> cobrosAntes = cobros(cargo);

		MvcResult r = peticion(pago(cargo, "{\"pagado\":false}"), tokenPropietario);
		assertEquals(409, r.getResponse().getStatus());
		assertTrue(r.getResponse().getContentAsString().contains("entrega de caja"),
				r.getResponse().getContentAsString());
		assertEquals(cargoAntes, filaCargo(cargo));
		assertEquals(cobrosAntes, cobros(cargo));

		assertEquals(409, estado(delete("/api/productos-alumno/" + cargo), tokenPropietario));
		assertEquals(cargoAntes, filaCargo(cargo));
		assertEquals(cobrosAntes, cobros(cargo));
	}

	// ------------------------------------------------------------------ convocatoria (segunda puerta)

	@Test
	void convocatoria_creaUnCobro_yRevertirLoAnula() throws Exception {
		long inscripcion = inscripcionConCargo(e.aA);
		long cargo = cargoDe(inscripcion);
		assertEquals(200, estado(pagoConvocatoria(inscripcion,
				"{\"cuantiaExamen\":25.0,\"pagado\":true,\"metodoPago\":\"EFECTIVO\"}"), tokenGestor));
		List<Map<String, Object>> filas = cobros(cargo);
		assertEquals(1, filas.size());
		Map<String, Object> cobro = filas.get(0);
		assertEquals("CONVOCATORIA", cobro.get("origen"));
		assertEquals("EFECTIVO", cobro.get("metodo"));
		assertEquals(e.gestor, numero(cobro, "cobrado_por_usuario_id"));
		assertEquals(0, new BigDecimal("25.00").compareTo((BigDecimal) cobro.get("importe")),
				"el importe con la cuantía nueva del examen");
		assertEquals(1L, jdbc.queryForObject("""
				SELECT COUNT(*) FROM cobro c JOIN producto_alumno pa ON pa.id = c.producto_alumno_id
				WHERE c.id = ? AND c.fecha_cobro = pa.fecha_pago""", Long.class, numero(cobro, "id")));

		assertEquals(200, estado(pagoConvocatoria(inscripcion, "{\"cuantiaExamen\":25.0,\"pagado\":true}"),
				tokenGestor));
		assertEquals(1, cobros(cargo).size(), "volver a marcarlo no crea otro");

		assertEquals(200, estado(pagoConvocatoria(inscripcion, "{\"cuantiaExamen\":25.0,\"pagado\":false}"),
				tokenGestor));
		assertEquals("ANULADO", cobros(cargo).get(0).get("estado"));
		assertFalse(pagado(cargo));
	}

	@Test
	void lasDosPuertasSeguidas_unSoloCobro() throws Exception {
		// Primero el cargo y después la convocatoria.
		long inscripcion = inscripcionConCargo(e.aA);
		long cargo = cargoDe(inscripcion);
		assertEquals(200, estado(pago(cargo, "{\"pagado\":true,\"metodoPago\":\"TARJETA\"}"), tokenGestor));
		assertTrue(jdbc.queryForObject("SELECT pagado FROM alumno_convocatoria WHERE id = ?", Boolean.class,
				inscripcion));
		assertEquals(200, estado(pagoConvocatoria(inscripcion, "{\"cuantiaExamen\":20.0,\"pagado\":true}"),
				tokenGestor));
		List<Map<String, Object>> filas = cobros(cargo);
		assertEquals(1, filas.size());
		assertEquals("TESORERIA", filas.get(0).get("origen"));

		// Primero la convocatoria y después el cargo.
		long otra = inscripcionConCargo(e.aB);
		long cargoOtra = cargoDe(otra);
		assertEquals(200, estado(pagoConvocatoria(otra, "{\"cuantiaExamen\":20.0,\"pagado\":true}"), tokenGestor));
		assertEquals(200, estado(pago(cargoOtra, "{\"pagado\":true}"), tokenGestor));
		filas = cobros(cargoOtra);
		assertEquals(1, filas.size());
		assertEquals("CONVOCATORIA", filas.get(0).get("origen"));
		assertEquals(e.sB, numero(filas.get(0), "sede_id"), "la sede del cargo");
	}

	@Test
	void convocatoriaRevertirConEntrega_409SinCambios() throws Exception {
		long inscripcion = inscripcionConCargo(e.aA);
		long cargo = cargoDe(inscripcion);
		assertEquals(200, estado(pagoConvocatoria(inscripcion, "{\"cuantiaExamen\":20.0,\"pagado\":true}"),
				tokenGestor));
		meterEnEntrega(numero(cobros(cargo).get(0), "id"));
		Map<String, Object> inscripcionAntes = jdbc.queryForMap("SELECT * FROM alumno_convocatoria WHERE id = ?",
				inscripcion);
		Map<String, Object> cargoAntes = filaCargo(cargo);
		List<Map<String, Object>> cobrosAntes = cobros(cargo);

		assertEquals(409, estado(pagoConvocatoria(inscripcion, "{\"cuantiaExamen\":20.0,\"pagado\":false}"),
				tokenGestor));
		assertEquals(inscripcionAntes, jdbc.queryForMap("SELECT * FROM alumno_convocatoria WHERE id = ?",
				inscripcion));
		assertEquals(cargoAntes, filaCargo(cargo));
		assertEquals(cobrosAntes, cobros(cargo));
	}

	// ------------------------------------------------------------------ cargos que nacen pagados y borrado

	@Test
	void cargoQueNacePagado_tieneSuCobro() throws Exception {
		MvcResult r = peticion(post("/api/productos-alumno/alumno/" + e.aA + "/producto/" + producto)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"cantidad\":1,\"pagado\":true,\"metodoPago\":\"TARJETA\"}"), tokenGestor);
		assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		JsonNode respuesta = json.readTree(r.getResponse().getContentAsString());
		assertFalse(respuesta.has("metodoPago"));
		long cargo = respuesta.get("id").asLong();
		List<Map<String, Object>> filas = cobros(cargo);
		assertEquals(1, filas.size());
		assertEquals("TARJETA", filas.get(0).get("metodo"));
		assertEquals(e.sA, numero(filas.get(0), "sede_id"));
		assertEquals(1L, jdbc.queryForObject("""
				SELECT COUNT(*) FROM cobro c JOIN producto_alumno pa ON pa.id = c.producto_alumno_id
				WHERE c.producto_alumno_id = ? AND c.fecha_cobro = pa.fecha_pago""", Long.class, cargo));
		// Y uno que nace pendiente, sin cobro.
		assertTrue(cobros(cargo(e.aA)).isEmpty());
	}

	@Test
	void borrarCargoCobrado_anulaSuCobroYLoDesvincula() throws Exception {
		long cargo = cargo(e.aA);
		assertEquals(200, estado(pago(cargo, "{\"pagado\":true}"), tokenGestor));
		long cobro = numero(cobros(cargo).get(0), "id");
		assertEquals(204, estado(delete("/api/productos-alumno/" + cargo), tokenGestor));
		assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM producto_alumno WHERE id = ?", Long.class, cargo));
		Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM cobro WHERE id = ?", cobro);
		assertEquals("ANULADO", fila.get("estado"));
		assertEquals("cargo borrado", fila.get("motivo_anulacion"));
		assertEquals(e.gestor, numero(fila, "anulado_por_usuario_id"));
		assertNull(fila.get("producto_alumno_id"));

		// Un cargo pendiente se borra como siempre.
		long pendiente = cargo(e.aA);
		assertEquals(204, estado(delete("/api/productos-alumno/" + pendiente), tokenGestor));
		assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM producto_alumno WHERE id = ?", Long.class,
				pendiente));
	}

	// ------------------------------------------------------------------ otros caminos que borran cargos (ronda 2, D1)

	@Test
	void borrarAlumno_anulaLosCobrosDeSusCargos_yLosEntregadosSiguenActivosSinBloquear() throws Exception {
		long alumno = e.alumno("E6B1BORRADO" + e.marca, e.sA, true);
		long cargo = cargo(alumno);
		long cargoEntregado = cargo(alumno);
		long inscripcion = inscripcionConCargo(alumno);
		long cargoExamen = cargoDe(inscripcion);
		for (long c : List.of(cargo, cargoEntregado, cargoExamen)) {
			assertEquals(200, estado(pago(c, "{\"pagado\":true}"), tokenGestor));
		}
		long cobro = idCobro(cargo);
		long cobroEntregado = idCobro(cargoEntregado);
		long cobroExamen = idCobro(cargoExamen);
		meterEnEntrega(cobroEntregado);

		assertEquals(200, estado(delete("/api/alumnos/" + alumno), tokenGestor));
		assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM alumno WHERE id = ?", Long.class, alumno));
		assertAnuladoPorBorrado(cobro);
		assertAnuladoPorBorrado(cobroExamen);
		Map<String, Object> entregado = jdbc.queryForMap("SELECT * FROM cobro WHERE id = ?", cobroEntregado);
		assertEquals("ACTIVO", entregado.get("estado"), "el dinero ya está en una entrega: no se anula");
		assertNull(entregado.get("producto_alumno_id"));
		assertNull(entregado.get("alumno_id"));
		assertNotNull(entregado.get("entrega_caja_id"));
	}

	@Test
	void sacarAlumnoDeConvocatoria_anulaElCobroDelExamen() throws Exception {
		long inscripcion = inscripcionConCargo(e.aA);
		long cargo = cargoDe(inscripcion);
		assertEquals(200, estado(pagoConvocatoria(inscripcion, "{\"cuantiaExamen\":20.0,\"pagado\":true}"),
				tokenGestor));
		long cobro = idCobro(cargo);
		assertEquals(204, estado(delete("/api/alumnos/" + convocatoriaDe(inscripcion) + "/alumno/" + e.aA),
				tokenGestor));
		assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM producto_alumno WHERE id = ?", Long.class, cargo));
		assertAnuladoPorBorrado(cobro);

		// Con el cobro ya entregado: el borrado no falla y el cobro sigue activo, sin cargo.
		long otra = inscripcionConCargo(e.aA);
		long cargoOtra = cargoDe(otra);
		assertEquals(200, estado(pagoConvocatoria(otra, "{\"cuantiaExamen\":20.0,\"pagado\":true}"), tokenGestor));
		long cobroEntregado = idCobro(cargoOtra);
		meterEnEntrega(cobroEntregado);
		assertEquals(204, estado(delete("/api/alumnos/" + convocatoriaDe(otra) + "/alumno/" + e.aA), tokenGestor));
		assertActivoSinCargo(cobroEntregado);
	}

	@Test
	void borrarConvocatoria_anulaLosCobrosDeSusExamenes_yLosEntregadosSiguenActivos() throws Exception {
		long inscripcion = inscripcionConCargo(e.aA);
		long convocatoria = convocatoriaDe(inscripcion);
		long otra = inscripcionConCargo(e.aB, convocatoria);
		long cargo = cargoDe(inscripcion);
		long cargoOtra = cargoDe(otra);
		assertEquals(200, estado(pago(cargo, "{\"pagado\":true}"), tokenGestor));
		assertEquals(200, estado(pago(cargoOtra, "{\"pagado\":true}"), tokenGestor));
		long cobro = idCobro(cargo);
		long cobroEntregado = idCobro(cargoOtra);
		meterEnEntrega(cobroEntregado);

		int estado = estado(delete("/api/convocatorias/" + convocatoria), tokenGestor);
		assertTrue(estado >= 200 && estado < 300, "estado " + estado);
		assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM convocatoria WHERE id = ?", Long.class,
				convocatoria));
		assertAnuladoPorBorrado(cobro);
		assertActivoSinCargo(cobroEntregado);
	}

	// ------------------------------------------------------------------ concurrencia

	@Test
	void dosCobrosSimultaneos_unSoloCobroActivo() throws Exception {
		for (int vuelta = 0; vuelta < 5; vuelta++) {
			long cargo = cargo(e.aA);
			List<Integer> estados = enParalelo(
					() -> estado(pago(cargo, "{\"pagado\":true,\"metodoPago\":\"EFECTIVO\"}"), tokenGestor),
					() -> estado(pago(cargo, "{\"pagado\":true,\"metodoPago\":\"BIZUM\"}"), tokenGestor));
			assertEquals(List.of(200, 200), estados);
			assertEquals(1, cobros(cargo).size(), "vuelta " + vuelta);
		}
		// Las dos puertas a la vez.
		long inscripcion = inscripcionConCargo(e.aGrupoA);
		long cargo = cargoDe(inscripcion);
		List<Integer> estados = enParalelo(
				() -> estado(pago(cargo, "{\"pagado\":true}"), tokenGestor),
				() -> estado(pagoConvocatoria(inscripcion, "{\"cuantiaExamen\":20.0,\"pagado\":true}"), tokenGestor));
		assertEquals(List.of(200, 200), estados);
		assertEquals(1, cobros(cargo).size());
	}

	@Test
	void elCobroEsperaAlBloqueoDelCargo_yLuegoVeElPagoDeOtro() throws Exception {
		long cargo = cargo(e.aA);
		ExecutorService hilo = Executors.newSingleThreadExecutor();
		try (Connection otra = dataSource.getConnection()) {
			otra.setAutoCommit(false);
			try (PreparedStatement bloqueo = otra.prepareStatement(
					"SELECT id FROM producto_alumno WHERE id = ? FOR UPDATE")) {
				bloqueo.setLong(1, cargo);
				bloqueo.executeQuery().close();
			}
			Future<Integer> cobro = hilo.submit(
					() -> estado(pago(cargo, "{\"pagado\":true,\"metodoPago\":\"BIZUM\"}"), tokenGestor));
			Thread.sleep(1500);
			assertFalse(cobro.isDone(), "el PUT debe esperar al bloqueo de la fila del cargo");
			// Otro cobro del mismo cargo termina mientras tanto.
			try (PreparedStatement pagar = otra.prepareStatement(
					"UPDATE producto_alumno SET pagado = 1, fecha_pago = NOW(6) WHERE id = ?");
					PreparedStatement registrar = otra.prepareStatement("""
							INSERT INTO cobro (organizacion_id, producto_alumno_id, sede_id, importe, metodo, fecha_cobro,
							  origen, estado) VALUES (?, ?, ?, 10.50, 'EFECTIVO', NOW(6), 'TESORERIA', 'ACTIVO')""")) {
				pagar.setLong(1, cargo);
				pagar.executeUpdate();
				registrar.setLong(1, e.org);
				registrar.setLong(2, cargo);
				registrar.setLong(3, e.sA);
				registrar.executeUpdate();
			}
			otra.commit();
			assertEquals(200, cobro.get(60, TimeUnit.SECONDS));
		} finally {
			hilo.shutdownNow();
		}
		List<Map<String, Object>> filas = cobros(cargo);
		assertEquals(1, filas.size(), "relee el cargo tras el bloqueo: ya pagado, no crea otro");
		assertEquals("EFECTIVO", filas.get(0).get("metodo"));
	}

	// ------------------------------------------------------------------ servicio sin petición web y escuelas

	@Test
	void servicioSinPeticionWeb_registraElCobroConElUsuarioDeLaSesion() {
		long cargo = cargo(e.aB);
		Usuario gestor = TenantContext.calcularCon(EscuelaConSedesDePrueba.ORG1,
				() -> usuarios.findById(e.gestor).orElseThrow());
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(gestor, null,
				List.of(new SimpleGrantedAuthority("ROLE_MANAGER"))));
		ProductoAlumnoDTO cambios = new ProductoAlumnoDTO();
		cambios.setPagado(true);
		cambios.setMetodoPago(MetodoPago.TRANSFERENCIA);
		TenantContext.ejecutarCon(e.org, () -> productoAlumnoService.actualizarProductoAlumno(cargo, cambios));
		TenantContext.ejecutarCon(e.org, () -> productoAlumnoService.actualizarProductoAlumno(cargo, cambios));
		List<Map<String, Object>> filas = cobros(cargo);
		assertEquals(1, filas.size());
		assertEquals("TRANSFERENCIA", filas.get(0).get("metodo"));
		assertEquals(e.gestor, numero(filas.get(0), "cobrado_por_usuario_id"));
		assertEquals(e.sB, numero(filas.get(0), "sede_id"));
	}

	@Test
	void unCobroDeOtraEscuela_noSeVeDesdeLaOrganizacionUno() throws Exception {
		long cargo = cargo(e.aA);
		assertEquals(200, estado(pago(cargo, "{\"pagado\":true}"), tokenGestor));
		long cobro = numero(cobros(cargo).get(0), "id");
		assertTrue(TenantContext.calcularCon(e.org, () -> cobroRepository.findById(cobro)).isPresent());
		assertTrue(TenantContext.calcularCon(EscuelaConSedesDePrueba.ORG1, () -> cobroRepository.findById(cobro))
				.isEmpty());
		assertTrue(TenantContext.calcularCon(EscuelaConSedesDePrueba.ORG1,
				() -> cobroRepository.findByProductoAlumnoIdOrderByIdAsc(cargo)).isEmpty());
		assertFalse(TenantContext.calcularCon(EscuelaConSedesDePrueba.ORG1, () -> cobroRepository.findAll())
				.stream().anyMatch(c -> c.getId() == cobro));
	}

	// ------------------------------------------------------------------ utilidades

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

	/** Inscripción a una convocatoria nueva con su cargo de derecho a examen pendiente (como al apuntarlo). */
	private long inscripcionConCargo(long alumno) {
		jdbc.update("INSERT INTO convocatoria (fecha_convocatoria, organizacion_id, disciplina_id) VALUES "
				+ "('2026-06-01', ?, ?)", e.org, e.disciplina);
		long convocatoria = jdbc.queryForObject("SELECT MAX(id) FROM convocatoria WHERE organizacion_id = ?",
				Long.class, e.org);
		return inscripcionConCargo(alumno, convocatoria);
	}

	private long inscripcionConCargo(long alumno, long convocatoria) {
		jdbc.update("""
				INSERT INTO producto_alumno (alumno_id, concepto, precio, cantidad, pagado, fecha_asignacion,
				  organizacion_id, sede_id)
				VALUES (?, 'DERECHO A EXAMEN 6B1', 20.0, 1, 0, NOW(), ?,
				  (SELECT sede_principal_id FROM alumno WHERE id = ?))""", alumno, e.org, alumno);
		long cargo = jdbc.queryForObject("SELECT MAX(id) FROM producto_alumno WHERE organizacion_id = ?", Long.class,
				e.org);
		jdbc.update("""
				INSERT INTO alumno_convocatoria (cuantia_examen, pagado, alumno_id, convocatoria_id, grado_actual,
				  grado_siguiente, organizacion_id, producto_alumno_id) VALUES (20, 0, ?, ?, NULL, NULL, ?, ?)""",
				alumno, convocatoria, e.org, cargo);
		return jdbc.queryForObject("SELECT id FROM alumno_convocatoria WHERE alumno_id = ? AND convocatoria_id = ?",
				Long.class, alumno, convocatoria);
	}

	private long convocatoriaDe(long inscripcion) {
		return jdbc.queryForObject("SELECT convocatoria_id FROM alumno_convocatoria WHERE id = ?", Long.class,
				inscripcion);
	}

	private long idCobro(long cargo) {
		List<Map<String, Object>> filas = cobros(cargo);
		assertEquals(1, filas.size());
		return numero(filas.get(0), "id");
	}

	private void assertAnuladoPorBorrado(long cobro) {
		Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM cobro WHERE id = ?", cobro);
		assertEquals("ANULADO", fila.get("estado"));
		assertEquals("cargo borrado", fila.get("motivo_anulacion"));
		assertEquals(e.gestor, numero(fila, "anulado_por_usuario_id"));
		assertNotNull(fila.get("anulado_en"));
		assertNull(fila.get("producto_alumno_id"));
		assertEquals(1L, numero(fila, "version"), "el UPDATE sube la versión");
	}

	private void assertActivoSinCargo(long cobro) {
		Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM cobro WHERE id = ?", cobro);
		assertEquals("ACTIVO", fila.get("estado"));
		assertNull(fila.get("producto_alumno_id"));
		assertNotNull(fila.get("entrega_caja_id"));
		assertNull(fila.get("anulado_en"));
	}

	private long cargoDe(long inscripcion) {
		return jdbc.queryForObject("SELECT producto_alumno_id FROM alumno_convocatoria WHERE id = ?", Long.class,
				inscripcion);
	}

	/** Entrega de caja sintética (en la 6b-1 nadie las crea) con el cobro dentro. */
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

	private Map<String, Object> filaCargo(long cargo) {
		return jdbc.queryForMap("SELECT * FROM producto_alumno WHERE id = ?", cargo);
	}

	private boolean pagado(long cargo) {
		return Boolean.TRUE.equals(
				jdbc.queryForObject("SELECT pagado FROM producto_alumno WHERE id = ?", Boolean.class, cargo));
	}

	private static long numero(Map<String, Object> fila, String columna) {
		return ((Number) fila.get(columna)).longValue();
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
