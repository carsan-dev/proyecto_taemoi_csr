package com.taemoi.project.integracion;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mail.javamail.JavaMailSender;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.AuthProvider;
import com.taemoi.project.entities.Evento;
import com.taemoi.project.entities.Membresia;
import com.taemoi.project.entities.Organizacion;
import com.taemoi.project.entities.RegistroPendiente;
import com.taemoi.project.entities.RolMembresia;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.EventoRepository;
import com.taemoi.project.repositories.MembresiaRepository;
import com.taemoi.project.repositories.OrganizacionRepository;
import com.taemoi.project.repositories.RegistroPendienteRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.dtos.AlumnoDTO;
import com.taemoi.project.exceptions.usuario.UsuarioCompartidoException;
import com.taemoi.project.services.AlumnoService;
import org.springframework.transaction.support.TransactionTemplate;
import com.taemoi.project.services.ContadorService;
import com.taemoi.project.services.JwtService;
import com.taemoi.project.services.OAuth2UserService;
import com.taemoi.project.services.RegistroService;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;

/**
 * Permisos por organización y escuela activa en el JWT, por HTTP real (MockMvc con la cadena de
 * filtros completa: OrganizacionRequestFilter + Spring Security) contra MySQL.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SesionPorOrganizacionIntegrationTest extends MySqlIntegrationTest {

	private static final long ORG1 = 1L;
	private static final String CLAVE = "Clave-Segura-123";

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired PasswordEncoder encoder;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired MembresiaRepository membresias;
	@Autowired OrganizacionRepository organizaciones;
	@Autowired AlumnoRepository alumnos;
	@Autowired EventoRepository eventos;
	@Autowired RegistroPendienteRepository registrosPendientes;
	@Autowired ContadorService contadores;
	@Autowired RegistroService registroService;
	@Autowired OAuth2UserService oauth2UserService;
	@Autowired AlumnoService alumnoService;
	@Autowired TransactionTemplate tx;

	@MockitoBean JavaMailSender mailSender;

	@Value("${app.bootstrap.admin.email}") String bootstrapEmail;
	@Value("${app.bootstrap.admin.password}") String bootstrapClave;

	final String sufijo = UUID.randomUUID().toString().substring(0, 8);
	long org2;
	long alumnoOrg1;
	long alumnoOrg2;
	Usuario adminOrg1;
	Usuario adminOrg2;
	Usuario multi;
	String eventoOrg1;
	String eventoOrg2;

	@BeforeAll
	void crearEscuelasYUsuarios() {
		org2 = TenantContext.calcularCon(ORG1, () -> {
			Organizacion org = new Organizacion();
			org.setSlug("it-sesion-" + sufijo);
			org.setNombre("Escuela sesión " + sufijo);
			return organizaciones.save(org).getId();
		});
		assertNotEquals(ORG1, org2);

		String emailMulti = "multi-" + sufijo + "@example.test";
		alumnoOrg1 = TenantContext.calcularCon(ORG1, () -> crearAlumno("ORG1 " + sufijo, emailMulti));
		alumnoOrg2 = TenantContext.calcularCon(org2, () -> crearAlumno("ORG2 " + sufijo, "otro-" + sufijo + "@example.test"));
		eventoOrg1 = "Evento org1 " + sufijo;
		eventoOrg2 = "Evento org2 " + sufijo;
		TenantContext.ejecutarCon(ORG1, () -> crearEvento(eventoOrg1));
		TenantContext.ejecutarCon(org2, () -> crearEvento(eventoOrg2));

		// El admin de la escuela 1 está vinculado (usuario.alumno_id) a un alumno de la escuela 2.
		adminOrg1 = crearUsuario("admin1-" + sufijo + "@example.test", alumnoOrg2, org2);
		dar(adminOrg1, ORG1, RolMembresia.PROPIETARIO);
		adminOrg2 = crearUsuario("admin2-" + sufijo + "@example.test", null, null);
		dar(adminOrg2, org2, RolMembresia.PROPIETARIO);
		// Familia en la escuela 1 (con alumno activo con su email) y propietario en la 2.
		multi = crearUsuario(emailMulti, null, null);
		dar(multi, ORG1, RolMembresia.FAMILIA);
		dar(multi, org2, RolMembresia.PROPIETARIO);
	}

	@AfterEach
	void sinFugaDeOrganizacion() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
	}

	// ------------------------------------------------------------------ una escuela

	@Test
	void adminDeLaEscuela1VeSusDatosYNoLosDeLaOtra() throws Exception {
		String token = login(adminOrg1.getEmail());
		assertEquals(ORG1, jwtService.verificar(token).organizacionId());

		mvc.perform(get("/api/auth/roles").cookie(jwt(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*]", containsInAnyOrder("ROLE_ADMIN")));
		mvc.perform(get("/api/admin/" + alumnoOrg1).cookie(jwt(token))).andExpect(status().isOk());
		mvc.perform(get("/api/admin/" + alumnoOrg2).cookie(jwt(token))).andExpect(status().isNotFound());
	}

	@Test
	void sinMembresiaEnLaEscuela2NoSePuedeObtenerTokenDeEsaEscuela() throws Exception {
		String token = login(adminOrg1.getEmail());

		MvcResult cambio = mvc.perform(post("/api/auth/organizacion").cookie(jwt(token))
				.contentType(MediaType.APPLICATION_JSON).content("{\"organizacionId\":" + org2 + "}"))
				.andExpect(status().isForbidden())
				.andReturn();
		assertTrue(cambio.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream().noneMatch(c -> c.startsWith("jwt=e")),
				"No se emite ninguna cookie de sesión");

		mvc.perform(get("/api/auth/organizaciones").cookie(jwt(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].id").value(ORG1))
				.andExpect(jsonPath("$[0].activa").value(true));
	}

	@Test
	void unTokenConOtraEscuelaNoSePuedeFalsificarSinLaClave() throws Exception {
		String genuino = login(adminOrg1.getEmail());
		mvc.perform(get("/api/auth/roles").cookie(jwt(genuino))).andExpect(status().isOk());

		// 1) Firmado con otra clave.
		String otraClave = Jwts.builder().subject(adminOrg1.getEmail()).claim(JwtService.CLAIM_ORGANIZACION, org2)
				.issuedAt(new java.util.Date())
				.expiration(new java.util.Date(System.currentTimeMillis() + 600_000))
				.signWith(Keys.hmacShaKeyFor("otra-clave-de-32-bytes-como-minimo!!".getBytes(StandardCharsets.UTF_8)))
				.compact();
		mvc.perform(get("/api/auth/roles").cookie(jwt(otraClave))).andExpect(status().isUnauthorized());

		// 2) Token genuino con el claim org cambiado y la firma original.
		String[] partes = genuino.split("\\.");
		String payload = new String(Base64.getUrlDecoder().decode(partes[1]), StandardCharsets.UTF_8);
		assertTrue(payload.contains("\"org\":" + ORG1), payload);
		String manipulado = partes[0] + "."
				+ Base64.getUrlEncoder().withoutPadding().encodeToString(
						payload.replace("\"org\":" + ORG1, "\"org\":" + org2).getBytes(StandardCharsets.UTF_8))
				+ "." + partes[2];
		mvc.perform(get("/api/auth/roles").cookie(jwt(manipulado))).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/admin/" + alumnoOrg2).cookie(jwt(manipulado))).andExpect(status().isUnauthorized());
	}

	@Test
	void adminSoloDeLaEscuela2NoLeeDatosNiUsuariosDeLaEscuela1() throws Exception {
		String token = login(adminOrg2.getEmail());
		assertEquals(org2, jwtService.verificar(token).organizacionId());

		mvc.perform(get("/api/admin/" + alumnoOrg2).cookie(jwt(token))).andExpect(status().isOk());
		mvc.perform(get("/api/admin/" + alumnoOrg1).cookie(jwt(token))).andExpect(status().isNotFound());
		mvc.perform(get("/api/admin/users").cookie(jwt(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].email", org.hamcrest.Matchers.hasItem(adminOrg2.getEmail())))
				.andExpect(jsonPath("$[*].email", not(org.hamcrest.Matchers.hasItem(adminOrg1.getEmail()))));

		// Tampoco puede cambiar roles de un usuario que no es de su escuela: 404 con cuerpo estándar.
		mvc.perform(put("/api/admin/users/" + adminOrg1.getId() + "/roles")
				.cookie(jwt(token)).contentType(MediaType.APPLICATION_JSON).content("[\"ROLE_ADMIN\"]"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.mensaje").exists());
		assertTrue(TenantContext.calcularCon(ORG1,
				() -> membresias.findByUsuarioIdAndOrganizacionId(adminOrg1.getId(), org2)).isEmpty());
	}

	// ------------------------------------------------------------------ varias escuelas

	@Test
	void usuarioConDosEscuelasEntraSegunLaReglaCambiaYVeOtrosDatosYPermisos() throws Exception {
		jdbc.update("UPDATE usuario SET ultima_organizacion_id = NULL WHERE id = ?", multi.getId());

		// Sin última escuela: la de menor id (la 1), donde es familia.
		String tokenOrg1 = login(multi.getEmail());
		assertEquals(ORG1, jwtService.verificar(tokenOrg1).organizacionId());
		mvc.perform(get("/api/auth/roles").cookie(jwt(tokenOrg1)))
				.andExpect(jsonPath("$[*]", containsInAnyOrder("ROLE_USER")));
		mvc.perform(get("/api/admin/" + alumnoOrg2).cookie(jwt(tokenOrg1))).andExpect(status().isForbidden());
		mvc.perform(get("/api/auth/user").cookie(jwt(tokenOrg1)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.alumnoDTO.id").value(alumnoOrg1));
		mvc.perform(get("/api/auth/organizaciones").cookie(jwt(tokenOrg1)))
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[0].id").value(ORG1))
				.andExpect(jsonPath("$[0].roles[0]").value("ROLE_USER"))
				.andExpect(jsonPath("$[0].activa").value(true))
				.andExpect(jsonPath("$[1].id").value(org2))
				.andExpect(jsonPath("$[1].roles[0]").value("ROLE_ADMIN"))
				.andExpect(jsonPath("$[1].activa").value(false));

		// Cambio a la escuela 2: nuevo token en la cookie, con la misma caducidad.
		MvcResult cambio = mvc.perform(post("/api/auth/organizacion").cookie(jwt(tokenOrg1))
				.contentType(MediaType.APPLICATION_JSON).content("{\"organizacionId\":" + org2 + "}"))
				.andExpect(status().isOk())
				.andReturn();
		String tokenOrg2 = cookieJwt(cambio);
		assertEquals(tokenOrg2, json.readTree(cambio.getResponse().getContentAsString()).get("token").asText());
		assertEquals(org2, jwtService.verificar(tokenOrg2).organizacionId());
		assertFalse(jwtService.verificar(tokenOrg2).expiraEn().after(jwtService.verificar(tokenOrg1).expiraEn()),
				"El cambio no alarga la sesión");
		assertEquals(org2, jdbc.queryForObject("SELECT ultima_organizacion_id FROM usuario WHERE id = ?", Long.class,
				multi.getId()));

		mvc.perform(get("/api/auth/roles").cookie(jwt(tokenOrg2)))
				.andExpect(jsonPath("$[*]", containsInAnyOrder("ROLE_ADMIN")));
		mvc.perform(get("/api/admin/" + alumnoOrg2).cookie(jwt(tokenOrg2))).andExpect(status().isOk());
		mvc.perform(get("/api/admin/" + alumnoOrg1).cookie(jwt(tokenOrg2))).andExpect(status().isNotFound());

		// El token anterior sigue siendo de la escuela 1 (no gana permisos de la 2).
		mvc.perform(get("/api/admin/" + alumnoOrg2).cookie(jwt(tokenOrg1))).andExpect(status().isForbidden());

		// Siguiente login: la última escuela usada, si sigue siendo válida.
		assertEquals(org2, jwtService.verificar(login(multi.getEmail())).organizacionId());
	}

	@Test
	void familiaSinAlumnoActivoEnUnaEscuelaEntraEnLaOtra() throws Exception {
		Usuario usuario = crearUsuario("familia-sin-alumno-" + sufijo + "@example.test", null, null);
		dar(usuario, ORG1, RolMembresia.FAMILIA); // sin alumnos con su email en la escuela 1
		dar(usuario, org2, RolMembresia.ADMINISTRADOR);

		String token = login(usuario.getEmail());

		assertEquals(org2, jwtService.verificar(token).organizacionId());
		mvc.perform(get("/api/auth/roles").cookie(jwt(token)))
				.andExpect(jsonPath("$[*]", containsInAnyOrder("ROLE_MANAGER")));
		mvc.perform(post("/api/auth/organizacion").cookie(jwt(token))
				.contentType(MediaType.APPLICATION_JSON).content("{\"organizacionId\":" + ORG1 + "}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void sinNingunaMembresiaActivaNoSePuedeIniciarSesion() throws Exception {
		Usuario usuario = crearUsuario("sin-escuela-" + sufijo + "@example.test", null, null);

		mvc.perform(post("/api/auth/signin").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + usuario.getEmail() + "\",\"contrasena\":\"" + CLAVE + "\"}"))
				.andExpect(status().isForbidden());
	}

	// ------------------------------------------------------------------ tokens heredados

	@Test
	void tokenHeredadoSinOrgSoloValeSiElUsuarioTieneUnaSolaEscuela() throws Exception {
		String heredadoUnaEscuela = jwtService.generateToken(adminOrg1, 600_000);
		assertNull(jwtService.verificar(heredadoUnaEscuela).organizacionId());
		mvc.perform(get("/api/auth/roles").cookie(jwt(heredadoUnaEscuela)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*]", containsInAnyOrder("ROLE_ADMIN")));
		mvc.perform(get("/api/admin/" + alumnoOrg1).cookie(jwt(heredadoUnaEscuela))).andExpect(status().isOk());

		String heredadoVariasEscuelas = jwtService.generateToken(multi, 600_000);
		mvc.perform(get("/api/auth/roles").cookie(jwt(heredadoVariasEscuelas))).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/admin/" + alumnoOrg2).cookie(jwt(heredadoVariasEscuelas)))
				.andExpect(status().isUnauthorized());
	}

	// ------------------------------------------------------------------ membresía revocada

	@Test
	void membresiaDesactivadaRechazaLaSiguientePeticion() throws Exception {
		Usuario usuario = crearUsuario("revocado-" + sufijo + "@example.test", null, null);
		Membresia membresia = dar(usuario, ORG1, RolMembresia.ADMINISTRADOR);
		String token = login(usuario.getEmail());
		mvc.perform(get("/api/auth/roles").cookie(jwt(token))).andExpect(status().isOk());

		jdbc.update("UPDATE membresia SET activa = FALSE WHERE id = ?", membresia.getId());

		mvc.perform(get("/api/auth/roles").cookie(jwt(token))).andExpect(status().isUnauthorized());
	}

	@Test
	void editarRolesDesdeAdminEscribeMembresiasDeLaEscuelaActivaYNoUsuarioRol() throws Exception {
		Usuario usuario = crearUsuario("editado-" + sufijo + "@example.test", null, null);
		dar(usuario, ORG1, RolMembresia.PROPIETARIO);
		String tokenUsuario = login(usuario.getEmail());
		String tokenAdmin = login(adminOrg1.getEmail());

		mvc.perform(put("/api/admin/users/" + usuario.getId() + "/roles").cookie(jwt(tokenAdmin))
				.contentType(MediaType.APPLICATION_JSON).content("[\"ROLE_MANAGER\"]"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.rol").value("[ROLE_MANAGER]"));

		assertEquals(List.of("ADMINISTRADOR"), jdbc.queryForList(
				"SELECT rol FROM membresia WHERE usuario_id = ? AND organizacion_id = 1 AND activa = TRUE",
				String.class, usuario.getId()));
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM usuario_rol WHERE usuario_id = ?", Integer.class,
				usuario.getId()));
		// La sesión ya abierta pasa a tener los permisos nuevos en la siguiente petición.
		mvc.perform(get("/api/auth/roles").cookie(jwt(tokenUsuario)))
				.andExpect(jsonPath("$[*]", containsInAnyOrder("ROLE_MANAGER")));

		// Sin roles: deja de tener acceso a la escuela.
		mvc.perform(put("/api/admin/users/" + usuario.getId() + "/roles").cookie(jwt(tokenAdmin))
				.contentType(MediaType.APPLICATION_JSON).content("[]"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.rol").value("[]"));
		mvc.perform(get("/api/auth/roles").cookie(jwt(tokenUsuario))).andExpect(status().isUnauthorized());
	}

	// ------------------------------------------------------------------ anónimo y altas

	@Test
	void endpointPublicoSinTokenFuncionaEnLaEscuelaPorDefecto() throws Exception {
		mvc.perform(get("/api/eventos"))
				.andExpect(status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
						.string(containsString(eventoOrg1)))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
						.string(not(containsString(eventoOrg2))));

		// Con un JWT inválido sigue siendo público (petición anónima).
		mvc.perform(get("/api/eventos").cookie(jwt("no-es-un-jwt"))).andExpect(status().isOk());
	}

	@Test
	void adminDeArranqueEsPropietarioDeLaEscuelaPorDefecto() throws Exception {
		Long id = jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", Long.class, bootstrapEmail);
		assertEquals(List.of("PROPIETARIO"), jdbc.queryForList(
				"SELECT rol FROM membresia WHERE usuario_id = ? AND organizacion_id = 1 AND activa = TRUE",
				String.class, id));

		MvcResult resultado = mvc.perform(post("/api/auth/signin").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + bootstrapEmail + "\",\"contrasena\":\"" + bootstrapClave + "\"}"))
				.andExpect(status().isOk())
				.andReturn();
		String token = json.readTree(resultado.getResponse().getContentAsString()).get("token").asText();
		mvc.perform(get("/api/auth/roles").cookie(jwt(token)))
				.andExpect(jsonPath("$[*]", containsInAnyOrder("ROLE_ADMIN")));
	}

	@Test
	void registroCreaFamiliaEnLaEscuelaDelAlumno() throws Exception {
		String email = "registro-" + sufijo + "@example.test";
		long alumnoId = TenantContext.calcularCon(org2, () -> crearAlumno("REGISTRO " + sufijo, email));
		String token = "token-registro-" + sufijo;
		TenantContext.ejecutarCon(org2, () -> {
			RegistroPendiente pendiente = new RegistroPendiente();
			pendiente.setEmail(email);
			pendiente.setAlumnoId(alumnoId);
			pendiente.setPasswordHash(encoder.encode(CLAVE));
			pendiente.setTokenHash(sha256(token));
			pendiente.setTokenExpiresAt(LocalDateTime.now().plusHours(1));
			registrosPendientes.save(pendiente);
			registroService.confirmarRegistro(token);
		});

		Long usuarioId = jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", Long.class, email);
		assertEquals(List.of("FAMILIA"), jdbc.queryForList(
				"SELECT rol FROM membresia WHERE usuario_id = ? AND organizacion_id = ? AND activa = TRUE",
				String.class, usuarioId, org2));
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM usuario_rol WHERE usuario_id = ?", Integer.class,
				usuarioId));
		assertEquals(org2, jwtService.verificar(login(email)).organizacionId());
	}

	@Test
	void primerLoginConGoogleCreaFamiliaEnLaEscuelaDelAlumno() {
		String email = "google-" + sufijo + "@example.test";
		TenantContext.ejecutarCon(org2, () -> crearAlumno("GOOGLE " + sufijo, email));
		OAuth2User google = mock(OAuth2User.class);
		when(google.getAttribute("email")).thenReturn(email);

		Usuario usuario = TenantContext.calcularCon(org2, () -> oauth2UserService.processOAuth2Login(google, "google"));

		assertEquals(List.of("FAMILIA"), jdbc.queryForList(
				"SELECT rol FROM membresia WHERE usuario_id = ? AND organizacion_id = ? AND activa = TRUE",
				String.class, usuario.getId(), org2));
		assertEquals(AuthProvider.GOOGLE, usuario.getAuthProvider());
	}

	// ------------------------------------------------------------------ usuario ↔ alumno

	@Test
	void usuarioVinculadoAUnAlumnoDeOtraEscuelaSeCargaSinErrores() throws Exception {
		Usuario cargado = TenantContext.calcularCon(ORG1,
				() -> usuarios.findByEmailIgnoreCase(adminOrg1.getEmail()).orElseThrow());
		assertEquals(alumnoOrg2, cargado.getAlumnoId(), "El id se lee sin cargar el alumno de la otra escuela");

		String token = login(adminOrg1.getEmail());
		mvc.perform(get("/api/auth/user").cookie(jwt(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(adminOrg1.getEmail()))
				.andExpect(jsonPath("$.alumnoDTO").doesNotExist());
	}

	// ------------------------------------------------------------------ cuenta global (revisión)

	@Test
	void contrasenaSoloSeCambiaSiTodasLasMembresiasSonDeLaEscuelaActiva() throws Exception {
		String tokenAdmin = login(adminOrg1.getEmail());
		Usuario soloEscuela1 = crearUsuario("solo1-" + sufijo + "@example.test", null, null);
		dar(soloEscuela1, ORG1, RolMembresia.ADMINISTRADOR);
		// Membresía INACTIVA en la escuela 1 y propietario en la 2: la cuenta no es solo de la escuela 1.
		Usuario compartido = crearUsuario("compartido-" + sufijo + "@example.test", null, null);
		Membresia inactiva = dar(compartido, ORG1, RolMembresia.FAMILIA);
		jdbc.update("UPDATE membresia SET activa = FALSE WHERE id = ?", inactiva.getId());
		dar(compartido, org2, RolMembresia.PROPIETARIO);

		mvc.perform(put("/api/admin/users/" + compartido.getId() + "/password").cookie(jwt(tokenAdmin))
				.contentType(MediaType.APPLICATION_JSON).content("{\"nuevaContrasena\":\"Nueva-Clave-456\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.mensaje").value(containsString("otra escuela")));
		login(compartido.getEmail()); // la contraseña no ha cambiado

		mvc.perform(put("/api/admin/users/" + soloEscuela1.getId() + "/password").cookie(jwt(tokenAdmin))
				.contentType(MediaType.APPLICATION_JSON).content("{\"nuevaContrasena\":\"Nueva-Clave-456\"}"))
				.andExpect(status().isOk());
		mvc.perform(post("/api/auth/signin").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + soloEscuela1.getEmail() + "\",\"contrasena\":\"Nueva-Clave-456\"}"))
				.andExpect(status().isOk());

		// Usuario de otra escuela: 404 con el cuerpo de error estándar.
		mvc.perform(put("/api/admin/users/" + adminOrg2.getId() + "/password").cookie(jwt(tokenAdmin))
				.contentType(MediaType.APPLICATION_JSON).content("{\"nuevaContrasena\":\"Nueva-Clave-456\"}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.mensaje").exists());
	}

	@Test
	void editarRolesSoloTocaLasMembresiasDeLaEscuelaActiva() throws Exception {
		Usuario usuario = crearUsuario("roles-dos-" + sufijo + "@example.test", null, null);
		dar(usuario, ORG1, RolMembresia.ADMINISTRADOR);
		dar(usuario, org2, RolMembresia.PROPIETARIO);

		mvc.perform(put("/api/admin/users/" + usuario.getId() + "/roles").cookie(jwt(login(adminOrg1.getEmail())))
				.contentType(MediaType.APPLICATION_JSON).content("[]"))
				.andExpect(status().isOk());

		assertEquals(List.of("PROPIETARIO"), jdbc.queryForList(
				"SELECT rol FROM membresia WHERE usuario_id = ? AND organizacion_id = ? AND activa = TRUE",
				String.class, usuario.getId(), org2));
		assertEquals(0, jdbc.queryForObject(
				"SELECT COUNT(*) FROM membresia WHERE usuario_id = ? AND organizacion_id = 1 AND activa = TRUE",
				Integer.class, usuario.getId()));
	}

	@Test
	void usuarioSinNingunaEscuelaRecibeUnMensajeClaro() throws Exception {
		Usuario usuario = crearUsuario("sin-acceso-" + sufijo + "@example.test", null, null);

		mvc.perform(post("/api/auth/signin").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + usuario.getEmail() + "\",\"contrasena\":\"" + CLAVE + "\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.mensaje").value("El usuario no tiene acceso a ninguna escuela."));

		// Con membresía de familia pero sin alumnos activos se mantiene el mensaje de siempre.
		dar(usuario, ORG1, RolMembresia.FAMILIA);
		mvc.perform(post("/api/auth/signin").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + usuario.getEmail() + "\",\"contrasena\":\"" + CLAVE + "\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.mensaje").value("No hay alumnos activos asociados a esta cuenta."));
	}

	@Test
	void borrarAlumnoDeUnaFamiliaDeUnaSolaEscuelaBorraLaCuentaComoAntes() {
		String email = "borrar-solo-" + sufijo + "@example.test";
		long alumnoId = TenantContext.calcularCon(ORG1, () -> crearAlumno("BORRAR SOLO " + sufijo, email));
		Usuario usuario = crearUsuario(email, alumnoId, ORG1);
		dar(usuario, ORG1, RolMembresia.FAMILIA);

		assertTrue(TenantContext.calcularCon(ORG1, () -> alumnoService.eliminarAlumno(alumnoId)));

		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM usuario WHERE id = ?", Integer.class, usuario.getId()));
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM membresia WHERE usuario_id = ?", Integer.class,
				usuario.getId()));
	}

	@Test
	void borrarAlumnoNoBorraLaCuentaSiTieneMembresiasEnOtraEscuela() throws Exception {
		String email = "borrar-compartido-" + sufijo + "@example.test";
		long alumnoId = TenantContext.calcularCon(ORG1, () -> crearAlumno("BORRAR COMPARTIDO " + sufijo, email));
		Usuario usuario = crearUsuario(email, alumnoId, ORG1);
		dar(usuario, ORG1, RolMembresia.FAMILIA);
		dar(usuario, org2, RolMembresia.PROPIETARIO);

		assertTrue(TenantContext.calcularCon(ORG1, () -> alumnoService.eliminarAlumno(alumnoId)));

		assertNull(jdbc.queryForObject("SELECT alumno_id FROM usuario WHERE id = ?", Long.class, usuario.getId()));
		assertEquals(List.of("PROPIETARIO"), jdbc.queryForList(
				"SELECT CONCAT(rol) FROM membresia WHERE usuario_id = ? ORDER BY organizacion_id",
				String.class, usuario.getId()));
		assertEquals(org2, jwtService.verificar(login(email)).organizacionId(), "Sigue entrando en su otra escuela");
	}

	@Test
	void emailDeAlumnoVinculadoAUnaCuentaCompartidaDevuelveConflictoSinTocarLaCuenta() {
		String email = "email-compartido-" + sufijo + "@example.test";
		long alumnoId = TenantContext.calcularCon(ORG1, () -> crearAlumno("EMAIL COMPARTIDO " + sufijo, email));
		Usuario usuario = crearUsuario(email, alumnoId, ORG1);
		dar(usuario, ORG1, RolMembresia.FAMILIA);
		dar(usuario, org2, RolMembresia.ADMINISTRADOR);

		UsuarioCompartidoException error = assertThrows(UsuarioCompartidoException.class,
				() -> TenantContext.ejecutarCon(ORG1, () -> actualizarEmail(alumnoId, "nuevo-" + email)));

		assertTrue(error.getMessage().contains("otra escuela"), error.getMessage());
		assertEquals(email, jdbc.queryForObject("SELECT email FROM usuario WHERE id = ?", String.class, usuario.getId()));
		assertEquals(email, jdbc.queryForObject("SELECT email FROM alumno WHERE id = ?", String.class, alumnoId));
	}

	@Test
	void emailDeAlumnoDeUnaSolaEscuelaActualizaLaCuentaComoAntes() {
		String email = "email-solo-" + sufijo + "@example.test";
		long alumnoId = TenantContext.calcularCon(ORG1, () -> crearAlumno("EMAIL SOLO " + sufijo, email));
		Usuario usuario = crearUsuario(email, alumnoId, ORG1);
		dar(usuario, ORG1, RolMembresia.FAMILIA);

		TenantContext.ejecutarCon(ORG1, () -> actualizarEmail(alumnoId, "nuevo-" + email));

		assertEquals("nuevo-" + email, jdbc.queryForObject("SELECT email FROM usuario WHERE id = ?", String.class,
				usuario.getId()));
	}

	private void actualizarEmail(long alumnoId, String nuevoEmail) {
		// Como el controlador con open-in-view: el DTO se construye con la sesión abierta.
		tx.executeWithoutResult(estado -> {
			Alumno alumno = alumnos.findById(alumnoId).orElseThrow();
			AlumnoDTO dto = AlumnoDTO.deAlumno(alumno);
			dto.setEmail(nuevoEmail);
			dto.setTipoTarifa(com.taemoi.project.legado.TipoTarifa.ADULTO.name());
			alumnoService.actualizarAlumno(alumnoId, dto, alumno.getFechaNacimiento(), null);
		});
	}

	// ---------------------------------------------------------------------------------

	private String login(String email) throws Exception {
		MvcResult resultado = mvc.perform(post("/api/auth/signin").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"contrasena\":\"" + CLAVE + "\"}"))
				.andExpect(status().isOk())
				.andReturn();
		JsonNode cuerpo = json.readTree(resultado.getResponse().getContentAsString());
		String token = cuerpo.get("token").asText();
		assertEquals(token, cookieJwt(resultado), "La cookie lleva el mismo token que la respuesta");
		return token;
	}

	private static String cookieJwt(MvcResult resultado) {
		return resultado.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
				.filter(c -> c.startsWith("jwt=") && !c.startsWith("jwt=;"))
				.map(c -> c.substring(4, c.indexOf(';')))
				.reduce((a, b) -> b)
				.orElse(null);
	}

	private static Cookie jwt(String token) {
		return new Cookie("jwt", token);
	}

	private Usuario crearUsuario(String email, Long alumnoId, Long organizacionAlumno) {
		Long org = organizacionAlumno != null ? organizacionAlumno : ORG1;
		return TenantContext.calcularCon(org, () -> {
			Usuario usuario = new Usuario();
			usuario.setNombre("Usuario");
			usuario.setApellidos("Prueba");
			usuario.setEmail(email);
			usuario.setContrasena(encoder.encode(CLAVE));
			usuario.setAuthProvider(AuthProvider.LOCAL);
			if (alumnoId != null) {
				usuario.setAlumno(alumnos.findById(alumnoId).orElseThrow());
			}
			return usuarios.save(usuario);
		});
	}

	private Membresia dar(Usuario usuario, Long organizacionId, RolMembresia rol) {
		return TenantContext.calcularCon(ORG1,
				() -> membresias.save(new Membresia(usuario.getId(), organizacionId, rol)));
	}

	private long crearAlumno(String nombre, String email) {
		Alumno alumno = new Alumno();
		alumno.setNombre(nombre);
		alumno.setApellidos("PRUEBA SESION");
		alumno.setNumeroExpediente(contadores.siguienteNumeroExpediente());
		alumno.setFechaNacimiento(Date.valueOf(LocalDate.of(2010, 5, 5)));
		alumno.setDireccion("Calle Prueba 1");
		alumno.setTelefono("600000000");
		alumno.setActivo(true);
		alumno.setAutorizacionWeb(true);
		alumno.setEmail(email);
		return alumnos.save(alumno).getId();
	}

	private void crearEvento(String titulo) {
		Evento evento = new Evento();
		evento.setTitulo(titulo);
		evento.setDescripcion("-");
		evento.setVisible(true);
		eventos.save(evento);
	}

	private static String sha256(String valor) {
		try {
			return HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}
}
