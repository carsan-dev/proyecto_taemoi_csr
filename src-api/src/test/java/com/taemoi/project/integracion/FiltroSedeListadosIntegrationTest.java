package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
 * Pieza 9-6 (decisiones 142 y 376) contra MySQL: {@code sedeId} en los listados de Alumnos, Grupos, Preinscripciones y
 * Convocatorias y en el PDF de asistencia, combinado por AND con el alcance. Sede de otra escuela o inexistente: 400,
 * como Tesorería. Responsable fuera de sus sedes: 403, sin ampliar nunca el alcance. Sin parámetro, lo de siempre.
 * Escuela sintética de {@link EscuelaConSedesDePrueba}; {@code otra} es una segunda escuela con sus propias sedes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FiltroSedeListadosIntegrationTest extends MySqlIntegrationTest {

	private static final long INEXISTENTE = 999_999_999L;

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
	String tokenGestorOtra;
	long plantilla;
	String pA;
	String pB;
	String pSinSede;
	long convocatoria;

	@BeforeAll
	void preparar() {
		e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-9-6");
		otra = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-9-6-otra");
		e.crearResponsablesSolos(usuarios);
		tokenA = e.token(jwtService, usuarios, e.respSoloA);
		tokenAC = e.token(jwtService, usuarios, e.respSoloAC);
		tokenGestor = e.token(jwtService, usuarios, e.gestor);
		tokenGestorOtra = otra.token(jwtService, usuarios, otra.gestor);
		plantilla = jdbc.queryForObject("""
				SELECT p.id FROM plantilla_preinscripcion p JOIN disciplina d ON d.id = p.disciplina_id
				WHERE p.organizacion_id = 1 AND d.codigo = 'TAEKWONDO' ORDER BY p.id LIMIT 1""", Long.class);

		pA = preinscripcion(e.gA, e.tA);
		pB = preinscripcion(e.gB, e.tB);
		pSinSede = preinscripcion(e.gSin, e.tA);

		jdbc.update("INSERT INTO convocatoria (fecha_convocatoria, organizacion_id, disciplina_id) VALUES "
				+ "('2026-06-01', ?, ?)", e.org, e.disciplina);
		convocatoria = jdbc.queryForObject("SELECT MAX(id) FROM convocatoria WHERE organizacion_id = ?", Long.class,
				e.org);
		inscribir(e.aA);
		inscribir(e.aGrupoA);
		inscribir(e.aB);
		inscribir(e.aSinSede);

		// Va a una clase de la sede A y su sede principal es B: la hoja de A lo lista (decisión 142).
		e.enTurno(e.tA, e.aGrupoA);
	}

	@AfterEach
	void sinFugaDeOrganizacion() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
	}

	// ------------------------------------------------------------------ alumnos: por sede principal

	@Test
	void alumnos_gestionFiltraPorSedePrincipal() throws Exception {
		assertTrue(alumnos("/api/alumnos?page=1&size=100", tokenGestor)
				.containsAll(Set.of(e.aA, e.aB, e.aGrupoA, e.aSinSede)), "sin parámetro, todos");
		assertEquals(Set.of(e.aA), alumnos("/api/alumnos?page=1&size=100&sedeId=" + e.sA, tokenGestor),
				"aGrupoA está en un grupo de A, pero su sede principal es B");
		assertEquals(Set.of(e.aB, e.aGrupoA), alumnos("/api/alumnos?page=1&size=100&sedeId=" + e.sB, tokenGestor));
	}

	@Test
	void alumnos_conDeporteTambienFiltra() throws Exception {
		assertEquals(Set.of(e.aB, e.aGrupoA),
				alumnos("/api/alumnos?page=1&size=100&deporte=TAEKWONDO&sedeId=" + e.sB, tokenGestor));
	}

	@Test
	void alumnos_elTotalPaginadoEsElDeLaSede() throws Exception {
		JsonNode pagina = leer("/api/alumnos?page=1&size=1&sedeId=" + e.sB, tokenGestor);
		assertEquals(2, pagina.get("totalElements").asInt());
		assertEquals(2, pagina.get("totalPages").asInt());
	}

	@Test
	void alumnos_responsableAcotaDentroDeSuAlcance() throws Exception {
		assertTrue(alumnos("/api/alumnos?page=1&size=100", tokenA).containsAll(Set.of(e.aA, e.aGrupoA)),
				"sin parámetro, su alcance D9");
		assertEquals(Set.of(e.aA), alumnos("/api/alumnos?page=1&size=100&sedeId=" + e.sA, tokenA));
		assertEquals(403, estado("/api/alumnos?page=1&size=100&sedeId=" + e.sB, tokenA),
				"una sede ajena no amplía el alcance");
	}

	@Test
	void alumnos_sedeDeOtraEscuelaOInexistente() throws Exception {
		assertEquals(400, estado("/api/alumnos?page=1&size=100&sedeId=" + otra.sA, tokenGestor));
		assertEquals(400, estado("/api/alumnos?page=1&size=100&sedeId=" + INEXISTENTE, tokenGestor));
		assertEquals(400, estado("/api/alumnos?page=1&size=100&sedeId=" + e.sA, tokenGestorOtra));
		assertEquals(400, estado("/api/alumnos?page=1&size=100&sedeId=" + otra.sA, tokenA));
	}

	// ------------------------------------------------------------------ grupos: por sede del grupo

	@Test
	void grupos_gestionFiltraPorSedeDelGrupo() throws Exception {
		assertEquals(Set.of(e.gA, e.gA2, e.gB, e.gC, e.gSin), ids(leer("/api/grupos/resumen", tokenGestor)));
		assertEquals(Set.of(e.gA, e.gA2), ids(leer("/api/grupos/resumen?sedeId=" + e.sA, tokenGestor)));
		assertEquals(Set.of(e.gC), ids(leer("/api/grupos/resumen?sedeId=" + e.sC, tokenGestor)), "también inactiva");
	}

	@Test
	void grupos_responsableAcotaDentroDeSuAlcance() throws Exception {
		assertEquals(Set.of(e.gA, e.gA2, e.gC), ids(leer("/api/grupos/resumen", tokenAC)));
		assertEquals(Set.of(e.gC), ids(leer("/api/grupos/resumen?sedeId=" + e.sC, tokenAC)));
		assertEquals(403, estado("/api/grupos/resumen?sedeId=" + e.sB, tokenA));
	}

	@Test
	void grupos_sedeDeOtraEscuelaOInexistente() throws Exception {
		assertEquals(400, estado("/api/grupos/resumen?sedeId=" + otra.sA, tokenGestor));
		assertEquals(400, estado("/api/grupos/resumen?sedeId=" + INEXISTENTE, tokenGestor));
		assertEquals(400, estado("/api/grupos/resumen?sedeId=" + e.sA, tokenGestorOtra));
	}

	// ------------------------------------------------------------------ preinscripciones: por la sede de su grupo

	@Test
	void preinscripciones_gestionFiltraPorSedeDelGrupo() throws Exception {
		assertEquals(Set.of(pA, pB, pSinSede), referencias(leer("/api/preinscripciones?size=100", tokenGestor)));
		JsonNode deA = leer("/api/preinscripciones?size=100&sedeId=" + e.sA, tokenGestor);
		assertEquals(Set.of(pA), referencias(deA));
		assertEquals(1, deA.get("totalElements").asInt());
	}

	@Test
	void preinscripciones_responsableAcotaDentroDeSuAlcance() throws Exception {
		assertEquals(Set.of(pA), referencias(leer("/api/preinscripciones?size=100&sedeId=" + e.sA, tokenA)));
		assertEquals(403, estado("/api/preinscripciones?size=100&sedeId=" + e.sB, tokenA));
	}

	@Test
	void preinscripciones_sedeDeOtraEscuelaOInexistente() throws Exception {
		assertEquals(400, estado("/api/preinscripciones?sedeId=" + otra.sA, tokenGestor));
		assertEquals(400, estado("/api/preinscripciones?sedeId=" + INEXISTENTE, tokenGestor));
		assertEquals(400, estado("/api/preinscripciones?sedeId=" + e.sA, tokenGestorOtra));
	}

	// ------------------------------------------------------------------ convocatorias: por sede principal del alumno

	@Test
	void convocatorias_gestionFiltraLosInscritosPorSedePrincipal() throws Exception {
		String ruta = "/api/convocatorias/" + convocatoria + "/alumnos";
		assertEquals(Set.of(e.aA, e.aGrupoA, e.aB, e.aSinSede), alumnosInscritos(leer(ruta, tokenGestor)));
		assertEquals(Set.of(e.aA), alumnosInscritos(leer(ruta + "?sedeId=" + e.sA, tokenGestor)));
		assertEquals(Set.of(e.aB, e.aGrupoA), alumnosInscritos(leer(ruta + "?sedeId=" + e.sB, tokenGestor)));
	}

	@Test
	void convocatorias_responsableAcotaDentroDeSuAlcance() throws Exception {
		String ruta = "/api/convocatorias/" + convocatoria + "/alumnos";
		assertEquals(Set.of(e.aA, e.aGrupoA, e.aSinSede), alumnosInscritos(leer(ruta, tokenA)), "alcance D9");
		assertEquals(Set.of(e.aA), alumnosInscritos(leer(ruta + "?sedeId=" + e.sA, tokenA)));
		assertEquals(403, estado(ruta + "?sedeId=" + e.sB, tokenA));
	}

	@Test
	void convocatorias_sedeDeOtraEscuelaOInexistente() throws Exception {
		String ruta = "/api/convocatorias/" + convocatoria + "/alumnos";
		assertEquals(400, estado(ruta + "?sedeId=" + otra.sA, tokenGestor));
		assertEquals(400, estado(ruta + "?sedeId=" + INEXISTENTE, tokenGestor));
	}

	// ------------------------------------------------------------------ PDF de asistencia: por sede de la clase

	@Test
	void asistencia_gestionListaALosDeLaClaseDeEsaSede() throws Exception {
		String todas = texto(pdf(null, tokenGestor));
		assertTrue(todas.contains("E6A" + e.marca) && todas.contains("E6B" + e.marca), "sin parámetro, se mezclan");

		String deA = texto(pdf(e.sA, tokenGestor));
		assertTrue(deA.contains("E6A" + e.marca));
		assertTrue(deA.contains("E6GRUPOA" + e.marca), "decisión 142: no se filtra por sede principal");
		assertFalse(deA.contains("E6B" + e.marca), "clase de otra sede");

		String deB = texto(pdf(e.sB, tokenGestor));
		assertTrue(deB.contains("E6B" + e.marca));
		assertFalse(deB.contains("E6A" + e.marca), "clase de otra sede");
	}

	@Test
	void asistencia_responsableAcotaDentroDeSuAlcance() throws Exception {
		String deA = texto(pdf(e.sA, tokenA));
		assertTrue(deA.contains("E6A" + e.marca));
		assertFalse(deA.contains("E6B" + e.marca));
		assertEquals(403, estado(asistencia(e.sB), tokenA));
	}

	@Test
	void asistencia_sedeDeOtraEscuelaOInexistente() throws Exception {
		assertEquals(400, estado(asistencia(otra.sA), tokenGestor));
		assertEquals(400, estado(asistencia(INEXISTENTE), tokenGestor));
		assertEquals(400, estado(asistencia(e.sA), tokenGestorOtra));
	}

	// ------------------------------------------------------------------ utilidades

	private String preinscripcion(long grupo, long turno) {
		String referencia = "IT-96-" + UUID.randomUUID().toString().substring(0, 12);
		jdbc.update("""
				INSERT INTO preinscripcion (referencia, temporada, estado, nombre, apellidos, dni, identidad_hash,
				  fecha_nacimiento, direccion, telefono, email, tiene_discapacidad, consentimiento_fotografico,
				  aceptacion_normas, firmante_nombre, firma, plantilla_id, plantilla_snapshot, pdf_firmado,
				  token_documento_hash, creada_en, actualizada_en, grupo_id, organizacion_id, disciplina_id)
				VALUES (?, '2025-2026', 'PENDIENTE', 'Ana', 'Prueba', '12345678T', ?, '1990-05-05', 'Calle 1', '600000000',
				  'ana@example.test', 0, 0, 1, 'Firmante Prueba', x'00', ?, '{}', x'00', ?, '2025-09-10 12:00:00',
				  '2025-09-10 12:00:00', ?, ?, ?)""", referencia, UUID.randomUUID().toString().replace("-", ""),
				plantilla, "e".repeat(64), grupo, e.org, e.disciplina);
		long id = jdbc.queryForObject("SELECT id FROM preinscripcion WHERE referencia = ?", Long.class, referencia);
		jdbc.update("INSERT INTO preinscripcion_turno (preinscripcion_id, turno_id) VALUES (?, ?)", id, turno);
		return referencia;
	}

	private void inscribir(long alumno) {
		jdbc.update("""
				INSERT INTO alumno_convocatoria (cuantia_examen, pagado, alumno_id, convocatoria_id, grado_actual,
				  grado_siguiente, organizacion_id) VALUES (20, 0, ?, ?, NULL, NULL, ?)""", alumno, convocatoria, e.org);
	}

	private String asistencia(Long sede) {
		return "/api/informes/asistencia?year=2026&month=9&grupo=lunes&deporte=TAEKWONDO"
				+ (sede == null ? "" : "&sedeId=" + sede);
	}

	private byte[] pdf(Long sede, String token) throws Exception {
		MvcResult r = peticion(asistencia(sede), token);
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

	private Set<Long> alumnos(String ruta, String token) throws Exception {
		return ids(leer(ruta, token).get("content"));
	}

	private static Set<Long> ids(JsonNode lista) {
		Set<Long> ids = new HashSet<>();
		lista.forEach(n -> ids.add(n.get("id").asLong()));
		return ids;
	}

	private static Set<Long> alumnosInscritos(JsonNode lista) {
		Set<Long> ids = new HashSet<>();
		lista.forEach(n -> ids.add(n.get("alumnoId").asLong()));
		return ids;
	}

	private static Set<String> referencias(JsonNode pagina) {
		Set<String> referencias = new HashSet<>();
		pagina.get("content").forEach(n -> referencias.add(n.get("referencia").asText()));
		return referencias;
	}

	private JsonNode leer(String ruta, String token) throws Exception {
		MvcResult r = peticion(ruta, token);
		assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		return json.readTree(r.getResponse().getContentAsByteArray());
	}

	private int estado(String ruta, String token) throws Exception {
		return peticion(ruta, token).getResponse().getStatus();
	}

	private MvcResult peticion(String ruta, String token) throws Exception {
		MockHttpServletRequestBuilder peticion = get(ruta).cookie(new Cookie("jwt", token));
		return mvc.perform(peticion).andReturn();
	}
}
