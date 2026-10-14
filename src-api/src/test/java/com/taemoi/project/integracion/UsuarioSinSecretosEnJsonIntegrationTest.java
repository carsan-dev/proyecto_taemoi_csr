package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.AuthProvider;
import com.taemoi.project.entities.Membresia;
import com.taemoi.project.entities.Organizacion;
import com.taemoi.project.entities.RolMembresia;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.MembresiaRepository;
import com.taemoi.project.repositories.OrganizacionRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.ContadorService;

import jakarta.servlet.http.Cookie;

/**
 * Regresión de seguridad: las respuestas que serializan la entidad {@link Usuario} (directa o embebida en
 * {@code Alumno.usuario}) no deben exponer el hash de la contraseña ni el token de restablecimiento.
 *
 * <p>Los endpoints {@code GET /api/admin} (paginado y sin paginar) y {@code GET /api/admin/{id}} devuelven
 * la entidad {@code Alumno}, que embebe su {@code Usuario}. Datos sintéticos: escuela propia (para que el
 * listado solo contenga este alumno), hash BCrypt de una cadena ficticia y un hash de token falso.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UsuarioSinSecretosEnJsonIntegrationTest extends MySqlIntegrationTest {

	private static final long ORG1 = 1L;
	private static final String CLAVE = "Clave-Segura-123";
	private static final List<String> CLAVES_PROHIBIDAS = List.of(
			"contrasena", "password", "resetTokenHash", "resetTokenExpiresAt");
	/** Forma JSON no sensible del usuario embebido, que debe mantenerse idéntica. */
	private static final Set<String> CLAVES_USUARIO_ESPERADAS = new TreeSet<>(Set.of(
			"id", "nombre", "apellidos", "email", "authProvider", "recordatorioRachaEmailHabilitado",
			"ultimoRecordatorioRachaReset", "roles", "authorities", "username", "accountNonExpired",
			"accountNonLocked", "credentialsNonExpired", "enabled"));

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired PasswordEncoder encoder;
	@Autowired UsuarioRepository usuarios;
	@Autowired MembresiaRepository membresias;
	@Autowired OrganizacionRepository organizaciones;
	@Autowired AlumnoRepository alumnos;
	@Autowired ContadorService contadores;

	@MockitoBean JavaMailSender mailSender;

	final String sufijo = UUID.randomUUID().toString().substring(0, 8);
	long org;
	long alumnoId;
	String emailVinculado;
	String hashContrasenaVinculado;
	String hashTokenFalso;
	String tokenAdmin;

	@BeforeAll
	void crearEscuelaAdminYAlumnoConUsuarioVinculado() throws Exception {
		org = TenantContext.calcularCon(ORG1, () -> {
			Organizacion o = new Organizacion();
			o.setSlug("it-usuario-json-" + sufijo);
			o.setNombre("Escuela usuario json " + sufijo);
			return organizaciones.save(o).getId();
		});
		alumnoId = TenantContext.calcularCon(org, this::crearAlumno);

		Usuario admin = crearUsuario("admin-json-" + sufijo + "@example.test", encoder.encode(CLAVE));
		dar(admin, org, RolMembresia.PROPIETARIO);

		// Usuario vinculado al alumno: hash BCrypt de una cadena ficticia y un hash de token falso (64 hex).
		emailVinculado = "vinculado-json-" + sufijo + "@example.test";
		hashContrasenaVinculado = encoder.encode("dummy-no-es-una-clave-" + sufijo);
		hashTokenFalso = (UUID.randomUUID().toString() + UUID.randomUUID().toString()).replace("-", "")
				.substring(0, 64);
		Usuario vinculado = crearUsuario(emailVinculado, hashContrasenaVinculado);
		dar(vinculado, org, RolMembresia.FAMILIA);
		jdbc.update("UPDATE usuario SET alumno_id = ?, reset_token_hash = ?, reset_token_expires_at = ? WHERE id = ?",
				alumnoId, hashTokenFalso, Timestamp.valueOf(LocalDateTime.of(2099, 1, 2, 3, 4, 5)), vinculado.getId());

		tokenAdmin = login(admin.getEmail());
	}

	@AfterEach
	void sinFugaDeOrganizacion() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
	}

	@Test
	void listadoAdminSinPaginarNoExponeSecretosDelUsuario() throws Exception {
		JsonNode cuerpo = getSinSecretos("/api/admin");
		assertEquals(1, cuerpo.size(), "solo el alumno de esta escuela");
		comprobarUsuarioEmbebidoConservaDatosNoSensibles(cuerpo.get(0));
	}

	@Test
	void listadoAdminPaginadoNoExponeSecretosDelUsuario() throws Exception {
		JsonNode cuerpo = getSinSecretos("/api/admin?page=1&size=10");
		assertEquals(1, cuerpo.get("content").size(), "solo el alumno de esta escuela");
		comprobarUsuarioEmbebidoConservaDatosNoSensibles(cuerpo.get("content").get(0));
	}

	@Test
	void detalleAdminNoExponeSecretosDelUsuario() throws Exception {
		JsonNode cuerpo = getSinSecretos("/api/admin/" + alumnoId);
		comprobarUsuarioEmbebidoConservaDatosNoSensibles(cuerpo);
	}

	@Test
	void elUsuarioVinculadoSigueAutenticandoseConSuHashGuardado() throws Exception {
		// La contraseña sigue siendo legible por Spring Security (getPassword) aunque no se serialice.
		Usuario admin = TenantContext.calcularCon(ORG1,
				() -> usuarios.findByEmailIgnoreCase("admin-json-" + sufijo + "@example.test").orElseThrow());
		assertTrue(encoder.matches(CLAVE, admin.getPassword()));
		assertNotNull(login(admin.getEmail()));
	}

	private JsonNode getSinSecretos(String url) throws Exception {
		MvcResult r = mvc.perform(get(url).cookie(jwt(tokenAdmin))).andExpect(status().isOk()).andReturn();
		String cuerpo = r.getResponse().getContentAsString();
		List<String> fugas = new ArrayList<>();
		if (cuerpo.contains(hashContrasenaVinculado)) {
			fugas.add("valor del hash de contraseña");
		}
		if (cuerpo.contains(hashTokenFalso)) {
			fugas.add("valor del hash del token de restablecimiento");
		}
		JsonNode arbol = json.readTree(cuerpo);
		recogerClavesProhibidas(arbol, "$", fugas);
		assertTrue(fugas.isEmpty(), "GET " + url + " expone datos sensibles: " + fugas);
		return arbol;
	}

	private static void recogerClavesProhibidas(JsonNode nodo, String ruta, List<String> fugas) {
		if (nodo.isObject()) {
			Iterator<String> nombres = nodo.fieldNames();
			while (nombres.hasNext()) {
				String nombre = nombres.next();
				if (CLAVES_PROHIBIDAS.contains(nombre)) {
					fugas.add(ruta + "." + nombre);
				}
				recogerClavesProhibidas(nodo.get(nombre), ruta + "." + nombre, fugas);
			}
		} else if (nodo.isArray()) {
			for (int i = 0; i < nodo.size(); i++) {
				recogerClavesProhibidas(nodo.get(i), ruta + "[" + i + "]", fugas);
			}
		}
	}

	/** La corrección solo quita campos sensibles: el usuario embebido sigue saliendo con sus datos. */
	private void comprobarUsuarioEmbebidoConservaDatosNoSensibles(JsonNode alumno) {
		assertEquals(alumnoId, alumno.get("id").asLong());
		JsonNode usuario = alumno.get("usuario");
		assertNotNull(usuario, "el alumno sigue embebiendo su usuario");
		assertEquals(emailVinculado, usuario.get("email").asText());
		assertEquals("Usuario", usuario.get("nombre").asText());
		assertEquals("Prueba", usuario.get("apellidos").asText());
		assertEquals("LOCAL", usuario.get("authProvider").asText());
		assertFalse(usuario.has("alumno"), "la back-reference sigue sin serializarse");
		Set<String> claves = new TreeSet<>();
		usuario.fieldNames().forEachRemaining(claves::add);
		assertEquals(CLAVES_USUARIO_ESPERADAS, claves, "solo se quitan los campos sensibles");
	}

	private String login(String email) throws Exception {
		MvcResult resultado = mvc.perform(post("/api/auth/signin").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"contrasena\":\"" + CLAVE + "\"}"))
				.andExpect(status().isOk())
				.andReturn();
		return json.readTree(resultado.getResponse().getContentAsString()).get("token").asText();
	}

	private static Cookie jwt(String token) {
		return new Cookie("jwt", token);
	}

	private Usuario crearUsuario(String email, String hash) {
		return TenantContext.calcularCon(ORG1, () -> {
			Usuario usuario = new Usuario();
			usuario.setNombre("Usuario");
			usuario.setApellidos("Prueba");
			usuario.setEmail(email);
			usuario.setContrasena(hash);
			usuario.setAuthProvider(AuthProvider.LOCAL);
			return usuarios.save(usuario);
		});
	}

	private Membresia dar(Usuario usuario, Long organizacionId, RolMembresia rol) {
		return TenantContext.calcularCon(ORG1,
				() -> membresias.save(new Membresia(usuario.getId(), organizacionId, rol)));
	}

	private long crearAlumno() {
		Alumno alumno = new Alumno();
		alumno.setNombre("JSON " + sufijo);
		alumno.setApellidos("PRUEBA USUARIO JSON");
		alumno.setNumeroExpediente(contadores.siguienteNumeroExpediente());
		alumno.setFechaNacimiento(Date.valueOf(LocalDate.of(2012, 5, 5)));
		alumno.setDireccion("Calle Prueba 1");
		alumno.setTelefono("600000000");
		alumno.setActivo(true);
		alumno.setAutorizacionWeb(true);
		alumno.setEmail("alumno-json-" + sufijo + "@example.test");
		return alumnos.save(alumno).getId();
	}
}
