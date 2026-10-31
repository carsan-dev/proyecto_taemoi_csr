package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
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
 * Entrega 6-1 (entrega6-plan.md §3.2, §4.1) contra MySQL: el responsable de sede gestiona los grupos y turnos de SUS
 * sedes (D6-5): listados filtrados, lectura y edición por id, altas con sede obligatoria entre las suyas, ninguna alta
 * en una sede inactiva (D6-17, también para la gestión), el turno siempre con grupo, y el listado de asistencia solo
 * con los turnos de sus sedes. Datos sintéticos en una escuela nueva ({@link EscuelaConSedesDePrueba}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ResponsableSedeGruposIntegrationTest extends MySqlIntegrationTest {

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;

	@MockitoBean JavaMailSender mailSender;

	EscuelaConSedesDePrueba e;
	EscuelaConSedesDePrueba otra;
	String tokenA;
	String tokenAC;
	String tokenGestor;

	@BeforeAll
	void preparar() {
		e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-e6-grupos");
		otra = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-e6-grupos-otra");
		e.crearResponsablesSolos(usuarios);
		tokenA = e.token(jwtService, usuarios, e.respSoloA);
		tokenAC = e.token(jwtService, usuarios, e.respSoloAC);
		tokenGestor = e.token(jwtService, usuarios, e.gestor);
	}

	@AfterEach
	void sinFugaDeOrganizacion() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
	}

	// ------------------------------------------------------------------ grupos

	@Test
	void grupos_listadoSoloDeSusSedes() throws Exception {
		Set<Long> deA = ids(leer("/api/grupos/resumen", tokenA));
		assertEquals(Set.of(e.gA, e.gA2), deA);
		for (long grupo : deA) {
			assertEquals(e.sA, leer("/api/grupos/" + grupo, tokenA).get("sedeId").asLong(), "el JSON lleva sedeId (D6-5)");
		}
		assertEquals(Set.of(e.gA, e.gA2, e.gC), ids(leer("/api/grupos/resumen", tokenAC)), "también su sede inactiva");
		assertTrue(ids(leer("/api/grupos/resumen", tokenGestor)).containsAll(Set.of(e.gA, e.gA2, e.gB, e.gC, e.gSin)));
	}

	@Test
	void grupos_porIdSoloDeSusSedes() throws Exception {
		assertEquals(200, estado(get("/api/grupos/" + e.gA), tokenA));
		assertEquals(200, estado(get("/api/grupos/" + e.gA + "/turnos"), tokenA));
		for (long ajeno : List.of(e.gB, e.gSin)) {
			assertEquals(403, estado(get("/api/grupos/" + ajeno), tokenA));
			assertEquals(403, estado(get("/api/grupos/" + ajeno + "/turnos"), tokenA));
			assertEquals(403, estado(put("/api/grupos/" + ajeno).contentType(MediaType.APPLICATION_JSON)
					.content(grupo("Cambio", null)), tokenA));
			assertEquals(403, estado(delete("/api/grupos/" + ajeno), tokenA));
		}
		// Conteo por tipos: pieza 9-5, abierto y solo con los alumnos de su alcance (ResponsableSedePantallas...).
		assertEquals(200, estado(get("/api/grupos/conteo-alumnos"), tokenA));
	}

	@Test
	void grupos_altaEnSuSede() throws Exception {
		// Una sede propia: automática.
		MvcResult automatica = peticion(post("/api/grupos").contentType(MediaType.APPLICATION_JSON)
				.content(grupo("IT E6 nuevo A " + e.marca, null)), tokenA);
		assertEquals(201, automatica.getResponse().getStatus(), automatica.getResponse().getContentAsString());
		long creado = json.readTree(automatica.getResponse().getContentAsString()).get("id").asLong();
		assertEquals(e.sA, sedeDelGrupo(creado));

		// Varias propias: obligatoria (400); ajena 403; inactiva 400 (D6-17).
		assertEquals(400, estado(post("/api/grupos").contentType(MediaType.APPLICATION_JSON)
				.content(grupo("IT E6 sin sede " + e.marca, null)), tokenAC));
		assertEquals(403, estado(post("/api/grupos").contentType(MediaType.APPLICATION_JSON)
				.content(grupo("IT E6 ajena " + e.marca, e.sB)), tokenAC));
		assertEquals(400, estado(post("/api/grupos").contentType(MediaType.APPLICATION_JSON)
				.content(grupo("IT E6 inactiva " + e.marca, e.sC)), tokenAC));

		// Editar: cambiar a una sede ajena, 403; sin sede en el cuerpo, conserva la suya.
		assertEquals(403, estado(put("/api/grupos/" + creado).contentType(MediaType.APPLICATION_JSON)
				.content(grupo("IT E6 movido " + e.marca, e.sB)), tokenA));
		assertEquals(200, estado(put("/api/grupos/" + creado).contentType(MediaType.APPLICATION_JSON)
				.content(grupo("IT E6 renombrado " + e.marca, null)), tokenA));
		assertEquals(e.sA, sedeDelGrupo(creado));
		assertEquals(204, estado(delete("/api/grupos/" + creado), tokenA));

		// Gestión con varias sedes: sin sede, 400 (decisión 368); en una sede inactiva, 400 (D6-17).
		assertEquals(400, estado(post("/api/grupos").contentType(MediaType.APPLICATION_JSON)
				.content(grupo("IT E6 gestion " + e.marca, null)), tokenGestor));
		assertEquals(400, estado(post("/api/grupos").contentType(MediaType.APPLICATION_JSON)
				.content(grupo("IT E6 gestion inactiva " + e.marca, e.sC)), tokenGestor));
	}

	/**
	 * Pieza 9-5: un alta no admite id. Con él, {@code save} haría merge sobre el grupo que existe y el POST sobrescribiría
	 * un grupo de otra sede sin pasar por el permiso del PUT. 400 para todos, también para la gestión.
	 */
	@Test
	void grupos_altaConIdDeOtroGrupo_400ySinTocarlo() throws Exception {
		long grupos = jdbc.queryForObject("SELECT COUNT(*) FROM grupo WHERE organizacion_id = ?", Long.class, e.org);
		Map<String, Object> grupoB = estadoDelGrupo(e.gB);
		Map<String, Object> grupoA = estadoDelGrupo(e.gA);
		Map<String, Object> deOtraEscuela = estadoDelGrupo(otra.gA);

		for (String token : List.of(tokenA, tokenGestor)) {
			assertEquals(400, estado(post("/api/grupos").contentType(MediaType.APPLICATION_JSON)
					.content(grupoConId(e.gB, "Robado", e.sA)), token), "grupo de otra sede");
			assertEquals(400, estado(post("/api/grupos").contentType(MediaType.APPLICATION_JSON)
					.content(grupoConId(e.gA, "Pisado", e.sA)), token), "grupo de la misma sede");
			assertEquals(400, estado(post("/api/grupos").contentType(MediaType.APPLICATION_JSON)
					.content(grupoConId(otra.gA, "De otra escuela", e.sA)), token), "grupo de otra escuela");
			assertEquals(400, estado(post("/api/grupos").contentType(MediaType.APPLICATION_JSON)
					.content(grupoConId(Long.MAX_VALUE, "Sin existir", e.sA)), token), "id que no existe");
		}

		assertEquals(grupoB, estadoDelGrupo(e.gB));
		assertEquals(grupoA, estadoDelGrupo(e.gA));
		assertEquals(deOtraEscuela, estadoDelGrupo(otra.gA));
		assertEquals(grupos, jdbc.queryForObject("SELECT COUNT(*) FROM grupo WHERE organizacion_id = ?", Long.class,
				e.org), "ningún grupo nuevo");
	}

	private String grupoConId(long id, String nombre, long sede) {
		return "{\"id\":" + id + ",\"nombre\":\"" + nombre + "\",\"deporte\":\"TAEKWONDO\",\"sedeId\":" + sede + "}";
	}

	private Map<String, Object> estadoDelGrupo(long grupo) {
		return Map.of("fila", jdbc.queryForMap("SELECT * FROM grupo WHERE id = ?", grupo),
				"alumnos", jdbc.queryForList("SELECT alumno_id FROM alumno_grupo WHERE grupo_id = ? ORDER BY alumno_id",
						Long.class, grupo),
				"turnos", jdbc.queryForList("SELECT id FROM turno WHERE grupo_id = ? ORDER BY id", Long.class, grupo));
	}

	// ------------------------------------------------------------------ lotes con un id ajeno

	/** Lote con un alumno de otra escuela: 403 entero, ni en el grupo ni en sus turnos (alumno_turno). */
	@Test
	void lote_conAlumnoDeOtraEscuela_403ySinAnadirNadaAlGrupoNiASusTurnos() throws Exception {
		long turno = turnoEn(e.gA2, "Martes");
		try {
			assertEquals(403, estado(post("/api/grupos/" + e.gA2 + "/alumnos").contentType(MediaType.APPLICATION_JSON)
					.content("[" + e.aA + "," + otra.aA + "]"), tokenA));

			assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM alumno_grupo WHERE grupo_id = ?", Integer.class,
					e.gA2));
			assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM alumno_turno WHERE turno_id = ?", Integer.class,
					turno));
		} finally {
			jdbc.update("DELETE FROM turno WHERE id = ?", turno);
		}
	}

	/** Pasar lista con un alumno de otra escuela: 400 sin crear la clase ni cambiar una ya guardada. */
	@Test
	void pasarLista_conAlumnoDeOtraEscuela_400ySinCambiarClaseNiAsistencias() throws Exception {
		long turno = turnoEn(e.gA, "Lunes");
		e.enTurno(turno, e.aGrupoA);
		String lunes = LocalDate.now().with(TemporalAdjusters.previous(DayOfWeek.MONDAY)).toString();
		String ruta = "/api/grupos/" + e.gA + "/clases/" + turno + "/" + lunes;

		assertEquals(400, estado(lista(ruta, e.aGrupoA, otra.aA), tokenA), "clase sin guardar");
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM clase_asistencia WHERE turno_id = ?", Integer.class,
				turno));

		assertEquals(200, estado(lista(ruta, e.aGrupoA), tokenA));
		Map<String, Object> clase = jdbc.queryForMap("SELECT * FROM clase_asistencia WHERE turno_id = ?", turno);
		List<Map<String, Object>> asistencias = jdbc.queryForList(
				"SELECT * FROM asistencia_alumno WHERE clase_id = ? ORDER BY id", clase.get("id"));
		assertEquals(1, asistencias.size());

		assertEquals(400, estado(lista(ruta, otra.aA), tokenA), "clase guardada");
		assertEquals(400, estado(lista(ruta, e.aGrupoA, otra.aA), tokenA), "clase guardada, con un alumno propio");

		assertEquals(clase, jdbc.queryForMap("SELECT * FROM clase_asistencia WHERE turno_id = ?", turno));
		assertEquals(asistencias,
				jdbc.queryForList("SELECT * FROM asistencia_alumno WHERE clase_id = ? ORDER BY id", clase.get("id")));
		jdbc.update("DELETE FROM asistencia_alumno WHERE clase_id = ?", clase.get("id"));
		jdbc.update("DELETE FROM clase_asistencia WHERE turno_id = ?", turno);
		jdbc.update("DELETE FROM alumno_turno WHERE turno_id = ?", turno);
		jdbc.update("DELETE FROM turno WHERE id = ?", turno);
	}

	private MockHttpServletRequestBuilder lista(String ruta, long... presentes) throws Exception {
		return put(ruta).contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("presentes", presentes)));
	}

	// ------------------------------------------------------------------ turnos

	/**
	 * Decisión 374: al cambiar el día el turno conserva su grupo (y su sede); vale cualquier día de lunes a domingo y
	 * un valor que no es un día da 400. El responsable sigue limitado a sus sedes (403 en turnos ajenos).
	 */
	@Test
	void turnos_cambiarElDiaConservaSuGrupo() throws Exception {
		long deA = turnoEn(e.gA, "Lunes");
		assertEquals(200, estado(put("/api/turnos/" + deA).contentType(MediaType.APPLICATION_JSON)
				.content(turnoEl("martes")), tokenA));
		assertEquals(e.gA, grupoDelTurno(deA));

		long deB = turnoEn(e.gB, "Martes");
		assertEquals(200, estado(put("/api/turnos/" + deB).contentType(MediaType.APPLICATION_JSON)
				.content(turnoEl("lunes")), tokenGestor));
		assertEquals(e.gB, grupoDelTurno(deB), "no pasa a la sede A");

		for (String dia : List.of("viernes", "sábado", "domingo")) {
			assertEquals(200, estado(put("/api/turnos/" + deA).contentType(MediaType.APPLICATION_JSON)
					.content(turnoEl(dia)), tokenA), dia);
			assertEquals(e.gA, grupoDelTurno(deA));
		}
		assertEquals(400, estado(put("/api/turnos/" + deA).contentType(MediaType.APPLICATION_JSON)
				.content(turnoEl("foo")), tokenA));
		assertEquals(403, estado(put("/api/turnos/" + deB).contentType(MediaType.APPLICATION_JSON)
				.content(turnoEl("martes")), tokenA), "turno de otra sede");
		assertEquals(e.gB, grupoDelTurno(deB));
		jdbc.update("DELETE FROM turno WHERE id IN (?, ?)", deA, deB);
	}

	private long turnoEn(long grupo, String dia) {
		jdbc.update("""
				INSERT INTO turno (dia_semana, hora_inicio, hora_fin, tipo, grupo_id, organizacion_id)
				VALUES (?, '07:00', '08:00', NULL, ?, ?)""", dia, grupo, e.org);
		return jdbc.queryForObject("SELECT MAX(id) FROM turno WHERE organizacion_id = ?", Long.class, e.org);
	}

	private Long grupoDelTurno(long turno) {
		return jdbc.queryForObject("SELECT grupo_id FROM turno WHERE id = ?", Long.class, turno);
	}

	private static String turnoEl(String dia) {
		return "{\"diaSemana\":\"" + dia + "\",\"horaInicio\":\"07:00\",\"horaFin\":\"08:00\"}";
	}

	/** Pieza 9-5: el alta de turno no lee el id del cuerpo: crea uno nuevo y el que nombra sigue como estaba. */
	@Test
	void turnos_altaConIdDeOtroTurno_noLoSobrescribe() throws Exception {
		Map<String, Object> antes = jdbc.queryForMap("SELECT * FROM turno WHERE id = ?", e.tB);
		int enGrupoA = turnosDe(e.gA);

		assertEquals(201, estado(post("/api/turnos/crear-asignando-grupo").contentType(MediaType.APPLICATION_JSON)
				.content("{\"id\":" + e.tB + ",\"diaSemana\":\"Viernes\",\"horaInicio\":\"20:00\",\"horaFin\":\"21:00\","
						+ "\"tipoGrupo\":\"IT\",\"grupoId\":" + e.gA + "}"), tokenA));

		assertEquals(antes, jdbc.queryForMap("SELECT * FROM turno WHERE id = ?", e.tB));
		assertEquals(enGrupoA + 1, turnosDe(e.gA));
		jdbc.update("DELETE FROM turno WHERE grupo_id = ? AND dia_semana = 'Viernes'", e.gA);
	}

	@Test
	void turnos_listadoSoloDeSusSedes() throws Exception {
		JsonNode deA = leer("/api/turnos/resumen", tokenA);
		Set<Long> turnosDeA = ids(deA);
		assertTrue(turnosDeA.contains(e.tA));
		assertFalse(turnosDeA.contains(e.tB) || turnosDeA.contains(e.tSin), "sin el de B ni el turno sin grupo");
		deA.forEach(t -> assertTrue(Set.of(e.gA, e.gA2).contains(t.get("grupoId").asLong()), t.toString()));
		assertTrue(ids(leer("/api/turnos/resumen", tokenGestor)).containsAll(Set.of(e.tA, e.tB, e.tSin)));
		Set<Long> deAC = ids(leer("/api/turnos/resumen", tokenAC));
		assertTrue(deAC.contains(e.tA), "con dos sedes (A y C), igual");
		assertFalse(deAC.contains(e.tB) || deAC.contains(e.tSin), "sin el de B ni el turno sin grupo");
		assertEquals(200, estado(get("/api/turnos/" + e.tA), tokenA));
		for (long ajeno : List.of(e.tB, e.tSin)) {
			assertEquals(403, estado(get("/api/turnos/" + ajeno), tokenA));
			assertEquals(403, estado(put("/api/turnos/" + ajeno).contentType(MediaType.APPLICATION_JSON)
					.content(turno(null)), tokenA));
			assertEquals(403, estado(delete("/api/turnos/" + ajeno), tokenA));
		}
		assertEquals(403, estado(post("/api/grupos/" + e.gA + "/turnos/" + e.tB), tokenA), "turno de otra sede");
		assertEquals(403, estado(post("/api/grupos/" + e.gA + "/turnos/" + e.tSin), tokenA), "turno sin grupo");
	}

	@Test
	void turnos_altaConGrupoDeSuSede() throws Exception {
		int antes = turnosDe(e.gA);
		assertEquals(201, estado(post("/api/turnos/crear-asignando-grupo").contentType(MediaType.APPLICATION_JSON)
				.content(turno(e.gA)), tokenA));
		assertEquals(antes + 1, turnosDe(e.gA));
		assertEquals(403, estado(post("/api/turnos/crear-asignando-grupo").contentType(MediaType.APPLICATION_JSON)
				.content(turno(e.gB)), tokenA));
		assertEquals(403, estado(post("/api/turnos/crear-asignando-grupo").contentType(MediaType.APPLICATION_JSON)
				.content(turno(e.gSin)), tokenA));
		assertEquals(400, estado(post("/api/turnos/crear-asignando-grupo").contentType(MediaType.APPLICATION_JSON)
				.content(turno(e.gC)), tokenAC), "D6-17: sede inactiva");
		assertEquals(400, estado(post("/api/turnos/crear-asignando-grupo").contentType(MediaType.APPLICATION_JSON)
				.content(turno(e.gC)), tokenGestor), "D6-17 también para la gestión");
		assertEquals(403, estado(post("/api/turnos/crear").contentType(MediaType.APPLICATION_JSON)
				.content(turno(null)), tokenA), "un turno sin grupo no tiene sede");
		assertEquals(1, turnosDe(e.gB), "solo el turno inicial de gB");
		assertEquals(0, turnosDe(e.gC), "ninguno en la sede inactiva");
	}

	// ------------------------------------------------------------------ asistencia (6-1d)

	@Test
	void asistencia_soloTurnosDeSusSedes() throws Exception {
		String deA = texto(pdf(tokenA));
		assertTrue(deA.contains("E6A" + e.marca), "alumno del turno de su sede");
		assertFalse(deA.contains("E6B" + e.marca), "alumno de un turno de otra sede");
		String deGestion = texto(pdf(tokenGestor));
		assertTrue(deGestion.contains("E6A" + e.marca) && deGestion.contains("E6B" + e.marca));
	}

	// ------------------------------------------------------------------ utilidades

	private byte[] pdf(String token) throws Exception {
		MvcResult r = peticion(get("/api/informes/asistencia").param("year", "2026").param("month", "9")
				.param("grupo", "lunes").param("deporte", "TAEKWONDO"), token);
		assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		return r.getResponse().getContentAsByteArray();
	}

	private static String texto(byte[] pdf) {
		try (PDDocument documento = PDDocument.load(pdf)) {
			return new PDFTextStripper().getText(documento).replaceAll("\\s+", "");
		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	private String grupo(String nombre, Long sede) {
		return "{\"nombre\":\"" + nombre + "\",\"deporte\":\"TAEKWONDO\"" + (sede == null ? "" : ",\"sedeId\":" + sede)
				+ "}";
	}

	private String turno(Long grupo) {
		return "{\"diaSemana\":\"Lunes\",\"horaInicio\":\"20:00\",\"horaFin\":\"21:00\",\"tipoGrupo\":\"IT\""
				+ (grupo == null ? "" : ",\"grupoId\":" + grupo) + "}";
	}

	private Long sedeDelGrupo(long grupo) {
		return jdbc.queryForObject("SELECT sede_id FROM grupo WHERE id = ?", Long.class, grupo);
	}

	private int turnosDe(long grupo) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM turno WHERE grupo_id = ?", Integer.class, grupo);
	}

	private int estado(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		return peticion(peticion, token).getResponse().getStatus();
	}

	private MvcResult peticion(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		return mvc.perform(peticion.cookie(new Cookie("jwt", token))).andReturn();
	}

	private JsonNode leer(String ruta, String token) throws Exception {
		MvcResult r = peticion(get(ruta), token);
		assertEquals(200, r.getResponse().getStatus(), ruta + ": " + r.getResponse().getContentAsString());
		return json.readTree(r.getResponse().getContentAsString());
	}

	private static Set<Long> ids(JsonNode lista) {
		Set<Long> ids = new HashSet<>();
		lista.forEach(n -> ids.add(n.get("id").asLong()));
		assertEquals(lista.size(), ids.size(), "sin repetidos");
		return ids;
	}
}
