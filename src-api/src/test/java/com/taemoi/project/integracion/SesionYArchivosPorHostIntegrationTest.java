package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
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
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.util.UriComponentsBuilder;

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
 * Fase 4, escuela por dominio, pieza 3 ({@code docs/multi-escuela/fase4-dominio-plan.md} §3.1 punto 5, §3.2 y §4.2):
 * sesión, Google y archivos por Host. Con la configuración de producción que importa aquí ({@code app.base.url} y
 * {@code cors.allowed.origin} en {@code https://moiskimdo.es}, cabeceras de reenvío de Spring y la plantilla de
 * {@code redirect-uri}), en el host {@code moiskimdo.es} todo sale exactamente como antes de la pieza: las cadenas
 * esperadas de este fichero se comprobaron con el código anterior. En el host de otra escuela X (en la tabla
 * {@code organizacion_dominio}): cookie solo del host, URL de archivos y de Google de su propio origen y, al entrar,
 * la escuela X si el usuario puede. Un host fuera de la tabla, como antes. Datos inventados; nada en la escuela 1
 * salvo dos membresías sintéticas que se retiran al acabar.
 */
@SpringBootTest(properties = {
		"app.base.url=https://moiskimdo.es",
		"cors.allowed.origin=https://moiskimdo.es",
		"server.forward-headers-strategy=framework",
		"spring.security.oauth2.client.registration.google.redirect-uri={baseUrl}/login/oauth2/code/{registrationId}" })
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SesionYArchivosPorHostIntegrationTest extends MySqlIntegrationTest {

	private static final String MOISKIMDO = "moiskimdo.es";
	private static final String ORIGEN_DE_HOY = "https://moiskimdo.es";
	private static final String CLAVE = "Clave-Segura-123";
	private static final String BORRAR_JWT = "jwt=; Path=/; Max-Age=0; Expires=Thu, 1 Jan 1970 00:00:00 GMT; HttpOnly; "
			+ "SameSite=Lax";
	private static final String BORRAR_JWT_DOMINIO = "jwt=; Path=/; Domain=moiskimdo.es; Max-Age=0; "
			+ "Expires=Thu, 1 Jan 1970 00:00:00 GMT; HttpOnly; SameSite=Lax";

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired PasswordEncoder encoder;
	@Autowired ResolutorEscuelaPorHost porHost;

	@MockitoBean EmailService email;

	final String marca = UUID.randomUUID().toString().substring(0, 8);
	final String hostX = "x-" + marca + ".test";
	final String origenX = "https://" + hostX;
	long orgX, eventoX, fotoX, alumnoX;
	long doble, soloUno;
	String correoDoble, correoSoloUno;
	String tokenGestorX;

	@BeforeAll
	void preparar() {
		jdbc.update("INSERT INTO organizacion (slug, nombre) VALUES (?, 'Escuela IT pieza 3')", "it-p3-x-" + marca);
		orgX = jdbc.queryForObject("SELECT id FROM organizacion WHERE slug = ?", Long.class, "it-p3-x-" + marca);
		jdbc.update("INSERT INTO organizacion_dominio (organizacion_id, host, principal) VALUES (?, ?, TRUE)", orgX,
				hostX);
		porHost.recargar();

		correoDoble = "it-p3-doble-" + marca + "@example.test";
		correoSoloUno = "it-p3-uno-" + marca + "@example.test";
		doble = usuario(correoDoble);
		soloUno = usuario(correoSoloUno);
		membresia(doble, 1L);
		membresia(doble, orgX);
		membresia(soloUno, 1L);
		long gestorX = usuario("it-p3-gestor-" + marca + "@example.test");
		membresia(gestorX, orgX);
		Usuario gestor = TenantContext.calcularCon(1L, () -> usuarios.findById(gestorX).orElseThrow());
		tokenGestorX = jwtService.generateToken(gestor, 600_000, orgX);

		long cartel = imagen(orgX, "/imagenes/org-" + orgX + "/eventos/cartel-" + marca + ".webp");
		String titulo = "IT p3 " + marca;
		jdbc.update("""
				INSERT INTO evento (titulo, descripcion, visible, orden, organizacion_id, foto_evento_id)
				VALUES (?, 'Descripción de prueba', TRUE, 1000, ?, ?)""", titulo, orgX, cartel);
		eventoX = jdbc.queryForObject("SELECT id FROM evento WHERE titulo = ?", Long.class, titulo);
		long foto = imagen(orgX, "/imagenes/org-" + orgX + "/fotos-eventos/foto-" + marca + ".webp");
		long miniatura = imagen(orgX, "/imagenes/org-" + orgX + "/fotos-eventos/mini-" + marca + ".webp");
		jdbc.update("INSERT INTO evento_foto (organizacion_id, evento_id, imagen_id, miniatura_id, orden)"
				+ " VALUES (?, ?, ?, ?, 1)", orgX, eventoX, foto, miniatura);
		fotoX = jdbc.queryForObject("SELECT id FROM evento_foto WHERE imagen_id = ?", Long.class, foto);
		jdbc.update("""
				INSERT INTO documento (nombre, ruta, tipo, url, evento_id, organizacion_id)
				VALUES (?, ?, 'application/pdf', ?, ?, ?)""", "bases-" + marca + ".pdf",
				"org-" + orgX + "/eventos/bases-" + marca + ".pdf",
				"/documentos/org-" + orgX + "/eventos/bases-" + marca + ".pdf", eventoX, orgX);

		long fotoAlumno = imagen(orgX, "/imagenes/org-" + orgX + "/alumnos/a-" + marca + ".webp");
		long expediente = jdbc.queryForObject("SELECT COALESCE(MAX(numero_expediente), 60000) + 1 FROM alumno",
				Long.class);
		jdbc.update("""
				INSERT INTO alumno (activo, apellidos, autorizacion_web, direccion, fecha_nacimiento, nombre,
				  numero_expediente, telefono, tiene_derecho_examen, organizacion_id, foto_alumno_id)
				VALUES (TRUE, 'PRUEBA', 1, 'Calle 1', '2010-01-01', 'Pieza3', ?, '600000000', 0, ?, ?)""",
				expediente, orgX, fotoAlumno);
		alumnoX = jdbc.queryForObject("SELECT id FROM alumno WHERE numero_expediente = ? AND organizacion_id = ?",
				Long.class, expediente, orgX);
	}

	@AfterAll
	void retirar() {
		jdbc.update("DELETE FROM membresia WHERE organizacion_id = 1 AND usuario_id IN (?, ?)", doble, soloUno);
		jdbc.update("DELETE FROM organizacion_dominio WHERE host = ?", hostX);
		porHost.recargar();
	}

	// ------------------------------------------------------------------ cookies del login con contraseña

	@Test
	void loginEnMoiskimdo_cookiesLiteralesDeHoy() throws Exception {
		MockHttpServletResponse r = entrar(MOISKIMDO, correoSoloUno);

		String token = token(r);
		assertEquals(List.of(BORRAR_JWT, BORRAR_JWT_DOMINIO,
				"jwt=" + token + "; Path=/; Domain=moiskimdo.es; HttpOnly; SameSite=Lax"), r.getHeaders("Set-Cookie"));
	}

	@Test
	void loginEnHostFueraDeLaTabla_cookiesComoHoy() throws Exception {
		MockHttpServletResponse r = entrar("desconocido-" + marca + ".test", correoSoloUno);

		String token = token(r);
		assertEquals(List.of(BORRAR_JWT, BORRAR_JWT_DOMINIO,
				"jwt=" + token + "; Path=/; Domain=moiskimdo.es; HttpOnly; SameSite=Lax"), r.getHeaders("Set-Cookie"));
	}

	@Test
	void loginEnHostX_cookieSoloDelHost() throws Exception {
		MockHttpServletResponse r = entrar(hostX, correoSoloUno);

		String token = token(r);
		assertEquals(List.of(BORRAR_JWT, "jwt=" + token + "; Path=/; HttpOnly; SameSite=Lax"),
				r.getHeaders("Set-Cookie"));
	}

	@Test
	void loginEnHostX_conOrigenDelMismoHost_noLoFrenaCors() throws Exception {
		jdbc.update("UPDATE usuario SET ultima_organizacion_id = 1 WHERE id = ?", doble);
		MockHttpServletResponse r = mvc.perform(login(hostX, correoDoble).header("Origin", origenX)).andReturn()
				.getResponse();
		assertEquals(200, r.getStatus(), r.getContentAsString());
	}

	@Test
	void logoutEnMoiskimdo_comoHoy_yEnHostX_soloDelHost() throws Exception {
		assertEquals(List.of(BORRAR_JWT, BORRAR_JWT_DOMINIO), mvc.perform(post("/api/auth/logout")
				.header("Host", MOISKIMDO).header("X-Forwarded-Proto", "https")).andReturn().getResponse()
				.getHeaders("Set-Cookie"));
		assertEquals(List.of(BORRAR_JWT), mvc.perform(post("/api/auth/logout").header("Host", hostX)
				.header("X-Forwarded-Proto", "https")).andReturn().getResponse().getHeaders("Set-Cookie"));
	}

	// ------------------------------------------------------------------ escuela elegida al entrar

	@Test
	void variasEscuelas_enHostX_entraEnX() throws Exception {
		jdbc.update("UPDATE usuario SET ultima_organizacion_id = 1 WHERE id = ?", doble);
		assertEquals(orgX, escuelaDe(token(entrar(hostX, correoDoble))));
	}

	@Test
	void variasEscuelas_enMoiskimdo_laReglaDeSiempre() throws Exception {
		jdbc.update("UPDATE usuario SET ultima_organizacion_id = NULL WHERE id = ?", doble);
		assertEquals(1L, escuelaDe(token(entrar(MOISKIMDO, correoDoble))), "sin última: la de menor id");

		jdbc.update("UPDATE usuario SET ultima_organizacion_id = ? WHERE id = ?", orgX, doble);
		assertEquals(orgX, escuelaDe(token(entrar(MOISKIMDO, correoDoble))), "la última usada");

		jdbc.update("UPDATE usuario SET ultima_organizacion_id = 1 WHERE id = ?", doble);
		assertEquals(1L, escuelaDe(token(entrar(MOISKIMDO, correoDoble))), "la última usada");
	}

	@Test
	void enHostX_sinEscuelaX_laReglaDeSiempre() throws Exception {
		assertEquals(1L, escuelaDe(token(entrar(hostX, correoSoloUno))));
	}

	// ------------------------------------------------------------------ Google: redirect-uri con plantilla

	@Test
	void googleEnMoiskimdo_redirectUriDeHoy() throws Exception {
		assertEquals("https://moiskimdo.es/login/oauth2/code/google", redirectUriDeGoogle(MOISKIMDO));
	}

	@Test
	void googleEnHostX_redirectUriDeSuHost() throws Exception {
		assertEquals(origenX + "/login/oauth2/code/google", redirectUriDeGoogle(hostX));
	}

	// ------------------------------------------------------------------ URL de archivos en el JSON

	@Test
	void eventoEnMoiskimdo_urlDeHoy_yEnHostX_laDeSuOrigen() throws Exception {
		String imagen = "/api/eventos/" + eventoX + "/imagen";
		assertEquals(ORIGEN_DE_HOY + imagen, leer(get("/api/eventos/" + eventoX), MOISKIMDO, tokenGestorX)
				.at("/fotoEvento/url").asText());
		assertEquals(origenX + imagen, leer(get("/api/eventos/" + eventoX), hostX, null).at("/fotoEvento/url")
				.asText());
		assertEquals(ORIGEN_DE_HOY + imagen, leer(get("/api/eventos/" + eventoX), "desconocido-" + marca + ".test",
				tokenGestorX).at("/fotoEvento/url").asText(), "host fuera de la tabla: como hoy");
	}

	@Test
	void fotosDelEvento_moiskimdoComoHoy_yHostX() throws Exception {
		String foto = "/api/eventos/" + eventoX + "/fotos/" + fotoX;
		assertEquals(ORIGEN_DE_HOY + foto, leer(get("/api/eventos/" + eventoX + "/fotos"), MOISKIMDO, tokenGestorX)
				.at("/0/url").asText());
		assertEquals(origenX + foto, leer(get("/api/eventos/" + eventoX + "/fotos"), hostX, null).at("/0/url")
				.asText());
	}

	@Test
	void documentosDelEvento_serializadorMoiskimdoComoHoy_yHostX() throws Exception {
		String documento = "/documentos/org-" + orgX + "/eventos/bases-" + marca + ".pdf";
		assertEquals(ORIGEN_DE_HOY + documento, leer(get("/api/eventos/" + eventoX + "/documentos"), MOISKIMDO,
				tokenGestorX).at("/0/url").asText());
		assertEquals(origenX + documento, leer(get("/api/eventos/" + eventoX + "/documentos"), hostX, tokenGestorX)
				.at("/0/url").asText());
	}

	@Test
	void fotoDelAlumno_moiskimdoComoHoy_yHostX() throws Exception {
		String foto = "/api/alumnos/" + alumnoX + "/imagen";
		assertEquals(ORIGEN_DE_HOY + foto, leer(get("/api/alumnos/" + alumnoX + "/completo"), MOISKIMDO,
				tokenGestorX).at("/fotoAlumno/url").asText());
		assertEquals(origenX + foto, leer(get("/api/alumnos/" + alumnoX + "/completo"), hostX, tokenGestorX)
				.at("/fotoAlumno/url").asText());
	}

	// ------------------------------------------------------------------ utilidades

	private MockHttpServletRequestBuilder login(String host, String correo) throws Exception {
		return post("/api/auth/signin").header("Host", host).header("X-Forwarded-Proto", "https")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("email", correo, "contrasena", CLAVE)));
	}

	private MockHttpServletResponse entrar(String host, String correo) throws Exception {
		MockHttpServletResponse r = mvc.perform(login(host, correo)).andReturn().getResponse();
		assertEquals(200, r.getStatus(), r.getContentAsString());
		return r;
	}

	private String token(MockHttpServletResponse r) throws Exception {
		return json.readTree(r.getContentAsString()).get("token").asText();
	}

	private Long escuelaDe(String token) {
		return jwtService.verificar(token).organizacionId();
	}

	/** Como nginx en {@code /oauth2/}: {@code Host}, {@code X-Forwarded-Proto/Host/Port}. */
	private String redirectUriDeGoogle(String host) throws Exception {
		MockHttpServletResponse r = mvc.perform(get("/oauth2/authorization/google").header("Host", host)
				.header("X-Forwarded-Proto", "https").header("X-Forwarded-Host", host)
				.header("X-Forwarded-Port", "443")).andReturn().getResponse();
		assertEquals(302, r.getStatus());
		String destino = r.getRedirectedUrl();
		assertTrue(destino.startsWith("https://accounts.google.com/"), destino);
		String redirectUri = UriComponentsBuilder.fromUriString(destino).build().getQueryParams()
				.getFirst("redirect_uri");
		return URLDecoder.decode(redirectUri, StandardCharsets.UTF_8);
	}

	private JsonNode leer(MockHttpServletRequestBuilder peticion, String host, String token) throws Exception {
		peticion.header("Host", host).header("X-Forwarded-Proto", "https");
		if (token != null) {
			peticion.cookie(new Cookie("jwt", token));
		}
		MockHttpServletResponse r = mvc.perform(peticion).andReturn().getResponse();
		String cuerpo = r.getContentAsString(StandardCharsets.UTF_8);
		assertEquals(200, r.getStatus(), cuerpo);
		return json.readTree(cuerpo);
	}

	private long usuario(String correo) {
		return TenantContext.calcularCon(1L, () -> {
			Usuario usuario = new Usuario();
			usuario.setNombre("Pieza");
			usuario.setApellidos("Tres");
			usuario.setEmail(correo);
			usuario.setContrasena(encoder.encode(CLAVE));
			usuario.setAuthProvider(AuthProvider.LOCAL);
			return usuarios.save(usuario).getId();
		});
	}

	private void membresia(long usuario, long organizacion) {
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, 'ADMINISTRADOR')", usuario,
				organizacion);
	}

	private long imagen(long organizacion, String url) {
		String nombre = url.substring(url.lastIndexOf('/') + 1);
		jdbc.update("INSERT INTO imagen (nombre, ruta, tipo, url, organizacion_id) VALUES (?, ?, 'image/webp', ?, ?)",
				nombre, url.substring("/imagenes/".length()), url, organizacion);
		return jdbc.queryForObject("SELECT id FROM imagen WHERE nombre = ?", Long.class, nombre);
	}
}
