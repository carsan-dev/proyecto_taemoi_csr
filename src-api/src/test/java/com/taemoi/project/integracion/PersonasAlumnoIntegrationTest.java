package com.taemoi.project.integracion;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.TimeZone;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggerConfiguration;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.cifrado.CifradorDatos;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.AlumnoSalud;
import com.taemoi.project.entities.AuthProvider;
import com.taemoi.project.entities.Membresia;
import com.taemoi.project.entities.Organizacion;
import com.taemoi.project.entities.Preinscripcion;
import com.taemoi.project.entities.RolMembresia;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.AlumnoSaludRepository;
import com.taemoi.project.repositories.ConsentimientoRepository;
import com.taemoi.project.repositories.MembresiaRepository;
import com.taemoi.project.repositories.OrganizacionRepository;
import com.taemoi.project.repositories.PreinscripcionRepository;
import com.taemoi.project.repositories.TutorRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.ConsentimientoService;
import com.taemoi.project.services.ContadorService;

import jakarta.servlet.http.Cookie;

/**
 * V25 por HTTP real (MockMvc + cadena de filtros completa) contra MySQL: tutores y su puente con los
 * campos heredados, salud cifrada y auditada, consentimientos solo-añadir y {@code autorizacion_web},
 * teléfonos como texto, permisos (familia y otra escuela) y aislamiento por organización.
 *
 * <p>Con el SQL y los parámetros enlazados en el log (TRACE) para comprobar que el texto de salud
 * nunca aparece en claro. El nivel TRACE se activa solo mientras corre esta clase y se restaura al
 * terminar: los niveles de Logback son globales a la JVM, así que fijarlos con
 * {@code logging.level.*} en {@code @SpringBootTest} los dejaba activos para las clases que corren
 * después (p. ej. {@code DniCompartidoIntegrationTest}, que comprueba que el DNI no sale en el log).
 */
@SpringBootTest(properties = "spring.jpa.show-sql=true")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(OutputCaptureExtension.class)
class PersonasAlumnoIntegrationTest extends MySqlIntegrationTest {

	private static final long ORG1 = 1L;
	private static final String CLAVE = "Clave-Segura-123";
	private static final List<String> LOGGERS_TRACE = List.of(
			"org.hibernate.orm.jdbc.bind", "org.hibernate.orm.jdbc.extract");
	private static final LoggingSystem LOGGING = LoggingSystem.get(PersonasAlumnoIntegrationTest.class.getClassLoader());
	private final Map<String, LogLevel> nivelesPrevios = new LinkedHashMap<>();

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired PasswordEncoder encoder;
	@Autowired UsuarioRepository usuarios;
	@Autowired MembresiaRepository membresias;
	@Autowired OrganizacionRepository organizaciones;
	@Autowired AlumnoRepository alumnos;
	@Autowired TutorRepository tutores;
	@Autowired AlumnoSaludRepository salud;
	@Autowired ConsentimientoRepository consentimientos;
	@Autowired PreinscripcionRepository preinscripciones;
	@Autowired ConsentimientoService consentimientoService;
	@Autowired CifradorDatos cifrador;
	@Value("${app.documentos.directorio.linux}") String directorioDocumentos;
	@Autowired ContadorService contadores;
	@Autowired TransactionTemplate tx;

	@MockitoBean JavaMailSender mailSender;

	final String sufijo = UUID.randomUUID().toString().substring(0, 8);
	long org2;
	long alumnoFamilia;
	long alumnoTutores;
	long alumnoConsentimientos;
	long alumnoEdicion;
	long alumnoPreinscripcion;
	long alumnoOtraEscuela;
	long alumnoSaludA;
	long alumnoSaludB;
	long alumnoDocumentos;
	long alumnoAutorizacion;
	long alumnoResponsable;
	String tokenAdmin;
	String tokenManager;
	String tokenFamilia;
	String tokenAdminOtra;

	@BeforeAll
	void activarTraceDeParametrosSoloEnEstaClase() {
		for (String logger : LOGGERS_TRACE) {
			LoggerConfiguration actual = LOGGING.getLoggerConfiguration(logger);
			nivelesPrevios.put(logger, actual == null ? null : actual.getConfiguredLevel());
			LOGGING.setLogLevel(logger, LogLevel.TRACE);
		}
	}

	@AfterAll
	void restaurarNivelesDeLog() {
		nivelesPrevios.forEach(LOGGING::setLogLevel);
	}

