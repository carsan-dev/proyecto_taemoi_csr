package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.List;
import java.util.Map;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.GrupoService;
import com.taemoi.project.services.JwtService;

import jakarta.servlet.http.Cookie;

/**
 * Pieza 6b-2d contra MySQL: cargos ANTIGUOS sin {@code alumno_id} cuyo alumno solo se alcanza por la inscripción
 * ({@code COALESCE(alumno, alumnoDeporte.alumno)}, como la tesorería). Editar (examen y no examen), borrar, cancelar,
 * reactivar y el cobro y el deshacer del profesor ya no responden 500; el alumno resuelto recibe los mismos efectos que
 * en un cargo normal (derecho a examen). El permiso sigue siendo el de la sede del cargo y el de los grupos del
 * profesor. Un cargo sin alumno resoluble (ni {@code alumno_id} ni inscripción): el responsable y el profesor no lo
 * cobran (400 y 403) y la gestión lo edita y el propietario lo borra sin tocar a ningún alumno. Datos inventados; nada en
 * la organización 1.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CargosSinAlumnoIntegrationTest extends MySqlIntegrationTest {

	private static final String EXAMEN = "DERECHO A EXAMEN 6B2D - TAEKWONDO";
	private static final String CAMISETA = "CAMISETA 6B2D";

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;

	@MockitoBean JavaMailSender mailSender;

	EscuelaConSedesDePrueba e;
	long propietario, prof1;
	String tGestor, tPropietario, tResp, tProf1;

	@BeforeAll
	void preparar() {
		e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-6b2d");
		propietario = e.cuenta(usuarios, "propietario");
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, 'PROPIETARIO')",
				propietario, e.org);
		prof1 = e.cuentaFamilia(usuarios, "prof1");
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, 'PROFESOR')", prof1, e.org);
		jdbc.update("INSERT INTO asignacion_profesor (organizacion_id, usuario_id, grupo_id, rol) "
				+ "VALUES (?, ?, ?, 'TITULAR')", e.org, prof1, e.gA);
		tGestor = e.token(jwtService, usuarios, e.gestor);
		tPropietario = e.token(jwtService, usuarios, propietario);
		tResp = e.token(jwtService, usuarios, e.respA);
		tProf1 = e.token(jwtService, usuarios, prof1);
	}

	@AfterEach
	void sinFugas() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
		SecurityContextHolder.clearContext();
	}

	// ------------------------------------------------------------------ editar (PUT del cargo)

	@Test
	void editarExamenAntiguo_laGestionCobraYRevierte_yElDerechoAExamenVaAlAlumnoDeLaInscripcion() throws Exception {
		long alumno = alumnoEnGrupo("E6DEXAMEN", e.sA, e.gA);
		long cargo = cargoAntiguo(alumno, EXAMEN, 15.0);

		MvcResult r = peticion(pago(cargo, "{\"pagado\":true}"), tGestor);
		assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		assertEquals(alumno, json.readTree(r.getResponse().getContentAsString()).get("alumnoId").asLong());
		assertTrue(pagado(cargo));
		assertTrue(derechoExamen(alumno), "el alumno de la inscripción gana el derecho a examen");
		List<Map<String, Object>> cobros = cobros(cargo);
		assertEquals(1, cobros.size());
		assertEquals(alumno, numero(cobros.get(0), "alumno_id"), "el cobro copia el alumno resuelto");

		// Pieza 6c-1 (decisión 187): con el cobro ACTIVO el precio ya no cambia (409); otro campo, sí.
		assertEquals(409, estado(pago(cargo, "{\"precio\":16}"), tGestor), "precio con cobro activo");
		assertEquals(200, estado(pago(cargo, "{\"notas\":\"otro campo\"}"), tGestor), "otro campo");
		assertEquals(200, estado(pago(cargo, "{\"pagado\":false}"), tPropietario), "revertir (propietario)");
		assertFalse(pagado(cargo));
		assertFalse(derechoExamen(alumno), "revertido: pierde el derecho a examen");
		assertEquals("ANULADO", cobros(cargo).get(0).get("estado"));
		assertNull(filaCargo(cargo).get("alumno_id"), "no se rellena alumno_id (sin migración ni cambio de datos)");
	}

	@Test
	void editarNoExamenAntiguo_comoAntes_sinTocarElDerechoAExamen() throws Exception {
		long alumno = alumnoEnGrupo("E6DNOEXAMEN", e.sA, e.gA);
		jdbc.update("UPDATE alumno SET tiene_derecho_examen = 1 WHERE id = ?", alumno);
		long cargo = cargoAntiguo(alumno, CAMISETA, 10.0);

		assertEquals(200, estado(pago(cargo, "{\"pagado\":true}"), tGestor));
		assertTrue(pagado(cargo));
		assertTrue(derechoExamen(alumno), "una camiseta no toca el derecho a examen");
		assertEquals(200, estado(pago(cargo, "{\"pagado\":false}"), tPropietario));
		assertTrue(derechoExamen(alumno));
	}

	@Test
	void responsable_editaElCargoAntiguoDeSuSede_noElDeOtra_niUnoSinAlumno() throws Exception {
		long alumno = alumnoEnGrupo("E6DRESP", e.sA, e.gA);
		long cargo = cargoAntiguo(alumno, EXAMEN, 15.0);
		MvcResult r = peticion(pago(cargo, "{\"pagado\":true}"), tResp);
		assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		assertTrue(pagado(cargo));
		assertTrue(derechoExamen(alumno));
		// D6-20 sigue: cobrado, el responsable solo cambia las notas.
		assertEquals(403, estado(pago(cargo, "{\"pagado\":false}"), tResp));
		assertTrue(pagado(cargo));

		// Otra sede: el permiso es el de la sede del cargo, como siempre.
		long deB = cargoAntiguo(e.alumno("E6DRESPB" + e.marca, e.sB, true), EXAMEN, 15.0, true);
		assertEquals(403, estado(pago(deB, "{\"pagado\":true}"), tResp));
		assertFalse(pagado(deB));

		// Sin alumno resoluble (ni alumno_id ni inscripción): 400 sin cambiar nada, como antes.
		long huerfano = cargoHuerfano(EXAMEN, e.sA);
		r = peticion(pago(huerfano, "{\"pagado\":true}"), tResp);
		assertEquals(400, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		assertTrue(r.getResponse().getContentAsString().contains("no tiene alumno"),
				r.getResponse().getContentAsString());
		assertFalse(pagado(huerfano));
		assertTrue(cobros(huerfano).isEmpty());
	}

	@Test
	void cargoSinAlumnoResoluble_laGestionLoEditaSinTocarANingunAlumno() throws Exception {
		long huerfano = cargoHuerfano(EXAMEN, e.sA);
		MvcResult r = peticion(pago(huerfano, "{\"pagado\":true}"), tGestor);
		assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		assertTrue(pagado(huerfano));
		assertNull(cobros(huerfano).get(0).get("alumno_id"));
		assertEquals(200, estado(pago(huerfano, "{\"pagado\":false}"), tPropietario));
		assertFalse(pagado(huerfano));
	}

	// ------------------------------------------------------------------ borrar, cancelar, reactivar

	@Test
	void borrarCargosAntiguos_examenYNoExamen_yUnoSinAlumno() throws Exception {
		long alumno = alumnoEnGrupo("E6DBORRA", e.sA, e.gA);
		jdbc.update("UPDATE alumno SET tiene_derecho_examen = 1 WHERE id = ?", alumno);
		long camiseta = cargoAntiguo(alumno, CAMISETA, 10.0);
		long examen = cargoAntiguo(alumno, EXAMEN, 15.0);
		long huerfano = cargoHuerfano(EXAMEN, e.sA);

		assertEquals(403, estado(delete("/api/productos-alumno/" + examen), tGestor), "solo el propietario");
		assertEquals(204, estado(delete("/api/productos-alumno/" + camiseta), tPropietario));
		assertEquals(0L, existe(camiseta));
		assertTrue(derechoExamen(alumno), "borrar una camiseta no toca el derecho a examen");
		MvcResult r = peticion(delete("/api/productos-alumno/" + examen), tPropietario);
		assertEquals(204, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		assertEquals(0L, existe(examen));
		assertFalse(derechoExamen(alumno), "como en un cargo normal: borrar el examen quita el derecho");
		assertEquals("CARGO CARGO_BORRADO sede=#" + e.sA + " cargo=#" + examen, registro("CARGO_BORRADO", examen));
		assertEquals(204, estado(delete("/api/productos-alumno/" + huerfano), tPropietario));
		assertEquals(0L, existe(huerfano));
	}

	@Test
	void cancelarYReactivarCargosAntiguos() throws Exception {
		long alumno = alumnoEnGrupo("E6DCANCELA", e.sA, e.gA);
		for (long cargo : List.of(cargoAntiguo(alumno, EXAMEN, 15.0), cargoAntiguo(alumno, CAMISETA, 10.0),
				cargoHuerfano(EXAMEN, e.sA))) {
			MvcResult r = peticion(cancelar(cargo), tGestor);
			assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
			assertNotNull(filaCargo(cargo).get("cancelado_en"));
			assertEquals(200, estado(post("/api/productos-alumno/" + cargo + "/reactivar"), tResp));
			assertNull(filaCargo(cargo).get("cancelado_en"));
		}
	}

	// ------------------------------------------------------------------ profesor

	@Test
	void profesor_cobraYDeshaceElCargoAntiguoDeUnAlumnoDeSuGrupo() throws Exception {
		long alumno = alumnoEnGrupo("E6DPROF", e.sA, e.gA);
		long cargo = cargoAntiguo(alumno, EXAMEN, 15.0);

		MvcResult r = peticion(post("/api/caja/profesor/cargos/" + cargo + "/cobrar"), tProf1);
		assertEquals(201, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		long cobro = json.readTree(r.getResponse().getContentAsString()).get("cobroId").asLong();
		assertTrue(pagado(cargo));
		assertTrue(derechoExamen(alumno));
		assertEquals(alumno, numero(jdbc.queryForMap("SELECT * FROM cobro WHERE id = ?", cobro), "alumno_id"));

		r = peticion(post("/api/caja/profesor/cobros/" + cobro + "/deshacer"), tProf1);
		assertEquals(204, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		assertFalse(pagado(cargo));
		assertFalse(derechoExamen(alumno));
	}

	@Test
	void profesor_noCobraElCargoAntiguoDeOtroGrupoNiUnoSinAlumno() throws Exception {
		long fuera = e.alumno("E6DFUERA" + e.marca, e.sA, true);
		long ajeno = cargoAntiguo(fuera, EXAMEN, 15.0);
		assertEquals(403, estado(post("/api/caja/profesor/cargos/" + ajeno + "/cobrar"), tProf1));
		long huerfano = cargoHuerfano(EXAMEN, e.sA);
		assertEquals(403, estado(post("/api/caja/profesor/cargos/" + huerfano + "/cobrar"), tProf1));
		assertFalse(pagado(ajeno));
		assertFalse(pagado(huerfano));
	}

	// ------------------------------------------------------------------ utilidades

	private long alumnoEnGrupo(String nombre, long sede, long grupo) {
		long alumno = e.alumno(nombre + e.marca, sede, true);
		jdbc.update("INSERT INTO alumno_grupo (grupo_id, alumno_id) VALUES (?, ?)", grupo, alumno);
		return alumno;
	}

	private long cargoAntiguo(long alumno, String concepto, double precio) {
		return cargoAntiguo(alumno, concepto, precio, false);
	}

	/** Cargo antiguo: {@code alumno_id} NULL; el alumno solo por la inscripción; la sede, la principal del alumno. */
	private long cargoAntiguo(long alumno, String concepto, double precio, boolean nuevaInscripcion) {
		Long inscripcion = nuevaInscripcion ? null
				: jdbc.queryForObject("SELECT MAX(id) FROM alumno_deporte WHERE alumno_id = ?", Long.class, alumno);
		if (inscripcion == null) {
			e.taekwondo(alumno, true);
			inscripcion = jdbc.queryForObject("SELECT MAX(id) FROM alumno_deporte WHERE alumno_id = ?", Long.class,
					alumno);
		}
		jdbc.update("""
				INSERT INTO producto_alumno (alumno_id, alumno_deporte_id, concepto, precio, cantidad, pagado,
				  fecha_asignacion, organizacion_id, sede_id)
				VALUES (NULL, ?, ?, ?, 1, 0, NOW(), ?, (SELECT sede_principal_id FROM alumno WHERE id = ?))""",
				inscripcion, concepto, precio, e.org, alumno);
		return jdbc.queryForObject("SELECT MAX(id) FROM producto_alumno WHERE organizacion_id = ?", Long.class, e.org);
	}

	/** Sin alumno resoluble: ni {@code alumno_id} ni inscripción. */
	private long cargoHuerfano(String concepto, long sede) {
		jdbc.update("""
				INSERT INTO producto_alumno (alumno_id, alumno_deporte_id, concepto, precio, cantidad, pagado,
				  fecha_asignacion, organizacion_id, sede_id) VALUES (NULL, NULL, ?, 12.0, 1, 0, NOW(), ?, ?)""",
				concepto, e.org, sede);
		return jdbc.queryForObject("SELECT MAX(id) FROM producto_alumno WHERE organizacion_id = ?", Long.class, e.org);
	}

	private MockHttpServletRequestBuilder cancelar(long cargo) {
		return post("/api/productos-alumno/" + cargo + "/cancelar").contentType(MediaType.APPLICATION_JSON)
				.content("{\"motivo\":\"cargo antiguo\"}");
	}

	private static MockHttpServletRequestBuilder pago(long cargo, String cuerpo) {
		return put("/api/productos-alumno/" + cargo).contentType(MediaType.APPLICATION_JSON).content(cuerpo);
	}

	private boolean derechoExamen(long alumno) {
		return Boolean.TRUE.equals(jdbc.queryForObject("SELECT tiene_derecho_examen FROM alumno WHERE id = ?",
				Boolean.class, alumno));
	}

	private String registro(String accion, long cargo) {
		return jdbc.queryForObject("""
				SELECT resumen FROM auditoria_evento WHERE organizacion_id = ? AND modulo = 'productos-alumno'
				  AND resumen LIKE ? AND recurso_id = ? ORDER BY id DESC LIMIT 1""", String.class, e.org,
				"CARGO " + accion + " %", cargo);
	}

	private Map<String, Object> filaCargo(long cargo) {
		return jdbc.queryForMap("SELECT * FROM producto_alumno WHERE id = ?", cargo);
	}

	private long existe(long cargo) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM producto_alumno WHERE id = ?", Long.class, cargo);
	}

	private boolean pagado(long cargo) {
		return Boolean.TRUE.equals(jdbc.queryForObject("SELECT pagado FROM producto_alumno WHERE id = ?", Boolean.class,
				cargo));
	}

	private List<Map<String, Object>> cobros(long cargo) {
		return jdbc.queryForList("SELECT * FROM cobro WHERE producto_alumno_id = ? ORDER BY id", cargo);
	}

	private int estado(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		return peticion(peticion, token).getResponse().getStatus();
	}

	private MvcResult peticion(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		if (token != null) {
			peticion.cookie(new Cookie("jwt", token));
		}
		return mvc.perform(peticion).andReturn();
	}

	private static long numero(Map<String, Object> fila, String columna) {
		return ((Number) fila.get(columna)).longValue();
	}
}
