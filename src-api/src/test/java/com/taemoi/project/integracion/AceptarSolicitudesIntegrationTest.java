package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.GrupoService;
import com.taemoi.project.services.JwtService;

import jakarta.servlet.http.Cookie;

/**
 * Pieza 6c-3 contra MySQL: aceptar las solicitudes al propietario ({@code POST /api/solicitudes/{id}/aceptar}). Por
 * tipo, la acción de siempre con la identidad del propietario (A1, la 193, A7 y A9) y, si el estado cambió entre pedir
 * y aceptar, {@code NO_APLICABLE} sin efectos. En cada caso, el saldo de caja de la sede antes y después. El tipo 2 aún
 * no se pide por la API (6c-4): se inserta por SQL. Escuela sintética de sedes A y B ({@link EscuelaConSedesDePrueba});
 * datos inventados.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AceptarSolicitudesIntegrationTest extends MySqlIntegrationTest {

	private static final String NO_PENDIENTE = "La solicitud ya no está pendiente";
	private static final BigDecimal IMPORTE = new BigDecimal("10.50");

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;

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
					+ "('CAMISETA 6C3', 10.5, NULL, ?, TRUE)", e.org);
			producto = jdbc.queryForObject("SELECT MAX(id) FROM producto WHERE organizacion_id = ?", Long.class, e.org);
			aProfA = e.alumno("E6C3PROFA" + e.marca, e.sA, true);
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

		/** El profesor cobra en efectivo un cargo nuevo de su alumno (sin entregar): devuelve el cobro. */
		long cobroDelProfesor() throws Exception {
			long cargo = cargo(aProfA);
			MvcResult r = peticion(post("/api/caja/profesor/cargos/" + cargo + "/cobrar"), tProf1);
			assertEquals(201, r.getResponse().getStatus(), r.getResponse().getContentAsString());
			return json.readTree(r.getResponse().getContentAsString()).get("cobroId").asLong();
		}

		/** El profesor apunta y cobra el producto (su venta, decisión 172): devuelve el cobro. */
		long ventaDelProfesor() throws Exception {
			MvcResult r = peticion(post("/api/caja/profesor/alumnos/" + aProfA + "/productos/" + producto + "/cobrar"),
					tProf1);
			assertEquals(201, r.getResponse().getStatus(), r.getResponse().getContentAsString());
			return json.readTree(r.getResponse().getContentAsString()).get("cobroId").asLong();
		}

		/** El gestor recibe del profesor la entrega del cobro, contada como se indica. */
		JsonNode entregar(long cobro, String contado) throws Exception {
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

		/** Pide (gestor) y devuelve el id de la solicitud. */
		long pedir(String tipo, String campo, long recurso, String motivo) throws Exception {
			return leer(crear(tipo, campo, recurso, motivo), tGestor, 201).get("id").asLong();
		}

		/** Tipo 2 insertado por SQL (la ruta del profesor es de la 6c-4), pedido por el profesor que cobró. */
		long deshacerEntregado(long cobro, String motivoDeshacer) {
			jdbc.update("""
					INSERT INTO solicitud (organizacion_id, sede_id, tipo, estado, solicitado_por_usuario_id, solicitado_en,
					  motivo, cobro_id, producto_alumno_id, alumno_id, concepto, importe, motivo_deshacer)
					SELECT organizacion_id, sede_id, 'DESHACER_COBRO_ENTREGADO', 'PENDIENTE', ?, NOW(6), 'No pagó al final',
					  id, producto_alumno_id, alumno_id, concepto, importe, ?
					FROM cobro WHERE id = ?""", prof1, motivoDeshacer, cobro);
			return jdbc.queryForObject("SELECT MAX(id) FROM solicitud WHERE organizacion_id = ?", Long.class, e.org);
		}

		BigDecimal saldo(long sede) throws Exception {
			return leer(get("/api/caja/sedes/" + sede + "/resumen"), tPropietario, 200).get("saldoCaja").decimalValue();
		}

		/** La entrega tal como sale en el historial de la sede. */
		JsonNode entregaDelHistorial(long entrega) throws Exception {
			JsonNode pagina = leer(get("/api/caja/sedes/" + e.sA + "/entregas").param("size", "100"), tPropietario,
					200);
			for (JsonNode n : pagina.get("content")) {
				if (n.get("id").asLong() == entrega) {
					return n;
				}
			}
			throw new AssertionError("La entrega " + entrega + " no está en el historial: " + pagina);
		}

		JsonNode aceptar(long solicitud) throws Exception {
			return leer(post("/api/solicitudes/" + solicitud + "/aceptar"), tPropietario, 200);
		}

		long inscripcionConCargo(long alumno) {
			jdbc.update("INSERT INTO convocatoria (fecha_convocatoria, organizacion_id, disciplina_id) VALUES "
					+ "('2026-06-01', ?, ?)", e.org, e.disciplina);
			long convocatoria = jdbc.queryForObject("SELECT MAX(id) FROM convocatoria WHERE organizacion_id = ?",
					Long.class, e.org);
			jdbc.update("""
					INSERT INTO producto_alumno (alumno_id, concepto, precio, cantidad, pagado, fecha_asignacion,
					  organizacion_id, sede_id)
					VALUES (?, 'DERECHO A EXAMEN 6C3', 20.0, 1, 0, NOW(), ?,
					  (SELECT sede_principal_id FROM alumno WHERE id = ?))""", alumno, e.org, alumno);
			long cargo = jdbc.queryForObject("SELECT MAX(id) FROM producto_alumno WHERE organizacion_id = ?",
					Long.class, e.org);
			jdbc.update("""
					INSERT INTO alumno_convocatoria (cuantia_examen, pagado, alumno_id, convocatoria_id, grado_actual,
					  grado_siguiente, organizacion_id, producto_alumno_id) VALUES (20, 0, ?, ?, NULL, NULL, ?, ?)""",
					alumno, convocatoria, e.org, cargo);
			return jdbc.queryForObject(
					"SELECT id FROM alumno_convocatoria WHERE alumno_id = ? AND convocatoria_id = ?", Long.class, alumno,
					convocatoria);
		}
	}

	Escena s;
	Escena otra;

	@BeforeAll
	void preparar() {
		s = new Escena("it-6c3-acep");
		otra = new Escena("it-6c3-otra");
	}

	@AfterEach
	void sinFugas() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
		SecurityContextHolder.clearContext();
	}

	// ------------------------------------------------------------------ tipo 1: revertir un cobro

	@Test
	void aceptarRevertirCobro_anulaElCobroYDesmarca_comoA1() throws Exception {
		long cargo = s.cargoCobrado(s.e.aA);
		long cobro = cobroActivo(cargo);
		long id = s.pedir("REVERTIR_COBRO", "cobroId", cobro, "Se cobró dos veces");
		BigDecimal saldo = s.saldo(s.e.sA);

		JsonNode aceptada = s.aceptar(id);
		assertEquals("ACEPTADA", aceptada.get("estado").asText());
		assertTrue(aceptada.get("respuesta").isNull());
		assertEquals(s.propietario, aceptada.get("resueltoPorUsuarioId").asLong());
		assertNotNull(aceptada.get("resueltoEn").asText());

		Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM solicitud WHERE id = ?", id);
		assertEquals("ACEPTADA", fila.get("estado"));
		assertEquals(s.propietario, numero(fila, "resuelto_por_usuario_id"));
		assertNull(fila.get("respuesta"));
		Map<String, Object> c = jdbc.queryForMap("SELECT * FROM cobro WHERE id = ?", cobro);
		assertEquals("ANULADO", c.get("estado"));
		assertEquals(s.propietario, numero(c, "anulado_por_usuario_id"));
		assertEquals("Revertido por solicitud #" + id + ": Se cobró dos veces", c.get("motivo_anulacion"));
		assertFalse(pagado(cargo));
		assertNull(jdbc.queryForObject("SELECT fecha_pago FROM producto_alumno WHERE id = ?", Object.class, cargo));
		assertEquals(0, saldo.subtract(IMPORTE).compareTo(s.saldo(s.e.sA)), "lo mismo que deja A1: −10,50");
	}

	@Test
	void aceptarRevertirExamen_sincronizaConvocatoriaYDerechoAExamen() throws Exception {
		long inscripcion = s.inscripcionConCargo(s.e.aA);
		long cargo = jdbc.queryForObject("SELECT producto_alumno_id FROM alumno_convocatoria WHERE id = ?", Long.class,
				inscripcion);
		assertEquals(200, estado(pago(cargo, "{\"pagado\":true,\"metodoPago\":\"EFECTIVO\"}"), s.tGestor));
		assertEquals(Boolean.TRUE, jdbc.queryForObject("SELECT pagado FROM alumno_convocatoria WHERE id = ?",
				Boolean.class, inscripcion));
		jdbc.update("UPDATE alumno SET tiene_derecho_examen = 1 WHERE id = ?", s.e.aA);
		long id = s.pedir("REVERTIR_COBRO", "productoAlumnoId", cargo, "Examen pagado por error");
		BigDecimal saldo = s.saldo(s.e.sA);

		assertEquals("ACEPTADA", s.aceptar(id).get("estado").asText());
		assertFalse(pagado(cargo));
		assertEquals(Boolean.FALSE, jdbc.queryForObject("SELECT pagado FROM alumno_convocatoria WHERE id = ?",
				Boolean.class, inscripcion), "la inscripción se sincroniza como en A1");
		assertEquals(Boolean.FALSE, jdbc.queryForObject("SELECT tiene_derecho_examen FROM alumno WHERE id = ?",
				Boolean.class, s.e.aA), "y el derecho a examen");
		assertEquals(0L, jdbc.queryForObject(
				"SELECT COUNT(*) FROM cobro WHERE producto_alumno_id = ? AND estado = 'ACTIVO'", Long.class, cargo));
		assertEquals(0, saldo.subtract(new BigDecimal("20.00")).compareTo(s.saldo(s.e.sA)), "lo que deja A1");
	}

	/** Punto de revisión 2: si el cobro se entrega entre pedir y aceptar, NO_APLICABLE y nada más. */
	@Test
	void aceptarTrasEntregarElCobro_NO_APLICABLE_sinEfectos() throws Exception {
		long cobro = s.cobroDelProfesor();
		long cargo = cargoDe(cobro);
		long id = s.pedir("REVERTIR_COBRO", "cobroId", cobro, "Lo cobró dos veces");
		long entrega = s.entregar(cobro, "10.50").get("id").asLong();
		BigDecimal saldo = s.saldo(s.e.sA);

		JsonNode r = s.aceptar(id);
		assertEquals("NO_APLICABLE", r.get("estado").asText());
		assertEquals("El cobro ya va en una entrega de caja.", r.get("respuesta").asText());
		assertEquals(s.propietario, r.get("resueltoPorUsuarioId").asLong());
		Map<String, Object> c = jdbc.queryForMap("SELECT * FROM cobro WHERE id = ?", cobro);
		assertEquals("ACTIVO", c.get("estado"));
		assertEquals(entrega, numero(c, "entrega_caja_id"));
		assertTrue(pagado(cargo));
		assertEquals(0, saldo.compareTo(s.saldo(s.e.sA)));
		assertEquals(409, estado(post("/api/solicitudes/" + id + "/aceptar"), s.tPropietario), "ya resuelta");
	}

	@Test
	void aceptarTrasCambiarElEstado_NO_APLICABLE_porTipo() throws Exception {
		// Tipo 1 con cobro: el propietario lo revierte antes por el PUT.
		long cargo = s.cargoCobrado(s.e.aA);
		long cobro = cobroActivo(cargo);
		long conCobro = s.pedir("REVERTIR_COBRO", "cobroId", cobro, "x");
		assertEquals(200, estado(pago(cargo, "{\"pagado\":false}"), s.tPropietario));
		noAplicableSinEfectos(conCobro, "El cobro ya no está activo.");
		assertFalse(pagado(cargo));

		// Tipo 1 sin cobro (191): el propietario lo desmarca antes.
		long sinCobro = s.cargoPagadoSinCobro(s.e.aA);
		long desmarcado = s.pedir("REVERTIR_COBRO", "productoAlumnoId", sinCobro, "x");
		assertEquals(200, estado(pago(sinCobro, "{\"pagado\":false}"), s.tPropietario));
		noAplicableSinEfectos(desmarcado, "El cargo ya no está pagado.");

		// Tipo 1 sin cobro: desmarcado y cobrado otra vez (ahora tiene cobro): no se revierte ese cobro.
		long recobrado = s.cargoPagadoSinCobro(s.e.aA);
		long otraVez = s.pedir("REVERTIR_COBRO", "productoAlumnoId", recobrado, "x");
		assertEquals(200, estado(pago(recobrado, "{\"pagado\":false}"), s.tPropietario));
		assertEquals(200, estado(pago(recobrado, "{\"pagado\":true,\"metodoPago\":\"EFECTIVO\"}"), s.tPropietario));
		noAplicableSinEfectos(otraVez, "El cargo tiene ahora un cobro activo: pide revertir ese cobro.");
		assertEquals("ACTIVO", jdbc.queryForObject("SELECT estado FROM cobro WHERE id = ?", String.class,
				cobroActivo(recobrado)));

		// Tipo 3, entrega: el propietario la anula antes.
		long entrega = s.entregar(s.cobroDelProfesor(), "10.50").get("id").asLong();
		long anularEntrega = s.pedir("ANULAR_ENTREGA", "entregaId", entrega, "x");
		assertEquals(200, estado(anulacion("/api/caja/entregas/" + entrega + "/anular"), s.tPropietario));
		noAplicableSinEfectos(anularEntrega, "La entrega ya está anulada.");

		// Tipo 3, movimiento: el propietario lo anula antes.
		long movimiento = s.movimientoSuelto(s.e.sA);
		long anularMovimiento = s.pedir("ANULAR_MOVIMIENTO", "movimientoId", movimiento, "x");
		assertEquals(200, estado(anulacion("/api/caja/movimientos/" + movimiento + "/anular"), s.tPropietario));
		noAplicableSinEfectos(anularMovimiento, "El movimiento ya está anulado.");
		assertEquals("x", jdbc.queryForObject("SELECT motivo_anulacion FROM movimiento_caja WHERE id = ?",
				String.class, movimiento), "la anulación es la del propietario, no otra");
	}

	/** Una PENDIENTE cuyo cobro se anuló al borrar el alumno (y con él el cargo): NO_APLICABLE al aceptarla. */
	@Test
	void aceptarConCobroAnuladoPorBorrado_NO_APLICABLE_sinEfectos() throws Exception {
		long alumno = s.e.alumno("E6C3BORRA" + s.e.marca, s.e.sA, true);
		long cargo = s.cargoCobrado(alumno);
		long cobro = cobroActivo(cargo);
		long id = s.pedir("REVERTIR_COBRO", "cobroId", cobro, "x");
		assertEquals(200, estado(delete("/api/alumnos/" + alumno), s.tPropietario));
		Map<String, Object> antes = jdbc.queryForMap("SELECT * FROM cobro WHERE id = ?", cobro);
		assertEquals("ANULADO", antes.get("estado"));
		assertNull(jdbc.queryForObject("SELECT producto_alumno_id FROM solicitud WHERE id = ?", Long.class, id));

		noAplicableSinEfectos(id, "El cargo ya no existe.");
		assertEquals(antes, jdbc.queryForMap("SELECT * FROM cobro WHERE id = ?", cobro), "el cobro no cambia");
	}

	@Test
	void aceptarConDevolucion_NO_APLICABLE() throws Exception {
		long cargo = s.cargoCobrado(s.e.aA);
		long cobro = cobroActivo(cargo);
		long id = s.pedir("REVERTIR_COBRO", "cobroId", cobro, "x");
		jdbc.update("""
				INSERT INTO movimiento_caja (organizacion_id, sede_id, tipo, importe, alumno_id, nota,
				  creado_por_usuario_id, fecha, estado, cobro_id, producto_alumno_id)
				VALUES (?, ?, 'DEVOLUCION', -10.50, ?, 'devuelto', ?, NOW(6), 'ACTIVO', ?, ?)""", s.e.org, s.e.sA,
				s.e.aA, s.e.gestor, cobro, cargo);
		noAplicableSinEfectos(id, "El cobro tiene una devolución registrada.");
		assertEquals("ACTIVO", jdbc.queryForObject("SELECT estado FROM cobro WHERE id = ?", String.class, cobro));

		long cargo2 = s.cargoCobrado(s.e.aA);
		long cobro2 = cobroActivo(cargo2);
		long id2 = s.pedir("REVERTIR_COBRO", "cobroId", cobro2, "x");
		jdbc.update("""
				INSERT INTO solicitud (organizacion_id, sede_id, tipo, estado, solicitado_por_usuario_id, solicitado_en,
				  motivo, producto_id, producto_alumno_id, alumno_id, concepto, importe, devolucion_pendiente)
				VALUES (?, ?, 'ENCARGO', 'RECHAZADO', ?, NOW(6), 'talla M', ?, ?, ?, 'CAMISETA 6C3', 10.50, TRUE)""",
				s.e.org, s.e.sA, s.prof1, s.producto, cargo2, s.e.aA);
		noAplicableSinEfectos(id2, "El encargo tiene una devolución pendiente: regístrala antes.");
		assertEquals("ACTIVO", jdbc.queryForObject("SELECT estado FROM cobro WHERE id = ?", String.class, cobro2));
		assertTrue(pagado(cargo2));
	}

	// ------------------------------------------------------------------ tipo 2: deshacer un cobro entregado (193)

	@Test
	void aceptarDeshacerEntregado_anulaElCobroYApuntaErrorEnLaEntrega_saldoIgual() throws Exception {
		long cobro = s.cobroDelProfesor();
		long cargo = cargoDe(cobro);
		long entrega = s.entregar(cobro, "10.50").get("id").asLong();
		long id = s.deshacerEntregado(cobro, "AUN_NO_PAGADO");
		BigDecimal saldo = s.saldo(s.e.sA);

		assertEquals("ACEPTADA", s.aceptar(id).get("estado").asText());
		Map<String, Object> c = jdbc.queryForMap("SELECT * FROM cobro WHERE id = ?", cobro);
		assertEquals("ANULADO", c.get("estado"));
		assertEquals(s.propietario, numero(c, "anulado_por_usuario_id"));
		assertEquals("Deshecho por solicitud #" + id + ": No pagó al final", c.get("motivo_anulacion"));
		assertNull(c.get("entrega_caja_id"), "sale de la entrega: ningún cobro ANULADO en una entrega ACTIVA (6b-2)");
		assertEquals(0L, jdbc.queryForObject("""
				SELECT COUNT(*) FROM cobro c JOIN entrega_caja e ON e.id = c.entrega_caja_id
				WHERE c.estado = 'ANULADO' AND e.estado = 'ACTIVA' AND c.organizacion_id = ?""", Long.class, s.e.org));
		List<Map<String, Object>> movs = jdbc.queryForList("SELECT * FROM movimiento_caja WHERE entrega_caja_id = ?",
				entrega);
		assertEquals(1, movs.size(), "contado = declarado: solo el ERROR");
		Map<String, Object> error = movs.get(0);
		assertEquals("ERROR", error.get("tipo"));
		assertEquals("ACTIVO", error.get("estado"));
		assertEquals(0, IMPORTE.compareTo((BigDecimal) error.get("importe")));
		assertEquals(cobro, numero(error, "cobro_id"));
		assertEquals(cargo, numero(error, "producto_alumno_id"));
		assertEquals(s.aProfA, numero(error, "alumno_id"));
		assertEquals(s.propietario, numero(error, "creado_por_usuario_id"));
		assertEquals("EFECTIVO", error.get("metodo"));
		assertEquals("ACTIVA", jdbc.queryForObject("SELECT estado FROM entrega_caja WHERE id = ?", String.class,
				entrega), "la entrega no se anula");
		JsonNode enHistorial = s.entregaDelHistorial(entrega);
		assertTrue(enHistorial.get("movimientoId").isNull(), "el ERROR no es la diferencia");
		assertEquals(0, enHistorial.get("cobroIds").size(), "el rastro queda en el ERROR (cobro_id)");
		assertFalse(pagado(cargo), "el cargo vuelve a pendiente");
		assertNull(jdbc.queryForObject("SELECT cancelado_en FROM producto_alumno WHERE id = ?", Object.class, cargo));
		assertEquals(0, saldo.compareTo(s.saldo(s.e.sA)), "anulado + ERROR: el saldo no cambia");
		long errorId = numero(error, "id");
		assertEquals(1L, jdbc.queryForObject("""
				SELECT COUNT(*) FROM auditoria_evento WHERE organizacion_id = ? AND modulo = 'caja' AND resumen = ?""",
				Long.class, s.e.org, "CAJA COBRO_ENTREGADO_DESHECHO sede=#" + s.e.sA + " cobro=#" + cobro + " cargo=#"
						+ cargo + " entrega=#" + entrega + " movimiento=#" + errorId + " solicitud=#" + id));

		// El ERROR va con su entrega: no se pide anularlo suelto, y el aviso no lo llama «diferencia automática».
		MvcResult suelto = peticion(crear("ANULAR_MOVIMIENTO", "movimientoId", errorId, "x"), s.tGestor);
		assertEquals(409, suelto.getResponse().getStatus());
		assertEquals("Es el ERROR de un cobro deshecho en una entrega: se anula con la entrega; pide anular la entrega.",
				mensaje(suelto));

		// Anular después la entrega anula también el ERROR: queda lo que deja anular una entrega sin ese cobro.
		assertEquals(200, estado(anulacion("/api/caja/entregas/" + entrega + "/anular"), s.tPropietario));
		assertEquals("ANULADO", jdbc.queryForObject("SELECT estado FROM movimiento_caja WHERE id = ?", String.class,
				errorId));
		assertEquals("ANULADO", jdbc.queryForObject("SELECT estado FROM cobro WHERE id = ?", String.class, cobro));
		assertEquals(0, saldo.subtract(IMPORTE).compareTo(s.saldo(s.e.sA)));
	}

	@Test
	void aceptarDeshacerEntregado_noHuboVenta_cancelaElCargo() throws Exception {
		long cobro = s.ventaDelProfesor();
		long cargo = cargoDe(cobro);
		s.entregar(cobro, "10.50");
		long id = s.deshacerEntregado(cobro, "NO_HUBO_VENTA");
		BigDecimal saldo = s.saldo(s.e.sA);

		assertEquals("ACEPTADA", s.aceptar(id).get("estado").asText());
		Map<String, Object> pa = jdbc.queryForMap("SELECT * FROM producto_alumno WHERE id = ?", cargo);
		assertEquals(Boolean.FALSE, pa.get("pagado"));
		assertNotNull(pa.get("cancelado_en"), "no hubo venta: el cargo se cancela");
		assertEquals(s.propietario, numero(pa, "cancelado_por_usuario_id"));
		assertEquals("no hubo venta (solicitud #" + id + ")", pa.get("motivo_cancelacion"));
		assertEquals(0L, jdbc.queryForObject(
				"SELECT COUNT(*) FROM cobro WHERE producto_alumno_id = ? AND estado = 'ACTIVO'", Long.class, cargo));
		assertEquals(0, saldo.compareTo(s.saldo(s.e.sA)));
		assertEquals(1L, jdbc.queryForObject("""
				SELECT COUNT(*) FROM auditoria_evento WHERE organizacion_id = ? AND resumen = ?""", Long.class, s.e.org,
				"CARGO CARGO_CANCELADO sede=#" + s.e.sA + " cargo=#" + cargo + " solicitud=#" + id));
	}

	@Test
	void aceptarDeshacerEntregado_estadoCambiado_NO_APLICABLE() throws Exception {
		long cobro = s.cobroDelProfesor();
		long entrega = s.entregar(cobro, "10.50").get("id").asLong();
		long id = s.deshacerEntregado(cobro, "AUN_NO_PAGADO");
		assertEquals(200, estado(anulacion("/api/caja/entregas/" + entrega + "/anular"), s.tPropietario));
		noAplicableSinEfectos(id, "El cobro ya no va en una entrega: el profesor puede deshacerlo él mismo.");
		assertEquals("ACTIVO", jdbc.queryForObject("SELECT estado FROM cobro WHERE id = ?", String.class, cobro));

		// NO_HUBO_VENTA de un cargo que no es su venta (lo creó la gestión y el profesor solo lo cobró).
		long otro = s.cobroDelProfesor();
		long otraEntrega = s.entregar(otro, "10.50").get("id").asLong();
		long id2 = s.deshacerEntregado(otro, "NO_HUBO_VENTA");
		noAplicableSinEfectos(id2, "No fue una venta suya: no se deshace como «no hubo venta».");
		assertEquals("ACTIVO", jdbc.queryForObject("SELECT estado FROM cobro WHERE id = ?", String.class, otro));
		assertEquals(0L, jdbc.queryForObject(
				"SELECT COUNT(*) FROM movimiento_caja WHERE entrega_caja_id = ? AND tipo = 'ERROR'", Long.class,
				otraEntrega));
		assertTrue(pagado(cargoDe(otro)));
	}

	// ------------------------------------------------------------------ tipo 3: anular una entrega o un movimiento

	@Test
	void aceptarAnularEntrega_comoA7_conLaSolicitudEnElRegistro() throws Exception {
		long cobro = s.cobroDelProfesor();
		JsonNode entregaJson = s.entregar(cobro, "11.00");
		long entrega = entregaJson.get("id").asLong();
		long sobrante = entregaJson.get("movimientoId").asLong();
		long id = s.pedir("ANULAR_ENTREGA", "entregaId", entrega, "Sede equivocada");
		BigDecimal saldo = s.saldo(s.e.sA);

		assertEquals("ACEPTADA", s.aceptar(id).get("estado").asText());
		Map<String, Object> e = jdbc.queryForMap("SELECT * FROM entrega_caja WHERE id = ?", entrega);
		assertEquals("ANULADA", e.get("estado"));
		assertEquals(s.propietario, numero(e, "anulada_por_usuario_id"));
		assertEquals("Solicitud #" + id + ": Sede equivocada", e.get("motivo_anulacion"));
		Map<String, Object> c = jdbc.queryForMap("SELECT * FROM cobro WHERE id = ?", cobro);
		assertEquals("ACTIVO", c.get("estado"));
		assertNull(c.get("entrega_caja_id"), "vuelve a pendiente de entrega");
		assertEquals("ANULADO", jdbc.queryForObject("SELECT estado FROM movimiento_caja WHERE id = ?", String.class,
				sobrante));
		assertEquals(0, saldo.subtract(new BigDecimal("11.00")).compareTo(s.saldo(s.e.sA)), "lo que deja A7");
		assertEquals(1L, jdbc.queryForObject("""
				SELECT COUNT(*) FROM auditoria_evento WHERE organizacion_id = ? AND modulo = 'caja' AND resumen = ?""",
				Long.class, s.e.org, "CAJA ENTREGA_ANULADA sede=#" + s.e.sA + " entrega=#" + entrega + " cobros=[#"
						+ cobro + "] movimientos=[#" + sobrante + "] solicitud=#" + id));
	}

	@Test
	void aceptarAnularMovimiento_comoA9() throws Exception {
		long movimiento = s.movimientoSuelto(s.e.sA);
		long id = s.pedir("ANULAR_MOVIMIENTO", "movimientoId", movimiento, "Apuntado dos veces");
		BigDecimal saldo = s.saldo(s.e.sA);

		assertEquals("ACEPTADA", s.aceptar(id).get("estado").asText());
		Map<String, Object> m = jdbc.queryForMap("SELECT * FROM movimiento_caja WHERE id = ?", movimiento);
		assertEquals("ANULADO", m.get("estado"));
		assertEquals(s.propietario, numero(m, "anulado_por_usuario_id"));
		assertEquals("Solicitud #" + id + ": Apuntado dos veces", m.get("motivo_anulacion"));
		assertEquals(0, saldo.subtract(BigDecimal.ONE).compareTo(s.saldo(s.e.sA)), "lo que deja A9");
		assertEquals(1L, jdbc.queryForObject("""
				SELECT COUNT(*) FROM auditoria_evento WHERE organizacion_id = ? AND modulo = 'caja' AND resumen = ?""",
				Long.class, s.e.org,
				"CAJA MOVIMIENTO_ANULADO sede=#" + s.e.sA + " movimiento=#" + movimiento + " solicitud=#" + id));
	}

	// ------------------------------------------------------------------ concurrencia, permisos y registro

	/** Punto de revisión 3: aceptar y retirar a la vez; uno gana y el otro recibe 409 «ya no está pendiente». */
	@Test
	void aceptarYRetirarALaVez_unoGana409ElOtro() throws Exception {
		for (int i = 0; i < 3; i++) {
			long cargo = s.cargoCobrado(s.e.aA);
			long cobro = cobroActivo(cargo);
			long id = s.pedir("REVERTIR_COBRO", "cobroId", cobro, "a la vez");
			List<MvcResult> resultados = enParalelo(
					() -> peticion(post("/api/solicitudes/" + id + "/aceptar"), s.tPropietario),
					() -> peticion(post("/api/solicitudes/" + id + "/retirar"), s.tGestor));
			List<Integer> estados = new ArrayList<>();
			resultados.forEach(r -> estados.add(r.getResponse().getStatus()));
			assertEquals(List.of(200, 409), estados.stream().sorted().toList());
			MvcResult perdedor = resultados.stream().filter(r -> r.getResponse().getStatus() == 409).findFirst()
					.orElseThrow();
			assertEquals(NO_PENDIENTE, mensaje(perdedor));
			String estadoFinal = estadoDe(id);
			String estadoCobro = jdbc.queryForObject("SELECT estado FROM cobro WHERE id = ?", String.class, cobro);
			if ("ACEPTADA".equals(estadoFinal)) {
				assertEquals("ANULADO", estadoCobro);
				assertFalse(pagado(cargo));
			} else {
				assertEquals("RETIRADA", estadoFinal);
				assertEquals("ACTIVO", estadoCobro);
				assertTrue(pagado(cargo));
			}
		}
	}

	@Test
	void aceptar_soloPropietario_unaVez() throws Exception {
		long cargo = s.cargoCobrado(s.e.aA);
		long cobro = cobroActivo(cargo);
		long id = s.pedir("REVERTIR_COBRO", "cobroId", cobro, "x");
		long eventos = eventosSolicitudes(s);

		long soloProfesor = s.e.cuenta(usuarios, "solo-prof");
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, 'PROFESOR')", soloProfesor,
				s.e.org);
		for (String token : List.of(s.tGestor, s.tResp, s.tProf1, s.e.token(jwtService, usuarios, soloProfesor))) {
			assertEquals(403, estado(post("/api/solicitudes/" + id + "/aceptar"), token));
		}
		assertEquals(404, estado(post("/api/solicitudes/" + id + "/aceptar"), otra.tPropietario), "otra escuela");
		assertEquals(404, estado(post("/api/solicitudes/99999999/aceptar"), s.tPropietario));
		assertEquals("PENDIENTE", estadoDe(id));
		assertEquals("ACTIVO", jdbc.queryForObject("SELECT estado FROM cobro WHERE id = ?", String.class, cobro));
		assertEquals(eventos, eventosSolicitudes(s), "los rechazos no dejan registro");

		assertEquals("ACEPTADA", s.aceptar(id).get("estado").asText());
		MvcResult otraVez = peticion(post("/api/solicitudes/" + id + "/aceptar"), s.tPropietario);
		assertEquals(409, otraVez.getResponse().getStatus());
		assertEquals(NO_PENDIENTE, mensaje(otraVez));

		long denegada = s.pedir("REVERTIR_COBRO", "cobroId", cobroActivo(s.cargoCobrado(s.e.aA)), "y");
		assertEquals(200, estado(post("/api/solicitudes/" + denegada + "/denegar")
				.contentType(MediaType.APPLICATION_JSON).content("{\"respuesta\":\"no\"}"), s.tPropietario));
		assertEquals(409, estado(post("/api/solicitudes/" + denegada + "/aceptar"), s.tPropietario));

		jdbc.update("""
				INSERT INTO solicitud (organizacion_id, sede_id, tipo, estado, solicitado_por_usuario_id, solicitado_en,
				  motivo, producto_id, alumno_id, concepto, importe)
				VALUES (?, ?, 'ENCARGO', 'PEDIDO', ?, NOW(6), 'talla M', ?, ?, 'CAMISETA 6C3', 10.50)""", s.e.org, s.e.sA,
				s.prof1, s.producto, s.aProfA);
		long encargo = jdbc.queryForObject("SELECT MAX(id) FROM solicitud WHERE organizacion_id = ?", Long.class,
				s.e.org);
		assertEquals(400, estado(post("/api/solicitudes/" + encargo + "/aceptar"), s.tPropietario));
		assertEquals("PEDIDO", estadoDe(encargo));
	}

	@Test
	void registro_aceptadaYNoAplicable_soloIds() throws Exception {
		Escena n = new Escena("it-6c3-reg");
		long cargo = n.cargoCobrado(n.e.aA);
		long cobro = cobroActivo(cargo);
		long aceptada = n.pedir("REVERTIR_COBRO", "cobroId", cobro, "MOTIVO SECRETO");
		n.aceptar(aceptada);
		long cargo2 = n.cargoCobrado(n.e.aA);
		long cobro2 = cobroActivo(cargo2);
		long noAplicable = n.pedir("REVERTIR_COBRO", "cobroId", cobro2, "MOTIVO SECRETO 2");
		assertEquals(200, estado(pago(cargo2, "{\"pagado\":false}"), n.tPropietario));
		assertEquals("NO_APLICABLE", n.aceptar(noAplicable).get("estado").asText());

		List<Map<String, Object>> eventos = jdbc.queryForList("""
				SELECT * FROM auditoria_evento WHERE organizacion_id = ? AND modulo = 'solicitudes'
				  AND (resumen LIKE 'SOLICITUDES SOLICITUD_ACEPTADA %' OR resumen LIKE 'SOLICITUDES SOLICITUD_NO_APLICABLE %')
				ORDER BY id""", n.e.org);
		assertEquals(2, eventos.size());
		String sede = "sede=#" + n.e.sA;
		assertEvento(eventos.get(0), n.propietario, aceptada,
				"SOLICITUDES SOLICITUD_ACEPTADA " + sede + " solicitud=#" + aceptada + " cobro=#" + cobro + " cargo=#"
						+ cargo);
		assertEvento(eventos.get(1), n.propietario, noAplicable, "SOLICITUDES SOLICITUD_NO_APLICABLE " + sede
				+ " solicitud=#" + noAplicable + " cobro=#" + cobro2 + " cargo=#" + cargo2);
		for (Map<String, Object> evento : eventos) {
			String resumen = (String) evento.get("resumen");
			assertFalse(resumen.contains("SECRET") || resumen.contains("activo") || resumen.contains("CAMISETA"),
					resumen);
		}
	}

	// ------------------------------------------------------------------ utilidades

	/** Acepta y comprueba NO_APLICABLE con la razón, sin cambiar el saldo de la sede A. */
	private void noAplicableSinEfectos(long solicitud, String razon) throws Exception {
		BigDecimal saldo = s.saldo(s.e.sA);
		long movimientos = jdbc.queryForObject("SELECT COUNT(*) FROM movimiento_caja WHERE organizacion_id = ?",
				Long.class, s.e.org);
		JsonNode r = s.aceptar(solicitud);
		assertEquals("NO_APLICABLE", r.get("estado").asText(), r.toString());
		assertEquals(razon, r.get("respuesta").asText());
		Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM solicitud WHERE id = ?", solicitud);
		assertEquals("NO_APLICABLE", fila.get("estado"));
		assertEquals(razon, fila.get("respuesta"));
		assertEquals(s.propietario, numero(fila, "resuelto_por_usuario_id"));
		assertNotNull(fila.get("resuelto_en"));
		assertEquals(0, saldo.compareTo(s.saldo(s.e.sA)), "NO_APLICABLE no mueve dinero");
		assertEquals(movimientos, jdbc.queryForObject("SELECT COUNT(*) FROM movimiento_caja WHERE organizacion_id = ?",
				Long.class, s.e.org), "ni apunta movimientos");
	}

	private void assertEvento(Map<String, Object> evento, long usuario, long recurso, String resumen) {
		assertEquals(usuario, numero(evento, "usuario_id"));
		assertEquals("UPDATE", evento.get("accion"));
		assertEquals(recurso, numero(evento, "recurso_id"));
		assertEquals(resumen, evento.get("resumen"));
	}

	private long eventosSolicitudes(Escena x) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM auditoria_evento WHERE organizacion_id = ? AND ("
				+ "resumen LIKE 'SOLICITUDES %' OR resumen LIKE 'CAJA %' OR resumen LIKE 'CARGO %')", Long.class,
				x.e.org);
	}

	private long cobroActivo(long cargo) {
		return jdbc.queryForObject("SELECT id FROM cobro WHERE producto_alumno_id = ? AND estado = 'ACTIVO'", Long.class,
				cargo);
	}

	private long cargoDe(long cobro) {
		return jdbc.queryForObject("SELECT producto_alumno_id FROM cobro WHERE id = ?", Long.class, cobro);
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
		return post("/api/solicitudes").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo));
	}

	private static MockHttpServletRequestBuilder anulacion(String ruta) {
		return post(ruta).contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"x\"}");
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
