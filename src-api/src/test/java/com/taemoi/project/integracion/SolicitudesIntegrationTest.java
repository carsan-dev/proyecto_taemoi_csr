package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.acceso.ConsultaAccesoRecursosJpa;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.GrupoService;
import com.taemoi.project.services.JwtService;

import jakarta.servlet.http.Cookie;

/**
 * Pieza 6c-2 contra MySQL: solicitudes al propietario ({@code /api/solicitudes}): pedir (tipos 1, con o sin cobro, y
 * 3), retirar, denegar, bandeja por segmento y contador; registro de acciones; sesión solo-profesor fuera; aislamiento.
 * Escuela sintética de sedes A y B ({@link EscuelaConSedesDePrueba}) con propietario, gestor, responsable de A y un
 * profesor de gA; otra escuela para el aislamiento. Los encargos (6c-5) aún no se crean por la API: los tests que los
 * necesitan los insertan por SQL. Datos inventados.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SolicitudesIntegrationTest extends MySqlIntegrationTest {

	private static final String NO_PENDIENTE = "La solicitud ya no está pendiente";

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired DataSource dataSource;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;
	@Autowired ConsultaAccesoRecursosJpa consultaAcceso;

	@MockitoBean JavaMailSender mailSender;

	/** Una escuela de prueba con sus cuentas, tokens, un producto y un alumno del grupo del profesor. */
	final class Escena {
		final EscuelaConSedesDePrueba e;
		final long propietario, prof1, producto, aProfA;
		final String tGestor, tPropietario, tResp, tProf1;

		Escena(String prefijo) {
			e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, prefijo);
			propietario = e.cuenta(usuarios, "propietario");
			jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, 'PROPIETARIO')",
					propietario, e.org);
			prof1 = e.cuentaFamilia(usuarios, "prof1");
			jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, 'PROFESOR')", prof1,
					e.org);
			jdbc.update("INSERT INTO asignacion_profesor (organizacion_id, usuario_id, grupo_id, rol) "
					+ "VALUES (?, ?, ?, 'TITULAR')", e.org, prof1, e.gA);
			tGestor = e.token(jwtService, usuarios, e.gestor);
			tPropietario = e.token(jwtService, usuarios, propietario);
			tResp = e.token(jwtService, usuarios, e.respA);
			tProf1 = e.token(jwtService, usuarios, prof1);
			jdbc.update("INSERT INTO producto (concepto, precio, codigo, organizacion_id, vendible_profesor) VALUES "
					+ "('CAMISETA 6C2', 10.5, NULL, ?, TRUE)", e.org);
			producto = jdbc.queryForObject("SELECT MAX(id) FROM producto WHERE organizacion_id = ?", Long.class, e.org);
			aProfA = e.alumno("E6C2PROFA" + e.marca, e.sA, true);
			jdbc.update("INSERT INTO alumno_grupo (grupo_id, alumno_id) VALUES (?, ?)", e.gA, aProfA);
		}

		/** Cargo pendiente (10,50) del alumno, creado por el gestor como siempre (sede = su sede principal). */
		long cargo(long alumno) throws Exception {
			MvcResult r = peticion(post("/api/productos-alumno/alumno/" + alumno + "/producto/" + producto)
					.contentType(MediaType.APPLICATION_JSON).content("{\"cantidad\":1,\"pagado\":false}"), tGestor);
			assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
			return json.readTree(r.getResponse().getContentAsString()).get("id").asLong();
		}

		/** Cargo cobrado por el gestor en tesorería (EFECTIVO): devuelve el cargo. */
		long cargoCobrado(long alumno) throws Exception {
			long cargo = cargo(alumno);
			assertEquals(200, estado(pago(cargo, "{\"pagado\":true,\"metodoPago\":\"EFECTIVO\"}"), tGestor));
			return cargo;
		}

		/** Cargo pagado ANTES de la 6b-1: pagado por SQL, sin fila de cobro (decisión 191). */
		long cargoPagadoSinCobro(long alumno) throws Exception {
			long cargo = cargo(alumno);
			jdbc.update("UPDATE producto_alumno SET pagado = 1, fecha_pago = NOW(6) WHERE id = ?", cargo);
			return cargo;
		}

		/** El profesor cobra un cargo nuevo de su alumno y el gestor lo recibe en una entrega; devuelve la entrega. */
		JsonNode entregaDeUnCobro(String contado) throws Exception {
			long cargo = cargo(aProfA);
			MvcResult r = peticion(post("/api/caja/profesor/cargos/" + cargo + "/cobrar"), tProf1);
			assertEquals(201, r.getResponse().getStatus(), r.getResponse().getContentAsString());
			long cobro = json.readTree(r.getResponse().getContentAsString()).get("cobroId").asLong();
			Map<String, Object> cuerpo = new HashMap<>();
			cuerpo.put("entregadoPorUsuarioId", prof1);
			cuerpo.put("cobroIds", List.of(cobro));
			cuerpo.put("importeContado", new BigDecimal(contado));
			cuerpo.put("nota", "10.50".equals(contado) ? null : "diferencia");
			return leer(post("/api/caja/sedes/" + e.sA + "/entregas").contentType(MediaType.APPLICATION_JSON)
					.content(json.writeValueAsString(cuerpo)), tGestor, 201);
		}

		long movimientoSuelto(long sede) throws Exception {
			return leer(post("/api/caja/sedes/" + sede + "/movimientos").contentType(MediaType.APPLICATION_JSON)
					.content("{\"tipo\":\"OTRO\",\"importe\":1.00,\"nota\":\"ajuste\"}"), tGestor, 201).get("id")
					.asLong();
		}

		/** Encargo (tipo 4) insertado por SQL: la 6c-5 aún no los crea por la API. */
		long encargo(Long sede, String estado) {
			jdbc.update("""
					INSERT INTO solicitud (organizacion_id, sede_id, tipo, estado, solicitado_por_usuario_id, solicitado_en,
					  motivo, producto_id, alumno_id, concepto, importe)
					VALUES (?, ?, 'ENCARGO', ?, ?, NOW(6), 'talla M', ?, ?, 'CAMISETA 6C2', 10.50)""", e.org, sede, estado,
					prof1, producto, aProfA);
			return jdbc.queryForObject("SELECT MAX(id) FROM solicitud WHERE organizacion_id = ?", Long.class, e.org);
		}

		BigDecimal saldo(long sede) throws Exception {
			return leer(get("/api/caja/sedes/" + sede + "/resumen"), tPropietario, 200).get("saldoCaja").decimalValue();
		}
	}

	Escena s;
	Escena otra;

	@BeforeAll
	void preparar() {
		s = new Escena("it-6c2-sol");
		otra = new Escena("it-6c2-otra");
	}

	@AfterEach
	void sinFugas() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
		SecurityContextHolder.clearContext();
	}

	// ------------------------------------------------------------------ pedir (tipo 1)

	@Test
	void gestor_pideRevertirUnCobro_pendienteConInstantanea_sinTocarElCobroNiLaCaja() throws Exception {
		long cargo = s.cargoCobrado(s.e.aA);
		long cobro = cobroActivo(cargo);
		BigDecimal saldo = s.saldo(s.e.sA);

		JsonNode creada = leer(crear("REVERTIR_COBRO", "cobroId", cobro, "Se cobró dos veces"), s.tGestor, 201);
		long id = creada.get("id").asLong();
		assertEquals("REVERTIR_COBRO", creada.get("tipo").asText());
		assertEquals("PENDIENTE", creada.get("estado").asText());
		assertTrue(creada.get("propia").asBoolean());
		assertEquals(s.e.aA, creada.get("alumnoId").asLong());
		assertEquals("Se cobró dos veces", creada.get("motivo").asText());

		Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM solicitud WHERE id = ?", id);
		assertEquals("REVERTIR_COBRO", fila.get("tipo"));
		assertEquals("PENDIENTE", fila.get("estado"));
		assertEquals(s.e.org, numero(fila, "organizacion_id"));
		assertEquals(s.e.sA, numero(fila, "sede_id"));
		assertEquals(s.e.gestor, numero(fila, "solicitado_por_usuario_id"));
		assertNotNull(fila.get("solicitado_en"));
		assertEquals(cobro, numero(fila, "cobro_id"));
		assertEquals(cargo, numero(fila, "producto_alumno_id"));
		assertEquals(s.e.aA, numero(fila, "alumno_id"));
		assertEquals("CAMISETA 6C2", fila.get("concepto"));
		assertEquals(0, new BigDecimal("10.50").compareTo((BigDecimal) fila.get("importe")));
		assertNull(fila.get("resuelto_por_usuario_id"));
		assertNull(fila.get("respuesta"));

		assertEquals("ACTIVO", jdbc.queryForObject("SELECT estado FROM cobro WHERE id = ?", String.class, cobro),
				"mientras tanto el pago sigue marcado");
		assertTrue(pagado(cargo));
		assertEquals(0, saldo.compareTo(s.saldo(s.e.sA)), "pedir no mueve dinero");
	}

	@Test
	void pedirPorElCargoConCobroActivo_enlazaElCobro() throws Exception {
		long cargo = s.cargoCobrado(s.e.aA);
		long id = leer(crear("REVERTIR_COBRO", "productoAlumnoId", cargo, "por el cargo"), s.tGestor, 201).get("id")
				.asLong();
		assertEquals(cobroActivo(cargo), jdbc.queryForObject("SELECT cobro_id FROM solicitud WHERE id = ?", Long.class,
				id));
	}

	/**
	 * Decisión 191: pedir sobre un cargo pagado antes de la 6b-1 (sin cobro; 6c-2) y aceptar (6c-3): solo se desmarca,
	 * sin crear ni anular cobros, y la caja no cambia.
	 */
	@Test
	void revertirCargoSinCobro_pideAceptaYSoloDesmarca() throws Exception {
		long cargo = s.cargoPagadoSinCobro(s.e.aA);
		JsonNode creada = leer(crear("REVERTIR_COBRO", "productoAlumnoId", cargo, "Pagado dos veces en 2025"),
				s.tGestor, 201);
		long id = creada.get("id").asLong();
		Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM solicitud WHERE id = ?", id);
		assertNull(fila.get("cobro_id"));
		assertEquals(cargo, numero(fila, "producto_alumno_id"));
		assertEquals(s.e.sA, numero(fila, "sede_id"), "la sede del cargo");
		assertEquals(0, new BigDecimal("10.50").compareTo((BigDecimal) fila.get("importe")));
		assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM cobro WHERE producto_alumno_id = ?", Long.class,
				cargo), "pedir no crea cobros");
		assertTrue(pagado(cargo));

		BigDecimal saldo = s.saldo(s.e.sA);
		JsonNode aceptada = leer(post("/api/solicitudes/" + id + "/aceptar"), s.tPropietario, 200);
		assertEquals("ACEPTADA", aceptada.get("estado").asText());
		assertTrue(aceptada.get("respuesta").isNull());
		Map<String, Object> resuelta = jdbc.queryForMap("SELECT * FROM solicitud WHERE id = ?", id);
		assertEquals("ACEPTADA", resuelta.get("estado"));
		assertEquals(s.propietario, numero(resuelta, "resuelto_por_usuario_id"));
		assertNotNull(resuelta.get("resuelto_en"));
		assertFalse(pagado(cargo), "se desmarca como hoy");
		assertNull(jdbc.queryForObject("SELECT fecha_pago FROM producto_alumno WHERE id = ?", Object.class, cargo));
		assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM cobro WHERE producto_alumno_id = ?", Long.class,
				cargo), "ni crea ni anula cobros");
		assertEquals(0, saldo.compareTo(s.saldo(s.e.sA)), "no había cobro en la caja");
	}

	@Test
	void dosPendientesMismoCobro_409() throws Exception {
		long cargo = s.cargoCobrado(s.e.aA);
		long cobro = cobroActivo(cargo);
		long primera = leer(crear("REVERTIR_COBRO", "cobroId", cobro, "una"), s.tGestor, 201).get("id").asLong();
		MvcResult otra = peticion(crear("REVERTIR_COBRO", "cobroId", cobro, "otra"), s.tResp);
		assertEquals(409, otra.getResponse().getStatus());
		assertTrue(mensaje(otra).startsWith("Ya hay una solicitud pendiente"), mensaje(otra));
		assertEquals(409, estado(crear("REVERTIR_COBRO", "productoAlumnoId", cargo, "por el cargo"), s.tGestor));
		assertEquals(1L, contarSolicitudesDeCobro(cobro));

		assertEquals(200, estado(post("/api/solicitudes/" + primera + "/retirar"), s.tGestor));
		assertEquals(201, estado(crear("REVERTIR_COBRO", "cobroId", cobro, "de nuevo"), s.tResp));
		assertEquals(2L, contarSolicitudesDeCobro(cobro));
	}

	/** Dos peticiones a la vez sobre el mismo cobro: una crea (201) y la otra 409; queda una sola PENDIENTE. */
	@Test
	void dosCrearALaVezMismoCobro_201Y409_unaSolaPendiente() throws Exception {
		for (int i = 0; i < 3; i++) {
			long cobro = cobroActivo(s.cargoCobrado(s.e.aA));
			List<MvcResult> resultados = enParalelo(
					() -> peticion(crear("REVERTIR_COBRO", "cobroId", cobro, "a la vez 1"), s.tGestor),
					() -> peticion(crear("REVERTIR_COBRO", "cobroId", cobro, "a la vez 2"), s.tResp));
			List<Integer> estados = new ArrayList<>();
			resultados.forEach(r -> estados.add(r.getResponse().getStatus()));
			estados.sort(Integer::compareTo);
			assertEquals(List.of(201, 409), estados);
			MvcResult perdedor = resultados.stream().filter(r -> r.getResponse().getStatus() == 409).findFirst()
					.orElseThrow();
			assertTrue(mensaje(perdedor).startsWith("Ya hay una solicitud pendiente"), mensaje(perdedor));
			assertEquals(1L, jdbc.queryForObject(
					"SELECT COUNT(*) FROM solicitud WHERE cobro_id = ? AND estado = 'PENDIENTE'", Long.class, cobro));
		}
	}

	/**
	 * La clave única de V31_8 como última red, de forma determinista: otra transacción tiene insertada (sin confirmar)
	 * una PENDIENTE del mismo cobro. La comprobación previa no la ve (READ_COMMITTED), el INSERT espera a la clave y,
	 * al confirmar la otra, choca: 409, no 500, y no queda nada escrito.
	 */
	@Test
	void claveUnicaComoUltimaRed_insertEnCurso_409() throws Exception {
		long cargo = s.cargoCobrado(s.e.aA);
		long cobro = cobroActivo(cargo);
		try (Connection otraTransaccion = dataSource.getConnection()) {
			otraTransaccion.setAutoCommit(false);
			try (PreparedStatement insert = otraTransaccion.prepareStatement("""
					INSERT INTO solicitud (organizacion_id, sede_id, tipo, estado, solicitado_por_usuario_id, solicitado_en,
					  motivo, cobro_id, producto_alumno_id)
					VALUES (?, ?, 'REVERTIR_COBRO', 'PENDIENTE', ?, NOW(6), 'en curso', ?, ?)""")) {
				insert.setLong(1, s.e.org);
				insert.setLong(2, s.e.sA);
				insert.setLong(3, s.e.gestor);
				insert.setLong(4, cobro);
				insert.setLong(5, cargo);
				insert.executeUpdate();
			}
			ExecutorService executor = Executors.newSingleThreadExecutor();
			try {
				Future<MvcResult> pedir = executor
						.submit(() -> peticion(crear("REVERTIR_COBRO", "cobroId", cobro, "a la vez"), s.tResp));
				// Como CajaSedeIntegrationTest#revertirDuranteEntrega: la cuenta de los tests no ve INNODB_TRX.
				Thread.sleep(1500);
				assertFalse(pedir.isDone(), "la petición debe esperar a la clave única");
				otraTransaccion.commit();
				MvcResult r = pedir.get(60, TimeUnit.SECONDS);
				assertEquals(409, r.getResponse().getStatus(), r.getResponse().getContentAsString());
				assertEquals("Ya hay una solicitud pendiente sobre este recurso.", mensaje(r));
			} finally {
				executor.shutdownNow();
			}
		}
		assertEquals(1L, contarSolicitudesDeCobro(cobro), "solo la de la otra transacción");
		assertEquals(0L, jdbc.queryForObject("""
				SELECT COUNT(*) FROM auditoria_evento WHERE organizacion_id = ? AND modulo = 'solicitudes'
				  AND resumen LIKE CONCAT('SOLICITUDES SOLICITUD_CREADA % cobro=#', ?, ' %')""", Long.class, s.e.org,
				cobro), "el intento fallido no deja registro");
	}

	@Test
	void dosPendientesMismoCargoSinCobro_409() throws Exception {
		long cargo = s.cargoPagadoSinCobro(s.e.aA);
		assertEquals(201, estado(crear("REVERTIR_COBRO", "productoAlumnoId", cargo, "una"), s.tGestor));
		assertEquals(409, estado(crear("REVERTIR_COBRO", "productoAlumnoId", cargo, "otra"), s.tResp));
		assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM solicitud WHERE producto_alumno_id = ?", Long.class,
				cargo));
	}

	// ------------------------------------------------------------------ pedir (tipo 3)

	@Test
	void pedirAnularEntregaOMovimiento_tipo3() throws Exception {
		JsonNode entrega = s.entregaDeUnCobro("11.00");
		long entregaId = entrega.get("id").asLong();
		long automatico = entrega.get("movimientoId").asLong();
		JsonNode creada = leer(crear("ANULAR_ENTREGA", "entregaId", entregaId, "Se entregó en la sede equivocada"),
				s.tGestor, 201);
		assertEquals("ANULAR_ENTREGA", creada.get("tipo").asText());
		assertEquals(entregaId, creada.get("entregaCajaId").asLong());
		assertEquals(0, new BigDecimal("10.50").compareTo(creada.get("importe").decimalValue()), "lo declarado");
		assertEquals(s.e.sA, creada.get("sedeId").asLong());
		assertEquals(409, estado(crear("ANULAR_ENTREGA", "entregaId", entregaId, "otra"), s.tResp));

		long movimiento = s.movimientoSuelto(s.e.sA);
		JsonNode mov = leer(crear("ANULAR_MOVIMIENTO", "movimientoId", movimiento, "Apuntado por error"), s.tResp, 201);
		assertEquals(movimiento, mov.get("movimientoCajaId").asLong());
		assertEquals(0, BigDecimal.ONE.compareTo(mov.get("importe").decimalValue()));
		assertEquals(409, estado(crear("ANULAR_MOVIMIENTO", "movimientoId", movimiento, "otra"), s.tGestor));

		MvcResult r = peticion(crear("ANULAR_MOVIMIENTO", "movimientoId", automatico, "el automático"), s.tGestor);
		assertEquals(409, r.getResponse().getStatus(), "el automático se anula con su entrega");
		assertTrue(mensaje(r).contains("entrega"), mensaje(r));

		long anulada = s.entregaDeUnCobro("10.50").get("id").asLong();
		assertEquals(200, estado(post("/api/caja/entregas/" + anulada + "/anular").contentType(MediaType.APPLICATION_JSON)
				.content("{\"motivo\":\"x\"}"), s.tPropietario));
		assertEquals(409, estado(crear("ANULAR_ENTREGA", "entregaId", anulada, "ya anulada"), s.tGestor));
	}

	@Test
	void crear_validaciones() throws Exception {
		long cargo = s.cargoCobrado(s.e.aA);
		long cobro = cobroActivo(cargo);
		long antes = contarSolicitudes(s);

		MvcResult propietario = peticion(crear("REVERTIR_COBRO", "cobroId", cobro, "x"), s.tPropietario);
		assertEquals(400, propietario.getResponse().getStatus());
		assertEquals("El propietario lo hace directamente: no necesita pedirlo.", mensaje(propietario));
		assertEquals(400, estado(crear("REVERTIR_COBRO", "cobroId", cobro, " "), s.tGestor));
		assertEquals(400, estado(crear("REVERTIR_COBRO", "cobroId", cobro, "x".repeat(501)), s.tGestor));
		assertEquals(400, estado(crear("DESHACER_COBRO_ENTREGADO", "cobroId", cobro, "x"), s.tGestor));
		assertEquals(400, estado(crear("ENCARGO", "productoAlumnoId", cargo, "x"), s.tGestor));
		assertEquals(400, estado(cuerpo(Map.of("cobroId", cobro, "motivo", "x")), s.tGestor), "sin tipo");
		assertEquals(400, estado(cuerpo(Map.of("tipo", "NO_EXISTE", "cobroId", cobro, "motivo", "x")), s.tGestor));
		assertEquals(400, estado(cuerpo(Map.of("tipo", "REVERTIR_COBRO", "motivo", "x")), s.tGestor), "sin recurso");
		assertEquals(400, estado(cuerpo(Map.of("tipo", "REVERTIR_COBRO", "cobroId", cobro, "productoAlumnoId", cargo,
				"motivo", "x")), s.tGestor), "dos recursos");
		assertEquals(400, estado(cuerpo(Map.of("tipo", "ANULAR_ENTREGA", "cobroId", cobro, "motivo", "x")), s.tGestor),
				"recurso que no es del tipo");
		assertEquals(404, estado(crear("REVERTIR_COBRO", "cobroId", 999_999_999L, "x"), s.tGestor));
		long cobroOtra = cobroActivo(otra.cargoCobrado(otra.e.aA));
		assertEquals(404, estado(crear("REVERTIR_COBRO", "cobroId", cobroOtra, "x"), s.tGestor), "de otra escuela");
		assertEquals(404, estado(crear("ANULAR_ENTREGA", "entregaId", 999_999_999L, "x"), s.tGestor));
		assertEquals(404, estado(crear("ANULAR_MOVIMIENTO", "movimientoId", 999_999_999L, "x"), s.tGestor));

		long pendiente = s.cargo(s.e.aA);
		assertEquals(409, estado(crear("REVERTIR_COBRO", "productoAlumnoId", pendiente, "x"), s.tGestor),
				"cargo no pagado");
		long revertido = s.cargoCobrado(s.e.aA);
		long cobroRevertido = cobroActivo(revertido);
		assertEquals(200, estado(pago(revertido, "{\"pagado\":false}"), s.tPropietario));
		assertEquals(409, estado(crear("REVERTIR_COBRO", "cobroId", cobroRevertido, "x"), s.tGestor), "cobro anulado");
		JsonNode entrega = s.entregaDeUnCobro("10.50");
		long entregado = entrega.get("cobroIds").get(0).asLong();
		MvcResult r = peticion(crear("REVERTIR_COBRO", "cobroId", entregado, "x"), s.tGestor);
		assertEquals(409, r.getResponse().getStatus(), "cobro ya entregado: se pide anular la entrega");
		assertTrue(mensaje(r).contains("entrega"), mensaje(r));
		assertEquals(antes, contarSolicitudes(s), "ningún intento crea solicitud");
	}

	@Test
	void responsable_soloPideSobreRecursosDeSusSedes() throws Exception {
		long cobroA = cobroActivo(s.cargoCobrado(s.e.aA));
		long cobroB = cobroActivo(s.cargoCobrado(s.e.aB));
		long sinSede = s.cargoPagadoSinCobro(s.e.aNada);
		long movimientoB = s.movimientoSuelto(s.e.sB);
		assertEquals(201, estado(crear("REVERTIR_COBRO", "cobroId", cobroA, "de A"), s.tResp));
		assertEquals(403, estado(crear("REVERTIR_COBRO", "cobroId", cobroB, "de B"), s.tResp));
		assertEquals(403, estado(crear("REVERTIR_COBRO", "productoAlumnoId", sinSede, "sin sede"), s.tResp));
		assertEquals(403, estado(crear("ANULAR_MOVIMIENTO", "movimientoId", movimientoB, "de B"), s.tResp));
		assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM solicitud WHERE cobro_id = ? OR producto_alumno_id = ? "
				+ "OR movimiento_caja_id = ?", Long.class, cobroB, sinSede, movimientoB));
		// La gestión sí, en cualquier sede o sin sede.
		assertEquals(201, estado(crear("REVERTIR_COBRO", "cobroId", cobroB, "de B"), s.tGestor));
		assertEquals(201, estado(crear("REVERTIR_COBRO", "productoAlumnoId", sinSede, "sin sede"), s.tGestor));
	}

	// ------------------------------------------------------------------ retirar y denegar

	@Test
	void retirar_soloLaSuya_403ParaOtros() throws Exception {
		long cobro = cobroActivo(s.cargoCobrado(s.e.aA));
		long id = leer(crear("REVERTIR_COBRO", "cobroId", cobro, "mía"), s.tResp, 201).get("id").asLong();
		assertEquals(403, estado(post("/api/solicitudes/" + id + "/retirar"), s.tGestor));
		assertEquals(403, estado(post("/api/solicitudes/" + id + "/retirar"), s.tPropietario));
		assertEquals("PENDIENTE", estadoDe(id));

		JsonNode retirada = leer(post("/api/solicitudes/" + id + "/retirar"), s.tResp, 200);
		assertEquals("RETIRADA", retirada.get("estado").asText());
		Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM solicitud WHERE id = ?", id);
		assertEquals("RETIRADA", fila.get("estado"));
		assertEquals(s.e.respA, numero(fila, "resuelto_por_usuario_id"));
		assertNotNull(fila.get("resuelto_en"));

		MvcResult otraVez = peticion(post("/api/solicitudes/" + id + "/retirar"), s.tResp);
		assertEquals(409, otraVez.getResponse().getStatus());
		assertEquals(NO_PENDIENTE, mensaje(otraVez));
		assertEquals(404, estado(post("/api/solicitudes/999999999/retirar"), s.tResp));

		long encargo = s.encargo(s.e.sA, "PEDIDO");
		jdbc.update("UPDATE solicitud SET solicitado_por_usuario_id = ? WHERE id = ?", s.e.gestor, encargo);
		assertEquals(400, estado(post("/api/solicitudes/" + encargo + "/retirar"), s.tGestor),
				"un encargo se retira desde Mi caja (6c-5)");
		assertEquals("PEDIDO", estadoDe(encargo));
	}

	@Test
	void denegar_soloPropietario_conRespuesta() throws Exception {
		long cobro = cobroActivo(s.cargoCobrado(s.e.aA));
		long id = leer(crear("REVERTIR_COBRO", "cobroId", cobro, "por favor"), s.tGestor, 201).get("id").asLong();
		assertEquals(403, estado(denegar(id, "no"), s.tGestor));
		assertEquals(403, estado(denegar(id, "no"), s.tResp));
		assertEquals(400, estado(denegar(id, " "), s.tPropietario));
		assertEquals(400, estado(post("/api/solicitudes/" + id + "/denegar"), s.tPropietario), "sin cuerpo");
		assertEquals("PENDIENTE", estadoDe(id));

		JsonNode denegada = leer(denegar(id, "El cobro es correcto"), s.tPropietario, 200);
		assertEquals("DENEGADA", denegada.get("estado").asText());
		assertEquals("El cobro es correcto", denegada.get("respuesta").asText());
		Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM solicitud WHERE id = ?", id);
		assertEquals(s.propietario, numero(fila, "resuelto_por_usuario_id"));
		assertNotNull(fila.get("resuelto_en"));
		assertEquals("ACTIVO", jdbc.queryForObject("SELECT estado FROM cobro WHERE id = ?", String.class, cobro));

		assertEquals(409, estado(denegar(id, "otra vez"), s.tPropietario));
		assertEquals(409, estado(post("/api/solicitudes/" + id + "/retirar"), s.tGestor));
		assertEquals(404, estado(denegar(999_999_999L, "no"), s.tPropietario));
		assertEquals(201, estado(crear("REVERTIR_COBRO", "cobroId", cobro, "insisto"), s.tGestor),
				"denegada ya no bloquea pedir otra");

		long encargo = s.encargo(s.e.sA, "PEDIDO");
		MvcResult r = peticion(denegar(encargo, "no"), s.tPropietario);
		assertEquals(400, r.getResponse().getStatus(), "un encargo se rechaza (6c-5), no se deniega");
		assertEquals("Un encargo no se deniega: se rechaza.", mensaje(r));
		assertEquals("PEDIDO", estadoDe(encargo));
	}

	@Test
	void denegarYRetirarALaVez_unoGana409ElOtro() throws Exception {
		for (int i = 0; i < 3; i++) {
			long cobro = cobroActivo(s.cargoCobrado(s.e.aA));
			long id = leer(crear("REVERTIR_COBRO", "cobroId", cobro, "carrera"), s.tGestor, 201).get("id").asLong();
			List<MvcResult> resultados = enParalelo(
					() -> peticion(denegar(id, "no"), s.tPropietario),
					() -> peticion(post("/api/solicitudes/" + id + "/retirar"), s.tGestor));
			List<Integer> estados = new ArrayList<>();
			resultados.forEach(r -> estados.add(r.getResponse().getStatus()));
			estados.sort(Integer::compareTo);
			assertEquals(List.of(200, 409), estados);
			MvcResult perdedor = resultados.stream().filter(r -> r.getResponse().getStatus() == 409).findFirst()
					.orElseThrow();
			assertEquals(NO_PENDIENTE, mensaje(perdedor));
			String fin = estadoDe(id);
			assertTrue(Set.of("DENEGADA", "RETIRADA").contains(fin), fin);
			assertEquals(1L, jdbc.queryForObject("""
					SELECT COUNT(*) FROM auditoria_evento WHERE organizacion_id = ? AND modulo = 'solicitudes'
					  AND resumen LIKE 'SOLICITUDES SOLICITUD_%' AND resumen NOT LIKE 'SOLICITUDES SOLICITUD_CREADA%'
					  AND recurso_id = ?""", Long.class, s.e.org, id), "una sola resolución registrada");
		}
	}

	// ------------------------------------------------------------------ bandeja y contador

	@Test
	void listarPorSegmento_propietarioTodo_gestorLasSuyas() throws Exception {
		Escena n = new Escena("it-6c2-lista");
		long deGestor = leer(crear("REVERTIR_COBRO", "cobroId", cobroActivo(n.cargoCobrado(n.e.aA)), "g1"), n.tGestor,
				201).get("id").asLong();
		long deResp = leer(crear("REVERTIR_COBRO", "cobroId", cobroActivo(n.cargoCobrado(n.e.aA)), "r1"), n.tResp, 201)
				.get("id").asLong();
		long denegada = leer(crear("REVERTIR_COBRO", "cobroId", cobroActivo(n.cargoCobrado(n.e.aA)), "g2"), n.tGestor,
				201).get("id").asLong();
		assertEquals(200, estado(denegar(denegada, "No procede"), n.tPropietario));
		long encargoA = n.encargo(n.e.sA, "PEDIDO");
		long entregado = n.encargo(n.e.sA, "ENTREGADO");

		assertEquals(List.of(deGestor, deResp), ids(listar("PENDIENTES", n.tPropietario)), "P: todas, la más antigua antes");
		assertEquals(List.of(deGestor), ids(listar("PENDIENTES", n.tGestor)), "G: las suyas");
		assertEquals(List.of(deResp), ids(listar("PENDIENTES", n.tResp)), "R: las suyas");
		assertEquals(List.of(encargoA), ids(listar("ENCARGOS", n.tPropietario)));
		assertEquals(List.of(encargoA), ids(listar("ENCARGOS", n.tGestor)));

		JsonNode resueltasP = listar("RESUELTAS", n.tPropietario);
		assertEquals(Set.of(denegada, entregado), new HashSet<>(ids(resueltasP)));
		JsonNode filaDenegada = fila(resueltasP, denegada);
		assertEquals("DENEGADA", filaDenegada.get("estado").asText());
		assertEquals("No procede", filaDenegada.get("respuesta").asText());
		assertEquals("Usuario Prueba", filaDenegada.get("solicitadoPorNombre").asText());
		assertEquals("Usuario Prueba", filaDenegada.get("resueltoPorNombre").asText());
		assertFalse(filaDenegada.get("propia").asBoolean(), "P no la pidió");
		assertEquals(Set.of(denegada, entregado), new HashSet<>(ids(listar("RESUELTAS", n.tGestor))));
		assertEquals(Set.of(entregado), new HashSet<>(ids(listar("RESUELTAS", n.tResp))), "la denegada no es suya");

		JsonNode fila = fila(listar("PENDIENTES", n.tPropietario), deGestor);
		assertEquals(n.e.sA, fila.get("sedeId").asLong());
		assertTrue(fila.get("sedeNombre").asText().startsWith("IT E6 A"));
		assertEquals(n.e.aA, fila.get("alumnoId").asLong());
		assertTrue(fila.get("alumnoNombre").asText().startsWith("E6A"));
		assertEquals("PRUEBA", fila.get("alumnoApellidos").asText());

		assertEquals(400, estado(get("/api/solicitudes").param("segmento", "OTRO"), n.tPropietario));
		assertEquals(400, estado(get("/api/solicitudes"), n.tPropietario), "segmento obligatorio");
	}

	@Test
	void responsable_veYCuentaSoloLoSuyoYLosEncargosDeSusSedes() throws Exception {
		Escena n = new Escena("it-6c2-resp");
		long suya = leer(crear("REVERTIR_COBRO", "cobroId", cobroActivo(n.cargoCobrado(n.e.aA)), "r"), n.tResp, 201)
				.get("id").asLong();
		leer(crear("REVERTIR_COBRO", "cobroId", cobroActivo(n.cargoCobrado(n.e.aA)), "g"), n.tGestor, 201);
		long encargoA = n.encargo(n.e.sA, "PEDIDO");
		long encargadoA = n.encargo(n.e.sA, "ENCARGADO");
		long recibidoA = n.encargo(n.e.sA, "RECIBIDO");
		n.encargo(n.e.sB, "PEDIDO");
		n.encargo(null, "PEDIDO");
		long caidoA = n.encargo(n.e.sA, "RECHAZADO");
		jdbc.update("UPDATE solicitud SET devolucion_pendiente = TRUE WHERE id = ?", caidoA);

		assertEquals(List.of(suya), ids(listar("PENDIENTES", n.tResp)));
		assertEquals(List.of(encargoA, encargadoA, recibidoA, caidoA), ids(listar("ENCARGOS", n.tResp)));
		assertEquals(2L, contador(n.tResp), "encargos PEDIDO o ENCARGADO de sus sedes");
		assertEquals(4L, contador(n.tGestor), "encargos PEDIDO o ENCARGADO de todas las sedes");
		assertEquals(2L + 4L, contador(n.tPropietario), "pendientes de tipos 1-3 + encargos PEDIDO o ENCARGADO");
	}

	@Test
	void contador_propietarioPendientesYEncargos_gestorEncargos() throws Exception {
		Escena n = new Escena("it-6c2-cont");
		assertEquals(0L, contador(n.tPropietario));
		assertEquals(0L, contador(n.tGestor));
		assertEquals(0L, contador(n.tResp));
		long una = leer(crear("REVERTIR_COBRO", "cobroId", cobroActivo(n.cargoCobrado(n.e.aA)), "1"), n.tGestor, 201)
				.get("id").asLong();
		leer(crear("ANULAR_MOVIMIENTO", "movimientoId", n.movimientoSuelto(n.e.sA), "2"), n.tResp, 201);
		long tres = leer(crear("REVERTIR_COBRO", "cobroId", cobroActivo(n.cargoCobrado(n.e.aA)), "3"), n.tGestor, 201)
				.get("id").asLong();
		assertEquals(3L, contador(n.tPropietario));
		assertEquals(0L, contador(n.tGestor), "lo que uno pidió no espera su acción");
		assertEquals(0L, contador(n.tResp));
		assertEquals(200, estado(post("/api/solicitudes/" + una + "/retirar"), n.tGestor));
		assertEquals(200, estado(denegar(tres, "no"), n.tPropietario));
		assertEquals(1L, contador(n.tPropietario), "las resueltas no cuentan");
		n.encargo(n.e.sB, "PEDIDO");
		n.encargo(n.e.sA, "RECIBIDO");
		assertEquals(2L, contador(n.tPropietario));
		assertEquals(1L, contador(n.tGestor));
		assertEquals(0L, contador(n.tResp), "el encargo de B no es de sus sedes; el RECIBIDO no espera acción");
	}

	/** Punto de revisión 5 (Moiskimdo, sin sedes): sin sede la ven y la cuentan P y G, por su alcance total; R no. */
	@Test
	void solicitudSinSede_laVenYCuentanPyG_noR() throws Exception {
		Escena n = new Escena("it-6c2-sinsede");
		long cargo = n.cargoPagadoSinCobro(n.e.aNada);
		assertNull(jdbc.queryForObject("SELECT sede_id FROM producto_alumno WHERE id = ?", Long.class, cargo));
		long tipo1 = leer(crear("REVERTIR_COBRO", "productoAlumnoId", cargo, "sin sede"), n.tGestor, 201).get("id")
				.asLong();
		assertNull(jdbc.queryForObject("SELECT sede_id FROM solicitud WHERE id = ?", Long.class, tipo1));
		long encargo = n.encargo(null, "PEDIDO");

		assertEquals(List.of(tipo1), ids(listar("PENDIENTES", n.tPropietario)));
		assertEquals(List.of(encargo), ids(listar("ENCARGOS", n.tPropietario)));
		assertEquals(2L, contador(n.tPropietario));
		assertEquals(List.of(tipo1), ids(listar("PENDIENTES", n.tGestor)));
		assertEquals(List.of(encargo), ids(listar("ENCARGOS", n.tGestor)));
		assertEquals(1L, contador(n.tGestor), "el encargo; su propia petición no espera su acción");
		assertTrue(fila(listar("PENDIENTES", n.tPropietario), tipo1).get("sedeId").isNull());

		assertEquals(List.of(), ids(listar("PENDIENTES", n.tResp)));
		assertEquals(List.of(), ids(listar("ENCARGOS", n.tResp)));
		assertEquals(List.of(), ids(listar("RESUELTAS", n.tResp)));
		assertEquals(0L, contador(n.tResp));
	}

	// ------------------------------------------------------------------ seguridad, registro y aislamiento

	/** Punto de revisión 4: /api/solicitudes/** está fuera de la lista blanca de la sesión solo-profesor. */
	@Test
	void sesionSoloProfesorNoEntraEnSolicitudes() throws Exception {
		long id = leer(crear("REVERTIR_COBRO", "cobroId", cobroActivo(s.cargoCobrado(s.e.aA)), "x"), s.tGestor, 201)
				.get("id").asLong();
		long soloProfesor = s.e.cuenta(usuarios, "solo-prof");
		membresia(soloProfesor, s.e.org, "PROFESOR");
		jdbc.update("INSERT INTO asignacion_profesor (organizacion_id, usuario_id, grupo_id, rol) "
				+ "VALUES (?, ?, ?, 'TITULAR')", s.e.org, soloProfesor, s.e.gA);
		long respYProf = s.e.cuenta(usuarios, "resp-y-prof");
		membresia(respYProf, s.e.org, "RESPONSABLE_SEDE");
		jdbc.update("INSERT INTO membresia_sede (membresia_id, sede_id) SELECT id, ? FROM membresia "
				+ "WHERE usuario_id = ? AND rol = 'RESPONSABLE_SEDE'", s.e.sA, respYProf);
		membresia(respYProf, s.e.org, "PROFESOR");
		long familia = s.e.cuentaFamilia(usuarios, "solo-familia");

		List<String> tokens = List.of(s.e.token(jwtService, usuarios, soloProfesor),
				s.e.token(jwtService, usuarios, respYProf), s.tProf1, s.e.token(jwtService, usuarios, familia));
		assertEquals(200, estado(get("/api/caja/profesor/cargos-pendientes"), tokens.get(0)), "su caja sí");
		for (String token : tokens) {
			for (MockHttpServletRequestBuilder ruta : List.of(get("/api/solicitudes").param("segmento", "PENDIENTES"),
					get("/api/solicitudes/contador"), crear("REVERTIR_COBRO", "cobroId", 1L, "x"),
					post("/api/solicitudes/" + id + "/retirar"), denegar(id, "x"),
					post("/api/solicitudes/" + id + "/aceptar"))) {
				MvcResult r = peticion(ruta, token);
				assertEquals(403, r.getResponse().getStatus(),
						r.getRequest().getMethod() + " " + r.getRequest().getRequestURI());
			}
		}
		assertEquals("PENDIENTE", estadoDe(id));
	}

	@Test
	void registroDeAcciones_soloIds_sinMotivo() throws Exception {
		Escena n = new Escena("it-6c2-reg");
		long cargo = n.cargoCobrado(n.e.aA);
		long cobro = cobroActivo(cargo);
		long primera = leer(crear("REVERTIR_COBRO", "cobroId", cobro, "MOTIVO SECRETO"), n.tGestor, 201).get("id")
				.asLong();
		assertEquals(200, estado(post("/api/solicitudes/" + primera + "/retirar"), n.tGestor));
		long segunda = leer(crear("REVERTIR_COBRO", "cobroId", cobro, "MOTIVO SECRETO 2"), n.tResp, 201).get("id")
				.asLong();
		assertEquals(200, estado(denegar(segunda, "RESPUESTA SECRETA"), n.tPropietario));
		long sinSede = n.cargoPagadoSinCobro(n.e.aNada);
		long tercera = leer(crear("REVERTIR_COBRO", "productoAlumnoId", sinSede, "MOTIVO SECRETO 3"), n.tGestor, 201)
				.get("id").asLong();
		long movimiento = n.movimientoSuelto(n.e.sA);
		long cuarta = leer(crear("ANULAR_MOVIMIENTO", "movimientoId", movimiento, "MOTIVO SECRETO 4"), n.tGestor, 201)
				.get("id").asLong();

		List<Map<String, Object>> eventos = jdbc.queryForList("""
				SELECT * FROM auditoria_evento WHERE organizacion_id = ? AND modulo = 'solicitudes'
				  AND resumen LIKE 'SOLICITUDES %' ORDER BY id""", n.e.org);
		String sede = "sede=#" + n.e.sA;
		assertEvento(eventos.get(0), n.e.gestor, "CREATE", primera,
				"SOLICITUDES SOLICITUD_CREADA " + sede + " solicitud=#" + primera + " cobro=#" + cobro + " cargo=#" + cargo);
		assertEvento(eventos.get(1), n.e.gestor, "UPDATE", primera,
				"SOLICITUDES SOLICITUD_RETIRADA " + sede + " solicitud=#" + primera + " cobro=#" + cobro + " cargo=#"
						+ cargo);
		assertEvento(eventos.get(2), n.e.respA, "CREATE", segunda,
				"SOLICITUDES SOLICITUD_CREADA " + sede + " solicitud=#" + segunda + " cobro=#" + cobro + " cargo=#" + cargo);
		assertEvento(eventos.get(3), n.propietario, "UPDATE", segunda,
				"SOLICITUDES SOLICITUD_DENEGADA " + sede + " solicitud=#" + segunda + " cobro=#" + cobro + " cargo=#"
						+ cargo);
		assertEvento(eventos.get(4), n.e.gestor, "CREATE", tercera,
				"SOLICITUDES SOLICITUD_CREADA sede=- solicitud=#" + tercera + " cargo=#" + sinSede);
		assertEvento(eventos.get(5), n.e.gestor, "CREATE", cuarta,
				"SOLICITUDES SOLICITUD_CREADA " + sede + " solicitud=#" + cuarta + " movimiento=#" + movimiento);
		assertEquals(6, eventos.size());
		for (Map<String, Object> evento : eventos) {
			String resumen = (String) evento.get("resumen");
			assertFalse(resumen.contains("SECRET") || resumen.contains("E6A") || resumen.contains("PRUEBA")
					|| resumen.contains("CAMISETA") || resumen.contains("10.5"), resumen);
			assertNotNull(evento.get("fecha_evento"));
		}
		// Un intento fallido no deja registro.
		assertEquals(409, estado(denegar(segunda, "otra"), n.tPropietario));
		assertEquals(409, estado(crear("REVERTIR_COBRO", "productoAlumnoId", sinSede, "otra"), n.tGestor));
		assertEquals(6L, jdbc.queryForObject("""
				SELECT COUNT(*) FROM auditoria_evento WHERE organizacion_id = ? AND modulo = 'solicitudes'
				  AND resumen LIKE 'SOLICITUDES %'""", Long.class, n.e.org));
	}

	@Test
	void aislamientoDeEscuela() throws Exception {
		long id = leer(crear("REVERTIR_COBRO", "cobroId", cobroActivo(s.cargoCobrado(s.e.aA)), "x"), s.tGestor, 201)
				.get("id").asLong();
		long encargo = s.encargo(s.e.sA, "PEDIDO");
		for (String segmento : List.of("PENDIENTES", "ENCARGOS", "RESUELTAS")) {
			List<Long> vistas = ids(listar(segmento, otra.tPropietario));
			assertFalse(vistas.contains(id) || vistas.contains(encargo), segmento);
		}
		assertEquals(404, estado(post("/api/solicitudes/" + id + "/retirar"), otra.tGestor));
		assertEquals(404, estado(denegar(id, "no"), otra.tPropietario));
		assertEquals("PENDIENTE", estadoDe(id));
		long cuentaOtra = jdbc.queryForObject("SELECT COUNT(*) FROM solicitud WHERE organizacion_id = ? AND ("
				+ "(tipo <> 'ENCARGO' AND estado = 'PENDIENTE') OR (tipo = 'ENCARGO' AND estado IN ('PEDIDO', 'ENCARGADO')))",
				Long.class, otra.e.org);
		assertEquals(cuentaOtra, contador(otra.tPropietario), "solo cuenta lo de su escuela");
	}

	/** El mapeo nuevo de movimiento_caja (V31_8) no cambia la caja: EFECTIVO, sin cobro ni cargo, misma respuesta. */
	@Test
	void movimientoNuevo_quedaEfectivoSinCobroNiCargo_yLaRespuestaNoCambia() throws Exception {
		JsonNode creado = leer(post("/api/caja/sedes/" + s.e.sA + "/movimientos").contentType(MediaType.APPLICATION_JSON)
				.content("{\"tipo\":\"DEVOLUCION\",\"importe\":-2.00,\"alumnoId\":" + s.e.aA + ",\"nota\":\"devuelto\"}"),
				s.tGestor, 201);
		Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM movimiento_caja WHERE id = ?",
				creado.get("id").asLong());
		assertEquals("EFECTIVO", fila.get("metodo"));
		assertNull(fila.get("cobro_id"));
		assertNull(fila.get("producto_alumno_id"));
		assertEquals(Set.of("id", "sedeId", "tipo", "importe", "alumnoId", "entregaCajaId", "nota", "creadoPorUsuarioId",
				"fecha", "estado", "anuladoPorUsuarioId", "anuladoEn", "motivoAnulacion"), campos(creado));
	}

	/** Puerto de {@code @acceso.solicitudEnSede} (la 6c-5 y la 6c-6 lo usan en sus rutas). */
	@Test
	void consultaJpa_solicitudEnSedes_porLaSedeDeLaSolicitud() throws Exception {
		long enA = s.encargo(s.e.sA, "PEDIDO");
		long sinSede = s.encargo(null, "PEDIDO");
		TenantContext.ejecutarCon(s.e.org, () -> {
			assertTrue(consultaAcceso.solicitudEnSedes(enA, Set.of(s.e.sA)));
			assertTrue(consultaAcceso.solicitudEnSedes(enA, Set.of(s.e.sB, s.e.sA)));
			assertFalse(consultaAcceso.solicitudEnSedes(enA, Set.of(s.e.sB)));
			assertFalse(consultaAcceso.solicitudEnSedes(sinSede, Set.of(s.e.sA, s.e.sB)), "sin sede, en ninguna");
			assertFalse(consultaAcceso.solicitudEnSedes(enA, Set.of()));
			assertFalse(consultaAcceso.solicitudEnSedes(null, Set.of(s.e.sA)));
		});
		TenantContext.ejecutarCon(otra.e.org, () -> assertFalse(consultaAcceso.solicitudEnSedes(enA, Set.of(s.e.sA)),
				"de otra escuela"));
	}

	// ------------------------------------------------------------------ utilidades

	private void assertEvento(Map<String, Object> evento, long usuario, String accion, long recurso, String resumen) {
		assertEquals(usuario, numero(evento, "usuario_id"));
		assertEquals(accion, evento.get("accion"));
		assertEquals(recurso, numero(evento, "recurso_id"));
		assertEquals(resumen, evento.get("resumen"));
	}

	private void membresia(long usuario, long organizacion, String rol) {
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, ?)", usuario, organizacion,
				rol);
	}

	private long cobroActivo(long cargo) {
		return jdbc.queryForObject("SELECT id FROM cobro WHERE producto_alumno_id = ? AND estado = 'ACTIVO'", Long.class,
				cargo);
	}

	private long contarSolicitudesDeCobro(long cobro) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM solicitud WHERE cobro_id = ?", Long.class, cobro);
	}

	private long contarSolicitudes(Escena x) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM solicitud WHERE organizacion_id = ?", Long.class, x.e.org);
	}

	private String estadoDe(long solicitud) {
		return jdbc.queryForObject("SELECT estado FROM solicitud WHERE id = ?", String.class, solicitud);
	}

	private boolean pagado(long cargo) {
		return Boolean.TRUE.equals(
				jdbc.queryForObject("SELECT pagado FROM producto_alumno WHERE id = ?", Boolean.class, cargo));
	}

	private MockHttpServletRequestBuilder crear(String tipo, String campo, Long recurso, String motivo) throws Exception {
		Map<String, Object> cuerpo = new HashMap<>();
		cuerpo.put("tipo", tipo);
		cuerpo.put(campo, recurso);
		cuerpo.put("motivo", motivo);
		return cuerpo(cuerpo);
	}

	private MockHttpServletRequestBuilder cuerpo(Map<String, Object> cuerpo) throws Exception {
		return post("/api/solicitudes").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo));
	}

	private MockHttpServletRequestBuilder denegar(long id, String respuesta) throws Exception {
		return post("/api/solicitudes/" + id + "/denegar").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("respuesta", respuesta)));
	}

	private JsonNode listar(String segmento, String token) throws Exception {
		return leer(get("/api/solicitudes").param("segmento", segmento), token, 200);
	}

	private long contador(String token) throws Exception {
		JsonNode r = leer(get("/api/solicitudes/contador"), token, 200);
		assertEquals(Set.of("total"), campos(r));
		return r.get("total").asLong();
	}

	private static MockHttpServletRequestBuilder pago(long cargo, String cuerpo) {
		return put("/api/productos-alumno/" + cargo).contentType(MediaType.APPLICATION_JSON).content(cuerpo);
	}

	private JsonNode leer(MockHttpServletRequestBuilder peticion, String token, int esperado) throws Exception {
		MvcResult r = peticion(peticion, token);
		assertEquals(esperado, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		return json.readTree(r.getResponse().getContentAsString());
	}

	private int estado(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		return peticion(peticion, token).getResponse().getStatus();
	}

	private MvcResult peticion(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		return mvc.perform(peticion.cookie(new Cookie("jwt", token))).andReturn();
	}

	private String mensaje(MvcResult r) throws Exception {
		return json.readTree(r.getResponse().getContentAsString()).get("mensaje").asText();
	}

	private static List<Long> ids(JsonNode lista) {
		List<Long> ids = new ArrayList<>();
		lista.forEach(n -> ids.add(n.get("id").asLong()));
		return ids;
	}

	private static JsonNode fila(JsonNode lista, long id) {
		for (JsonNode n : lista) {
			if (n.get("id").asLong() == id) {
				return n;
			}
		}
		throw new AssertionError("Sin fila con id=" + id + ": " + lista);
	}

	private static Set<String> campos(JsonNode nodo) {
		Set<String> campos = new HashSet<>();
		for (Iterator<String> it = nodo.fieldNames(); it.hasNext();) {
			campos.add(it.next());
		}
		return campos;
	}

	private static long numero(Map<String, Object> fila, String columna) {
		return ((Number) fila.get(columna)).longValue();
	}

	/** Las dos a la vez; los resultados en el orden de las acciones. */
	private static List<MvcResult> enParalelo(Callable<MvcResult> primera, Callable<MvcResult> segunda)
			throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch preparados = new CountDownLatch(2);
		CountDownLatch salida = new CountDownLatch(1);
		try {
			List<Future<MvcResult>> futuros = new ArrayList<>();
			for (Callable<MvcResult> accion : List.of(primera, segunda)) {
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
			List<MvcResult> resultados = new ArrayList<>();
			for (Future<MvcResult> futuro : futuros) {
				resultados.add(futuro.get(60, TimeUnit.SECONDS));
			}
			return resultados;
		} finally {
			salida.countDown();
			executor.shutdownNow();
		}
	}
}