	@BeforeAll
	void crearEscuelasUsuariosYAlumnos() throws Exception {
		org2 = TenantContext.calcularCon(ORG1, () -> {
			Organizacion org = new Organizacion();
			org.setSlug("it-personas-" + sufijo);
			org.setNombre("Escuela personas " + sufijo);
			return organizaciones.save(org).getId();
		});
		String emailFamilia = "familia-" + sufijo + "@example.test";
		alumnoFamilia = TenantContext.calcularCon(ORG1, () -> crearAlumno("FAMILIA", emailFamilia));
		alumnoTutores = TenantContext.calcularCon(ORG1, () -> crearAlumno("TUTORES", null));
		alumnoConsentimientos = TenantContext.calcularCon(ORG1, () -> crearAlumno("CONSENTIMIENTOS", null));
		alumnoEdicion = TenantContext.calcularCon(ORG1, () -> crearAlumno("EDICION", null));
		alumnoPreinscripcion = TenantContext.calcularCon(ORG1, () -> crearAlumno("PREINSCRIPCION", null));
		alumnoOtraEscuela = TenantContext.calcularCon(org2, () -> crearAlumno("OTRA", null));
		alumnoSaludA = TenantContext.calcularCon(ORG1, () -> crearAlumno("SALUDA", null));
		alumnoSaludB = TenantContext.calcularCon(ORG1, () -> crearAlumno("SALUDB", null));
		alumnoDocumentos = TenantContext.calcularCon(ORG1, () -> crearAlumno("DOCUMENTOS", null));
		alumnoAutorizacion = TenantContext.calcularCon(ORG1, () -> crearAlumno("AUTORIZACION", null));
		alumnoResponsable = TenantContext.calcularCon(ORG1, () -> crearAlumno("RESPONSABLE", null));

		Usuario admin = crearUsuario("admin-" + sufijo + "@example.test");
		dar(admin, ORG1, RolMembresia.PROPIETARIO);
		Usuario manager = crearUsuario("manager-" + sufijo + "@example.test");
		dar(manager, ORG1, RolMembresia.ADMINISTRADOR);
		Usuario familia = crearUsuario(emailFamilia);
		dar(familia, ORG1, RolMembresia.FAMILIA);
		Usuario adminOtra = crearUsuario("admin-otra-" + sufijo + "@example.test");
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

	// ------------------------------------------------------------------ salud

	@Test
	void laSaludSeGuardaCifradaYSeLeeEnClaroPorLaApi(CapturedOutput salida) throws Exception {
		String marcador = "Marcador-clinico-" + sufijo;
		mvc.perform(put("/api/alumnos/" + alumnoFamilia + "/salud").cookie(jwt(tokenAdmin))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"lesionEnfermedad":true,"descripcion":"Rotura de tibia %s","impideActividad":false,
						 "alergias":"Penicilina %s","origen":"FICHA_PAPEL"}
						""".formatted(marcador, marcador)))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", containsString("no-store")))
				.andExpect(jsonPath("$.registrado").value(true));

		Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM alumno_salud WHERE alumno_id = ?", alumnoFamilia);
		for (String columna : List.of("descripcion", "alergias")) {
			String guardado = (String) fila.get(columna);
			assertTrue(guardado.startsWith("v1:"), columna + " cifrada con versión de clave");
			assertFalse(guardado.contains(marcador), columna + " no está en claro");
		}
		assertNull(fila.get("impide_detalle"));
		assertEquals("FICHA_PAPEL", fila.get("origen"));
		assertEquals(ORG1, ((Number) fila.get("organizacion_id")).longValue());

		AlumnoSalud entidad = TenantContext.calcularCon(ORG1, () -> salud.findById(alumnoFamilia).orElseThrow());
		assertTrue(entidad.getDescripcionCifrada().startsWith("v1:"), "la entidad solo guarda el cifrado");
		assertEquals("Rotura de tibia " + marcador, cifrador.descifrar(entidad.getDescripcionCifrada(),
				CifradorDatos.contexto("alumno_salud", "descripcion", alumnoFamilia)));

		mvc.perform(get("/api/alumnos/" + alumnoFamilia + "/salud").cookie(jwt(tokenManager)))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", containsString("no-store")))
				.andExpect(jsonPath("$.descripcion").value("Rotura de tibia " + marcador))
				.andExpect(jsonPath("$.alergias").value("Penicilina " + marcador))
				.andExpect(jsonPath("$.lesionEnfermedad").value(true))
				.andExpect(jsonPath("$.impideActividad").value(false));

		// El SQL y los parámetros enlazados se han registrado (TRACE), pero nunca el texto en claro.
		assertTrue(salida.getAll().contains("alumno_salud"), "el log de SQL está activo en este test");
		assertFalse(salida.getAll().contains(marcador), "el texto de salud aparece en el log");
		// Tampoco en la auditoría (ni en el cuerpo del PUT ni en el resumen).
		assertEquals(0, jdbc.queryForObject("""
				SELECT COUNT(*) FROM auditoria_evento
				WHERE payload_json LIKE ? OR resumen LIKE ? OR query_params_json LIKE ?
				""", Integer.class, "%" + marcador + "%", "%" + marcador + "%", "%" + marcador + "%"));
		assertEquals(1, jdbc.queryForObject("""
				SELECT COUNT(*) FROM auditoria_evento
				WHERE endpoint = ? AND accion = 'UPDATE' AND payload_json = '[payload omitted: datos de salud]'
				""", Integer.class, "/api/alumnos/" + alumnoFamilia + "/salud"));
	}

	@Test
	void cadaLecturaDeSaludQuedaAuditadaConQuienYQueAlumno() throws Exception {
		int antes = lecturasSalud(alumnoTutores);
		mvc.perform(get("/api/alumnos/" + alumnoTutores + "/salud").cookie(jwt(tokenManager)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.registrado").value(false))
				.andExpect(jsonPath("$.descripcion").value(nullValue()));
		mvc.perform(get("/api/alumnos/" + alumnoTutores + "/salud").cookie(jwt(tokenAdmin)))
				.andExpect(status().isOk());
		assertEquals(antes + 2, lecturasSalud(alumnoTutores), "una fila por lectura");
		assertEquals(0, jdbc.queryForObject("""
				SELECT COUNT(*) FROM auditoria_evento
				WHERE endpoint = ? AND accion = 'READ' AND modulo <> 'salud' AND estado_http = 200
				""", Integer.class, "/api/alumnos/" + alumnoTutores + "/salud"), "el filtro no duplica la lectura");

		Map<String, Object> ultima = jdbc.queryForMap("""
				SELECT * FROM auditoria_evento WHERE modulo = 'salud' AND recurso_id = ? AND accion = 'READ'
				ORDER BY id DESC LIMIT 1
				""", alumnoTutores);
		assertEquals("admin-" + sufijo + "@example.test", ultima.get("usuario_email"));
		assertEquals("GET", ultima.get("metodo_http"));
		assertEquals(ORG1, ((Number) ultima.get("organizacion_id")).longValue());
		assertTrue(((String) ultima.get("resumen")).contains("#" + alumnoTutores));
		assertNull(ultima.get("payload_json"));
	}

	// ------------------------------------------------------------------ permisos

	@Test
	void unaFamiliaNoAccedeNiASuPropioAlumno() throws Exception {
		String base = "/api/alumnos/" + alumnoFamilia;
		mvc.perform(get(base + "/salud").cookie(jwt(tokenFamilia))).andExpect(status().isForbidden());
		mvc.perform(put(base + "/salud").cookie(jwt(tokenFamilia)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"alergias\":\"x\"}")).andExpect(status().isForbidden());
		mvc.perform(get(base + "/tutores").cookie(jwt(tokenFamilia))).andExpect(status().isForbidden());
		mvc.perform(post(base + "/tutores").cookie(jwt(tokenFamilia)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"x\"}")).andExpect(status().isForbidden());
		mvc.perform(get(base + "/consentimientos").cookie(jwt(tokenFamilia))).andExpect(status().isForbidden());
		mvc.perform(post(base + "/consentimientos").cookie(jwt(tokenFamilia)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"tipo\":\"USO_IMAGEN\",\"otorgado\":false}")).andExpect(status().isForbidden());
		mvc.perform(get(base + "/salud")).andExpect(status().isUnauthorized());
	}

	@Test
	void otraEscuelaRecibe404YNoSeAuditaNingunaLectura() throws Exception {
		int antes = lecturasSalud(alumnoFamilia);
		String base = "/api/alumnos/" + alumnoFamilia;
		mvc.perform(get(base + "/salud").cookie(jwt(tokenAdminOtra))).andExpect(status().isNotFound());
		mvc.perform(put(base + "/salud").cookie(jwt(tokenAdminOtra)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"alergias\":\"x\"}")).andExpect(status().isNotFound());
		mvc.perform(get(base + "/tutores").cookie(jwt(tokenAdminOtra))).andExpect(status().isNotFound());
		mvc.perform(post(base + "/tutores").cookie(jwt(tokenAdminOtra)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"Intruso\"}")).andExpect(status().isNotFound());
		mvc.perform(get(base + "/consentimientos").cookie(jwt(tokenAdminOtra))).andExpect(status().isNotFound());
		mvc.perform(post(base + "/consentimientos").cookie(jwt(tokenAdminOtra)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"tipo\":\"USO_IMAGEN\",\"otorgado\":false}")).andExpect(status().isNotFound());
		mvc.perform(get("/api/alumnos/" + alumnoOtraEscuela + "/salud").cookie(jwt(tokenAdmin)))
				.andExpect(status().isNotFound());
		assertEquals(antes, lecturasSalud(alumnoFamilia));
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM tutor WHERE nombre = 'Intruso'", Integer.class));
	}

	// ------------------------------------------------------------------ tutores

	@Test
	void losTutoresSeGestionanYElResponsableLegalSeReflejaEnLosCamposHeredados() throws Exception {
		String base = "/api/alumnos/" + alumnoTutores + "/tutores";
		MvcResult creado = mvc.perform(post(base).cookie(jwt(tokenAdmin)).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"nombre":"Lucía","apellidos":"Ruiz Sanz","nif":"12345678z","parentesco":"MADRE",
						 "telefono":"+34 600 111 222","email":"Lucia@Example.test","responsableLegal":true}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.nif").value("12345678Z"))
				.andExpect(jsonPath("$.telefono").value("600111222"))
				.andExpect(jsonPath("$.parentesco").value("MADRE"))
				.andExpect(jsonPath("$.responsableLegal").value(true))
				.andReturn();
		long madre = json.readTree(creado.getResponse().getContentAsString()).get("id").asLong();
		mvc.perform(post(base).cookie(jwt(tokenAdmin)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"Abuelo\",\"parentesco\":\"OTRO\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.responsableLegal").value(false))
				.andExpect(jsonPath("$.orden").value(2));

		assertHeredado(alumnoTutores, "Lucía Ruiz Sanz", "12345678Z");
		mvc.perform(get("/api/alumnos/" + alumnoTutores).cookie(jwt(tokenAdmin)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.responsableLegalNombre").value("Lucía Ruiz Sanz"))
				.andExpect(jsonPath("$.responsableLegalNif").value("12345678Z"));
		mvc.perform(get(base).cookie(jwt(tokenAdmin))).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[0].nombre").value("Lucía"));

		mvc.perform(put(base + "/" + madre).cookie(jwt(tokenAdmin)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"Lucía\",\"apellidos\":\"Ruiz\",\"responsableLegal\":false}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.responsableLegal").value(false));
		assertHeredado(alumnoTutores, null, null);

		mvc.perform(put(base + "/999999").cookie(jwt(tokenAdmin)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"x\"}")).andExpect(status().isNotFound());
		mvc.perform(post(base).cookie(jwt(tokenAdmin)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"  \"}")).andExpect(status().isBadRequest());
		mvc.perform(post(base).cookie(jwt(tokenAdmin)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"Tel\",\"telefono\":\"123\"}")).andExpect(status().isBadRequest());

		mvc.perform(delete(base + "/" + madre).cookie(jwt(tokenAdmin))).andExpect(status().isNoContent());
		mvc.perform(get(base).cookie(jwt(tokenAdmin))).andExpect(jsonPath("$", hasSize(1)));
	}

	@Test
	void editarElAlumnoPorLosCamposHeredadosCreaYActualizaElTutorYAceptaTelefonosNumeroOTexto() throws Exception {
		// Teléfono como número (lo que envía hoy editar-alumno) y el secundario como texto con espacios.
		JsonNode respuesta = editar(alumnoEdicion, """
				"telefono":600222333,"telefono2":"955 111 222","responsableLegalNombre":"Pedro  Gómez",
				"responsableLegalNif":"87654321x","genero":"FEMENINO","localidad":"Casariche","codigoPostal":"41580"
				""");
		assertTrue(respuesta.get("telefono").isTextual(), "la API devuelve el teléfono como texto");
		assertEquals("600222333", respuesta.get("telefono").asText());
		assertEquals("955111222", respuesta.get("telefono2").asText());
		assertEquals("FEMENINO", respuesta.get("genero").asText());
		assertEquals("Casariche", respuesta.get("localidad").asText());
		assertEquals("41580", respuesta.get("codigoPostal").asText());
		assertEquals("Pedro Gómez", respuesta.get("responsableLegalNombre").asText());

		List<Map<String, Object>> filas = jdbc.queryForList("SELECT * FROM tutor WHERE alumno_id = ?", alumnoEdicion);
		assertEquals(1, filas.size());
		assertEquals("Pedro Gómez", filas.get(0).get("nombre"));
		assertEquals("87654321X", filas.get(0).get("nif"));
		assertEquals("TUTOR_LEGAL", filas.get(0).get("parentesco"));
		assertEquals(Boolean.TRUE, filas.get(0).get("responsable_legal"));
		assertHeredado(alumnoEdicion, "Pedro Gómez", "87654321X");
		Map<String, Object> alumno = jdbc.queryForMap("SELECT * FROM alumno WHERE id = ?", alumnoEdicion);
		assertEquals("600222333", alumno.get("telefono"));
		assertEquals("FEMENINO", alumno.get("genero"));

		// El tutor se reparte en nombre y apellidos desde /tutores; guardar la ficha con el mismo nombre
		// completo no lo deshace, y un cambio de NIF se aplica al mismo tutor.
		long tutorId = ((Number) filas.get(0).get("id")).longValue();
		mvc.perform(put("/api/alumnos/" + alumnoEdicion + "/tutores/" + tutorId).cookie(jwt(tokenAdmin))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"Pedro\",\"apellidos\":\"Gómez\",\"nif\":\"87654321X\",\"parentesco\":\"PADRE\"}"))
				.andExpect(status().isOk());
		editar(alumnoEdicion, """
				"telefono":"600222333","responsableLegalNombre":"Pedro Gómez","responsableLegalNif":"11111111H"
				""");
		Map<String, Object> tutor = jdbc.queryForMap("SELECT * FROM tutor WHERE id = ?", tutorId);
		assertEquals("Pedro", tutor.get("nombre"));
		assertEquals("Gómez", tutor.get("apellidos"));
		assertEquals("11111111H", tutor.get("nif"));
		assertEquals("PADRE", tutor.get("parentesco"));
		// Una edición sin los campos nuevos (como la del frontend actual) no los borra.
		Map<String, Object> trasEditar = jdbc.queryForMap("SELECT * FROM alumno WHERE id = ?", alumnoEdicion);
		assertEquals("FEMENINO", trasEditar.get("genero"));
		assertEquals("Casariche", trasEditar.get("localidad"));
		assertEquals("41580", trasEditar.get("codigo_postal"));
		assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM tutor WHERE alumno_id = ?", Integer.class,
				alumnoEdicion));

		// Vaciar el responsable legal quita la marca, sin borrar al tutor.
		JsonNode sinResponsable = editar(alumnoEdicion, "\"telefono\":\"600222333\",\"responsableLegalNombre\":\"\",\"responsableLegalNif\":\"\"");
		assertTrue(sinResponsable.get("responsableLegalNombre").isNull());
		assertEquals(Boolean.FALSE, jdbc.queryForObject("SELECT responsable_legal FROM tutor WHERE id = ?",
				Boolean.class, tutorId));
		assertHeredado(alumnoEdicion, null, null);

		// Teléfono inválido: 400.
		mvc.perform(multipart(HttpMethod.PUT, "/api/alumnos/" + alumnoEdicion).cookie(jwt(tokenAdmin))
				.param("alumnoEditado", cuerpoEdicion("EDICION", "\"telefono\":\"12ab\"")))
				.andExpect(status().isBadRequest());
	}

	// ------------------------------------------------------------------ consentimientos

	@Test
	void losConsentimientosSoloSeAnadenYAutorizacionWebSigueAlUsoDeImagenVigente() throws Exception {
		String base = "/api/alumnos/" + alumnoConsentimientos + "/consentimientos";
		LocalDate hoy = LocalDate.now(ZoneId.of("Europe/Madrid"));
		assertEquals(Boolean.TRUE, autorizacionWeb(alumnoConsentimientos));

		// Manda el ÚLTIMO REGISTRADO, sea cual sea su fecha.
		registrar(base, "{\"tipo\":\"USO_IMAGEN\",\"otorgado\":true,\"fecha\":\"2019-01-01\","
				+ "\"fechaAproximada\":true,\"origen\":\"FICHA_PAPEL\"}");
		assertEquals(Boolean.TRUE, autorizacionWeb(alumnoConsentimientos));
		registrar(base, "{\"tipo\":\"USO_IMAGEN\",\"otorgado\":false,\"fecha\":\"2018-06-01\","
				+ "\"firmanteNombre\":\"Madre\",\"firmanteRelacion\":\"TUTOR\"}");
		assertEquals(Boolean.FALSE, autorizacionWeb(alumnoConsentimientos),
				"una retirada posterior gana aunque su fecha sea anterior");
		registrar(base, "{\"tipo\":\"NORMAS\",\"otorgado\":true,\"fecha\":\"" + hoy + "\"}");
		assertEquals(Boolean.FALSE, autorizacionWeb(alumnoConsentimientos), "otros tipos no cuentan");
		registrar(base, "{\"tipo\":\"USO_IMAGEN\",\"otorgado\":true}");
		assertEquals(Boolean.TRUE, autorizacionWeb(alumnoConsentimientos));
		registrar(base, "{\"tipo\":\"USO_IMAGEN\",\"otorgado\":false,\"fecha\":\"" + hoy + "\"}");
		assertEquals(Boolean.FALSE, autorizacionWeb(alumnoConsentimientos));

		mvc.perform(get(base).cookie(jwt(tokenManager)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(5)))
				.andExpect(jsonPath("$[0].tipo").value("USO_IMAGEN"))
				.andExpect(jsonPath("$[0].otorgado").value(false))
				.andExpect(jsonPath("$[4].fecha[0]").value(2019));

		mvc.perform(post(base).cookie(jwt(tokenAdmin)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"tipo\":\"USO_IMAGEN\",\"otorgado\":true,\"origen\":\"PREINSCRIPCION\"}"))
				.andExpect(status().isBadRequest());
		mvc.perform(post(base).cookie(jwt(tokenAdmin)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"tipo\":\"USO_IMAGEN\",\"otorgado\":true,\"fecha\":\"" + hoy.plusDays(2) + "\"}"))
				.andExpect(status().isBadRequest());
		mvc.perform(post(base).cookie(jwt(tokenAdmin)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"otorgado\":true}"))
				.andExpect(status().isBadRequest());
		mvc.perform(post(base).cookie(jwt(tokenAdmin)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"tipo\":\"NORMAS\",\"otorgado\":true,\"documentoId\":999999}"))
				.andExpect(status().isBadRequest());
		assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM consentimiento WHERE alumno_id = ?", Integer.class,
				alumnoConsentimientos));
	}

	@Test
	void laFinalizacionDeUnaPreinscripcionDejaSusConsentimientosUnaSolaVez() {
		long plantilla = jdbc.queryForObject(
				"SELECT id FROM plantilla_preinscripcion WHERE deporte = 'TAEKWONDO' ORDER BY id LIMIT 1", Long.class);
		String referencia = "IT-PER-" + sufijo;
		jdbc.update("""
				INSERT INTO preinscripcion (referencia, temporada, deporte, estado, nombre, apellidos, dni, identidad_hash,
				  fecha_nacimiento, direccion, telefono, email, consentimiento_fotografico, aceptacion_normas,
				  firmante_nombre, firma, plantilla_id, plantilla_snapshot, pdf_firmado, token_documento_hash,
				  creada_en, actualizada_en, alumno_id, organizacion_id, disciplina_id)
				VALUES (?, '2025-2026', 'TAEKWONDO', 'FINALIZADA', 'Ana', 'Prueba', NULL, ?, '2012-05-05', 'Calle 1',
				  '600000000', 'ana@example.test', 0, 1, 'Madre Firmante', x'00', ?, '{}', x'00', ?,
				  '2025-09-10 12:00:00', '2025-09-10 12:00:00', ?, 1,
				  (SELECT d.id FROM disciplina d WHERE d.organizacion_id = 1 AND d.codigo = 'TAEKWONDO'))
				""", referencia, UUID.randomUUID().toString().replace("-", ""), plantilla, "f".repeat(64),
				alumnoPreinscripcion);

		for (int i = 0; i < 2; i++) {
			TenantContext.ejecutarCon(ORG1, () -> tx.executeWithoutResult(estado -> {
				Preinscripcion p = preinscripciones.findByReferencia(referencia).orElseThrow();
				Alumno a = alumnos.findById(alumnoPreinscripcion).orElseThrow();
				consentimientoService.registrarDesdePreinscripcion(a, p);
			}));
		}

		List<Map<String, Object>> filas = jdbc.queryForList(
				"SELECT * FROM consentimiento WHERE alumno_id = ? ORDER BY tipo", alumnoPreinscripcion);
		assertEquals(2, filas.size(), "idempotente: una fila por tipo y preinscripción");
		Map<String, Object> normas = filas.get(0);
		Map<String, Object> imagen = filas.get(1);
		assertEquals("NORMAS", normas.get("tipo"));
		assertEquals(Boolean.TRUE, normas.get("otorgado"));
		assertEquals("USO_IMAGEN", imagen.get("tipo"));
		assertEquals(Boolean.FALSE, imagen.get("otorgado"));
		assertEquals(Date.valueOf("2025-09-10"), imagen.get("fecha"));
		assertEquals("TUTOR", imagen.get("firmante_relacion"), "menor al firmar");
		assertEquals("Madre Firmante", imagen.get("firmante_nombre"));
		assertEquals("PREINSCRIPCION", imagen.get("origen"));
		assertNotEquals(null, imagen.get("preinscripcion_id"));
		assertEquals(Boolean.FALSE, autorizacionWeb(alumnoPreinscripcion), "sigue al uso de imagen de la preinscripción");
	}

	// ------------------------------------------------------------------ aislamiento

	@Test
	void cadaEscuelaSoloVeSusTutoresSaludYConsentimientos() throws Exception {
		mvc.perform(post("/api/alumnos/" + alumnoOtraEscuela + "/tutores").cookie(jwt(tokenAdminOtra))
				.contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Tutor org2\",\"responsableLegal\":true}"))
				.andExpect(status().isCreated());
		mvc.perform(put("/api/alumnos/" + alumnoOtraEscuela + "/salud").cookie(jwt(tokenAdminOtra))
				.contentType(MediaType.APPLICATION_JSON).content("{\"alergias\":\"Polen\"}"))
				.andExpect(status().isOk());
		mvc.perform(post("/api/alumnos/" + alumnoOtraEscuela + "/consentimientos").cookie(jwt(tokenAdminOtra))
				.contentType(MediaType.APPLICATION_JSON).content("{\"tipo\":\"NORMAS\",\"otorgado\":true}"))
				.andExpect(status().isCreated());

		assertEquals(org2, jdbc.queryForObject("SELECT organizacion_id FROM tutor WHERE alumno_id = ?", Long.class,
				alumnoOtraEscuela));
		assertEquals(org2, jdbc.queryForObject("SELECT organizacion_id FROM alumno_salud WHERE alumno_id = ?",
				Long.class, alumnoOtraEscuela));
		assertEquals(org2, jdbc.queryForObject("SELECT organizacion_id FROM consentimiento WHERE alumno_id = ?",
				Long.class, alumnoOtraEscuela));

		TenantContext.ejecutarCon(ORG1, () -> {
			assertTrue(tutores.findByAlumnoIdOrderByOrdenAscIdAsc(alumnoOtraEscuela).isEmpty());
			assertTrue(salud.findById(alumnoOtraEscuela).isEmpty());
			assertTrue(consentimientos.findByAlumnoIdOrderByIdDesc(alumnoOtraEscuela).isEmpty());
			assertTrue(tutores.findAll().stream().noneMatch(t -> "Tutor org2".equals(t.getNombre())));
		});
		TenantContext.ejecutarCon(org2, () -> {
			assertEquals(1, tutores.findByAlumnoIdOrderByOrdenAscIdAsc(alumnoOtraEscuela).size());
			assertTrue(salud.findById(alumnoOtraEscuela).orElseThrow().getAlergiasCifradas().startsWith("v1:"));
		});
	}

	// ------------------------------------------------------------------ revisión: cifrado ligado a fila/columna

	@Test
	void unCifradoCopiadoAOtraFilaOColumnaNoSeDescifraYLaApiDaUnErrorControlado() throws Exception {
		String marcador = "Marcador-intercambio-" + sufijo;
		for (long id : new long[] { alumnoSaludA, alumnoSaludB }) {
			mvc.perform(put("/api/alumnos/" + id + "/salud").cookie(jwt(tokenAdmin))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"descripcion\":\"Desc " + marcador + " " + id + "\",\"alergias\":\"Alerg " + marcador
							+ "\"}"))
					.andExpect(status().isOk());
		}
		// Fila: la descripción de A se copia en B.
		String descripcionA = jdbc.queryForObject("SELECT descripcion FROM alumno_salud WHERE alumno_id = ?",
				String.class, alumnoSaludA);
		jdbc.update("UPDATE alumno_salud SET descripcion = ? WHERE alumno_id = ?", descripcionA, alumnoSaludB);
		MvcResult filaCambiada = mvc.perform(get("/api/alumnos/" + alumnoSaludB + "/salud").cookie(jwt(tokenAdmin)))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.mensaje").exists())
				.andReturn();
		String cuerpo = filaCambiada.getResponse().getContentAsString();
		assertFalse(cuerpo.contains(marcador), "sin contenido en la respuesta");
		assertFalse(cuerpo.contains("Exception") || cuerpo.contains("at com."), "sin traza en la respuesta");

		// Columna: en A, las alergias se copian en la descripción.
		jdbc.update("UPDATE alumno_salud SET descripcion = alergias WHERE alumno_id = ?", alumnoSaludA);
		mvc.perform(get("/api/alumnos/" + alumnoSaludA + "/salud").cookie(jwt(tokenAdmin)))
				.andExpect(status().isInternalServerError());
		// El intento queda auditado por el filtro (no es 2xx), sin cuerpo.
		assertTrue(jdbc.queryForObject("""
				SELECT COUNT(*) FROM auditoria_evento WHERE endpoint = ? AND accion = 'READ' AND estado_http = 500
				""", Integer.class, "/api/alumnos/" + alumnoSaludA + "/salud") >= 1);
	}

	// ------------------------------------------------------------------ revisión: validación y ruta codificada

	@Test
	void losErroresDeValidacionDeSaludNoRepitenElValor(CapturedOutput salida) throws Exception {
		String marcador = "Marcador-invalido-" + sufijo;
		MvcResult largo = mvc.perform(put("/api/alumnos/" + alumnoSaludA + "/salud").cookie(jwt(tokenAdmin))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"descripcion\":\"" + marcador + "x".repeat(4100) + "\"}"))
				.andExpect(status().isBadRequest())
				.andReturn();
		MvcResult tipo = mvc.perform(put("/api/alumnos/" + alumnoSaludA + "/salud").cookie(jwt(tokenAdmin))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"lesionEnfermedad\":\"" + marcador + "\"}"))
				.andExpect(status().isBadRequest())
				.andReturn();
		assertFalse(largo.getResponse().getContentAsString().contains(marcador));
		assertFalse(tipo.getResponse().getContentAsString().contains(marcador));
		assertFalse(salida.getAll().contains(marcador), "el valor rechazado aparece en el log");
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM auditoria_evento WHERE payload_json LIKE ?",
				Integer.class, "%" + marcador + "%"));
	}

	@Test
	void unaRutaDeSaludCodificadaTampocoGuardaElCuerpoEnLaAuditoria() throws Exception {
		String marcador = "Marcador-codificado-" + sufijo;
		String ruta = "/api/alumnos/" + alumnoSaludB + "/%73alud";
		mvc.perform(put(java.net.URI.create(ruta)).cookie(jwt(tokenAdmin)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"alergias\":\"" + marcador + "\"}"));
		assertEquals(0, jdbc.queryForObject("""
				SELECT COUNT(*) FROM auditoria_evento WHERE payload_json LIKE ? OR resumen LIKE ?
				""", Integer.class, "%" + marcador + "%", "%" + marcador + "%"));
		assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM auditoria_evento WHERE endpoint = ?", Integer.class,
				ruta) >= 1, "la petición se audita (sin cuerpo)");
	}

	// ------------------------------------------------------------------ revisión: documentos y borrado

	@Test
	void unDocumentoConConsentimientoNoSeBorraYElAlumnoSiSePuedeBorrarEntero() throws Exception {
		MvcResult subida = mvc.perform(multipart("/api/alumnos/" + alumnoDocumentos + "/documentos")
				.file(new MockMultipartFile("archivo", "ficha.pdf", "application/pdf", "%PDF-ficha".getBytes()))
				.cookie(jwt(tokenAdmin)))
				.andExpect(status().isOk())
				.andReturn();
		JsonNode documento = json.readTree(subida.getResponse().getContentAsString());
		long documentoId = documento.get("id").asLong();
		Path archivo = Path.of(directorioDocumentos).resolve(documento.get("ruta").asText());
		assertTrue(Files.exists(archivo), archivo.toString());

		registrar("/api/alumnos/" + alumnoDocumentos + "/consentimientos",
				"{\"tipo\":\"USO_IMAGEN\",\"otorgado\":true,\"origen\":\"FICHA_PAPEL\",\"documentoId\":" + documentoId + "}");
		mvc.perform(post("/api/alumnos/" + alumnoDocumentos + "/tutores").cookie(jwt(tokenAdmin))
				.contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Tutor a borrar\"}"))
				.andExpect(status().isCreated());
		mvc.perform(put("/api/alumnos/" + alumnoDocumentos + "/salud").cookie(jwt(tokenAdmin))
				.contentType(MediaType.APPLICATION_JSON).content("{\"alergias\":\"x\"}"))
				.andExpect(status().isOk());

		mvc.perform(delete("/api/alumnos/" + alumnoDocumentos + "/documentos/" + documentoId).cookie(jwt(tokenAdmin)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.mensaje").value(containsString("consentimiento")));
		assertTrue(Files.exists(archivo), "el 409 llega antes de tocar el disco");
		assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM documento WHERE id = ?", Integer.class, documentoId));

		mvc.perform(delete("/api/alumnos/" + alumnoDocumentos).cookie(jwt(tokenAdmin))).andExpect(status().isOk());
		for (String tabla : List.of("alumno", "consentimiento", "tutor", "alumno_salud")) {
			String columna = "alumno".equals(tabla) ? "id" : "alumno_id";
			assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM " + tabla + " WHERE " + columna + " = ?",
					Integer.class, alumnoDocumentos), tabla);
		}
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM documento WHERE id = ?", Integer.class, documentoId));
	}

	// ------------------------------------------------------------------ revisión: autorizacionWeb de la ficha

	@Test
	void cambiarAutorizacionWebEnLaFichaRegistraUnConsentimientoYManda() throws Exception {
		editarComo(alumnoAutorizacion, "AUTORIZACION", "\"telefono\":\"600000000\",\"autorizacionWeb\":true");
		assertEquals(0, contarUsoImagen(alumnoAutorizacion), "sin cambio no hay consentimiento");

		editarComo(alumnoAutorizacion, "AUTORIZACION", "\"telefono\":\"600000000\",\"autorizacionWeb\":false");
		Map<String, Object> retirada = jdbc.queryForMap(
				"SELECT * FROM consentimiento WHERE alumno_id = ? ORDER BY id DESC LIMIT 1", alumnoAutorizacion);
		assertEquals("USO_IMAGEN", retirada.get("tipo"));
		assertEquals(Boolean.FALSE, retirada.get("otorgado"));
		assertEquals("APP", retirada.get("origen"));
		assertEquals("Usuario Prueba", retirada.get("firmante_nombre"), "firma el usuario de la escuela");
		assertNull(retirada.get("firmante_relacion"));
		assertEquals(Date.valueOf(LocalDate.now(ZoneId.of("Europe/Madrid"))), retirada.get("fecha"));
		assertEquals(Boolean.FALSE, autorizacionWeb(alumnoAutorizacion));

		// Registrar después un consentimiento (aunque con fecha antigua) manda: es el último registrado.
		registrar("/api/alumnos/" + alumnoAutorizacion + "/consentimientos",
				"{\"tipo\":\"USO_IMAGEN\",\"otorgado\":true,\"fecha\":\"2019-01-01\"}");
		assertEquals(Boolean.TRUE, autorizacionWeb(alumnoAutorizacion));
		// Y una retirada posterior desde la ficha no se deshace.
		editarComo(alumnoAutorizacion, "AUTORIZACION", "\"telefono\":\"600000000\",\"autorizacionWeb\":false");
		assertEquals(Boolean.FALSE, autorizacionWeb(alumnoAutorizacion));
		assertEquals(3, contarUsoImagen(alumnoAutorizacion));
	}

	@Test
	void elAltaPorLaFichaRegistraElConsentimientoDeImagen() throws Exception {
		String email = "alta-" + sufijo + "@example.test";
		String nuevo = """
				{"nombre":"ALTA","apellidos":"PERSONAS","fechaNacimiento":"2012-03-04","direccion":"Calle 2",
				 "telefono":"600 000 009","email":"%s","autorizacionWeb":false,"tieneDiscapacidad":false,
				 "deportesInicial":[{"deporte":"PILATES","fechaAlta":"2024-09-01","tipoTarifa":"PILATES",
				   "cuantiaTarifa":25.0,"competidor":false,"tieneLicencia":false}]}""".formatted(email);
		MvcResult r = mvc.perform(multipart("/api/alumnos/crear").param("nuevo", nuevo).cookie(jwt(tokenAdmin)))
				.andExpect(status().isCreated())
				.andReturn();
		long id = json.readTree(r.getResponse().getContentAsString()).get("id").asLong();
		assertEquals("600000009", jdbc.queryForObject("SELECT telefono FROM alumno WHERE id = ?", String.class, id));
		Map<String, Object> c = jdbc.queryForMap("SELECT * FROM consentimiento WHERE alumno_id = ?", id);
		assertEquals("USO_IMAGEN", c.get("tipo"));
		assertEquals(Boolean.FALSE, c.get("otorgado"));
		assertEquals("APP", c.get("origen"));
		assertEquals("Usuario Prueba", c.get("firmante_nombre"));
	}

	// ------------------------------------------------------------------ revisión: responsable legal ausente

	@Test
	void unaEdicionSinResponsableLegalNoTocaAlTutor() throws Exception {
		editarComo(alumnoResponsable, "RESPONSABLE",
				"\"telefono\":\"600000000\",\"responsableLegalNombre\":\"Rosa Díaz\",\"responsableLegalNif\":\"22222222J\"");
		editarComo(alumnoResponsable, "RESPONSABLE", "\"telefono\":\"600000000\"");
		assertEquals(Boolean.TRUE, jdbc.queryForObject(
				"SELECT responsable_legal FROM tutor WHERE alumno_id = ?", Boolean.class, alumnoResponsable));
		assertHeredado(alumnoResponsable, "Rosa Díaz", "22222222J");
		editarComo(alumnoResponsable, "RESPONSABLE", "\"telefono\":\"600000000\",\"responsableLegalNombre\":null");
		assertHeredado(alumnoResponsable, "Rosa Díaz", "22222222J");
		editarComo(alumnoResponsable, "RESPONSABLE", "\"telefono\":\"600000000\",\"responsableLegalNombre\":\"\"");
		assertEquals(Boolean.FALSE, jdbc.queryForObject(
				"SELECT responsable_legal FROM tutor WHERE alumno_id = ?", Boolean.class, alumnoResponsable));
	}

	// ------------------------------------------------------------------ revisión: zona horaria de los Instant

	@Test
	void losInstantesSeGuardanEnUtc() throws Exception {
		mvc.perform(put("/api/alumnos/" + alumnoSaludB + "/salud").cookie(jwt(tokenAdmin))
				.contentType(MediaType.APPLICATION_JSON).content("{\"lesionEnfermedad\":false}"))
				.andExpect(status().isOk());
		LocalDateTime guardado = jdbc.queryForObject("SELECT actualizado_en FROM alumno_salud WHERE alumno_id = ?",
				LocalDateTime.class, alumnoSaludB);
		LocalDateTime utc = LocalDateTime.now(ZoneOffset.UTC);
		assertTrue(Duration.between(guardado, utc).abs().toMinutes() < 2, "DATETIME en UTC: " + guardado + " vs "
				+ utc + " (zona JVM " + TimeZone.getDefault().getID() + ")");
	}

	// ------------------------------------------------------------------ utilidades

	private void editarComo(long alumnoId, String nombre, String camposExtra) throws Exception {
		String extra = camposExtra.contains("autorizacionWeb") ? camposExtra : camposExtra + ",\"autorizacionWeb\":true";
		mvc.perform(multipart(HttpMethod.PUT, "/api/alumnos/" + alumnoId).cookie(jwt(tokenAdmin))
				.param("alumnoEditado", """
						{"nombre":"%s %s","apellidos":"PRUEBA PERSONAS","fechaNacimiento":"2012-05-05",
						 "direccion":"Calle Prueba 1","email":"%s-%s@example.test",
						 "tipoTarifa":"INFANTIL","fechaAlta":"2020-09-01",%s}
						""".formatted(nombre, sufijo, nombre.toLowerCase(), sufijo, extra)))
				.andExpect(status().isOk());
	}

	private int contarUsoImagen(long alumnoId) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM consentimiento WHERE alumno_id = ? AND tipo = 'USO_IMAGEN'",
				Integer.class, alumnoId);
	}

	private JsonNode editar(long alumnoId, String camposExtra) throws Exception {
		MvcResult r = mvc.perform(multipart(HttpMethod.PUT, "/api/alumnos/" + alumnoId).cookie(jwt(tokenAdmin))
				.param("alumnoEditado", cuerpoEdicion("EDICION", camposExtra)))
				.andExpect(status().isOk())
				.andReturn();
		return json.readTree(r.getResponse().getContentAsString());
	}

	private String cuerpoEdicion(String nombre, String camposExtra) {
		return """
				{"nombre":"%s %s","apellidos":"PRUEBA PERSONAS","fechaNacimiento":"2012-05-05",
				 "direccion":"Calle Prueba 1","email":"edicion-%s@example.test","autorizacionWeb":true,
				 "tipoTarifa":"INFANTIL","fechaAlta":"2020-09-01",%s}
				""".formatted(nombre, sufijo, sufijo, camposExtra);
	}

	private void registrar(String base, String cuerpo) throws Exception {
		mvc.perform(post(base).cookie(jwt(tokenAdmin)).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
				.andExpect(status().isCreated());
	}

	private Boolean autorizacionWeb(long alumnoId) {
		return jdbc.queryForObject("SELECT autorizacion_web FROM alumno WHERE id = ?", Boolean.class, alumnoId);
	}

	private void assertHeredado(long alumnoId, String nombre, String nif) {
		Map<String, Object> a = jdbc.queryForMap(
				"SELECT responsable_legal_nombre, responsable_legal_nif FROM alumno WHERE id = ?", alumnoId);
		assertEquals(nombre, a.get("responsable_legal_nombre"));
		assertEquals(nif, a.get("responsable_legal_nif"));
	}

	private int lecturasSalud(long alumnoId) {
		return jdbc.queryForObject("""
				SELECT COUNT(*) FROM auditoria_evento
				WHERE recurso_id = ? AND accion = 'READ' AND modulo = 'salud' AND endpoint = ?
				""", Integer.class, alumnoId, "/api/alumnos/" + alumnoId + "/salud");
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

	private long crearAlumno(String nombre, String email) {
		Alumno alumno = new Alumno();
		alumno.setNombre(nombre + " " + sufijo);
		alumno.setApellidos("PRUEBA PERSONAS");
		alumno.setNumeroExpediente(contadores.siguienteNumeroExpediente());
		alumno.setFechaNacimiento(Date.valueOf(LocalDate.of(2012, 5, 5)));
		alumno.setDireccion("Calle Prueba 1");
		alumno.setTelefono("600000000");
		alumno.setActivo(true);
		alumno.setAutorizacionWeb(true);
		alumno.setEmail(email != null ? email : nombre.toLowerCase() + "-" + sufijo + "@example.test");
		return alumnos.save(alumno).getId();
	}
}
