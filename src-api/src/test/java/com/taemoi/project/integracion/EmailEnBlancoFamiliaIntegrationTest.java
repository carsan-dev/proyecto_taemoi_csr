package com.taemoi.project.integracion;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.AuthProvider;
import com.taemoi.project.entities.Membresia;
import com.taemoi.project.entities.RolMembresia;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.MembresiaRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.ContadorService;
import com.taemoi.project.services.JwtService;

import jakarta.servlet.http.Cookie;

/**
 * El acceso de familia une cuenta y alumno por email: un email en blanco nunca es una coincidencia.
 * Una cuenta con email vacío y membresía FAMILIA activa, con un alumno activo de la misma escuela también
 * con email vacío, no puede iniciar sesión, su JWT se rechaza y no ve a ese alumno.
 *
 * <p>Las validaciones de las entidades impiden guardar emails vacíos, así que se crean con un email
 * válido y se vacían por JDBC (datos heredados o escritos fuera de la aplicación).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EmailEnBlancoFamiliaIntegrationTest extends MySqlIntegrationTest {

	private static final long ORG1 = 1L;
	private static final String CLAVE = "Clave-Segura-123";

	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired PasswordEncoder encoder;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired MembresiaRepository membresias;
	@Autowired AlumnoRepository alumnos;
	@Autowired ContadorService contadores;

	@MockitoBean JavaMailSender mailSender;

	final String sufijo = UUID.randomUUID().toString().substring(0, 8);
	long alumnoId;
	long usuarioId;
	long membresiaId;

	@BeforeAll
	void crearCuentaYAlumnoConEmailVacio() {
		alumnoId = TenantContext.calcularCon(ORG1, this::crearAlumno);
		usuarioId = TenantContext.calcularCon(ORG1, this::crearUsuario);
		membresiaId = TenantContext.calcularCon(ORG1,
				() -> membresias.save(new Membresia(usuarioId, ORG1, RolMembresia.FAMILIA)).getId());

		jdbc.update("UPDATE alumno SET email = '' WHERE id = ?", alumnoId);
		jdbc.update("UPDATE usuario SET email = '' WHERE id = ?", usuarioId);

		assertEquals("", jdbc.queryForObject("SELECT email FROM usuario WHERE id = ?", String.class, usuarioId));
		assertEquals(Boolean.TRUE, jdbc.queryForObject(
				"SELECT activo FROM alumno WHERE id = ? AND email = '' AND organizacion_id = ?", Boolean.class,
				alumnoId, ORG1));
		assertEquals(Boolean.TRUE, jdbc.queryForObject(
				"SELECT activa FROM membresia WHERE id = ? AND rol = 'FAMILIA' AND organizacion_id = ?",
				Boolean.class, membresiaId, ORG1));
	}

	/** Sin borrar nada: se deja de usar el email vacío para no interferir con otras clases. */
	@AfterAll
	void retirarEmailVacio() {
		jdbc.update("UPDATE membresia SET activa = FALSE WHERE id = ?", membresiaId);
		jdbc.update("UPDATE usuario SET email = ? WHERE id = ?", "retirado-" + sufijo + "@example.test", usuarioId);
		jdbc.update("UPDATE alumno SET activo = FALSE, email = ? WHERE id = ?",
				"retirado-alumno-" + sufijo + "@example.test", alumnoId);
	}

	@AfterEach
	void sinFugaDeOrganizacion() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
	}

	@Test
	void elLoginSeDeniegaConEmailVacioOSoloEspacios() throws Exception {
		for (String email : List.of("", "   ")) {
			MvcResult resultado = mvc.perform(post("/api/auth/signin").contentType(MediaType.APPLICATION_JSON)
					.content("{\"email\":\"" + email + "\",\"contrasena\":\"" + CLAVE + "\"}"))
					.andExpect(status().isForbidden())
					.andReturn();
			assertTrue(resultado.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
					.noneMatch(c -> c.startsWith("jwt=e")), "No se emite ninguna cookie de sesión");
		}
	}

	@Test
	void elJwtDeLaCuentaConEmailVacioSeRechaza() throws Exception {
		Usuario cuenta = cuenta();
		assertEquals("", cuenta.getEmail());
		String token = jwtService.generateToken(cuenta, 600_000, ORG1);

		mvc.perform(get("/api/auth/roles").cookie(new Cookie("jwt", token))).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/auth/user/alumnos").cookie(new Cookie("jwt", token)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void unaSesionDeFamiliaConEmailVacioNoVeAlAlumnoConEmailVacio() throws Exception {
		UsernamePasswordAuthenticationToken sesion = new UsernamePasswordAuthenticationToken(cuenta(), null,
				List.of(new SimpleGrantedAuthority("ROLE_USER")));

		mvc.perform(get("/api/auth/user/alumnos").with(authentication(sesion)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(0)));
		mvc.perform(get("/api/auth/user").with(authentication(sesion)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(usuarioId))
				.andExpect(jsonPath("$.alumnoDTO").doesNotExist());
	}

	// ---------------------------------------------------------------------------------

	private Usuario cuenta() {
		return TenantContext.calcularCon(ORG1, () -> usuarios.findById(usuarioId).orElseThrow());
	}

	private long crearUsuario() {
		Usuario usuario = new Usuario();
		usuario.setNombre("Usuario");
		usuario.setApellidos("Email vacio");
		usuario.setEmail("vacio-" + sufijo + "@example.test");
		usuario.setContrasena(encoder.encode(CLAVE));
		usuario.setAuthProvider(AuthProvider.LOCAL);
		return usuarios.save(usuario).getId();
	}

	private long crearAlumno() {
		Alumno alumno = new Alumno();
		alumno.setNombre("Vacio " + sufijo);
		alumno.setApellidos("PRUEBA EMAIL VACIO");
		alumno.setNumeroExpediente(contadores.siguienteNumeroExpediente());
		alumno.setFechaNacimiento(Date.valueOf(LocalDate.of(2011, 6, 6)));
		alumno.setDireccion("Calle Prueba 2");
		alumno.setTelefono("600000001");
		alumno.setActivo(true);
		alumno.setAutorizacionWeb(true);
		alumno.setEmail("alumno-vacio-" + sufijo + "@example.test");
		return alumnos.save(alumno).getId();
	}
}
