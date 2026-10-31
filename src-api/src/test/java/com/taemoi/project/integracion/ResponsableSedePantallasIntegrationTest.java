package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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
import com.taemoi.project.entities.AlumnoDeporte;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.GrupoService;
import com.taemoi.project.services.JwtService;

import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.http.Cookie;

/**
 * Pieza 9-5 (decisiones 370, 372 y 369) contra MySQL, con una cuenta que SOLO es responsable de la sede A: cada pantalla
 * de la 370 lee y escribe dentro de sus sedes, nada de otra sede entra en listados ni en totales de dinero, y lo que la
 * decisión deja fuera sigue en 403. Las rutas de las entregas 6 y 7 tienen su detalle en los demás
 * {@code ResponsableSede*IntegrationTest}; aquí, las que abre la 9-5 y una muestra de cada pantalla.
 *
 * <pre>
 * cargos sin cobrar, de ENERO de 2024 (vencidos)   aA: 30 (A) · aB: 77 (B) · aGrupoA: 13 (B) · aSinSede: 11 (sin sede)
 *                                                  otra.aA: 500 (otra escuela)
 * </pre>
 * Escuelas sintéticas ({@link EscuelaConSedesDePrueba}); datos inventados.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ResponsableSedePantallasIntegrationTest extends MySqlIntegrationTest {

	private static final String CONCEPTO = "MENSUALIDAD ENERO 2024 - TAEKWONDO";

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;
	@Autowired EntityManagerFactory emf;

	@MockitoBean JavaMailSender mailSender;

	EscuelaConSedesDePrueba e;
	EscuelaConSedesDePrueba otra;
	long responsable;
	String tR;
	String tGestor;
	long cargoA, cargoB, cargoGrupoA, cargoSinSede, cargoOtra, producto;

	@BeforeAll
	void preparar() {
		e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-95-pantallas");
		otra = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-95-pantallas-otra");
		responsable = e.cuenta(usuarios, "solo-a");
		e.responsable(responsable, e.sA);
		tR = e.token(jwtService, usuarios, responsable);
		tGestor = e.token(jwtService, usuarios, e.gestor);

		cargoA = cargo(e, e.aA, "30.00", e.sA);
		cargoB = cargo(e, e.aB, "77.00", e.sB);
		cargoGrupoA = cargo(e, e.aGrupoA, "13.00", e.sB);
		cargoSinSede = cargo(e, e.aSinSede, "11.00", null);
		cargoOtra = cargo(otra, otra.aA, "500.00", otra.sA);
		jdbc.update("INSERT INTO producto (concepto, precio, codigo, organizacion_id) VALUES ('CAMISETA 95', 9.0, NULL, ?)",
				e.org);
		producto = jdbc.queryForObject("SELECT MAX(id) FROM producto WHERE organizacion_id = ?", Long.class, e.org);
	}

	@AfterEach
	void sinFugaDeOrganizacion() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
	}

	// ------------------------------------------------------------------ Inicio

	@Test
	void inicio_lasLecturasResponden_yElVencidoSoloLleva_elDineroDeSusSedes() throws Exception {
		assertEquals(4, leer(get("/api/alumnos?page=1&size=1&incluirInactivos=true"), tR).get("totalElements").asInt(),
				"aA, aGrupoA, aSinSede y aInactivoA");
		assertEquals(Set.of(e.gA, e.gA2), ids(leer(get("/api/grupos/resumen"), tR), "id"));
		assertEquals(Set.of(e.tA), ids(leer(get("/api/turnos/resumen"), tR), "id"));
		assertEquals(200, estado(get("/api/alumnos/aptos"), tR));
		assertEquals(200, estado(get("/api/alumnos/deportes/distribucion"), tR));
		assertEquals(200, estado(get("/api/turnos/limite"), tR));

		JsonNode deR = leer(get("/api/tesoreria/pendiente-vencido"), tR);
		assertEquals(0, new BigDecimal("30.00").compareTo(deR.get("total").decimalValue()), deR.toString());
		assertEquals(1, deR.get("cargos").asInt());
		assertEquals(1, deR.get("porSede").size());
		assertEquals(e.sA, deR.get("porSede").get(0).get("sedeId").asLong());
		for (JsonNode antiguo : deR.get("masAntiguos")) {
			assertEquals(cargoA, antiguo.get("productoAlumnoId").asLong(), "ningún cargo de otra sede");
		}
		assertEquals(200, estado(head("/api/tesoreria/pendiente-vencido"), tR));

		JsonNode gestion = leer(get("/api/tesoreria/pendiente-vencido"), tGestor);
		assertEquals(0, new BigDecimal("131.00").compareTo(gestion.get("total").decimalValue()), gestion.toString());
		assertEquals(3, gestion.get("porSede").size(), "A, B y sin sede: la gestión sigue viéndolo todo");
	}

	@Test
	void inicio_sinEventosNiDatosDeGestion() throws Exception {
		assertEquals(403, estado(get("/api/eventos/admin/todos"), tR));
		assertEquals(403, estado(get("/api/plataforma/errores"), tR));
	}

	@Test
	void miPerfil_soloLaSuya() throws Exception {
		assertEquals(200, estado(get("/api/panel/mi-perfil"), tR));
		assertEquals(200, estado(put("/api/panel/mi-perfil").contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombrePreferido\":\"Nueva\"}"), tR));
		assertEquals("Nueva", jdbc.queryForObject("SELECT nombre_preferido FROM usuario WHERE id = ?", String.class,
				responsable));
		assertEquals("Nueva", leer(get("/api/auth/permisos"), tR).get("nombreSaludo").asText());
		jdbc.update("UPDATE usuario SET nombre_preferido = NULL WHERE id = ?", responsable);
	}

	// ------------------------------------------------------------------ Alumnos

	@Test
	void alumnos_soloLosDeSusSedes_conSuPendienteYElAltaConGrados() throws Exception {
		assertEquals(200, estado(get("/api/alumnos/" + e.aA), tR));
		assertEquals(403, estado(get("/api/alumnos/" + e.aB), tR));
		assertEquals(403, estado(get("/api/alumnos/" + e.aNada), tR));
		assertEquals(403, estado(get("/api/alumnos/" + otra.aA), tR), "alumno de otra escuela");

		// Cobrar desde la lista: lo vencido de los que puede ver. Un id de otra sede o de otra escuela no sale.
		JsonNode pendientes = leer(get("/api/tesoreria/pendientes-por-alumno?alumnoIds=" + e.aA + "," + e.aB + ","
				+ e.aGrupoA + "," + e.aSinSede + "," + otra.aA), tR);
		assertEquals(1, pendientes.size(), pendientes.toString());
		assertEquals(e.aA, pendientes.get(0).get("alumnoId").asLong());
		assertEquals(0, new BigDecimal("30.00").compareTo(pendientes.get(0).get("importe").decimalValue()));

		// El alta de alumno pide los grados: catálogo común de la escuela, no un dato de sede.
		assertEquals(200, estado(get("/api/grados"), tR));
		assertEquals(200, estado(get("/api/grados/disponibles/2010-01-01"), tR));
	}

	@Test
	void alumnos_exclusiones_sinTocarNada() throws Exception {
		assertEquals(403, estado(get("/api/alumnos/cargos-resumen?alumnoIds=" + e.aA), tR));
		assertEquals(403, estado(delete("/api/alumnos/" + e.aA), tR));
		assertEquals(403, estado(post("/api/alumnos/" + e.aA + "/anonimizar"), tR));
		assertEquals(403, estado(put("/api/alumnos/" + e.aB + "/baja"), tR));
		assertEquals(Boolean.TRUE, jdbc.queryForObject("SELECT activo FROM alumno WHERE id = ?", Boolean.class, e.aB));
		assertEquals(403, estado(get("/api/informes/alumnosPorGrado"), tR), "informes sin filtro de sede");
	}

	// ------------------------------------------------------------------ Grupos y pasar lista

	@Test
	void grupos_conteoYAlumnosPorTipo_soloDeSusSedes() throws Exception {
		// TAEKWONDO activo en la escuela: aA y aGrupoA, de su alcance (D9), y aB, no.
		JsonNode deR = leer(get("/api/grupos/conteo-alumnos"), tR);
		assertEquals(2, deR.get("Taekwondo").get("active").asInt(), deR.toString());
		assertEquals(2, deR.get("Taekwondo").get("total").asInt(), deR.toString());
		assertEquals(0, deR.get("Kickboxing").get("total").asInt(), deR.toString());
		assertEquals(3, leer(get("/api/grupos/conteo-alumnos"), tGestor).get("Taekwondo").get("active").asInt());

		assertEquals(Set.of(e.aA, e.aGrupoA), ids(leer(get("/api/grupos/tipo/Taekwondo/alumnos"), tR), "id"));
		assertEquals(Set.of(e.aA, e.aB, e.aGrupoA), ids(leer(get("/api/grupos/tipo/Taekwondo/alumnos"), tGestor), "id"));
		assertEquals(0, leer(get("/api/grupos/tipo/{tipo}/alumnos", "Taekwondo Competición"), tR).size());
	}

	/** Cuenta por sus alumnos en la base: ni una inscripción cargada en memoria, por grande que sea la escuela. */
	@Test
	void grupos_conteo_noCargaLasInscripcionesDeLaEscuela() throws Exception {
		Statistics st = emf.unwrap(SessionFactory.class).getStatistics();
		boolean activas = st.isStatisticsEnabled();
		st.setStatisticsEnabled(true);
		try {
			st.clear();
			assertEquals(2, leer(get("/api/grupos/conteo-alumnos"), tR).get("Taekwondo").get("total").asInt());
			assertTrue(st.getPrepareStatementCount() > 0, "las estadísticas cuentan");
			assertEquals(0, st.getEntityStatistics(AlumnoDeporte.class.getName()).getLoadCount());
		} finally {
			st.setStatisticsEnabled(activas);
		}
	}

	@Test
	void grupos_conteoDeOtraEscuela_noSeCuela() throws Exception {
		// Misma disciplina y mismos tipos en la otra escuela: sus alumnos no cuentan nunca.
		JsonNode deOtra = leer(get("/api/grupos/conteo-alumnos"), otra.token(jwtService, usuarios, otra.gestor));
		assertEquals(3, deOtra.get("Taekwondo").get("active").asInt(), "la otra escuela, sin sumar la primera");
	}

	@Test
	void pasarLista_soloEnGruposDeSusSedes() throws Exception {
		assertEquals(200, estado(get("/api/grupos/" + e.gA + "/clases?year=2026&month=9"), tR));
		assertEquals(403, estado(get("/api/grupos/" + e.gB + "/clases?year=2026&month=9"), tR));
		assertEquals(403, estado(get("/api/grupos/" + otra.gA + "/clases?year=2026&month=9"), tR), "otra escuela");
	}

	@Test
	void grupos_escriturasFueraDeAlcance_noEscriben() throws Exception {
		int enGrupoB = jdbc.queryForObject("SELECT COUNT(*) FROM alumno_grupo WHERE grupo_id = ?", Integer.class, e.gB);
		assertEquals(403, estado(post("/api/grupos/" + e.gB + "/alumnos/" + e.aA), tR));
		assertEquals(403, estado(post("/api/alumnos/" + e.aB + "/grupos/" + e.gA), tR));
		assertEquals(403, estado(delete("/api/grupos/" + e.gB), tR));
		assertEquals(403, estado(put("/api/turnos/" + e.tB).contentType(MediaType.APPLICATION_JSON)
				.content("{\"horaInicio\":\"09:00\",\"horaFin\":\"10:00\",\"diaSemana\":\"Lunes\"}"), tR));
		assertEquals(enGrupoB,
				jdbc.queryForObject("SELECT COUNT(*) FROM alumno_grupo WHERE grupo_id = ?", Integer.class, e.gB));
		assertEquals("18:00", jdbc.queryForObject("SELECT hora_inicio FROM turno WHERE id = ?", String.class, e.tB));
	}

	// ------------------------------------------------------------------ Preinscripciones y convocatorias

	@Test
	void preinscripcionesYConvocatorias_respondenEnSuAlcance() throws Exception {
		assertEquals(200, estado(get("/api/preinscripciones?size=10"), tR));
		assertEquals(403, estado(get("/api/preinscripciones/PRE-NO-EXISTE"), tR));
		assertEquals(200, estado(get("/api/convocatorias?deporte=TAEKWONDO"), tR));
		assertEquals(403, estado(post("/api/convocatorias").contentType(MediaType.APPLICATION_JSON).content("{}"), tR));
		assertEquals(403, estado(delete("/api/preinscripciones/PRE-NO-EXISTE"), tR));
		assertEquals(403, estado(get("/api/preinscripciones/plantillas/TAEKWONDO"), tR), "plantillas: configuración");
	}

	// ------------------------------------------------------------------ Tesorería

	@Test
	void tesoreria_soloElDineroDeSusSedes_enTotalesYExportaciones() throws Exception {
		JsonNode resumen = leer(get("/api/tesoreria/resumen"), tR);
		double deSuSede = jdbc.queryForObject("SELECT SUM(precio) FROM producto_alumno WHERE sede_id = ?", Double.class,
				e.sA);
		assertEquals(deSuSede, resumen.get("importeTotal").asDouble(), resumen.toString());
		assertEquals(1, resumen.get("porSede").size());
		assertEquals(e.sA, resumen.get("porSede").get(0).get("sedeId").asLong());
		assertEquals(403, estado(get("/api/tesoreria/resumen?sedeId=" + e.sB), tR), "otra sede");
		assertEquals(400, estado(get("/api/tesoreria/resumen?sedeId=" + otra.sA), tR), "sede de otra escuela");

		String csv = texto(get("/api/tesoreria/export/csv"), tR);
		assertTrue(csv.contains("E6A" + e.marca), csv);
		assertFalse(csv.contains("E6B" + e.marca), csv);
		assertFalse(csv.contains("E6GRUPOA" + e.marca), csv);
		assertFalse(csv.contains(otra.marca), csv);
	}

	@Test
	void tesoreria_cobroYRecibo_soloDeCargosDeSusSedes() throws Exception {
		long cobrable = cargo(e, e.aA, "19.00", e.sA);
		assertEquals(409, estado(get("/api/productos-alumno/" + cobrable + "/recibo"), tR), "sin cobrar");
		assertEquals(200, estado(put("/api/productos-alumno/" + cobrable).contentType(MediaType.APPLICATION_JSON)
				.content("{\"pagado\":true}"), tR));

		MvcResult recibo = peticion(get("/api/productos-alumno/" + cobrable + "/recibo"), tR);
		assertEquals(200, recibo.getResponse().getStatus(), recibo.getResponse().getContentAsString());
		assertEquals(MediaType.APPLICATION_PDF_VALUE, recibo.getResponse().getContentType());

		for (long ajeno : List.of(cargoB, cargoGrupoA, cargoSinSede, cargoOtra, Long.MAX_VALUE)) {
			assertEquals(403, estado(get("/api/productos-alumno/" + ajeno + "/recibo"), tR), "cargo " + ajeno);
		}
		assertEquals(404, estado(get("/api/productos-alumno/" + cargoOtra + "/recibo"), tGestor),
				"la gestión sigue con su 404");
	}

	@Test
	void tesoreria_escriturasFueraDeAlcance_noEscriben() throws Exception {
		for (long ajeno : List.of(cargoB, cargoGrupoA, cargoSinSede, cargoOtra)) {
			assertEquals(403, estado(put("/api/productos-alumno/" + ajeno).contentType(MediaType.APPLICATION_JSON)
					.content("{\"pagado\":true}"), tR), "cobrar " + ajeno);
			assertEquals(403, estado(post("/api/productos-alumno/" + ajeno + "/cancelar")
					.contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"IT\"}"), tR), "cancelar " + ajeno);
		}
		assertEquals(403, estado(post("/api/productos-alumno/alumno/" + e.aB + "/producto/" + producto)
				.contentType(MediaType.APPLICATION_JSON).content("{}"), tR), "cargo nuevo a otra sede");
		assertEquals(0, jdbc.queryForObject(
				"SELECT COUNT(*) FROM producto_alumno WHERE id IN (?, ?, ?, ?) AND (pagado = 1 OR cancelado_en IS NOT NULL)",
				Integer.class, cargoB, cargoGrupoA, cargoSinSede, cargoOtra));
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM cobro WHERE organizacion_id = ? AND producto_alumno_id IN (?, ?, ?)",
				Integer.class, e.org, cargoB, cargoGrupoA, cargoSinSede));
	}

	// ------------------------------------------------------------------ Caja de sede

	@Test
	void cajaDeSede_soloLaDeSusSedes() throws Exception {
		for (String ruta : List.of("/resumen", "/pendiente-entrega", "/entregas", "/movimientos")) {
			assertEquals(200, estado(get("/api/caja/sedes/" + e.sA + ruta), tR), ruta);
			assertEquals(403, estado(get("/api/caja/sedes/" + e.sB + ruta), tR), ruta);
			assertEquals(403, estado(get("/api/caja/sedes/" + otra.sA + ruta), tR), "otra escuela " + ruta);
		}
		assertEquals(403, estado(post("/api/caja/sedes/" + e.sB + "/movimientos")
				.contentType(MediaType.APPLICATION_JSON).content("{\"importe\":10,\"motivo\":\"IT\"}"), tR));
		assertEquals(403, estado(post("/api/caja/entregas/1/anular"), tR), "anular: solo el propietario");
		assertEquals(403, estado(post("/api/caja/movimientos/1/anular"), tR), "anular: solo el propietario");
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM movimiento_caja WHERE organizacion_id = ?",
				Integer.class, e.org));
	}

	// ------------------------------------------------------------------ Solicitudes

	@Test
	void solicitudes_bandejaDeSusSedes_sinAceptarNiDenegar() throws Exception {
		assertEquals(200, estado(get("/api/solicitudes?segmento=PENDIENTES"), tR));
		assertEquals(200, estado(get("/api/solicitudes/contador"), tR));
		assertEquals(403, estado(post("/api/solicitudes/1/aceptar"), tR));
		assertEquals(403, estado(post("/api/solicitudes/1/denegar").contentType(MediaType.APPLICATION_JSON)
				.content("{\"respuesta\":\"no\"}"), tR));
	}

	// ------------------------------------------------------------------ exclusiones de la 370, 372 y 369

	@Test
	void decision370_eventosProductosConfiguracionSedesYMiembros_403() throws Exception {
		List<MockHttpServletRequestBuilder> rutas = List.of(
				// Eventos
				get("/api/eventos/admin/todos"), post("/api/eventos/crear"), delete("/api/eventos/1"),
				// Productos: escribir el catálogo (leerlo lo necesita el alta de cargos en la ficha, 6-3)
				post("/api/productos").contentType(MediaType.APPLICATION_JSON).content("{}"),
				put("/api/productos/" + producto).contentType(MediaType.APPLICATION_JSON).content("{}"),
				delete("/api/productos/" + producto),
				patch("/api/productos/" + producto + "/vendible-profesor").contentType(MediaType.APPLICATION_JSON)
						.content("{\"vendibleProfesor\":true}"),
				post("/api/productos-alumno/mensualidades/general"),
				// Configuración
				get("/api/admin/configuracion/limite-turno"), get("/api/admin/users"),
				get("/api/admin/auditoria/eventos"), put("/api/escuela/logo"),
				post("/api/tarifas-sede").contentType(MediaType.APPLICATION_JSON).content("{}"),
				// Sedes
				get("/api/sedes"), get("/api/sedes/" + e.sA),
				post("/api/sedes").contentType(MediaType.APPLICATION_JSON).content("{}"),
				put("/api/sedes/" + e.sA).contentType(MediaType.APPLICATION_JSON).content("{}"),
				put("/api/sedes/" + e.sA + "/activa").contentType(MediaType.APPLICATION_JSON)
						.content("{\"activa\":false}"),
				// Miembros
				get("/api/miembros"), get("/api/miembros/" + e.gestor),
				post("/api/miembros").contentType(MediaType.APPLICATION_JSON).content("{}"),
				put("/api/miembros/" + responsable + "/sedes").contentType(MediaType.APPLICATION_JSON)
						.content("{\"sedeIds\":[" + e.sB + "]}"),
				put("/api/miembros/" + responsable + "/roles/ADMINISTRADOR"));
		for (MockHttpServletRequestBuilder ruta : rutas) {
			MvcResult r = peticion(ruta, tR);
			assertEquals(403, r.getResponse().getStatus(),
					r.getRequest().getMethod() + " " + r.getRequest().getRequestURI());
		}
		assertEquals(List.of(e.sA), jdbc.queryForList("""
				SELECT ms.sede_id FROM membresia_sede ms JOIN membresia m ON m.id = ms.membresia_id
				WHERE m.usuario_id = ?""", Long.class, responsable), "no se da sedes a sí mismo");
		assertEquals(Boolean.TRUE, jdbc.queryForObject("SELECT activa FROM sede WHERE id = ?", Boolean.class, e.sA));
	}

	@Test
	void decision370_leerElCatalogoDeProductos_siguePermitidoPorLaFicha() throws Exception {
		assertEquals(200, estado(get("/api/productos/todos"), tR));
	}

	@Test
	void decision372_noAsignaProfesoresAGrupos() throws Exception {
		assertEquals(403, estado(get("/api/grupos/" + e.gA + "/profesores"), tR));
		assertEquals(403, estado(post("/api/grupos/" + e.gA + "/profesores").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"x@example.test\",\"rol\":\"TITULAR\"}"), tR));
		assertEquals(403, estado(delete("/api/grupos/" + e.gA + "/profesores/" + e.gestor), tR));
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM asignacion_profesor WHERE organizacion_id = ?",
				Integer.class, e.org));
		assertFalse(leer(get("/api/auth/permisos"), tR).get("capacidades").toString().contains("ASIGNAR_PROFESORES"));
	}

	@Test
	void decision369_noCambiaDeSedeAUnAlumno() throws Exception {
		assertEquals(403, estado(get("/api/alumnos/" + e.aA + "/cambio-sede?sedeId=" + e.sB), tR));
		assertEquals(403, estado(put("/api/alumnos/" + e.aA + "/sede").contentType(MediaType.APPLICATION_JSON)
				.content("{\"sedeId\":" + e.sB + "}"), tR));
		assertEquals(e.sA, e.sedeDe(e.aA));
	}

	// ------------------------------------------------------------------ utilidades

	private long cargo(EscuelaConSedesDePrueba escuela, long alumno, String precio, Long sede) {
		jdbc.update("""
				INSERT INTO producto_alumno (alumno_id, concepto, precio, cantidad, pagado, fecha_asignacion,
				  organizacion_id, sede_id)
				VALUES (?, ?, ?, 1, 0, '2024-01-02 12:00:00', ?, ?)""", alumno, CONCEPTO, new BigDecimal(precio),
				escuela.org, sede);
		return jdbc.queryForObject("SELECT MAX(id) FROM producto_alumno WHERE organizacion_id = ?", Long.class,
				escuela.org);
	}

	private JsonNode leer(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		MvcResult r = peticion(peticion, token);
		assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		return json.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8));
	}

	private String texto(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		MvcResult r = peticion(peticion, token);
		assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		return r.getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

	private int estado(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		return peticion(peticion, token).getResponse().getStatus();
	}

	private MvcResult peticion(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		return mvc.perform(peticion.cookie(new Cookie("jwt", token))).andReturn();
	}

	private static Set<Long> ids(JsonNode lista, String campo) {
		Set<Long> resultado = new HashSet<>();
		lista.forEach(n -> resultado.add(n.get(campo).asLong()));
		return resultado;
	}
}
