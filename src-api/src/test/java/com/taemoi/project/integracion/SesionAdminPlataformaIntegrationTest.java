package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.entities.AuthProvider;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.GrupoService;

import jakarta.servlet.http.Cookie;

/**
 * {@code GET /api/auth/plataforma} por HTTP real (cadena de filtros completa) contra MySQL: el frontend pregunta si
 * la sesión es administradora de plataforma sin sondear {@code /api/plataforma/errores}, que dejaba un 403 en el
 * registro por cada carga de la gestión. Responde 200 a toda sesión, con la misma autoridad que exige
 * {@code /api/plataforma/**}; sin sesión, 401.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SesionAdminPlataformaIntegrationTest extends MySqlIntegrationTest {

	private static final long ORG1 = 1L;
	private static final String CLAVE = "Clave-Segura-Plataforma";
	private static final String RUTA = "/api/auth/plataforma";

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired PasswordEncoder encoder;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;

	@MockitoBean JavaMailSender mailSender;

	EscuelaConSedesDePrueba e;

	@BeforeAll
	void preparar() {
		e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-auth-plataforma");
	}

	@AfterEach
	void sinFugaDeOrganizacion() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
	}

	@Test
	void sinSesion_401() throws Exception {
		mvc.perform(get(RUTA)).andExpect(status().isUnauthorized());
	}

	@Test
	void plataformaSinMembresia_true() throws Exception {
		Usuario u = cuenta("sola");
		marcarPlataforma(u);
		Cookie c = jwt(login(u));
		esperar(c, true);
		// La misma sesión sigue sin poder leer /roles ni /user: por eso la ruta es propia.
		mvc.perform(get("/api/auth/user").cookie(c)).andExpect(status().isForbidden());
	}

	@Test
	void plataformaYPropietario_true() throws Exception {
		Usuario u = cuenta("prop");
		membresia(u, ORG1, "PROPIETARIO");
		marcarPlataforma(u);
		esperar(jwt(login(u)), true);
	}

	@Test
	void plataformaYSoloProfesor_true() throws Exception {
		Usuario u = cuenta("prof");
		membresia(u, e.org, "PROFESOR");
		marcarPlataforma(u);
		esperar(jwt(login(u)), true);
	}

	@Test
	void propietarioDeEscuela_false() throws Exception {
		Usuario u = cuenta("solo-prop");
		membresia(u, ORG1, "PROPIETARIO");
		esperar(jwt(login(u)), false);
	}

	@Test
	void gestor_false() throws Exception {
		Usuario u = cuenta("gestor");
		membresia(u, ORG1, "ADMINISTRADOR");
		esperar(jwt(login(u)), false);
	}

	@Test
	void soloProfesor_false() throws Exception {
		Usuario u = cuenta("solo-prof");
		membresia(u, e.org, "PROFESOR");
		Cookie c = jwt(login(u));
		esperar(c, false);
		// Solo GET (y HEAD) entra en su lista blanca.
		assertEquals(403, mvc.perform(post(RUTA).cookie(c)).andReturn().getResponse().getStatus());
	}

	// ---------------------------------------------------------------------------------

	private void esperar(Cookie c, boolean adminPlataforma) throws Exception {
		mvc.perform(get(RUTA).cookie(c))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.adminPlataforma").value(adminPlataforma));
		mvc.perform(head(RUTA).cookie(c)).andExpect(status().isOk());
	}

	private String login(Usuario u) throws Exception {
		MvcResult r = mvc.perform(post("/api/auth/signin").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + u.getEmail() + "\",\"contrasena\":\"" + CLAVE + "\"}"))
				.andExpect(status().isOk())
				.andReturn();
		return json.readTree(r.getResponse().getContentAsString()).get("token").asText();
	}

	private static Cookie jwt(String token) {
		return new Cookie("jwt", token);
	}

	private Usuario cuenta(String nombre) {
		String email = "it-auth-plataforma-" + nombre + "-" + e.marca + "@example.test";
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

	/** La marca no se escribe desde la aplicación: se pone por SQL, como en producción. */
	private void marcarPlataforma(Usuario usuario) {
		assertEquals(1, jdbc.update("UPDATE usuario SET es_admin_plataforma = TRUE WHERE id = ?", usuario.getId()));
	}

	private void membresia(Usuario usuario, long organizacion, String rol) {
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, ?)", usuario.getId(),
				organizacion, rol);
	}
}
