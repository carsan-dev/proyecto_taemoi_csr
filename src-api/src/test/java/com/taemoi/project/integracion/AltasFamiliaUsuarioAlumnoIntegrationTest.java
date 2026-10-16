package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.config.OAuth2AuthenticationSuccessHandler;
import com.taemoi.project.dtos.AlumnoDTO;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.AuthProvider;
import com.taemoi.project.entities.Membresia;
import com.taemoi.project.entities.Organizacion;
import com.taemoi.project.entities.RegistroPendiente;
import com.taemoi.project.entities.RolMembresia;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.MembresiaRepository;
import com.taemoi.project.repositories.OrganizacionRepository;
import com.taemoi.project.repositories.RegistroPendienteRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.AccesoFamiliaService;
import com.taemoi.project.services.AlumnoService;
import com.taemoi.project.services.ContadorService;
import com.taemoi.project.services.RegistroService;
import com.taemoi.project.services.VinculoFamiliaService;
import com.taemoi.project.utils.EmailUtils;

import jakarta.servlet.http.Cookie;

/**
 * Fase 3, entrega 2b: el registro y el primer login con Google escriben {@code usuario_alumno} (origen REGISTRO,
 * relación por edad). De extremo a extremo (alta → inicio de sesión → {@code GET /api/auth/user/alumnos}) contra
 * MySQL y la cadena de filtros real:
 * <ul>
 * <li>escuela SIN {@code comportamiento_legado_taemoi}: la familia ve sus alumnos por el vínculo nuevo (antes de la
 * 2b no entraba);</li>
 * <li>Taemoi: el conjunto visible no cambia (las filas nuevas son de alumnos de su mismo email);</li>
 * <li>la escritura es idempotente y no mezcla escuelas.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AltasFamiliaUsuarioAlumnoIntegrationTest extends MySqlIntegrationTest {

	private static final long ORG1 = 1L;
	private static final String CLAVE = "Clave-Segura-123";
	/** Relativas a hoy para que la relación esperada (PROPIO desde los 18 años) no dependa de la fecha. */
	private static final LocalDate MENOR = LocalDate.now().minusYears(10);
	private static final LocalDate ADULTO = LocalDate.now().minusYears(40);

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired PasswordEncoder encoder;
	@Autowired UsuarioRepository usuarios;
	@Autowired MembresiaRepository membresias;
	@Autowired OrganizacionRepository organizaciones;
	@Autowired AlumnoRepository alumnos;
	@Autowired RegistroPendienteRepository registrosPendientes;
	@Autowired ContadorService contadores;
	@Autowired RegistroService registroService;
	@Autowired OAuth2AuthenticationSuccessHandler googleHandler;
	@Autowired AccesoFamiliaService acceso;
	@Autowired VinculoFamiliaService vinculos;
	@Autowired AlumnoService alumnoService;
	@Autowired TransactionTemplate tx;

	@MockitoBean JavaMailSender mailSender;

	final String sufijo = UUID.randomUUID().toString().substring(0, 8);
	long org2;
	final List<Long> alumnosCreados = new ArrayList<>();
	final List<String> emailsCreados = new ArrayList<>();

	@BeforeAll
	void crearEscuelaSinMarca() {
		org2 = TenantContext.calcularCon(ORG1, () -> {
			Organizacion org = new Organizacion();
			org.setSlug("it-altas-" + sufijo);
			org.setNombre("Escuela altas " + sufijo);
			return organizaciones.save(org).getId();
		});
		assertEquals(Boolean.FALSE, jdbc.queryForObject(
				"SELECT comportamiento_legado_taemoi FROM organizacion WHERE id = ?", Boolean.class, org2));
		assertEquals(Boolean.TRUE, jdbc.queryForObject(
				"SELECT comportamiento_legado_taemoi FROM organizacion WHERE id = ?", Boolean.class, ORG1));
	}

	/** Sin borrar nada: se retiran cuentas y alumnos para no interferir con otras clases. */
	@AfterAll
	void retirar() {
		for (String email : emailsCreados) {
			jdbc.update("UPDATE membresia SET activa = FALSE WHERE usuario_id IN (SELECT id FROM usuario WHERE email = ?)",
					email);
		}
		for (Long id : alumnosCreados) {
			jdbc.update("UPDATE alumno SET activo = FALSE, email = ? WHERE id = ?",
					"retirado-altas-" + id + "-" + sufijo + "@example.test", id);
		}
	}

	@AfterEach
	void sinFugaDeOrganizacion() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
	}

	// ------------------------------------------------------------------ escuela sin la marca

	@Test
	void escuelaSinMarca_registroEscribeElVinculoYLaFamiliaVeSuAlumno() throws Exception {
		String email = email("registro-sin-marca");
		long propio = alumno(org2, "REG ADULTO", email, ADULTO);
		// Hermano con el mismo email: el registro solo vincula el alumno registrado (como usuario.alumno_id).
		long hermano = alumno(org2, "REG HERMANO", email, MENOR);

		long usuarioId = registrar(org2, email, propio);

		assertEquals(List.of(Map.of("organizacion_id", org2, "relacion", "PROPIO", "origen", "REGISTRO")),
				filas(usuarioId, propio));
		assertEquals(List.of(), filas(usuarioId, hermano));
		assertEquals(propio, jdbc.queryForObject("SELECT alumno_id FROM usuario WHERE id = ?", Long.class, usuarioId),
				"usuario.alumno_id se mantiene hasta V32");

		String token = loginConClave(email);
		assertEquals(Set.of(propio), alumnosVisibles(token));
	}

	@Test
	void escuelaSinMarca_googleVinculaTodosLosAlumnosLibresDelEmailYLaFamiliaLosVe() throws Exception {
		String email = email("google-sin-marca");
		long primero = alumno(org2, "GOO PRIMERO", email, MENOR);
		long hermano = alumno(org2, "GOO HERMANO", email, ADULTO);
		long inactivo = alumno(org2, "GOO INACTIVO", email, MENOR);
		jdbc.update("UPDATE alumno SET activo = FALSE WHERE id = ?", inactivo);
		// Alumno con el email X que ya tiene OTRA cuenta Y (de otro email): opción conservadora, no se vincula.
		long deOtraCuenta = alumno(org2, "GOO DE Y", email, MENOR);
		long cuentaY = cuentaConClave(email("google-sin-marca-y"), org2, RolMembresia.FAMILIA);
		jdbc.update("UPDATE usuario SET alumno_id = ? WHERE id = ?", deOtraCuenta, cuentaY);

		String token = loginConGoogle(org2, email);

		long usuarioId = jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", Long.class, email);
		assertEquals(List.of(Map.of("organizacion_id", org2, "relacion", "TUTOR", "origen", "REGISTRO")),
				filas(usuarioId, primero));
		assertEquals(List.of(Map.of("organizacion_id", org2, "relacion", "PROPIO", "origen", "REGISTRO")),
				filas(usuarioId, hermano));
		// Inactivo sin cuenta: también se vincula (la lectura filtra por activo; al reactivarlo se verá sin más).
		assertEquals(List.of(Map.of("organizacion_id", org2, "relacion", "TUTOR", "origen", "REGISTRO")),
				filas(usuarioId, inactivo));
		assertEquals(List.of(), filas(usuarioId, deOtraCuenta));
		assertEquals(primero, jdbc.queryForObject("SELECT alumno_id FROM usuario WHERE id = ?", Long.class, usuarioId));
		assertEquals(deOtraCuenta,
				jdbc.queryForObject("SELECT alumno_id FROM usuario WHERE id = ?", Long.class, cuentaY),
				"la cuenta Y conserva su alumno");

		assertEquals(Set.of(primero, hermano), alumnosVisibles(token));

		// Segundo login con Google: cuenta existente, nada nuevo que escribir.
		String otraVez = loginConGoogle(org2, email);
		assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM usuario_alumno WHERE usuario_id = ?", Integer.class,
				usuarioId));
		assertEquals(Set.of(primero, hermano), alumnosVisibles(otraVez));

		// Al reactivar el alumno inactivo, la familia lo ve sin revincularlo.
		jdbc.update("UPDATE alumno SET activo = TRUE WHERE id = ?", inactivo);
		assertEquals(Set.of(primero, hermano, inactivo), alumnosVisibles(otraVez));
	}

	@Test
	void escuelaSinMarca_googleSinAlumnoLibreSigueRechazandose() throws Exception {
		String email = email("google-sin-libre");
		long deOtraCuenta = alumno(org2, "GOO SOLO DE Y", email, MENOR);
		long cuentaY = cuentaConClave(email("google-sin-libre-y"), org2, RolMembresia.FAMILIA);
		jdbc.update("UPDATE usuario SET alumno_id = ? WHERE id = ?", deOtraCuenta, cuentaY);

		MockHttpServletResponse respuesta = google(org2, email);

		assertTrue(respuesta.getRedirectedUrl().contains("error=no_alumno_found"), respuesta.getRedirectedUrl());
		assertNull(cookieJwt(respuesta));
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM usuario WHERE email = ?", Integer.class, email));
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM usuario_alumno WHERE alumno_id = ?", Integer.class,
				deOtraCuenta));
	}

	// ------------------------------------------------------------------ Taemoi: nada visible cambia

	@Test
	void taemoi_registroNoCambiaElConjuntoVisible() throws Exception {
		String email = email("registro-taemoi");
		long propio = alumno(ORG1, "REG T PROPIO", email, MENOR);
		long hermano = alumno(ORG1, "REG T HERMANO", email, MENOR);

		long usuarioId = registrar(ORG1, email, propio);

		assertEquals(List.of(Map.of("organizacion_id", ORG1, "relacion", "TUTOR", "origen", "REGISTRO")),
				filas(usuarioId, propio));
		Usuario usuario = cuenta(usuarioId);
		assertEquals(reglaDeEmail(usuario, ORG1), accesiblesEnTaemoi(usuario));
		assertEquals(Set.of(propio, hermano), alumnosVisibles(loginConClave(email)));
	}

	@Test
	void taemoi_googleNoCambiaElConjuntoVisible() throws Exception {
		String email = email("google-taemoi");
		long primero = alumno(ORG1, "GOO T PRIMERO", email, MENOR);
		long hermano = alumno(ORG1, "GOO T HERMANO", email, MENOR);

		String token = loginConGoogle(ORG1, email);

		long usuarioId = jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", Long.class, email);
		assertEquals(Set.of(primero, hermano), new TreeSet<>(jdbc.queryForList(
				"SELECT alumno_id FROM usuario_alumno WHERE usuario_id = ? AND origen = 'REGISTRO'", Long.class,
				usuarioId)));
		Usuario usuario = cuenta(usuarioId);
		assertEquals(reglaDeEmail(usuario, ORG1), accesiblesEnTaemoi(usuario));
		assertEquals(Set.of(primero, hermano), alumnosVisibles(token));
	}

	/**
	 * Revisión de la 2b, caso (a): en Taemoi, cambiar el email de un hermano vinculado por Google le quita el acceso
	 * a la familia, como antes (la fila REGISTRO solo cuenta con el email coincidente en las escuelas con la marca).
	 */
	@Test
	void taemoi_googleHermanoQueCambiaDeEmailDejaDeVerse() throws Exception {
		String email = email("google-taemoi-cambio");
		long primero = alumno(ORG1, "GOO TC PRIMERO", email, MENOR);
		long hermano = alumno(ORG1, "GOO TC HERMANO", email, MENOR);
		String token = loginConGoogle(ORG1, email);
		long usuarioId = jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", Long.class, email);
		assertEquals("REGISTRO", jdbc.queryForObject(
				"SELECT origen FROM usuario_alumno WHERE usuario_id = ? AND alumno_id = ?", String.class, usuarioId,
				hermano));
		assertEquals(Set.of(primero, hermano), alumnosVisibles(token));
		assertEquals(200, estado("/api/alumnos/" + hermano + "/deportes", token));

		// La gestión cambia el email del hermano (sin cuenta: la cuenta X no se toca).
		actualizarEmail(ORG1, hermano, email("google-taemoi-cambio-y"));

		Usuario usuario = cuenta(usuarioId);
		assertEquals(email, usuario.getEmail());
		assertEquals(reglaDeEmail(usuario, ORG1), accesiblesEnTaemoi(usuario));
		assertEquals(Set.of(primero), alumnosVisibles(token));
		assertEquals(403, estado("/api/alumnos/" + hermano + "/deportes", token));
		assertEquals(200, estado("/api/alumnos/" + primero + "/deportes", token));
	}

	/**
	 * Revisión de la 2b, caso (b): en Taemoi, vaciar el email del alumno registrado (la cuenta conserva el suyo) le
	 * quita el acceso, como antes.
	 */
	@Test
	void taemoi_alumnoRegistradoConEmailVaciadoDejaDeVerse() throws Exception {
		String email = email("registro-taemoi-vaciado");
		long propio = alumno(ORG1, "REG TV PROPIO", email, MENOR);
		long hermano = alumno(ORG1, "REG TV HERMANO", email, MENOR);
		long usuarioId = registrar(ORG1, email, propio);
		String token = loginConClave(email);
		assertEquals(Set.of(propio, hermano), alumnosVisibles(token));

		actualizarEmail(ORG1, propio, null);

		Usuario usuario = cuenta(usuarioId);
		assertEquals(email, usuario.getEmail(), "la cuenta conserva su email");
		assertEquals(1, jdbc.queryForObject(
				"SELECT COUNT(*) FROM usuario_alumno WHERE usuario_id = ? AND alumno_id = ? AND origen = 'REGISTRO'",
				Integer.class, usuarioId, propio), "la fila REGISTRO sigue ahí: es la lectura la que no la cuenta");
		assertEquals(reglaDeEmail(usuario, ORG1), accesiblesEnTaemoi(usuario));
		assertEquals(Set.of(hermano), alumnosVisibles(token));
		assertEquals(403, estado("/api/alumnos/" + propio + "/deportes", token));
		assertEquals(Set.of(hermano), alumnosVisibles(loginConClave(email)));
	}

	/** En una escuela sin la marca, la fila REGISTRO sigue valiendo aunque cambie el email del alumno. */
	@Test
	void escuelaSinMarca_registroSigueValiendoTrasCambiarElEmailDelAlumno() throws Exception {
		String email = email("registro-sin-marca-cambio");
		long propio = alumno(org2, "REG SC PROPIO", email, MENOR);
		long usuarioId = registrar(org2, email, propio);
		String token = loginConClave(email);

		actualizarEmail(org2, propio, null);

		assertEquals(email, cuenta(usuarioId).getEmail());
		assertEquals(Set.of(propio), alumnosVisibles(token));
		assertEquals(200, estado("/api/alumnos/" + propio + "/deportes", token));
	}

	// ------------------------------------------------------------------ escritura: idempotencia y escuelas

	@Test
	void vincularDosVecesNoDuplicaNiCambiaUnaFilaExistente() {
		String email = email("idempotente");
		long alumnoId = alumno(org2, "IDEM", email, MENOR);
		long usuarioId = cuentaConClave(email, org2, RolMembresia.FAMILIA);
		jdbc.update("""
				INSERT INTO usuario_alumno (usuario_id, alumno_id, organizacion_id, relacion, origen)
				VALUES (?, ?, ?, 'PROPIO', 'MIGRACION')
				""", usuarioId, alumnoId, org2);

		boolean escrita = TenantContext.calcularCon(org2, () -> vinculos.vincularAlta(
				usuarios.findById(usuarioId).orElseThrow(), alumnos.findById(alumnoId).orElseThrow()));

		assertFalse(escrita);
		assertEquals(List.of(Map.of("organizacion_id", org2, "relacion", "PROPIO", "origen", "MIGRACION")),
				filas(usuarioId, alumnoId));
	}

	@Test
	void unAlumnoDeOtraEscuelaQueLaDeLaSesionNoSeVincula() {
		String email = email("otra-escuela");
		long alumnoId = alumno(org2, "OTRA ESCUELA", email, MENOR);
		long usuarioId = cuentaConClave(email, org2, RolMembresia.FAMILIA);
		Alumno deLaEscuela2 = TenantContext.calcularCon(org2, () -> alumnos.findById(alumnoId).orElseThrow());
		Usuario cuenta = cuenta(usuarioId);

		assertThrows(IllegalStateException.class,
				() -> TenantContext.ejecutarCon(ORG1, () -> vinculos.vincularAlta(cuenta, deLaEscuela2)));

		assertEquals(List.of(), filas(usuarioId, alumnoId));
	}

	// ------------------------------------------------------------------ utilidades

	private long registrar(long organizacionId, String email, long alumnoId) {
		String token = "token-altas-" + UUID.randomUUID();
		TenantContext.ejecutarCon(organizacionId, () -> {
			RegistroPendiente pendiente = new RegistroPendiente();
			pendiente.setEmail(email);
			pendiente.setAlumnoId(alumnoId);
			pendiente.setPasswordHash(encoder.encode(CLAVE));
			pendiente.setTokenHash(sha256(token));
			pendiente.setTokenExpiresAt(LocalDateTime.now().plusHours(1));
			registrosPendientes.save(pendiente);
			registroService.confirmarRegistro(token);
		});
		return jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", Long.class, email);
	}

	/** Como la petición de vuelta de Google: el filtro fija la escuela y el manejador emite el JWT en la cookie. */
	private MockHttpServletResponse google(long organizacionId, String email) throws Exception {
		OAuth2User google = mock(OAuth2User.class);
		when(google.getAttribute("email")).thenReturn(email);
		when(google.getAttribute("email_verified")).thenReturn(true);
		Authentication autenticacion = mock(Authentication.class);
		when(autenticacion.getPrincipal()).thenReturn(google);
		MockHttpServletResponse respuesta = new MockHttpServletResponse();
		TenantContext.set(organizacionId);
		try {
			googleHandler.onAuthenticationSuccess(new MockHttpServletRequest(), respuesta, autenticacion);
		} finally {
			TenantContext.clear();
		}
		return respuesta;
	}

	private String loginConGoogle(long organizacionId, String email) throws Exception {
		MockHttpServletResponse respuesta = google(organizacionId, email);
		String token = cookieJwt(respuesta);
		assertNotNull(token, "Google no emitió JWT: " + respuesta.getRedirectedUrl());
		return token;
	}

	private String loginConClave(String email) throws Exception {
		MvcResult resultado = mvc.perform(post("/api/auth/signin").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"contrasena\":\"" + CLAVE + "\"}"))
				.andExpect(status().isOk())
				.andReturn();
		return json.readTree(resultado.getResponse().getContentAsString()).get("token").asText();
	}

	private Set<Long> alumnosVisibles(String token) throws Exception {
		MvcResult resultado = mvc.perform(get("/api/auth/user/alumnos").cookie(new Cookie("jwt", token)))
				.andExpect(status().isOk())
				.andReturn();
		Set<Long> ids = new TreeSet<>();
		for (JsonNode alumno : json.readTree(resultado.getResponse().getContentAsString())) {
			ids.add(alumno.get("id").asLong());
		}
		return ids;
	}

	/** La regla de antes de la fase 3 (alumnos activos de la escuela con el email de la cuenta). */
	private Set<Long> reglaDeEmail(Usuario usuario, long organizacionId) {
		String email = EmailUtils.normalizeEmail(usuario.getEmail());
		return TenantContext.calcularCon(organizacionId, () -> alumnos.findAllByEmailIgnoreCaseAndActivoTrue(email))
				.stream().map(Alumno::getId).collect(Collectors.toSet());
	}

	/** Como la edición desde la gestión (AlumnoService con la sesión abierta, como el controlador). */
	private void actualizarEmail(long organizacionId, long alumnoId, String nuevoEmail) {
		TenantContext.ejecutarCon(organizacionId, () -> tx.executeWithoutResult(estado -> {
			Alumno alumno = alumnos.findById(alumnoId).orElseThrow();
			AlumnoDTO dto = AlumnoDTO.deAlumno(alumno);
			dto.setEmail(nuevoEmail);
			dto.setTipoTarifa(com.taemoi.project.legado.TipoTarifa.ADULTO.name());
			alumnoService.actualizarAlumno(alumnoId, dto, alumno.getFechaNacimiento(), null);
		}));
	}

	private int estado(String ruta, String token) throws Exception {
		return mvc.perform(get(ruta).cookie(new Cookie("jwt", token))).andReturn().getResponse().getStatus();
	}

	/** Regla nueva (AccesoFamiliaService), como al iniciar sesión en Taemoi. */
	private Set<Long> accesiblesEnTaemoi(Usuario usuario) {
		return TenantContext.calcularCon(ORG1, () -> Set.copyOf(acceso.idsAccesibles(usuario, ORG1)));
	}

	/** La cuenta no es de ninguna escuela, pero Hibernate necesita una fijada para abrir la sesión. */
	private Usuario cuenta(long usuarioId) {
		return TenantContext.calcularCon(ORG1, () -> usuarios.findById(usuarioId).orElseThrow());
	}

	private List<Map<String, Object>> filas(long usuarioId, long alumnoId) {
		return jdbc.queryForList("""
				SELECT organizacion_id, relacion, origen FROM usuario_alumno WHERE usuario_id = ? AND alumno_id = ?
				""", usuarioId, alumnoId);
	}

	private static String cookieJwt(MockHttpServletResponse respuesta) {
		return respuesta.getHeaders(HttpHeaders.SET_COOKIE).stream()
				.filter(c -> c.startsWith("jwt=") && !c.startsWith("jwt=;"))
				.map(c -> c.substring(4, c.indexOf(';')))
				.reduce((a, b) -> b)
				.orElse(null);
	}

	private String email(String caso) {
		String email = "altas-" + caso + "-" + sufijo + "@example.test";
		emailsCreados.add(email);
		return email;
	}

	private long cuentaConClave(String email, long organizacionId, RolMembresia rol) {
		long id = TenantContext.calcularCon(ORG1, () -> {
			Usuario usuario = new Usuario();
			usuario.setNombre("Usuario");
			usuario.setApellidos("Altas");
			usuario.setEmail(email);
			usuario.setContrasena(encoder.encode(CLAVE));
			usuario.setAuthProvider(AuthProvider.LOCAL);
			return usuarios.save(usuario).getId();
		});
		TenantContext.ejecutarCon(ORG1, () -> membresias.save(new Membresia(id, organizacionId, rol)));
		return id;
	}

	private long alumno(long organizacionId, String nombre, String email, LocalDate nacimiento) {
		long id = TenantContext.calcularCon(organizacionId, () -> {
			Alumno alumno = new Alumno();
			alumno.setNombre(nombre + " " + sufijo);
			alumno.setApellidos("PRUEBA ALTAS");
			alumno.setNumeroExpediente(contadores.siguienteNumeroExpediente());
			alumno.setFechaNacimiento(Date.valueOf(nacimiento));
			alumno.setDireccion("Calle Prueba 2");
			alumno.setTelefono("600000002");
			alumno.setActivo(true);
			alumno.setAutorizacionWeb(true);
			alumno.setEmail(email);
			return alumnos.save(alumno).getId();
		});
		alumnosCreados.add(id);
		return id;
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
