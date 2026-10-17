package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.TarifaRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.GrupoService;
import com.taemoi.project.services.JwtService;

import jakarta.servlet.http.Cookie;

/**
 * Entrega 6-3 (entrega6-plan.md §3.4, §4.3; D4, D6-3, D6-9 a D6-13, D6-17) contra MySQL: el responsable de sede
 * gestiona la tesorería de SUS sedes. Resumen, movimientos, exportaciones e informes solo con los cargos de sus sedes
 * (por la sede del cargo); cargos sueltos y cobros solo de alumnos de su sede principal (un alumno visible por grupo
 * desde otra sede no se cobra); sin generaciones masivas ni edición del catálogo; en una sede inactiva no se crean
 * cargos pero se cobran los existentes; cada cargo nace con la sede principal del alumno y no se mueve. Las cuentas son
 * FAMILIA + RESPONSABLE_SEDE (D6-15). Escuela sintética de {@link EscuelaConSedesDePrueba}.
 *
 * <pre>
 * cargos iniciales (gestor, por la API)   aA: 10 pendiente + 25 pagado (A) · aB: 25 (B) · aGrupoA: 10 (B)
 *                                         aSinSede: 10 (sin sede) · aC: 25 (C, inactiva; por SQL)
 * </pre>
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ResponsableSedeTesoreriaIntegrationTest extends MySqlIntegrationTest {

	private static final int ANO = LocalDate.now().getYear();

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired AlumnoRepository alumnos;
	@Autowired GrupoService grupoService;
	@Autowired TarifaRepository tarifas;

	@MockitoBean JavaMailSender mailSender;

	EscuelaConSedesDePrueba e;
	String tokenA;
	String tokenAC;
	String tokenGestor;
	String tokenFamilia;
	long aC;
	long p10, p25, pMensualidad, pReserva;
	long cargoAPendiente, cargoAPagado, cargoB, cargoGrupoA, cargoSinSede, cargoC;

	@BeforeAll
	void preparar() throws Exception {
		e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-e6-tesoreria");
		TenantContext.ejecutarCon(e.org, () -> DisciplinasDePrueba.tarifa(tarifas, "ADULTO", "30.00", null));
		jdbc.update("UPDATE disciplina SET requiere_licencia = TRUE WHERE id = ?", e.disciplina);
		p10 = producto("CAMISETA IT", 10.0, null);
		p25 = producto("CHANDAL IT", 25.0, null);
		pMensualidad = producto("MENSUALIDAD", 30.0, null);
		pReserva = producto("RESERVA DE PLAZA", 20.0, null);
		producto("LICENCIA IT ADULTO", 40.0, "LICENCIA_TAEKWONDO_ADULTO");
		producto("LICENCIA IT INFANTIL", 35.0, "LICENCIA_TAEKWONDO_INFANTIL");
		aC = e.alumno("E6C" + e.marca, e.sC, true);
		jdbc.update("""
				INSERT INTO alumno_deporte (activo, apto_para_examen, alumno_id, competidor, principal, tiene_licencia,
				  organizacion_id, fecha_alta, fecha_alta_inicial, disciplina_id)
				VALUES (TRUE, 0, ?, 0, 1, 0, ?, '2023-09-01', '2023-09-01', ?)""", aC, e.org, e.disciplina);
		jdbc.update("UPDATE alumno_deporte SET cuantia_tarifa = 30.0 WHERE organizacion_id = ?", e.org);

		tokenA = e.token(jwtService, usuarios, e.respA);
		tokenAC = e.token(jwtService, usuarios, e.respAC);
		tokenGestor = e.token(jwtService, usuarios, e.gestor);
		tokenFamilia = e.token(jwtService, usuarios, e.cuentaFamilia(usuarios, "familiatesoreria"));

		cargoAPendiente = cargo(tokenGestor, e.aA, p10, false);
		cargoAPagado = cargo(tokenGestor, e.aA, p25, true);
		cargoB = cargo(tokenGestor, e.aB, p25, false);
		cargoGrupoA = cargo(tokenGestor, e.aGrupoA, p10, false);
		cargoSinSede = cargo(tokenGestor, e.aSinSede, p10, false);
		jdbc.update("""
				INSERT INTO producto_alumno (alumno_id, producto_id, concepto, precio, cantidad, pagado, fecha_asignacion,
				  organizacion_id, sede_id)
				VALUES (?, ?, 'CHANDAL IT', 25.0, 1, 0, NOW(), ?, ?)""", aC, p25, e.org, e.sC);
		cargoC = jdbc.queryForObject("SELECT MAX(id) FROM producto_alumno WHERE alumno_id = ?", Long.class, aC);
	}

	@AfterEach
	void sinFugaDeOrganizacion() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
	}

	// ------------------------------------------------------------------ sede del cargo (D6-9)

	@Test
	@Order(1)
	void cargos_nacenConLaSedePrincipalDelAlumno() {
		assertEquals(e.sA, sedeDe(cargoAPendiente));
		assertEquals(e.sA, sedeDe(cargoAPagado));
		assertEquals(e.sB, sedeDe(cargoB));
		assertEquals(e.sB, sedeDe(cargoGrupoA), "su sede principal (B), no la del grupo de A");
		assertNull(sedeDe(cargoSinSede), "alumno sin sede principal: cargo sin sede");
	}

	// ------------------------------------------------------------------ resumen y movimientos (D6-12)

	@Test
	@Order(2)
	void resumen_soloSusSedes_yGestionConDesglosePorSede() throws Exception {
		JsonNode deA = leer("/api/tesoreria/resumen", tokenA);
		assertEquals(2, deA.get("totalMovimientos").asLong());
		assertEquals(1, deA.get("totalPagados").asLong());
		assertEquals(35.0, deA.get("importeTotal").asDouble());
		assertEquals(25.0, deA.get("importePagado").asDouble());
		assertEquals(10.0, deA.get("importePendiente").asDouble());
		assertEquals(1, deA.get("alumnosConPendientes").asLong());
		assertEquals(1, deA.get("porSede").size(), "solo su sede");
		assertEquals(e.sA, deA.get("porSede").get(0).get("sedeId").asLong());
		assertEquals(35.0, deA.get("porSede").get(0).get("importeTotal").asDouble());

		JsonNode deAC = leer("/api/tesoreria/resumen", tokenAC);
		assertEquals(3, deAC.get("totalMovimientos").asLong(), "A y C (inactiva, se consulta su histórico)");
		assertEquals(60.0, deAC.get("importeTotal").asDouble());

		JsonNode gestion = leer("/api/tesoreria/resumen", tokenGestor);
		assertEquals(6, gestion.get("totalMovimientos").asLong());
		assertEquals(105.0, gestion.get("importeTotal").asDouble());
		assertEquals(80.0, gestion.get("importePendiente").asDouble());
		assertPorSedeSumaElTotal(gestion);
		List<String> filas = new ArrayList<>();
		gestion.get("porSede").forEach(f -> filas.add(f.get("sedeId").isNull() ? "sin" : f.get("sedeId").asText()));
		assertEquals(List.of(String.valueOf(e.sA), String.valueOf(e.sB), String.valueOf(e.sC), "sin"), filas,
				"todas las sedes de la escuela por id y después «Sin sede»");

		// Con período (otra ruta del servicio): mismas cifras.
		JsonNode deAEsteAno = leer("/api/tesoreria/resumen?ano=" + ANO, tokenA);
		assertEquals(35.0, deAEsteAno.get("importeTotal").asDouble());
		assertEquals(1, deAEsteAno.get("porSede").size());
		JsonNode gestionEsteAno = leer("/api/tesoreria/resumen?ano=" + ANO, tokenGestor);
		assertEquals(105.0, gestionEsteAno.get("importeTotal").asDouble());
		assertPorSedeSumaElTotal(gestionEsteAno);
	}

	@Test
	@Order(3)
	void resumenYMovimientos_filtroPorSede() throws Exception {
		JsonNode soloB = leer("/api/tesoreria/resumen?sedeId=" + e.sB, tokenGestor);
		assertEquals(2, soloB.get("totalMovimientos").asLong());
		assertEquals(35.0, soloB.get("importeTotal").asDouble());
		assertEquals(1, soloB.get("porSede").size());
		assertEquals(Set.of(cargoB, cargoGrupoA), cargos(leer("/api/tesoreria/movimientos?sedeId=" + e.sB, tokenGestor)));
		assertEquals(Set.of(cargoAPendiente, cargoAPagado),
				cargos(leer("/api/tesoreria/movimientos?sedeId=" + e.sA, tokenA)), "una de las suyas");
		assertEquals(403, estado(get("/api/tesoreria/resumen?sedeId=" + e.sB), tokenA), "otra sede");
		assertEquals(403, estado(get("/api/tesoreria/movimientos?sedeId=" + e.sB), tokenA));
		long umbrete = jdbc.queryForObject("SELECT MIN(id) FROM sede WHERE organizacion_id = 1", Long.class);
		assertEquals(400, estado(get("/api/tesoreria/resumen?sedeId=" + umbrete), tokenGestor), "sede de otra escuela");
		assertEquals(400, estado(get("/api/tesoreria/resumen?sedeId=" + umbrete), tokenA));
	}

	@Test
	@Order(4)
	void movimientos_soloSusSedes() throws Exception {
		JsonNode pagina = leer("/api/tesoreria/movimientos", tokenA);
		assertEquals(2, pagina.get("totalElements").asLong());
		assertEquals(Set.of(cargoAPendiente, cargoAPagado), cargos(pagina));
		pagina.get("content").forEach(m -> assertEquals(e.sA, m.get("sedeId").asLong()));
		assertEquals(Set.of(cargoAPendiente), cargos(leer("/api/tesoreria/movimientos?pagado=false&ano=" + ANO, tokenA)));
		assertEquals(Set.of(cargoAPendiente, cargoAPagado, cargoC), cargos(leer("/api/tesoreria/movimientos", tokenAC)));
		assertEquals(Set.of(cargoAPendiente, cargoAPagado, cargoB, cargoGrupoA, cargoSinSede, cargoC),
				cargos(leer("/api/tesoreria/movimientos?size=200", tokenGestor)));
		// Página 2 de tamaño 1: el recuento es el de sus sedes.
		JsonNode segunda = leer("/api/tesoreria/movimientos?page=2&size=1", tokenA);
		assertEquals(2, segunda.get("totalElements").asLong());
		assertEquals(1, segunda.get("content").size());
	}

	@Test
	@Order(5)
	void exportaciones_soloSusSedes() throws Exception {
		String csv = texto(get("/api/tesoreria/export/csv"), tokenA);
		assertEquals(3, csv.strip().split("\n").length, "cabecera y sus dos cargos");
		assertFalse(csv.contains("E6B" + e.marca));
		String gestion = texto(get("/api/tesoreria/export/csv"), tokenGestor);
		assertEquals(7, gestion.strip().split("\n").length);
		assertEquals(200, estado(get("/api/tesoreria/export/pdf"), tokenA));
		assertEquals(200, estado(get("/api/tesoreria/anios-disponibles"), tokenA));
	}

	@Test
	@Order(6)
	void informes_deudasYListadoMensual_soloSusSedes_restoDenegado() throws Exception {
		String deudas = texto(get("/api/informes/deudas/csv?soloActivos=false"), tokenA);
		assertTrue(deudas.contains("E6A" + e.marca), deudas);
		assertFalse(deudas.contains("E6B" + e.marca), "deuda de otra sede");
		assertFalse(deudas.contains("E6GRUPOA" + e.marca), "alumno visible por grupo, deuda de su sede B");
		assertFalse(deudas.contains("E6SINSEDE" + e.marca));
		String deudasGestion = texto(get("/api/informes/deudas/csv?soloActivos=false"), tokenGestor);
		for (String alumno : List.of("E6A", "E6B", "E6GRUPOA", "E6SINSEDE", "E6C")) {
			assertTrue(deudasGestion.contains(alumno + e.marca), alumno);
		}
		String deudasPdf = textoPdf(get("/api/informes/deudas?soloActivos=false"), tokenA);
		assertTrue(deudasPdf.contains("E6A" + e.marca), deudasPdf);
		assertFalse(deudasPdf.contains("E6B" + e.marca));
		assertFalse(deudasPdf.contains("E6GRUPOA" + e.marca));
		// Listado mensual: las filas de los alumnos de su sede principal (D6-3), no los visibles por grupo.
		String listado = textoPdf(get("/api/informes/listado-mensualidad-mensual?mesAno=" + ANO + "-01&soloActivos=false"),
				tokenA);
		assertTrue(listado.contains("E6A" + e.marca), listado);
		assertFalse(listado.contains("E6B" + e.marca));
		assertFalse(listado.contains("E6GRUPOA" + e.marca));
		String listadoGestion = textoPdf(
				get("/api/informes/listado-mensualidad-mensual?mesAno=" + ANO + "-01&soloActivos=false"), tokenGestor);
		assertTrue(listadoGestion.contains("E6B" + e.marca) && listadoGestion.contains("E6GRUPOA" + e.marca));
		for (String ruta : List.of("/api/informes/mensualidades", "/api/informes/productos", "/api/informes/licencias",
				"/api/informes/reservas-plaza/temporadas", "/api/informes/competidores")) {
			assertEquals(403, estado(get(ruta), tokenA), ruta);
		}
	}

	@Test
	@Order(7)
	void productosDeAlumno_soloSuSedePrincipal() throws Exception {
		assertEquals(Set.of(cargoAPendiente, cargoAPagado), ids(leer("/api/productos-alumno/alumno/" + e.aA, tokenA)));
		assertEquals(403, estado(get("/api/productos-alumno/alumno/" + e.aB), tokenA));
		assertEquals(403, estado(get("/api/productos-alumno/alumno/" + e.aGrupoA), tokenA), "visible por grupo (D6-3)");
		assertEquals(403, estado(get("/api/productos-alumno/alumno/" + e.aSinSede), tokenA), "sin sede principal");
		assertEquals(403, estado(get("/api/productos-alumno/alumno/" + e.propioA), tokenA),
				"su alumno de familia no le abre la tesorería");
		assertEquals(Set.of(cargoB), ids(leer("/api/productos-alumno/alumno/" + e.aB, tokenGestor)));
		JsonNode deA = leer("/api/productos-alumno/alumno/" + e.aA, tokenGestor);
		deA.forEach(c -> assertEquals(e.sA, c.get("sedeId").asLong(), "sedeId aditivo en el JSON"));
	}

	// ------------------------------------------------------------------ cobros (D6-3)

	@Test
	@Order(8)
	void cobrar_soloCargosDeSusSedes_sinRevertirNiBorrarCobrados() throws Exception {
		assertEquals(403, estado(cobro(cargoB, true), tokenA));
		assertEquals(403, estado(cobro(cargoGrupoA, true), tokenA), "alumno visible por grupo de otra sede");
		assertEquals(403, estado(cobro(cargoSinSede, true), tokenA));
		assertEquals(403, estado(cobro(cargoAPagado, false), tokenA), "revertir un cobro: solo el propietario");
		assertTrue(pagado(cargoAPagado));
		assertEquals(403, estado(delete("/api/productos-alumno/" + cargoAPagado), tokenA),
				"borrar un cargo cobrado equivale a revertirlo");
		assertEquals(403, estado(delete("/api/productos-alumno/" + cargoB), tokenA));

		MvcResult cobrado = peticion(cobro(cargoAPendiente, true), tokenA);
		assertEquals(200, cobrado.getResponse().getStatus(), cobrado.getResponse().getContentAsString());
		assertTrue(pagado(cargoAPendiente));
		assertEquals(e.sA, sedeDe(cargoAPendiente), "cobrar no cambia la sede del cargo");
		// Devolver el estado para los demás tests (la gestión revierte solo con ROLE_ADMIN: por SQL).
		jdbc.update("UPDATE producto_alumno SET pagado = 0, fecha_pago = NULL WHERE id = ?", cargoAPendiente);
	}

	@Test
	@Order(9)
	void sedeInactiva_noSeCreanCargosPeroSeCobranLosExistentes() throws Exception {
		assertEquals(400, estado(asignar(aC, p10), tokenAC), "D6-17");
		assertEquals(400, estado(asignar(aC, p10), tokenGestor), "para todos los roles");
		assertEquals(400, estado(post("/api/productos-alumno/mensualidades/individual?alumnoId=" + aC)
				.contentType(MediaType.TEXT_PLAIN).content(ANO + "-02"), tokenAC), "no 500");
		assertEquals(400, estado(post("/api/productos-alumno/" + aC + "/reservar-plaza?pagado=false"), tokenGestor));
		assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM producto_alumno WHERE alumno_id = ? AND id <> ?",
				Long.class, aC, cargoC));

		MvcResult cobrado = peticion(cobro(cargoC, true), tokenAC);
		assertEquals(200, cobrado.getResponse().getStatus(), cobrado.getResponse().getContentAsString());
		assertTrue(pagado(cargoC));
		assertEquals(403, estado(cobro(cargoC, true), tokenA), "respA no tiene la sede C");
		jdbc.update("UPDATE producto_alumno SET pagado = 0, fecha_pago = NULL WHERE id = ?", cargoC);
	}

	// ------------------------------------------------------------------ cargos sueltos (D6-3) y masivos (D6-10)

	@Test
	@Order(10)
	void cargosSueltos_soloAlumnosDeSuSedePrincipal() throws Exception {
		MvcResult suelto = peticion(asignar(e.aA, p10), tokenA);
		assertEquals(200, suelto.getResponse().getStatus(), suelto.getResponse().getContentAsString());
		JsonNode creado = json.readTree(suelto.getResponse().getContentAsString());
		assertEquals(e.sA, creado.get("sedeId").asLong());
		assertEquals(e.sA, sedeDe(creado.get("id").asLong()));

		assertEquals(403, estado(asignar(e.aB, p10), tokenA));
		assertEquals(403, estado(asignar(e.aGrupoA, p10), tokenA), "visible por grupo desde A, su sede principal es B");
		assertEquals(403, estado(asignar(e.aSinSede, p10), tokenA));
		assertEquals(403, estado(post("/api/productos-alumno/" + e.aB + "/reservar-plaza?pagado=false"), tokenA));
		assertEquals(403, estado(post("/api/productos-alumno/mensualidades/individual?alumnoId=" + e.aB)
				.contentType(MediaType.TEXT_PLAIN).content(ANO + "-03"), tokenA));
		assertEquals(403, estado(post("/api/productos-alumno/licencias/individual?alumnoId=" + e.aB + "&ano=" + ANO),
				tokenA));
		assertEquals(403, estado(post("/api/productos-alumno/" + e.aGrupoA + "/renovar-licencia"), tokenA));

		long antes = cargosDe(e.aA);
		assertEquals(200, estado(post("/api/productos-alumno/" + e.aA + "/reservar-plaza?pagado=false"), tokenA));
		assertEquals(200, estado(post("/api/productos-alumno/mensualidades/individual?alumnoId=" + e.aA)
				.contentType(MediaType.TEXT_PLAIN).content(ANO + "-03"), tokenA));
		assertEquals(200, estado(post("/api/productos-alumno/licencias/individual?alumnoId=" + e.aA + "&ano=" + ANO),
				tokenA));
		assertEquals(antes + 3, cargosDe(e.aA));
		assertEquals(0L, jdbc.queryForObject(
				"SELECT COUNT(*) FROM producto_alumno WHERE alumno_id = ? AND (sede_id IS NULL OR sede_id <> ?)",
				Long.class, e.aA, e.sA), "reserva, mensualidad y licencia en la sede A");
	}

	@Test
	@Order(11)
	void masivas_soloGestion_yCadaCargoEnLaSedeDeSuAlumno() throws Exception {
		assertEquals(403, estado(post("/api/productos-alumno/mensualidades/general")
				.contentType(MediaType.TEXT_PLAIN).content(ANO + "-04"), tokenA));
		assertEquals(403, estado(post("/api/productos-alumno/mensualidades/deporte?deporte=TAEKWONDO")
				.contentType(MediaType.TEXT_PLAIN).content(ANO + "-04"), tokenA));
		assertEquals(403, estado(post("/api/productos-alumno/mensualidades/multi-deporte")
				.contentType(MediaType.TEXT_PLAIN).content(ANO + "-04"), tokenA));
		assertEquals(403, estado(post("/api/productos-alumno/licencias/general?ano=" + ANO), tokenA));

		MvcResult general = peticion(post("/api/productos-alumno/mensualidades/general")
				.contentType(MediaType.TEXT_PLAIN).content(ANO + "-05"), tokenGestor);
		assertEquals(200, general.getResponse().getStatus(), general.getResponse().getContentAsString());
		JsonNode respuesta = json.readTree(general.getResponse().getContentAsString());
		assertEquals("Mensualidades creadas correctamente.", respuesta.get("mensaje").asText(), "mensaje de siempre");
		assertEquals(1, respuesta.get("alumnosOmitidosSedeInactiva").asInt(), "D6-23: aC, de la sede C inactiva");
		assertEquals(2, respuesta.size(), "solo el campo nuevo");
		String concepto = "MENSUALIDAD MAYO " + ANO + " - TAEKWONDO";
		assertEquals(e.sA, sedeDeConcepto(e.aA, concepto));
		assertEquals(e.sB, sedeDeConcepto(e.aB, concepto));
		assertEquals(e.sB, sedeDeConcepto(e.aGrupoA, concepto));
		assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM producto_alumno WHERE alumno_id = ? AND concepto = ?",
				Long.class, aC, concepto), "D6-17: la masiva salta a los alumnos de una sede inactiva");

		MvcResult licencias = peticion(post("/api/productos-alumno/licencias/general?ano=" + ANO), tokenGestor);
		assertEquals(200, licencias.getResponse().getStatus(), licencias.getResponse().getContentAsString());
		assertEquals(1, json.readTree(licencias.getResponse().getContentAsString()).get("alumnosOmitidosSedeInactiva")
				.asInt());
		assertEquals(0L, jdbc.queryForObject("""
				SELECT COUNT(*) FROM producto_alumno pa JOIN alumno a ON a.id = pa.alumno_id
				WHERE a.organizacion_id = ? AND pa.concepto LIKE 'LICENCIA IT%'
				  AND (pa.sede_id IS NULL OR pa.sede_id <> a.sede_principal_id)""", Long.class, e.org));
		assertTrue(jdbc.queryForObject("""
				SELECT COUNT(*) FROM producto_alumno WHERE alumno_id = ? AND concepto LIKE 'LICENCIA IT%'""",
				Long.class, e.aB) >= 1);
	}

	@Test
	@Order(12)
	void cambioDeSedeDelAlumno_noMueveSusCargos() throws Exception {
		long alumno = e.alumno("E6MUDANZA" + e.marca, e.sA, true);
		long antiguo = cargo(tokenGestor, alumno, p10, false);
		assertEquals(e.sA, sedeDe(antiguo));
		TenantContext.ejecutarCon(e.org, () -> alumnos.asignarSedePrincipal(alumno, e.sB));
		long nuevo = cargo(tokenGestor, alumno, p25, false);
		assertEquals(e.sA, sedeDe(antiguo), "el cargo antiguo sigue en A");
		assertEquals(e.sB, sedeDe(nuevo), "el nuevo, en su sede actual");
		// Un save de la entidad (cobrar) tampoco lo mueve.
		assertEquals(200, estado(cobro(antiguo, true), tokenGestor));
		assertEquals(e.sA, sedeDe(antiguo));
		// El responsable de A ya no le crea cargos ni ve su ficha de cobros, pero cobra el cargo de su sede.
		assertEquals(403, estado(asignar(alumno, p10), tokenA));
		jdbc.update("UPDATE producto_alumno SET pagado = 0, fecha_pago = NULL WHERE id = ?", antiguo);
		assertEquals(200, estado(cobro(antiguo, true), tokenA));
	}

	// ------------------------------------------------------------------ tarifa, catálogo y certificados

	@Test
	@Order(13)
	void tarifaYCuantia_soloSuSedePrincipal() throws Exception {
		assertEquals(200, estado(put("/api/alumnos/" + e.aA + "/deportes/TAEKWONDO/cuantia-tarifa")
				.contentType(MediaType.APPLICATION_JSON).content("{\"cuantiaTarifa\": 28.5}"), tokenA));
		assertEquals(28.5, jdbc.queryForObject("SELECT cuantia_tarifa FROM alumno_deporte WHERE alumno_id = ?",
				Double.class, e.aA));
		assertEquals(200, estado(put("/api/alumnos/" + e.aA + "/deportes/TAEKWONDO/tipo-tarifa")
				.contentType(MediaType.APPLICATION_JSON).content("{\"tipoTarifa\": \"ADULTO\"}"), tokenA));
		for (long otro : List.of(e.aB, e.aGrupoA, e.aSinSede)) {
			assertEquals(403, estado(put("/api/alumnos/" + otro + "/deportes/TAEKWONDO/cuantia-tarifa")
					.contentType(MediaType.APPLICATION_JSON).content("{\"cuantiaTarifa\": 1}"), tokenA), "alumno " + otro);
			assertEquals(403, estado(put("/api/alumnos/" + otro + "/deportes/TAEKWONDO/tipo-tarifa")
					.contentType(MediaType.APPLICATION_JSON).content("{\"tipoTarifa\": \"ADULTO\"}"), tokenA));
		}
	}

	@Test
	@Order(14)
	void catalogo_leeConTarifasSinEditar() throws Exception {
		assertEquals(200, estado(get("/api/productos"), tokenA));
		assertEquals(200, estado(get("/api/productos/todos"), tokenA));
		assertEquals(200, estado(get("/api/productos/" + p10), tokenA));
		assertEquals(403, estado(post("/api/productos").contentType(MediaType.APPLICATION_JSON)
				.content("{\"concepto\":\"X\",\"precio\":1}"), tokenA));
		assertEquals(403, estado(put("/api/productos/" + p10).contentType(MediaType.APPLICATION_JSON)
				.content("{\"concepto\":\"X\",\"precio\":1}"), tokenA));
		assertEquals(403, estado(delete("/api/productos/" + p10), tokenA));
		assertEquals(1, leer("/api/catalogo", tokenA).get("tarifas").size(), "D6-11: con tarifas");
		assertEquals(0, leer("/api/catalogo", tokenFamilia).get("tarifas").size(), "la familia, sin tarifas");
	}

	@Test
	@Order(15)
	void certificados_soloAlumnosDeSuSedePrincipal() throws Exception {
		assertEquals(200, estado(certificados(e.aA), tokenA));
		assertEquals(403, estado(certificados(e.aA, e.aB), tokenA));
		assertEquals(403, estado(certificados(e.aGrupoA), tokenA));
		assertEquals(200, estado(certificados(e.aA, e.aB), tokenGestor));
	}

	// ------------------------------------------------------------------ otras cuentas y otra escuela

	@Test
	@Order(16)
	void familiaSinTesoreria_yOtraEscuelaDenegada() throws Exception {
		for (String ruta : List.of("/api/tesoreria/resumen", "/api/tesoreria/movimientos", "/api/productos",
				"/api/informes/deudas", "/api/productos-alumno/alumno/" + e.propioA)) {
			assertEquals(403, estado(get(ruta), tokenFamilia), ruta);
		}
		// Otra escuela con una sede del MISMO id de alcance no sirve: alumno y cargo de otra organización.
		String slug = "it-e6-tes-otra-" + e.marca;
		jdbc.update("INSERT INTO organizacion (slug, nombre) VALUES (?, 'Otra escuela IT')", slug);
		long otra = jdbc.queryForObject("SELECT id FROM organizacion WHERE slug = ?", Long.class, slug);
		long expediente = jdbc.queryForObject("SELECT COALESCE(MAX(numero_expediente), 60000) + 1 FROM alumno",
				Long.class);
		jdbc.update("""
				INSERT INTO alumno (activo, apellidos, autorizacion_web, direccion, fecha_nacimiento, nombre,
				  numero_expediente, telefono, tiene_derecho_examen, organizacion_id, sede_principal_id)
				VALUES (1, 'PRUEBA', 1, 'Calle 1', '2010-01-01', 'E6OTRA', ?, '600000000', 0, ?, ?)""", expediente, otra,
				e.sA);
		long alumnoOtra = jdbc.queryForObject("SELECT id FROM alumno WHERE organizacion_id = ?", Long.class, otra);
		jdbc.update("""
				INSERT INTO producto_alumno (alumno_id, concepto, precio, cantidad, pagado, organizacion_id, sede_id)
				VALUES (?, 'OTRA', 1.0, 1, 0, ?, ?)""", alumnoOtra, otra, e.sA);
		long cargoOtra = jdbc.queryForObject("SELECT id FROM producto_alumno WHERE organizacion_id = ?", Long.class,
				otra);
		assertEquals(403, estado(cobro(cargoOtra, true), tokenA), "cargo de otra escuela (aunque su sede_id sea A)");
		assertEquals(403, estado(delete("/api/productos-alumno/" + cargoOtra), tokenA));
		assertEquals(403, estado(get("/api/productos-alumno/alumno/" + alumnoOtra), tokenA));
		assertEquals(403, estado(asignar(alumnoOtra, p10), tokenA));
		assertFalse(pagado(cargoOtra));
	}

	// ------------------------------------------------------------------ D6-20 y cargos sin alumno

	@Test
	@Order(17)
	void cargoCobrado_elResponsableSoloCambiaLasNotas() throws Exception {
		assertTrue(pagado(cargoAPagado));
		assertEquals(200, estado(editar(cargoAPagado, "{\"notas\":\"entregado en mano\"}"), tokenA));
		assertEquals("entregado en mano",
				jdbc.queryForObject("SELECT notas FROM producto_alumno WHERE id = ?", String.class, cargoAPagado));
		// La pantalla reenvía el cargo entero: con los mismos valores y otras notas, también vale.
		JsonNode guardado = null;
		for (JsonNode c : leer("/api/productos-alumno/alumno/" + e.aA, tokenA)) {
			if (c.get("id").asLong() == cargoAPagado) {
				guardado = c;
			}
		}
		((com.fasterxml.jackson.databind.node.ObjectNode) guardado).put("notas", "otra nota");
		MvcResult entero = peticion(editar(cargoAPagado, json.writeValueAsString(guardado)), tokenA);
		assertEquals(200, entero.getResponse().getStatus(), entero.getResponse().getContentAsString());

		for (String cambio : List.of("{\"precio\":0}", "{\"precio\":24.99}", "{\"cantidad\":2}",
				"{\"concepto\":\"OTRA COSA\"}", "{\"fechaPago\":\"2020-01-01\"}", "{\"fechaAsignacion\":\"2020-01-01\"}",
				"{\"pagado\":false}", "{\"notas\":\"x\",\"precio\":1}")) {
			assertEquals(403, estado(editar(cargoAPagado, cambio), tokenA), cambio);
		}
		assertEquals(25.0, jdbc.queryForObject("SELECT precio FROM producto_alumno WHERE id = ?", Double.class,
				cargoAPagado));
		assertTrue(pagado(cargoAPagado));

		// Un cargo pendiente sí lo edita; la gestión edita también los cobrados (como hoy).
		assertEquals(200, estado(editar(cargoAPendiente, "{\"precio\":11}"), tokenA));
		assertEquals(200, estado(editar(cargoAPagado, "{\"precio\":26}"), tokenGestor));
		jdbc.update("UPDATE producto_alumno SET precio = 25.0 WHERE id = ?", cargoAPagado);
		jdbc.update("UPDATE producto_alumno SET precio = 10.0 WHERE id = ?", cargoAPendiente);
	}

	@Test
	@Order(18)
	void cargoSinAlumno_elResponsableRecibe400SinCambiarNada() throws Exception {
		long inscripcion = jdbc.queryForObject("SELECT MIN(id) FROM alumno_deporte WHERE alumno_id = ?", Long.class,
				e.aA);
		jdbc.update("""
				INSERT INTO producto_alumno (alumno_id, alumno_deporte_id, concepto, precio, cantidad, pagado,
				  fecha_asignacion, organizacion_id, sede_id)
				VALUES (NULL, ?, 'DERECHO A EXAMEN AZUL - TAEKWONDO', 15.0, 1, 0, NOW(), ?, ?)""", inscripcion, e.org,
				e.sA);
		long cargo = jdbc.queryForObject("SELECT MAX(id) FROM producto_alumno WHERE alumno_deporte_id = ?", Long.class,
				inscripcion);
		assertEquals(400, estado(cobro(cargo, true), tokenA), "no 500");
		assertFalse(pagado(cargo));
		assertEquals(400, estado(delete("/api/productos-alumno/" + cargo), tokenA), "no 500");
		assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM producto_alumno WHERE id = ?", Long.class, cargo));
		jdbc.update("DELETE FROM producto_alumno WHERE id = ?", cargo);
	}

	// ------------------------------------------------------------------ utilidades

	private MockHttpServletRequestBuilder editar(long cargo, String cuerpo) {
		return put("/api/productos-alumno/" + cargo).contentType(MediaType.APPLICATION_JSON).content(cuerpo);
	}

	private void assertPorSedeSumaElTotal(JsonNode resumen) {
		long movimientos = 0;
		long pagados = 0;
		double importe = 0;
		double pendiente = 0;
		for (JsonNode fila : resumen.get("porSede")) {
			movimientos += fila.get("totalMovimientos").asLong();
			pagados += fila.get("totalPagados").asLong();
			importe += fila.get("importeTotal").asDouble();
			pendiente += fila.get("importePendiente").asDouble();
		}
		assertEquals(resumen.get("totalMovimientos").asLong(), movimientos);
		assertEquals(resumen.get("totalPagados").asLong(), pagados);
		assertEquals(resumen.get("importeTotal").asDouble(), importe, 0.0001);
		assertEquals(resumen.get("importePendiente").asDouble(), pendiente, 0.0001);
	}

	private long producto(String concepto, double precio, String codigo) {
		jdbc.update("INSERT INTO producto (concepto, precio, codigo, organizacion_id) VALUES (?, ?, ?, ?)", concepto,
				precio, codigo, e.org);
		return jdbc.queryForObject("SELECT MAX(id) FROM producto WHERE organizacion_id = ? AND concepto = ?",
				Long.class, e.org, concepto);
	}

	private long cargo(String token, long alumno, long producto, boolean pagado) throws Exception {
		MvcResult r = peticion(post("/api/productos-alumno/alumno/" + alumno + "/producto/" + producto)
				.contentType(MediaType.APPLICATION_JSON).content("{\"cantidad\":1,\"pagado\":" + pagado + "}"), token);
		assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		return json.readTree(r.getResponse().getContentAsString()).get("id").asLong();
	}

	private MockHttpServletRequestBuilder asignar(long alumno, long producto) {
		return post("/api/productos-alumno/alumno/" + alumno + "/producto/" + producto)
				.contentType(MediaType.APPLICATION_JSON).content("{\"cantidad\":1,\"pagado\":false}");
	}

	private MockHttpServletRequestBuilder cobro(long cargo, boolean pagado) {
		return put("/api/productos-alumno/" + cargo).contentType(MediaType.APPLICATION_JSON)
				.content("{\"pagado\":" + pagado + "}");
	}

	private MockHttpServletRequestBuilder certificados(Long... alumnos) throws Exception {
		return post("/api/tesoreria/certificados-cobros").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("alumnoIds", List.of(alumnos), "ano", ANO)));
	}

	private Long sedeDe(long cargo) {
		return jdbc.queryForObject("SELECT sede_id FROM producto_alumno WHERE id = ?", Long.class, cargo);
	}

	private Long sedeDeConcepto(long alumno, String concepto) {
		return jdbc.queryForObject("SELECT sede_id FROM producto_alumno WHERE alumno_id = ? AND concepto = ?",
				Long.class, alumno, concepto);
	}

	private boolean pagado(long cargo) {
		return Boolean.TRUE.equals(
				jdbc.queryForObject("SELECT pagado FROM producto_alumno WHERE id = ?", Boolean.class, cargo));
	}

	private long cargosDe(long alumno) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM producto_alumno WHERE alumno_id = ?", Long.class, alumno);
	}

	private static Set<Long> cargos(JsonNode pagina) {
		Set<Long> ids = new HashSet<>();
		pagina.get("content").forEach(m -> ids.add(m.get("productoAlumnoId").asLong()));
		return ids;
	}

	private static Set<Long> ids(JsonNode lista) {
		Set<Long> ids = new HashSet<>();
		lista.forEach(n -> ids.add(n.get("id").asLong()));
		return ids;
	}

	private int estado(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		return peticion(peticion, token).getResponse().getStatus();
	}

	private MvcResult peticion(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		return mvc.perform(peticion.cookie(new Cookie("jwt", token))).andReturn();
	}

	private String texto(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		MvcResult r = peticion(peticion, token);
		assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		return new String(r.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
	}

	/** Texto del PDF sin espacios (el maquetado parte las celdas). */
	private String textoPdf(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		MvcResult r = peticion(peticion, token);
		assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		try (org.apache.pdfbox.pdmodel.PDDocument pdf = org.apache.pdfbox.pdmodel.PDDocument
				.load(r.getResponse().getContentAsByteArray())) {
			return new org.apache.pdfbox.text.PDFTextStripper().getText(pdf).replaceAll("\\s+", "");
		}
	}

	private JsonNode leer(String ruta, String token) throws Exception {
		MvcResult r = peticion(get(ruta), token);
		assertEquals(200, r.getResponse().getStatus(), ruta + ": " + r.getResponse().getContentAsString());
		return json.readTree(r.getResponse().getContentAsString());
	}
}
