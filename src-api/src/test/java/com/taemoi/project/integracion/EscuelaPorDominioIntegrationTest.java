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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.entities.AuthProvider;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.ResolutorEscuelaPorHost;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.EmailService;
import com.taemoi.project.services.JwtService;

import jakarta.servlet.http.Cookie;

/**
 * Fase 4, escuela por dominio, pieza 1 ({@code docs/multi-escuela/fase4-dominio-plan.md} §3.1): una petición anónima
 * cae en la escuela de su Host ({@code organizacion_dominio}); un Host desconocido o el de Moiskimdo, en la por defecto
 * (la 1), exactamente como antes; con sesión manda la de la sesión. Por HTTP real contra MySQL con la cadena de filtros
 * completa y dos escuelas sintéticas: X (con dominio) e Y (sin dominio, la de la sesión). Datos inventados.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(OutputCaptureExtension.class)
class EscuelaPorDominioIntegrationTest extends MySqlIntegrationTest {

	private static final String DEPORTE = "TAEKWONDO";
	private static final String TURNOS = "/api/preinscripciones/public/turnos/" + DEPORTE;
	private static final String REGISTRO = "/api/auth/register/request";

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired ResolutorEscuelaPorHost porHost;

	@MockitoBean EmailService email;

	final String marca = UUID.randomUUID().toString().substring(0, 8);
	final String hostX = "x-" + marca + ".test";
	long orgX, turnoX, alumnoX, orgY, turnoY;
	String alumnoXEmail;
	String tokenY;
	int altas;

	@BeforeAll
	void preparar() {
		orgX = escuela("it-dominio-x-" + marca);
		turnoX = turno(orgX);
		alumnoXEmail = "it-dominio-alumno-" + marca + "@example.test";
		alumnoX = alumno(orgX, alumnoXEmail);
		jdbc.update("INSERT INTO organizacion_dominio (organizacion_id, host, principal) VALUES (?, ?, TRUE)", orgX,
				hostX);
		porHost.recargar();

		orgY = escuela("it-dominio-y-" + marca);
		turnoY = turno(orgY);
		long gestorY = TenantContext.calcularCon(1L, () -> {
			Usuario usuario = new Usuario();
			usuario.setNombre("Gestor");
			usuario.setApellidos("Dominio");
			usuario.setEmail("it-dominio-gestor-" + marca + "@example.test");
			usuario.setContrasena("fake-Password-123");
			usuario.setAuthProvider(AuthProvider.LOCAL);
			return usuarios.save(usuario).getId();
		});
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, 'ADMINISTRADOR')", gestorY,
				orgY);
		Usuario gestor = TenantContext.calcularCon(1L, () -> usuarios.findById(gestorY).orElseThrow());
		tokenY = jwtService.generateToken(gestor, 600_000, orgY);
	}

	@AfterAll
	void retirarDominio() {
		jdbc.update("DELETE FROM organizacion_dominio WHERE host = ?", hostX);
		porHost.recargar();
	}

	@Test
	void laSemillaEsMoiskimdoEnLaUno() {
		assertEquals(List.of(Map.of("organizacion_id", 1L, "host", "moiskimdo.es", "principal", true)),
				jdbc.queryForList("SELECT organizacion_id, host, principal FROM organizacion_dominio WHERE organizacion_id = 1"));
	}

	// ------------------------------------------------------------------ preinscripción pública

	@Test
	void preinscripcionAnonimaConHostX_seGuardaEnX() throws Exception {
		MvcResult r = mvc.perform(alta(turnoX).header("Host", hostX)).andReturn();
		String cuerpo = r.getResponse().getContentAsString(StandardCharsets.UTF_8);
		assertEquals(201, r.getResponse().getStatus(), cuerpo);

		String referencia = json.readTree(cuerpo).get("referencia").asText();
		assertEquals(orgX, jdbc.queryForObject("SELECT organizacion_id FROM preinscripcion WHERE referencia = ?",
				Long.class, referencia));
	}

	@Test
	void preinscripcionAnonimaConHostEnMayusculasYPuerto_tambienEnX() throws Exception {
		MvcResult r = mvc.perform(alta(turnoX).header("Host", hostX.toUpperCase() + ".:443")).andReturn();
		assertEquals(201, r.getResponse().getStatus(), r.getResponse().getContentAsString(StandardCharsets.UTF_8));
	}

	@Test
	void preinscripcionAnonimaEnMoiskimdoOHostDesconocido_noVeLosTurnosDeX() throws Exception {
		for (String host : List.of("moiskimdo.es", "desconocido-" + marca + ".test")) {
			int antes = preinscripcionesDe(orgX);
			MvcResult r = mvc.perform(alta(turnoX).header("Host", host)).andReturn();
			assertEquals(400, r.getResponse().getStatus(), host);
			assertEquals(antes, preinscripcionesDe(orgX), host);
		}
	}

	@Test
	void turnosPublicos_conHostXSoloLosDeX() throws Exception {
		assertEquals(List.of(turnoX), ids(turnos(get(TURNOS).header("Host", hostX))));
	}

	/** Moiskimdo idéntico: con su Host, con uno desconocido o con una IP, la misma respuesta que sin Host (la 1). */
	@Test
	void turnosPublicos_enMoiskimdoDesconocidoOIp_igualQueAntes() throws Exception {
		String sinHost = turnos(get(TURNOS));
		assertFalse(ids(sinHost).contains(turnoX));
		assertFalse(ids(sinHost).contains(turnoY));
		for (String host : List.of("moiskimdo.es", "MOISKIMDO.ES:443", "desconocido-" + marca + ".test", "10.0.0.7")) {
			assertEquals(sinHost, turnos(get(TURNOS).header("Host", host)), host);
		}
	}

	// ------------------------------------------------------------------ registro de familia

	@Test
	void registroAnonimoConHostX_buscaElAlumnoEnXYGuardaLaSolicitudEnX() throws Exception {
		MvcResult r = mvc.perform(registro(alumnoXEmail).header("Host", hostX)).andReturn();
		assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString(StandardCharsets.UTF_8));
		assertEquals(orgX, jdbc.queryForObject("SELECT organizacion_id FROM registro_pendiente WHERE email = ?",
				Long.class, alumnoXEmail));
	}

	@Test
	void registroAnonimoEnMoiskimdoOHostDesconocido_noEncuentraAlAlumnoDeX() throws Exception {
		String otroEmail = "it-dominio-otro-" + marca + "@example.test";
		long otro = alumno(orgX, otroEmail);
		for (String host : List.of("moiskimdo.es", "desconocido-" + marca + ".test")) {
			MvcResult r = mvc.perform(registro(otroEmail).header("Host", host)).andReturn();
			String cuerpo = r.getResponse().getContentAsString(StandardCharsets.UTF_8);
			assertEquals(400, r.getResponse().getStatus(), host);
			assertTrue(cuerpo.contains("No se encontraron alumnos"), cuerpo);
		}
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM registro_pendiente WHERE alumno_id = ?",
				Integer.class, otro));
	}

	// ------------------------------------------------------------------ con sesión

	@Test
	void conSesion_mandaLaSesionSobreElHostYQuedaUnAviso(CapturedOutput salida) throws Exception {
		String respuesta = turnos(get(TURNOS).header("Host", hostX).cookie(new Cookie("jwt", tokenY)));

		assertEquals(List.of(turnoY), ids(respuesta), "la escuela de la sesión, no la del Host");
		assertTrue(salida.getAll().contains(ResolutorEscuelaPorHost.AVISO_SESION_Y_HOST), "aviso de sesión y host");
	}

	@Test
	void conSesionSinHostRegistrado_laSesionComoSiempre() throws Exception {
		assertEquals(List.of(turnoY), ids(turnos(get(TURNOS).cookie(new Cookie("jwt", tokenY)))));
		assertEquals(List.of(turnoY),
				ids(turnos(get(TURNOS).header("Host", "moiskimdo.es").cookie(new Cookie("jwt", tokenY)))));
	}

	// ------------------------------------------------------------------ utilidades

	private long escuela(String slug) {
		jdbc.update("INSERT INTO organizacion (slug, nombre) VALUES (?, 'Escuela IT dominio')", slug);
		long org = jdbc.queryForObject("SELECT id FROM organizacion WHERE slug = ?", Long.class, slug);
		jdbc.update("""
				INSERT INTO disciplina (organizacion_id, codigo, nombre, sistema_grados_id, edad_adulto, orden)
				VALUES (?, 'TAEKWONDO', 'Taekwondo IT', NULL, 14, 1)""", org);
		long disciplina = jdbc.queryForObject(
				"SELECT id FROM disciplina WHERE organizacion_id = ? AND codigo = 'TAEKWONDO'", Long.class, org);
		jdbc.update("""
				INSERT INTO plantilla_preinscripcion (version, activa, contenido, instrucciones, creada_en, organizacion_id,
				  disciplina_id)
				SELECT p.version, p.activa, p.contenido, p.instrucciones, p.creada_en, ?, ?
				FROM plantilla_preinscripcion p JOIN disciplina d ON d.id = p.disciplina_id
				WHERE p.organizacion_id = 1 AND d.codigo = 'TAEKWONDO' AND p.activa = TRUE
				ORDER BY p.version DESC LIMIT 1""", org, disciplina);
		jdbc.update("INSERT INTO sede (organizacion_id, nombre, activa) VALUES (?, 'Sede IT dominio', TRUE)", org);
		return org;
	}

	private long turno(long org) {
		jdbc.update("""
				INSERT INTO grupo (nombre, tipo, rango_edad_min, rango_edad_max, organizacion_id, sede_id, disciplina_id)
				VALUES (?, NULL, 0, 99, ?, (SELECT id FROM sede WHERE organizacion_id = ?),
				  (SELECT id FROM disciplina WHERE organizacion_id = ? AND codigo = 'TAEKWONDO'))""",
				"IT dominio " + marca, org, org, org);
		long grupo = jdbc.queryForObject("SELECT id FROM grupo WHERE organizacion_id = ?", Long.class, org);
		jdbc.update("""
				INSERT INTO turno (dia_semana, hora_inicio, hora_fin, tipo, grupo_id, organizacion_id)
				VALUES ('Lunes', '18:00', '19:00', NULL, ?, ?)""", grupo, org);
		return jdbc.queryForObject("SELECT id FROM turno WHERE organizacion_id = ?", Long.class, org);
	}

	private long alumno(long org, String correo) {
		long expediente = jdbc.queryForObject("SELECT COALESCE(MAX(numero_expediente), 60000) + 1 FROM alumno",
				Long.class);
		jdbc.update("""
				INSERT INTO alumno (activo, apellidos, autorizacion_web, direccion, fecha_nacimiento, nombre,
				  numero_expediente, telefono, tiene_derecho_examen, organizacion_id, email)
				VALUES (TRUE, 'PRUEBA', 1, 'Calle 1', '2010-01-01', 'Dominio', ?, '600000000', 0, ?, ?)""",
				expediente, org, correo);
		return jdbc.queryForObject("SELECT id FROM alumno WHERE numero_expediente = ? AND organizacion_id = ?",
				Long.class, expediente, org);
	}

	private int preinscripcionesDe(long org) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM preinscripcion WHERE organizacion_id = ?", Integer.class, org);
	}

	private String turnos(MockHttpServletRequestBuilder peticion) throws Exception {
		MvcResult r = mvc.perform(peticion).andReturn();
		String cuerpo = r.getResponse().getContentAsString(StandardCharsets.UTF_8);
		assertEquals(200, r.getResponse().getStatus(), cuerpo);
		return cuerpo;
	}

	private List<Long> ids(String cuerpo) throws Exception {
		List<Long> ids = new ArrayList<>();
		for (JsonNode turno : json.readTree(cuerpo)) {
			ids.add(turno.get("id").asLong());
		}
		return ids;
	}

	private MockHttpServletRequestBuilder registro(String correo) throws Exception {
		Map<String, String> cuerpo = Map.of("email", correo, "fechaNacimiento", "2010-01-01", "contrasena",
				"Clave-Segura-123");
		return post(REGISTRO).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo));
	}

	/** Alta pública real en ese turno (adulto, sin sesión). Cada una con su IP para no tocar el límite por IP. */
	private MockHttpServletRequestBuilder alta(long turno) throws Exception {
		int n = 42_000_000 + (++altas) + (int) (System.nanoTime() % 1_000_000);
		String dni = n + "" + "TRWAGMYFPDXBNJZSQVHLCKE".charAt(n % 23);
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("deporte", DEPORTE);
		body.put("turnoIds", List.of(turno));
		body.put("nombre", "Alta");
		body.put("apellidos", "Escuela Por Dominio");
		body.put("dni", dni);
		body.put("fechaNacimiento", "1990-01-01");
		body.put("direccion", "Calle Sintética 1");
		body.put("telefono", "612345678");
		body.put("telefono2", "");
		body.put("email", "familia-dominio-" + marca + "@example.test");
		body.put("observaciones", "");
		body.put("tutorNombre", "");
		body.put("tutorDni", "");
		body.put("tieneDiscapacidad", false);
		body.put("consentimientoFotografico", false);
		body.put("aceptacionNormas", true);
		body.put("firmanteNombre", "Firmante Dominio");
		body.put("firmaBase64", firma());
		return post("/api/preinscripciones").contentType(MediaType.APPLICATION_JSON)
				.header("X-Forwarded-For", "10.97." + (altas / 250) + "." + (altas % 250 + 1))
				.content(json.writeValueAsString(body));
	}

	private static String firma() throws Exception {
		BufferedImage imagen = new BufferedImage(320, 100, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = imagen.createGraphics();
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, imagen.getWidth(), imagen.getHeight());
		g.setColor(Color.BLACK);
		g.drawString("Firma de prueba por dominio", 20, 50);
		g.dispose();
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		ImageIO.write(imagen, "png", bytes);
		return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes.toByteArray());
	}
}
