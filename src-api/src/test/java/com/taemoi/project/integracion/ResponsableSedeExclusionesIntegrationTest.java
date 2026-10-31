package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.acceso.GestionPorSede;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.GrupoService;
import com.taemoi.project.services.JwtService;

import jakarta.servlet.http.Cookie;

/**
 * Pieza 9-5 contra MySQL: lo que las decisiones 370, 372, 369 y 96 dejan fuera del responsable de sede sigue fuera con
 * cuerpos válidos y para las tres sesiones que tiene: R, R + Pr y R + F. Cada caso compara una foto de la base antes y
 * después: un 403 que hubiera escrito algo no pasaría. Escuela sintética de {@link EscuelaConSedesDePrueba}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ResponsableSedeExclusionesIntegrationTest extends MySqlIntegrationTest {

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;

	@MockitoBean JavaMailSender mailSender;

	EscuelaConSedesDePrueba e;
	long producto;
	long responsableYProfesor;
	long aConEmail;
	long aSinEmail;
	String emailFicha;
	Map<String, String> tokens;
	Map<String, Long> cuentas;

	@BeforeAll
	void preparar() {
		e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-95-exclusiones");
		e.crearResponsablesSolos(usuarios);
		responsableYProfesor = e.cuenta(usuarios, "r-pr");
		e.responsable(responsableYProfesor, e.sA);
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, 'PROFESOR')",
				responsableYProfesor, e.org);
		jdbc.update("INSERT INTO asignacion_profesor (organizacion_id, usuario_id, grupo_id, rol) "
				+ "VALUES (?, ?, ?, 'TITULAR')", e.org, responsableYProfesor, e.gA);
		cuentas = Map.of("R", e.respSoloA, "R + Pr", responsableYProfesor, "R + F", e.respA);
		tokens = new LinkedHashMap<>();
		cuentas.forEach((perfil, cuenta) -> tokens.put(perfil, e.token(jwtService, usuarios, cuenta)));

		jdbc.update("INSERT INTO producto (concepto, precio, codigo, organizacion_id) VALUES ('CAMISETA 95', 9.0, NULL, ?)",
				e.org);
		producto = jdbc.queryForObject("SELECT MAX(id) FROM producto WHERE organizacion_id = ?", Long.class, e.org);
		emailFicha = "it-96-ficha-" + e.marca + "@example.test";
		aConEmail = alumnoConDeporte("E96CONEMAIL");
		jdbc.update("UPDATE alumno SET email = ? WHERE id = ?", emailFicha, aConEmail);
		aSinEmail = alumnoConDeporte("E96SINEMAIL");
	}

	@AfterEach
	void sinFugaDeOrganizacion() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
	}

	Stream<String> perfiles() {
		return Stream.of("R", "R + Pr", "R + F");
	}

	@ParameterizedTest
	@MethodSource("perfiles")
	void decision370_eventosProductosConfiguracionSedesYMiembros(String perfil) throws Exception {
		long cuenta = cuentas.get(perfil);
		noEscribe(perfil, List.of(
				// Eventos
				multipart("/api/eventos/crear").param("nuevo",
						"{\"titulo\":\"Evento IT\",\"descripcion\":\"Texto\",\"fechaEvento\":\"2026-11-01\"}"),
				delete("/api/eventos/1"),
				// Productos
				conJson(post("/api/productos"), "{\"concepto\":\"NUEVO IT\",\"precio\":9.0}"),
				conJson(put("/api/productos/" + producto), "{\"concepto\":\"CAMBIADO IT\",\"precio\":1.0}"),
				delete("/api/productos/" + producto),
				conJson(patch("/api/productos/" + producto + "/vendible-profesor"), "{\"vendibleProfesor\":true}"),
				post("/api/productos-alumno/mensualidades/general").contentType(MediaType.TEXT_PLAIN)
						.content("MENSUALIDAD ENERO 2027"),
				// Configuración
				conJson(put("/api/admin/configuracion/limite-turno"), "{\"limite\":30}"),
				conJson(post("/api/tarifas-sede"), "{\"codigoTarifa\":\"ADULTO\",\"sedeId\":" + e.sA
						+ ",\"importe\":40.00,\"vigenteDesde\":\"2027-01\"}"),
				// Sedes
				conJson(post("/api/sedes"), "{\"nombre\":\"Sede nueva IT\"}"),
				conJson(put("/api/sedes/" + e.sA), "{\"nombre\":\"Sede renombrada IT\"}"),
				conJson(put("/api/sedes/" + e.sA + "/activa"), "{\"activa\":false}"),
				// Miembros: ni darse a sí mismo otra sede ni otro rol
				conJson(post("/api/miembros"), "{\"email\":\"nuevo-" + e.marca + "@example.test\",\"rol\":\"FAMILIA\","
						+ "\"nombre\":\"Nuevo\",\"apellidos\":\"Miembro\"}"),
				conJson(put("/api/miembros/" + cuenta + "/sedes"), "{\"sedeIds\":[" + e.sA + "," + e.sB + "]}"),
				conJson(put("/api/miembros/" + cuenta + "/roles/ADMINISTRADOR"), "{}")));
	}

	@ParameterizedTest
	@MethodSource("perfiles")
	void decision372_noAsignaNiQuitaProfesores(String perfil) throws Exception {
		String email = "it-e6-r-pr-" + e.marca + "@example.test";
		noEscribe(perfil, List.of(
				get("/api/grupos/" + e.gA + "/profesores"),
				conJson(post("/api/grupos/" + e.gA2 + "/profesores"), "{\"email\":\"" + email + "\",\"rol\":\"TITULAR\"}"),
				delete("/api/grupos/" + e.gA + "/profesores/" + responsableYProfesor)));
	}

	@ParameterizedTest
	@MethodSource("perfiles")
	void decision369_noCambiaDeSedeAUnAlumno(String perfil) throws Exception {
		noEscribe(perfil, List.of(
				get("/api/alumnos/" + e.aA + "/cambio-sede?sedeId=" + e.sB),
				conJson(put("/api/alumnos/" + e.aA + "/sede"), "{\"sedeId\":" + e.sB + "}")));
	}

	@ParameterizedTest
	@MethodSource("perfiles")
	void decision96_noCambiaNiPoneElEmailDeUnAlumnoSinCuenta(String perfil) throws Exception {
		Map<String, Object> antes = foto();
		for (long alumno : List.of(aConEmail, aSinEmail)) {
			MvcResult r = peticion(editar(alumno, "it-96-otro-" + e.marca + "@example.test"), tokens.get(perfil));

			assertEquals(403, r.getResponse().getStatus(), r.getResponse().getContentAsString());
			assertEquals(GestionPorSede.EMAIL_SOLO_GESTION, json
					.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("mensaje").asText());
		}
		assertEquals(antes, foto());
	}

	// ------------------------------------------------------------------ utilidades

	/** Cada ruta da 403 con esa sesión y la base queda exactamente igual. */
	private void noEscribe(String perfil, List<MockHttpServletRequestBuilder> rutas) throws Exception {
		Map<String, Object> antes = foto();
		for (MockHttpServletRequestBuilder ruta : rutas) {
			MvcResult r = peticion(ruta, tokens.get(perfil));
			assertEquals(403, r.getResponse().getStatus(),
					perfil + ": " + r.getRequest().getMethod() + " " + r.getRequest().getRequestURI());
		}
		assertEquals(antes, foto());
	}

	private Map<String, Object> foto() {
		Map<String, Object> foto = new LinkedHashMap<>();
		foto.put("producto", filas("SELECT * FROM producto WHERE organizacion_id = ? ORDER BY id"));
		foto.put("sede", filas("SELECT * FROM sede WHERE organizacion_id = ? ORDER BY id"));
		foto.put("membresia", filas("SELECT * FROM membresia WHERE organizacion_id = ? ORDER BY id"));
		foto.put("membresia_sede", filas("""
				SELECT ms.* FROM membresia_sede ms JOIN membresia m ON m.id = ms.membresia_id
				WHERE m.organizacion_id = ? ORDER BY ms.membresia_id, ms.sede_id"""));
		foto.put("asignacion_profesor", filas(
				"SELECT * FROM asignacion_profesor WHERE organizacion_id = ? ORDER BY usuario_id, grupo_id"));
		foto.put("tarifa_sede", filas("""
				SELECT ts.* FROM tarifa_sede ts JOIN sede s ON s.id = ts.sede_id
				WHERE s.organizacion_id = ? ORDER BY ts.id"""));
		foto.put("evento", filas("SELECT id FROM evento WHERE organizacion_id = ? ORDER BY id"));
		foto.put("alumno", filas("""
				SELECT id, nombre, email, direccion, activo, sede_principal_id FROM alumno
				WHERE organizacion_id = ? ORDER BY id"""));
		foto.put("producto_alumno", filas("SELECT * FROM producto_alumno WHERE organizacion_id = ? ORDER BY id"));
		return foto;
	}

	private List<Map<String, Object>> filas(String sql) {
		return jdbc.queryForList(sql, e.org);
	}

	private long alumnoConDeporte(String nombre) {
		long alumno = e.alumno(nombre + e.marca, e.sA, true);
		e.taekwondo(alumno, false);
		return alumno;
	}

	private MockHttpServletRequestBuilder conJson(MockHttpServletRequestBuilder peticion, String cuerpo) {
		return peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
	}

	private MockHttpServletRequestBuilder editar(long alumno, String email) {
		String cuerpo = """
				{"nombre":"E96%s","apellidos":"PRUEBA","fechaNacimiento":"2010-01-01","direccion":"Calle 2",
				 "email":"%s","telefono":"600000000","autorizacionWeb":true}""".formatted(e.marca, email);
		return multipart(HttpMethod.PUT, "/api/alumnos/" + alumno).param("alumnoEditado", cuerpo);
	}

	private MvcResult peticion(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		return mvc.perform(peticion.cookie(new Cookie("jwt", token))).andReturn();
	}
}
