package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;
import org.mockito.invocation.Invocation;
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
 * Decisión 391 (fase 4, pieza 1): el formulario de contacto y el aviso interno de una preinscripción van al buzón de la
 * escuela de la petición ({@code organizacion.email_contacto}), nunca al de otra escuela. Contra MySQL real, con una
 * escuela sintética ({@link EscuelaConSedesDePrueba}) y Moiskimdo (organización 1, buzón de la V31_12) solo leída.
 * <ul>
 * <li>alta de otra escuela → su aviso a SU buzón, y nada al de Moiskimdo;</li>
 * <li>escuela sin buzón (nulo o en blanco) → sin aviso interno; el correo de la familia sale igual;</li>
 * <li>promoción de la lista de espera en otra escuela → su aviso interno a SU buzón, nada al de Moiskimdo;</li>
 * <li>contacto → al buzón de la escuela resuelta (visitante: la por defecto; con sesión: la de la sesión);</li>
 * <li>contacto sin buzón → no se envía nada y responde 409.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BuzonEscuelaIntegrationTest extends MySqlIntegrationTest {

	private static final String BUZON_OTRA = "buzon-escuela-f41@example.test";
	private static final String FAMILIA = "familia-buzon-f41@example.test";
	private static final String AVISO = "Nueva preinscripción recibida: ";
	private static final String AVISO_PROMOCION = "Lista de espera promocionada: ";

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;

	@MockitoBean EmailService email;

	EscuelaConSedesDePrueba e;
	String tokenOtra;
	String buzonMoiskimdo;
	int altas;

	@BeforeAll
	void preparar() {
		e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-f41-buzon");
		tokenOtra = e.token(jwtService, usuarios, e.gestor);
		jdbc.update("""
				INSERT INTO plantilla_preinscripcion (version, activa, contenido, instrucciones, creada_en, organizacion_id,
				  disciplina_id)
				SELECT p.version, p.activa, p.contenido, p.instrucciones, p.creada_en, ?, ?
				FROM plantilla_preinscripcion p JOIN disciplina d ON d.id = p.disciplina_id
				WHERE p.organizacion_id = 1 AND d.codigo = 'TAEKWONDO' AND p.activa = TRUE
				ORDER BY p.version DESC LIMIT 1""", e.org, e.disciplina);
		buzonMoiskimdo = jdbc.queryForObject("SELECT email_contacto FROM organizacion WHERE id = 1", String.class);
	}

	@BeforeEach
	void limpiarCorreos() {
		Mockito.reset(email);
	}

	@AfterEach
	void restaurarBuzon() {
		jdbc.update("UPDATE organizacion SET email_contacto = NULL WHERE id = ?", e.org);
	}

	// ------------------------------------------------------------------ aviso interno de la preinscripción

	@Test
	void altaDeOtraEscuela_avisoASuBuzonYNuncaAlDeMoiskimdo() throws Exception {
		buzon(BUZON_OTRA);

		String ref = alta(tokenOtra);

		List<String> destinos = destinatarios();
		assertTrue(destinos.contains(FAMILIA), destinos.toString());
		assertEquals(List.of(BUZON_OTRA), destinatariosDe(AVISO + ref), "aviso interno a su buzón");
		assertFalse(destinos.contains(buzonMoiskimdo), destinos.toString());
		assertFalse(destinos.contains("moiskimdotaekwondo@gmail.com"), destinos.toString());
	}

	@Test
	void escuelaSinBuzon_sinAvisoInternoYLaFamiliaRecibeSuCorreo() throws Exception {
		buzon(null);
		String ref = alta(tokenOtra);
		assertEquals(List.of(FAMILIA), destinatarios(), "solo el correo de la familia");
		assertEquals(List.of(), destinatariosDe(AVISO + ref));

		Mockito.reset(email);
		buzon("   ");
		alta(tokenOtra);
		assertEquals(List.of(FAMILIA), destinatarios(), "en blanco cuenta como sin buzón");
	}

	@Test
	void promocionEnOtraEscuela_avisoASuBuzonYNuncaAlDeMoiskimdo() throws Exception {
		buzon(BUZON_OTRA);
		// Sede propia con aforo 1 y un turno vacío: la segunda alta queda en lista de espera.
		String nombreSede = "IT F41 promocion " + e.marca;
		jdbc.update("INSERT INTO sede (organizacion_id, nombre, activa, aforo_turno) VALUES (?, ?, TRUE, 1)", e.org,
				nombreSede);
		long sede = jdbc.queryForObject("SELECT id FROM sede WHERE organizacion_id = ? AND nombre = ?", Long.class,
				e.org, nombreSede);
		jdbc.update("UPDATE grupo SET sede_id = ? WHERE id = ?", sede, e.gA2);
		jdbc.update("""
				INSERT INTO turno (dia_semana, hora_inicio, hora_fin, tipo, grupo_id, organizacion_id)
				VALUES ('Jueves', '18:00', '19:00', NULL, ?, ?)""", e.gA2, e.org);
		long turno = jdbc.queryForObject("SELECT MAX(id) FROM turno WHERE organizacion_id = ?", Long.class, e.org);
		assertEquals("PENDIENTE", alta(tokenOtra, turno).get("estado").asText());
		JsonNode enEspera = alta(tokenOtra, turno);
		assertEquals("EN_LISTA_ESPERA", enEspera.get("estado").asText());
		String ref = enEspera.get("referencia").asText();
		Mockito.reset(email);

		// Subir el aforo de la sede promociona la lista de espera (gestor de la escuela, en su sesión).
		Map<String, Object> datos = new LinkedHashMap<>();
		datos.put("nombre", nombreSede);
		datos.put("aforoTurno", 2);
		MvcResult r = mvc.perform(put("/api/sedes/" + sede).cookie(new Cookie("jwt", tokenOtra))
				.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(datos))).andReturn();
		assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString(StandardCharsets.UTF_8));

		assertEquals("PENDIENTE", jdbc.queryForObject("SELECT estado FROM preinscripcion WHERE referencia = ?",
				String.class, ref));
		List<String> destinos = destinatarios();
		assertTrue(destinos.contains(FAMILIA), "aviso de plaza a la familia: " + destinos);
		assertEquals(List.of(BUZON_OTRA), destinatariosDe(AVISO_PROMOCION + ref), "aviso interno a su buzón");
		assertFalse(destinos.contains(buzonMoiskimdo), destinos.toString());
		assertFalse(destinos.contains("moiskimdotaekwondo@gmail.com"), destinos.toString());
	}

	// ------------------------------------------------------------------ formulario de contacto

	@Test
	void contacto_alBuzonDeLaEscuelaResuelta() throws Exception {
		buzon(BUZON_OTRA);

		assertEquals(200, contacto(tokenOtra).getResponse().getStatus());
		assertEquals(List.of(BUZON_OTRA), destinatarios(), "con sesión: la escuela de la sesión");

		Mockito.reset(email);
		assertNotNull(buzonMoiskimdo, "Moiskimdo tiene buzón desde la V31_12");
		assertNotEquals(BUZON_OTRA, buzonMoiskimdo);
		assertEquals(200, contacto(null).getResponse().getStatus());
		assertEquals(List.of(buzonMoiskimdo), destinatarios(), "visitante: la escuela por defecto");
	}

	@Test
	void contacto_escuelaSinBuzonNoEnviaYDevuelve409() throws Exception {
		buzon(null);
		MvcResult r = contacto(tokenOtra);
		assertEquals(409, r.getResponse().getStatus(), r.getResponse().getContentAsString(StandardCharsets.UTF_8));
		assertEquals(List.of(), destinatarios());

		buzon(" ");
		assertEquals(409, contacto(tokenOtra).getResponse().getStatus());
		assertEquals(List.of(), destinatarios(), "ni al de Moiskimdo ni a ningún otro");
	}

	// ------------------------------------------------------------------ utilidades

	private void buzon(String valor) {
		jdbc.update("UPDATE organizacion SET email_contacto = ? WHERE id = ?", valor, e.org);
	}

	/** Destinatario (primer argumento) de cada envío registrado en el {@link EmailService} simulado, en orden. */
	private List<String> destinatarios() {
		List<String> destinos = new ArrayList<>();
		for (Invocation i : Mockito.mockingDetails(email).getInvocations()) {
			if (i.getMethod().getName().startsWith("send")) {
				destinos.add(i.getArgument(0));
			}
		}
		return destinos;
	}

	private List<String> destinatariosDe(String asunto) {
		List<String> destinos = new ArrayList<>();
		for (Invocation i : Mockito.mockingDetails(email).getInvocations()) {
			if (i.getMethod().getName().startsWith("send") && asunto.equals(i.getArgument(1))) {
				destinos.add(i.getArgument(0));
			}
		}
		return destinos;
	}

	/** Alta real en el turno A de la escuela sintética; devuelve la referencia. */
	private String alta(String token) throws Exception {
		return alta(token, e.tA).get("referencia").asText();
	}

	/** Alta real en ese turno; devuelve la respuesta (referencia, estado...). */
	private JsonNode alta(String token, long turno) throws Exception {
		int n = 41_000_000 + (++altas) + (int) (System.nanoTime() % 1_000_000);
		String dni = n + "" + "TRWAGMYFPDXBNJZSQVHLCKE".charAt(n % 23);
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("deporte", "TAEKWONDO");
		body.put("turnoIds", List.of(turno));
		body.put("nombre", "Alta");
		body.put("apellidos", "Buzon Escuela");
		body.put("dni", dni);
		body.put("fechaNacimiento", "1990-01-01");
		body.put("direccion", "Calle Sintética 41");
		body.put("telefono", "612345678");
		body.put("telefono2", "");
		body.put("email", FAMILIA);
		body.put("observaciones", "");
		body.put("tutorNombre", "");
		body.put("tutorDni", "");
		body.put("tieneDiscapacidad", false);
		body.put("consentimientoFotografico", false);
		body.put("aceptacionNormas", true);
		body.put("firmanteNombre", "Firmante Buzon");
		body.put("firmaBase64", firma());
		MockHttpServletRequestBuilder peticion = post("/api/preinscripciones").contentType(MediaType.APPLICATION_JSON)
				.header("X-Forwarded-For", "10.96.1." + (altas % 250 + 1)).content(json.writeValueAsString(body));
		MvcResult r = mvc.perform(conSesion(peticion, token)).andReturn();
		String contenido = r.getResponse().getContentAsString(StandardCharsets.UTF_8);
		assertEquals(201, r.getResponse().getStatus(), contenido);
		return json.readTree(contenido);
	}

	private MvcResult contacto(String token) throws Exception {
		Map<String, String> cuerpo = new LinkedHashMap<>();
		cuerpo.put("nombre", "Ana");
		cuerpo.put("apellidos", "Ruiz");
		cuerpo.put("email", "visitante-f41@example.test");
		cuerpo.put("asunto", "Información");
		cuerpo.put("mensaje", "Hola");
		return mvc.perform(conSesion(post("/api/mail/enviar").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(cuerpo)), token)).andReturn();
	}

	private static MockHttpServletRequestBuilder conSesion(MockHttpServletRequestBuilder peticion, String token) {
		return token == null ? peticion : peticion.cookie(new Cookie("jwt", token));
	}

	private static String firma() throws Exception {
		BufferedImage imagen = new BufferedImage(320, 100, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = imagen.createGraphics();
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, imagen.getWidth(), imagen.getHeight());
		g.setColor(Color.BLACK);
		g.drawString("Firma de prueba del buzon", 20, 50);
		g.dispose();
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		ImageIO.write(imagen, "png", bytes);
		return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes.toByteArray());
	}
}
