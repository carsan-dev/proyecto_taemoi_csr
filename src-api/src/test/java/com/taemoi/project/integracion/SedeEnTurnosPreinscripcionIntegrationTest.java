package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
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
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.EmailService;
import com.taemoi.project.services.GrupoService;
import com.taemoi.project.services.JwtService;

import jakarta.servlet.http.Cookie;

/**
 * Decisión 393 contra MySQL: en una escuela con varias sedes activas, cada sitio que describe un turno de preinscripción
 * (turnos y grupos públicos, detalle del panel y los textos guardados: PDF, correo de alta y de cambio de turnos) lleva
 * su sede; con una sola sede activa no cambia nada. Los turnos tA (sede A) y tB (sede B) tienen el mismo día y hora.
 * Datos inventados.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SedeEnTurnosPreinscripcionIntegrationTest extends MySqlIntegrationTest {

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;

	@MockitoBean EmailService email;

	EscuelaConSedesDePrueba e;
	String token;
	String nombreA, nombreB;
	int altas;

	@BeforeEach
	void preparar() {
		e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-393");
		token = e.token(jwtService, usuarios, e.gestor);
		nombreA = jdbc.queryForObject("SELECT nombre FROM sede WHERE id = ?", String.class, e.sA);
		nombreB = jdbc.queryForObject("SELECT nombre FROM sede WHERE id = ?", String.class, e.sB);
		jdbc.update("""
				INSERT INTO plantilla_preinscripcion (version, activa, contenido, instrucciones, creada_en, organizacion_id,
				  disciplina_id)
				SELECT p.version, p.activa, p.contenido, p.instrucciones, p.creada_en, ?, ?
				FROM plantilla_preinscripcion p JOIN disciplina d ON d.id = p.disciplina_id
				WHERE p.organizacion_id = 1 AND d.codigo = 'TAEKWONDO' AND p.activa = TRUE
				ORDER BY p.version DESC LIMIT 1""", e.org, e.disciplina);
	}

	@Test
	void turnosPublicos_distinguenLaSedeDeDosTurnosIguales() throws Exception {
		JsonNode lista = leer(get("/api/preinscripciones/public/turnos/TAEKWONDO"), token, 200);
		JsonNode a = turno(lista, e.tA), b = turno(lista, e.tB);
		assertEquals(a.get("diaSemana").asText(), b.get("diaSemana").asText());
		assertEquals(a.get("horaInicio").asText(), b.get("horaInicio").asText());
		assertEquals(e.sA, a.get("sedeId").asLong());
		assertEquals(nombreA, a.get("sede").asText());
		assertEquals(e.sB, b.get("sedeId").asLong());
		assertEquals(nombreB, b.get("sede").asText());
	}

	@Test
	void gruposPublicos_llevanLaSedeDelGrupoYDeCadaTurno() throws Exception {
		JsonNode lista = leer(get("/api/preinscripciones/public/grupos/TAEKWONDO"), token, 200);
		JsonNode gA = null, gB = null;
		for (JsonNode g : lista) {
			if (g.get("id").asLong() == e.gA) gA = g;
			if (g.get("id").asLong() == e.gB) gB = g;
		}
		assertEquals(nombreA, gA.get("sede").asText());
		assertEquals(e.sA, gA.get("sedeId").asLong());
		assertEquals(nombreB, gB.get("sede").asText());
		assertEquals(nombreA, gA.get("turnos").get(0).get("sede").asText());
		assertEquals(nombreB, gB.get("turnos").get(0).get("sede").asText());
	}

	@Test
	void textosGuardados_yDetalleDelPanelLlevanLaSede() throws Exception {
		String refA = alta(e.tA), refB = alta(e.tB);
		String snapshotA = snapshot(refA), snapshotB = snapshot(refB);
		assertTrue(snapshotA.endsWith("Lunes 18:00-19:00 (IT E6 gA " + e.marca + ") · " + nombreA), snapshotA);
		assertTrue(snapshotB.endsWith("Lunes 18:00-19:00 (IT E6 gB " + e.marca + ") · " + nombreB), snapshotB);

		JsonNode detalleA = leer(get("/api/preinscripciones/" + refA), token, 200);
		JsonNode detalleB = leer(get("/api/preinscripciones/" + refB), token, 200);
		assertTrue(detalleA.get("grupo").asText().endsWith(" · " + nombreA));
		assertTrue(detalleB.get("grupo").asText().endsWith(" · " + nombreB));
		assertEquals(e.sA, detalleA.get("turnosSolicitados").get(0).get("sedeId").asLong());
		assertEquals(nombreA, detalleA.get("turnosSolicitados").get(0).get("sede").asText());
		assertEquals(e.sB, detalleB.get("turnosSolicitados").get(0).get("sedeId").asLong());
		assertEquals(nombreB, detalleB.get("turnosSolicitados").get(0).get("sede").asText());
		assertNotEquals(snapshotA, snapshotB);

		JsonNode listado = leer(get("/api/preinscripciones?q=Turno&size=25"), token, 200);
		assertFalse(listado.get("content").isEmpty());
		for (JsonNode fila : listado.get("content")) {
			assertTrue(fila.get("grupo").asText().contains(" · " + fila.get("turnosSolicitados").get(0).get("sede").asText()));
		}
	}

	private String snapshot(String ref) {
		return jdbc.queryForObject("SELECT grupo_snapshot FROM preinscripcion WHERE referencia = ?", String.class, ref);
	}

	@Test
	void cambioDeTurnos_guardaLaSedeEnLosDosSnapshots() throws Exception {
		String ref = alta(e.tA);
		leer(put("/api/preinscripciones/" + ref + "/turnos").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("turnoIds", List.of(e.tB)))), token, 200);
		Map<String, Object> fila = jdbc.queryForMap("""
				SELECT email_cambio_turnos_anterior_snapshot AS antes, email_cambio_turnos_nuevo_snapshot AS despues
				FROM preinscripcion WHERE referencia = ?""", ref);
		assertTrue(((String) fila.get("antes")).endsWith(" · " + nombreA), (String) fila.get("antes"));
		assertTrue(((String) fila.get("despues")).endsWith(" · " + nombreB), (String) fila.get("despues"));
	}

	@Test
	void conUnaSolaSedeActiva_nadaCambia() throws Exception {
		jdbc.update("UPDATE sede SET activa = FALSE WHERE id IN (?, ?)", e.sB, e.sSin);
		JsonNode lista = leer(get("/api/preinscripciones/public/turnos/TAEKWONDO"), token, 200);
		assertFalse(turno(lista, e.tA).has("sede"));
		assertFalse(turno(lista, e.tA).has("sedeId"));
		for (JsonNode g : leer(get("/api/preinscripciones/public/grupos/TAEKWONDO"), token, 200)) {
			assertFalse(g.has("sede"));
			assertFalse(g.has("sedeId"));
			for (JsonNode t : g.get("turnos")) assertFalse(t.has("sede"));
		}
		String ref = alta(e.tA);
		String snapshot = jdbc.queryForObject("SELECT grupo_snapshot FROM preinscripcion WHERE referencia = ?",
				String.class, ref);
		assertEquals("Taekwondo IT · Lunes 18:00-19:00 (IT E6 gA " + e.marca + ")", snapshot);
		JsonNode detalle = leer(get("/api/preinscripciones/" + ref), token, 200);
		assertFalse(detalle.get("turnosSolicitados").get(0).has("sede"));
	}

	@Test
	void turnoDeSedeInactiva_conVariasSedesActivas_muestraSuSede() throws Exception {
		long t = turnoDe(e.gC);
		String nombreC = jdbc.queryForObject("SELECT nombre FROM sede WHERE id = ?", String.class, e.sC);
		JsonNode turnos = leer(get("/api/preinscripciones/public/turnos/TAEKWONDO"), token, 200);
		assertEquals(nombreC, turno(turnos, t).get("sede").asText());
		assertEquals(e.sC, turno(turnos, t).get("sedeId").asLong());
		assertTrue(snapshot(alta(t)).endsWith(" · " + nombreC));
	}

	private long turnoDe(long grupo) {
		jdbc.update("""
				INSERT INTO turno (dia_semana, hora_inicio, hora_fin, tipo, grupo_id, organizacion_id)
				VALUES ('Martes', '18:00', '19:00', NULL, ?, ?)""", grupo, e.org);
		return jdbc.queryForObject("SELECT MAX(id) FROM turno WHERE organizacion_id = ?", Long.class, e.org);
	}

	// ------------------------------------------------------------------ utilidades

	private static JsonNode turno(JsonNode lista, long id) {
		for (JsonNode t : lista) {
			if (t.get("id").asLong() == id) {
				return t;
			}
		}
		throw new AssertionError("Falta el turno " + id + " en " + lista);
	}

	private String alta(long... turnos) throws Exception {
		int n = 41_000_000 + (++altas) + (int) (System.nanoTime() % 1_000_000);
		String dni = n + "" + "TRWAGMYFPDXBNJZSQVHLCKE".charAt(n % 23);
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("deporte", "TAEKWONDO");
		body.put("turnoIds", java.util.Arrays.stream(turnos).boxed().toList());
		body.put("nombre", "Alta");
		body.put("apellidos", "Sede Turno");
		body.put("dni", dni);
		body.put("fechaNacimiento", "1990-01-01");
		body.put("direccion", "Calle Sintética 3");
		body.put("telefono", "612345678");
		body.put("telefono2", "");
		body.put("email", "alta-393@example.test");
		body.put("observaciones", "");
		body.put("tutorNombre", "");
		body.put("tutorDni", "");
		body.put("tieneDiscapacidad", false);
		body.put("consentimientoFotografico", false);
		body.put("aceptacionNormas", true);
		body.put("firmanteNombre", "Firmante Sede");
		body.put("firmaBase64", firma());
		MockHttpServletRequestBuilder peticion = post("/api/preinscripciones").contentType(MediaType.APPLICATION_JSON)
				.header("X-Forwarded-For", "10.93.0." + (altas % 250 + 1)).content(json.writeValueAsString(body));
		return leer(peticion, token, 201).get("referencia").asText();
	}

	private static String firma() throws Exception {
		BufferedImage imagen = new BufferedImage(320, 100, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = imagen.createGraphics();
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, imagen.getWidth(), imagen.getHeight());
		g.setColor(Color.BLACK);
		g.drawString("Firma de prueba de sede", 20, 50);
		g.dispose();
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		ImageIO.write(imagen, "png", bytes);
		return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes.toByteArray());
	}

	private JsonNode leer(MockHttpServletRequestBuilder peticion, String jwt, int esperado) throws Exception {
		peticion.cookie(new Cookie("jwt", jwt));
		MvcResult r = mvc.perform(peticion).andReturn();
		String contenido = r.getResponse().getContentAsString(StandardCharsets.UTF_8);
		assertEquals(esperado, r.getResponse().getStatus(), contenido);
		return json.readTree(contenido);
	}
}
