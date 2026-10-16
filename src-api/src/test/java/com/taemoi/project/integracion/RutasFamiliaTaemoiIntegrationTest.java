package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Las 22 rutas del portal de familia ({@code hasRole('ROLE_USER')}, fase3-sedes-permisos.md §1) en Taemoi, por
 * HTTP con sesión real: cada una permite a la familia lo suyo y deniega lo ajeno igual que antes de leer
 * {@code usuario_alumno} (entrega 2a). Las familias se crean después de V31, sin fila en {@code usuario_alumno}:
 * en Taemoi entran por el email (D6).
 *
 * <ul>
 * <li>16 rutas con alumno: 403 (desde la 5c-2 de {@code @PreAuthorize} con {@code @acceso}, con el cuerpo JSON
 * del {@code accessDeniedHandler}; antes, vacío) con el alumno de otra familia, con un
 * alumno dado de baja con su email y, para un gestor degradado, con el alumno de otro email de su fila R1 de
 * V31; cualquier otro estado con el suyo.</li>
 * <li>5 rutas de {@code /api/auth} sin control por alumno: 2xx para la familia; 401 para una cuenta solo
 * familia sin alumno activo (su JWT se rechaza en cada petición).</li>
 * <li>{@code /api/alumnos/{id}/completo}: cerrada a la familia por la regla de URL (403), antes y ahora.</li>
 * </ul>
 * Se ejecutó igual (verde) con el código anterior a la entrega 2a.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RutasFamiliaTaemoiIntegrationTest extends MySqlIntegrationTest {

	private static final long ORG1 = 1L;
	private static final String CLAVE = "Clave-Segura-123";

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired PasswordEncoder encoder;
	@Autowired UsuarioRepository usuarios;
	@Autowired MembresiaRepository membresias;
	@Autowired AlumnoRepository alumnos;
	@Autowired ContadorService contadores;
	@Autowired JwtService jwtService;

	@MockitoBean JavaMailSender mailSender;

	final String sufijo = UUID.randomUUID().toString().substring(0, 8);
	final List<Long> cuentas = new ArrayList<>();
	final List<Long> fichas = new ArrayList<>();

	long alumnoFamilia;
	long alumnoFamiliaBaja;
	long alumnoOtraFamilia;
	long alumnoDegradado;
	String tokenFamilia;
	String tokenDegradado;
	String tokenSinAlumno;

	@BeforeAll
	void crearFamilias() throws Exception {
		Usuario familia = cuenta("rf-familia");
		dar(familia, RolMembresia.FAMILIA);
		alumnoFamilia = alumno("RF FAMILIA", familia.getEmail());
		alumnoFamiliaBaja = alumno("RF FAMILIA BAJA", familia.getEmail());
		jdbc.update("UPDATE alumno SET activo = FALSE WHERE id = ?", alumnoFamiliaBaja);

		Usuario otra = cuenta("rf-otra");
		dar(otra, RolMembresia.FAMILIA);
		alumnoOtraFamilia = alumno("RF OTRA", otra.getEmail());

		// Gestor degradado: su fila R1 de V31 (alumno de otro email) ya no le da acceso.
		Usuario degradado = cuenta("rf-degradado");
		dar(degradado, RolMembresia.FAMILIA);
		jdbc.update("UPDATE membresia SET activa = FALSE WHERE id = ?", dar(degradado, RolMembresia.ADMINISTRADOR).getId());
		alumnoDegradado = alumno("RF DEGRADADO", degradado.getEmail());
		jdbc.update("UPDATE usuario SET alumno_id = ? WHERE id = ?", alumnoOtraFamilia, degradado.getId());
		jdbc.update("""
				INSERT INTO usuario_alumno (usuario_id, alumno_id, organizacion_id, relacion, origen)
				VALUES (?, ?, ?, 'TUTOR', 'MIGRACION')
				""", degradado.getId(), alumnoOtraFamilia, ORG1);

		Usuario sinAlumno = cuenta("rf-sin-alumno");
		dar(sinAlumno, RolMembresia.FAMILIA);
		long baja = alumno("RF SIN ALUMNO", sinAlumno.getEmail());
		jdbc.update("UPDATE alumno SET activo = FALSE WHERE id = ?", baja);

		tokenFamilia = login(familia.getEmail());
		tokenDegradado = login(degradado.getEmail());
		mvc.perform(post("/api/auth/signin").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + sinAlumno.getEmail() + "\",\"contrasena\":\"" + CLAVE + "\"}"))
				.andExpect(status().is4xxClientError());
		// Token firmado de la escuela 1 (como uno emitido antes de perder el alumno): se rechaza en cada petición.
		tokenSinAlumno = jwtService.generateToken(sinAlumno, 3_600_000L, ORG1);
	}

	/** Sin borrar nada: se retiran las cuentas y los alumnos para no interferir con otras clases. */
	@AfterAll
	void retirar() {
		for (Long cuenta : cuentas) {
			jdbc.update("UPDATE membresia SET activa = FALSE WHERE usuario_id = ?", cuenta);
			jdbc.update("UPDATE usuario SET alumno_id = NULL WHERE id = ?", cuenta);
		}
		for (Long ficha : fichas) {
			jdbc.update("UPDATE alumno SET activo = FALSE WHERE id = ?", ficha);
		}
	}

	@AfterEach
	void sinFugaDeOrganizacion() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
	}

	/** Las 16 rutas que comprueban el acceso al alumno. */
	private static List<String> rutasConAlumno(long alumnoId) {
		String a = "/api/alumnos/" + alumnoId;
		return List.of(
				"GET " + a + "/imagen",
				"GET " + a + "/documentos",
				"GET " + a + "/documentos/999999999/descargar",
				"GET " + a + "/grupos",
				"GET " + a + "/turnos",
				"GET " + a + "/reto-diario",
				"PUT " + a + "/reto-diario/completar",
				"GET " + a + "/reto-diario/ranking-semanal?deporte=TAEKWONDO",
				"GET " + a + "/reto-diario/ranking-general?deporte=TAEKWONDO",
				"GET " + a + "/deportes",
				"GET " + a + "/deportes/TAEKWONDO/material-examen",
				"GET " + a + "/deportes/TAEKWONDO/material-examen/temario",
				"GET " + a + "/deportes/TAEKWONDO/material-examen/documentacion/no-existe.pdf",
				"GET " + a + "/deportes/TAEKWONDO/material-examen/videos/no-existe.mp4",
				"GET /api/convocatorias/alumnos/" + alumnoId,
				"GET /api/grupos/999999999/alumnos/" + alumnoId + "/turnos");
	}

	/** Las 5 rutas de familia de {@code /api/auth} (sin control por alumno). */
	private static List<String> rutasSinAlumno() {
		return List.of(
				"GET /api/auth/roles",
				"GET /api/auth/user",
				"GET /api/auth/user/recordatorios-racha-email",
				"PUT /api/auth/user/recordatorios-racha-email",
				"GET /api/auth/user/alumnos");
	}

	/**
	 * La ruta 22, {@code GET /api/alumnos/{id}/completo}, lleva {@code ROLE_USER} en su {@code @PreAuthorize} pero
	 * no comprueba el alumno; la regla por URL {@code GET /api/alumnos/**} de {@code SecurityConfiguration} la
	 * cierra a la familia (403) incluso con su propio alumno. Así era antes y así sigue.
	 */
	@Test
	void completo_cerradoALaFamiliaPorLaReglaDeUrl() throws Exception {
		assertEquals(403, estado("GET /api/alumnos/" + alumnoFamilia + "/completo", tokenFamilia));
		assertEquals(403, estado("GET /api/alumnos/" + alumnoOtraFamilia + "/completo", tokenFamilia));
		assertEquals(401, estado("GET /api/alumnos/" + alumnoFamilia + "/completo", tokenSinAlumno));
	}

	@Test
	void rutasConAlumno_permitenLoSuyoYDeniegan403LoAjeno() throws Exception {
		assertEquals(16, rutasConAlumno(1L).size());
		Map<String, Integer> fallos = new LinkedHashMap<>();
		for (String ruta : rutasConAlumno(alumnoFamilia)) {
			int estado = estado(ruta, tokenFamilia);
			if (estado == 401 || estado == 403) {
				fallos.put("familia, su alumno: " + ruta, estado);
			}
		}
		for (String ruta : rutasConAlumno(alumnoDegradado)) {
			int estado = estado(ruta, tokenDegradado);
			if (estado == 401 || estado == 403) {
				fallos.put("gestor degradado, su alumno: " + ruta, estado);
			}
		}
		for (String ruta : rutasConAlumno(alumnoOtraFamilia)) {
			esperar403(fallos, "familia, alumno de otra familia: " + ruta, ruta, tokenFamilia);
			esperar403(fallos, "gestor degradado, alumno de su fila R1: " + ruta, ruta, tokenDegradado);
		}
		for (String ruta : rutasConAlumno(alumnoFamiliaBaja)) {
			esperar403(fallos, "familia, su alumno dado de baja: " + ruta, ruta, tokenFamilia);
		}
		for (String ruta : rutasConAlumno(alumnoFamilia)) {
			int estado = estado(ruta, tokenSinAlumno);
			if (estado != 401) {
				fallos.put("cuenta sin alumno activo: " + ruta, estado);
			}
		}
		assertTrue(fallos.isEmpty(), "Rutas con otro resultado: " + fallos);
	}

	@Test
	void rutasSinAlumno_familiaEntraYCuentaSinAlumnoActivoNo() throws Exception {
		assertEquals(5, rutasSinAlumno().size());
		Map<String, Integer> fallos = new LinkedHashMap<>();
		for (String ruta : rutasSinAlumno()) {
			int estado = estado(ruta, tokenFamilia);
			if (estado < 200 || estado >= 300) {
				fallos.put("familia: " + ruta, estado);
			}
			int sinAlumno = estado(ruta, tokenSinAlumno);
			if (sinAlumno != 401) {
				fallos.put("cuenta sin alumno activo: " + ruta, sinAlumno);
			}
		}
		assertTrue(fallos.isEmpty(), "Rutas con otro resultado: " + fallos);
	}

	@Test
	void userAlumnosYUser_muestranSoloLosAlumnosDeLaFamilia() throws Exception {
		assertEquals(List.of(alumnoFamilia), ids(leer("/api/auth/user/alumnos", tokenFamilia)));
		assertEquals(alumnoFamilia, leer("/api/auth/user", tokenFamilia).get("alumnoDTO").get("id").asLong());

		// El gestor degradado no ve el alumno de su vínculo directo (otro email), ni en la lista ni en /user.
		assertEquals(List.of(alumnoDegradado), ids(leer("/api/auth/user/alumnos", tokenDegradado)));
		assertEquals(alumnoDegradado, leer("/api/auth/user", tokenDegradado).get("alumnoDTO").get("id").asLong());
	}

	// ------------------------------------------------------------------ utilidades

	private void esperar403(Map<String, Integer> fallos, String caso, String ruta, String token) throws Exception {
		MvcResult r = peticion(ruta, token);
		int estado = r.getResponse().getStatus();
		// Desde la 5c-2 lo deniega @PreAuthorize (@acceso): cuerpo JSON del accessDeniedHandler, ya no vacío (D5-4).
		if (estado != 403 || !r.getResponse().getContentAsString().contains("Acceso denegado")) {
			fallos.put(caso, estado);
		}
	}

	private int estado(String ruta, String token) throws Exception {
		return peticion(ruta, token).getResponse().getStatus();
	}

	private MvcResult peticion(String ruta, String token) throws Exception {
		String[] partes = ruta.split(" ", 2);
		MockHttpServletRequestBuilder req = switch (partes[0]) {
			case "GET" -> get(partes[1]);
			case "PUT" -> put(partes[1]).contentType(MediaType.APPLICATION_JSON).content("{\"habilitado\":true}");
			default -> throw new IllegalArgumentException(ruta);
		};
		return mvc.perform(req.cookie(new Cookie("jwt", token))).andReturn();
	}

	private JsonNode leer(String ruta, String token) throws Exception {
		MvcResult r = mvc.perform(get(ruta).cookie(new Cookie("jwt", token))).andExpect(status().isOk()).andReturn();
		return json.readTree(r.getResponse().getContentAsString());
	}

	private static List<Long> ids(JsonNode lista) {
		List<Long> ids = new ArrayList<>();
		lista.forEach(n -> ids.add(n.get("id").asLong()));
		return ids;
	}

	private String login(String email) throws Exception {
		MvcResult resultado = mvc.perform(post("/api/auth/signin").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"contrasena\":\"" + CLAVE + "\"}"))
				.andExpect(status().isOk())
				.andReturn();
		return json.readTree(resultado.getResponse().getContentAsString()).get("token").asText();
	}

	private Usuario cuenta(String nombre) {
		Usuario creada = TenantContext.calcularCon(ORG1, () -> {
			Usuario usuario = new Usuario();
			usuario.setNombre("Usuario");
			usuario.setApellidos("Rutas familia");
			usuario.setEmail(nombre + "-" + sufijo + "@example.test");
			usuario.setContrasena(encoder.encode(CLAVE));
			usuario.setAuthProvider(AuthProvider.LOCAL);
			return usuarios.save(usuario);
		});
		cuentas.add(creada.getId());
		return creada;
	}

	private Membresia dar(Usuario usuario, RolMembresia rol) {
		return TenantContext.calcularCon(ORG1, () -> membresias.save(new Membresia(usuario.getId(), ORG1, rol)));
	}

	private long alumno(String nombre, String email) {
		long id = TenantContext.calcularCon(ORG1, () -> {
			Alumno alumno = new Alumno();
			alumno.setNombre(nombre + " " + sufijo);
			alumno.setApellidos("PRUEBA RUTAS FAMILIA");
			alumno.setNumeroExpediente(contadores.siguienteNumeroExpediente());
			alumno.setFechaNacimiento(Date.valueOf(LocalDate.of(2013, 4, 4)));
			alumno.setDireccion("Calle Prueba 7");
			alumno.setTelefono("600000007");
			alumno.setActivo(true);
			alumno.setAutorizacionWeb(true);
			alumno.setEmail(email);
			return alumnos.save(alumno).getId();
		});
		fichas.add(id);
		return id;
	}
}
