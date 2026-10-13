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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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
 * Informe y corrección de DNIs compartidos por varios alumnos ({@code /api/admin/dni-compartidos}) por
 * HTTP real contra MySQL: reglas de propuesta, aplicación (tutor creado, NIF vaciado, responsable
 * legal), idempotencia, 409 si el grupo cambió, aislamiento por organización, permisos y que el DNI
 * completo nunca sale en respuestas, auditoría ni logs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(OutputCaptureExtension.class)
class DniCompartidoIntegrationTest extends MySqlIntegrationTest {

	private static final long ORG1 = 1L;
	private static final String CLAVE = "Clave-Segura-123";
	private static final String BASE = "/api/admin/dni-compartidos";
	private static final String LETRAS = "TRWAGMYFPDXBNJZSQVHLCKE";

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
	long org2;
	String tokenAdmin;
	String tokenManager;
	String tokenFamilia;
	String tokenAdminOtra;

	@BeforeAll
	void crearEscuelasYUsuarios() throws Exception {
		org2 = TenantContext.calcularCon(ORG1, () -> {
			Organizacion org = new Organizacion();
			org.setSlug("it-dni-" + sufijo);
			org.setNombre("Escuela DNI " + sufijo);
			return organizaciones.save(org).getId();
		});
		Usuario admin = crearUsuario("dni-admin-" + sufijo + "@example.test");
		dar(admin, ORG1, RolMembresia.PROPIETARIO);
		Usuario manager = crearUsuario("dni-manager-" + sufijo + "@example.test");
		dar(manager, ORG1, RolMembresia.ADMINISTRADOR);
		Usuario familia = crearUsuario("dni-familia-" + sufijo + "@example.test");
		alumnoConEmail(ORG1, "FAMILIA", menor(), null, familia.getEmail());
		dar(familia, ORG1, RolMembresia.FAMILIA);
		Usuario adminOtra = crearUsuario("dni-admin-otra-" + sufijo + "@example.test");
		dar(adminOtra, org2, RolMembresia.PROPIETARIO);
		tokenAdmin = login(admin.getEmail());
		tokenManager = login(manager.getEmail());
		tokenFamilia = login(familia.getEmail());
		tokenAdminOtra = login(adminOtra.getEmail());
	}

