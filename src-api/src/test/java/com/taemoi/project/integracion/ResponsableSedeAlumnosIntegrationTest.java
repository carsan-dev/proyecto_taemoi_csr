package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.DisciplinaRepository;
import com.taemoi.project.repositories.TarifaRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.GrupoService;
import com.taemoi.project.services.JwtService;

import jakarta.servlet.http.Cookie;

/**
 * Entrega 6-1 (entrega6-plan.md §3.1, §4.1) contra MySQL: el responsable de sede gestiona los alumnos de SUS sedes.
 * Listados, ids, contadores y aptos filtrados por D9; lectura por id por D9 (D6-1); edición solo por la sede principal
 * (D6-3); alta con sede obligatoria y suya (D6-2); borrado solo gestión (D6-4); asignaciones solo con alumnos de su
 * alcance y grupos de sus sedes (D6-6, D6-16). Las cuentas son FAMILIA + RESPONSABLE_SEDE (la única forma de entrar
 * hasta la entrega 9, D6-15). Datos sintéticos en una escuela nueva ({@link EscuelaConSedesDePrueba}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ResponsableSedeAlumnosIntegrationTest extends MySqlIntegrationTest {

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;
	@Autowired DisciplinaRepository disciplinas;
	@Autowired TarifaRepository tarifas;

	@MockitoBean JavaMailSender mailSender;

	EscuelaConSedesDePrueba e;
	String tokenA;
	String tokenAC;
	String tokenGestor;

	@BeforeAll
	void preparar() {
		e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-e6-alumnos");
		TenantContext.ejecutarCon(e.org, () -> DisciplinasDePrueba.tarifa(tarifas, "ADULTO", "30.00", null));
		tokenA = e.token(jwtService, usuarios, e.respA);
		tokenAC = e.token(jwtService, usuarios, e.respAC);
		tokenGestor = e.token(jwtService, usuarios, e.gestor);
	}

	@AfterEach
	void sinFugaDeOrganizacion() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
	}

	// ------------------------------------------------------------------ listados

	@Test
	void listado_soloAlumnosDeSusSedes() throws Exception {
		assertEquals(Set.of(e.aA, e.aGrupoA, e.aSinSede), conjunto(ids(leer("/api/alumnos", tokenA))),
				"D9: sede principal A, o grupo de A (también sin sede principal, D6-1)");
		assertEquals(Set.of(e.aA, e.aGrupoA, e.aSinSede, e.aInactivoA),
				conjunto(ids(leer("/api/alumnos?incluirInactivos=true", tokenA))));
		JsonNode pagina = leer("/api/alumnos?page=1&size=2&incluirInactivos=true", tokenA);
		assertEquals(4, pagina.get("totalElements").asInt());
		assertEquals(2, pagina.get("content").size());
		assertEquals(Set.of(e.aA, e.aGrupoA, e.aSinSede),
				conjunto(ids(leer("/api/alumnos/con-deportes", tokenA))));
		assertEquals(Set.of(e.aA), conjunto(ids(leer("/api/alumnos?aptoParaExamen=true", tokenA))));

		// La gestión ve toda la escuela.
		Set<Long> gestion = conjunto(ids(leer("/api/alumnos?incluirInactivos=true", tokenGestor)));
		assertTrue(gestion.containsAll(Set.of(e.aA, e.aGrupoA, e.aSinSede, e.aB, e.aNada, e.aInactivoA)));
	}

	@Test
	void ids_losDelListadoEnElMismoOrden() throws Exception {
		for (String incluir : List.of("false", "true")) {
			for (String token : List.of(tokenA, tokenAC, tokenGestor)) {
				List<Long> listado = ids(leer("/api/alumnos?incluirInactivos=" + incluir, token));
				List<Long> ids = numeros(leer("/api/alumnos/ids?incluirInactivos=" + incluir, token));
				assertEquals(listado, ids, "incluirInactivos=" + incluir);
			}
		}
		List<Long> deA = numeros(leer("/api/alumnos/ids?incluirInactivos=true", tokenA));
		assertEquals(List.of(e.aA, e.aGrupoA, e.aSinSede, e.aInactivoA), deA, "por id ascendente");
	}

	@Test
	void contadorYDistribucion_soloSusAlumnos() throws Exception {
		assertEquals(4, leer("/api/alumnos/count", tokenA).asLong(), "D9, activos e inactivos, como la gestión");
		JsonNode distribucion = leer("/api/alumnos/deportes/distribucion", tokenA);
		assertEquals(2, distribucion.get("TAEKWONDO").asLong(), "aA y aGrupoA; no aB");
		assertEquals(1, distribucion.size());
		assertEquals(3, leer("/api/alumnos/deportes/distribucion", tokenGestor).get("TAEKWONDO").asLong());
	}

	@Test
	void aptos_soloSusAlumnos() throws Exception {
		assertEquals(Set.of(e.aA), conjunto(ids(leer("/api/alumnos/aptos", tokenA))));
		assertEquals(Set.of(e.aA), conjunto(ids(leer("/api/alumnos/aptos/deporte?deporte=TAEKWONDO", tokenA))));
		assertEquals(Set.of(e.aA),
				conjunto(ids(leer("/api/alumnos/aptos/convocatoria?deporte=TAEKWONDO", tokenA))));
		assertEquals(200, estado(get("/api/alumnos/aptos/" + e.aA), tokenA));
		assertEquals(403, estado(get("/api/alumnos/aptos/" + e.aB), tokenA));
		assertEquals(Set.of(e.aA, e.aB), conjunto(ids(leer("/api/alumnos/aptos", tokenGestor))));
	}

	// ------------------------------------------------------------------ por id: leer D9, editar sede principal

	@Test
	void porId_leerPorD9_editarSoloLaSedePrincipal() throws Exception {
		for (long visible : List.of(e.aA, e.aGrupoA, e.aSinSede)) {
			assertEquals(200, estado(get("/api/alumnos/" + visible), tokenA), "leer " + visible);
			assertEquals(200, estado(get("/api/alumnos/" + visible + "/completo"), tokenA));
			assertEquals(200, estado(get("/api/alumnos/" + visible + "/tutores"), tokenA));
			assertEquals(200, estado(get("/api/alumnos/" + visible + "/grupos"), tokenA));
		}
		for (long ajeno : List.of(e.aB, e.aNada)) {
			MvcResult r = peticion(get("/api/alumnos/" + ajeno), tokenA);
			assertEquals(403, r.getResponse().getStatus(), "ajeno " + ajeno);
			assertTrue(r.getResponse().getContentAsString().contains("Acceso denegado"));
			assertEquals(403, estado(get("/api/alumnos/" + ajeno + "/grupos"), tokenA));
			assertEquals(403, estado(get("/api/alumnos/" + ajeno + "/tutores"), tokenA));
		}

		String observaciones = "{\"observaciones\":\"Nota IT\"}";
		assertEquals(200, estado(put("/api/alumnos/" + e.aA + "/observaciones")
				.contentType(MediaType.APPLICATION_JSON).content(observaciones), tokenA));
		assertEquals(403, estado(put("/api/alumnos/" + e.aGrupoA + "/observaciones")
				.contentType(MediaType.APPLICATION_JSON).content(observaciones), tokenA),
				"D6-3: sede principal B, solo lectura para A");
		assertEquals(403, estado(put("/api/alumnos/" + e.aSinSede + "/observaciones")
				.contentType(MediaType.APPLICATION_JSON).content(observaciones), tokenA),
				"D6-3: sin sede principal, solo lectura");
		assertEquals(403, estado(put("/api/alumnos/" + e.aB + "/observaciones")
				.contentType(MediaType.APPLICATION_JSON).content(observaciones), tokenA));
		assertEquals(e.sA, e.sedeDe(e.aA), "editar la ficha conserva la sede principal");
	}

	/** Al final: crea alumnos en la sede A (cambiarían los recuentos exactos de los demás tests). */
	@Test
	@Order(Integer.MAX_VALUE)
	void bajaYAltaSi_borradoDefinitivoNo() throws Exception {
		long alumno = e.alumno("E6BAJA" + e.marca, e.sA, true);
		assertEquals(200, estado(put("/api/alumnos/" + alumno + "/baja"), tokenA));
		assertEquals(200, estado(put("/api/alumnos/" + alumno + "/alta"), tokenA));
		assertEquals(403, estado(put("/api/alumnos/" + e.aB + "/baja"), tokenA));
		assertEquals(403, estado(delete("/api/alumnos/" + alumno), tokenA), "D6-4: borrar es de gestión");
		assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM alumno WHERE id = ?", Integer.class, alumno));
		assertEquals(e.sA, e.sedeDe(alumno), "baja y alta conservan la sede principal");
	}

	// ------------------------------------------------------------------ alta (D6-2)

	/** Al final: crea alumnos en las sedes A y B (cambiarían los recuentos exactos de los demás tests). */
	@Test
	@Order(Integer.MAX_VALUE)
	void alta_sedePrincipalObligatoriaYSuya() throws Exception {
		// Una sede propia: automática.
		MvcResult conUna = alta(tokenA, null);
		assertEquals(201, conUna.getResponse().getStatus(), conUna.getResponse().getContentAsString());
		assertEquals(e.sA, e.sedeDe(idDe(conUna)));
		assertFalse(conUna.getResponse().getContentAsString().contains("sedePrincipal"), "solo de entrada");

		// Varias sedes propias: obligatoria (400); una suya, 201; una ajena, 403 (no un 500).
		assertEquals(400, alta(tokenAC, null).getResponse().getStatus());
		MvcResult ajena = alta(tokenAC, e.sB);
		assertEquals(403, ajena.getResponse().getStatus());
		assertTrue(ajena.getResponse().getContentAsString().contains("Acceso denegado"),
				ajena.getResponse().getContentAsString());
		MvcResult suya = alta(tokenAC, e.sA);
		assertEquals(201, suya.getResponse().getStatus(), suya.getResponse().getContentAsString());
		assertEquals(e.sA, e.sedeDe(idDe(suya)));
		assertEquals(400, alta(tokenA, 999_999_999L).getResponse().getStatus(), "sede que no existe");

		// Gestión en una escuela con varias sedes: opcional (NULL como hasta ahora) o la indicada.
		MvcResult gestionSinSede = alta(tokenGestor, null);
		assertEquals(201, gestionSinSede.getResponse().getStatus(), gestionSinSede.getResponse().getContentAsString());
		assertNull(e.sedeDe(idDe(gestionSinSede)));
		MvcResult gestionConSede = alta(tokenGestor, e.sB);
		assertEquals(201, gestionConSede.getResponse().getStatus());
		assertEquals(e.sB, e.sedeDe(idDe(gestionConSede)));

		// D6-18: nadie da altas en una sede inactiva (400), tampoco la gestión.
		int alumnosAntes = jdbc.queryForObject("SELECT COUNT(*) FROM alumno WHERE organizacion_id = ?", Integer.class,
				e.org);
		assertEquals(400, alta(tokenAC, e.sC).getResponse().getStatus(), "su sede C, inactiva");
		assertEquals(400, alta(tokenGestor, e.sC).getResponse().getStatus(), "también la gestión");
		assertEquals(alumnosAntes, jdbc.queryForObject("SELECT COUNT(*) FROM alumno WHERE organizacion_id = ?",
				Integer.class, e.org));
	}

	// ------------------------------------------------------------------ email de la cuenta (revisión 6-1)

	/**
	 * El email de la ficha de un alumno con cuenta es el email de acceso de esa cuenta. El responsable no lo cambia
	 * (403, la cuenta conserva su email: si no, podría quedarse con una cuenta de gestión por Google o por «olvidé la
	 * contraseña»). La gestión lo sigue cambiando como hasta ahora.
	 */
	@Test
	@Order(Integer.MAX_VALUE)
	void email_elResponsableNoCambiaElDeUnaCuenta_laGestionSi() throws Exception {
		String emailCuenta = "it-e6-cuenta-" + e.marca + "@example.test";
		long alumno = e.alumno("E6CUENTA" + e.marca, e.sA, true);
		jdbc.update("UPDATE alumno SET email = ? WHERE id = ?", emailCuenta, alumno);
		long disciplina = jdbc.queryForObject(
				"SELECT id FROM disciplina WHERE organizacion_id = ? AND codigo = 'TAEKWONDO'", Long.class, e.org);
		jdbc.update("""
				INSERT INTO alumno_deporte (activo, apto_para_examen, alumno_id, competidor, principal, tiene_licencia,
				  organizacion_id, fecha_alta, fecha_alta_inicial, disciplina_id)
				VALUES (FALSE, 0, ?, 0, 1, 0, ?, '2023-09-01', '2023-09-01', ?)""", alumno, e.org, disciplina);
		long cuenta = TenantContext.calcularCon(EscuelaConSedesDePrueba.ORG1, () -> {
			com.taemoi.project.entities.Usuario u = new com.taemoi.project.entities.Usuario();
			u.setNombre("Gestor");
			u.setApellidos("Vinculado");
			u.setEmail(emailCuenta);
			u.setContrasena("fake-Password-123");
			u.setAuthProvider(com.taemoi.project.entities.AuthProvider.LOCAL);
			return usuarios.save(u).getId();
		});
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, 'ADMINISTRADOR')", cuenta,
				e.org);
		jdbc.update("UPDATE usuario SET alumno_id = ? WHERE id = ?", alumno, cuenta);

		String ajeno = "it-e6-responsable-" + e.marca + "@example.test";
		MvcResult cambio = peticion(editar(alumno, ajeno, "Calle 2"), tokenA);
		assertEquals(403, cambio.getResponse().getStatus(), cambio.getResponse().getContentAsString());
		assertEquals(emailCuenta, emailDeCuenta(cuenta), "la cuenta conserva su email");
		assertEquals(emailCuenta, jdbc.queryForObject("SELECT email FROM alumno WHERE id = ?", String.class, alumno));
		assertEquals("Calle 1", jdbc.queryForObject("SELECT direccion FROM alumno WHERE id = ?", String.class, alumno),
				"el 403 no deja cambios a medias");

		// Sin tocar el email, el responsable sí edita la ficha; la cuenta no se toca.
		MvcResult sinEmail = peticion(editar(alumno, emailCuenta, "Calle 3"), tokenA);
		assertEquals(200, sinEmail.getResponse().getStatus(), sinEmail.getResponse().getContentAsString());
		assertEquals(emailCuenta, emailDeCuenta(cuenta));

		// La gestión: como hasta ahora, cambia el email de la ficha y el de la cuenta.
		String nuevo = "it-e6-gestion-" + e.marca + "@example.test";
		MvcResult gestion = peticion(editar(alumno, nuevo, "Calle 4"), tokenGestor);
		assertEquals(200, gestion.getResponse().getStatus(), gestion.getResponse().getContentAsString());
		assertEquals(nuevo, emailDeCuenta(cuenta));
		jdbc.update("UPDATE membresia SET activa = FALSE WHERE usuario_id = ?", cuenta);
	}

	private MockHttpServletRequestBuilder editar(long alumno, String email, String direccion) {
		String cuerpo = """
				{"nombre":"E6CUENTA%s","apellidos":"PRUEBA","fechaNacimiento":"2010-01-01","direccion":"%s",
				 "email":"%s","telefono":"600000000","autorizacionWeb":true}""".formatted(e.marca, direccion, email);
		return multipart(org.springframework.http.HttpMethod.PUT, "/api/alumnos/" + alumno).param("alumnoEditado",
				cuerpo);
	}

	private String emailDeCuenta(long cuenta) {
		return jdbc.queryForObject("SELECT email FROM usuario WHERE id = ?", String.class, cuenta);
	}

	// ------------------------------------------------------------------ asignaciones (D6-6, D6-16)

	@Test
	void asignaciones_alumnoDeSuAlcanceYGrupoDeSusSedes() throws Exception {
		assertEquals(403, estado(post("/api/alumnos/" + e.aB + "/grupos/" + e.gA), tokenA),
				"D6-6: no adopta alumnos de otra sede");
		assertEquals(403, estado(post("/api/alumnos/" + e.aA + "/grupos/" + e.gB), tokenA), "grupo de otra sede");
		assertEquals(403, estado(post("/api/alumnos/" + e.aNada + "/grupos/" + e.gA), tokenA));
		assertEquals(200, estado(post("/api/alumnos/" + e.aA + "/grupos/" + e.gA2), tokenA));
		assertEquals(200, estado(delete("/api/alumnos/" + e.aA + "/grupos/" + e.gA2), tokenA));

		// D6-16: ve todos los grupos de aGrupoA (gA y gB), pero solo cambia los de sus sedes.
		Set<Long> grupos = conjunto(ids(leer("/api/alumnos/" + e.aGrupoA + "/grupos", tokenA)));
		assertEquals(Set.of(e.gA, e.gB), grupos);
		assertEquals(403, estado(delete("/api/alumnos/" + e.aGrupoA + "/grupos/" + e.gB), tokenA));
		assertEquals(403, estado(delete("/api/alumnos/" + e.aB + "/turnos/" + e.tB), tokenA));

		// Lista en el cuerpo: un alumno fuera del alcance deniega todo (y no se añade nada).
		assertEquals(403, estado(post("/api/grupos/" + e.gA2 + "/alumnos").contentType(MediaType.APPLICATION_JSON)
				.content("[" + e.aA + "," + e.aB + "]"), tokenA));
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM alumno_grupo WHERE grupo_id = ?", Integer.class,
				e.gA2));
		assertEquals(200, estado(post("/api/grupos/" + e.gA2 + "/alumnos").contentType(MediaType.APPLICATION_JSON)
				.content("[" + e.aA + "," + e.aSinSede + "]"), tokenA));
		assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM alumno_grupo WHERE grupo_id = ?", Integer.class,
				e.gA2));
		jdbc.update("DELETE FROM alumno_grupo WHERE grupo_id = ?", e.gA2);

		// Alumno de su sede en un grupo de otra sede: lo edita (sede principal A) y ve ese grupo, pero no lo cambia.
		jdbc.update("INSERT INTO alumno_grupo (grupo_id, alumno_id) VALUES (?, ?)", e.gB, e.aA);
		try {
			assertTrue(conjunto(ids(leer("/api/alumnos/" + e.aA + "/grupos", tokenA))).contains(e.gB));
			assertEquals(403, estado(delete("/api/alumnos/" + e.aA + "/grupos/" + e.gB), tokenA));
			assertEquals(403, estado(delete("/api/grupos/" + e.gB + "/alumnos/" + e.aA), tokenA));
			assertEquals(200, estado(put("/api/alumnos/" + e.aA + "/observaciones")
					.contentType(MediaType.APPLICATION_JSON).content("{\"observaciones\":\"Otra sede\"}"), tokenA));
		} finally {
			jdbc.update("DELETE FROM alumno_grupo WHERE grupo_id = ? AND alumno_id = ?", e.gB, e.aA);
		}
	}

	// ------------------------------------------------------------------ cuenta FAMILIA + RESPONSABLE_SEDE

	@Test
	void cuentaFamiliaYResponsable_familiaComoAntesYSedePorLaApi() throws Exception {
		// Su alumno de familia: rutas de familia sí; rutas de gestión no (no es de su sede).
		assertEquals(200, estado(get("/api/alumnos/" + e.propioA + "/grupos"), tokenA));
		assertEquals(403, estado(get("/api/alumnos/" + e.propioA), tokenA));
		assertFalse(ids(leer("/api/alumnos", tokenA)).contains(e.propioA));
		// El portal de familia (reto diario, material de examen) no se abre por sede.
		assertEquals(403, estado(get("/api/alumnos/" + e.aA + "/reto-diario"), tokenA));
	}

	@Test
	void otraEscuela_invisibleAunqueSuSedeEsteEnMembresiaSede() throws Exception {
		long umbrete = jdbc.queryForObject("SELECT id FROM sede WHERE organizacion_id = 1 ORDER BY id LIMIT 1",
				Long.class);
		long expediente = jdbc.queryForObject("SELECT COALESCE(MAX(numero_expediente), 60000) + 1 FROM alumno",
				Long.class);
		jdbc.update("""
				INSERT INTO alumno (activo, apellidos, autorizacion_web, direccion, fecha_nacimiento, nombre,
				  numero_expediente, telefono, tiene_derecho_examen, organizacion_id, sede_principal_id)
				VALUES (0, 'PRUEBA', 1, 'Calle 1', '2010-01-01', 'E6 OTRA ESCUELA', ?, '600000000', 0, 1, ?)""",
				expediente, umbrete);
		long deMoiskimdo = jdbc.queryForObject(
				"SELECT id FROM alumno WHERE organizacion_id = 1 AND numero_expediente = ?", Long.class, expediente);
		long membresia = jdbc.queryForObject("""
				SELECT id FROM membresia WHERE usuario_id = ? AND organizacion_id = ? AND rol = 'RESPONSABLE_SEDE'""",
				Long.class, e.respA, e.org);
		jdbc.update("INSERT INTO membresia_sede (membresia_id, sede_id) VALUES (?, ?)", membresia, umbrete);
		try {
			assertEquals(403, estado(get("/api/alumnos/" + deMoiskimdo), tokenA));
			assertEquals(Set.of(e.aA, e.aGrupoA, e.aSinSede), conjunto(ids(leer("/api/alumnos", tokenA))));
		} finally {
			jdbc.update("DELETE FROM membresia_sede WHERE membresia_id = ? AND sede_id = ?", membresia, umbrete);
		}
	}

	// ------------------------------------------------------------------ utilidades

	private MvcResult alta(String token, Long sede) throws Exception {
		String cuerpo = """
				{"nombre":"ALTA","apellidos":"RESPONSABLE %s","fechaNacimiento":"1990-03-04","direccion":"Calle 1",
				 "telefono":600000009,"autorizacionWeb":true%s,
				 "deportesInicial":[{"deporte":"TAEKWONDO","fechaAlta":"2024-09-01","tipoTarifa":"ADULTO",
				   "cuantiaTarifa":30.0,"competidor":false,"tieneLicencia":false}]}"""
				.formatted(e.marca, sede == null ? "" : ",\"sedePrincipalId\":" + sede);
		return peticion(multipart("/api/alumnos/crear").param("nuevo", cuerpo), token);
	}

	private long idDe(MvcResult resultado) throws Exception {
		return json.readTree(resultado.getResponse().getContentAsString()).get("id").asLong();
	}

	private int estado(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		return peticion(peticion, token).getResponse().getStatus();
	}

	private MvcResult peticion(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		return mvc.perform(peticion.cookie(new Cookie("jwt", token))).andReturn();
	}

	private JsonNode leer(String ruta, String token) throws Exception {
		MvcResult r = peticion(get(ruta), token);
		assertEquals(200, r.getResponse().getStatus(), ruta + ": " + r.getResponse().getContentAsString());
		return json.readTree(r.getResponse().getContentAsString());
	}

	private static List<Long> ids(JsonNode lista) {
		List<Long> ids = new ArrayList<>();
		lista.forEach(n -> ids.add(n.get("id").asLong()));
		return ids;
	}

	private static List<Long> numeros(JsonNode lista) {
		List<Long> ids = new ArrayList<>();
		lista.forEach(n -> ids.add(n.asLong()));
		return ids;
	}

	private static Set<Long> conjunto(List<Long> lista) {
		Set<Long> conjunto = new HashSet<>(lista);
		assertEquals(lista.size(), conjunto.size(), "sin repetidos: " + lista);
		return conjunto;
	}
}
