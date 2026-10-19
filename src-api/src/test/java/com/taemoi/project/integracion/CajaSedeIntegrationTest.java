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
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
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
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.GrupoService;
import com.taemoi.project.services.JwtService;

import jakarta.servlet.http.Cookie;

/**
 * Pieza 6b-2 contra MySQL: API de caja de sede ({@code /api/caja}). Escuela sintética de dos sedes activas
 * ({@link EscuelaConSedesDePrueba}: A y B) con dos profesores (FAMILIA + PROFESOR, la única forma de abrir sesión de
 * profesor hoy): {@code prof1} en el grupo gA (sede A) y {@code prof2} en gB (sede B); el responsable de la sede A, el
 * gestor y el propietario; y otra escuela para el aislamiento. Datos inventados.
 *
 * <p>Concurrencia: dos entregas con un cobro común, y la carrera revertir ↔ entregar por las tres vías (PUT del cargo,
 * puerta de la convocatoria y deshacer del profesor), con una conexión JDBC que hace de «entrega en curso» (bloqueo
 * determinista) y con hilos reales.
 */
@SpringBootTest(properties = {
		"spring.jpa.properties.hibernate.session_factory.statement_inspector=com.taemoi.project.integracion.ContadorSentencias" })
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CajaSedeIntegrationTest extends MySqlIntegrationTest {

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired DataSource dataSource;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;

	@MockitoBean JavaMailSender mailSender;

	/** Una escuela de prueba con sus cuentas, tokens y un producto. */
	final class Escena {
		final EscuelaConSedesDePrueba e;
		final long propietario, prof1, prof2, producto;
		final String tGestor, tPropietario, tResp, tProf1, tProf2;

		Escena(String prefijo) {
			e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, prefijo);
			propietario = e.cuenta(usuarios, "propietario");
			jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, 'PROPIETARIO')",
					propietario, e.org);
			prof1 = profesor("prof1", e.gA);
			prof2 = profesor("prof2", e.gB);
			tGestor = e.token(jwtService, usuarios, e.gestor);
			tPropietario = e.token(jwtService, usuarios, propietario);
			tResp = e.token(jwtService, usuarios, e.respA);
			tProf1 = e.token(jwtService, usuarios, prof1);
			tProf2 = e.token(jwtService, usuarios, prof2);
			// Marcado por la gestión como «Lo puede vender el profesor» (decisión 182, V31_7).
			jdbc.update("INSERT INTO producto (concepto, precio, codigo, organizacion_id, vendible_profesor) VALUES "
					+ "('CAMISETA 6B2', 10.5, NULL, ?, TRUE)", e.org);
			producto = jdbc.queryForObject("SELECT MAX(id) FROM producto WHERE organizacion_id = ?", Long.class, e.org);
			jdbc.update("UPDATE alumno_deporte SET fecha_grado = '2024-01-01' WHERE organizacion_id = ?", e.org);
		}

		private long profesor(String nombre, long grupo) {
			long id = e.cuentaFamilia(usuarios, nombre);
			jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, 'PROFESOR')", id, e.org);
			jdbc.update("INSERT INTO asignacion_profesor (organizacion_id, usuario_id, grupo_id, rol) "
					+ "VALUES (?, ?, ?, 'TITULAR')", e.org, id, grupo);
			return id;
		}

		/** Alumno nuevo con sede principal {@code sede} y en el grupo {@code grupo}. */
		long alumnoEnGrupo(String nombre, long sede, long grupo) {
			long alumno = e.alumno(nombre + e.marca, sede, true);
			jdbc.update("INSERT INTO alumno_grupo (grupo_id, alumno_id) VALUES (?, ?)", grupo, alumno);
			return alumno;
		}

		/** Cargo pendiente (10,50) del alumno, creado por el gestor como siempre. */
		long cargo(long alumno) {
			try {
				MvcResult r = peticion(post("/api/productos-alumno/alumno/" + alumno + "/producto/" + producto)
						.contentType(MediaType.APPLICATION_JSON).content("{\"cantidad\":1,\"pagado\":false}"), tGestor);
				assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
				return json.readTree(r.getResponse().getContentAsString()).get("id").asLong();
			} catch (Exception ex) {
				throw new IllegalStateException(ex);
			}
		}

		/** Inscripción a una convocatoria nueva con su cargo de derecho a examen pendiente (20,00). */
		long inscripcionConCargo(long alumno) {
			jdbc.update("INSERT INTO convocatoria (fecha_convocatoria, organizacion_id, disciplina_id) VALUES "
					+ "('2026-06-01', ?, ?)", e.org, e.disciplina);
			long convocatoria = jdbc.queryForObject("SELECT MAX(id) FROM convocatoria WHERE organizacion_id = ?",
					Long.class, e.org);
			jdbc.update("""
					INSERT INTO producto_alumno (alumno_id, concepto, precio, cantidad, pagado, fecha_asignacion,
					  organizacion_id, sede_id)
					VALUES (?, 'DERECHO A EXAMEN 6B2', 20.0, 1, 0, NOW(), ?,
					  (SELECT sede_principal_id FROM alumno WHERE id = ?))""", alumno, e.org, alumno);
			long cargo = jdbc.queryForObject("SELECT MAX(id) FROM producto_alumno WHERE organizacion_id = ?",
					Long.class, e.org);
			jdbc.update("""
					INSERT INTO alumno_convocatoria (cuantia_examen, pagado, alumno_id, convocatoria_id, grado_actual,
					  grado_siguiente, organizacion_id, producto_alumno_id) VALUES (20, 0, ?, ?, NULL, NULL, ?, ?)""",
					alumno, convocatoria, e.org, cargo);
			return jdbc.queryForObject(
					"SELECT id FROM alumno_convocatoria WHERE alumno_id = ? AND convocatoria_id = ?", Long.class,
					alumno, convocatoria);
		}

		long cargoDe(long inscripcion) {
			return jdbc.queryForObject("SELECT producto_alumno_id FROM alumno_convocatoria WHERE id = ?", Long.class,
					inscripcion);
		}

		/** El profesor cobra el cargo (201) y devuelve el id del cobro. */
		long cobrar(long cargo, String token) throws Exception {
			MvcResult r = peticion(post("/api/caja/profesor/cargos/" + cargo + "/cobrar"), token);
			assertEquals(201, r.getResponse().getStatus(), r.getResponse().getContentAsString());
			return json.readTree(r.getResponse().getContentAsString()).get("cobroId").asLong();
		}
	}

	Escena s;
	Escena otra;
	long aProfA, aProfA2, aProfB;

	@BeforeAll
	void preparar() {
		s = new Escena("it-6b2-caja");
		otra = new Escena("it-6b2-otra");
		aProfA = s.alumnoEnGrupo("E6B2PROFA", s.e.sA, s.e.gA);
		aProfA2 = s.alumnoEnGrupo("E6B2PROFA2", s.e.sA, s.e.gA);
		aProfB = s.e.aB; // sede B, en gB (prof2)
	}

	@AfterEach
	void sinFugas() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
		SecurityContextHolder.clearContext();
	}

	// ------------------------------------------------------------------ profesor: ver y cobrar

	@Test
	void profesor_veSoloLosCargosPendientesDeSusGrupos_sinTotales() throws Exception {
		long pendiente = s.cargo(aProfA);
		long examen = s.cargoDe(s.inscripcionConCargo(aProfA));
		long pagado = s.cargo(aProfA);
		assertEquals(200, estado(pago(pagado, "{\"pagado\":true}"), s.tGestor));
		long deB = s.cargo(aProfB);
		long fueraDeGrupo = s.cargo(s.e.aA);

		JsonNode lista = leer(get("/api/caja/profesor/cargos-pendientes"), s.tProf1, 200);
		Set<Long> ids = ids(lista, "cargoId");
		assertTrue(ids.containsAll(List.of(pendiente, examen)), "pendientes de sus grupos, también el derecho a examen");
		assertFalse(ids.contains(pagado));
		assertFalse(ids.contains(deB));
		assertFalse(ids.contains(fueraDeGrupo));
		JsonNode fila = fila(lista, "cargoId", pendiente);
		assertEquals(Set.of("cargoId", "alumnoId", "alumnoNombre", "alumnoApellidos", "concepto", "importe",
				"fechaAsignacion", "sedeId", "sedeNombre"), campos(fila), "solo la fila, sin totales");
		assertEquals(aProfA, fila.get("alumnoId").asLong());
		assertEquals(0, new BigDecimal("10.50").compareTo(fila.get("importe").decimalValue()));
		assertEquals(s.e.sA, fila.get("sedeId").asLong());

		assertTrue(ids(leer(get("/api/caja/profesor/cargos-pendientes"), s.tProf2, 200), "cargoId").contains(deB));
		// Las rutas del profesor no son de gestión ni del responsable.
		for (String token : List.of(s.tGestor, s.tPropietario, s.tResp)) {
			assertEquals(403, estado(get("/api/caja/profesor/cargos-pendientes"), token));
			assertEquals(403, estado(post("/api/caja/profesor/cargos/" + pendiente + "/cobrar"), token));
		}
	}

	@Test
	void cargosPendientes_sinNMasUno() throws Exception {
		long alumno = s.alumnoEnGrupo("E6B2NMAS1", s.e.sA, s.e.gA2);
		long prof = s.profesor("profnmas1", s.e.gA2);
		String token = s.e.token(jwtService, usuarios, prof);
		s.cargo(alumno);
		estado(get("/api/caja/profesor/cargos-pendientes"), token);
		int conUno = ContadorSentencias.contar(() -> estado(get("/api/caja/profesor/cargos-pendientes"), token)).total();
		for (int i = 0; i < 4; i++) {
			s.cargo(s.alumnoEnGrupo("E6B2NMAS1X" + i, s.e.sA, s.e.gA2));
		}
		ContadorSentencias.Conteo<JsonNode> conCinco = ContadorSentencias
				.contar(() -> leer(get("/api/caja/profesor/cargos-pendientes"), token, 200));
		assertEquals(5, conCinco.resultado().size());
		assertEquals(conUno, conCinco.total(), "mismas sentencias con 1 y con 5 cargos: " + conCinco.sentencias());
	}

	@Test
	void profesor_cobraEnEfectivo_conLosMismosEfectosQueElPut() throws Exception {
		// Dos inscripciones iguales: una la cobra el profesor, la otra el gestor por el PUT del cargo.
		long insProfesor = s.inscripcionConCargo(aProfA);
		long insPut = s.inscripcionConCargo(aProfA2);
		long cargoProfesor = s.cargoDe(insProfesor);
		long cargoPut = s.cargoDe(insPut);

		MvcResult r = peticion(post("/api/caja/profesor/cargos/" + cargoProfesor + "/cobrar"), s.tProf1);
		assertEquals(201, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		JsonNode respuesta = json.readTree(r.getResponse().getContentAsString());
		assertEquals(Set.of("cobroId", "cargoId", "alumnoId", "alumnoNombre", "alumnoApellidos", "concepto", "importe",
				"fechaCobro", "sedeId", "venta"), campos(respuesta), "solo la fila, sin totales");
		assertEquals(200, estado(pago(cargoPut, "{\"pagado\":true,\"metodoPago\":\"EFECTIVO\"}"), s.tGestor));

		Map<String, Object> cobro = jdbc.queryForMap("SELECT * FROM cobro WHERE id = ?",
				respuesta.get("cobroId").asLong());
		assertEquals("ACTIVO", cobro.get("estado"));
		assertEquals("EFECTIVO", cobro.get("metodo"));
		assertEquals("PROFESOR", cobro.get("origen"));
		assertEquals(s.prof1, numero(cobro, "cobrado_por_usuario_id"));
		assertEquals(s.e.sA, numero(cobro, "sede_id"));
		assertEquals(cargoProfesor, numero(cobro, "producto_alumno_id"));
		assertNull(cobro.get("entrega_caja_id"));
		assertEquals(1L, jdbc.queryForObject("""
				SELECT COUNT(*) FROM cobro c JOIN producto_alumno pa ON pa.id = c.producto_alumno_id
				WHERE c.id = ? AND c.fecha_cobro = pa.fecha_pago""", Long.class, numero(cobro, "id")));

		// Efectos colaterales idénticos: cargo, inscripción y derecho a examen.
		for (long[] par : new long[][] { { cargoProfesor, insProfesor, aProfA }, { cargoPut, insPut, aProfA2 } }) {
			assertTrue(pagado(par[0]));
			assertNotNull(jdbc.queryForObject("SELECT fecha_pago FROM producto_alumno WHERE id = ?", Object.class,
					par[0]));
			assertTrue(jdbc.queryForObject("SELECT pagado FROM alumno_convocatoria WHERE id = ?", Boolean.class,
					par[1]));
			assertEquals(0, new BigDecimal("20").compareTo(jdbc.queryForObject(
					"SELECT cuantia_examen FROM alumno_convocatoria WHERE id = ?", BigDecimal.class, par[1])));
			assertTrue(jdbc.queryForObject("SELECT tiene_derecho_examen FROM alumno WHERE id = ?", Boolean.class,
					par[2]));
		}

		// Ya pagado → 409 sin cambios; de otro grupo → 403; de otra escuela → 403.
		assertEquals(409, estado(post("/api/caja/profesor/cargos/" + cargoProfesor + "/cobrar"), s.tProf1));
		assertEquals(409, estado(post("/api/caja/profesor/cargos/" + cargoPut + "/cobrar"), s.tProf1));
		assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM cobro WHERE producto_alumno_id = ?", Long.class,
				cargoProfesor));
		long deB = s.cargo(aProfB);
		assertEquals(403, estado(post("/api/caja/profesor/cargos/" + deB + "/cobrar"), s.tProf1));
		assertFalse(pagado(deB));
		long deOtraEscuela = otra.cargo(otra.e.aGrupoA);
		assertEquals(403, estado(post("/api/caja/profesor/cargos/" + deOtraEscuela + "/cobrar"), s.tProf1));
		assertEquals(403, estado(post("/api/caja/profesor/cargos/" + cargoPut + "/cobrar"), otra.tProf1));
		assertFalse(pagado(deOtraEscuela));
	}

	@Test
	void profesor_deshaceSuCobro_noElAjeno_niElEntregado() throws Exception {
		long ins = s.inscripcionConCargo(aProfA);
		long cargo = s.cargoDe(ins);
		long cobro = s.cobrar(cargo, s.tProf1);

		// prof2 tiene a aGrupoA en su grupo, pero el cobro no es suyo → 403.
		assertEquals(403, estado(post("/api/caja/profesor/cobros/" + cobro + "/deshacer"), s.tProf2));
		// El gestor y el propietario no deshacen por la ruta del profesor.
		assertEquals(403, estado(post("/api/caja/profesor/cobros/" + cobro + "/deshacer"), s.tPropietario));
		assertEquals("ACTIVO", jdbc.queryForObject("SELECT estado FROM cobro WHERE id = ?", String.class, cobro));

		assertEquals(204, estado(post("/api/caja/profesor/cobros/" + cobro + "/deshacer"), s.tProf1));
		Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM cobro WHERE id = ?", cobro);
		assertEquals("ANULADO", fila.get("estado"));
		assertEquals(s.prof1, numero(fila, "anulado_por_usuario_id"));
		assertEquals("cobro deshecho por el profesor", fila.get("motivo_anulacion"));
		assertFalse(pagado(cargo));
		assertNull(jdbc.queryForObject("SELECT fecha_pago FROM producto_alumno WHERE id = ?", Object.class, cargo));
		assertFalse(jdbc.queryForObject("SELECT pagado FROM alumno_convocatoria WHERE id = ?", Boolean.class, ins));
		assertFalse(jdbc.queryForObject("SELECT tiene_derecho_examen FROM alumno WHERE id = ?", Boolean.class,
				aProfA));
		// Otra vez: ya anulado → 409.
		assertEquals(409, estado(post("/api/caja/profesor/cobros/" + cobro + "/deshacer"), s.tProf1));

		// Un cobro de tesorería (lo cobró el gestor) no es del profesor → 403.
		long cargoGestor = s.cargo(aProfA);
		assertEquals(200, estado(pago(cargoGestor, "{\"pagado\":true,\"metodoPago\":\"EFECTIVO\"}"), s.tGestor));
		assertEquals(403, estado(post("/api/caja/profesor/cobros/" + idCobroActivo(cargoGestor) + "/deshacer"),
				s.tProf1));

		// Entregado → 409 sin cambios.
		long otro = s.cargo(aProfA);
		long cobroEntregado = s.cobrar(otro, s.tProf1);
		assertEquals(201, estado(entrega(s.e.sA, s.prof1, List.of(cobroEntregado), "10.50", null), s.tResp));
		assertEquals(409, estado(post("/api/caja/profesor/cobros/" + cobroEntregado + "/deshacer"), s.tProf1));
		assertEquals("ACTIVO", jdbc.queryForObject("SELECT estado FROM cobro WHERE id = ?", String.class,
				cobroEntregado));
		assertTrue(pagado(otro));
	}

	@Test
	void profesor_misCobrosPendientesDeEntrega_soloLosSuyosConSusTotales() throws Exception {
		long prof = s.profesor("profmios", s.e.gA);
		String token = s.e.token(jwtService, usuarios, prof);
		long c1 = s.cobrar(s.cargo(aProfA), token);
		long c2 = s.cobrar(s.cargo(aProfA2), token);
		long ajeno = s.cobrar(s.cargo(aProfA), s.tProf1);
		JsonNode mios = leer(get("/api/caja/profesor/cobros-pendientes-entrega"), token, 200);
		// Sus propios totales (lo que lleva encima) y sus filas; nada de sede, caja ni otros profesores.
		assertEquals(Set.of("numeroCobros", "importe", "cobros"), campos(mios));
		assertEquals(2, mios.get("numeroCobros").asInt());
		assertEquals(0, new BigDecimal("21.00").compareTo(mios.get("importe").decimalValue()));
		JsonNode lista = mios.get("cobros");
		assertEquals(List.of(c1, c2), new ArrayList<>(idsEnOrden(lista, "cobroId")));
		assertFalse(ids(lista, "cobroId").contains(ajeno));
		assertEquals(Set.of("cobroId", "cargoId", "alumnoId", "alumnoNombre", "alumnoApellidos", "concepto", "importe",
				"fechaCobro", "sedeId", "venta"), campos(lista.get(0)));
		assertEquals(201, estado(entrega(s.e.sA, prof, List.of(c1), "10.50", null), s.tGestor));
		JsonNode despues = leer(get("/api/caja/profesor/cobros-pendientes-entrega"), token, 200);
		assertEquals(List.of(c2), new ArrayList<>(idsEnOrden(despues.get("cobros"), "cobroId")));
		assertEquals(1, despues.get("numeroCobros").asInt());
		assertEquals(0, new BigDecimal("10.50").compareTo(despues.get("importe").decimalValue()));
	}

	// ------------------------------------------------------------------ profesor: apuntar y cobrar un producto

	@Test
	void profesor_apuntaYCobraUnProductoPermitido_pendienteDeEntregarEnSuSede() throws Exception {
		long alumno = s.alumnoEnGrupo("E6B2VENTA", s.e.sA, s.e.gA);
		// Lo que envíe (importe, concepto) no se lee: precio y concepto del catálogo.
		MvcResult r = peticion(venta(alumno, s.producto).contentType(MediaType.APPLICATION_JSON)
				.content("{\"importe\":1.00,\"precio\":1.0,\"concepto\":\"REGALO\",\"pagado\":false}"), s.tProf1);
		assertEquals(201, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		JsonNode fila = json.readTree(r.getResponse().getContentAsString());
		long cobro = fila.get("cobroId").asLong();
		long cargo = fila.get("cargoId").asLong();
		Map<String, Object> filaCargo = jdbc.queryForMap("SELECT * FROM producto_alumno WHERE id = ?", cargo);
		assertTrue((Boolean) filaCargo.get("pagado"));
		assertNotNull(filaCargo.get("fecha_pago"));
		assertEquals(10.5, ((Number) filaCargo.get("precio")).doubleValue(), 0.0001, "el precio del catálogo");
		assertEquals("CAMISETA 6B2", filaCargo.get("concepto"));
		assertEquals(alumno, numero(filaCargo, "alumno_id"));
		assertEquals(s.producto, numero(filaCargo, "producto_id"));
		assertEquals(s.e.sA, numero(filaCargo, "sede_id"), "la sede del @PrePersist (sede principal del alumno)");
		Map<String, Object> filaCobro = jdbc.queryForMap("SELECT * FROM cobro WHERE id = ?", cobro);
		assertEquals("ACTIVO", filaCobro.get("estado"));
		assertEquals("EFECTIVO", filaCobro.get("metodo"));
		assertEquals("PROFESOR", filaCobro.get("origen"));
		assertEquals(s.prof1, numero(filaCobro, "cobrado_por_usuario_id"));
		assertEquals(s.e.sA, numero(filaCobro, "sede_id"));
		assertNull(filaCobro.get("entrega_caja_id"));
		assertEquals(0, new BigDecimal("10.50").compareTo((BigDecimal) filaCobro.get("importe")));
		assertEquals(1L, jdbc.queryForObject("""
				SELECT COUNT(*) FROM cobro c JOIN producto_alumno pa ON pa.id = c.producto_alumno_id
				WHERE c.id = ? AND c.fecha_cobro = pa.fecha_pago""", Long.class, cobro));

		// En la caja de su sede, pendiente de entregar a su nombre.
		JsonNode pendiente = leer(get("/api/caja/sedes/" + s.e.sA + "/pendiente-entrega"), s.tResp, 200);
		assertTrue(ids(fila(pendiente, "usuarioId", s.prof1).get("cobros"), "cobroId").contains(cobro));
		// Registro de acciones.
		assertEquals("CAJA COBRO_PROFESOR_PRODUCTO sede=#" + s.e.sA + " cobro=#" + cobro + " cargo=#" + cargo
				+ " producto=#" + s.producto + " alumno=#" + alumno, jdbc.queryForObject("""
						SELECT resumen FROM auditoria_evento WHERE organizacion_id = ? AND modulo = 'caja'
						AND resumen LIKE 'CAJA COBRO_PROFESOR_PRODUCTO %' AND recurso_id = ?""", String.class,
						s.e.org, cobro));

		// Deshacerlo deja el cargo como no pagado (no lo borra).
		assertEquals(204, estado(post("/api/caja/profesor/cobros/" + cobro + "/deshacer"), s.tProf1));
		assertFalse(pagado(cargo));
		assertEquals("ANULADO", jdbc.queryForObject("SELECT estado FROM cobro WHERE id = ?", String.class, cobro));
	}

	@Test
	void profesor_noApuntaProductosConEfectos_niAAlumnosAjenos() throws Exception {
		long alumno = s.alumnoEnGrupo("E6B2VENTANO", s.e.sA, s.e.gA);
		List<Long> excluidos = List.of(
				producto(s, "MENSUALIDAD 6B2", "OTRO", null),
				producto(s, "LICENCIA FEDERATIVA 6B2", "OTRO", null),
				producto(s, "MATRÍCULA 6B2", "OTRO", null),
				producto(s, "RESERVA DE PLAZA 6B2", "OTRO", null),
				producto(s, "DERECHO A EXAMEN VERDE 6B2", "OTRO", null),
				producto(s, "PASE DE GRADO POR RECOMPENSA 6B2", "OTRO", null),
				producto(s, "TARIFA COMPETIDOR 6B2", "OTRO", null),
				producto(s, "CINTURÓN NUEVO 6B2", "EXAMEN", "EX-6B2-" + s.e.marca),
				producto(s, "PACK 6B2", "RECOMPENSA", "RE-6B2-" + s.e.marca),
				producto(s, "CUOTA 6B2", "MENSUALIDAD", "ME-6B2-" + s.e.marca),
				producto(s, "SEGURO 6B2", "LICENCIA", "LI-6B2-" + s.e.marca),
				producto(s, "PLAZA 6B2", "RESERVA_PLAZA", "RP-6B2-" + s.e.marca),
				producto(s, "COMPETICIÓN 6B2", "COMPETIDOR", "CO-6B2-" + s.e.marca));
		long cargosAntes = jdbc.queryForObject("SELECT COUNT(*) FROM producto_alumno WHERE alumno_id = ?", Long.class,
				alumno);
		for (long producto : excluidos) {
			MvcResult r = peticion(venta(alumno, producto), s.tProf1);
			assertEquals(400, r.getResponse().getStatus(), "producto " + producto);
			assertTrue(r.getResponse().getContentAsString().contains("no lo puede cobrar un profesor"));
		}
		// Alumno fuera de sus grupos → 403; de otra escuela → 403; producto de otra escuela → 404.
		assertEquals(403, estado(venta(aProfB, s.producto), s.tProf1));
		assertEquals(403, estado(venta(otra.e.aGrupoA, s.producto), s.tProf1));
		assertEquals(404, estado(venta(alumno, otra.producto), s.tProf1));
		assertEquals(404, estado(venta(alumno, 999999999L), s.tProf1));
		// Solo el profesor: la gestión usa la asignación de siempre.
		assertEquals(403, estado(venta(alumno, s.producto), s.tGestor));
		assertEquals(403, estado(venta(alumno, s.producto), s.tResp));
		assertEquals(cargosAntes, jdbc.queryForObject("SELECT COUNT(*) FROM producto_alumno WHERE alumno_id = ?",
				Long.class, alumno), "ningún intento deja cargo");
		assertFalse(ids(leer(get("/api/caja/profesor/cobros-pendientes-entrega"), s.tProf2, 200).get("cobros"),
				"alumnoId").contains(alumno));
	}

	// ------------------------------------------------------------------ caja de sede: permisos

	@Test
	void responsableDeA_noVeNiEntregaEnB_gestorYPropietarioSi() throws Exception {
		long cobroB = s.cobrar(s.cargo(aProfB), s.tProf2);
		for (String ruta : List.of("/resumen", "/pendiente-entrega", "/entregas", "/movimientos")) {
			assertEquals(403, estado(get("/api/caja/sedes/" + s.e.sB + ruta), s.tResp), ruta);
			assertEquals(200, estado(get("/api/caja/sedes/" + s.e.sA + ruta), s.tResp), ruta);
			assertEquals(200, estado(get("/api/caja/sedes/" + s.e.sB + ruta), s.tGestor), ruta);
			assertEquals(200, estado(get("/api/caja/sedes/" + s.e.sB + ruta), s.tPropietario), ruta);
			assertEquals(403, estado(get("/api/caja/sedes/" + s.e.sA + ruta), s.tProf1), ruta);
		}
		assertEquals(403, estado(entrega(s.e.sB, s.prof2, List.of(cobroB), "10.50", null), s.tResp));
		assertEquals(403, estado(movimiento(s.e.sB, "OTRO", "1.00", null, "x"), s.tResp));
		assertNull(jdbc.queryForObject("SELECT entrega_caja_id FROM cobro WHERE id = ?", Long.class, cobroB));
		assertEquals(201, estado(entrega(s.e.sB, s.prof2, List.of(cobroB), "10.50", null), s.tGestor));

		long otroB = s.cobrar(s.cargo(aProfB), s.tProf2);
		assertEquals(201, estado(entrega(s.e.sB, s.prof2, List.of(otroB), "10.50", null), s.tPropietario));
		// Sede inexistente para la gestión: 404.
		assertEquals(404, estado(get("/api/caja/sedes/999999999/resumen"), s.tGestor));
	}

	@Test
	void pendientePorCobrador_agrupaPorProfesorConSusCobros() throws Exception {
		Escena n = new Escena("it-6b2-pend");
		long alumno = n.alumnoEnGrupo("E6B2PEND", n.e.sA, n.e.gA);
		long otroProf = n.profesor("prof3", n.e.gA);
		String tOtro = n.e.token(jwtService, usuarios, otroProf);
		long c1 = n.cobrar(n.cargo(alumno), n.tProf1);
		long c2 = n.cobrar(n.cargo(alumno), n.tProf1);
		long c3 = n.cobrar(n.cargo(alumno), tOtro);
		JsonNode grupos = leer(get("/api/caja/sedes/" + n.e.sA + "/pendiente-entrega"), n.tResp, 200);
		assertEquals(2, grupos.size());
		JsonNode primero = fila(grupos, "usuarioId", n.prof1);
		assertEquals(2, primero.get("numeroCobros").asInt());
		assertEquals(0, new BigDecimal("21.00").compareTo(primero.get("importe").decimalValue()));
		assertEquals("Usuario Prueba", primero.get("nombre").asText());
		assertNotNull(primero.get("cobroMasAntiguo"));
		assertEquals(List.of(c1, c2), new ArrayList<>(idsEnOrden(primero.get("cobros"), "cobroId")));
		JsonNode cobro = primero.get("cobros").get(0);
		assertEquals(alumno, cobro.get("alumnoId").asLong());
		assertEquals("CAMISETA 6B2", cobro.get("concepto").asText());
		assertEquals(List.of(c3), new ArrayList<>(idsEnOrden(fila(grupos, "usuarioId", otroProf).get("cobros"),
				"cobroId")));
	}

	// ------------------------------------------------------------------ entregas

	@Test
	void entrega_validaLosCobros_yLaDiferenciaCreaSobranteOFaltanteConNota() throws Exception {
		long c1 = s.cobrar(s.cargo(aProfA), s.tProf1);
		long c2 = s.cobrar(s.cargo(aProfA2), s.tProf1);
		long deProf2 = s.cobrar(s.cargo(s.e.aGrupoA), s.tProf2); // sede B
		long cargoTesoreria = s.cargo(aProfA);
		assertEquals(200, estado(pago(cargoTesoreria, "{\"pagado\":true,\"metodoPago\":\"EFECTIVO\"}"), s.tGestor));
		long deTesoreria = idCobroActivo(cargoTesoreria);
		long entregasAntes = contarEntregas(s);

		assertEquals(400, estado(entrega(s.e.sA, s.prof1, List.of(), "0", null), s.tResp), "lista vacía");
		assertEquals(400, estado(entrega(s.e.sA, null, List.of(c1), "10.50", null), s.tResp), "sin quien entrega");
		assertEquals(400, estado(entrega(s.e.sA, s.prof1, List.of(c1, c1), "21.00", null), s.tResp), "repetido");
		assertEquals(400, estado(entrega(s.e.sA, s.prof1, List.of(c1), "-1.00", "x"), s.tResp), "negativo");
		assertEquals(400, estado(entrega(s.e.sA, s.prof1, List.of(c1), "10.505", "x"), s.tResp), "tres decimales");
		assertEquals(400, estado(entrega(s.e.sA, s.prof2, List.of(c1), "10.50", null), s.tResp), "otro cobrador");
		assertEquals(400, estado(entrega(s.e.sA, s.prof2, List.of(deProf2), "10.50", null), s.tGestor),
				"cobro de otra sede");
		assertEquals(400, estado(entrega(s.e.sA, s.e.gestor, List.of(deTesoreria), "10.50", null), s.tResp),
				"cobro de tesorería");
		assertEquals(400, estado(entrega(s.e.sA, s.prof1, List.of(c1, 999999999L), "10.50", null), s.tResp),
				"cobro inexistente");
		// Diferencia sin nota → 400.
		MvcResult sinNota = peticion(entrega(s.e.sA, s.prof1, List.of(c1, c2), "22.00", "  "), s.tResp);
		assertEquals(400, sinNota.getResponse().getStatus());
		assertTrue(sinNota.getResponse().getContentAsString().contains("nota es obligatoria"));
		assertEquals(entregasAntes, contarEntregas(s), "ninguna validación deja una entrega");
		assertNull(jdbc.queryForObject("SELECT entrega_caja_id FROM cobro WHERE id = ?", Long.class, c1));

		// Sobrante: contado 22,00, declarado 21,00.
		JsonNode sobrante = leer(entrega(s.e.sA, s.prof1, List.of(c1, c2), "22.00", "sobra un euro"), s.tResp, 201);
		long entregaId = sobrante.get("id").asLong();
		assertEquals(0, new BigDecimal("21.00").compareTo(sobrante.get("importeDeclarado").decimalValue()));
		assertEquals(0, new BigDecimal("22.00").compareTo(sobrante.get("importeContado").decimalValue()));
		assertEquals(0, new BigDecimal("1.00").compareTo(sobrante.get("diferencia").decimalValue()));
		assertEquals("ACTIVA", sobrante.get("estado").asText());
		assertEquals(s.e.respA, sobrante.get("recibidoPorUsuarioId").asLong());
		assertEquals(s.prof1, sobrante.get("entregadoPorUsuarioId").asLong());
		assertEquals(List.of(c1, c2), new ArrayList<>(idsEnOrden(sobrante.get("cobroIds"))));
		Map<String, Object> mov = jdbc.queryForMap("SELECT * FROM movimiento_caja WHERE entrega_caja_id = ?",
				entregaId);
		assertEquals("SOBRANTE", mov.get("tipo"));
		assertEquals(0, new BigDecimal("1.00").compareTo((BigDecimal) mov.get("importe")));
		assertEquals("sobra un euro", mov.get("nota"));
		assertNull(mov.get("alumno_id"));
		assertEquals(numero(mov, "id"), sobrante.get("movimientoId").asLong());
		for (long c : List.of(c1, c2)) {
			assertEquals(entregaId, jdbc.queryForObject("SELECT entrega_caja_id FROM cobro WHERE id = ?", Long.class, c));
		}
		// Ya entregados → 409.
		assertEquals(409, estado(entrega(s.e.sA, s.prof1, List.of(c1), "10.50", null), s.tResp));

		// Faltante: contado 10,00 de 10,50.
		long c3 = s.cobrar(s.cargo(aProfA), s.tProf1);
		JsonNode faltante = leer(entrega(s.e.sA, s.prof1, List.of(c3), "10.00", "falta medio"), s.tGestor, 201);
		Map<String, Object> movF = jdbc.queryForMap("SELECT * FROM movimiento_caja WHERE entrega_caja_id = ?",
				faltante.get("id").asLong());
		assertEquals("FALTANTE", movF.get("tipo"));
		assertEquals(0, new BigDecimal("-0.50").compareTo((BigDecimal) movF.get("importe")));
		// Exacta: sin movimiento y sin nota.
		long c4 = s.cobrar(s.cargo(aProfA), s.tProf1);
		JsonNode exacta = leer(entrega(s.e.sA, s.prof1, List.of(c4), "10.50", null), s.tResp, 201);
		assertTrue(exacta.get("movimientoId").isNull());
		assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM movimiento_caja WHERE entrega_caja_id = ?",
				Long.class, exacta.get("id").asLong()));

		// Historial: más recientes primero.
		JsonNode historial = leer(get("/api/caja/sedes/" + s.e.sA + "/entregas?page=1&size=2"), s.tResp, 200);
		assertEquals(2, historial.get("content").size());
		assertEquals(exacta.get("id").asLong(), historial.get("content").get(0).get("id").asLong());
		assertEquals(faltante.get("id").asLong(), historial.get("content").get(1).get("id").asLong());
		assertTrue(historial.get("content").get(0).has("recibidoPorNombre"));
	}

	@Test
	void anularEntrega_soloPropietario_liberaLosCobrosYAnulaSuMovimiento() throws Exception {
		long c1 = s.cobrar(s.cargo(aProfA), s.tProf1);
		JsonNode entrega = leer(entrega(s.e.sA, s.prof1, List.of(c1), "11.00", "sobra"), s.tResp, 201);
		long id = entrega.get("id").asLong();
		long movimiento = entrega.get("movimientoId").asLong();

		// El automático no se anula suelto.
		assertEquals(409, estado(anular("/api/caja/movimientos/" + movimiento + "/anular", "x"), s.tPropietario));
		assertEquals(403, estado(anular("/api/caja/entregas/" + id + "/anular", "x"), s.tResp));
		assertEquals(403, estado(anular("/api/caja/entregas/" + id + "/anular", "x"), s.tGestor));
		assertEquals(403, estado(anular("/api/caja/entregas/" + id + "/anular", "x"), s.tProf1));
		assertEquals(400, estado(anular("/api/caja/entregas/" + id + "/anular", " "), s.tPropietario));
		assertEquals("ACTIVA", jdbc.queryForObject("SELECT estado FROM entrega_caja WHERE id = ?", String.class, id));

		JsonNode anulada = leer(anular("/api/caja/entregas/" + id + "/anular", "contada dos veces"), s.tPropietario,
				200);
		assertEquals("ANULADA", anulada.get("estado").asText());
		assertEquals(0, anulada.get("cobroIds").size());
		Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM entrega_caja WHERE id = ?", id);
		assertEquals("ANULADA", fila.get("estado"));
		assertEquals(s.propietario, numero(fila, "anulada_por_usuario_id"));
		assertEquals("contada dos veces", fila.get("motivo_anulacion"));
		assertNotNull(fila.get("anulada_en"));
		assertNull(jdbc.queryForObject("SELECT entrega_caja_id FROM cobro WHERE id = ?", Long.class, c1));
		assertEquals("ACTIVO", jdbc.queryForObject("SELECT estado FROM cobro WHERE id = ?", String.class, c1));
		assertEquals("ANULADO", jdbc.queryForObject("SELECT estado FROM movimiento_caja WHERE id = ?", String.class,
				movimiento));
		assertEquals(409, estado(anular("/api/caja/entregas/" + id + "/anular", "otra vez"), s.tPropietario));
		// Vuelve a estar pendiente: el profesor lo ve y se puede volver a entregar.
		assertTrue(ids(leer(get("/api/caja/profesor/cobros-pendientes-entrega"), s.tProf1, 200).get("cobros"),
				"cobroId").contains(c1));
		assertEquals(201, estado(entrega(s.e.sA, s.prof1, List.of(c1), "10.50", null), s.tResp));
		// De otra escuela: 404.
		assertEquals(404, estado(anular("/api/caja/entregas/" + id + "/anular", "x"), otra.tPropietario));
	}

	@Test
	void anularEntrega_unCobroCuyoCargoSeBorroSeAnula_conMotivoCargoBorrado() throws Exception {
		long alumnoBorrado = s.alumnoEnGrupo("E6B2BORRADO", s.e.sA, s.e.gA);
		long cobroSinCargo = s.cobrar(s.cargo(alumnoBorrado), s.tProf1);
		long cobroNormal = s.cobrar(s.cargo(aProfA), s.tProf1);
		long entrega = leer(entrega(s.e.sA, s.prof1, List.of(cobroSinCargo, cobroNormal), "21.00", null), s.tResp, 201)
				.get("id").asLong();
		// Borrar el alumno con el cobro ya entregado: el cobro sigue ACTIVO, sin cargo (6b-1).
		assertEquals(200, estado(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.delete("/api/alumnos/" + alumnoBorrado), s.tGestor));
		Map<String, Object> antes = jdbc.queryForMap("SELECT * FROM cobro WHERE id = ?", cobroSinCargo);
		assertEquals("ACTIVO", antes.get("estado"));
		assertNull(antes.get("producto_alumno_id"));

		JsonNode anulada = leer(anular("/api/caja/entregas/" + entrega + "/anular", "recuento"), s.tPropietario, 200);
		assertEquals("ANULADA", anulada.get("estado").asText());
		Map<String, Object> sinCargo = jdbc.queryForMap("SELECT * FROM cobro WHERE id = ?", cobroSinCargo);
		assertEquals("ANULADO", sinCargo.get("estado"), "no vuelve a pendiente: su cargo ya no existe");
		assertEquals("cargo borrado", sinCargo.get("motivo_anulacion"));
		assertEquals(s.propietario, numero(sinCargo, "anulado_por_usuario_id"));
		assertNotNull(sinCargo.get("anulado_en"));
		assertNull(sinCargo.get("entrega_caja_id"));
		Map<String, Object> normal = jdbc.queryForMap("SELECT * FROM cobro WHERE id = ?", cobroNormal);
		assertEquals("ACTIVO", normal.get("estado"));
		assertNull(normal.get("entrega_caja_id"));
		JsonNode pendientes = leer(get("/api/caja/profesor/cobros-pendientes-entrega"), s.tProf1, 200).get("cobros");
		assertTrue(ids(pendientes, "cobroId").contains(cobroNormal));
		assertFalse(ids(pendientes, "cobroId").contains(cobroSinCargo));
		assertTrue(jdbc.queryForObject("""
				SELECT resumen FROM auditoria_evento WHERE organizacion_id = ? AND resumen LIKE 'CAJA ENTREGA_ANULADA %'
				AND recurso_id = ?""", String.class, s.e.org, entrega).contains("cobrosAnuladosSinCargo=[#"
				+ cobroSinCargo + "]"));
	}

	@Test
	void autoentrega_responsableQueEsProfesorEntregaSuPropioEfectivo_yQuedaSeñalada() throws Exception {
		long cuenta = s.e.cuentaFamilia(usuarios, "respprof");
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, 'RESPONSABLE_SEDE')", cuenta,
				s.e.org);
		long membresia = jdbc.queryForObject("""
				SELECT id FROM membresia WHERE usuario_id = ? AND organizacion_id = ? AND rol = 'RESPONSABLE_SEDE'""",
				Long.class, cuenta, s.e.org);
		jdbc.update("INSERT INTO membresia_sede (membresia_id, sede_id) VALUES (?, ?)", membresia, s.e.sA);
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, 'PROFESOR')", cuenta, s.e.org);
		jdbc.update("INSERT INTO asignacion_profesor (organizacion_id, usuario_id, grupo_id, rol) VALUES (?, ?, ?, 'TITULAR')",
				s.e.org, cuenta, s.e.gA);
		String token = s.e.token(jwtService, usuarios, cuenta);

		long cobro = s.cobrar(s.cargo(aProfA), token);
		JsonNode auto = leer(entrega(s.e.sA, cuenta, List.of(cobro), "10.50", null), token, 201);
		assertTrue(auto.get("autoentrega").asBoolean());
		assertEquals(cuenta, auto.get("entregadoPorUsuarioId").asLong());
		assertEquals(cuenta, auto.get("recibidoPorUsuarioId").asLong());
		long otro = s.cobrar(s.cargo(aProfA), s.tProf1);
		JsonNode normal = leer(entrega(s.e.sA, s.prof1, List.of(otro), "10.50", null), s.tResp, 201);
		assertFalse(normal.get("autoentrega").asBoolean());
		JsonNode historial = leer(get("/api/caja/sedes/" + s.e.sA + "/entregas?page=1&size=100"), s.tGestor, 200);
		assertTrue(fila(historial.get("content"), "id", auto.get("id").asLong()).get("autoentrega").asBoolean());
		assertFalse(fila(historial.get("content"), "id", normal.get("id").asLong()).get("autoentrega").asBoolean());
		Map<String, Object> evento = jdbc.queryForMap("""
				SELECT * FROM auditoria_evento WHERE organizacion_id = ? AND resumen LIKE 'CAJA %ENTREGA_REGISTRADA %'
				AND recurso_id = ?""", s.e.org, auto.get("id").asLong());
		assertTrue(((String) evento.get("resumen")).startsWith("CAJA AUTOENTREGA_REGISTRADA sede=#" + s.e.sA),
				(String) evento.get("resumen"));
		assertEquals(cuenta, numero(evento, "usuario_id"));
		assertTrue(jdbc.queryForObject("""
				SELECT resumen FROM auditoria_evento WHERE organizacion_id = ? AND resumen LIKE 'CAJA %ENTREGA_REGISTRADA %'
				AND recurso_id = ?""", String.class, s.e.org, normal.get("id").asLong())
				.startsWith("CAJA ENTREGA_REGISTRADA "));
	}

	@Test
	void derechoAExamen_soloLoTocanLosCargosDeExamen() throws Exception {
		long alumno = s.alumnoEnGrupo("E6B2DERECHO", s.e.sA, s.e.gA);
		jdbc.update("UPDATE alumno SET tiene_derecho_examen = 1 WHERE id = ?", alumno);
		// PUT del cargo (como Moiskimdo): cobrar y revertir un cargo que no es de examen no lo cambia.
		long camiseta = s.cargo(alumno);
		assertEquals(200, estado(pago(camiseta, "{\"pagado\":true}"), s.tGestor));
		assertTrue(derechoAExamen(alumno));
		assertEquals(200, estado(pago(camiseta, "{\"pagado\":false}"), s.tPropietario));
		assertTrue(derechoAExamen(alumno));
		// Rutas del profesor: cobrar y deshacer otro cargo que no es de examen, tampoco.
		long cobro = s.cobrar(s.cargo(alumno), s.tProf1);
		assertTrue(derechoAExamen(alumno));
		assertEquals(204, estado(post("/api/caja/profesor/cobros/" + cobro + "/deshacer"), s.tProf1));
		assertTrue(derechoAExamen(alumno));
		// El cargo de examen sigue como antes: revertirlo lo quita y cobrarlo lo da.
		long examen = s.cargoDe(s.inscripcionConCargo(alumno));
		assertEquals(200, estado(pago(examen, "{\"pagado\":true}"), s.tGestor));
		assertTrue(derechoAExamen(alumno));
		assertEquals(200, estado(pago(examen, "{\"pagado\":false}"), s.tPropietario));
		assertFalse(derechoAExamen(alumno));
		long cobroExamen = s.cobrar(examen, s.tProf1);
		assertTrue(derechoAExamen(alumno));
		assertEquals(204, estado(post("/api/caja/profesor/cobros/" + cobroExamen + "/deshacer"), s.tProf1));
		assertFalse(derechoAExamen(alumno));
	}

	@Test
	void profesor_listaLosProductosQuePuedeVender_conLaMismaListaBlanca() throws Exception {
		long llavero = producto(s, "LLAVERO R2 6B2", "OTRO", null);
		long mensualidad = producto(s, "MENSUALIDAD R2 6B2", "OTRO", null);
		long examen = producto(s, "CINTURÓN R2 6B2", "EXAMEN", "EX-R2-" + s.e.marca);
		JsonNode lista = leer(get("/api/caja/profesor/productos"), s.tProf1, 200);
		Set<Long> ids = ids(lista, "id");
		assertTrue(ids.containsAll(List.of(s.producto, llavero)));
		assertFalse(ids.contains(mensualidad));
		assertFalse(ids.contains(examen));
		assertFalse(ids.contains(otra.producto), "solo el catálogo de su escuela");
		JsonNode fila = fila(lista, "id", llavero);
		assertEquals(Set.of("id", "concepto", "precio"), campos(fila));
		assertEquals(0, new BigDecimal("15.00").compareTo(fila.get("precio").decimalValue()));
		// Coherente con apuntar y cobrar: lo listado se vende; lo excluido, 400.
		long alumno = s.alumnoEnGrupo("E6B2LISTA", s.e.sA, s.e.gA);
		assertEquals(201, estado(venta(alumno, llavero), s.tProf1));
		assertEquals(400, estado(venta(alumno, mensualidad), s.tProf1));
		for (String token : List.of(s.tGestor, s.tPropietario, s.tResp)) {
			assertEquals(403, estado(get("/api/caja/profesor/productos"), token));
		}
	}

	// ------------------------------------------------------------------ movimientos

	@Test
	void movimientos_reglasDeSignoYAlumno() throws Exception {
		String sA = String.valueOf(s.e.sA);
		assertEquals(400, estado(movimiento(s.e.sA, "DEVOLUCION", "5.00", aProfA, "x"), s.tResp), "devolución +");
		assertEquals(400, estado(movimiento(s.e.sA, "DEVOLUCION", "-5.00", null, "x"), s.tResp), "devolución sin alumno");
		assertEquals(400, estado(movimiento(s.e.sA, "SOBRANTE", "-1.00", null, "x"), s.tResp), "sobrante −");
		assertEquals(400, estado(movimiento(s.e.sA, "SOBRANTE", "1.00", aProfA, "x"), s.tResp), "sobrante con alumno");
		assertEquals(400, estado(movimiento(s.e.sA, "FALTANTE", "1.00", null, "x"), s.tResp), "faltante +");
		assertEquals(400, estado(movimiento(s.e.sA, "FALTANTE", "-1.00", aProfA, "x"), s.tResp), "faltante con alumno");
		assertEquals(400, estado(movimiento(s.e.sA, "ERROR", "0.00", null, "x"), s.tResp), "cero");
		assertEquals(400, estado(movimiento(s.e.sA, "ERROR", "1.001", null, "x"), s.tResp), "tres decimales");
		assertEquals(400, estado(movimiento(s.e.sA, "ERROR", "1.00", null, " "), s.tResp), "sin nota");
		assertEquals(400, estado(movimiento(s.e.sA, "INVENTADO", "1.00", null, "x"), s.tResp), "tipo desconocido");
		assertEquals(400, estado(movimiento(s.e.sA, "DEVOLUCION", "-5.00", aProfB, "x"), s.tResp),
				"alumno de otra sede");
		assertEquals(400, estado(movimiento(s.e.sA, "DEVOLUCION", "-5.00", otra.e.aA, "x"), s.tGestor),
				"alumno de otra escuela");

		JsonNode devolucion = leer(movimiento(s.e.sA, "DEVOLUCION", "-5.00", aProfA, "devuelta la camiseta"), s.tResp,
				201);
		assertEquals("DEVOLUCION", devolucion.get("tipo").asText());
		assertEquals(aProfA, devolucion.get("alumnoId").asLong());
		assertEquals(s.e.respA, devolucion.get("creadoPorUsuarioId").asLong());
		assertEquals(201, estado(movimiento(s.e.sA, "ERROR", "2.00", null, "error de cambio"), s.tResp));
		assertEquals(201, estado(movimiento(s.e.sA, "ERROR", "-2.00", aProfA, "error de cambio"), s.tGestor));
		assertEquals(201, estado(movimiento(s.e.sA, "SOBRANTE", "3.00", null, "arqueo"), s.tResp));
		assertEquals(201, estado(movimiento(s.e.sA, "FALTANTE", "-3.00", null, "arqueo"), s.tResp));
		assertEquals(201, estado(movimiento(s.e.sA, "OTRO", "-1.50", null, "tiza"), s.tResp));
		// D9: aGrupoA es de la sede A por grupo (sede principal B).
		assertEquals(201, estado(movimiento(s.e.sA, "OTRO", "1.00", s.e.aGrupoA, "por grupo"), s.tResp));

		JsonNode lista = leer(get("/api/caja/sedes/" + sA + "/movimientos"), s.tResp, 200);
		assertTrue(ids(lista, "id").contains(devolucion.get("id").asLong()));

		long id = devolucion.get("id").asLong();
		assertEquals(403, estado(anular("/api/caja/movimientos/" + id + "/anular", "x"), s.tResp));
		assertEquals(403, estado(anular("/api/caja/movimientos/" + id + "/anular", "x"), s.tGestor));
		assertEquals(400, estado(anular("/api/caja/movimientos/" + id + "/anular", null), s.tPropietario));
		JsonNode anulado = leer(anular("/api/caja/movimientos/" + id + "/anular", "apuntado dos veces"),
				s.tPropietario, 200);
		assertEquals("ANULADO", anulado.get("estado").asText());
		assertEquals(409, estado(anular("/api/caja/movimientos/" + id + "/anular", "otra"), s.tPropietario));
		assertEquals(404, estado(anular("/api/caja/movimientos/" + id + "/anular", "x"), otra.tPropietario));
	}

	// ------------------------------------------------------------------ resumen y saldo con números

	@Test
	void resumenYSaldo_conNumerosConcretos() throws Exception {
		Escena n = new Escena("it-6b2-num");
		long alumno = n.alumnoEnGrupo("E6B2NUM", n.e.sA, n.e.gA);
		LocalDate hoy = LocalDate.now(ZoneId.of("Europe/Madrid"));
		String periodo = "?desde=" + hoy.minusDays(1) + "&hasta=" + hoy.plusDays(1);
		String resumen = "/api/caja/sedes/" + n.e.sA + "/resumen";
		assertResumen(leer(get(resumen + periodo), n.tResp, 200), "0.00", "0.00", "0.00", "0.00");

		// Gestor: 10,50 en EFECTIVO (entra en caja al momento) y 10,50 por BIZUM (no es caja).
		long efectivo = n.cargo(alumno);
		long bizum = n.cargo(alumno);
		assertEquals(200, estado(pago(efectivo, "{\"pagado\":true,\"metodoPago\":\"EFECTIVO\"}"), n.tGestor));
		assertEquals(200, estado(pago(bizum, "{\"pagado\":true,\"metodoPago\":\"BIZUM\"}"), n.tGestor));
		// Profesor: tres de 10,50 (pendientes de entrega).
		long p1 = n.cobrar(n.cargo(alumno), n.tProf1);
		long p2 = n.cobrar(n.cargo(alumno), n.tProf1);
		n.cobrar(n.cargo(alumno), n.tProf1);
		assertResumen(leer(get(resumen + periodo), n.tResp, 200), "31.50", "0.00", "0.00", "10.50");

		// Entrega de p1 y p2: declarado 21,00, contado 20,00 → FALTANTE −1,00.
		long entrega = leer(entrega(n.e.sA, n.prof1, List.of(p1, p2), "20.00", "falta un euro"), n.tResp, 201)
				.get("id").asLong();
		// Movimientos: devolución −5,00 y error +2,00.
		assertEquals(201, estado(movimiento(n.e.sA, "DEVOLUCION", "-5.00", alumno, "devolución"), n.tResp));
		assertEquals(201, estado(movimiento(n.e.sA, "ERROR", "2.00", null, "error"), n.tResp));
		// pendiente 10,50 (p3); recibido 20,00; diferencias −1,00;
		// saldo = 10,50 (gestor, efectivo) + 21,00 (p1+p2 entregados) − 1,00 − 5,00 + 2,00 = 27,50.
		assertResumen(leer(get(resumen + periodo), n.tResp, 200), "10.50", "20.00", "-1.00", "27.50");
		// Por defecto, el mes en curso (Europe/Madrid).
		JsonNode mes = leer(get(resumen), n.tGestor, 200);
		// Fechas como el resto de la API (Jackson de la aplicación: [año, mes, día]).
		assertEquals(hoy.withDayOfMonth(1), fecha(mes.get("desde")), mes.toString());
		assertEquals(hoy.withDayOfMonth(hoy.lengthOfMonth()), fecha(mes.get("hasta")), mes.toString());
		// Otro período: lo recibido y las diferencias son del período; pendiente y saldo, el estado actual.
		String pasado = "?desde=" + hoy.minusMonths(2).withDayOfMonth(1) + "&hasta=" + hoy.minusDays(2);
		assertResumen(leer(get(resumen + pasado), n.tResp, 200), "10.50", "0.00", "0.00", "27.50");
		assertEquals(400, estado(get(resumen + "?desde=" + hoy + "&hasta=" + hoy.minusDays(1)), n.tResp));

		// Anular la entrega: p1 y p2 vuelven a pendientes y el FALTANTE se anula.
		assertEquals(200, estado(anular("/api/caja/entregas/" + entrega + "/anular", "recuento"), n.tPropietario));
		// pendiente 31,50; recibido 0; diferencias 0; saldo = 10,50 − 5,00 + 2,00 = 7,50.
		assertResumen(leer(get(resumen + periodo), n.tResp, 200), "31.50", "0.00", "0.00", "7.50");
		// La sede B no se mezcla.
		assertResumen(leer(get("/api/caja/sedes/" + n.e.sB + "/resumen" + periodo), n.tGestor, 200), "0.00", "0.00",
				"0.00", "0.00");
	}

	// ------------------------------------------------------------------ concurrencia

	@Test
	void dosEntregasSimultaneasConUnCobroComun_unaGanaYLaOtra409() throws Exception {
		for (int vuelta = 0; vuelta < 4; vuelta++) {
			long comun = s.cobrar(s.cargo(aProfA), s.tProf1);
			long a = s.cobrar(s.cargo(aProfA), s.tProf1);
			long b = s.cobrar(s.cargo(aProfA2), s.tProf1);
			long antes = contarEntregas(s);
			List<MvcResult> respuestas = enParaleloResultados(
					() -> peticion(entrega(s.e.sA, s.prof1, List.of(a, comun), "21.00", null), s.tResp),
					() -> peticion(entrega(s.e.sA, s.prof1, List.of(comun, b), "21.00", null), s.tGestor));
			List<Integer> estados = new ArrayList<>(respuestas.stream().map(r -> r.getResponse().getStatus()).toList());
			estados.sort(Integer::compareTo);
			assertEquals(List.of(201, 409), estados, "vuelta " + vuelta);
			for (MvcResult r : respuestas) {
				if (r.getResponse().getStatus() == 409) {
					assertTrue(r.getResponse().getContentAsString().contains("ya está incluido en una entrega"),
							r.getResponse().getContentAsString());
				}
			}
			assertEquals(antes + 1, contarEntregas(s), "la perdedora no deja entrega");
			long entregados = jdbc.queryForObject(
					"SELECT COUNT(*) FROM cobro WHERE id IN (?, ?) AND entrega_caja_id IS NOT NULL", Long.class, a, b);
			assertEquals(1L, entregados, "solo los cobros de la ganadora");
		}
	}

	@Test
	void laEntregaEsperaAlBloqueoDelCobro_yLuegoLoVeEntregado409() throws Exception {
		long cobro = s.cobrar(s.cargo(aProfA), s.tProf1);
		long antes = contarEntregas(s);
		ExecutorService hilo = Executors.newSingleThreadExecutor();
		try (Connection otraEntrega = dataSource.getConnection()) {
			otraEntrega.setAutoCommit(false);
			long fantasma = entregaEnCurso(otraEntrega, cobro);
			Future<MvcResult> peticion = hilo.submit(
					() -> peticion(entrega(s.e.sA, s.prof1, List.of(cobro), "10.50", null), s.tResp));
			Thread.sleep(1500);
			assertFalse(peticion.isDone(), "la entrega debe esperar al bloqueo de la fila del cobro");
			otraEntrega.commit();
			MvcResult r = peticion.get(60, TimeUnit.SECONDS);
			assertEquals(409, r.getResponse().getStatus());
			assertTrue(r.getResponse().getContentAsString().contains("ya está incluido en una entrega"),
					r.getResponse().getContentAsString());
			assertEquals(fantasma, jdbc.queryForObject("SELECT entrega_caja_id FROM cobro WHERE id = ?", Long.class,
					cobro));
		} finally {
			hilo.shutdownNow();
		}
		assertEquals(antes + 1, contarEntregas(s), "solo la entrega que ganó");
	}

	@Test
	void revertirMientrasSeEntrega_esperaYResponde409_porLasTresVias() throws Exception {
		// PUT del cargo (propietario).
		long cargoPut = s.cargo(aProfA);
		long cobroPut = s.cobrar(cargoPut, s.tProf1);
		Map<String, Object> cargoPutAntes = jdbc.queryForMap("SELECT * FROM producto_alumno WHERE id = ?", cargoPut);
		revertirDuranteEntrega(cobroPut, () -> peticion(pago(cargoPut, "{\"pagado\":false}"), s.tPropietario));
		assertEquals(cargoPutAntes, jdbc.queryForMap("SELECT * FROM producto_alumno WHERE id = ?", cargoPut));
		// Deshacer del profesor.
		long cobroProf = s.cobrar(s.cargo(aProfA), s.tProf1);
		revertirDuranteEntrega(cobroProf,
				() -> peticion(post("/api/caja/profesor/cobros/" + cobroProf + "/deshacer"), s.tProf1));
		// Puerta de la convocatoria (su comprobación previa no ve la entrega sin confirmar): el 409 no deja nada
		// escrito, ni en la inscripción ni en el cargo.
		long ins = s.inscripcionConCargo(aProfA);
		long cargoConv = s.cargoDe(ins);
		long cobroConv = s.cobrar(cargoConv, s.tProf1);
		Map<String, Object> inscripcionAntes = jdbc.queryForMap("SELECT * FROM alumno_convocatoria WHERE id = ?", ins);
		Map<String, Object> cargoConvAntes = jdbc.queryForMap("SELECT * FROM producto_alumno WHERE id = ?", cargoConv);
		assertTrue((Boolean) inscripcionAntes.get("pagado"));
		revertirDuranteEntrega(cobroConv, () -> peticion(put("/api/convocatorias/alumno/" + ins)
				.contentType(MediaType.APPLICATION_JSON).content("{\"cuantiaExamen\":30.0,\"pagado\":false}"),
				s.tGestor));
		assertEquals(inscripcionAntes, jdbc.queryForMap("SELECT * FROM alumno_convocatoria WHERE id = ?", ins));
		assertEquals(cargoConvAntes, jdbc.queryForMap("SELECT * FROM producto_alumno WHERE id = ?", cargoConv));
		assertNingunCobroAnuladoEnEntregaActiva();
	}

	@Test
	void revertirYEntregarConHilos_nuncaQuedaUnCobroAnuladoEnUnaEntregaActiva() throws Exception {
		for (int vuelta = 0; vuelta < 6; vuelta++) {
			long cargo = s.cargo(aProfA);
			long cobro = s.cobrar(cargo, s.tProf1);
			Callable<MvcResult> revertir = vuelta % 2 == 0
					? () -> peticion(pago(cargo, "{\"pagado\":false}"), s.tPropietario)
					: () -> peticion(post("/api/caja/profesor/cobros/" + cobro + "/deshacer"), s.tProf1);
			List<MvcResult> respuestas = enParaleloResultados(revertir,
					() -> peticion(entrega(s.e.sA, s.prof1, List.of(cobro), "10.50", null), s.tResp));
			List<Integer> estados = respuestas.stream().map(r -> r.getResponse().getStatus()).toList();
			for (MvcResult r : respuestas) {
				assertFalse(r.getResponse().getContentAsString().contains("a la vez"),
						"un 409 de concurrencia genérico: falta un bloqueo (" + r.getResponse().getContentAsString() + ")");
			}
			Map<String, Object> fila = jdbc.queryForMap("SELECT estado, entrega_caja_id FROM cobro WHERE id = ?", cobro);
			if (estados.get(1) == 201) {
				assertEquals(409, estados.get(0), "vuelta " + vuelta + ": " + estados);
				assertEquals("ACTIVO", fila.get("estado"));
				assertNotNull(fila.get("entrega_caja_id"));
				assertTrue(pagado(cargo));
			} else {
				assertEquals(409, estados.get(1), "vuelta " + vuelta + ": " + estados);
				assertTrue(estados.get(0) == 200 || estados.get(0) == 204, "vuelta " + vuelta + ": " + estados);
				assertEquals("ANULADO", fila.get("estado"));
				assertNull(fila.get("entrega_caja_id"));
				assertFalse(pagado(cargo));
			}
		}
		assertNingunCobroAnuladoEnEntregaActiva();
	}

	// ------------------------------------------------------------------ escuelas y registro de acciones

	@Test
	void aislamientoDeEscuela() throws Exception {
		long cobro = s.cobrar(s.cargo(aProfA), s.tProf1);
		// La gestión de otra escuela no ve la sede ni entrega sus cobros.
		assertEquals(404, estado(get("/api/caja/sedes/" + s.e.sA + "/resumen"), otra.tGestor));
		assertEquals(404, estado(get("/api/caja/sedes/" + s.e.sA + "/pendiente-entrega"), otra.tPropietario));
		assertEquals(404, estado(entrega(s.e.sA, s.prof1, List.of(cobro), "10.50", null), otra.tGestor));
		assertEquals(400, estado(entrega(otra.e.sA, s.prof1, List.of(cobro), "10.50", null), otra.tGestor),
				"un cobro de otra escuela no existe");
		assertNull(jdbc.queryForObject("SELECT entrega_caja_id FROM cobro WHERE id = ?", Long.class, cobro));
		// El responsable de otra escuela tampoco (su sede A no es esta).
		assertEquals(403, estado(get("/api/caja/sedes/" + s.e.sA + "/resumen"), otra.tResp));
		// Ni su profesor deshace un cobro de esta.
		assertEquals(403, estado(post("/api/caja/profesor/cobros/" + cobro + "/deshacer"), otra.tProf1));
		assertFalse(ids(leer(get("/api/caja/profesor/cobros-pendientes-entrega"), otra.tProf1, 200).get("cobros"),
				"cobroId").contains(cobro));
	}

	@Test
	void registroDeAcciones_quienCuandoEscuelaSedeTipoEIds_sinDatosPersonales() throws Exception {
		Escena n = new Escena("it-6b2-reg");
		long alumno = n.alumnoEnGrupo("E6B2REGISTRO", n.e.sA, n.e.gA);
		long cargo = n.cargo(alumno);
		long cobro = n.cobrar(cargo, n.tProf1);
		assertEquals(204, estado(post("/api/caja/profesor/cobros/" + cobro + "/deshacer"), n.tProf1));
		long cobro2 = n.cobrar(cargo, n.tProf1);
		JsonNode entrega = leer(entrega(n.e.sA, n.prof1, List.of(cobro2), "11.00", "Sobra; nota libre"), n.tResp, 201);
		long entregaId = entrega.get("id").asLong();
		long automatico = entrega.get("movimientoId").asLong();
		assertEquals(200, estado(anular("/api/caja/entregas/" + entregaId + "/anular", "motivo"), n.tPropietario));
		long mov = leer(movimiento(n.e.sA, "DEVOLUCION", "-1.00", alumno, "devolución"), n.tGestor, 201).get("id")
				.asLong();
		assertEquals(200, estado(anular("/api/caja/movimientos/" + mov + "/anular", "motivo"), n.tPropietario));

		List<Map<String, Object>> eventos = jdbc.queryForList("""
				SELECT * FROM auditoria_evento WHERE organizacion_id = ? AND modulo = 'caja' AND resumen LIKE 'CAJA %'
				ORDER BY id""", n.e.org);
		String sede = "sede=#" + n.e.sA;
		assertEvento(eventos.get(0), n.prof1, "CREATE", cobro,
				"CAJA COBRO_PROFESOR " + sede + " cobro=#" + cobro + " cargo=#" + cargo);
		assertEvento(eventos.get(1), n.prof1, "UPDATE", cobro,
				"CAJA COBRO_PROFESOR_DESHECHO " + sede + " cobro=#" + cobro + " cargo=#" + cargo);
		assertEvento(eventos.get(2), n.prof1, "CREATE", cobro2,
				"CAJA COBRO_PROFESOR " + sede + " cobro=#" + cobro2 + " cargo=#" + cargo);
		assertEvento(eventos.get(3), n.e.respA, "CREATE", entregaId, "CAJA ENTREGA_REGISTRADA " + sede + " entrega=#"
				+ entregaId + " cobros=[#" + cobro2 + "] movimiento=#" + automatico + " entregadoPor=#" + n.prof1);
		assertEvento(eventos.get(4), n.propietario, "UPDATE", entregaId, "CAJA ENTREGA_ANULADA " + sede
				+ " entrega=#" + entregaId + " cobros=[#" + cobro2 + "] movimientos=[#" + automatico + "]");
		assertEvento(eventos.get(5), n.e.gestor, "CREATE", mov,
				"CAJA MOVIMIENTO_CREADO " + sede + " movimiento=#" + mov + " alumno=#" + alumno);
		assertEvento(eventos.get(6), n.propietario, "UPDATE", mov, "CAJA MOVIMIENTO_ANULADO " + sede + " movimiento=#"
				+ mov);
		assertEquals(7, eventos.size());
		for (Map<String, Object> evento : eventos) {
			String resumen = (String) evento.get("resumen");
			assertFalse(resumen.contains("E6B2REGISTRO") || resumen.contains("PRUEBA") || resumen.contains("@")
					|| resumen.contains("nota libre") || resumen.contains("Usuario"), resumen);
			assertNotNull(evento.get("fecha_evento"));
		}
		// Un intento denegado o fallido no deja acción de caja.
		assertEquals(409, estado(post("/api/caja/profesor/cargos/" + cargo + "/cobrar"), n.tProf1));
		assertEquals(7L, jdbc.queryForObject(
				"SELECT COUNT(*) FROM auditoria_evento WHERE organizacion_id = ? AND modulo = 'caja' "
						+ "AND resumen LIKE 'CAJA %'", Long.class,
				n.e.org));
	}

	// ------------------------------------------------------------------ utilidades

	private void assertEvento(Map<String, Object> evento, long usuario, String accion, long recurso, String resumen) {
		assertEquals(usuario, numero(evento, "usuario_id"));
		assertEquals(accion, evento.get("accion"));
		assertEquals(recurso, numero(evento, "recurso_id"));
		assertEquals(resumen, evento.get("resumen"));
	}

	private void assertResumen(JsonNode r, String pendiente, String recibido, String diferencias, String saldo) {
		assertEquals(0, new BigDecimal(pendiente).compareTo(r.get("pendienteDeEntregar").decimalValue()), r.toString());
		assertEquals(0, new BigDecimal(recibido).compareTo(r.get("recibidoEnPeriodo").decimalValue()), r.toString());
		assertEquals(0, new BigDecimal(diferencias).compareTo(r.get("diferenciasEnPeriodo").decimalValue()),
				r.toString());
		assertEquals(0, new BigDecimal(saldo).compareTo(r.get("saldoCaja").decimalValue()), r.toString());
	}

	/**
	 * Con una «entrega en curso» en otra conexión (cobro bloqueado y ya apuntado a una entrega sin confirmar), lanza la
	 * reversión: debe esperar al bloqueo del cobro y, al confirmar la entrega, responder 409 sin anular el cobro.
	 */
	private void revertirDuranteEntrega(long cobro, Callable<MvcResult> revertir) throws Exception {
		ExecutorService hilo = Executors.newSingleThreadExecutor();
		try (Connection conexion = dataSource.getConnection()) {
			conexion.setAutoCommit(false);
			long entrega = entregaEnCurso(conexion, cobro);
			Future<MvcResult> peticion = hilo.submit(revertir);
			Thread.sleep(1500);
			assertFalse(peticion.isDone(), "la reversión debe esperar al bloqueo de la fila del cobro");
			conexion.commit();
			MvcResult r = peticion.get(60, TimeUnit.SECONDS);
			assertEquals(409, r.getResponse().getStatus());
			// El 409 es el del cobro entregado (visto con la fila bloqueada), no el genérico de concurrencia.
			assertTrue(r.getResponse().getContentAsString().contains("entrega de caja"),
					r.getResponse().getContentAsString());
			Map<String, Object> fila = jdbc.queryForMap("SELECT estado, entrega_caja_id FROM cobro WHERE id = ?", cobro);
			assertEquals("ACTIVO", fila.get("estado"));
			assertEquals(entrega, ((Number) fila.get("entrega_caja_id")).longValue());
		} finally {
			hilo.shutdownNow();
		}
	}

	/** En la conexión dada (sin confirmar): bloquea el cobro, crea una entrega y lo mete en ella, como CajaService. */
	private long entregaEnCurso(Connection conexion, long cobro) throws Exception {
		try (PreparedStatement bloqueo = conexion.prepareStatement("SELECT id FROM cobro WHERE id = ? FOR UPDATE")) {
			bloqueo.setLong(1, cobro);
			bloqueo.executeQuery().close();
		}
		long entrega;
		try (PreparedStatement alta = conexion.prepareStatement("""
				INSERT INTO entrega_caja (organizacion_id, sede_id, entregado_por_usuario_id, recibido_por_usuario_id,
				  importe_declarado, importe_contado, fecha) VALUES (?, ?, ?, ?, 10.50, 10.50, NOW(6))""",
				PreparedStatement.RETURN_GENERATED_KEYS)) {
			alta.setLong(1, s.e.org);
			alta.setLong(2, s.e.sA);
			alta.setLong(3, s.prof1);
			alta.setLong(4, s.e.gestor);
			alta.executeUpdate();
			try (ResultSet claves = alta.getGeneratedKeys()) {
				claves.next();
				entrega = claves.getLong(1);
			}
		}
		try (PreparedStatement meter = conexion.prepareStatement(
				"UPDATE cobro SET entrega_caja_id = ?, version = version + 1 WHERE id = ?")) {
			meter.setLong(1, entrega);
			meter.setLong(2, cobro);
			meter.executeUpdate();
		}
		return entrega;
	}

	private void assertNingunCobroAnuladoEnEntregaActiva() {
		assertEquals(0L, jdbc.queryForObject("""
				SELECT COUNT(*) FROM cobro c JOIN entrega_caja e ON e.id = c.entrega_caja_id
				WHERE c.estado = 'ANULADO' AND e.estado = 'ACTIVA'"""
				, Long.class));
	}

	private long contarEntregas(Escena x) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM entrega_caja WHERE organizacion_id = ?", Long.class, x.e.org);
	}

	private long idCobroActivo(long cargo) {
		return jdbc.queryForObject("SELECT id FROM cobro WHERE producto_alumno_id = ? AND estado = 'ACTIVO'", Long.class,
				cargo);
	}

	private boolean derechoAExamen(long alumno) {
		return Boolean.TRUE.equals(
				jdbc.queryForObject("SELECT tiene_derecho_examen FROM alumno WHERE id = ?", Boolean.class, alumno));
	}

	private boolean pagado(long cargo) {
		return Boolean.TRUE.equals(
				jdbc.queryForObject("SELECT pagado FROM producto_alumno WHERE id = ?", Boolean.class, cargo));
	}

	private MockHttpServletRequestBuilder entrega(long sede, Long entregadoPor, List<Long> cobros, String contado,
			String nota) throws Exception {
		Map<String, Object> cuerpo = new java.util.HashMap<>();
		cuerpo.put("entregadoPorUsuarioId", entregadoPor);
		cuerpo.put("cobroIds", cobros);
		cuerpo.put("importeContado", new BigDecimal(contado));
		cuerpo.put("nota", nota);
		return post("/api/caja/sedes/" + sede + "/entregas").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(cuerpo));
	}

	private MockHttpServletRequestBuilder movimiento(long sede, String tipo, String importe, Long alumno, String nota)
			throws Exception {
		Map<String, Object> cuerpo = new java.util.HashMap<>();
		cuerpo.put("tipo", tipo);
		cuerpo.put("importe", new BigDecimal(importe));
		cuerpo.put("alumnoId", alumno);
		cuerpo.put("nota", nota);
		return post("/api/caja/sedes/" + sede + "/movimientos").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(cuerpo));
	}

	private MockHttpServletRequestBuilder anular(String ruta, String motivo) throws Exception {
		Map<String, Object> cuerpo = new java.util.HashMap<>();
		cuerpo.put("motivo", motivo);
		return post(ruta).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo));
	}

	private static MockHttpServletRequestBuilder venta(long alumno, long producto) {
		return post("/api/caja/profesor/alumnos/" + alumno + "/productos/" + producto + "/cobrar");
	}

	/**
	 * Producto del catálogo de la escuela; {@code tipo} y {@code codigo} solo se fijan al crearlo. Marcado como «Lo
	 * puede vender el profesor» (decisión 182): la marca no salta la regla fija de la 6b-2.
	 */
	private long producto(Escena x, String concepto, String tipo, String codigo) {
		jdbc.update("INSERT INTO producto (concepto, precio, codigo, tipo, organizacion_id, vendible_profesor) "
				+ "VALUES (?, 15.0, ?, ?, ?, TRUE)", concepto, codigo, tipo, x.e.org);
		return jdbc.queryForObject("SELECT MAX(id) FROM producto WHERE organizacion_id = ?", Long.class, x.e.org);
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

	private static Set<String> campos(JsonNode nodo) {
		Set<String> campos = new HashSet<>();
		for (Iterator<String> it = nodo.fieldNames(); it.hasNext();) {
			campos.add(it.next());
		}
		return campos;
	}

	private static Set<Long> ids(JsonNode lista, String campo) {
		return new HashSet<>(idsEnOrden(lista, campo));
	}

	private static List<Long> idsEnOrden(JsonNode lista, String campo) {
		List<Long> ids = new ArrayList<>();
		lista.forEach(n -> ids.add(n.get(campo).asLong()));
		return ids;
	}

	private static List<Long> idsEnOrden(JsonNode lista) {
		List<Long> ids = new ArrayList<>();
		lista.forEach(n -> ids.add(n.asLong()));
		return ids;
	}

	private static JsonNode fila(JsonNode lista, String campo, long valor) {
		for (JsonNode n : lista) {
			if (n.get(campo).asLong() == valor) {
				return n;
			}
		}
		throw new AssertionError("Sin fila con " + campo + "=" + valor + ": " + lista);
	}

	private static LocalDate fecha(JsonNode nodo) {
		return nodo.isArray() ? LocalDate.of(nodo.get(0).asInt(), nodo.get(1).asInt(), nodo.get(2).asInt())
				: LocalDate.parse(nodo.asText());
	}

	private static long numero(Map<String, Object> fila, String columna) {
		return ((Number) fila.get(columna)).longValue();
	}

	private static List<Integer> enParalelo(Callable<Integer> primera, Callable<Integer> segunda) throws Exception {
		List<Integer> estados = enParaleloSinOrdenar(primera, segunda);
		estados.sort(Integer::compareTo);
		return estados;
	}

	/** Las dos a la vez; las respuestas en el orden de las acciones. */
	private static List<MvcResult> enParaleloResultados(Callable<MvcResult> primera, Callable<MvcResult> segunda)
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

	/** Las dos a la vez; los estados en el orden de las acciones. */
	private static List<Integer> enParaleloSinOrdenar(Callable<Integer> primera, Callable<Integer> segunda)
			throws Exception {
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
			return estados;
		} finally {
			salida.countDown();
			executor.shutdownNow();
		}
	}
}