	@AfterEach
	void sinFugaDeOrganizacion() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
	}

	// ------------------------------------------------------------------ propuestas

	@Test
	void unSoloAdultoEsElTitularYLosMenoresRecibenUnTutor(CapturedOutput salida) throws Exception {
		String nif = nifAleatorio();
		long padre = alumno(ORG1, "PADRE", adulto(), nif);
		long hijo = alumno(ORG1, "HIJO", menor(), nif.toLowerCase());
		long hija = alumno(ORG1, "HIJA", menor(), nif);
		tutorExistente(hija, "Abuela Responsable", null, true);

		JsonNode grupo = grupoCon(tokenAdmin, padre);
		assertEquals("*****" + nif.substring(5), grupo.get("nifEnmascarado").asText());
		assertEquals(3, grupo.get("miembros").size());
		JsonNode miembroPadre = miembro(grupo, padre);
		assertTrue(miembroPadre.get("edad").asInt() >= 18);
		assertTrue(miembroPadre.get("activo").asBoolean());
		assertEquals(1, miembro(grupo, hija).get("tutores").size());

		JsonNode propuesta = grupo.get("propuesta");
		assertEquals("TITULAR_ADULTO", propuesta.get("tipo").asText());
		assertEquals(padre, propuesta.get("titularId").asLong());
		assertEquals("PADRE " + sufijo + " PRUEBA DNI", propuesta.get("nombreTutor").asText());
		assertTrue(propuesta.get("descripcion").asText().contains("PADRE " + sufijo));
		JsonNode cambioHijo = cambio(propuesta, hijo);
		assertTrue(cambioHijo.get("quitarNif").asBoolean());
		assertTrue(cambioHijo.get("crearTutor").asBoolean());
		assertTrue(cambioHijo.get("responsableLegal").asBoolean(), "menor sin responsable");
		assertFalse(cambio(propuesta, hija).get("responsableLegal").asBoolean(), "ya tiene responsable");
		assertFalse(cambio(propuesta, padre).get("quitarNif").asBoolean());

		assertFalse(salida.getAll().contains(nif), "el DNI completo aparece en el log");
	}

	@Test
	void todosMenoresProponenUnTutorNoAlumnoConElNombreConocido() throws Exception {
		String nif = nifAleatorio();
		long a = alumno(ORG1, "MENORA", menor(), nif);
		long b = alumno(ORG1, "MENORB", menor(), nif);
		tutorExistente(a, "Ana Madre", nif, true);

		JsonNode propuesta = grupoCon(tokenAdmin, a).get("propuesta");
		assertEquals("TUTOR_NO_ALUMNO", propuesta.get("tipo").asText());
		assertTrue(propuesta.get("titularId").isNull());
		assertEquals("Ana Madre", propuesta.get("nombreTutor").asText());
		assertTrue(cambio(propuesta, a).get("quitarNif").asBoolean());
		assertFalse(cambio(propuesta, a).get("crearTutor").asBoolean(), "ya tiene un tutor con ese DNI");
		assertTrue(cambio(propuesta, b).get("crearTutor").asBoolean());
		assertTrue(cambio(propuesta, b).get("responsableLegal").asBoolean());
	}

	@Test
	void todosMenoresSinDatosDelTutorProponenPendiente() throws Exception {
		String nif = nifAleatorio();
		long a = alumno(ORG1, "SINDATOSA", menor(), nif);
		alumno(ORG1, "SINDATOSB", menor(), nif);
		JsonNode propuesta = grupoCon(tokenAdmin, a).get("propuesta");
		assertEquals("TUTOR_NO_ALUMNO", propuesta.get("tipo").asText());
		assertEquals("Pendiente", propuesta.get("nombreTutor").asText());
	}

	@Test
	void variosAdultosRequierenRevisionManual() throws Exception {
		String nif = nifAleatorio();
		long a = alumno(ORG1, "ADULTOA", adulto(), nif);
		long b = alumno(ORG1, "ADULTOB", adulto(), nif);
		JsonNode grupo = grupoCon(tokenAdmin, a);
		JsonNode propuesta = grupo.get("propuesta");
		assertEquals("REVISION_MANUAL", propuesta.get("tipo").asText());
		assertEquals(0, propuesta.get("cambios").size());

		// Sin propuesta automática no se puede "aceptar la propuesta"...
		aplicar(tokenAdmin, grupo, "PROPUESTA", null, null).andExpect(status().isBadRequest());
		// ...pero sí elegir explícitamente al titular.
		aplicar(tokenAdmin, grupo, "TITULAR", b, null).andExpect(status().isOk());
		assertEquals(nif, nifDe(b));
		assertNull(nifDe(a));
		Map<String, Object> tutor = jdbc.queryForMap("SELECT * FROM tutor WHERE alumno_id = ?", a);
		assertEquals(nif, tutor.get("nif"));
		assertEquals(Boolean.FALSE, tutor.get("responsable_legal"), "un adulto no necesita responsable legal");
	}

	// ------------------------------------------------------------------ aplicar

	@Test
	void aplicarLaPropuestaCreaTutoresVaciaNifsYEsIdempotente(CapturedOutput salida) throws Exception {
		String nif = nifAleatorio();
		long madre = alumno(ORG1, "MADRE", adulto(), nif);
		long hijo = alumno(ORG1, "HIJOAP", menor(), nif);
		long hija = alumno(ORG1, "HIJAAP", menor(), nif);
		tutorExistente(hija, "Abuelo Responsable", null, true);
		JsonNode grupo = grupoCon(tokenAdmin, madre);
		long eventosAntes = eventosAuditoria();

		JsonNode resultado = leer(aplicar(tokenAdmin, grupo, "PROPUESTA", null, null)
				.andExpect(status().isOk()).andReturn());
		assertTrue(resultado.get("aplicado").asBoolean());
		assertEquals(madre, resultado.get("titularId").asLong());
		assertEquals(2, resultado.get("tutoresCreados").asInt());
		assertFalse(resultado.toString().contains(nif), "el DNI completo sale en la respuesta");

		assertEquals(nif, nifDe(madre));
		assertNull(nifDe(hijo));
		assertNull(nifDe(hija));
		Map<String, Object> tutorHijo = jdbc.queryForMap("SELECT * FROM tutor WHERE alumno_id = ?", hijo);
		assertEquals(nif, tutorHijo.get("nif"));
		assertEquals("MADRE " + sufijo, tutorHijo.get("nombre"));
		assertEquals("PRUEBA DNI", tutorHijo.get("apellidos"));
		assertEquals("TUTOR_LEGAL", tutorHijo.get("parentesco"));
		assertEquals(Boolean.TRUE, tutorHijo.get("responsable_legal"));
		assertEquals(ORG1, ((Number) tutorHijo.get("organizacion_id")).longValue());
		// Campos heredados sincronizados con el nuevo responsable legal.
		assertEquals(nif, jdbc.queryForObject("SELECT responsable_legal_nif FROM alumno WHERE id = ?", String.class, hijo));
		List<Map<String, Object>> tutoresHija = jdbc.queryForList(
				"SELECT * FROM tutor WHERE alumno_id = ? ORDER BY orden, id", hija);
		assertEquals(2, tutoresHija.size());
		assertEquals(Boolean.FALSE, tutoresHija.get(1).get("responsable_legal"), "ya tenía responsable legal");

		// Una entrada de auditoría propia, con el DNI enmascarado y nunca completo.
		Map<String, Object> evento = jdbc.queryForMap("""
				SELECT * FROM auditoria_evento WHERE modulo = 'dni-compartidos' AND id > ? ORDER BY id DESC LIMIT 1
				""", eventosAntes);
		assertTrue(((String) evento.get("resumen")).contains("*****" + nif.substring(5)));
		assertEquals("UPDATE", evento.get("accion"));
		assertEquals(0, jdbc.queryForObject("""
				SELECT COUNT(*) FROM auditoria_evento
				WHERE id > ? AND (payload_json LIKE ? OR resumen LIKE ? OR query_params_json LIKE ?)
				""", Integer.class, eventosAntes, "%" + nif + "%", "%" + nif + "%", "%" + nif + "%"));

		// Repetir la misma petición no cambia nada.
		JsonNode otraVez = leer(aplicar(tokenAdmin, grupo, "PROPUESTA", null, null)
				.andExpect(status().isOk()).andReturn());
		assertFalse(otraVez.get("aplicado").asBoolean());
		assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM tutor WHERE alumno_id = ?", Integer.class, hijo));
		assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM tutor WHERE alumno_id = ?", Integer.class, hija));
		assertEquals(nif, nifDe(madre));
		// El grupo ya no aparece en el informe.
		assertTrue(buscarGrupo(listar(tokenAdmin), madre) == null);

		assertTrue(salida.getAll().contains("Corrección de DNI compartido *****" + nif.substring(5)),
				"la corrección queda en el log, enmascarada");
		assertFalse(salida.getAll().contains(nif), "el DNI completo aparece en el log");
	}

	@Test
	void aplicarSinTitularDejaATodosSinNifConElNombreIndicado() throws Exception {
		String nif = nifAleatorio();
		long a = alumno(ORG1, "SINTITA", menor(), nif);
		long b = alumno(ORG1, "SINTITB", menor(), nif);
		JsonNode grupo = grupoCon(tokenAdmin, a);
		aplicar(tokenAdmin, grupo, "SIN_TITULAR", null, "Luis Padre").andExpect(status().isOk());
		assertNull(nifDe(a));
		assertNull(nifDe(b));
		for (long id : List.of(a, b)) {
			Map<String, Object> t = jdbc.queryForMap("SELECT * FROM tutor WHERE alumno_id = ?", id);
			assertEquals("Luis Padre", t.get("nombre"));
			assertEquals(nif, t.get("nif"));
			assertEquals(Boolean.TRUE, t.get("responsable_legal"));
		}
	}

	@Test
	void siElGrupoCambioDevuelve409YNoToca() throws Exception {
		String nif = nifAleatorio();
		long padre = alumno(ORG1, "PADRE409", adulto(), nif);
		long hijo = alumno(ORG1, "HIJO409", menor(), nif);
		JsonNode grupo = grupoCon(tokenAdmin, padre);
		long nuevo = alumno(ORG1, "NUEVO409", menor(), nif);

		aplicar(tokenAdmin, grupo, "PROPUESTA", null, null).andExpect(status().isConflict());
		assertEquals(nif, nifDe(hijo));
		assertEquals(nif, nifDe(nuevo));
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM tutor WHERE alumno_id = ?", Integer.class, hijo));

		// Enviar un subconjunto del grupo actual también es un cambio.
		aplicarConIds(tokenAdmin, grupo.get("grupo").asText(), List.of(padre, hijo, nuevo, 999_999_999L),
				"PROPUESTA", null, null).andExpect(status().isConflict());
	}

	@Test
	void elTitularElegidoDebeSerDelGrupo() throws Exception {
		String nif = nifAleatorio();
		long a = alumno(ORG1, "TITA", adulto(), nif);
		alumno(ORG1, "TITB", menor(), nif);
		long ajeno = alumno(ORG1, "AJENO", adulto(), nifAleatorio());
		aplicar(tokenAdmin, grupoCon(tokenAdmin, a), "TITULAR", ajeno, null).andExpect(status().isBadRequest());
	}

	// ------------------------------------------------------------------ organización y permisos

	@Test
	void cadaEscuelaSoloVeYCorrigeSusGrupos() throws Exception {
		String nif = nifAleatorio();
		long propio1 = alumno(ORG1, "ORGUNOA", adulto(), nif);
		long propio2 = alumno(ORG1, "ORGUNOB", menor(), nif);
		long otra1 = alumno(org2, "ORGDOSA", adulto(), nif);
		long otra2 = alumno(org2, "ORGDOSB", menor(), nif);

		JsonNode grupo1 = grupoCon(tokenAdmin, propio1);
		assertEquals(2, grupo1.get("miembros").size(), "solo los alumnos de la escuela activa");
		assertNull(buscarGrupo(listar(tokenAdmin), otra1));
		JsonNode grupo2 = grupoCon(tokenAdminOtra, otra1);
		assertEquals(2, grupo2.get("miembros").size());
		assertNull(buscarGrupo(listar(tokenAdminOtra), propio1));

		// La referencia de grupo de otra escuela no sirve aquí, ni con sus ids.
		aplicar(tokenAdminOtra, grupo1, "PROPUESTA", null, null).andExpect(status().isBadRequest());
		aplicarConIds(tokenAdminOtra, grupo2.get("grupo").asText(), List.of(propio1, propio2), "PROPUESTA", null,
				null).andExpect(status().isConflict());
		assertEquals(nif, nifDe(propio2));

		aplicar(tokenAdminOtra, grupo2, "PROPUESTA", null, null).andExpect(status().isOk());
		assertNull(nifDe(otra2));
		assertEquals(nif, nifDe(propio2), "la otra escuela no toca a los alumnos de esta");
	}

	@Test
	void soloAdministradores() throws Exception {
		String nif = nifAleatorio();
		long a = alumno(ORG1, "PERMA", adulto(), nif);
		alumno(ORG1, "PERMB", menor(), nif);
		JsonNode grupo = grupoCon(tokenAdmin, a);
		for (String token : List.of(tokenManager, tokenFamilia)) {
			mvc.perform(get(BASE).cookie(jwt(token))).andExpect(status().isForbidden());
			aplicar(token, grupo, "PROPUESTA", null, null).andExpect(status().isForbidden());
		}
		assertEquals(nif, nifDe(a));
		mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
	}

	// ------------------------------------------------------------------ utilidades

	private org.springframework.test.web.servlet.ResultActions aplicar(String token, JsonNode grupo, String accion,
			Long titularId, String nombreTutor) throws Exception {
		List<Long> ids = new ArrayList<>();
		grupo.get("miembros").forEach(m -> ids.add(m.get("id").asLong()));
		return aplicarConIds(token, grupo.get("grupo").asText(), ids, accion, titularId, nombreTutor);
	}

	private org.springframework.test.web.servlet.ResultActions aplicarConIds(String token, String referencia,
			List<Long> ids, String accion, Long titularId, String nombreTutor) throws Exception {
		Map<String, Object> cuerpo = new java.util.LinkedHashMap<>();
		cuerpo.put("grupo", referencia);
		cuerpo.put("alumnoIds", ids);
		cuerpo.put("accion", accion);
		cuerpo.put("titularId", titularId);
		cuerpo.put("nombreTutor", nombreTutor);
		return mvc.perform(post(BASE + "/aplicar").cookie(jwt(token)).contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(cuerpo)));
	}

	private JsonNode listar(String token) throws Exception {
		MvcResult r = mvc.perform(get(BASE).cookie(jwt(token))).andExpect(status().isOk()).andReturn();
		return leer(r);
	}

	private JsonNode grupoCon(String token, long alumnoId) throws Exception {
		MvcResult r = mvc.perform(get(BASE).cookie(jwt(token))).andExpect(status().isOk()).andReturn();
		String cuerpo = r.getResponse().getContentAsString();
		JsonNode grupo = buscarGrupo(json.readTree(cuerpo), alumnoId);
		assertNotNull(grupo, "grupo del alumno " + alumnoId);
		// Ningún DNI completo en la respuesta (ni de alumnos ni de tutores).
		for (JsonNode m : grupo.get("miembros")) {
			String n = jdbc.queryForObject("SELECT nif FROM alumno WHERE id = ?", String.class, m.get("id").asLong());
			if (n != null) {
				assertFalse(cuerpo.contains(n) || cuerpo.contains(n.toUpperCase()), "DNI completo en la respuesta");
			}
		}
		return grupo;
	}

	private static JsonNode buscarGrupo(JsonNode grupos, long alumnoId) {
		for (JsonNode g : grupos) {
			for (JsonNode m : g.get("miembros")) {
				if (m.get("id").asLong() == alumnoId) {
					return g;
				}
			}
		}
		return null;
	}

	private static JsonNode miembro(JsonNode grupo, long id) {
		for (JsonNode m : grupo.get("miembros")) {
			if (m.get("id").asLong() == id) {
				return m;
			}
		}
		throw new AssertionError("miembro " + id);
	}

	private static JsonNode cambio(JsonNode propuesta, long id) {
		for (JsonNode c : propuesta.get("cambios")) {
			if (c.get("alumnoId").asLong() == id) {
				return c;
			}
		}
		throw new AssertionError("cambio " + id);
	}

	private JsonNode leer(MvcResult r) throws Exception {
		return json.readTree(r.getResponse().getContentAsString());
	}

	private String nifDe(long alumnoId) {
		String n = jdbc.queryForObject("SELECT nif FROM alumno WHERE id = ?", String.class, alumnoId);
		return n == null ? null : n.toUpperCase();
	}

	private long eventosAuditoria() {
		Long max = jdbc.queryForObject("SELECT MAX(id) FROM auditoria_evento", Long.class);
		return max == null ? 0 : max;
	}

	private void tutorExistente(long alumnoId, String nombre, String nif, boolean responsable) {
		long org = jdbc.queryForObject("SELECT organizacion_id FROM alumno WHERE id = ?", Long.class, alumnoId);
		jdbc.update("""
				INSERT INTO tutor (organizacion_id, alumno_id, nombre, nif, parentesco, responsable_legal, orden)
				VALUES (?, ?, ?, ?, 'MADRE', ?, 1)
				""", org, alumnoId, nombre, nif, responsable);
	}

	private static String nifAleatorio() {
		int numero = ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999);
		return numero + String.valueOf(LETRAS.charAt(numero % 23));
	}

	private static LocalDate adulto() {
		return LocalDate.now(ZoneId.of("Europe/Madrid")).minusYears(40);
	}

	private static LocalDate menor() {
		return LocalDate.now(ZoneId.of("Europe/Madrid")).minusYears(10);
	}

	private long alumno(long org, String nombre, LocalDate nacimiento, String nif) {
		return alumnoConEmail(org, nombre, nacimiento, nif, nombre.toLowerCase() + "-" + sufijo + "@example.test");
	}

	private long alumnoConEmail(long org, String nombre, LocalDate nacimiento, String nif, String email) {
		return TenantContext.calcularCon(org, () -> {
			Alumno alumno = new Alumno();
			alumno.setNombre(nombre + " " + sufijo);
			alumno.setApellidos("PRUEBA DNI");
			alumno.setNumeroExpediente(contadores.siguienteNumeroExpediente());
			alumno.setFechaNacimiento(Date.valueOf(nacimiento));
			alumno.setDireccion("Calle Prueba 1");
			alumno.setTelefono("600000000");
			alumno.setActivo(true);
			alumno.setAutorizacionWeb(true);
			alumno.setNif(nif);
			alumno.setEmail(email);
			return alumnos.save(alumno).getId();
		});
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

	private Usuario crearUsuario(String email) {
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

	private Membresia dar(Usuario usuario, Long organizacionId, RolMembresia rol) {
		return TenantContext.calcularCon(ORG1,
				() -> membresias.save(new Membresia(usuario.getId(), organizacionId, rol)));
	}
}
