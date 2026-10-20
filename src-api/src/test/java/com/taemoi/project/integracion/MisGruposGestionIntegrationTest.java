package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.GrupoService;
import com.taemoi.project.services.JwtService;

import jakarta.servlet.http.Cookie;

/**
 * Pieza E8-1 contra MySQL (decisión 217, D8-15): «Mis grupos» para propietario y gestor que además son profesores.
 * <ul>
 * <li>P+Pr y G+Pr usan {@code /api/profesor/**} con SUS grupos ({@code alcance.grupos()}) y nada más: el alcance total
 * no abre ningún otro grupo ni alumno (403 sin revelar si existe); sin asignaciones, lista vacía.</li>
 * <li>La salud y los pases van por los mismos servicios que el profesor: un evento de salud con la ruta del profesor y
 * una sola fila {@code PROFESOR} por pase (decisión 214).</li>
 * <li>P y G sin PROFESOR siguen fuera (las columnas GESTOR y PROPIETARIO de la matriz no cambian) y Mi caja no cambia:
 * P+Pr con grupos sigue con la caja vacía y sin cobrar por sus rutas.</li>
 * </ul>
 * Escuela sintética ({@link EscuelaConSedesDePrueba}, con un sistema de grados y productos de grado propios) y otra para
 * el aislamiento. Datos inventados; un alumno nuevo en los tests que escriben, así que el orden no importa.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MisGruposGestionIntegrationTest extends MySqlIntegrationTest {

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;

	@MockitoBean JavaMailSender mailSender;

	EscuelaConSedesDePrueba e;
	EscuelaConSedesDePrueba otra;
	long propProf, gestorProf, gestorProfSinGrupos, propietario;
	long gAmarillo, gRojo;
	String tPropProf, tGestorProf, tGestorProfSinGrupos, tPropietario, tGestor;

	@BeforeAll
	void preparar() {
		e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-e8-1");
		otra = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-e8-1-otra");
		sistemaDeGrados();
		productoDeGrado("EXAMEN_ROJO", "EXAMEN", 25.0);
		productoDeGrado("RECOMPENSA_ROJO", "RECOMPENSA", 15.0);

		propProf = e.cuenta(usuarios, "propprof");
		membresia(e, propProf, "PROPIETARIO");
		membresia(e, propProf, "PROFESOR");
		asignar(e, propProf, e.gA, "TITULAR");
		gestorProf = e.cuenta(usuarios, "gestorprof");
		membresia(e, gestorProf, "ADMINISTRADOR");
		membresia(e, gestorProf, "PROFESOR");
		asignar(e, gestorProf, e.gB, "AYUDANTE");
		gestorProfSinGrupos = e.cuenta(usuarios, "gestorprofsin");
		membresia(e, gestorProfSinGrupos, "ADMINISTRADOR");
		membresia(e, gestorProfSinGrupos, "PROFESOR");
		propietario = e.cuenta(usuarios, "propietario");
		membresia(e, propietario, "PROPIETARIO");
		// Un profesor de la otra escuela en su gA: nada suyo se ve desde esta.
		long profOtra = otra.cuenta(usuarios, "profotra");
		membresia(otra, profOtra, "PROFESOR");
		asignar(otra, profOtra, otra.gA, "TITULAR");

		tPropProf = e.token(jwtService, usuarios, propProf);
		tGestorProf = e.token(jwtService, usuarios, gestorProf);
		tGestorProfSinGrupos = e.token(jwtService, usuarios, gestorProfSinGrupos);
		tPropietario = e.token(jwtService, usuarios, propietario);
		tGestor = e.token(jwtService, usuarios, e.gestor);
	}

	@AfterEach
	void sinFugas() {
		assertFalse(TenantContext.get() != null, "El test dejó una organización fijada en el hilo");
		SecurityContextHolder.clearContext();
	}

	// ------------------------------------------------------------------ solo sus grupos

	@Test
	void propietarioProfesor_soloSusGrupos() throws Exception {
		JsonNode grupos = leer(get("/api/profesor/grupos"), tPropProf, 200);
		assertEquals(Set.of(e.gA), ids(grupos, "id"), "solo gA, aunque vea toda la escuela por gestión: " + grupos);
		assertEquals("TITULAR", grupos.get(0).get("rol").asText());
		assertEquals(200, estado(head("/api/profesor/grupos"), tPropProf));

		Set<Long> deGA = ids(leer(get("/api/profesor/grupos/" + e.gA + "/alumnos"), tPropProf, 200), "id");
		assertTrue(deGA.containsAll(Set.of(e.aGrupoA, e.aSinSede)), deGA.toString());
		assertFalse(deGA.contains(e.aB) || deGA.contains(e.aA), deGA.toString());
		for (long ajeno : List.of(e.gB, e.gA2, e.gSin, e.gC, otra.gA)) {
			assertEquals(403, estado(get("/api/profesor/grupos/" + ajeno + "/alumnos"), tPropProf), "grupo " + ajeno);
		}

		JsonNode ficha = leer(get("/api/profesor/alumnos/" + e.aGrupoA), tPropProf, 200);
		assertEquals(List.of(e.gA), idsEnOrden(ficha.get("grupos"), "id"), "solo los grupos que comparte: " + ficha);
		assertEquals(200, estado(get("/api/profesor/alumnos/" + e.aSinSede), tPropProf));
		assertEquals(403, estado(get("/api/profesor/alumnos/" + e.aB), tPropProf), "aB solo está en gB");
		assertEquals(403, estado(get("/api/profesor/alumnos/" + e.aA), tPropProf), "aA es de la sede A, sin grupo");

		assertEquals(200, estado(get("/api/profesor/grupos/" + e.gA + "/asistencia?year=2026&month=9"), tPropProf));
		assertEquals(403, estado(get("/api/profesor/grupos/" + e.gB + "/asistencia?year=2026&month=9"), tPropProf));
	}

	@Test
	void gestorProfesor_alumnoDeOtroGrupo_403() throws Exception {
		assertEquals(Set.of(e.gB), ids(leer(get("/api/profesor/grupos"), tGestorProf, 200), "id"));
		assertEquals(200, estado(get("/api/profesor/alumnos/" + e.aB), tGestorProf));
		for (long alumno : List.of(e.aSinSede, e.aA, e.aNada, e.aInactivoA, otra.aGrupoA, otra.aB, 999_999_999L)) {
			assertEquals(403, estado(get("/api/profesor/alumnos/" + alumno), tGestorProf), "alumno " + alumno);
			assertEquals(403, estado(get("/api/profesor/alumnos/" + alumno + "/salud"), tGestorProf), "salud " + alumno);
			assertEquals(403, estado(put("/api/profesor/alumnos/" + alumno + "/deportes/TAEKWONDO/apto")
					.contentType(MediaType.APPLICATION_JSON).content("{\"aptoParaExamen\":false}"), tGestorProf),
					"apto " + alumno);
		}
		assertEquals(403, estado(get("/api/profesor/grupos/" + e.gA + "/alumnos"), tGestorProf));
		assertEquals(403, estado(get("/api/profesor/grupos/" + otra.gA + "/alumnos"), tGestorProf), "otra escuela");
		assertEquals(403, estado(get("/api/profesor/grupos/999999999/alumnos"), tGestorProf));
		assertTrue(aptoDe(e.aA), "el apto no cambió");
	}

	@Test
	void gestionProfesorSinAsignaciones_listaVacia() throws Exception {
		assertEquals(0, leer(get("/api/profesor/grupos"), tGestorProfSinGrupos, 200).size());
		assertEquals(403, estado(get("/api/profesor/grupos/" + e.gA + "/alumnos"), tGestorProfSinGrupos));
		assertEquals(403, estado(get("/api/profesor/grupos/" + e.gA + "/asistencia?year=2026&month=9"),
				tGestorProfSinGrupos));
		assertEquals(403, estado(get("/api/profesor/alumnos/" + e.aGrupoA), tGestorProfSinGrupos));
		assertEquals(403, estado(get("/api/profesor/alumnos/" + e.aGrupoA + "/salud"), tGestorProfSinGrupos));
	}

	@Test
	void gestionSinProfesor_sigueDenegada() throws Exception {
		long a = alumno("E81SINPROF", e.gA, e.sA, gAmarillo);
		for (String token : List.of(tPropietario, tGestor)) {
			assertEquals(403, estado(get("/api/profesor/grupos"), token));
			assertEquals(403, estado(get("/api/profesor/grupos/" + e.gA + "/alumnos"), token));
			assertEquals(403, estado(get("/api/profesor/alumnos/" + a), token));
			assertEquals(403, estado(get("/api/profesor/alumnos/" + a + "/salud"), token));
			assertEquals(403, estado(pase(a, "pase-examen"), token));
		}
		assertTrue(cargos(a).isEmpty());
	}

	// ------------------------------------------------------------------ salud y pases, como el profesor

	@Test
	void gestionProfesor_saludRegistradaIgual() throws Exception {
		long a = alumno("E81SALUD", e.gA, e.sA, gAmarillo);
		String ruta = "/api/profesor/alumnos/" + a + "/salud";
		long antes = eventosSalud(a, propProf);
		long antesRuta = eventosConEndpoint(ruta);

		MvcResult r = peticion(get(ruta), tPropProf);
		assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		assertTrue(r.getResponse().getHeader("Cache-Control").contains("no-store"));

		assertEquals(antes + 1, eventosSalud(a, propProf), "un evento de salud con su usuario");
		assertEquals(antesRuta + 1, eventosConEndpoint(ruta), "uno solo: el filtro HTTP no duplica la lectura");
		Map<String, Object> evento = jdbc.queryForMap("""
				SELECT accion, endpoint, modulo, estado_http, resumen, organizacion_id FROM auditoria_evento
				WHERE id = (SELECT MAX(id) FROM auditoria_evento WHERE modulo = 'salud' AND recurso_id = ? AND usuario_id = ?)""",
				a, propProf);
		assertEquals("READ", evento.get("accion"));
		assertEquals(ruta, evento.get("endpoint"));
		assertEquals(200, ((Number) evento.get("estado_http")).intValue());
		assertEquals(e.org, ((Number) evento.get("organizacion_id")).longValue());
		assertEquals("Lectura de datos de salud del alumno #" + a + " (profesor)", evento.get("resumen"),
				"el mismo texto que la lectura del profesor");

		// Fuera de sus grupos: 403 y sin evento de salud.
		long fuera = alumno("E81SALUDFUERA", e.gB, e.sB, gAmarillo);
		assertEquals(403, estado(get("/api/profesor/alumnos/" + fuera + "/salud"), tPropProf));
		assertEquals(0, eventosSalud(fuera, propProf));
	}

	@Test
	void gestionProfesor_paseRegistraUnaFila() throws Exception {
		long a = alumno("E81PASE", e.gB, e.sB, gAmarillo);
		long antes = ultimoEvento();

		MvcResult r = peticion(pase(a, "pase-examen"), tGestorProf);
		assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());

		List<Map<String, Object>> filas = jdbc.queryForList(
				"SELECT * FROM auditoria_evento WHERE id > ? AND organizacion_id = ? ORDER BY id", antes, e.org);
		assertEquals(1, filas.size(), "una sola fila, la explícita del profesor: " + filas);
		Map<String, Object> fila = filas.get(0);
		assertTrue(((String) fila.get("resumen")).startsWith("PROFESOR PASE_EXAMEN "), fila.toString());
		assertEquals("profesor", fila.get("modulo"));
		assertEquals(gestorProf, ((Number) fila.get("usuario_id")).longValue());
		assertEquals(1, cargos(a).size(), "el cargo del pase, pendiente de cobro");
		assertEquals(gAmarillo, gradoDe(a), "el grado sube al cobrar, no ahora (D7-6)");

		// El propietario-profesor no da clase en gB: 403 y nada nuevo.
		assertEquals(403, estado(pase(a, "pase-recompensa"), tPropProf));
		assertEquals(1, cargos(a).size());
	}

	// ------------------------------------------------------------------ Mi caja no cambia

	@Test
	void cajaProfesorSinCambios_gestionConProfesor() throws Exception {
		long a = alumno("E81CAJA", e.gA, e.sA, gAmarillo);
		for (String token : List.of(tPropProf, tGestorProf)) {
			assertEquals(0, leer(get("/api/caja/profesor/alumnos"), token, 200).size(), "sin alumnos en Mi caja");
			assertEquals(0, leer(get("/api/caja/profesor/cargos-pendientes"), token, 200).size(),
					"sin cargos por cobrar en Mi caja");
		}
		assertEquals(403, estado(post("/api/caja/profesor/alumnos/" + a + "/productos/999999999/cobrar")
				.contentType(MediaType.APPLICATION_JSON).content("{}"), tPropProf), "no vende por la ruta del profesor");
		assertEquals(403, estado(post("/api/caja/profesor/alumnos/" + a + "/encargos")
				.contentType(MediaType.APPLICATION_JSON).content("{}"), tPropProf));
	}

	/**
	 * Un cargo real pendiente de un alumno de SU grupo (el pase que acaba de crear por «Mis grupos»): por la ruta de
	 * cobro de Mi caja, 403 y el cargo sigue sin pagar y sin cobro. Lo cobra gestión por la tesorería, como hoy.
	 */
	@Test
	void cajaProfesor_cargoPendienteDeSuGrupo_cobrar403() throws Exception {
		for (Object[] caso : List.of(new Object[] { tPropProf, e.gA, e.sA, "E81COBRARP" },
				new Object[] { tGestorProf, e.gB, e.sB, "E81COBRARG" })) {
			String token = (String) caso[0];
			long a = alumno((String) caso[3], (Long) caso[1], (Long) caso[2], gAmarillo);
			MvcResult pase = peticion(pase(a, "pase-examen"), token);
			assertEquals(200, pase.getResponse().getStatus(), pase.getResponse().getContentAsString());
			List<Map<String, Object>> cargos = cargos(a);
			assertEquals(1, cargos.size(), cargos.toString());
			long cargo = ((Number) cargos.get(0).get("id")).longValue();

			assertEquals(403, estado(post("/api/caja/profesor/cargos/" + cargo + "/cobrar"), token),
					"Mi caja no cobra para P/G+Pr");

			assertFalse(Boolean.TRUE.equals(jdbc.queryForObject("SELECT pagado FROM producto_alumno WHERE id = ?",
					Boolean.class, cargo)), "el cargo sigue sin pagar");
			assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM cobro WHERE producto_alumno_id = ?", Long.class,
					cargo), "sin fila de cobro");
			assertEquals(gAmarillo, gradoDe(a), "el grado no sube sin cobro");
		}
	}

	// ------------------------------------------------------------------ utilidades

	private MockHttpServletRequestBuilder pase(long alumno, String tipo) {
		return post("/api/profesor/alumnos/" + alumno + "/deportes/TAEKWONDO/" + tipo);
	}

	/** Alumno activo de la escuela, en ese grupo, con taekwondo apto en ese grado y fecha de grado antigua. */
	private long alumno(String prefijo, long grupo, long sede, long grado) {
		long id = e.alumno(prefijo + e.marca, sede, true);
		jdbc.update("INSERT INTO alumno_grupo (grupo_id, alumno_id) VALUES (?, ?)", grupo, id);
		e.taekwondo(id, true);
		jdbc.update("""
				UPDATE alumno_deporte SET grado_id = ?, fecha_grado = '2025-01-10'
				WHERE alumno_id = ? AND disciplina_id = ?""", grado, id, e.disciplina);
		return id;
	}

	private List<Map<String, Object>> cargos(long alumno) {
		return jdbc.queryForList("""
				SELECT id FROM producto_alumno
				WHERE alumno_id = ? OR alumno_deporte_id IN (SELECT id FROM alumno_deporte WHERE alumno_id = ?)
				ORDER BY id""", alumno, alumno);
	}

	private long gradoDe(long alumno) {
		return jdbc.queryForObject("SELECT grado_id FROM alumno_deporte WHERE alumno_id = ? AND disciplina_id = ?",
				Long.class, alumno, e.disciplina);
	}

	private boolean aptoDe(long alumno) {
		return Boolean.TRUE.equals(jdbc.queryForObject(
				"SELECT apto_para_examen FROM alumno_deporte WHERE alumno_id = ? AND disciplina_id = ?", Boolean.class,
				alumno, e.disciplina));
	}

	private long eventosSalud(long alumno, long usuario) {
		return jdbc.queryForObject("""
				SELECT COUNT(*) FROM auditoria_evento
				WHERE modulo = 'salud' AND accion = 'READ' AND recurso_id = ? AND usuario_id = ?""", Long.class,
				alumno, usuario);
	}

	private long eventosConEndpoint(String endpoint) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM auditoria_evento WHERE endpoint = ?", Long.class, endpoint);
	}

	private long ultimoEvento() {
		return jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM auditoria_evento", Long.class);
	}

	private void sistemaDeGrados() {
		jdbc.update("INSERT INTO sistema_grados (organizacion_id, nombre, plantilla_origen) VALUES (?, 'Sistema IT E81', NULL)",
				e.org);
		long sistema = jdbc.queryForObject("SELECT id FROM sistema_grados WHERE organizacion_id = ?", Long.class, e.org);
		jdbc.update("""
				INSERT INTO grado (organizacion_id, sistema_grados_id, codigo, nombre, orden, color_1, color_2, aplica_a)
				VALUES (?, ?, 'BLANCO', 'Blanco IT', 1, NULL, NULL, 'TODOS'),
				       (?, ?, 'AMARILLO', 'Amarillo IT', 2, NULL, NULL, 'TODOS'),
				       (?, ?, 'ROJO', 'Rojo IT', 3, NULL, NULL, 'TODOS'),
				       (?, ?, 'NEGRO_1_DAN', 'Negro IT', 4, NULL, NULL, 'TODOS')""",
				e.org, sistema, e.org, sistema, e.org, sistema, e.org, sistema);
		jdbc.update("UPDATE disciplina SET sistema_grados_id = ? WHERE id = ?", sistema, e.disciplina);
		gAmarillo = jdbc.queryForObject("SELECT id FROM grado WHERE sistema_grados_id = ? AND codigo = 'AMARILLO'",
				Long.class, sistema);
		gRojo = jdbc.queryForObject("SELECT id FROM grado WHERE sistema_grados_id = ? AND codigo = 'ROJO'", Long.class,
				sistema);
	}

	/** Producto de grado clasificado (código, tipo y grado concedido Rojo) de la escuela sintética. */
	private void productoDeGrado(String codigo, String tipo, double precio) {
		jdbc.update("""
				INSERT INTO producto (concepto, precio, codigo, tipo, grado_id, organizacion_id)
				VALUES (?, ?, ?, ?, ?, ?)""", codigo.replace('_', ' ') + " E81", precio, codigo, tipo, gRojo, e.org);
	}

	private void membresia(EscuelaConSedesDePrueba x, long usuario, String rol) {
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, ?)", usuario, x.org, rol);
	}

	private void asignar(EscuelaConSedesDePrueba x, long profesor, long grupo, String rol) {
		jdbc.update("INSERT INTO asignacion_profesor (organizacion_id, usuario_id, grupo_id, rol) VALUES (?, ?, ?, ?)",
				x.org, profesor, grupo, rol);
	}

	private JsonNode leer(MockHttpServletRequestBuilder peticion, String token, int esperado) throws Exception {
		MvcResult r = peticion(peticion, token);
		assertEquals(esperado, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		return json.readTree(r.getResponse().getContentAsString());
	}

	private int estado(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		return peticion(peticion, token).getResponse().getStatus();
	}

	private MvcResult peticion(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		return mvc.perform(peticion.cookie(new Cookie("jwt", token))).andReturn();
	}

	private static Set<Long> ids(JsonNode lista, String campo) {
		return new HashSet<>(idsEnOrden(lista, campo));
	}

	private static List<Long> idsEnOrden(JsonNode lista, String campo) {
		List<Long> ids = new ArrayList<>();
		lista.forEach(n -> ids.add(n.get(campo).asLong()));
		return ids;
	}
}
