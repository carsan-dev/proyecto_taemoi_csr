package com.taemoi.project.integracion;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
import com.taemoi.project.services.impl.AuthenticationServiceImpl;

import jakarta.servlet.http.Cookie;

/**
 * Decisión 180 (matiza la D5-1), por HTTP real contra MySQL: una cuenta que SOLO es profesor inicia sesión (con
 * contraseña y con Google) y solo usa su caja ({@code /api/caja/profesor/**}), lo imprescindible de sesión y lo
 * público; todo lo demás, 403. El responsable de sede solo sigue sin entrar con su mensaje. Escuela sintética
 * ({@link EscuelaConSedesDePrueba}); datos inventados.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SesionSoloProfesorIntegrationTest extends MySqlIntegrationTest {

	private static final long ORG1 = 1L;
	private static final String CLAVE = "Clave-Segura-180";

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
	Usuario profesor;
	Usuario profesorSinGrupos;
	long alumnoDeSuGrupo;
	long cargo;
	long producto;

	@BeforeAll
	void preparar() {
		e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-180-profesor");
		alumnoDeSuGrupo = e.alumno("E180PROF" + e.marca, e.sA, true);
		jdbc.update("INSERT INTO alumno_grupo (grupo_id, alumno_id) VALUES (?, ?)", e.gA, alumnoDeSuGrupo);
		// La cuenta está vinculada (usuario.alumno_id) a un alumno activo de la escuela: no se le muestra.
		profesor = cuenta("prof", alumnoDeSuGrupo);
		membresia(profesor, e.org, "PROFESOR");
		jdbc.update("INSERT INTO asignacion_profesor (organizacion_id, usuario_id, grupo_id, rol) "
				+ "VALUES (?, ?, ?, 'TITULAR')", e.org, profesor.getId(), e.gA);
		profesorSinGrupos = cuenta("prof-sin-grupos", null);
		membresia(profesorSinGrupos, e.org, "PROFESOR");

		jdbc.update("""
				INSERT INTO producto_alumno (alumno_id, concepto, precio, cantidad, pagado, fecha_asignacion,
				  organizacion_id, sede_id)
				VALUES (?, 'CARGO 180', 12.0, 1, 0, NOW(), ?, ?)""", alumnoDeSuGrupo, e.org, e.sA);
		cargo = jdbc.queryForObject("SELECT MAX(id) FROM producto_alumno WHERE organizacion_id = ?", Long.class, e.org);
		jdbc.update("INSERT INTO producto (concepto, precio, codigo, organizacion_id, vendible_profesor) "
				+ "VALUES ('CAMISETA 180', 9.0, NULL, ?, TRUE)", e.org);
		producto = jdbc.queryForObject("SELECT MAX(id) FROM producto WHERE organizacion_id = ?", Long.class, e.org);
	}

	@AfterEach
	void sinFugaDeOrganizacion() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
	}

	// ------------------------------------------------------------------ inicio de sesión

	@Test
	void loginConContrasena_abreSesionEnSuEscuela_conSoloRoleProfesor() throws Exception {
		String token = login(profesor.getEmail());

		assertEquals(e.org, jwtService.verificar(token).organizacionId());
		mvc.perform(get("/api/auth/roles").cookie(jwt(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*]", containsInAnyOrder("ROLE_PROFESOR")));
		// Quién es (lo pide el frontend al recargar), sin alumno: nada que lo haga pasar por familia.
		mvc.perform(get("/api/auth/user").cookie(jwt(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(profesor.getEmail()))
				.andExpect(jsonPath("$.alumnoDTO").doesNotExist());
		mvc.perform(get("/api/auth/auth-status").cookie(jwt(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").value(true));
		mvc.perform(post("/api/auth/logout").cookie(jwt(token))).andExpect(status().isOk());
	}

	@Test
	void loginConGoogle_abreSesionEnSuEscuela() throws Exception {
		// Cuenta propia: el login con Google la pasa a proveedor GOOGLE.
		Usuario conGoogle = cuenta("prof-google", null);
		membresia(conGoogle, e.org, "PROFESOR");
		MockHttpServletResponse respuesta = loginGoogle(conGoogle.getEmail());

		String redirigido = respuesta.getRedirectedUrl();
		assertNotNull(redirigido);
		assertTrue(!redirigido.contains("error="), redirigido);
		String token = cookieJwt(respuesta.getHeaders(HttpHeaders.SET_COOKIE));
		assertNotNull(token, "la cookie de sesión");
		assertEquals(e.org, jwtService.verificar(token).organizacionId());
		mvc.perform(get("/api/auth/roles").cookie(jwt(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*]", containsInAnyOrder("ROLE_PROFESOR")));
		mvc.perform(get("/api/caja/profesor/cargos-pendientes").cookie(jwt(token))).andExpect(status().isOk());
	}

	@Test
	void profesorSinGrupos_entraYVeSuCajaVacia() throws Exception {
		String token = login(profesorSinGrupos.getEmail());

		mvc.perform(get("/api/caja/profesor/cargos-pendientes").cookie(jwt(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
		mvc.perform(get("/api/caja/profesor/cobros-pendientes-entrega").cookie(jwt(token)))
				.andExpect(status().isOk());
		mvc.perform(get("/api/alumnos").cookie(jwt(token))).andExpect(status().isForbidden());
	}

	@Test
	void soloResponsable_sigueRechazadoConSuMensaje() throws Exception {
		Usuario responsable = cuenta("solo-resp", null);
		responsableDe(responsable, e.sA);

		for (Usuario cuenta : List.of(responsable)) {
			mvc.perform(post("/api/auth/signin").contentType(MediaType.APPLICATION_JSON)
					.content("{\"email\":\"" + cuenta.getEmail() + "\",\"contrasena\":\"" + CLAVE + "\"}"))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.mensaje").value(AuthenticationServiceImpl.ROL_SIN_ACCESO));

			String redirigido = loginGoogle(cuenta.getEmail()).getRedirectedUrl();
			assertNotNull(redirigido);
			assertTrue(URLDecoder.decode(redirigido, StandardCharsets.UTF_8)
					.endsWith("message=" + AuthenticationServiceImpl.ROL_SIN_ACCESO), redirigido);

			// Un token emitido a mano tampoco abre sesión (puerta en cada petición).
			String emitido = e.token(jwtService, usuarios, cuenta.getId());
			mvc.perform(get("/api/auth/roles").cookie(jwt(emitido))).andExpect(status().isUnauthorized());
			mvc.perform(get("/api/caja/profesor/cargos-pendientes").cookie(jwt(emitido)))
					.andExpect(status().isUnauthorized());
		}
	}

	// ------------------------------------------------------------------ solo su caja

	@Test
	void cajaDelProfesor_responde() throws Exception {
		String token = login(profesor.getEmail());

		JsonNode pendientes = leer(get("/api/caja/profesor/cargos-pendientes"), token, 200);
		Set<Long> ids = new HashSet<>();
		pendientes.forEach(f -> ids.add(f.get("cargoId").asLong()));
		assertTrue(ids.contains(cargo), pendientes.toString());
		assertEquals(200, estado(head("/api/caja/profesor/cargos-pendientes"), token));
		leer(get("/api/caja/profesor/productos"), token, 200);
		leer(get("/api/caja/profesor/cobros-pendientes-entrega"), token, 200);

		long cobro = leer(post("/api/caja/profesor/cargos/" + cargo + "/cobrar"), token, 201).get("cobroId").asLong();
		assertEquals(204, estado(post("/api/caja/profesor/cobros/" + cobro + "/deshacer"), token));
		assertEquals(201, estado(post("/api/caja/profesor/alumnos/" + alumnoDeSuGrupo + "/productos/" + producto
				+ "/cobrar"), token));
		// Su caja sigue con su alcance: un alumno fuera de sus grupos, 403.
		assertEquals(403, estado(post("/api/caja/profesor/alumnos/" + e.aB + "/productos/" + producto + "/cobrar"),
				token));
	}

	/**
	 * Integración 6b-3b: la sesión solo-profesor lista sus alumnos (solo los de sus grupos) y no puede cambiar la marca
	 * «vendible por el profesor» de un producto (eso es de gestión).
	 */
	@Test
	void sesionProfesor_listaSusAlumnos_yNoMarcaProductos() throws Exception {
		String token = login(profesor.getEmail());
		JsonNode lista = leer(get("/api/caja/profesor/alumnos"), token, 200);
		Set<Long> ids = new HashSet<>();
		lista.forEach(f -> ids.add(f.get("alumnoId").asLong()));
		assertTrue(ids.contains(alumnoDeSuGrupo), lista.toString());
		assertTrue(!ids.contains(e.aB), "alumno de un grupo ajeno: " + lista);
		assertEquals(200, estado(head("/api/caja/profesor/alumnos"), token));
		assertEquals(403, estado(put("/api/productos/" + producto).contentType(MediaType.APPLICATION_JSON)
				.content("{\"concepto\":\"CAMISETA 180\",\"precio\":9.0,\"vendibleProfesor\":false}"), token));
		assertEquals(Boolean.TRUE, jdbc.queryForObject("SELECT vendible_profesor FROM producto WHERE id = ?",
				Boolean.class, producto));
	}

	/**
	 * Decisión 184: responsable de sede (de la sede A) + profesor entra como profesor, con contraseña y con Google: la
	 * misma sesión limitada. Su parte de responsable no abre nada (ni lo que tendría por su sede) y /roles solo dice
	 * ROLE_PROFESOR, para que el frontend la lleve a «Mi caja».
	 */
	@Test
	void responsableYProfesor_entraComoProfesor_soloASuCaja() throws Exception {
		Usuario ambos = cuenta("resp-y-prof", null);
		responsableDe(ambos, e.sA);
		membresia(ambos, e.org, "PROFESOR");
		jdbc.update("INSERT INTO asignacion_profesor (organizacion_id, usuario_id, grupo_id, rol) "
				+ "VALUES (?, ?, ?, 'TITULAR')", e.org, ambos.getId(), e.gA);

		String token = login(ambos.getEmail());
		assertEquals(e.org, jwtService.verificar(token).organizacionId());
		mvc.perform(get("/api/auth/roles").cookie(jwt(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*]", containsInAnyOrder("ROLE_PROFESOR")));
		mvc.perform(get("/api/auth/user").cookie(jwt(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.alumnoDTO").doesNotExist());
		JsonNode pendientes = leer(get("/api/caja/profesor/cargos-pendientes"), token, 200);
		Set<Long> ids = new HashSet<>();
		pendientes.forEach(f -> ids.add(f.get("cargoId").asLong()));
		assertTrue(ids.contains(cargo), pendientes.toString());
		leer(get("/api/caja/profesor/productos"), token, 200);
		leer(get("/api/caja/profesor/cobros-pendientes-entrega"), token, 200);
		soloSuCajaSesionYLoPublico(token);

		Usuario ambosGoogle = cuenta("resp-y-prof-google", null);
		responsableDe(ambosGoogle, e.sA);
		membresia(ambosGoogle, e.org, "PROFESOR");
		MockHttpServletResponse respuesta = loginGoogle(ambosGoogle.getEmail());
		assertTrue(!String.valueOf(respuesta.getRedirectedUrl()).contains("error="), respuesta.getRedirectedUrl());
		String deGoogle = cookieJwt(respuesta.getHeaders(HttpHeaders.SET_COOKIE));
		assertNotNull(deGoogle, "la cookie de sesión");
		assertEquals(e.org, jwtService.verificar(deGoogle).organizacionId());
		mvc.perform(get("/api/auth/roles").cookie(jwt(deGoogle)))
				.andExpect(jsonPath("$[*]", containsInAnyOrder("ROLE_PROFESOR")));
		assertEquals(200, estado(get("/api/caja/profesor/cargos-pendientes"), deGoogle));
		assertEquals(403, estado(get("/api/alumnos"), deGoogle));
	}

	@Test
	void todoLoDemas_403AunqueLaRutaSoloPidieraSesion() throws Exception {
		soloSuCajaSesionYLoPublico(login(profesor.getEmail()));
	}

	/** 403 en una muestra amplia (también lo que un responsable de la sede A tendría); lo público, 200. */
	private void soloSuCajaSesionYLoPublico(String token) throws Exception {
		List<MockHttpServletRequestBuilder> rutas = List.of(
				get("/api/alumnos/" + e.aA), get("/api/alumnos/" + e.aA + "/salud"), get("/api/grupos/resumen"),
				get("/api/turnos/" + e.tA), put("/api/grupos/" + e.gA).contentType(MediaType.APPLICATION_JSON)
						.content("{}"),
				get("/api/tesoreria/anios-disponibles"), get("/api/productos-alumno/alumno/" + e.aA),
				get("/api/preinscripciones/1"), get("/api/convocatorias/1"),
				get("/api/informes/listado-mensualidad-mensual"), get("/api/caja/sedes/" + e.sA + "/pendiente-entrega"),
				get("/api/alumnos"), get("/api/alumnos/" + alumnoDeSuGrupo), get("/api/alumnos/count"),
				post("/api/alumnos/crear"), get("/api/alumnos/" + alumnoDeSuGrupo + "/grupos"),
				get("/api/grupos"), get("/api/grupos/" + e.gA), get("/api/turnos"),
				get("/api/tesoreria/resumen"), get("/api/tesoreria/movimientos"),
				get("/api/productos"), get("/api/productos-alumno/alumno/" + alumnoDeSuGrupo),
				put("/api/productos-alumno/" + cargo).contentType(MediaType.APPLICATION_JSON).content("{}"),
				get("/api/auth/user/alumnos"), get("/api/auth/user/recordatorios-racha-email"),
				put("/api/auth/user/recordatorios-racha-email").contentType(MediaType.APPLICATION_JSON)
						.content("{\"habilitado\":true}"),
				get("/api/admin/configuracion/limite-turno"), get("/api/admin/auditoria/eventos"),
				get("/api/admin/users"), get("/api/informes/deudas"), get("/api/informes/asistencia"),
				get("/api/convocatorias"), get("/api/preinscripciones"), get("/api/catalogo"),
				get("/api/tarifas-sede"), get("/api/caja/sedes/" + e.sA + "/resumen"),
				get("/api/caja/sedes/" + e.sA + "/entregas"), get("/api/plataforma/errores"),
				get("/api/pagos"), delete("/api/grupos/" + e.gA), get("/imagenes/alumnos/1.jpg"),
				get("/documentos/Documentos_Alumnos/1.pdf"), get("/api/ruta-que-no-existe"));
		assertEquals(44, rutas.size(), "muestra amplia");
		for (MockHttpServletRequestBuilder ruta : rutas) {
			MvcResult r = peticion(ruta, token);
			assertEquals(403, r.getResponse().getStatus(),
					r.getRequest().getMethod() + " " + r.getRequest().getRequestURI());
		}
		// Lo público sigue funcionando.
		assertEquals(200, estado(get("/api/eventos"), token));
		assertEquals(200, estado(get("/api/turnos/limite"), token));
	}

	// ------------------------------------------------------------------ cambio de escuela

	@Test
	void cambioDeEscuela_entreGestionYSuCajaDeProfesor() throws Exception {
		Usuario mixta = cuenta("gestor-org1-y-prof", null);
		membresia(mixta, ORG1, "ADMINISTRADOR");
		membresia(mixta, e.org, "PROFESOR");
		Usuario soloResp = cuenta("gestor-org1-y-resp", null);
		membresia(soloResp, ORG1, "ADMINISTRADOR");
		membresia(soloResp, e.org, "RESPONSABLE_SEDE");

		String token = login(mixta.getEmail());
		assertEquals(ORG1, jwtService.verificar(token).organizacionId(), "la de menor id entre las válidas");
		mvc.perform(get("/api/auth/organizaciones").cookie(jwt(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id", containsInAnyOrder((int) ORG1, (int) e.org)));

		String deProfesor = cambiar(token, e.org, 200);
		mvc.perform(get("/api/auth/roles").cookie(jwt(deProfesor)))
				.andExpect(jsonPath("$[*]", containsInAnyOrder("ROLE_PROFESOR")));
		assertEquals(403, estado(get("/api/alumnos"), deProfesor));
		assertEquals(200, estado(get("/api/caja/profesor/cargos-pendientes"), deProfesor));
		assertEquals(200, estado(get("/api/auth/organizaciones"), deProfesor));

		String deGestion = cambiar(deProfesor, ORG1, 200);
		mvc.perform(get("/api/auth/roles").cookie(jwt(deGestion)))
				.andExpect(jsonPath("$[*]", containsInAnyOrder("ROLE_MANAGER")));
		assertEquals(200, estado(get("/api/alumnos/count"), deGestion));

		// A la escuela donde solo es responsable de sede no se cambia, y no aparece en el selector.
		String deResp = login(soloResp.getEmail());
		cambiar(deResp, e.org, 403);
		mvc.perform(get("/api/auth/organizaciones").cookie(jwt(deResp)))
				.andExpect(jsonPath("$[*].id", containsInAnyOrder((int) ORG1)));
	}

	// ---------------------------------------------------------------------------------

	private String cambiar(String token, long organizacion, int esperado) throws Exception {
		MvcResult r = mvc.perform(post("/api/auth/organizacion").cookie(jwt(token))
				.contentType(MediaType.APPLICATION_JSON).content("{\"organizacionId\":" + organizacion + "}"))
				.andReturn();
		assertEquals(esperado, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		return esperado == 200 ? json.readTree(r.getResponse().getContentAsString()).get("token").asText() : null;
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

	private String login(String email) throws Exception {
		MvcResult r = mvc.perform(post("/api/auth/signin").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"contrasena\":\"" + CLAVE + "\"}"))
				.andExpect(status().isOk())
				.andReturn();
		return json.readTree(r.getResponse().getContentAsString()).get("token").asText();
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

	private Usuario cuenta(String nombre, Long alumnoId) {
		String email = "it-180-" + nombre + "-" + e.marca + "@example.test";
		Usuario guardado = TenantContext.calcularCon(ORG1, () -> {
			Usuario usuario = new Usuario();
			usuario.setNombre("Usuario");
			usuario.setApellidos("Prueba");
			usuario.setEmail(email);
			usuario.setContrasena(encoder.encode(CLAVE));
			usuario.setAuthProvider(AuthProvider.LOCAL);
			return usuarios.save(usuario);
		});
		if (alumnoId != null) {
			jdbc.update("UPDATE usuario SET alumno_id = ? WHERE id = ?", alumnoId, guardado.getId());
		}
		return guardado;
	}

	/** Membresía RESPONSABLE_SEDE con sus sedes (alcance de responsable si entrara como tal). */
	private void responsableDe(Usuario usuario, long... sedes) {
		membresia(usuario, e.org, "RESPONSABLE_SEDE");
		long membresia = jdbc.queryForObject("SELECT id FROM membresia WHERE usuario_id = ? AND organizacion_id = ? "
				+ "AND rol = 'RESPONSABLE_SEDE'", Long.class, usuario.getId(), e.org);
		for (long sede : sedes) {
			jdbc.update("INSERT INTO membresia_sede (membresia_id, sede_id) VALUES (?, ?)", membresia, sede);
		}
	}

	private void membresia(Usuario usuario, long organizacion, String rol) {
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, ?)", usuario.getId(),
				organizacion, rol);
	}
}
