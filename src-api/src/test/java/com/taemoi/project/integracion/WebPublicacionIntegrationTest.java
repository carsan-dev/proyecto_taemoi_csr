package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
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
import com.taemoi.project.entities.AuthProvider;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.ResolutorEscuelaPorHost;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.JwtService;

import jakarta.servlet.http.Cookie;

/**
 * Pieza W3 de la web por plantilla (fase4-web-plan.md §5 y §8): estado de la web para el panel y publicar con un tema.
 * Lo publica el propietario de la escuela ({@code /api/escuela/web/publicacion}) o el superadmin
 * ({@code /api/plataforma/escuelas/{id}/web/publicacion}); nunca Moiskimdo (marca heredada, tiene su web propia).
 * También el tema de la vista previa ({@code /api/publico/web?vistaPrevia=true&tema=…}), solo para la gestión.
 *
 * <pre>
 * P  sin fila; propietario (publica por primera vez, cambia de tema y repite).
 * Q  publicada (estandar); propietario. No cambia cuando publica P (aislamiento).
 * R  sin fila; gestor, profesor, familia y responsable de sede (403); el superadmin publica.
 * S  sin fila; propietario para los 400.
 * Z  con fila sin publicar (linea-comarcal); gestor y familia para la vista previa.
 * </pre>
 * Datos inventados en escuelas sintéticas; la 1 (Moiskimdo) no se toca.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WebPublicacionIntegrationTest extends MySqlIntegrationTest {

	private static final long ORG1 = 1L;
	private static final String ESTADO = "/api/escuela/web";
	private static final String PUBLICAR = "/api/escuela/web/publicacion";
	private static final String PUBLICO = "/api/publico/web";
	private static final List<String> TEMAS = List.of("linea-comarcal", "chandal", "sede-por-pantalla", "estandar");

	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired ObjectMapper json;
	@Autowired JwtService jwt;
	@Autowired UsuarioRepository usuarios;
	@Autowired ResolutorEscuelaPorHost porHost;

	@MockitoBean JavaMailSender mailSender;

	final String marca = UUID.randomUUID().toString().substring(0, 8);
	long p, q, r, s, z;
	long propietarioP, propietarioQ, propietarioS, gestorR, profesorR, familiaR, responsableR, gestorZ, familiaZ,
			superadmin;
	final Map<Long, String> hosts = new HashMap<>();

	@BeforeAll
	void sembrar() {
		p = organizacion("p", "Escuela W3 P " + marca);
		propietarioP = cuenta("propietario-p", p, "PROPIETARIO");

		q = organizacion("q", "Escuela W3 Q " + marca);
		web(q, "estandar", true, "2026-09-15 09:00:00");
		propietarioQ = cuenta("propietario-q", q, "PROPIETARIO");

		r = organizacion("r", "Escuela W3 R " + marca);
		gestorR = cuenta("gestor-r", r, "ADMINISTRADOR");
		profesorR = cuenta("profesor-r", r, "PROFESOR");
		familiaR = cuenta("familia-r", r, "FAMILIA");
		alumnoDe(r, familiaR);
		responsableR = cuenta("responsable-r", r, "RESPONSABLE_SEDE");

		s = organizacion("s", "Escuela W3 S " + marca);
		propietarioS = cuenta("propietario-s", s, "PROPIETARIO");

		z = organizacion("z", "Escuela W3 Z " + marca);
		web(z, "linea-comarcal", false, null);
		gestorZ = cuenta("gestor-z", z, "ADMINISTRADOR");
		familiaZ = cuenta("familia-z", z, "FAMILIA");
		alumnoDe(z, familiaZ);

		superadmin = cuentaSinEscuela("superadmin");
		assertEquals(1, jdbc.update("UPDATE usuario SET es_admin_plataforma = TRUE WHERE id = ?", superadmin));

		for (long org : List.of(p, q, r, s, z)) {
			String host = "w3-" + org + "-" + marca + ".test";
			jdbc.update("INSERT INTO organizacion_dominio (organizacion_id, host, principal) VALUES (?, ?, TRUE)", org,
					host);
			hosts.put(org, host);
		}
		porHost.recargar();
	}

	@AfterAll
	void retirarDominios() {
		hosts.values().forEach(host -> jdbc.update("DELETE FROM organizacion_dominio WHERE host = ?", host));
		porHost.recargar();
	}

	// ------------------------------------------------------------------ propietario

	@Test
	void propietario_sinFila_estadoSinPublicar_conLosCuatroTemas() throws Exception {
		JsonNode estado = cuerpo(mvc.perform(get(ESTADO).cookie(sesion(propietarioS, s))).andReturn());
		assertTrue(estado.get("tema").isNull(), estado.toString());
		assertFalse(estado.get("publicada").asBoolean());
		assertTrue(estado.get("publicadaEn").isNull());
		assertEquals(TEMAS, textos(estado.get("temasDisponibles")));
		assertFalse(estado.has("publicadaPor"), "sin el id de quien publicó");
	}

	/**
	 * Primera publicación (crea la fila), cambio de tema y repetición del mismo tema (idempotente: ni cambia la fecha
	 * ni el autor). Cada 2xx deja UNA fila de auditoría explícita en la escuela (sin la genérica del filtro); la web
	 * pública refleja el tema nuevo en cuanto se publica.
	 */
	@Test
	void propietario_publicaPrimeraVez_cambiaDeTema_yRepite() throws Exception {
		assertEquals(404, anonimo(p, get(PUBLICO)).getResponse().getStatus(), "sin publicar: sin web");
		Cookie sesion = sesion(propietarioP, p);

		long antes = ultimoEvento();
		JsonNode primera = cuerpo(publicar(PUBLICAR, sesion, "{\"tema\":\"chandal\"}"));
		assertEquals("chandal", primera.get("tema").asText());
		assertTrue(primera.get("publicada").asBoolean());
		assertFalse(primera.get("publicadaEn").isNull());
		assertEquals(TEMAS, textos(primera.get("temasDisponibles")));
		Map<String, Object> fila = jdbc.queryForMap(
				"SELECT tema, publicada, publicada_en, publicada_por FROM web_escuela WHERE organizacion_id = ?", p);
		assertEquals("chandal", fila.get("tema"));
		assertEquals(Boolean.TRUE, fila.get("publicada"));
		assertEquals(propietarioP, ((Number) fila.get("publicada_por")).longValue());
		assertNotNull(fila.get("publicada_en"));
		assertEquals("chandal", cuerpo(anonimo(p, get(PUBLICO))).get("tema").asText());
		List<Map<String, Object>> eventos = eventosDesde(p, antes);
		assertEquals(1, eventos.size(), eventos.toString());
		assertEventoPublicacion(eventos.get(0), "CREATE", PUBLICAR, propietarioP,
				"WEB TEMA_PUBLICADO organizacion=#" + p + " tema=CHANDAL primera=SI");

		jdbc.update("UPDATE web_escuela SET publicada_en = '2026-10-01 10:00:00' WHERE organizacion_id = ?", p);
		antes = ultimoEvento();
		JsonNode cambio = cuerpo(publicar(PUBLICAR, sesion, "{\"tema\":\"estandar\"}"));
		assertEquals("estandar", cambio.get("tema").asText());
		Timestamp publicadaEn = jdbc.queryForObject("SELECT publicada_en FROM web_escuela WHERE organizacion_id = ?",
				Timestamp.class, p);
		assertTrue(publicadaEn.toLocalDateTime().getYear() >= 2026
				&& !"2026-10-01T10:00".equals(publicadaEn.toLocalDateTime().toString()), "fecha nueva: " + publicadaEn);
		assertEquals("estandar", cuerpo(anonimo(p, get(PUBLICO))).get("tema").asText());
		eventos = eventosDesde(p, antes);
		assertEquals(1, eventos.size(), eventos.toString());
		assertEventoPublicacion(eventos.get(0), "UPDATE", PUBLICAR, propietarioP,
				"WEB TEMA_PUBLICADO organizacion=#" + p + " tema=ESTANDAR primera=NO");

		// El mismo tema otra vez: 200, sin cambios (fecha y autor iguales) y registrado con sinCambios=SI.
		jdbc.update("UPDATE web_escuela SET publicada_en = '2026-10-02 11:00:00', publicada_por = 7 "
				+ "WHERE organizacion_id = ?", p);
		antes = ultimoEvento();
		JsonNode repetida = cuerpo(publicar(PUBLICAR, sesion, "{\"tema\":\"estandar\"}"));
		assertEquals("estandar", repetida.get("tema").asText());
		assertEquals("2026-10-02T11:00:00", repetida.get("publicadaEn").asText());
		assertEquals(7L, jdbc.queryForObject("SELECT publicada_por FROM web_escuela WHERE organizacion_id = ?",
				Long.class, p));
		eventos = eventosDesde(p, antes);
		assertEquals(1, eventos.size(), eventos.toString());
		assertEventoPublicacion(eventos.get(0), "UPDATE", PUBLICAR, propietarioP,
				"WEB TEMA_PUBLICADO organizacion=#" + p + " tema=ESTANDAR primera=NO sinCambios=SI");

		JsonNode estado = cuerpo(mvc.perform(get(ESTADO).cookie(sesion)).andReturn());
		assertEquals("estandar", estado.get("tema").asText());
		assertTrue(estado.get("publicada").asBoolean());
		assertEquals("2026-10-02T11:00:00", estado.get("publicadaEn").asText());

		// Aislamiento: la web de Q no cambia.
		assertEquals("estandar", jdbc.queryForObject("SELECT tema FROM web_escuela WHERE organizacion_id = ?",
				String.class, q));
		assertEquals("2026-09-15T09:00", jdbc.queryForObject(
				"SELECT publicada_en FROM web_escuela WHERE organizacion_id = ?", Timestamp.class, q).toLocalDateTime()
				.toString());
		assertEquals(0, eventosDesde(q, 0).size(), "nada en el registro de Q");
	}

	/** Cada propietario ve y publica SOLO la web de su escuela activa (la ruta no lleva escuela). */
	@Test
	void propietario_soloSuEscuela() throws Exception {
		JsonNode estadoQ = cuerpo(mvc.perform(get(ESTADO).cookie(sesion(propietarioQ, q))).andReturn());
		assertEquals("estandar", estadoQ.get("tema").asText());
		assertTrue(estadoQ.get("publicada").asBoolean());
		assertEquals("2026-09-15T09:00:00", estadoQ.get("publicadaEn").asText());
	}

	// ------------------------------------------------------------------ 400

	@Test
	void temaNoValido_400_yNadaCambia() throws Exception {
		Cookie sesion = sesion(propietarioS, s);
		// Un 4xx deja solo la fila genérica del filtro, como siempre.
		long antes = ultimoEvento();
		assertEquals(400, publicar(PUBLICAR, sesion, "{\"tema\":\"rosa\"}").getResponse().getStatus());
		List<Map<String, Object>> errores = eventosDesde(s, antes);
		assertEquals(1, errores.size(), errores.toString());
		assertTrue(((String) errores.get(0).get("resumen")).matches("^UPDATE \\S+( #\\d+)? -> 400$"),
				errores.toString());
		for (String cuerpo : List.of("{\"tema\":\"rosa\"}", "{\"tema\":\"\"}", "{\"tema\":null}", "{}",
				"{\"tema\":\"CHANDAL\"}", "{\"tema\":42}", "no es json", "[]")) {
			MvcResult r = publicar(PUBLICAR, sesion, cuerpo);
			assertEquals(400, r.getResponse().getStatus(), cuerpo + " → " + r.getResponse().getContentAsString());
			assertTrue(json.readTree(r.getResponse().getContentAsString()).hasNonNull("mensaje"), cuerpo);
		}
		MvcResult sinCuerpo = mvc.perform(put(PUBLICAR).cookie(sesion)).andReturn();
		assertEquals(400, sinCuerpo.getResponse().getStatus(), sinCuerpo.getResponse().getContentAsString());
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM web_escuela WHERE organizacion_id = ?", Integer.class,
				s));
	}

	// ------------------------------------------------------------------ 403

	@Test
	void gestorProfesorFamiliaYResponsable_403_yNadaCambia() throws Exception {
		for (long cuenta : List.of(gestorR, profesorR, familiaR, responsableR)) {
			Cookie sesion = sesion(cuenta, r);
			assertEquals(403, mvc.perform(get(ESTADO).cookie(sesion)).andReturn().getResponse().getStatus(),
					"GET de " + cuenta);
			assertEquals(403, publicar(PUBLICAR, sesion, "{\"tema\":\"chandal\"}").getResponse().getStatus(),
					"PUT de " + cuenta);
			assertEquals(403, publicar("/api/plataforma/escuelas/" + r + "/web/publicacion", sesion,
					"{\"tema\":\"chandal\"}").getResponse().getStatus(), "plataforma, PUT de " + cuenta);
		}
		assertEquals(401, publicar(PUBLICAR, null, "{\"tema\":\"chandal\"}").getResponse().getStatus(), "anónimo");
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM web_escuela WHERE organizacion_id = ?", Integer.class,
				r));
	}

	// ------------------------------------------------------------------ superadmin

	/** El superadmin (sin escuela) publica la de otra escuela por su id; el registro va a ESA escuela. */
	@Test
	void superadmin_publicaPorId_registroEnLaEscuelaDestino() throws Exception {
		Cookie sesion = sesion(superadmin, ORG1);
		String ruta = "/api/plataforma/escuelas/" + r + "/web";
		JsonNode antesDe = cuerpo(mvc.perform(get(ruta).cookie(sesion)).andReturn());
		assertFalse(antesDe.get("publicada").asBoolean());

		long antes = ultimoEvento();
		JsonNode publicada = cuerpo(publicar(ruta + "/publicacion", sesion, "{\"tema\":\"sede-por-pantalla\"}"));
		assertEquals("sede-por-pantalla", publicada.get("tema").asText());
		assertTrue(publicada.get("publicada").asBoolean());
		assertEquals(superadmin, jdbc.queryForObject("SELECT publicada_por FROM web_escuela WHERE organizacion_id = ?",
				Long.class, r));
		assertEquals("sede-por-pantalla", cuerpo(anonimo(r, get(PUBLICO))).get("tema").asText());
		List<Map<String, Object>> eventos = eventosDesde(r, antes);
		assertEquals(1, eventos.size(), eventos.toString());
		assertEventoPublicacion(eventos.get(0), "CREATE", ruta + "/publicacion", superadmin,
				"WEB TEMA_PUBLICADO organizacion=#" + r + " tema=SEDE_POR_PANTALLA primera=SI");
		assertEquals(0, eventosDesde(ORG1, antes).size(), "nada en la escuela activa del superadmin (la 1)");

		assertEquals(404, publicar("/api/plataforma/escuelas/999999999/web/publicacion", sesion,
				"{\"tema\":\"chandal\"}").getResponse().getStatus(), "escuela que no existe");
	}

	// ------------------------------------------------------------------ Moiskimdo

	/** «Web · Tema» no existe para Moiskimdo: ni su propietario ni el superadmin publican; 409 y sin fila. */
	@Test
	void moiskimdo_409_sinFila() throws Exception {
		Usuario admin = TenantContext.calcularCon(ORG1,
				() -> usuarios.findByEmailIgnoreCase("admin-it@example.test").orElseThrow());
		Cookie propietario = new Cookie("jwt", jwt.generateToken(admin, 600_000, ORG1));
		MvcResult estado = mvc.perform(get(ESTADO).cookie(propietario)).andReturn();
		assertEquals(409, estado.getResponse().getStatus(), estado.getResponse().getContentAsString());
		MvcResult r1 = publicar(PUBLICAR, propietario, "{\"tema\":\"chandal\"}");
		assertEquals(409, r1.getResponse().getStatus(), r1.getResponse().getContentAsString());
		assertTrue(json.readTree(r1.getResponse().getContentAsString()).get("mensaje").asText().contains("web propia"));

		Cookie plataforma = sesion(superadmin, ORG1);
		assertEquals(409, mvc.perform(get("/api/plataforma/escuelas/1/web").cookie(plataforma)).andReturn()
				.getResponse().getStatus());
		assertEquals(409, publicar("/api/plataforma/escuelas/1/web/publicacion", plataforma, "{\"tema\":\"chandal\"}")
				.getResponse().getStatus());
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM web_escuela WHERE organizacion_id = 1",
				Integer.class));
	}

	// ------------------------------------------------------------------ vista previa con tema

	/** La gestión ve la vista previa con el tema que elige en la pantalla, sin guardarlo. */
	@Test
	void vistaPrevia_temaDeLaGestion() throws Exception {
		JsonNode web = cuerpo(mvc.perform(get(PUBLICO).param("vistaPrevia", "true").param("tema", "chandal")
				.cookie(sesion(gestorZ, z))).andReturn());
		assertEquals("chandal", web.get("tema").asText());
		assertTrue(web.get("vistaPrevia").asBoolean());
		assertFalse(web.get("publicada").asBoolean());
		assertEquals("linea-comarcal", jdbc.queryForObject("SELECT tema FROM web_escuela WHERE organizacion_id = ?",
				String.class, z), "no se guarda");

		// Sin tema, el guardado.
		assertEquals("linea-comarcal", cuerpo(mvc.perform(get(PUBLICO).param("vistaPrevia", "true")
				.cookie(sesion(gestorZ, z))).andReturn()).get("tema").asText());

		// Tema que no existe, en la vista previa de la gestión: 400.
		MvcResult malo = mvc.perform(get(PUBLICO).param("vistaPrevia", "true").param("tema", "rosa")
				.cookie(sesion(gestorZ, z))).andReturn();
		assertEquals(400, malo.getResponse().getStatus(), malo.getResponse().getContentAsString());
	}

	/** Anónimo o sin rol de gestión: el parámetro {@code tema} se ignora (también uno que no existe). */
	@Test
	void vistaPrevia_temaIgnoradoSinGestion() throws Exception {
		for (String tema : List.of("chandal", "rosa")) {
			JsonNode anonima = cuerpo(anonimo(q, get(PUBLICO).param("vistaPrevia", "true").param("tema", tema)));
			assertEquals("estandar", anonima.get("tema").asText(), "anónimo con vista previa, " + tema);
			JsonNode sinVista = cuerpo(anonimo(q, get(PUBLICO).param("tema", tema)));
			assertEquals("estandar", sinVista.get("tema").asText(), "anónimo, " + tema);
			assertEquals(404, mvc.perform(get(PUBLICO).param("vistaPrevia", "true").param("tema", tema)
					.cookie(sesion(familiaZ, z))).andReturn().getResponse().getStatus(), "familia, " + tema);
		}
	}

	// ------------------------------------------------------------------ utilidades

	private void assertEventoPublicacion(Map<String, Object> evento, String accion, String endpoint, long usuario,
			String resumen) {
		assertEquals(resumen, evento.get("resumen"), evento.toString());
		assertEquals(accion, evento.get("accion"));
		assertEquals("web", evento.get("modulo"));
		assertEquals(endpoint, evento.get("endpoint"));
		assertEquals(usuario, ((Number) evento.get("usuario_id")).longValue());
		assertEquals(200, ((Number) evento.get("estado_http")).intValue());
	}

	private long ultimoEvento() {
		Long id = jdbc.queryForObject("SELECT MAX(id) FROM auditoria_evento", Long.class);
		return id == null ? 0 : id;
	}

	private List<Map<String, Object>> eventosDesde(long org, long desde) {
		return jdbc.queryForList("""
				SELECT id, accion, modulo, endpoint, usuario_id, estado_http, resumen FROM auditoria_evento
				WHERE organizacion_id = ? AND id > ? AND metodo_http <> 'GET' ORDER BY id""", org, desde);
	}

	private MvcResult publicar(String ruta, Cookie sesion, String cuerpo) throws Exception {
		MockHttpServletRequestBuilder peticion = put(ruta).contentType(MediaType.APPLICATION_JSON).content(cuerpo);
		if (sesion != null) {
			peticion.cookie(sesion);
		}
		return mvc.perform(peticion).andReturn();
	}

	private MvcResult anonimo(long org, MockHttpServletRequestBuilder peticion) throws Exception {
		return mvc.perform(peticion.header("Host", hosts.get(org))).andReturn();
	}

	private JsonNode cuerpo(MvcResult r) throws Exception {
		assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		return json.readTree(r.getResponse().getContentAsString());
	}

	private static List<String> textos(JsonNode lista) {
		List<String> textos = new java.util.ArrayList<>();
		lista.forEach(n -> textos.add(n.asText()));
		return textos;
	}

	private Cookie sesion(long usuarioId, long org) {
		Usuario usuario = TenantContext.calcularCon(ORG1, () -> usuarios.findById(usuarioId).orElseThrow());
		return new Cookie("jwt", jwt.generateToken(usuario, 600_000, org));
	}

	private long organizacion(String sufijo, String nombre) {
		String slug = "it-w3-" + sufijo + "-" + marca;
		jdbc.update("INSERT INTO organizacion (slug, nombre) VALUES (?, ?)", slug, nombre);
		return jdbc.queryForObject("SELECT id FROM organizacion WHERE slug = ?", Long.class, slug);
	}

	private void web(long org, String tema, boolean publicada, String publicadaEn) {
		jdbc.update("INSERT INTO web_escuela (organizacion_id, tema, publicada, publicada_en) VALUES (?, ?, ?, ?)", org,
				tema, publicada, publicadaEn);
	}

	/** Alumno activo vinculado a mano a una cuenta de familia (le abre la sesión en {@code org}). */
	private void alumnoDe(long org, long usuario) {
		long expediente = jdbc.queryForObject("SELECT COALESCE(MAX(numero_expediente), 60000) + 1 FROM alumno",
				Long.class);
		jdbc.update("""
				INSERT INTO alumno (activo, apellidos, autorizacion_web, direccion, fecha_nacimiento, nombre,
				  numero_expediente, telefono, tiene_derecho_examen, organizacion_id)
				VALUES (TRUE, 'PRUEBA', 1, 'Calle 1', '2010-01-01', 'Familia W3', ?, '600000000', 0, ?)""",
				expediente, org);
		long alumno = jdbc.queryForObject("SELECT id FROM alumno WHERE numero_expediente = ? AND organizacion_id = ?",
				Long.class, expediente, org);
		jdbc.update("""
				INSERT INTO usuario_alumno (usuario_id, alumno_id, organizacion_id, relacion, origen)
				VALUES (?, ?, ?, 'TUTOR', 'MANUAL')""", usuario, alumno, org);
	}

	private long cuenta(String nombre, long org, String rol) {
		long id = cuentaSinEscuela(nombre);
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, ?)", id, org, rol);
		return id;
	}

	private long cuentaSinEscuela(String nombre) {
		String email = "it-w3-" + nombre + "-" + marca + "@example.test";
		return TenantContext.calcularCon(ORG1, () -> {
			Usuario usuario = new Usuario();
			usuario.setNombre("Usuario");
			usuario.setApellidos("Prueba");
			usuario.setEmail(email);
			usuario.setContrasena("fake-Password-123");
			usuario.setAuthProvider(AuthProvider.LOCAL);
			return usuarios.save(usuario).getId();
		});
	}
}
