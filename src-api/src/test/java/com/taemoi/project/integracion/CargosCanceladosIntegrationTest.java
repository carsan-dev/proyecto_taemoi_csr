package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import com.taemoi.project.repositories.TarifaRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.GrupoService;
import com.taemoi.project.services.JwtService;

import jakarta.servlet.http.Cookie;

/**
 * Pieza 6b-2c contra MySQL: cargos cancelados. Escuela sintética de dos sedes ({@link EscuelaConSedesDePrueba}) con un
 * profesor (prof1, grupo gA de la sede A), el responsable de la sede A, el gestor y el propietario; y otra escuela para
 * el aislamiento. Datos inventados; nada en la organización 1 (Moiskimdo), cuyos oráculos siguen sin cancelados.
 *
 * <ul>
 * <li>Cancelar y reactivar: rutas, motivo, permisos por la sede del cargo, registro de acciones, JSON aditivo.</li>
 * <li>Invariante «cancelado ⇒ no pagado y sin cobro ACTIVO»: cancelar un pagado → 409; cobrar un cancelado → 409 por
 * el PUT (gestión y responsable), el profesor y la puerta de la convocatoria; examen con inscripción → 400.</li>
 * <li>Lectores: un cancelado no cuenta como pendiente ni deuda (resumen, movimientos, CSV, deudas, reservas, pendientes
 * del profesor) y sale «Cancelado» sin sumar en los PDF de mensualidades; cuenta como existente al generar.</li>
 * <li>Profesor: deshacer con NO_HUBO_VENTA (anula y cancela su venta) y AUN_NO_PAGADO (como en la 6b-2).</li>
 * <li>DELETE manual: solo el propietario y solo cargos que nunca se cobraron.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CargosCanceladosIntegrationTest extends MySqlIntegrationTest {

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;
	@Autowired TarifaRepository tarifas;

	@MockitoBean JavaMailSender mailSender;

	EscuelaConSedesDePrueba e;
	EscuelaConSedesDePrueba otra;
	long propietario, prof1, producto;
	String tGestor, tPropietario, tResp, tProf1, tGestorOtra;

	@BeforeAll
	void preparar() {
		e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-6b2c");
		otra = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-6b2c-otra");
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
		tGestorOtra = otra.token(jwtService, usuarios, otra.gestor);
		jdbc.update("INSERT INTO producto (concepto, precio, codigo, organizacion_id) VALUES ('CAMISETA 6B2C', 10.5, "
				+ "NULL, ?)", e.org);
		producto = jdbc.queryForObject("SELECT MAX(id) FROM producto WHERE organizacion_id = ?", Long.class, e.org);
	}

	@AfterEach
	void sinFugas() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
		SecurityContextHolder.clearContext();
	}

	// ------------------------------------------------------------------ cancelar y reactivar

	@Test
	void cancelarYReactivar_pendiente_conRegistroYJsonAditivo() throws Exception {
		long alumno = alumnoEnGrupo("E6CCANCELA", e.sA, e.gA);
		long cargo = cargo(alumno);
		Set<String> camposVigente = campos(fichaDe(alumno, cargo));
		assertFalse(camposVigente.contains("canceladoEn"), camposVigente.toString());
		assertFalse(camposVigente.contains("motivoCancelacion"));
		assertFalse(camposVigente.contains("canceladoPorUsuarioId"));

		JsonNode cancelado = leer(cancelar(cargo, "  se dio de baja antes de empezar  "), tGestor, 200);
		assertNotNull(cancelado.get("canceladoEn"));
		assertEquals("se dio de baja antes de empezar", cancelado.get("motivoCancelacion").asText(), "sin espacios");
		assertEquals(e.gestor, cancelado.get("canceladoPorUsuarioId").asLong());
		assertFalse(cancelado.get("pagado").asBoolean());
		Map<String, Object> fila = filaCargo(cargo);
		assertNotNull(fila.get("cancelado_en"));
		assertEquals(e.gestor, numero(fila, "cancelado_por_usuario_id"));
		JsonNode enFicha = fichaDe(alumno, cargo);
		Set<String> camposCancelado = new HashSet<>(camposVigente);
		camposCancelado.addAll(Set.of("canceladoEn", "canceladoPorUsuarioId", "motivoCancelacion"));
		assertEquals(camposCancelado, campos(enFicha), "solo se añaden los tres campos");
		assertEquals("CARGO CARGO_CANCELADO sede=#" + e.sA + " cargo=#" + cargo, registro("CARGO_CANCELADO", cargo));

		assertEquals(409, estado(cancelar(cargo, "otra vez"), tGestor), "ya cancelado");

		JsonNode reactivado = leer(post("/api/productos-alumno/" + cargo + "/reactivar"), tGestor, 200);
		assertFalse(reactivado.has("canceladoEn"));
		assertFalse(reactivado.has("motivoCancelacion"));
		assertFalse(reactivado.has("canceladoPorUsuarioId"));
		fila = filaCargo(cargo);
		assertNull(fila.get("cancelado_en"));
		assertNull(fila.get("cancelado_por_usuario_id"));
		assertNull(fila.get("motivo_cancelacion"));
		assertEquals("CARGO CARGO_REACTIVADO sede=#" + e.sA + " cargo=#" + cargo, registro("CARGO_REACTIVADO", cargo));
		assertEquals(409, estado(post("/api/productos-alumno/" + cargo + "/reactivar"), tGestor), "no cancelado");

		// Reactivado = pendiente: se cobra como siempre.
		assertEquals(200, estado(pago(cargo, "{\"pagado\":true}"), tGestor));
		assertEquals(1, cobros(cargo).size());
	}

	@Test
	void cancelar_motivoObligatorioYHasta500() throws Exception {
		long cargo = cargo(alumnoEnGrupo("E6CMOTIVO", e.sA, e.gA));
		assertEquals(400, estado(post("/api/productos-alumno/" + cargo + "/cancelar"), tGestor), "sin cuerpo");
		assertEquals(400, estado(cancelar(cargo, null), tGestor));
		assertEquals(400, estado(cancelar(cargo, "   "), tGestor));
		assertEquals(400, estado(cancelar(cargo, "x".repeat(501)), tGestor));
		assertNull(filaCargo(cargo).get("cancelado_en"));
		assertEquals(200, estado(cancelar(cargo, "x".repeat(500)), tGestor));
		assertEquals(404, estado(cancelar(999999999L, "m"), tGestor));
	}

	@Test
	void permisos_porLaSedeDelCargo() throws Exception {
		long enA = cargo(alumnoEnGrupo("E6CPERMA", e.sA, e.gA));
		long enB = cargo(e.alumno("E6CPERMB" + e.marca, e.sB, true));
		long sinSede = cargo(e.aNada);

		assertEquals(200, estado(cancelar(enA, "m"), tResp), "responsable de la sede del cargo");
		assertEquals(200, estado(post("/api/productos-alumno/" + enA + "/reactivar"), tResp));
		assertEquals(403, estado(cancelar(enB, "m"), tResp), "responsable de otra sede");
		assertEquals(403, estado(cancelar(sinSede, "m"), tResp), "cargo sin sede: solo gestión");
		assertEquals(403, estado(cancelar(enA, "m"), tProf1), "profesor");
		assertEquals(200, estado(cancelar(enB, "m"), tPropietario));
		assertEquals(403, estado(post("/api/productos-alumno/" + enB + "/reactivar"), tResp));
		assertEquals(403, estado(post("/api/productos-alumno/" + enB + "/reactivar"), tProf1));
		assertEquals(200, estado(post("/api/productos-alumno/" + enB + "/reactivar"), tGestor));
		assertEquals(200, estado(cancelar(sinSede, "m"), tGestor));
		assertTrue(Set.of(401, 403).contains(estado(cancelar(enA, "m"), null)), "sin sesión");

		// Tenant: un cargo de otra escuela no existe para esta (404) y no cambia.
		long ajeno = cargoSql(otra, otra.aA, "AJENO 6B2C", 5.0, false, otra.sA);
		assertEquals(404, estado(cancelar(ajeno, "m"), tGestor));
		assertEquals(404, estado(post("/api/productos-alumno/" + ajeno + "/reactivar"), tGestor));
		assertNull(filaCargo(ajeno).get("cancelado_en"));
		assertEquals(200, estado(cancelar(ajeno, "m"), tGestorOtra), "su gestión sí");
	}

	// ------------------------------------------------------------------ invariante

	@Test
	void cancelarUnPagado_409SinCambios() throws Exception {
		long cargo = cargo(alumnoEnGrupo("E6CPAGADO", e.sA, e.gA));
		assertEquals(200, estado(pago(cargo, "{\"pagado\":true}"), tGestor));
		Map<String, Object> antes = filaCargo(cargo);
		MvcResult r = peticion(cancelar(cargo, "m"), tGestor);
		assertEquals(409, r.getResponse().getStatus());
		assertTrue(r.getResponse().getContentAsString().contains("revierta antes el cobro"),
				r.getResponse().getContentAsString());
		assertEquals(antes, filaCargo(cargo));
		assertEquals("ACTIVO", cobros(cargo).get(0).get("estado"));
	}

	@Test
	void cobrarUnCancelado_409PorTodasLasVias_soloSeCambianLasNotas() throws Exception {
		long alumno = alumnoEnGrupo("E6CCOBRAR", e.sA, e.gA);
		long cargo = cargo(alumno);
		assertEquals(200, estado(cancelar(cargo, "no viene"), tGestor));

		assertEquals(409, estado(pago(cargo, "{\"pagado\":true}"), tGestor), "PUT de la gestión");
		assertEquals(409, estado(pago(cargo, "{\"pagado\":true}"), tPropietario));
		assertEquals(409, estado(pago(cargo, "{\"pagado\":true}"), tResp), "PUT del responsable");
		assertEquals(409, estado(pago(cargo, "{\"precio\":1}"), tGestor), "otro campo");
		MvcResult r = peticion(post("/api/caja/profesor/cargos/" + cargo + "/cobrar"), tProf1);
		assertEquals(409, r.getResponse().getStatus(), "cobro del profesor");
		assertTrue(r.getResponse().getContentAsString().contains("cancelado"), r.getResponse().getContentAsString());
		assertFalse(pagado(cargo));
		assertTrue(cobros(cargo).isEmpty());

		// Las notas sí; y reenviar el cargo entero (como la pantalla, con pagado:false) también.
		assertEquals(200, estado(pago(cargo, "{\"notas\":\"avisado\"}"), tGestor));
		JsonNode entero = fichaDe(alumno, cargo);
		((com.fasterxml.jackson.databind.node.ObjectNode) entero).put("notas", "otra nota");
		assertEquals(200, estado(pago(cargo, json.writeValueAsString(entero)), tGestor));
		assertEquals("otra nota", filaCargo(cargo).get("notas"));
		assertNotNull(filaCargo(cargo).get("cancelado_en"), "sigue cancelado");
		// El profesor ya no lo ve entre sus pendientes.
		assertFalse(ids(leer(get("/api/caja/profesor/cargos-pendientes"), tProf1, 200), "cargoId").contains(cargo));
	}

	@Test
	void examenConInscripcion_noSeCancela400_yLaPuertaDeLaConvocatoriaNoCobraUnCancelado() throws Exception {
		long alumno = alumnoEnGrupo("E6CEXAMEN", e.sA, e.gA);
		long inscripcion = inscripcionConCargo(alumno);
		long cargo = jdbc.queryForObject("SELECT producto_alumno_id FROM alumno_convocatoria WHERE id = ?", Long.class,
				inscripcion);
		MvcResult r = peticion(cancelar(cargo, "m"), tGestor);
		assertEquals(400, r.getResponse().getStatus());
		assertTrue(r.getResponse().getContentAsString().contains("convocatoria"), r.getResponse().getContentAsString());
		assertNull(filaCargo(cargo).get("cancelado_en"));

		// Defensa en profundidad: si existiera un cargo cancelado unido a una inscripción, la puerta de la convocatoria
		// no lo cobra (guarda central de CobroService.registrar): 409 y todo como estaba.
		jdbc.update("UPDATE producto_alumno SET cancelado_en = NOW(6), motivo_cancelacion = 'm' WHERE id = ?", cargo);
		assertEquals(409, estado(put("/api/convocatorias/alumno/" + inscripcion).contentType(MediaType.APPLICATION_JSON)
				.content("{\"cuantiaExamen\":20.0,\"pagado\":true}"), tGestor));
		assertFalse(pagado(cargo));
		assertTrue(cobros(cargo).isEmpty());
		assertEquals(Boolean.FALSE, jdbc.queryForObject("SELECT pagado FROM alumno_convocatoria WHERE id = ?",
				Boolean.class, inscripcion));
		jdbc.update("UPDATE producto_alumno SET cancelado_en = NULL, motivo_cancelacion = NULL WHERE id = ?", cargo);
	}

	// ------------------------------------------------------------------ lectores

	@Test
	void lectores_unCanceladoNoEsPendienteNiDeuda_yLosPdfLoPintanSinSumar() throws Exception {
		long uno = alumnoEnGrupo("E6CLECUNO", e.sA, e.gA);
		long dos = alumnoEnGrupo("E6CLECDOS", e.sA, e.gA);
		long adUno = inscripcionTaekwondo(uno);
		long adDos = inscripcionTaekwondo(dos);
		String mes = "MENSUALIDAD MAYO 2032 - TAEKWONDO";
		long mUno = cargoSql(e, uno, adUno, mes, 35.0, "2032-05-01");
		long mDos = cargoSql(e, dos, adDos, mes, 35.0, "2032-05-01");
		long reserva = cargoSql(e, uno, null, "RESERVA DE PLAZA 2032/2033", 25.0, "2032-06-10");
		String nombreUno = "E6CLECUNO" + e.marca;
		try {
			JsonNode resumenAntes = leer(get("/api/tesoreria/resumen?ano=2032&soloActivos=false"), tGestor, 200);
			JsonNode sedeAntes = leer(get("/api/tesoreria/resumen?ano=2032&soloActivos=false&sedeId=" + e.sA),
					tGestor, 200);
			JsonNode movAntes = leer(get("/api/tesoreria/movimientos?ano=2032&soloActivos=false&size=200"), tGestor,
					200);
			JsonNode pendAntes = leer(get("/api/tesoreria/movimientos?ano=2032&soloActivos=false&pagado=false&size=1"),
					tGestor, 200);
			int lineasCsvAntes = texto(get("/api/tesoreria/export/csv?ano=2032&soloActivos=false")).split("\n").length;
			assertTrue(texto(get("/api/informes/deudas/csv?soloActivos=false")).contains(nombreUno));
			assertTrue(pdf("/api/informes/deudas?soloActivos=false").contains(nombreUno));
			assertTrue(pdf("/api/informes/reservas-plaza?temporada=2032/2033&soloActivos=false").contains(nombreUno));
			assertTrue(ids(leer(get("/api/caja/profesor/cargos-pendientes"), tProf1, 200), "cargoId").contains(mUno));
			String listadoAntes = pdf("/api/informes/listado-mensualidad-mensual?mesAno=2032-05&soloActivos=false");
			assertFalse(listadoAntes.contains("Cancelado"));
			String informeAntes = pdf("/api/informes/mensualidades?soloActivos=false");

			assertEquals(200, estado(cancelar(mUno, "baja"), tGestor));
			assertEquals(200, estado(cancelar(reserva, "baja"), tGestor));

			// Resumen (también por sede): dos movimientos y dos pendientes menos, 60 € menos, un alumno menos.
			for (JsonNode[] par : List.of(
					new JsonNode[] { resumenAntes, leer(get("/api/tesoreria/resumen?ano=2032&soloActivos=false"),
							tGestor, 200) },
					new JsonNode[] { sedeAntes, leer(get("/api/tesoreria/resumen?ano=2032&soloActivos=false&sedeId="
							+ e.sA), tGestor, 200) })) {
				JsonNode antes = par[0];
				JsonNode ahora = par[1];
				assertEquals(antes.get("totalMovimientos").asLong() - 2, ahora.get("totalMovimientos").asLong());
				assertEquals(antes.get("totalPendientes").asLong() - 2, ahora.get("totalPendientes").asLong());
				assertEquals(antes.get("totalPagados").asLong(), ahora.get("totalPagados").asLong());
				assertEquals(antes.get("importePendiente").asDouble() - 60.0, ahora.get("importePendiente").asDouble(),
						0.001);
				assertEquals(antes.get("importeTotal").asDouble() - 60.0, ahora.get("importeTotal").asDouble(), 0.001);
				assertEquals(antes.get("alumnosConPendientes").asLong() - 1, ahora.get("alumnosConPendientes").asLong());
			}
			JsonNode movAhora = leer(get("/api/tesoreria/movimientos?ano=2032&soloActivos=false&size=200"), tGestor,
					200);
			Set<Long> idsAhora = ids(movAhora.get("content"), "productoAlumnoId");
			assertTrue(ids(movAntes.get("content"), "productoAlumnoId").containsAll(List.of(mUno, reserva)));
			assertFalse(idsAhora.contains(mUno));
			assertFalse(idsAhora.contains(reserva));
			assertTrue(idsAhora.contains(mDos));
			assertEquals(movAntes.get("totalElements").asLong() - 2, movAhora.get("totalElements").asLong(),
					"la countQuery cuenta lo mismo que la página");
			assertEquals(pendAntes.get("totalElements").asLong() - 2,
					leer(get("/api/tesoreria/movimientos?ano=2032&soloActivos=false&pagado=false&size=1"), tGestor, 200)
							.get("totalElements").asLong());
			assertEquals(lineasCsvAntes - 2,
					texto(get("/api/tesoreria/export/csv?ano=2032&soloActivos=false")).split("\n").length);
			assertFalse(texto(get("/api/informes/deudas/csv?soloActivos=false")).contains(nombreUno), "deudas CSV");
			assertFalse(pdf("/api/informes/deudas?soloActivos=false").contains(nombreUno), "deudas PDF");
			assertFalse(pdf("/api/informes/reservas-plaza?temporada=2032/2033&soloActivos=false").contains(nombreUno),
					"reservas");
			Set<Long> pendientesProfesor = ids(leer(get("/api/caja/profesor/cargos-pendientes"), tProf1, 200),
					"cargoId");
			assertFalse(pendientesProfesor.contains(mUno));
			assertTrue(pendientesProfesor.contains(mDos));

			// Listado mensual: el alumno sigue, con «Cancelado» en la cuantía; el otro, con su cuantía.
			String listado = pdf("/api/informes/listado-mensualidad-mensual?mesAno=2032-05&soloActivos=false");
			assertTrue(listado.contains("PRUEBA" + nombreUno), listado);
			assertTrue(listado.contains("Cancelado"), listado);
			assertTrue(listado.contains("PRUEBAE6CLECDOS" + e.marca), listado);
			assertTrue(listado.contains("35.00EUR") || listado.contains("35,00EUR"), listado);
			// Informe de mensualidades: la fila «CANCELADO», una pendiente menos y 35 € menos en el total.
			String informe = pdf("/api/informes/mensualidades?soloActivos=false");
			assertTrue(informe.contains(nombreUno), "el alumno sigue apareciendo");
			assertEquals(veces(informeAntes, "CANCELADO") + 1, veces(informe, "CANCELADO"));
			assertEquals(contador(informeAntes, "Mensualidadespendientes:") - 1,
					contador(informe, "Mensualidadespendientes:"));
			assertEquals(contador(informeAntes, "Totaldemensualidades:") - 1, contador(informe, "Totaldemensualidades:"));
			// La ficha sí lo enseña (con su estado).
			assertNotNull(fichaDe(uno, mUno).get("canceladoEn"));
		} finally {
			for (long cargo : List.of(mUno, mDos, reserva)) {
				jdbc.update("DELETE FROM producto_alumno WHERE id = ?", cargo);
			}
		}
	}

	@Test
	void generacion_unaMensualidadCanceladaCuentaComoExistente() throws Exception {
		TenantContext.ejecutarCon(e.org, () -> DisciplinasDePrueba.tarifa(tarifas, "ADULTO", "35.00", null));
		long tarifa = jdbc.queryForObject("SELECT MAX(id) FROM tarifa WHERE organizacion_id = ? AND codigo = 'ADULTO'",
				Long.class, e.org);
		jdbc.update("INSERT INTO producto (concepto, precio, organizacion_id) VALUES ('MENSUALIDAD', 30.0, ?)", e.org);
		long alumno = alumnoEnGrupo("E6CGENERA", e.sA, e.gA);
		inscripcionTaekwondo(alumno);
		jdbc.update("UPDATE alumno_deporte SET tarifa_id = ?, cuantia_tarifa = 35.0 WHERE organizacion_id = ?", tarifa,
				e.org);
		String concepto = com.taemoi.project.utils.MensualidadUtils.formatearNombreMensualidad("2031-03")
				+ " - TAEKWONDO";

		assertEquals(200, estado(generar("/api/productos-alumno/mensualidades/individual?alumnoId=" + alumno,
				"2031-03"), tGestor));
		long cargo = jdbc.queryForObject("SELECT id FROM producto_alumno WHERE alumno_id = ? AND concepto = ?",
				Long.class, alumno, concepto);
		assertEquals(200, estado(cancelar(cargo, "este mes no viene"), tGestor));

		assertEquals(409, estado(generar("/api/productos-alumno/mensualidades/individual?alumnoId=" + alumno,
				"2031-03"), tGestor), "la individual lo ve como ya asignada");
		assertEquals(200, estado(generar("/api/productos-alumno/mensualidades/general", "2031-03"), tGestor));
		assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM producto_alumno WHERE alumno_id = ? AND concepto = ?",
				Long.class, alumno, concepto), "la general no la vuelve a crear");
		assertNotNull(filaCargo(cargo).get("cancelado_en"));
	}

	// ------------------------------------------------------------------ profesor: deshacer con motivo

	@Test
	void profesor_noHuboVenta_anulaElCobroYCancelaSuVenta() throws Exception {
		long alumno = alumnoEnGrupo("E6CVENTA", e.sA, e.gA);
		JsonNode venta = leer(venta(alumno), tProf1, 201);
		long cobro = venta.get("cobroId").asLong();
		long cargo = venta.get("cargoId").asLong();

		assertEquals(204, estado(deshacer(cobro, "{\"motivo\":\"NO_HUBO_VENTA\"}"), tProf1));
		Map<String, Object> fila = filaCargo(cargo);
		assertFalse((Boolean) fila.get("pagado"));
		assertNull(fila.get("fecha_pago"));
		assertNotNull(fila.get("cancelado_en"));
		assertEquals(prof1, numero(fila, "cancelado_por_usuario_id"));
		assertEquals("no hubo venta", fila.get("motivo_cancelacion"));
		Map<String, Object> filaCobro = jdbc.queryForMap("SELECT * FROM cobro WHERE id = ?", cobro);
		assertEquals("ANULADO", filaCobro.get("estado"));
		assertEquals("no hubo venta", filaCobro.get("motivo_anulacion"));
		assertEquals(prof1, numero(filaCobro, "anulado_por_usuario_id"));
		assertEquals("CARGO CARGO_CANCELADO sede=#" + e.sA + " cargo=#" + cargo, registro("CARGO_CANCELADO", cargo));
		// Ya no está en su caja ni en sus pendientes, y la gestión puede reactivarlo.
		assertFalse(ids(leer(get("/api/caja/profesor/cobros-pendientes-entrega"), tProf1, 200).get("cobros"),
				"cobroId").contains(cobro));
		assertFalse(ids(leer(get("/api/caja/profesor/cargos-pendientes"), tProf1, 200), "cargoId").contains(cargo));
		assertEquals(409, estado(deshacer(cobro, "{\"motivo\":\"NO_HUBO_VENTA\"}"), tProf1), "ya anulado");
		assertEquals(200, estado(post("/api/productos-alumno/" + cargo + "/reactivar"), tGestor));
	}

	@Test
	void profesor_noHuboVenta_soloSiEsSuVenta_yAunNoPagadoComoAntes() throws Exception {
		long alumno = alumnoEnGrupo("E6CNOVENTA", e.sA, e.gA);
		// Cargo creado por la gestión y cobrado por el profesor: no es su venta → 400, sin cambios.
		long cargo = cargo(alumno);
		long cobro = leer(post("/api/caja/profesor/cargos/" + cargo + "/cobrar"), tProf1, 201).get("cobroId").asLong();
		MvcResult r = peticion(deshacer(cobro, "{\"motivo\":\"NO_HUBO_VENTA\"}"), tProf1);
		assertEquals(400, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		assertTrue(pagado(cargo));
		assertEquals("ACTIVO", jdbc.queryForObject("SELECT estado FROM cobro WHERE id = ?", String.class, cobro));
		assertNull(filaCargo(cargo).get("cancelado_en"));
		assertEquals(400, estado(deshacer(cobro, "{\"motivo\":\"OTRO\"}"), tProf1), "motivo desconocido");
		assertTrue(pagado(cargo));
		// AUN_NO_PAGADO explícito: vuelve a pendiente, sin cancelar.
		assertEquals(204, estado(deshacer(cobro, "{\"motivo\":\"AUN_NO_PAGADO\"}"), tProf1));
		assertFalse(pagado(cargo));
		assertNull(filaCargo(cargo).get("cancelado_en"));
		assertEquals("cobro deshecho por el profesor",
				jdbc.queryForObject("SELECT motivo_anulacion FROM cobro WHERE id = ?", String.class, cobro));

		// Su venta deshecha sin cuerpo: como en la 6b-2 (pendiente, no cancelada).
		JsonNode venta = leer(venta(alumno), tProf1, 201);
		assertEquals(204, estado(post("/api/caja/profesor/cobros/" + venta.get("cobroId").asLong() + "/deshacer"),
				tProf1));
		Map<String, Object> filaVenta = filaCargo(venta.get("cargoId").asLong());
		assertFalse((Boolean) filaVenta.get("pagado"));
		assertNull(filaVenta.get("cancelado_en"));

		// Una venta cuyo cargo ya se cobró antes (primer cobro anulado) no es «su venta»: 400.
		long otraVenta = leer(post("/api/caja/profesor/cargos/" + venta.get("cargoId").asLong() + "/cobrar"), tProf1,
				201).get("cobroId").asLong();
		assertEquals(400, estado(deshacer(otraVenta, "{\"motivo\":\"NO_HUBO_VENTA\"}"), tProf1));

		// Venta entregada en caja: 409 sin cambios.
		JsonNode entregada = leer(venta(alumno), tProf1, 201);
		long cobroEntregado = entregada.get("cobroId").asLong();
		meterEnEntrega(cobroEntregado);
		assertEquals(409, estado(deshacer(cobroEntregado, "{\"motivo\":\"NO_HUBO_VENTA\"}"), tProf1));
		assertTrue(pagado(entregada.get("cargoId").asLong()));
		assertNull(filaCargo(entregada.get("cargoId").asLong()).get("cancelado_en"));
		// Un cobro ajeno, 403 (como en la 6b-2), con o sin motivo.
		assertEquals(403, estado(deshacer(cobroEntregado, "{\"motivo\":\"NO_HUBO_VENTA\"}"), tGestor));
	}

	// ------------------------------------------------------------------ DELETE manual

	@Test
	void borrar_soloElPropietario_ySoloCargosQueNuncaSeCobraron() throws Exception {
		long alumno = alumnoEnGrupo("E6CBORRA", e.sA, e.gA);
		long pendiente = cargo(alumno);
		assertEquals(403, estado(delete("/api/productos-alumno/" + pendiente), tGestor), "gestor");
		assertEquals(403, estado(delete("/api/productos-alumno/" + pendiente), tResp), "responsable de su sede");
		assertEquals(403, estado(delete("/api/productos-alumno/" + pendiente), tProf1), "profesor");
		assertEquals(1L, existe(pendiente));
		assertEquals(204, estado(delete("/api/productos-alumno/" + pendiente), tPropietario));
		assertEquals(0L, existe(pendiente));
		assertEquals("CARGO CARGO_BORRADO sede=#" + e.sA + " cargo=#" + pendiente, registro("CARGO_BORRADO", pendiente));

		long pagado = cargo(alumno);
		assertEquals(200, estado(pago(pagado, "{\"pagado\":true}"), tGestor));
		MvcResult r = peticion(delete("/api/productos-alumno/" + pagado), tPropietario);
		assertEquals(409, r.getResponse().getStatus(), "pagado");
		assertTrue(r.getResponse().getContentAsString().contains("Cancélelo"), r.getResponse().getContentAsString());
		// Revertido (su cobro queda ANULADO): se cobró alguna vez → 409.
		assertEquals(200, estado(pago(pagado, "{\"pagado\":false}"), tPropietario));
		assertEquals(409, estado(delete("/api/productos-alumno/" + pagado), tPropietario), "cobro anulado");
		assertEquals(1L, existe(pagado));
		assertEquals("ANULADO", cobros(pagado).get(0).get("estado"));

		long cancelado = cargo(alumno);
		assertEquals(200, estado(cancelar(cancelado, "error de alta"), tGestor));
		assertEquals(204, estado(delete("/api/productos-alumno/" + cancelado), tPropietario), "cancelado sin cobros");
		assertEquals(404, estado(delete("/api/productos-alumno/" + cancelado), tPropietario));
	}

	// ------------------------------------------------------------------ utilidades

	private long alumnoEnGrupo(String nombre, long sede, long grupo) {
		long alumno = e.alumno(nombre + e.marca, sede, true);
		jdbc.update("INSERT INTO alumno_grupo (grupo_id, alumno_id) VALUES (?, ?)", grupo, alumno);
		return alumno;
	}

	private long inscripcionTaekwondo(long alumno) {
		e.taekwondo(alumno, true);
		return jdbc.queryForObject("SELECT MAX(id) FROM alumno_deporte WHERE alumno_id = ?", Long.class, alumno);
	}

	/** Cargo pendiente (10,50) creado por la gestión como siempre. */
	private long cargo(long alumno) throws Exception {
		JsonNode creado = leer(post("/api/productos-alumno/alumno/" + alumno + "/producto/" + producto)
				.contentType(MediaType.APPLICATION_JSON).content("{\"cantidad\":1,\"pagado\":false}"), tGestor, 200);
		return creado.get("id").asLong();
	}

	private long cargoSql(EscuelaConSedesDePrueba x, long alumno, String concepto, double precio, boolean pagado,
			long sede) {
		jdbc.update("""
				INSERT INTO producto_alumno (alumno_id, concepto, precio, cantidad, pagado, fecha_asignacion,
				  organizacion_id, sede_id) VALUES (?, ?, ?, 1, ?, NOW(), ?, ?)""", alumno, concepto, precio, pagado,
				x.org, sede);
		return jdbc.queryForObject("SELECT MAX(id) FROM producto_alumno WHERE organizacion_id = ?", Long.class, x.org);
	}

	private long cargoSql(EscuelaConSedesDePrueba x, long alumno, Long inscripcion, String concepto, double precio,
			String fecha) {
		jdbc.update("""
				INSERT INTO producto_alumno (alumno_id, alumno_deporte_id, concepto, precio, cantidad, pagado,
				  fecha_asignacion, organizacion_id, sede_id)
				VALUES (?, ?, ?, ?, 1, 0, ?, ?, (SELECT sede_principal_id FROM alumno WHERE id = ?))""", alumno,
				inscripcion, concepto, precio, fecha, x.org, alumno);
		return jdbc.queryForObject("SELECT MAX(id) FROM producto_alumno WHERE organizacion_id = ?", Long.class, x.org);
	}

	private long inscripcionConCargo(long alumno) {
		jdbc.update("INSERT INTO convocatoria (fecha_convocatoria, organizacion_id, disciplina_id) VALUES "
				+ "('2026-06-01', ?, ?)", e.org, e.disciplina);
		long convocatoria = jdbc.queryForObject("SELECT MAX(id) FROM convocatoria WHERE organizacion_id = ?",
				Long.class, e.org);
		jdbc.update("""
				INSERT INTO producto_alumno (alumno_id, concepto, precio, cantidad, pagado, fecha_asignacion,
				  organizacion_id, sede_id)
				VALUES (?, 'DERECHO A EXAMEN 6B2C', 20.0, 1, 0, NOW(), ?,
				  (SELECT sede_principal_id FROM alumno WHERE id = ?))""", alumno, e.org, alumno);
		long cargo = jdbc.queryForObject("SELECT MAX(id) FROM producto_alumno WHERE organizacion_id = ?", Long.class,
				e.org);
		jdbc.update("""
				INSERT INTO alumno_convocatoria (cuantia_examen, pagado, alumno_id, convocatoria_id, grado_actual,
				  grado_siguiente, organizacion_id, producto_alumno_id) VALUES (20, 0, ?, ?, NULL, NULL, ?, ?)""",
				alumno, convocatoria, e.org, cargo);
		return jdbc.queryForObject("SELECT id FROM alumno_convocatoria WHERE alumno_id = ? AND convocatoria_id = ?",
				Long.class, alumno, convocatoria);
	}

	private void meterEnEntrega(long cobro) {
		jdbc.update("""
				INSERT INTO entrega_caja (organizacion_id, sede_id, entregado_por_usuario_id, recibido_por_usuario_id,
				  importe_declarado, importe_contado, fecha) VALUES (?, ?, ?, ?, 10.50, 10.50, NOW(6))""",
				e.org, e.sA, prof1, e.respA);
		long entrega = jdbc.queryForObject("SELECT MAX(id) FROM entrega_caja WHERE organizacion_id = ?", Long.class,
				e.org);
		jdbc.update("UPDATE cobro SET entrega_caja_id = ? WHERE id = ?", entrega, cobro);
	}

	private MockHttpServletRequestBuilder cancelar(long cargo, String motivo) throws Exception {
		Map<String, Object> cuerpo = new java.util.HashMap<>();
		cuerpo.put("motivo", motivo);
		return post("/api/productos-alumno/" + cargo + "/cancelar").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(cuerpo));
	}

	private static MockHttpServletRequestBuilder deshacer(long cobro, String cuerpo) {
		return post("/api/caja/profesor/cobros/" + cobro + "/deshacer").contentType(MediaType.APPLICATION_JSON)
				.content(cuerpo);
	}

	private MockHttpServletRequestBuilder venta(long alumno) {
		return post("/api/caja/profesor/alumnos/" + alumno + "/productos/" + producto + "/cobrar");
	}

	private static MockHttpServletRequestBuilder pago(long cargo, String cuerpo) {
		return put("/api/productos-alumno/" + cargo).contentType(MediaType.APPLICATION_JSON).content(cuerpo);
	}

	private static MockHttpServletRequestBuilder generar(String ruta, String mesAno) {
		return post(ruta).contentType(MediaType.TEXT_PLAIN).content(mesAno);
	}

	private JsonNode fichaDe(long alumno, long cargo) throws Exception {
		for (JsonNode c : leer(get("/api/productos-alumno/alumno/" + alumno), tGestor, 200)) {
			if (c.get("id").asLong() == cargo) {
				return c;
			}
		}
		throw new AssertionError("El cargo " + cargo + " no está en la ficha");
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

	private String texto(MockHttpServletRequestBuilder peticion) throws Exception {
		MvcResult r = peticion(peticion, tGestor);
		assertEquals(200, r.getResponse().getStatus());
		return new String(r.getResponse().getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8);
	}

	/** Texto del PDF sin espacios. */
	private String pdf(String ruta) throws Exception {
		MvcResult r = peticion(get(ruta), tGestor);
		assertEquals(200, r.getResponse().getStatus(), ruta);
		try (org.apache.pdfbox.pdmodel.PDDocument documento = org.apache.pdfbox.pdmodel.PDDocument
				.load(r.getResponse().getContentAsByteArray())) {
			return new org.apache.pdfbox.text.PDFTextStripper().getText(documento).replaceAll("\\s+", "");
		}
	}

	private static int veces(String texto, String buscado) {
		int n = 0;
		for (int i = texto.indexOf(buscado); i >= 0; i = texto.indexOf(buscado, i + 1)) {
			n++;
		}
		return n;
	}

	private static int contador(String texto, String etiqueta) {
		Matcher m = Pattern.compile(Pattern.quote(etiqueta) + "(\\d+)").matcher(texto);
		assertTrue(m.find(), etiqueta + " en " + texto);
		return Integer.parseInt(m.group(1));
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
		if (token != null) {
			peticion.cookie(new Cookie("jwt", token));
		}
		return mvc.perform(peticion).andReturn();
	}

	private static Set<String> campos(JsonNode nodo) {
		Set<String> campos = new HashSet<>();
		for (Iterator<String> it = nodo.fieldNames(); it.hasNext();) {
			campos.add(it.next());
		}
		return campos;
	}

	private static Set<Long> ids(JsonNode lista, String campo) {
		List<Long> ids = new ArrayList<>();
		lista.forEach(n -> ids.add(n.get(campo).asLong()));
		return new HashSet<>(ids);
	}

	private static long numero(Map<String, Object> fila, String columna) {
		return ((Number) fila.get(columna)).longValue();
	}
}
