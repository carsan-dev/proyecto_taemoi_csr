package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.dtos.AlumnoDTO;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.AuthProvider;
import com.taemoi.project.entities.Organizacion;
import com.taemoi.project.entities.RegistroPendiente;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.exceptions.usuario.UsuarioCompartidoException;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.OrganizacionRepository;
import com.taemoi.project.repositories.RegistroPendienteRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.AlumnoService;
import com.taemoi.project.services.JwtService;
import com.taemoi.project.services.OAuth2UserService;
import com.taemoi.project.services.RegistroService;

import jakarta.servlet.http.Cookie;

/**
 * R2-P2: la cuenta vinculada a un alumno ({@code usuario.alumno_id}, {@code Alumno.getUsuario()}) deja de cargarse con
 * cada alumno. Fija, contra MySQL, cada uso de la cuenta vinculada con y sin cuenta: el JSON de la entidad
 * ({@code /api/admin}), el registro, el primer login con Google, el cambio de email desde la gestión y el borrado del
 * alumno; y que un listado de alumnos ya no consulta la cuenta de cada uno. Escuela sintética propia (datos
 * inventados); usa el mismo contexto que {@link MoiskimdoAlumnosJsonDoradoIntegrationTest} (contador de sentencias).
 */
@SpringBootTest(properties = {
		"spring.jpa.properties.hibernate.session_factory.statement_inspector=com.taemoi.project.integracion.ContadorSentencias",
		"app.base.url=http://localhost:8080" })
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UsuarioVinculadoAlumnoIntegrationTest extends MySqlIntegrationTest {

	private static final long ORG1 = 1L;
	private static final String CLAVE = "Clave-Segura-123";

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired PasswordEncoder encoder;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired AlumnoRepository alumnos;
	@Autowired OrganizacionRepository organizaciones;
	@Autowired RegistroPendienteRepository registrosPendientes;
	@Autowired RegistroService registroService;
	@Autowired OAuth2UserService oauth2;
	@Autowired AlumnoService alumnoService;
	@Autowired TransactionTemplate tx;

	@MockitoBean JavaMailSender mailSender;

	final String sufijo = UUID.randomUUID().toString().substring(0, 8);
	long org;
	long otraEscuela;
	String tokenAdmin;

	@BeforeAll
	void crearEscuela() {
		org = escuela("it-r2p2-");
		otraEscuela = escuela("it-r2p2-otra-");
		long admin = cuenta("admin-r2p2-" + sufijo + "@example.test");
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, 'PROPIETARIO')", admin, org);
		Usuario usuario = TenantContext.calcularCon(ORG1, () -> usuarios.findById(admin).orElseThrow());
		tokenAdmin = jwtService.generateToken(usuario, 600_000, org);
	}

	@AfterEach
	void sinFugaDeOrganizacion() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
	}

	// ------------------------------------------------------------------ JSON de la entidad

	@Test
	void fichaYListadoDeAdmin_llevanLaCuentaVinculadaONull() throws Exception {
		long conCuenta = alumno("JSONCON", email("json-con"), true);
		long sinCuenta = alumno("JSONSIN", email("json-sin"), true);
		long cuenta = cuenta(email("json-cuenta"));
		vincularCuenta(cuenta, conCuenta);

		JsonNode con = leer("/api/admin/" + conCuenta);
		assertEquals(cuenta, con.get("usuario").get("id").asLong());
		assertEquals(email("json-cuenta"), con.get("usuario").get("email").asText());
		assertFalse(con.get("usuario").has("contrasena"));
		assertFalse(con.get("usuario").has("alumno"), "la referencia inversa no se serializa");
		assertTrue(leer("/api/admin/" + sinCuenta).get("usuario").isNull());

		// Listado de entidades (paginado y sin paginar): cada alumno con su cuenta, o null.
		for (JsonNode lista : List.of(leer("/api/admin?page=1&size=50").get("content"), leer("/api/admin"))) {
			Map<Long, JsonNode> porId = new HashMap<>();
			lista.forEach(a -> porId.put(a.get("id").asLong(), a.get("usuario")));
			assertEquals(cuenta, porId.get(conCuenta).get("id").asLong());
			assertTrue(porId.get(sinCuenta).isNull());
		}
	}

	@Test
	void listadoDeAlumnos_noConsultaLaCuentaDeCadaAlumno() throws Exception {
		for (int i = 0; i < 3; i++) {
			long alumno = alumno("LISTADO" + i, email("listado-" + i), true);
			vincularCuenta(cuenta(email("listado-cuenta-" + i)), alumno);
		}
		leer("/api/alumnos?incluirInactivos=false"); // calentamiento
		ContadorSentencias.Conteo<JsonNode> conteo = ContadorSentencias
				.contar(() -> leer("/api/alumnos?incluirInactivos=false"));
		assertTrue(conteo.resultado().size() >= 3);
		List<String> porAlumno = conteo.sentencias().stream()
				.filter(sql -> sql.toLowerCase().matches("(?s).*from usuario\\b.*\\balumno_id\\s*=.*")).toList();
		assertEquals(List.of(), porAlumno, "un select de usuario por alumno (N+1)");
	}

	// ------------------------------------------------------------------ registro

	@Test
	void registro_alumnoConCuentaSeRechazaYSinCuentaSeVincula() {
		long conCuenta = alumno("REGCON", email("reg-con"), true);
		long cuenta = cuenta(email("reg-con-cuenta"));
		vincularCuenta(cuenta, conCuenta);
		String tokenCon = solicitarRegistro(email("reg-con"), conCuenta);

		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> TenantContext.ejecutarCon(org, () -> registroService.confirmarRegistro(tokenCon)));
		assertEquals("El alumno ya tiene usuario asociado.", error.getMessage());
		assertEquals(0, contar("SELECT COUNT(*) FROM usuario WHERE email = ?", email("reg-con")));
		assertEquals(0, contar("SELECT COUNT(*) FROM registro_pendiente WHERE email = ?", email("reg-con")),
				"la solicitud inservible se borra");
		assertEquals(conCuenta, alumnoDeLaCuenta(cuenta), "la cuenta existente no se toca");

		long sinCuenta = alumno("REGSIN", email("reg-sin"), true);
		String tokenSin = solicitarRegistro(email("reg-sin"), sinCuenta);
		TenantContext.ejecutarCon(org, () -> registroService.confirmarRegistro(tokenSin));
		long nueva = jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", Long.class, email("reg-sin"));
		assertEquals(sinCuenta, alumnoDeLaCuenta(nueva));
		assertEquals(1, contar("SELECT COUNT(*) FROM membresia WHERE usuario_id = ? AND organizacion_id = ? "
				+ "AND rol = 'FAMILIA'", nueva, org));
		assertEquals(1, contar("SELECT COUNT(*) FROM usuario_alumno WHERE usuario_id = ? AND alumno_id = ?", nueva,
				sinCuenta));
		// Y un segundo registro del mismo alumno, ya con cuenta, se rechaza.
		String otraVez = solicitarRegistro(email("reg-sin-2"), sinCuenta);
		assertThrows(IllegalArgumentException.class,
				() -> TenantContext.ejecutarCon(org, () -> registroService.confirmarRegistro(otraVez)));
		assertEquals(0, contar("SELECT COUNT(*) FROM usuario WHERE email = ?", email("reg-sin-2")));
	}

	// ------------------------------------------------------------------ Google

	@Test
	void google_soloVinculaLosAlumnosSinCuenta() {
		String email = email("google");
		long deOtraCuenta = alumno("GOOOTRA", email, true);
		long otraCuenta = cuenta(email("google-otra"));
		vincularCuenta(otraCuenta, deOtraCuenta);
		long libre = alumno("GOOLIBRE", email, true);
		long inactivoLibre = alumno("GOOINACTIVO", email, false);
		long inactivoConCuenta = alumno("GOOINACTIVOCON", email, false);
		long terceraCuenta = cuenta(email("google-tercera"));
		vincularCuenta(terceraCuenta, inactivoConCuenta);

		Usuario creada = TenantContext.calcularCon(org, () -> oauth2.processOAuth2Login(google(email), "google"));

		assertEquals(libre, alumnoDeLaCuenta(creada.getId()), "el primer alumno libre");
		assertEquals(otraCuenta, cuentaDelAlumno(deOtraCuenta));
		assertEquals(terceraCuenta, cuentaDelAlumno(inactivoConCuenta));
		assertEquals(List.of(inactivoLibre, libre).stream().sorted().toList(), jdbc.queryForList(
				"SELECT alumno_id FROM usuario_alumno WHERE usuario_id = ? ORDER BY alumno_id", Long.class,
				creada.getId()), "vínculo de familia solo con los alumnos sin cuenta, también el inactivo");
	}

	@Test
	void google_sinAlumnoLibreSeRechaza() {
		String email = email("google-sin-libre");
		long alumno = alumno("GOOSINLIBRE", email, true);
		vincularCuenta(cuenta(email("google-sin-libre-otra")), alumno);

		assertThrows(IllegalArgumentException.class,
				() -> TenantContext.calcularCon(org, () -> oauth2.processOAuth2Login(google(email), "google")));
		assertEquals(0, contar("SELECT COUNT(*) FROM usuario WHERE email = ?", email));
	}

	// ------------------------------------------------------------------ cambio de email desde la gestión

	@Test
	void cambioDeEmail_conCuentaCambiaElDeLaCuentaYSinCuentaSoloElDelAlumno() {
		long conCuenta = alumno("EMAILCON", email("email-con"), true);
		long cuenta = cuenta(email("email-con"));
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, 'FAMILIA')", cuenta, org);
		vincularCuenta(cuenta, conCuenta);
		long sinCuenta = alumno("EMAILSIN", email("email-sin"), true);

		actualizarEmail(conCuenta, email("email-con-nuevo"));
		assertEquals(email("email-con-nuevo"), emailDelAlumno(conCuenta));
		assertEquals(email("email-con-nuevo"),
				jdbc.queryForObject("SELECT email FROM usuario WHERE id = ?", String.class, cuenta));

		actualizarEmail(sinCuenta, email("email-sin-nuevo"));
		assertEquals(email("email-sin-nuevo"), emailDelAlumno(sinCuenta));
		assertEquals(0, contar("SELECT COUNT(*) FROM usuario WHERE email = ?", email("email-sin-nuevo")));
	}

	@Test
	void cambioDeEmail_deUnaCuentaDeOtraEscuelaSeRechaza() {
		long alumno = alumno("EMAILCOMPARTIDA", email("email-compartida"), true);
		long cuenta = cuenta(email("email-compartida"));
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, 'FAMILIA')", cuenta, org);
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, 'FAMILIA')", cuenta,
				otraEscuela);
		vincularCuenta(cuenta, alumno);

		assertThrows(UsuarioCompartidoException.class, () -> actualizarEmail(alumno, email("email-compartida-nuevo")));
		assertEquals(email("email-compartida"), emailDelAlumno(alumno));
		assertEquals(email("email-compartida"),
				jdbc.queryForObject("SELECT email FROM usuario WHERE id = ?", String.class, cuenta));
	}

	// ------------------------------------------------------------------ borrado del alumno

	@Test
	void borrado_conCuentaSoloDeFamiliaAquiBorraLaCuentaYSinCuentaSigueIgual() throws Exception {
		long conCuenta = alumno("BORRACON", email("borra-con"), true);
		long cuenta = cuenta(email("borra-con-cuenta"));
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, 'FAMILIA')", cuenta, org);
		vincularCuenta(cuenta, conCuenta);
		long sinCuenta = alumno("BORRASIN", email("borra-sin"), true);

		assertEquals(200, estado(delete("/api/alumnos/" + conCuenta)));
		assertEquals(0, contar("SELECT COUNT(*) FROM alumno WHERE id = ?", conCuenta));
		assertEquals(0, contar("SELECT COUNT(*) FROM usuario WHERE id = ?", cuenta),
				"sin ninguna otra membresía, la cuenta se borra");
		assertEquals(0, contar("SELECT COUNT(*) FROM membresia WHERE usuario_id = ?", cuenta));

		assertEquals(200, estado(delete("/api/alumnos/" + sinCuenta)));
		assertEquals(0, contar("SELECT COUNT(*) FROM alumno WHERE id = ?", sinCuenta));
	}

	// ------------------------------------------------------------------ datos

	private long escuela(String prefijo) {
		return TenantContext.calcularCon(ORG1, () -> {
			Organizacion o = new Organizacion();
			o.setSlug(prefijo + sufijo);
			o.setNombre("Escuela " + prefijo + sufijo);
			return organizaciones.save(o).getId();
		});
	}

	private String email(String nombre) {
		return "it-r2p2-" + nombre + "-" + sufijo + "@example.test";
	}

	private long alumno(String nombre, String email, boolean activo) {
		long expediente = jdbc.queryForObject("SELECT COALESCE(MAX(numero_expediente), 80000) + 1 FROM alumno",
				Long.class);
		jdbc.update("""
				INSERT INTO alumno (activo, apellidos, autorizacion_web, direccion, fecha_nacimiento, nombre,
				  numero_expediente, telefono, tiene_derecho_examen, email, organizacion_id)
				VALUES (?, 'PRUEBA R2P2', 1, 'Calle 1', '1990-01-01', ?, ?, '600000000', 0, ?, ?)""",
				activo, nombre + sufijo, expediente, email, org);
		return jdbc.queryForObject("SELECT id FROM alumno WHERE numero_expediente = ? AND organizacion_id = ?",
				Long.class, expediente, org);
	}

	private long cuenta(String email) {
		return TenantContext.calcularCon(ORG1, () -> {
			Usuario u = new Usuario();
			u.setNombre("Cuenta");
			u.setApellidos("R2P2");
			u.setEmail(email);
			u.setContrasena(encoder.encode(CLAVE));
			u.setAuthProvider(AuthProvider.LOCAL);
			return usuarios.save(u).getId();
		});
	}

	private void vincularCuenta(long usuario, long alumno) {
		jdbc.update("UPDATE usuario SET alumno_id = ? WHERE id = ?", alumno, usuario);
	}

	private Long alumnoDeLaCuenta(long usuario) {
		return jdbc.queryForObject("SELECT alumno_id FROM usuario WHERE id = ?", Long.class, usuario);
	}

	private Long cuentaDelAlumno(long alumno) {
		return jdbc.queryForObject("SELECT id FROM usuario WHERE alumno_id = ?", Long.class, alumno);
	}

	private String emailDelAlumno(long alumno) {
		return jdbc.queryForObject("SELECT email FROM alumno WHERE id = ?", String.class, alumno);
	}

	private String solicitarRegistro(String email, long alumnoId) {
		String token = "token-r2p2-" + UUID.randomUUID();
		TenantContext.ejecutarCon(org, () -> {
			RegistroPendiente pendiente = new RegistroPendiente();
			pendiente.setEmail(email);
			pendiente.setAlumnoId(alumnoId);
			pendiente.setPasswordHash(encoder.encode(CLAVE));
			pendiente.setTokenHash(sha256(token));
			pendiente.setTokenExpiresAt(LocalDateTime.now().plusHours(1));
			registrosPendientes.save(pendiente);
		});
		return token;
	}

	private static OAuth2User google(String email) {
		OAuth2User google = mock(OAuth2User.class);
		when(google.getAttribute("email")).thenReturn(email);
		when(google.getAttribute("email_verified")).thenReturn(true);
		return google;
	}

	/** Como la edición desde la gestión (AlumnoService con la sesión abierta, como el controlador). */
	private void actualizarEmail(long alumnoId, String nuevoEmail) {
		TenantContext.ejecutarCon(org, () -> tx.executeWithoutResult(estado -> {
			Alumno alumno = alumnos.findById(alumnoId).orElseThrow();
			AlumnoDTO dto = AlumnoDTO.deAlumno(alumno);
			dto.setEmail(nuevoEmail);
			dto.setTipoTarifa(com.taemoi.project.legado.TipoTarifa.ADULTO.name());
			alumnoService.actualizarAlumno(alumnoId, dto, alumno.getFechaNacimiento(), null);
		}));
	}

	private int contar(String sql, Object... args) {
		return jdbc.queryForObject(sql, Integer.class, args);
	}

	private int estado(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder peticion)
			throws Exception {
		return mvc.perform(peticion.cookie(new Cookie("jwt", tokenAdmin))).andReturn().getResponse().getStatus();
	}

	private JsonNode leer(String ruta) throws Exception {
		MvcResult r = mvc.perform(get(ruta).cookie(new Cookie("jwt", tokenAdmin))).andReturn();
		assertEquals(200, r.getResponse().getStatus(), ruta + ": " + r.getResponse().getContentAsString());
		JsonNode nodo = json.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8));
		assertNotNull(nodo);
		return nodo;
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
