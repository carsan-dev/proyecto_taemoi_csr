package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
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
 * Pieza 9-5 contra MySQL: el dinero va por la sede del cargo, no por la del alumno. Un alumno que cambia de sede deja
 * sus cobros en la sede antigua (decisión 369), así que un guard por el alumno no basta en las rutas que tocan un cargo.
 * Además, lo que se fija tal como está: los años de la tesorería, la caja de R + Pr y el certificado (decisión D6-3).
 * Escuelas sintéticas ({@link EscuelaConSedesDePrueba}); datos inventados.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ResponsableSedeDineroIntegrationTest extends MySqlIntegrationTest {

	private static final double CUANTIA = 25.0;

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;

	@MockitoBean JavaMailSender mailSender;

	final AtomicInteger numero = new AtomicInteger();
	EscuelaConSedesDePrueba e;
	EscuelaConSedesDePrueba otra;
	String tokenA;
	String tokenB;
	String tokenGestor;
	long convocatoria;

	@BeforeAll
	void preparar() {
		e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-95-dinero");
		otra = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-95-dinero-otra");
		e.crearResponsablesSolos(usuarios);
		long respB = e.cuenta(usuarios, "solo-b");
		e.responsable(respB, e.sB);
		tokenA = e.token(jwtService, usuarios, e.respSoloA);
		tokenB = e.token(jwtService, usuarios, respB);
		tokenGestor = e.token(jwtService, usuarios, e.gestor);
		jdbc.update("INSERT INTO convocatoria (fecha_convocatoria, organizacion_id, disciplina_id) VALUES "
				+ "('2026-06-01', ?, ?)", e.org, e.disciplina);
		convocatoria = jdbc.queryForObject("SELECT MAX(id) FROM convocatoria WHERE organizacion_id = ?", Long.class,
				e.org);
	}

	@AfterEach
	void sinFugaDeOrganizacion() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
	}

	// ------------------------------------------------------------------ examen: la sede del cargo manda

	@Test
	void convocatoria_noRevierteUnCobroDeOtraSede() throws Exception {
		Examen examen = examenCobradoEnBDeUnAlumnoQuePasoAA();
		BigDecimal saldoB = saldo(e.sB);
		assertTrue(saldoB.signum() > 0, "el examen entró en la caja de B");

		assertEquals(403, estado(examen(examen.inscripcion, CUANTIA, false), tokenA));

		assertEquals("ACTIVO", estadoDelCobro(examen.cargo));
		assertTrue(cargoPagado(examen.cargo));
		assertTrue(inscripcionPagada(examen.inscripcion));
		assertEquals(0, saldoB.compareTo(saldo(e.sB)), "la caja de B no baja");
	}

	@Test
	void convocatoria_noQuitaUnExamenCobradoEnOtraSede() throws Exception {
		Examen examen = examenCobradoEnBDeUnAlumnoQuePasoAA();

		assertEquals(403, estado(delete("/api/alumnos/" + convocatoria + "/alumno/" + examen.alumno), tokenA));

		assertEquals("ACTIVO", estadoDelCobro(examen.cargo));
		assertEquals(1, cuenta("SELECT COUNT(*) FROM alumno_convocatoria WHERE id = ?", examen.inscripcion));
	}

	/** Un cargo pendiente que sigue en B (por ejemplo, revertido tras el cambio de sede): ni se cobra ni se borra. */
	@Test
	void convocatoria_cargoPendienteDeOtraSede_niSeCobraNiSeReprecioNiSeBorra() throws Exception {
		long alumno = alumnoEn(e.sA);
		long cargo = cargoSinCobrar(alumno, e.sB, "DERECHO A EXAMEN IT");
		long inscripcion = inscripcion(alumno, cargo);

		assertEquals(403, estado(examen(inscripcion, CUANTIA, true), tokenA), "cobrar");
		assertEquals(403, estado(examen(inscripcion, 99.0, false), tokenA), "cambiar el precio");
		assertEquals(403, estado(delete("/api/alumnos/" + convocatoria + "/alumno/" + alumno), tokenA), "quitar");

		assertFalse(cargoPagado(cargo));
		assertEquals(0, cuenta("SELECT COUNT(*) FROM cobro WHERE producto_alumno_id = ?", cargo));
		assertEquals(CUANTIA, jdbc.queryForObject("SELECT precio FROM producto_alumno WHERE id = ?", Double.class, cargo));
		assertEquals(1, cuenta("SELECT COUNT(*) FROM alumno_convocatoria WHERE id = ?", inscripcion));
	}

	/** Un cargo sin sede es solo de gestión, como en {@code productoAlumnoEnSede}. */
	@Test
	void convocatoria_cargoSinSede_soloGestion() throws Exception {
		long alumno = alumnoEn(e.sA);
		long inscripcion = inscripcion(alumno, cargoSinCobrar(alumno, null, "DERECHO A EXAMEN IT"));

		assertEquals(403, estado(examen(inscripcion, CUANTIA, true), tokenA));
		assertEquals(200, estado(examen(inscripcion, CUANTIA, true), tokenGestor));
	}

	/** Decisión 154: en su sede, R sigue marcando y desmarcando el examen y quitando al alumno sin pagar. */
	@Test
	void convocatoria_suSede_marcaDesmarcaYQuita_decision154() throws Exception {
		long alumno = alumnoEn(e.sA);
		long cargo = cargoSinCobrar(alumno, e.sA, "DERECHO A EXAMEN IT");
		long inscripcion = inscripcion(alumno, cargo);

		assertEquals(200, estado(examen(inscripcion, CUANTIA, true), tokenA));
		assertEquals("ACTIVO", estadoDelCobro(cargo));
		assertTrue(cargoPagado(cargo));

		assertEquals(200, estado(examen(inscripcion, CUANTIA, false), tokenA));
		assertEquals("ANULADO", estadoDelCobro(cargo));
		assertFalse(cargoPagado(cargo));
		assertFalse(inscripcionPagada(inscripcion));

		assertEquals(204, estado(delete("/api/alumnos/" + convocatoria + "/alumno/" + alumno), tokenA));
		assertEquals(0, cuenta("SELECT COUNT(*) FROM producto_alumno WHERE id = ?", cargo));
	}

	@Test
	void convocatoria_laGestionSiRevierteElCobroDeOtraSede() throws Exception {
		Examen examen = examenCobradoEnBDeUnAlumnoQuePasoAA();

		assertEquals(200, estado(examen(examen.inscripcion, CUANTIA, false), tokenGestor));

		assertEquals("ANULADO", estadoDelCobro(examen.cargo));
	}

	/** Ni la sede del cargo basta: el alumno ya no es suyo. Revertirlo le queda a la gestión. */
	@Test
	void convocatoria_laSedeAntiguaTampocoRevierte() throws Exception {
		Examen examen = examenCobradoEnBDeUnAlumnoQuePasoAA();

		assertEquals(403, estado(examen(examen.inscripcion, CUANTIA, false), tokenB));

		assertEquals("ACTIVO", estadoDelCobro(examen.cargo));
	}

	// ------------------------------------------------------------------ altas con id

	/** El alta de un cargo no lee el id del cuerpo: crea uno nuevo y el que nombra sigue como estaba. */
	@Test
	void cargo_altaConIdDeOtroCargo_noLoSobrescribe() throws Exception {
		long ajeno = cargoSinCobrar(e.aB, e.sB, "CAMISETA IT");
		Map<String, Object> antes = jdbc.queryForMap("SELECT * FROM producto_alumno WHERE id = ?", ajeno);
		jdbc.update("INSERT INTO producto (concepto, precio, codigo, organizacion_id) VALUES ('CAMISETA ALTA IT', 9.0, "
				+ "NULL, ?)", e.org);
		long producto = jdbc.queryForObject("SELECT MAX(id) FROM producto WHERE organizacion_id = ?", Long.class, e.org);

		MvcResult alta = peticion(post("/api/productos-alumno/alumno/" + e.aA + "/producto/" + producto)
				.contentType(MediaType.APPLICATION_JSON).content("{\"id\":" + ajeno + ",\"pagado\":false}"), tokenA);

		assertEquals(200, alta.getResponse().getStatus(), alta.getResponse().getContentAsString());
		assertNotEquals(ajeno, json.readTree(alta.getResponse().getContentAsString()).get("id").asLong());
		assertEquals(antes, jdbc.queryForMap("SELECT * FROM producto_alumno WHERE id = ?", ajeno));
	}

	// ------------------------------------------------------------------ años de la tesorería

	@Test
	void aniosDisponibles_soloLosDeSusSedes() throws Exception {
		insertarCargo(e.aB, e.sB, "CAMISETA IT", "2011-03-01");
		insertarCargo(e.aB, e.sB, "MENSUALIDAD ENERO 2012 - TAEKWONDO", null);
		insertarCargo(e.aSinSede, null, "CAMISETA IT", "2013-03-01");
		insertarCargo(e.aA, e.sA, "CAMISETA IT", "2014-03-01");
		insertarCargo(e.aA, e.sA, "MENSUALIDAD ENERO 2015 - TAEKWONDO", null);
		jdbc.update("""
				INSERT INTO producto_alumno (alumno_id, concepto, precio, cantidad, pagado, fecha_asignacion,
				  organizacion_id, sede_id)
				VALUES (?, 'CAMISETA IT', 10, 1, 0, '2016-03-01 12:00:00', ?, ?)""", otra.aA, otra.org, otra.sA);

		List<Integer> deA = anios(tokenA);
		assertTrue(deA.containsAll(List.of(2014, 2015)), deA.toString());
		for (int ajeno : List.of(2011, 2012, 2013, 2016)) {
			assertFalse(deA.contains(ajeno), ajeno + " en " + deA);
		}

		List<Integer> gestion = anios(tokenGestor);
		assertTrue(gestion.containsAll(List.of(2011, 2012, 2013, 2014, 2015)), gestion.toString());
		assertFalse(gestion.contains(2016), "otra escuela");
	}

	// ------------------------------------------------------------------ R + Pr

	/** Su sede no le abre la caja de profesor: esa ruta va solo por sus grupos, también en otra sede (371). */
	@Test
	void responsableYProfesor_noCobraComoProfesorUnCargoDeSuSedeFueraDeSusGrupos() throws Exception {
		String token = e.token(jwtService, usuarios, responsableDeAYProfesorDe("rpr-cobrar", e.gB));
		long deSuSede = cargoSinCobrar(e.aA, e.sA, "CAMISETA IT");
		long deSuGrupo = cargoSinCobrar(e.aB, e.sB, "CAMISETA IT");

		assertEquals(403, estado(post("/api/caja/profesor/cargos/" + deSuSede + "/cobrar"), token));

		assertFalse(cargoPagado(deSuSede));
		assertEquals(0, cuenta("SELECT COUNT(*) FROM cobro WHERE producto_alumno_id = ?", deSuSede));
		assertEquals(201, estado(post("/api/caja/profesor/cargos/" + deSuGrupo + "/cobrar"), token), "Mi caja: su grupo");
	}

	/**
	 * Fija lo que pasa hoy (decisión del propietario, sin número): R + Pr sin familia registra la entrega de su propio
	 * efectivo en su sede y queda marcada. Fuera de su sede, nada.
	 */
	@Test
	void autoentrega_responsableYProfesorSinFamilia_soloEnSuSede() throws Exception {
		long alumnoDeA = alumnoEn(e.sA);
		jdbc.update("INSERT INTO alumno_grupo (grupo_id, alumno_id) VALUES (?, ?)", e.gA, alumnoDeA);
		long cuenta = responsableDeAYProfesorDe("rpr-entrega", e.gA, e.gB);
		String token = e.token(jwtService, usuarios, cuenta);
		long cobroA = cobrar(cargoSinCobrar(alumnoDeA, e.sA, "CAMISETA IT"), token);
		long cobroB = cobrar(cargoSinCobrar(e.aB, e.sB, "CAMISETA IT"), token);

		assertEquals(403, estado(entrega(e.sB, cuenta, cobroB), token), "sede ajena");
		assertEquals(400, estado(entrega(e.sA, cuenta, cobroB), token), "cobro de otra sede en su caja");
		assertEquals(0, cuenta("SELECT COUNT(*) FROM cobro WHERE id = ? AND entrega_caja_id IS NOT NULL", cobroB));

		MvcResult propia = peticion(entrega(e.sA, cuenta, cobroA), token);

		assertEquals(201, propia.getResponse().getStatus(), propia.getResponse().getContentAsString());
		JsonNode dto = json.readTree(propia.getResponse().getContentAsString());
		assertTrue(dto.get("autoentrega").asBoolean());
		assertEquals(cuenta, dto.get("entregadoPorUsuarioId").asLong());
		assertEquals(cuenta, dto.get("recibidoPorUsuarioId").asLong());
	}

	// ------------------------------------------------------------------ certificado (decisión D6-3)

	/**
	 * Fija lo que pasa hoy: el certificado es del alumno, así que suma sus cobros de todas las sedes, también los de una
	 * sede que su responsable actual no ve. Solo lo pide quien tiene al alumno en su sede principal.
	 */
	@Test
	void certificado_deUnAlumnoDeASumaSusCobrosDeAyDeB() throws Exception {
		int ano = 2025;
		long alumno = alumnoEn(e.sA);
		cargoPagado(alumno, e.sA, "MENSUALIDAD ENERO 2025 - TAEKWONDO", 30.0);
		cargoPagado(alumno, e.sB, "MENSUALIDAD FEBRERO 2025 - TAEKWONDO", 77.0);

		Map<String, String> campos = certificado(alumno, ano, tokenA);

		assertTrue(campos.get("totalImporte").startsWith("107"), campos.toString());
		assertTrue(campos.get("mes_1").startsWith("30"), campos.toString());
		assertTrue(campos.get("mes_2").startsWith("77"), campos.toString());
		assertEquals(campos, certificado(alumno, ano, tokenGestor), "lo mismo que la gestión");
		assertEquals(403, estado(certificados(alumno, ano), tokenB), "la sede B no tiene al alumno");
	}

	// ------------------------------------------------------------------ utilidades

	private record Examen(long alumno, long cargo, long inscripcion) {
	}

	/** El guion de la revisión: cobrado en B, gestión cambia al alumno a A y el cargo y su cobro se quedan en B (369). */
	private Examen examenCobradoEnBDeUnAlumnoQuePasoAA() throws Exception {
		long alumno = alumnoEn(e.sB);
		long cargo = cargoSinCobrar(alumno, e.sB, "DERECHO A EXAMEN IT");
		long inscripcion = inscripcion(alumno, cargo);
		assertEquals(200, estado(examen(inscripcion, CUANTIA, true), tokenGestor));
		assertEquals(200, estado(put("/api/alumnos/" + alumno + "/sede").contentType(MediaType.APPLICATION_JSON)
				.content("{\"sedeId\":" + e.sA + "}"), tokenGestor));
		assertEquals(e.sA, e.sedeDe(alumno));
		assertEquals(e.sB, jdbc.queryForObject("SELECT sede_id FROM producto_alumno WHERE id = ?", Long.class, cargo));
		assertEquals(e.sB, jdbc.queryForObject("SELECT sede_id FROM cobro WHERE producto_alumno_id = ?", Long.class, cargo));
		return new Examen(alumno, cargo, inscripcion);
	}

	private MockHttpServletRequestBuilder examen(long inscripcion, double cuantia, boolean pagado) {
		return put("/api/convocatorias/alumno/" + inscripcion).contentType(MediaType.APPLICATION_JSON)
				.content("{\"cuantiaExamen\":" + cuantia + ",\"pagado\":" + pagado + ",\"metodoPago\":\"EFECTIVO\"}");
	}

	private long alumnoEn(long sede) {
		long alumno = e.alumno("E95D" + numero.incrementAndGet() + e.marca, sede, true);
		e.taekwondo(alumno, false);
		// Con fecha de grado, marcar el pago no la rellena (esta disciplina de prueba no tiene sistema de grados).
		jdbc.update("UPDATE alumno_deporte SET fecha_grado = '2024-01-01' WHERE alumno_id = ?", alumno);
		return alumno;
	}

	private long cargoSinCobrar(long alumno, Long sede, String concepto) {
		return insertarCargo(alumno, sede, concepto, "2026-01-02");
	}

	private long insertarCargo(long alumno, Long sede, String concepto, String fecha) {
		jdbc.update("""
				INSERT INTO producto_alumno (alumno_id, concepto, precio, cantidad, pagado, fecha_asignacion,
				  organizacion_id, sede_id)
				VALUES (?, ?, ?, 1, 0, ?, ?, ?)""", alumno, concepto, CUANTIA, fecha, e.org, sede);
		return jdbc.queryForObject("SELECT MAX(id) FROM producto_alumno WHERE organizacion_id = ?", Long.class, e.org);
	}

	private void cargoPagado(long alumno, long sede, String concepto, double precio) {
		jdbc.update("""
				INSERT INTO producto_alumno (alumno_id, concepto, precio, cantidad, pagado, fecha_asignacion, fecha_pago,
				  organizacion_id, sede_id)
				VALUES (?, ?, ?, 1, 1, '2025-01-02 12:00:00', '2025-03-02 12:00:00', ?, ?)""", alumno, concepto, precio,
				e.org, sede);
	}

	private long inscripcion(long alumno, long cargo) {
		jdbc.update("""
				INSERT INTO alumno_convocatoria (cuantia_examen, pagado, alumno_id, convocatoria_id, grado_actual,
				  grado_siguiente, organizacion_id, producto_alumno_id) VALUES (?, 0, ?, ?, NULL, NULL, ?, ?)""", CUANTIA,
				alumno, convocatoria, e.org, cargo);
		return jdbc.queryForObject("SELECT id FROM alumno_convocatoria WHERE alumno_id = ? AND convocatoria_id = ?",
				Long.class, alumno, convocatoria);
	}

	private long responsableDeAYProfesorDe(String nombre, long... grupos) {
		long cuenta = e.cuenta(usuarios, nombre);
		e.responsable(cuenta, e.sA);
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, 'PROFESOR')", cuenta, e.org);
		for (long grupo : grupos) {
			jdbc.update("INSERT INTO asignacion_profesor (organizacion_id, usuario_id, grupo_id, rol) "
					+ "VALUES (?, ?, ?, 'TITULAR')", e.org, cuenta, grupo);
		}
		return cuenta;
	}

	private long cobrar(long cargo, String token) throws Exception {
		MvcResult r = peticion(post("/api/caja/profesor/cargos/" + cargo + "/cobrar"), token);
		assertEquals(201, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		return json.readTree(r.getResponse().getContentAsString()).get("cobroId").asLong();
	}

	private MockHttpServletRequestBuilder entrega(long sede, long entregadoPor, long cobro) throws Exception {
		Map<String, Object> cuerpo = new HashMap<>();
		cuerpo.put("entregadoPorUsuarioId", entregadoPor);
		cuerpo.put("cobroIds", List.of(cobro));
		cuerpo.put("importeContado", BigDecimal.valueOf(CUANTIA));
		return post("/api/caja/sedes/" + sede + "/entregas").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(cuerpo));
	}

	private MockHttpServletRequestBuilder certificados(long alumno, int ano) throws Exception {
		return post("/api/tesoreria/certificados-cobros").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("alumnoIds", List.of(alumno), "ano", ano)));
	}

	/** Campos editables del PDF del certificado, sin la fecha de generación. */
	private Map<String, String> certificado(long alumno, int ano, String token) throws Exception {
		MvcResult r = peticion(certificados(alumno, ano), token);
		assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		Map<String, String> campos = new HashMap<>();
		try (PDDocument pdf = PDDocument.load(r.getResponse().getContentAsByteArray())) {
			PDAcroForm formulario = pdf.getDocumentCatalog().getAcroForm();
			formulario.getFieldTree().forEach(campo -> campos.put(campo.getFullyQualifiedName(), campo.getValueAsString()));
		}
		campos.remove("fechaGeneracion");
		return campos;
	}

	private List<Integer> anios(String token) throws Exception {
		MvcResult r = peticion(get("/api/tesoreria/anios-disponibles"), token);
		assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		List<Integer> anios = new ArrayList<>();
		json.readTree(r.getResponse().getContentAsString()).forEach(n -> anios.add(n.asInt()));
		return anios;
	}

	private BigDecimal saldo(long sede) throws Exception {
		MvcResult r = peticion(get("/api/caja/sedes/" + sede + "/resumen"), tokenGestor);
		assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		return json.readTree(r.getResponse().getContentAsString()).get("saldoCaja").decimalValue();
	}

	private String estadoDelCobro(long cargo) {
		return jdbc.queryForObject("SELECT estado FROM cobro WHERE producto_alumno_id = ? ORDER BY id DESC LIMIT 1",
				String.class, cargo);
	}

	private boolean cargoPagado(long cargo) {
		return jdbc.queryForObject("SELECT pagado FROM producto_alumno WHERE id = ?", Boolean.class, cargo);
	}

	private boolean inscripcionPagada(long inscripcion) {
		return jdbc.queryForObject("SELECT pagado FROM alumno_convocatoria WHERE id = ?", Boolean.class, inscripcion);
	}

	private int cuenta(String sql, long id) {
		return jdbc.queryForObject(sql, Integer.class, id);
	}

	private int estado(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		return peticion(peticion, token).getResponse().getStatus();
	}

	private MvcResult peticion(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		return mvc.perform(peticion.cookie(new Cookie("jwt", token))).andReturn();
	}
}
