package com.taemoi.project.integracion;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.config.OAuth2AuthenticationSuccessHandler;
import com.taemoi.project.entities.AuthProvider;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.GrupoService;
import com.taemoi.project.services.JwtService;

import jakarta.servlet.http.Cookie;

/**
 * Pieza 9-5 (decisiones 370, 371 y 65) contra MySQL: la puerta de sesión del responsable de sede en login con
 * contraseña y con Google, cambio de escuela y cada petición, y lo que {@code roles}, {@code user} y {@code permisos}
 * devuelven a cada combinación de cuenta: R solo, R + Pr, R + F, R + gestión y R sin sedes.
 *
 * <p>Lo que cada combinación debe poder distinguir sin deducirlo es el contrato con el panel: PANEL_SEDE (panel
 * limitado a sus sedes), MI_CAJA y MIS_GRUPOS (R + Pr suma las dos, 371), ELEGIR_VISTA (R + F, 371). Escuelas
 * sintéticas ({@link EscuelaConSedesDePrueba}); datos inventados.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ResponsableSedeEntradaIntegrationTest extends MySqlIntegrationTest {

	private static final long ORG1 = 1L;
	private static final String CLAVE = "Clave-Segura-95";

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired PasswordEncoder encoder;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;
	@Autowired OAuth2AuthenticationSuccessHandler google;

	@MockitoBean JavaMailSender mailSender;

	EscuelaConSedesDePrueba e;
	EscuelaConSedesDePrueba otra;

	@BeforeAll
	void preparar() {
		e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-95-entrada");
		otra = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-95-otra");
	}

	@AfterEach
	void sinFugaDeOrganizacion() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
	}

	// ------------------------------------------------------------------ R solo

	@Test
	void responsableSolo_entraConContrasena_conSusSedesYSinFichaDeFamilia() throws Exception {
		Usuario cuenta = cuenta("solo");
		e.responsable(cuenta.getId(), e.sA, e.sC);

		String token = login(cuenta.getEmail());

		assertEquals(e.org, jwtService.verificar(token).organizacionId());
		mvc.perform(get("/api/auth/roles").cookie(jwt(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*]", containsInAnyOrder("ROLE_RESPONSABLE_SEDE")));
		mvc.perform(get("/api/auth/user").cookie(jwt(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(cuenta.getEmail()))
				.andExpect(jsonPath("$.alumnoDTO").doesNotExist());

		JsonNode permisos = leer(get("/api/auth/permisos"), token, 200);
		assertEquals(List.of("RESPONSABLE_SEDE"), textos(permisos.get("roles")));
		assertEquals(List.of(e.sA, e.sC), ids(permisos.get("sedes")));
		assertTrue(permisos.get("variasSedes").asBoolean());
		assertFalse(permisos.get("tieneGrupos").asBoolean());
		assertEquals(List.of("PANEL_SEDE"), textos(permisos.get("capacidades")));

		assertEquals(200, estado(get("/api/escuela/marca"), token));
		assertEquals(1, leer(get("/api/auth/organizaciones"), token, 200).size());
		assertEquals(200, estado(get("/api/auth/plataforma"), token));
		assertEquals(403, estado(get("/api/auth/user/alumnos"), token), "no es familia");
		assertEquals(403, estado(get("/api/caja/profesor/cargos-pendientes"), token), "no es profesor");
		assertEquals(403, estado(get("/api/profesor/grupos"), token), "no es profesor");
	}

	@Test
	void responsableSolo_vinculadoAUnAlumnoActivo_noRecibeFicha() throws Exception {
		Usuario cuenta = cuenta("solo-vinculado");
		e.responsable(cuenta.getId(), e.sA);
		jdbc.update("UPDATE usuario SET alumno_id = ? WHERE id = ?", e.aA, cuenta.getId());

		mvc.perform(get("/api/auth/user").cookie(jwt(login(cuenta.getEmail()))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.alumnoDTO").doesNotExist());
	}

	@Test
	void responsableSolo_entraConGoogle() throws Exception {
		Usuario cuenta = cuenta("solo-google");
		e.responsable(cuenta.getId(), e.sA);

		MockHttpServletResponse respuesta = loginGoogle(cuenta.getEmail());

		String redirigido = respuesta.getRedirectedUrl();
		assertNotNull(redirigido);
		assertFalse(redirigido.contains("error="), redirigido);
		String token = cookieJwt(respuesta.getHeaders(HttpHeaders.SET_COOKIE));
		assertNotNull(token, "la cookie de sesión");
		assertEquals(e.org, jwtService.verificar(token).organizacionId());
		mvc.perform(get("/api/auth/roles").cookie(jwt(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*]", containsInAnyOrder("ROLE_RESPONSABLE_SEDE")));
	}

	@Test
	void responsableSolo_membresiaDesactivada_laSiguientePeticionNoEntra() throws Exception {
		Usuario cuenta = cuenta("solo-baja");
		e.responsable(cuenta.getId(), e.sA);
		String token = login(cuenta.getEmail());
		assertEquals(200, estado(get("/api/alumnos"), token));

		jdbc.update("UPDATE membresia SET activa = FALSE WHERE usuario_id = ?", cuenta.getId());

		assertEquals(401, estado(get("/api/alumnos"), token));
		assertEquals(401, estado(get("/api/auth/roles"), token));
	}

	@Test
	void responsableSolo_tokenDeOtraEscuelaSinMembresia_noEntra() throws Exception {
		Usuario cuenta = cuenta("solo-falso");
		e.responsable(cuenta.getId(), e.sA);
		String deOtra = jwtService.generateToken(cuenta, 600_000, otra.org);

		assertEquals(401, estado(get("/api/auth/roles"), deOtra));
		assertEquals(401, estado(get("/api/alumnos"), deOtra));
	}

	// ------------------------------------------------------------------ cambio de escuela

	@Test
	void cambioDeEscuela_responsableEnLasDos_soloVeLaActivaYSusSedes() throws Exception {
		Usuario cuenta = cuenta("dos-escuelas");
		e.responsable(cuenta.getId(), e.sA);
		otra.responsable(cuenta.getId(), otra.sB);

		String token = jwtService.generateToken(cuenta, 600_000, e.org);
		assertEquals(List.of(e.sA), ids(leer(get("/api/auth/permisos"), token, 200).get("sedes")));
		assertEquals(2, leer(get("/api/auth/organizaciones"), token, 200).size());

		String enOtra = cambiar(token, otra.org);

		assertEquals(otra.org, jwtService.verificar(enOtra).organizacionId());
		assertEquals(List.of(otra.sB), ids(leer(get("/api/auth/permisos"), enOtra, 200).get("sedes")));
		assertEquals(200, estado(get("/api/alumnos/" + otra.aB), enOtra));
		assertEquals(403, estado(get("/api/alumnos/" + e.aA), enOtra), "alumno de la escuela anterior");
		assertEquals(403, estado(get("/api/caja/sedes/" + e.sA + "/resumen"), enOtra), "sede de la escuela anterior");
	}

	@Test
	void cambioDeEscuela_aUnaSinMembresia_403() throws Exception {
		Usuario cuenta = cuenta("cambio-prohibido");
		e.responsable(cuenta.getId(), e.sA);
		String token = jwtService.generateToken(cuenta, 600_000, e.org);

		assertEquals(403, estado(post("/api/auth/organizacion").contentType(MediaType.APPLICATION_JSON)
				.content("{\"organizacionId\":" + otra.org + "}"), token));
	}

	// ------------------------------------------------------------------ R + Pr (371)

	@Test
	void responsableYProfesor_entraComoResponsableYSumaMiCajaYMisGrupos() throws Exception {
		Usuario cuenta = cuenta("con-profesor");
		e.responsable(cuenta.getId(), e.sA);
		membresia(cuenta.getId(), e.org, "PROFESOR");
		// Un grupo de OTRA sede: Mis grupos lo ve, pero no abre nada de la sede B como responsable.
		asignar(cuenta.getId(), e.gB);

		String token = login(cuenta.getEmail());

		mvc.perform(get("/api/auth/roles").cookie(jwt(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*]", containsInAnyOrder("ROLE_RESPONSABLE_SEDE", "ROLE_PROFESOR")));
		mvc.perform(get("/api/auth/user").cookie(jwt(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.alumnoDTO").doesNotExist());
		JsonNode permisos = leer(get("/api/auth/permisos"), token, 200);
		assertEquals(List.of("RESPONSABLE_SEDE", "PROFESOR"), textos(permisos.get("roles")));
		assertEquals(List.of(e.sA), ids(permisos.get("sedes")));
		assertTrue(permisos.get("tieneGrupos").asBoolean());
		assertEquals(List.of("MIS_GRUPOS", "MI_CAJA", "PANEL_SEDE"), textos(permisos.get("capacidades")));

		// Como responsable: el panel de su sede, ya no la lista blanca de la sesión solo-profesor.
		assertEquals(200, estado(get("/api/alumnos/" + e.aA), token));
		assertEquals(200, estado(get("/api/tesoreria/resumen"), token));
		assertEquals(200, estado(get("/api/caja/sedes/" + e.sA + "/resumen"), token));
		// Suma Mi caja y Mis grupos.
		assertEquals(200, estado(get("/api/caja/profesor/cargos-pendientes"), token));
		assertEquals(200, estado(get("/api/profesor/grupos"), token));
		assertEquals(200, estado(get("/api/profesor/grupos/" + e.gB + "/alumnos"), token));
		assertEquals(200, estado(get("/api/profesor/alumnos/" + e.aB), token));
		// Ser profesor en la sede B no le abre la gestión de esa sede.
		assertEquals(403, estado(get("/api/alumnos/" + e.aB), token));
		assertEquals(403, estado(get("/api/grupos/" + e.gB), token));
		assertEquals(403, estado(get("/api/caja/sedes/" + e.sB + "/resumen"), token));
		assertEquals(403, estado(get("/api/productos-alumno/alumno/" + e.aB), token));
		assertEquals(403, estado(get("/api/tesoreria/resumen?sedeId=" + e.sB), token));
	}

	@Test
	void responsableYProfesorSinGrupos_tambienSumaMisGrupos() throws Exception {
		Usuario cuenta = cuenta("con-profesor-sin-grupos");
		e.responsable(cuenta.getId(), e.sA);
		membresia(cuenta.getId(), e.org, "PROFESOR");

		JsonNode permisos = leer(get("/api/auth/permisos"), login(cuenta.getEmail()), 200);

		assertFalse(permisos.get("tieneGrupos").asBoolean());
		assertEquals(List.of("MIS_GRUPOS", "MI_CAJA", "PANEL_SEDE"), textos(permisos.get("capacidades")));
	}

	@Test
	void profesorSolo_sigueSiendoSesionSoloProfesor() throws Exception {
		Usuario cuenta = cuenta("profesor");
		membresia(cuenta.getId(), e.org, "PROFESOR");
		asignar(cuenta.getId(), e.gA);

		String token = login(cuenta.getEmail());

		mvc.perform(get("/api/auth/roles").cookie(jwt(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*]", containsInAnyOrder("ROLE_PROFESOR")));
		JsonNode permisos = leer(get("/api/auth/permisos"), token, 200);
		assertEquals(List.of("PROFESOR"), textos(permisos.get("roles")));
		assertEquals(List.of("MIS_GRUPOS", "MI_CAJA"), textos(permisos.get("capacidades")));
		assertEquals(0, permisos.get("sedes").size());
		assertEquals(403, estado(get("/api/alumnos"), token));
		assertEquals(403, estado(get("/api/alumnos/" + e.aA), token));
		assertEquals(403, estado(get("/api/tesoreria/resumen"), token));
		assertEquals(403, estado(get("/api/panel/mi-perfil"), token), "sigue fuera de su lista blanca");
		assertEquals(200, estado(get("/api/caja/profesor/cargos-pendientes"), token));
	}

	// ------------------------------------------------------------------ R + F (371)

	@Test
	void responsableYFamilia_eligeVista_conSusSedesYSuFicha() throws Exception {
		String token = e.token(jwtService, usuarios, e.respA);

		mvc.perform(get("/api/auth/roles").cookie(jwt(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*]", containsInAnyOrder("ROLE_RESPONSABLE_SEDE", "ROLE_USER")));
		mvc.perform(get("/api/auth/user").cookie(jwt(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.alumnoDTO.id").value(e.propioA));
		JsonNode permisos = leer(get("/api/auth/permisos"), token, 200);
		assertEquals(List.of("RESPONSABLE_SEDE", "FAMILIA"), textos(permisos.get("roles")));
		assertEquals(List.of(e.sA), ids(permisos.get("sedes")));
		assertEquals(List.of("PANEL_SEDE", "ELEGIR_VISTA"), textos(permisos.get("capacidades")));
		assertEquals(200, estado(get("/api/escuela/marca"), token));
		assertEquals(200, estado(get("/api/auth/user/alumnos"), token));
		assertEquals(200, estado(get("/api/alumnos/" + e.aA), token));
		assertEquals(403, estado(get("/api/alumnos/" + e.aB), token));
	}

	/** Si R entra solo, perder a su último alumno como familia no le puede cerrar la puerta (decisión propia, 9-5). */
	@Test
	void responsableYFamiliaSinAlumnos_entraComoResponsable() throws Exception {
		Usuario cuenta = cuenta("familia-sin-alumnos");
		e.responsable(cuenta.getId(), e.sA);
		membresia(cuenta.getId(), e.org, "FAMILIA");

		String token = login(cuenta.getEmail());

		assertEquals(e.org, jwtService.verificar(token).organizacionId());
		assertEquals(200, estado(get("/api/alumnos/" + e.aA), token));
		mvc.perform(get("/api/auth/user").cookie(jwt(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.alumnoDTO").doesNotExist());
	}

	@Test
	void familiaYProfesorSinAlumnos_sigueSinEntrar() throws Exception {
		Usuario cuenta = cuenta("familia-profesor-sin-alumnos");
		membresia(cuenta.getId(), e.org, "PROFESOR");
		membresia(cuenta.getId(), e.org, "FAMILIA");

		assertEquals(403, mvc.perform(signin(cuenta.getEmail())).andReturn().getResponse().getStatus());
	}

	// ------------------------------------------------------------------ R + gestión, sin regresión

	@Test
	void responsableYGestion_sigueSiendoGestion_sinPanelDeSede() throws Exception {
		Usuario cuenta = cuenta("con-gestion");
		e.responsable(cuenta.getId(), e.sA);
		membresia(cuenta.getId(), e.org, "ADMINISTRADOR");

		String token = login(cuenta.getEmail());

		JsonNode permisos = leer(get("/api/auth/permisos"), token, 200);
		assertEquals(List.of("ADMINISTRADOR", "RESPONSABLE_SEDE"), textos(permisos.get("roles")));
		assertEquals(List.of(e.sA, e.sB, e.sC), ids(permisos.get("sedes")), "todas: alcance total");
		assertEquals(List.of("MIEMBROS", "SEDES", "ASIGNAR_PROFESORES"), textos(permisos.get("capacidades")));
		assertEquals(200, estado(get("/api/alumnos/" + e.aB), token));
		assertEquals(200, estado(get("/api/sedes"), token));
		assertEquals(200, estado(get("/api/grupos/" + e.gB + "/profesores"), token));
	}

	@Test
	void responsableGestionYFamilia_sinCapacidadesNuevas() throws Exception {
		Usuario cuenta = cuenta("gestion-familia");
		e.responsable(cuenta.getId(), e.sA);
		membresia(cuenta.getId(), e.org, "PROPIETARIO");
		membresia(cuenta.getId(), e.org, "FAMILIA");

		JsonNode permisos = leer(get("/api/auth/permisos"), login(cuenta.getEmail()), 200);

		assertEquals(List.of("MIEMBROS", "DAR_GESTION", "SEDES", "ASIGNAR_PROFESORES"),
				textos(permisos.get("capacidades")));
	}

	// ------------------------------------------------------------------ R sin sedes (decisión 65)

	/** Decisión 65: falla cerrado. Entra (la puerta no mira las sedes) y no ve nada, ni dinero ni fichas. */
	@Test
	void responsableSinSedes_entraSinVerNada() throws Exception {
		Usuario cuenta = cuenta("sin-sedes");
		e.responsable(cuenta.getId());

		String token = login(cuenta.getEmail());

		JsonNode permisos = leer(get("/api/auth/permisos"), token, 200);
		assertEquals(List.of("RESPONSABLE_SEDE"), textos(permisos.get("roles")));
		assertEquals(0, permisos.get("sedes").size());
		assertEquals(List.of("PANEL_SEDE"), textos(permisos.get("capacidades")));
		assertEquals(0, leer(get("/api/alumnos?page=1&size=50"), token, 200).get("totalElements").asInt());
		assertEquals(0, leer(get("/api/grupos/resumen"), token, 200).size());
		assertEquals(0, leer(get("/api/turnos/resumen"), token, 200).size());
		assertEquals(0, leer(get("/api/preinscripciones?size=10"), token, 200).get("totalElements").asInt());
		assertEquals(0, leer(get("/api/tesoreria/movimientos?size=10"), token, 200).get("totalElements").asInt());
		assertEquals(0, leer(get("/api/tesoreria/pendiente-vencido"), token, 200).get("porSede").size());
		assertEquals(0, leer(get("/api/tesoreria/pendientes-por-alumno?alumnoIds=" + e.aA), token, 200).size());
		assertEquals(0, leer(get("/api/grupos/conteo-alumnos"), token, 200).get("Taekwondo").get("total").asInt());
		assertEquals(0, leer(get("/api/grupos/tipo/Taekwondo/alumnos"), token, 200).size());
		assertEquals(403, estado(get("/api/alumnos/" + e.aA), token));
		assertEquals(403, estado(get("/api/grupos/" + e.gA), token));
		assertEquals(403, estado(get("/api/caja/sedes/" + e.sA + "/resumen"), token));
		assertEquals(403, estado(get("/api/tesoreria/resumen?sedeId=" + e.sA), token));
		assertEquals(400, estado(post("/api/grupos").contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"Sin sede IT\",\"deporte\":\"TAEKWONDO\"}"), token), "sin sede que elegir");
		assertEquals(403, estado(post("/api/grupos").contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"Sede ajena IT\",\"deporte\":\"TAEKWONDO\",\"sedeId\":" + e.sA + "}"), token));
	}

	// ------------------------------------------------------------------ utilidades

	private Usuario cuenta(String nombre) {
		String email = "it-95-" + nombre + "-" + e.marca + "@example.test";
		return TenantContext.calcularCon(ORG1, () -> {
			Usuario usuario = new Usuario();
			usuario.setNombre("Usuario");
			usuario.setApellidos("Prueba");
			usuario.setEmail(email);
			usuario.setContrasena(encoder.encode(CLAVE));
			usuario.setAuthProvider(AuthProvider.LOCAL);
			return usuarios.save(usuario);
		});
	}

	private void membresia(long usuario, long organizacion, String rol) {
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, ?)", usuario, organizacion,
				rol);
	}

	private void asignar(long usuario, long grupo) {
		jdbc.update("INSERT INTO asignacion_profesor (organizacion_id, usuario_id, grupo_id, rol) "
				+ "VALUES (?, ?, ?, 'TITULAR')", e.org, usuario, grupo);
	}

	private MockHttpServletRequestBuilder signin(String email) {
		return post("/api/auth/signin").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"contrasena\":\"" + CLAVE + "\"}");
	}

	private String login(String email) throws Exception {
		MvcResult r = mvc.perform(signin(email)).andReturn();
		assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		return json.readTree(r.getResponse().getContentAsString()).get("token").asText();
	}

	private String cambiar(String token, long organizacion) throws Exception {
		MvcResult r = mvc.perform(post("/api/auth/organizacion").contentType(MediaType.APPLICATION_JSON)
				.content("{\"organizacionId\":" + organizacion + "}").cookie(jwt(token))).andReturn();
		assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		return json.readTree(r.getResponse().getContentAsString()).get("token").asText();
	}

	private MockHttpServletResponse loginGoogle(String email) throws Exception {
		OAuth2User usuarioGoogle = mock(OAuth2User.class);
		when(usuarioGoogle.getAttribute("email")).thenReturn(email);
		when(usuarioGoogle.getAttribute("email_verified")).thenReturn(true);
		MockHttpServletResponse respuesta = new MockHttpServletResponse();
		// Como en una petición real: OrganizacionRequestFilter fija la escuela por defecto.
		TenantContext.ejecutarCon(ORG1, () -> {
			try {
				google.onAuthenticationSuccess(new MockHttpServletRequest("GET", "/login/oauth2/code/google"),
						respuesta, new TestingAuthenticationToken(usuarioGoogle, null));
			} catch (Exception ex) {
				throw new IllegalStateException(ex);
			}
		});
		return respuesta;
	}

	private static String cookieJwt(List<String> cabeceras) {
		return cabeceras.stream()
				.filter(c -> c.startsWith("jwt=") && !c.startsWith("jwt=;"))
				.map(c -> c.substring(4, c.indexOf(';')))
				.reduce((a, b) -> b)
				.orElse(null);
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
		return mvc.perform(peticion.cookie(jwt(token))).andReturn();
	}

	private static Cookie jwt(String token) {
		return new Cookie("jwt", token);
	}

	private static List<String> textos(JsonNode lista) {
		List<String> resultado = new ArrayList<>();
		lista.forEach(n -> resultado.add(n.asText()));
		return resultado;
	}

	private static List<Long> ids(JsonNode sedes) {
		List<Long> resultado = new ArrayList<>();
		sedes.forEach(s -> resultado.add(s.get("id").asLong()));
		return resultado;
	}
}
