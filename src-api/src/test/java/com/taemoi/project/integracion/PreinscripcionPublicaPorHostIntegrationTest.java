package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.organizacion.ResolutorEscuelaPorHost;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.EmailService;
import com.taemoi.project.services.GrupoService;

/**
 * Fase 4, pieza W6 (preinscripción de la web por plantilla) contra MySQL y por HTTP real: una familia ANÓNIMA entra por
 * el Host de su escuela (B, con varias sedes activas) y el formulario común usa los mismos endpoints que Moiskimdo.
 * <ul>
 * <li>{@code public/grupos|turnos/{deporte}} con el Host de B: solo lo de B, cada elemento con {@code sedeId} y
 * {@code sede} (decisión 393).</li>
 * <li>{@code POST /api/preinscripciones} con el Host de B: se guarda en B; con el aforo de la sede lleno, a la lista de
 * espera con {@code posicionListaEspera}; un turno de otra escuela (A) o turnos de dos sedes de B (D6-7) dan 400 y no se
 * escribe nada.</li>
 * <li>Moiskimdo (la 1, una sola sede activa): las listas públicas sin claves nuevas, como siempre.</li>
 * </ul>
 * Datos inventados.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PreinscripcionPublicaPorHostIntegrationTest extends MySqlIntegrationTest {

	private static final String GRUPOS = "/api/preinscripciones/public/grupos/TAEKWONDO";
	private static final String TURNOS = "/api/preinscripciones/public/turnos/TAEKWONDO";
	private static final String MISMA_SEDE = "Todos los turnos deben ser de la misma sede.";

	/** Claves de siempre de los endpoints públicos (las de los dorados de Moiskimdo). */
	private static final Set<String> CLAVES_GRUPO = Set.of("id", "deporte", "nombre", "rangoEdadMin", "rangoEdadMax",
			"turnos", "completo");
	private static final Set<String> CLAVES_TURNO_DE_GRUPO = Set.of("id", "diaSemana", "horaInicio", "horaFin",
			"completo");
	private static final Set<String> CLAVES_TURNO = Set.of("id", "deporte", "grupo", "grupoId", "rangoEdadMin",
			"rangoEdadMax", "diaSemana", "horaInicio", "horaFin", "completo");

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;
	@Autowired ResolutorEscuelaPorHost porHost;

	@MockitoBean EmailService email;

	/** B: la escuela del Host (sedes activas A, B y S; C inactiva). A: otra escuela, sin dominio. */
	EscuelaConSedesDePrueba b, otra;
	String hostB;
	/** Turnos extra de B: martes en la sede B, martes en la sede A (otro grupo) y miércoles en la sede S (aforo 1). */
	long tMartesSedeB, tMartesSedeA, tSedeLlena;
	int altas;

	@BeforeAll
	void preparar() {
		b = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-w6-b");
		otra = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-w6-a");
		plantilla(b);
		plantilla(otra);
		tMartesSedeB = turno(b, b.gB, "Martes");
		tMartesSedeA = turno(b, b.gA2, "Martes");
		tSedeLlena = turno(b, b.gSin, "Miércoles");
		jdbc.update("UPDATE sede SET aforo_turno = 1 WHERE id = ?", b.sSin);
		hostB = "w6-" + b.marca + ".test";
		jdbc.update("INSERT INTO organizacion_dominio (organizacion_id, host, principal) VALUES (?, ?, TRUE)", b.org,
				hostB);
		porHost.recargar();
	}

	@AfterAll
	void retirarDominio() {
		jdbc.update("DELETE FROM organizacion_dominio WHERE host = ?", hostB);
		porHost.recargar();
	}

	// ------------------------------------------------------------------ listas públicas

	@Test
	void turnosAnonimosConHostB_soloDeBYCadaUnoConSuSede() throws Exception {
		JsonNode lista = leer(get(TURNOS).header("Host", hostB), 200);
		Set<Long> ids = new HashSet<>();
		for (JsonNode t : lista) {
			long id = t.get("id").asLong();
			ids.add(id);
			assertEquals(b.org, organizacionDelTurno(id), "turno " + id + " de otra escuela");
			long sede = sedeDelGrupo(t.get("grupoId").asLong());
			assertEquals(sede, t.get("sedeId").asLong(), t.toString());
			assertEquals(nombreSede(sede), t.get("sede").asText(), t.toString());
			assertEquals(clavesCon(CLAVES_TURNO), claves(t), "solo se añaden sedeId y sede");
		}
		assertTrue(ids.containsAll(List.of(b.tA, b.tB, tMartesSedeB, tMartesSedeA, tSedeLlena)), ids.toString());
		assertFalse(ids.contains(otra.tA) || ids.contains(otra.tB), "turnos de la otra escuela");
	}

	@Test
	void gruposAnonimosConHostB_soloDeBConLaSedeDelGrupoYDeSusTurnos() throws Exception {
		JsonNode lista = leer(get(GRUPOS).header("Host", hostB), 200);
		Set<Long> ids = new HashSet<>();
		for (JsonNode g : lista) {
			long id = g.get("id").asLong();
			ids.add(id);
			long sede = sedeDelGrupo(id);
			assertEquals(b.org, jdbc.queryForObject("SELECT organizacion_id FROM grupo WHERE id = ?", Long.class, id));
			assertEquals(sede, g.get("sedeId").asLong(), g.toString());
			assertEquals(nombreSede(sede), g.get("sede").asText(), g.toString());
			assertEquals(clavesCon(CLAVES_GRUPO), claves(g), "solo se añaden sedeId y sede");
			for (JsonNode t : g.get("turnos")) {
				assertEquals(sede, t.get("sedeId").asLong(), t.toString());
				assertEquals(nombreSede(sede), t.get("sede").asText(), t.toString());
				assertEquals(clavesCon(CLAVES_TURNO_DE_GRUPO), claves(t), "solo se añaden sedeId y sede");
			}
		}
		assertTrue(ids.containsAll(List.of(b.gA, b.gA2, b.gB, b.gSin, b.gC)), "gC: sin turnos y de una sede inactiva, también con su sede " + ids);
		assertFalse(ids.contains(otra.gA) || ids.contains(otra.gB), "grupos de la otra escuela");
	}

	/** Moiskimdo: con su Host y sin Host, las listas de siempre, sin {@code sedeId} ni {@code sede} (una sola sede activa). */
	@Test
	void moiskimdo_listasPublicasConLasClavesDeSiempre() throws Exception {
		for (MockHttpServletRequestBuilder peticion : List.of(get(TURNOS), get(TURNOS).header("Host", "moiskimdo.es"))) {
			JsonNode lista = leer(peticion, 200);
			for (JsonNode t : lista) {
				assertEquals(CLAVES_TURNO, claves(t), t.toString());
				assertEquals(1L, organizacionDelTurno(t.get("id").asLong()));
			}
		}
		for (MockHttpServletRequestBuilder peticion : List.of(get(GRUPOS), get(GRUPOS).header("Host", "moiskimdo.es"))) {
			for (JsonNode g : leer(peticion, 200)) {
				assertEquals(CLAVES_GRUPO, claves(g), g.toString());
				for (JsonNode t : g.get("turnos")) {
					assertEquals(CLAVES_TURNO_DE_GRUPO, claves(t), t.toString());
				}
			}
		}
	}

	// ------------------------------------------------------------------ alta anónima

	@Test
	void altaAnonimaConHostB_turnosDeUnaSede_seGuardaEnBConPlaza() throws Exception {
		JsonNode r = alta(hostB, 201, b.tA, tMartesSedeA);
		String referencia = r.get("referencia").asText();
		assertEquals(b.org, jdbc.queryForObject("SELECT organizacion_id FROM preinscripcion WHERE referencia = ?",
				Long.class, referencia));
		assertEquals(Set.of(b.tA, tMartesSedeA), new HashSet<>(jdbc.queryForList("""
				SELECT pt.turno_id FROM preinscripcion_turno pt JOIN preinscripcion p ON p.id = pt.preinscripcion_id
				WHERE p.referencia = ?""", Long.class, referencia)));
		assertTrue(r.hasNonNull("tokenDocumento"));
		assertTrue(r.has("posicionListaEspera"));
	}

	@Test
	void altaAnonimaConHostB_sedeLlena_listaDeEsperaConSuPosicion() throws Exception {
		JsonNode primera = alta(hostB, 201, tSedeLlena);
		assertEquals("PENDIENTE", primera.get("estado").asText());
		assertTrue(primera.get("plazaAsignada").asBoolean());
		assertTrue(primera.get("posicionListaEspera").isNull());

		JsonNode segunda = alta(hostB, 201, tSedeLlena);
		assertEquals("EN_LISTA_ESPERA", segunda.get("estado").asText());
		assertFalse(segunda.get("plazaAsignada").asBoolean());
		assertEquals(1, segunda.get("posicionListaEspera").asInt());
		assertEquals("Preinscripción añadida a la lista de espera.", segunda.get("mensaje").asText());

		JsonNode tercera = alta(hostB, 201, tSedeLlena);
		assertEquals("EN_LISTA_ESPERA", tercera.get("estado").asText());
		assertEquals(2, tercera.get("posicionListaEspera").asInt());
	}

	@Test
	void altaAnonimaConHostB_turnoDeOtraEscuela_400SinEscribirNada() throws Exception {
		long antes = preinscripciones(), antesTurnos = preinscripcionTurnos();
		JsonNode solo = alta(hostB, 400, otra.tA);
		assertEquals("Alguno de los turnos seleccionados no existe.", solo.get("mensaje").asText());
		// Mezclado con uno propio (otro día): tampoco.
		alta(hostB, 400, tMartesSedeB, otra.tA);
		// Y al revés: el turno de B por el Host de Moiskimdo.
		alta("moiskimdo.es", 400, b.tA);
		assertEquals(antes, preinscripciones());
		assertEquals(antesTurnos, preinscripcionTurnos());
	}

	@Test
	void altaAnonimaConHostB_turnosDeDosSedes_400SinEscribirNada() throws Exception {
		long antes = preinscripciones(), antesTurnos = preinscripcionTurnos();
		JsonNode r = alta(hostB, 400, b.tA, tMartesSedeB);
		assertEquals(MISMA_SEDE, r.get("mensaje").asText());
		assertEquals(antes, preinscripciones());
		assertEquals(antesTurnos, preinscripcionTurnos());
	}

	// ------------------------------------------------------------------ utilidades

	private void plantilla(EscuelaConSedesDePrueba e) {
		jdbc.update("""
				INSERT INTO plantilla_preinscripcion (version, activa, contenido, instrucciones, creada_en, organizacion_id,
				  disciplina_id)
				SELECT p.version, p.activa, p.contenido, p.instrucciones, p.creada_en, ?, ?
				FROM plantilla_preinscripcion p JOIN disciplina d ON d.id = p.disciplina_id
				WHERE p.organizacion_id = 1 AND d.codigo = 'TAEKWONDO' AND p.activa = TRUE
				ORDER BY p.version DESC LIMIT 1""", e.org, e.disciplina);
	}

	private long turno(EscuelaConSedesDePrueba e, long grupo, String dia) {
		jdbc.update("""
				INSERT INTO turno (dia_semana, hora_inicio, hora_fin, tipo, grupo_id, organizacion_id)
				VALUES (?, '18:00', '19:00', NULL, ?, ?)""", dia, grupo, e.org);
		return jdbc.queryForObject("SELECT MAX(id) FROM turno WHERE organizacion_id = ?", Long.class, e.org);
	}

	private long organizacionDelTurno(long turno) {
		return jdbc.queryForObject("SELECT organizacion_id FROM turno WHERE id = ?", Long.class, turno);
	}

	private long sedeDelGrupo(long grupo) {
		return jdbc.queryForObject("SELECT sede_id FROM grupo WHERE id = ?", Long.class, grupo);
	}

	private String nombreSede(long sede) {
		return jdbc.queryForObject("SELECT nombre FROM sede WHERE id = ?", String.class, sede);
	}

	private long preinscripciones() {
		return jdbc.queryForObject("SELECT COUNT(*) FROM preinscripcion", Long.class);
	}

	private long preinscripcionTurnos() {
		return jdbc.queryForObject("SELECT COUNT(*) FROM preinscripcion_turno", Long.class);
	}

	private static Set<String> claves(JsonNode nodo) {
		Set<String> claves = new HashSet<>();
		for (Iterator<String> it = nodo.fieldNames(); it.hasNext();) {
			claves.add(it.next());
		}
		return claves;
	}

	private static Set<String> clavesCon(Set<String> deSiempre) {
		Set<String> claves = new HashSet<>(deSiempre);
		claves.add("sedeId");
		claves.add("sede");
		return claves;
	}

	/** Alta anónima (sin cookie) por el Host dado; cada una desde una IP distinta (límite de 5 por IP). */
	private JsonNode alta(String host, int esperado, long... turnos) throws Exception {
		int n = 43_000_000 + (++altas) * 1_000 + (int) (System.nanoTime() % 1_000);
		String dni = n + "" + "TRWAGMYFPDXBNJZSQVHLCKE".charAt(n % 23);
		List<Long> ids = new ArrayList<>();
		for (long t : turnos) {
			ids.add(t);
		}
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("deporte", "TAEKWONDO");
		body.put("turnoIds", ids);
		body.put("nombre", "Alta");
		body.put("apellidos", "Plantilla W6");
		body.put("dni", dni);
		body.put("fechaNacimiento", "1990-01-01");
		body.put("direccion", "Calle Sintética 6");
		body.put("telefono", "612345678");
		body.put("telefono2", "");
		body.put("email", "alta-w6@example.test");
		body.put("observaciones", "");
		body.put("tutorNombre", "");
		body.put("tutorDni", "");
		body.put("tieneDiscapacidad", false);
		body.put("consentimientoFotografico", false);
		body.put("aceptacionNormas", true);
		body.put("firmanteNombre", "Firmante W6");
		body.put("firmaBase64", firma());
		return leer(post("/api/preinscripciones").contentType(MediaType.APPLICATION_JSON).header("Host", host)
				.header("X-Forwarded-For", "10.96." + (altas / 250) + "." + (altas % 250 + 1))
				.content(json.writeValueAsString(body)), esperado);
	}

	private static String firma() throws Exception {
		BufferedImage imagen = new BufferedImage(320, 100, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = imagen.createGraphics();
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, imagen.getWidth(), imagen.getHeight());
		g.setColor(Color.BLACK);
		g.drawString("Firma de prueba W6", 20, 50);
		g.dispose();
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		ImageIO.write(imagen, "png", bytes);
		return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes.toByteArray());
	}

	private JsonNode leer(MockHttpServletRequestBuilder peticion, int esperado) throws Exception {
		MvcResult r = mvc.perform(peticion).andReturn();
		String contenido = r.getResponse().getContentAsString(StandardCharsets.UTF_8);
		assertEquals(esperado, r.getResponse().getStatus(), contenido);
		return json.readTree(contenido);
	}
}
