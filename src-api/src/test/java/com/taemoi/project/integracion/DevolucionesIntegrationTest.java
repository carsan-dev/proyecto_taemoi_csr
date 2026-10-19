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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
import com.taemoi.project.services.CobroService;
import com.taemoi.project.services.GrupoService;
import com.taemoi.project.services.JwtService;

import jakarta.servlet.http.Cookie;

/**
 * Pieza 6c-6 contra MySQL: la devolución enlazada al cobro (decisión 153) de un encargo caído y pagado, registrada por
 * quien tiene el dinero (195, D6c-10): el profesor si aún lo lleva encima (una entrega con contado 0 cuya diferencia es
 * la DEVOLUCION: saldo igual y fuera de «Llevas encima») o la sede si ya se entregó (saldo − importe). El cobro no se
 * anula: con la devolución hecha o pendiente, ninguna vía lo revierte (409). El saldo suma solo EFECTIVO (D6c-13), la
 * tesorería marca lo devuelto (D6c-12) y quitar de una convocatoria un examen devuelto lo desvincula (D6c-15). Escuela
 * sintética de sedes A y B ({@link EscuelaConSedesDePrueba}); datos inventados.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DevolucionesIntegrationTest extends MySqlIntegrationTest {

	private static final String SIN_PENDIENTE = "El encargo no tiene una devolución pendiente.";
	private static final String LO_LLEVA_EL_PROFESOR = "El profesor aún lleva el dinero encima: la devolución la "
			+ "registra él desde Mi caja.";
	private static final String NO_LO_LLEVAS = "No llevas encima el dinero de este encargo: la devolución la registra "
			+ "la sede.";
	private static final String CON_DEVOLUCION = "El cobro tiene una devolución registrada.";
	private static final String DEVOLUCION_PENDIENTE = "El encargo tiene una devolución pendiente: regístrala antes.";
	private static final String NOTA_PRIVADA = "Nota con datos de la familia";
	private static final BigDecimal IMPORTE = new BigDecimal("25.00");

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;

	@MockitoBean JavaMailSender mailSender;

	/** Un encargo cobrado y caído: la solicitud, su cargo y su cobro ACTIVO. */
	record Caido(long solicitud, long cargo, long cobro) {
	}

	/**
	 * Una escuela de prueba. prof1 (solo PROFESOR) da clase en gA, gB y gSin; prof2 (FAMILIA + PROFESOR) en gA2.
	 * aProfA está solo en gA (sede A); e.aGrupoA tiene la sede principal B y está en gA y gB. respB es responsable de B.
	 */
	final class Escena {
		final EscuelaConSedesDePrueba e;
		final long propietario, prof1, prof2, respB, aProfA, dobok;
		final String tGestor, tPropietario, tRespA, tRespB, tProf1, tProf2;

		Escena(String prefijo) {
			e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, prefijo);
			propietario = e.cuenta(usuarios, "propietario");
			membresia(propietario, "PROPIETARIO");
			prof1 = e.cuenta(usuarios, "prof1");
			membresia(prof1, "PROFESOR");
			for (long grupo : List.of(e.gA, e.gB, e.gSin)) {
				asignar(prof1, grupo);
			}
			prof2 = e.cuentaFamilia(usuarios, "prof2");
			membresia(prof2, "PROFESOR");
			asignar(prof2, e.gA2);
			respB = e.cuentaFamilia(usuarios, "respb");
			membresia(respB, "RESPONSABLE_SEDE");
			long membresiaB = jdbc.queryForObject("SELECT id FROM membresia WHERE usuario_id = ? AND organizacion_id = ? "
					+ "AND rol = 'RESPONSABLE_SEDE'", Long.class, respB, e.org);
			jdbc.update("INSERT INTO membresia_sede (membresia_id, sede_id) VALUES (?, ?)", membresiaB, e.sB);
			tGestor = e.token(jwtService, usuarios, e.gestor);
			tPropietario = e.token(jwtService, usuarios, propietario);
			tRespA = e.token(jwtService, usuarios, e.respA);
			tRespB = e.token(jwtService, usuarios, respB);
			tProf1 = e.token(jwtService, usuarios, prof1);
			tProf2 = e.token(jwtService, usuarios, prof2);
			jdbc.update("INSERT INTO producto (concepto, precio, codigo, organizacion_id, vendible_profesor, tipo) "
					+ "VALUES (?, 25.0, NULL, ?, FALSE, 'OTRO')", "DOBOK 6C6 " + e.marca, e.org);
			dobok = jdbc.queryForObject("SELECT MAX(id) FROM producto WHERE organizacion_id = ?", Long.class, e.org);
			aProfA = alumnoEnGrupo("E6C6PROFA", e.gA);
			// Con fecha de grado, marcar el pago de un examen no la rellena (la disciplina de prueba no tiene grados).
			jdbc.update("UPDATE alumno_deporte SET fecha_grado = '2024-01-01' WHERE organizacion_id = ?", e.org);
		}

		private void membresia(long usuario, String rol) {
			jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, ?)", usuario, e.org, rol);
		}

		private void asignar(long profesor, long grupo) {
			jdbc.update("INSERT INTO asignacion_profesor (organizacion_id, usuario_id, grupo_id, rol) "
					+ "VALUES (?, ?, ?, 'TITULAR')", e.org, profesor, grupo);
		}

		/** Un alumno nuevo con la sede principal A en ese grupo. */
		long alumnoEnGrupo(String nombre, long grupo) {
			long alumno = e.alumno(nombre + e.marca + System.nanoTime() % 100000, e.sA, true);
			jdbc.update("INSERT INTO alumno_grupo (grupo_id, alumno_id) VALUES (?, ?)", grupo, alumno);
			return alumno;
		}

		/** prof1 encarga un DOBOK para el alumno (en la sede pedida, si la hay): devuelve la solicitud. */
		long encargo(long alumno, Long sede) throws Exception {
			Map<String, Object> cuerpo = new HashMap<>();
			cuerpo.put("productoId", dobok);
			cuerpo.put("nota", "Talla 150");
			cuerpo.put("sedeId", sede);
			return leer(post("/api/caja/profesor/alumnos/" + alumno + "/encargos").contentType(MediaType.APPLICATION_JSON)
					.content(json.writeValueAsString(cuerpo)), tProf1, 201).get("id").asLong();
		}

		/** prof1 encarga, cobra en efectivo y retira: la devolución queda pendiente y el dinero lo lleva él. */
		Caido caido(long alumno, Long sede) throws Exception {
			long id = encargo(alumno, sede);
			long cargo = cargoDe(id);
			assertEquals(201, estado(post("/api/caja/profesor/cargos/" + cargo + "/cobrar"), tProf1));
			long cobro = cobroActivo(cargo);
			assertTrue(leer(post("/api/caja/profesor/solicitudes/" + id + "/retirar"), tProf1, 200)
					.get("devolucionPendiente").asBoolean());
			return new Caido(id, cargo, cobro);
		}

		/** El gestor recibe la entrega del cobro de prof1 en la sede (contado = declarado). */
		MockHttpServletRequestBuilder entrega(long sede, long cobro) throws Exception {
			Map<String, Object> cuerpo = new HashMap<>();
			cuerpo.put("entregadoPorUsuarioId", prof1);
			cuerpo.put("cobroIds", List.of(cobro));
			cuerpo.put("importeContado", IMPORTE);
			return post("/api/caja/sedes/" + sede + "/entregas").contentType(MediaType.APPLICATION_JSON)
					.content(json.writeValueAsString(cuerpo));
		}

		long entregar(long sede, long cobro) throws Exception {
			return leer(entrega(sede, cobro), tGestor, 201).get("id").asLong();
		}

		BigDecimal saldo(long sede) throws Exception {
			return leer(get("/api/caja/sedes/" + sede + "/resumen"), tPropietario, 200).get("saldoCaja").decimalValue()
					.setScale(2);
		}

		List<BigDecimal> saldos() throws Exception {
			return List.of(saldo(e.sA), saldo(e.sB));
		}

		/** Si el cobro sale en «Llevas encima» de prof1. */
		boolean llevaEncima(long cobro) throws Exception {
			for (JsonNode c : leer(get("/api/caja/profesor/cobros-pendientes-entrega"), tProf1, 200).get("cobros")) {
				if (c.get("cobroId").asLong() == cobro) {
					return true;
				}
			}
			return false;
		}

		/** Un cargo del DOBOK del alumno cobrado por el gestor en tesorería con ese método: devuelve el cobro. */
		long cobradoEnTesoreria(long alumno, String metodo) throws Exception {
			long cargo = leer(post("/api/productos-alumno/alumno/" + alumno + "/producto/" + dobok)
					.contentType(MediaType.APPLICATION_JSON).content("{\"cantidad\":1,\"pagado\":false}"), tGestor, 200)
					.get("id").asLong();
			assertEquals(200, estado(put("/api/productos-alumno/" + cargo).contentType(MediaType.APPLICATION_JSON)
					.content("{\"pagado\":true,\"metodoPago\":\"" + metodo + "\"}"), tGestor));
			return cobroActivo(cargo);
		}
	}

	Escena s;
	Escena otra;

	@BeforeAll
	void preparar() {
		s = new Escena("it-6c6-dev");
		otra = new Escena("it-6c6-otra");
	}

	@AfterEach
	void sinFugas() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
		SecurityContextHolder.clearContext();
	}

	// ------------------------------------------------------------------ el profesor (D6c-10)

	@Test
	void devolucionDelProfesor_saldoIgualYFueraDeLlevasEncima() throws Exception {
		Caido c = s.caido(s.aProfA, null);
		List<BigDecimal> saldos = s.saldos();
		assertTrue(s.llevaEncima(c.cobro()));

		JsonNode dto = leer(devolucionProfesor(c.solicitud(), NOTA_PRIVADA), s.tProf1, 200);
		assertFalse(dto.get("devolucionPendiente").asBoolean());
		long movimiento = dto.get("movimientoDevolucionId").asLong();
		Map<String, Object> m = jdbc.queryForMap("SELECT * FROM movimiento_caja WHERE id = ?", movimiento);
		assertEquals("DEVOLUCION", m.get("tipo"));
		assertEquals(0, IMPORTE.negate().compareTo((BigDecimal) m.get("importe")));
		assertEquals(c.cobro(), numero(m, "cobro_id"));
		assertEquals(c.cargo(), numero(m, "producto_alumno_id"));
		assertEquals(s.aProfA, numero(m, "alumno_id"));
		assertEquals("EFECTIVO", m.get("metodo"));
		assertEquals("ACTIVO", m.get("estado"));
		assertEquals(s.e.sA, numero(m, "sede_id"));
		assertEquals(NOTA_PRIVADA, m.get("nota"));
		long entrega = numero(m, "entrega_caja_id");
		Map<String, Object> en = jdbc.queryForMap("SELECT * FROM entrega_caja WHERE id = ?", entrega);
		assertEquals(0, IMPORTE.compareTo((BigDecimal) en.get("importe_declarado")));
		assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) en.get("importe_contado")));
		assertEquals(s.prof1, numero(en, "entregado_por_usuario_id"));
		assertEquals(s.prof1, numero(en, "recibido_por_usuario_id"));
		assertEquals("ACTIVA", en.get("estado"));
		Map<String, Object> cobro = jdbc.queryForMap("SELECT * FROM cobro WHERE id = ?", c.cobro());
		assertEquals("ACTIVO", cobro.get("estado"), "la devolución no anula el cobro (153)");
		assertEquals(entrega, numero(cobro, "entrega_caja_id"));
		assertTrue(pagado(c.cargo()));
		assertFalse(s.llevaEncima(c.cobro()), "fuera de «Llevas encima»");
		assertEquals(saldos, s.saldos(), "+importe (cobro entregado) −importe (devolución) = 0");
		Map<String, Object> sol = jdbc.queryForMap("SELECT * FROM solicitud WHERE id = ?", c.solicitud());
		assertFalse(Boolean.TRUE.equals(sol.get("devolucion_pendiente")));
		assertEquals(movimiento, numero(sol, "movimiento_devolucion_id"));
		assertEquals("RETIRADA", sol.get("estado"));

		MvcResult otraVez = peticion(devolucionProfesor(c.solicitud(), null), s.tProf1);
		assertEquals(409, otraVez.getResponse().getStatus());
		assertEquals(SIN_PENDIENTE, mensaje(otraVez));

		assertEquals("CAJA DEVOLUCION_PROFESOR sede=#" + s.e.sA + " entrega=#" + entrega + " movimiento=#" + movimiento
				+ " cobro=#" + c.cobro() + " cargo=#" + c.cargo() + " solicitud=#" + c.solicitud(),
				registro("CAJA DEVOLUCION_PROFESOR %solicitud=#" + c.solicitud()));
		assertEquals("SOLICITUDES ENCARGO_DEVOLUCION_REGISTRADA sede=#" + s.e.sA + " solicitud=#" + c.solicitud()
				+ " cargo=#" + c.cargo() + " producto=#" + s.dobok + " devolucion=#" + movimiento,
				registro("SOLICITUDES ENCARGO_DEVOLUCION_REGISTRADA sede=#" + s.e.sA + " solicitud=#" + c.solicitud()
						+ " %"));
		assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM auditoria_evento WHERE organizacion_id = ? AND "
				+ "resumen LIKE ?", Long.class, s.e.org, "%" + NOTA_PRIVADA + "%"), "la nota no va al registro");

		// Tesorería (D6c-12): la fila marca «devuelto» y el cargo sigue sumando como pagado.
		JsonNode fila = filaTesoreria(c.cargo());
		assertTrue(fila.get("devuelto").asBoolean());
		assertTrue(fila.get("pagado").asBoolean());
	}

	@Test
	void devolucionDelProfesor_soloSiLlevaElDinero() throws Exception {
		Caido c = s.caido(s.aProfA, null);
		assertEquals(403, estado(devolucionProfesor(c.solicitud(), null), s.tProf2), "otro profesor");
		assertEquals(403, estado(devolucionProfesor(c.solicitud(), null), s.tGestor), "fuera de su ruta");
		assertEquals(403, estado(devolucionProfesor(c.solicitud(), null), s.tPropietario));

		Caido entregado = s.caido(s.aProfA, null);
		s.entregar(s.e.sA, entregado.cobro());
		List<BigDecimal> saldos = s.saldos();
		MvcResult r = peticion(devolucionProfesor(entregado.solicitud(), null), s.tProf1);
		assertEquals(409, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		assertEquals(NO_LO_LLEVAS, mensaje(r));

		long enTesoreria = s.encargo(s.aProfA, null);
		long cargo = cargoDe(enTesoreria);
		assertEquals(200, estado(put("/api/productos-alumno/" + cargo).contentType(MediaType.APPLICATION_JSON)
				.content("{\"pagado\":true,\"metodoPago\":\"EFECTIVO\"}"), s.tGestor));
		assertEquals(200, estado(rechazar(enTesoreria), s.tGestor));
		saldos = s.saldos();
		r = peticion(devolucionProfesor(enTesoreria, null), s.tProf1);
		assertEquals(409, r.getResponse().getStatus());
		assertEquals(NO_LO_LLEVAS, mensaje(r));

		long pedido = s.encargo(s.aProfA, null);
		r = peticion(devolucionProfesor(pedido, null), s.tProf1);
		assertEquals(409, r.getResponse().getStatus());
		assertEquals(SIN_PENDIENTE, mensaje(r));
		assertEquals(400, estado(post("/api/caja/profesor/solicitudes/" + c.solicitud() + "/devolucion")
				.contentType(MediaType.APPLICATION_JSON).content("{\"nota\":\"" + "x".repeat(501) + "\"}"), s.tProf1));

		assertEquals(saldos, s.saldos(), "ningún rechazo mueve dinero");
		assertTrue(Boolean.TRUE.equals(jdbc.queryForObject("SELECT devolucion_pendiente FROM solicitud WHERE id = ?",
				Boolean.class, c.solicitud())));
		assertEquals(0L, devolucionesActivas(c.cobro()) + devolucionesActivas(entregado.cobro()));
	}

	// ------------------------------------------------------------------ la sede

	@Test
	void devolucionDeLaSede_trasEntregar_saldoBajaYCargoDevuelto() throws Exception {
		Caido encima = s.caido(s.aProfA, null);
		MvcResult r = peticion(devolucionSede(encima.solicitud(), null), s.tPropietario);
		assertEquals(409, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		assertEquals(LO_LLEVA_EL_PROFESOR, mensaje(r));

		for (String token : List.of(s.tPropietario, s.tGestor)) {
			Caido c = s.caido(s.aProfA, null);
			s.entregar(s.e.sA, c.cobro());
			BigDecimal antes = s.saldo(s.e.sA);
			BigDecimal sedeB = s.saldo(s.e.sB);
			double devueltoAntes = importeDevuelto(null);
			double devueltoAnoAntes = importeDevuelto(LocalDate.now().getYear());

			JsonNode dto = leer(devolucionSede(c.solicitud(), null), token, 200);
			assertFalse(dto.get("devolucionPendiente").asBoolean());
			long movimiento = dto.get("movimientoDevolucionId").asLong();
			Map<String, Object> m = jdbc.queryForMap("SELECT * FROM movimiento_caja WHERE id = ?", movimiento);
			assertEquals("DEVOLUCION", m.get("tipo"));
			assertEquals(s.e.sA, numero(m, "sede_id"));
			assertNull(m.get("entrega_caja_id"));
			assertEquals(c.cobro(), numero(m, "cobro_id"));
			assertEquals("Devolución del encargo (solicitud #" + c.solicitud() + ")", m.get("nota"));
			assertEquals(antes.subtract(IMPORTE), s.saldo(s.e.sA), "sale de la caja de la sede");
			assertEquals(sedeB, s.saldo(s.e.sB));
			assertEquals("ACTIVO", jdbc.queryForObject("SELECT estado FROM cobro WHERE id = ?", String.class,
					c.cobro()));
			assertTrue(pagado(c.cargo()));
			assertEquals(devueltoAntes + 25.0, importeDevuelto(null), 0.001);
			assertEquals(devueltoAnoAntes + 25.0, importeDevuelto(LocalDate.now().getYear()), 0.001);
			assertTrue(filaTesoreria(c.cargo()).get("devuelto").asBoolean());
			assertNotNull(registro("CAJA DEVOLUCION_CREADA sede=#" + s.e.sA + " movimiento=#" + movimiento + " cobro=#"
					+ c.cobro() + " cargo=#" + c.cargo() + " solicitud=#" + c.solicitud()));

			MvcResult otraVez = peticion(devolucionSede(c.solicitud(), null), token);
			assertEquals(409, otraVez.getResponse().getStatus());
			assertEquals(SIN_PENDIENTE, mensaje(otraVez));
			assertEquals(409, estado(devolucionProfesor(c.solicitud(), null), s.tProf1));
		}
		assertEquals(400, estado(devolucionSede(tipo1(), null), s.tPropietario), "no es un encargo");
	}

	@Test
	void devolucionDeLaSede_miraLaSedeDelCobro() throws Exception {
		// e.aGrupoA: sede principal B (el cargo y su cobro van a B), encargo pedido en la sede A (D6c-9).
		Caido c = s.caido(s.e.aGrupoA, s.e.sA);
		assertEquals(s.e.sA, jdbc.queryForObject("SELECT sede_id FROM solicitud WHERE id = ?", Long.class,
				c.solicitud()));
		assertEquals(s.e.sB, jdbc.queryForObject("SELECT sede_id FROM cobro WHERE id = ?", Long.class, c.cobro()));
		s.entregar(s.e.sB, c.cobro());
		List<BigDecimal> saldos = s.saldos();
		assertEquals(403, estado(devolucionSede(c.solicitud(), null), s.tRespA), "el cobro no es de su sede");
		assertEquals(403, estado(devolucionSede(c.solicitud(), null), s.tRespB), "la solicitud no es de su sede");
		assertEquals(403, estado(devolucionSede(c.solicitud(), null), s.tProf1), "fuera de su lista blanca");
		assertEquals(saldos, s.saldos());

		leer(devolucionSede(c.solicitud(), null), s.tGestor, 200);
		assertEquals(saldos.get(0), s.saldo(s.e.sA));
		assertEquals(saldos.get(1).subtract(IMPORTE), s.saldo(s.e.sB), "sale en la caja del cobro (F2)");
	}

	// ------------------------------------------------------------------ «no se anula» (153) y «pendiente → 409»

	@Test
	void conDevolucionOPendiente_noSeAnulaPorNingunaVia_409() throws Exception {
		Caido pendiente = s.caido(s.aProfA, null);
		MvcResult a1 = peticion(revertir(pendiente.cargo()), s.tPropietario);
		assertEquals(409, a1.getResponse().getStatus(), a1.getResponse().getContentAsString());
		assertEquals(DEVOLUCION_PENDIENTE, mensaje(a1));
		MvcResult a4 = peticion(post("/api/caja/profesor/cobros/" + pendiente.cobro() + "/deshacer"), s.tProf1);
		assertEquals(409, a4.getResponse().getStatus(), a4.getResponse().getContentAsString());
		assertEquals(DEVOLUCION_PENDIENTE, mensaje(a4));
		assertEquals("ACTIVO", estadoCobro(pendiente.cobro()));
		assertTrue(pagado(pendiente.cargo()));

		Caido devuelto = s.caido(s.aProfA, null);
		leer(devolucionProfesor(devuelto.solicitud(), null), s.tProf1, 200);
		a1 = peticion(revertir(devuelto.cargo()), s.tPropietario);
		assertEquals(409, a1.getResponse().getStatus());
		assertEquals(CON_DEVOLUCION, mensaje(a1));
		assertEquals(409, estado(post("/api/caja/profesor/cobros/" + devuelto.cobro() + "/deshacer"), s.tProf1));
		assertEquals("ACTIVO", estadoCobro(devuelto.cobro()));

		// Cobro de tesorería (dinero en la sede desde el principio) devuelto por la sede.
		long cobro = s.cobradoEnTesoreria(s.aProfA, "EFECTIVO");
		long cargo = cargoDeCobro(cobro);
		leer(devolucionEnlazada(s.e.sA, cobro, null), s.tGestor, 201);
		a1 = peticion(revertir(cargo), s.tPropietario);
		assertEquals(409, a1.getResponse().getStatus());
		assertEquals(CON_DEVOLUCION, mensaje(a1));
		assertEquals("ACTIVO", estadoCobro(cobro));
		assertTrue(pagado(cargo));
	}

	@Test
	void quitarDeConvocatoriaExamenDevuelto_desvincula_yAvisoCompleto() throws Exception {
		long alumno = s.e.aA;
		long inscripcion = inscripcionConCargo(alumno);
		long cargo = jdbc.queryForObject("SELECT producto_alumno_id FROM alumno_convocatoria WHERE id = ?", Long.class,
				inscripcion);
		assertEquals(200, estado(pagoConvocatoria(inscripcion, "{\"cuantiaExamen\":20.0,\"pagado\":true,"
				+ "\"metodoPago\":\"EFECTIVO\"}"), s.tPropietario));
		long cobro = cobroActivo(cargo);

		for (String token : List.of(s.tPropietario, s.tGestor)) {
			MvcResult r = peticion(quitar(inscripcion, alumno), token);
			assertEquals(409, r.getResponse().getStatus(), r.getResponse().getContentAsString());
			assertEquals("El examen está pagado: haz la devolución o revierte el cobro antes", mensaje(r));
			assertEquals(CobroService.EXAMEN_PAGADO_REVIERTE_ANTES, mensaje(r));
		}

		BigDecimal antes = s.saldo(s.e.sA);
		long movimiento = leer(devolucionEnlazada(s.e.sA, cobro, null), s.tPropietario, 201).get("id").asLong();
		assertEquals(antes.subtract(new BigDecimal("20.00")), s.saldo(s.e.sA));
		MvcResult a2 = peticion(pagoConvocatoria(inscripcion, "{\"cuantiaExamen\":20.0,\"pagado\":false}"),
				s.tPropietario);
		assertEquals(409, a2.getResponse().getStatus(), a2.getResponse().getContentAsString());
		assertEquals(CON_DEVOLUCION, mensaje(a2));

		BigDecimal conDevolucion = s.saldo(s.e.sA);
		assertEquals(204, estado(quitar(inscripcion, alumno), s.tGestor), "D6c-15: se desvincula");
		assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM alumno_convocatoria WHERE id = ?", Long.class,
				inscripcion));
		assertTrue(pagado(cargo), "el cargo se queda (153), pagado");
		Map<String, Object> filaCobro = jdbc.queryForMap("SELECT * FROM cobro WHERE id = ?", cobro);
		assertEquals("ACTIVO", filaCobro.get("estado"));
		assertEquals(cargo, numero(filaCobro, "producto_alumno_id"));
		assertEquals("ACTIVO", jdbc.queryForObject("SELECT estado FROM movimiento_caja WHERE id = ?", String.class,
				movimiento));
		assertEquals(conDevolucion, s.saldo(s.e.sA), "desvincular no mueve dinero");
	}

	@Test
	void borrarAlumno_conDevolucionPendiente409_conDevolucionHechaSaltaElCobro() throws Exception {
		long alumno = s.alumnoEnGrupo("E6C6BORRAR", s.e.gA);
		long id = s.encargo(alumno, null);
		long cargo = cargoDe(id);
		assertEquals(200, estado(put("/api/productos-alumno/" + cargo).contentType(MediaType.APPLICATION_JSON)
				.content("{\"pagado\":true,\"metodoPago\":\"EFECTIVO\"}"), s.tGestor));
		long cobro = cobroActivo(cargo);
		assertEquals(200, estado(rechazar(id), s.tPropietario));

		MvcResult r = peticion(delete("/api/alumnos/" + alumno), s.tPropietario);
		assertEquals(409, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		assertEquals(DEVOLUCION_PENDIENTE, mensaje(r));
		assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM alumno WHERE id = ?", Long.class, alumno));
		assertEquals("ACTIVO", estadoCobro(cobro));

		leer(devolucionSede(id, null), s.tGestor, 200);
		BigDecimal antes = s.saldo(s.e.sA);
		assertEquals(200, estado(delete("/api/alumnos/" + alumno), s.tPropietario));
		Map<String, Object> filaCobro = jdbc.queryForMap("SELECT * FROM cobro WHERE id = ?", cobro);
		assertEquals("ACTIVO", filaCobro.get("estado"), "un cobro devuelto no se anula al borrar el cargo");
		assertNull(filaCobro.get("producto_alumno_id"));
		assertEquals(1L, devolucionesActivas(cobro));
		assertEquals(antes, s.saldo(s.e.sA));
	}

	// ------------------------------------------------------------------ API de movimientos con cobro (§6.4)

	@Test
	void crearMovimientoConCobro_reglas() throws Exception {
		long cobro = s.cobradoEnTesoreria(s.aProfA, "EFECTIVO");
		assertEquals(400, estado(post("/api/caja/sedes/" + s.e.sA + "/movimientos")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"tipo\":\"OTRO\",\"importe\":-25.00,\"cobroId\":" + cobro + ",\"nota\":\"x\"}"), s.tGestor));
		assertEquals(400, estado(devolucionEnlazada(s.e.sB, cobro, null), s.tGestor), "otra sede");
		assertEquals(400, estado(post("/api/caja/sedes/" + s.e.sA + "/movimientos")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"tipo\":\"DEVOLUCION\",\"importe\":-10.00,\"cobroId\":" + cobro + ",\"nota\":\"x\"}"),
				s.tGestor), "parcial");
		assertEquals(400, estado(post("/api/caja/sedes/" + s.e.sA + "/movimientos")
				.contentType(MediaType.APPLICATION_JSON).content("{\"tipo\":\"DEVOLUCION\",\"alumnoId\":" + s.e.aA
						+ ",\"cobroId\":" + cobro + ",\"nota\":\"x\"}"), s.tGestor), "otro alumno");
		assertEquals(404, estado(devolucionEnlazada(s.e.sA, 999_999_999L, null), s.tGestor));
		long deOtra = otra.cobradoEnTesoreria(otra.aProfA, "EFECTIVO");
		assertEquals(404, estado(devolucionEnlazada(s.e.sA, deOtra, null), s.tGestor), "otra escuela");

		JsonNode dto = leer(post("/api/caja/sedes/" + s.e.sA + "/movimientos").contentType(MediaType.APPLICATION_JSON)
				.content("{\"tipo\":\"DEVOLUCION\",\"importe\":-25.00,\"alumnoId\":" + s.aProfA + ",\"cobroId\":"
						+ cobro + ",\"nota\":\"Devuelto\"}"), s.tGestor, 201);
		assertEquals(cobro, dto.get("cobroId").asLong());
		assertEquals("EFECTIVO", dto.get("metodo").asText());
		assertEquals(0, IMPORTE.negate().compareTo(dto.get("importe").decimalValue()));
		MvcResult r = peticion(devolucionEnlazada(s.e.sA, cobro, null), s.tGestor);
		assertEquals(409, r.getResponse().getStatus());
		assertEquals(CON_DEVOLUCION, mensaje(r));
		assertEquals(1L, devolucionesActivas(cobro));
		assertNotNull(registro("CAJA MOVIMIENTO_CREADO sede=#" + s.e.sA + " movimiento=#" + dto.get("id").asLong()
				+ " alumno=#" + s.aProfA + " cobro=#" + cobro + " cargo=#" + cargoDeCobro(cobro)));

		// El profesor aún lleva el dinero encima.
		long cargoPr = leer(post("/api/productos-alumno/alumno/" + s.aProfA + "/producto/" + s.dobok)
				.contentType(MediaType.APPLICATION_JSON).content("{\"cantidad\":1,\"pagado\":false}"), s.tGestor, 200)
				.get("id").asLong();
		assertEquals(201, estado(post("/api/caja/profesor/cargos/" + cargoPr + "/cobrar"), s.tProf1));
		r = peticion(devolucionEnlazada(s.e.sA, cobroActivo(cargoPr), null), s.tGestor);
		assertEquals(409, r.getResponse().getStatus());
		assertEquals(LO_LLEVA_EL_PROFESOR, mensaje(r));

		// Encargo con la devolución pendiente: se registra desde el encargo.
		Caido c = s.caido(s.aProfA, null);
		s.entregar(s.e.sA, c.cobro());
		r = peticion(devolucionEnlazada(s.e.sA, c.cobro(), null), s.tGestor);
		assertEquals(409, r.getResponse().getStatus());
		assertEquals("El cobro es de un encargo con la devolución pendiente: regístrala desde el encargo.", mensaje(r));
		assertEquals(0L, devolucionesActivas(c.cobro()));
	}

	@Test
	void devolucionEnlazadaSobreEncargoEnCurso_409() throws Exception {
		long id = s.encargo(s.aProfA, null);
		long cargo = cargoDe(id);
		assertEquals(200, estado(put("/api/productos-alumno/" + cargo).contentType(MediaType.APPLICATION_JSON)
				.content("{\"pagado\":true,\"metodoPago\":\"EFECTIVO\"}"), s.tGestor));
		long cobro = cobroActivo(cargo);
		BigDecimal antes = s.saldo(s.e.sA);
		MvcResult r = peticion(devolucionEnlazada(s.e.sA, cobro, null), s.tGestor);
		assertEquals(409, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		assertEquals("Tiene un encargo en curso: recházalo desde Solicitudes", mensaje(r));
		assertEquals(0L, devolucionesActivas(cobro));
		assertEquals(antes, s.saldo(s.e.sA));

		// Cuando cae, la devolución se registra desde el encargo.
		assertEquals(200, estado(rechazar(id), s.tGestor));
		assertTrue(leer(devolucionSede(id, null), s.tGestor, 200).has("movimientoDevolucionId"));
		assertEquals(1L, devolucionesActivas(cobro));
		assertEquals(antes.subtract(IMPORTE), s.saldo(s.e.sA));
	}

	@Test
	void caerConDevolucionYaRegistrada_noQuedaPendiente() throws Exception {
		long id = s.encargo(s.aProfA, null);
		long cargo = cargoDe(id);
		assertEquals(200, estado(put("/api/productos-alumno/" + cargo).contentType(MediaType.APPLICATION_JSON)
				.content("{\"pagado\":true,\"metodoPago\":\"EFECTIVO\"}"), s.tGestor));
		long cobro = cobroActivo(cargo);
		// Una devolución ya enlazada al cobro (la carrera o un dato anterior), apuntada por SQL.
		jdbc.update("""
				INSERT INTO movimiento_caja (organizacion_id, sede_id, tipo, importe, alumno_id, nota, fecha, estado,
				  cobro_id, producto_alumno_id, metodo)
				VALUES (?, ?, 'DEVOLUCION', -25.00, ?, 'Devuelto antes', NOW(6), 'ACTIVO', ?, ?, 'EFECTIVO')""",
				s.e.org, s.e.sA, s.aProfA, cobro, cargo);
		long devolucion = jdbc.queryForObject("SELECT MAX(id) FROM movimiento_caja WHERE organizacion_id = ?",
				Long.class, s.e.org);
		long propietario = contador(s.tPropietario);

		JsonNode dto = leer(rechazar(id), s.tPropietario, 200);
		assertEquals("RECHAZADO", dto.get("estado").asText());
		assertFalse(dto.get("devolucionPendiente").asBoolean(), "ya está devuelto: nada pendiente");
		assertEquals(devolucion, dto.get("movimientoDevolucionId").asLong(), "el encargo enlaza esa devolución");
		assertEquals(propietario - 1, contador(s.tPropietario), "deja de esperar acción (ni encargo ni devolución)");
		MvcResult r = peticion(devolucionSede(id, null), s.tPropietario);
		assertEquals(409, r.getResponse().getStatus());
		assertEquals(SIN_PENDIENTE, mensaje(r));
		Set<Long> resueltas = new HashSet<>();
		leer(get("/api/solicitudes").param("segmento", "RESUELTAS"), s.tPropietario, 200)
				.forEach(n -> resueltas.add(n.get("id").asLong()));
		assertTrue(resueltas.contains(id), "no se queda atascado en «Encargos en curso»");
	}

	@Test
	void saldoSumaSoloEfectivo() throws Exception {
		long cobro = s.cobradoEnTesoreria(s.aProfA, "TRANSFERENCIA");
		BigDecimal antes = s.saldo(s.e.sA);
		JsonNode dto = leer(devolucionEnlazada(s.e.sA, cobro, null), s.tPropietario, 201);
		assertEquals("TRANSFERENCIA", dto.get("metodo").asText());
		assertEquals("TRANSFERENCIA", jdbc.queryForObject("SELECT metodo FROM movimiento_caja WHERE id = ?",
				String.class, dto.get("id").asLong()));
		assertEquals(antes, s.saldo(s.e.sA), "una transferencia devuelta no sale de la caja física (D6c-13)");
	}

	// ------------------------------------------------------------------ anular la devolución

	@Test
	void anularLaEntregaDelProfesor_vuelveLaDevolucionPendiente() throws Exception {
		Caido c = s.caido(s.aProfA, null);
		List<BigDecimal> saldos = s.saldos();
		long movimiento = leer(devolucionProfesor(c.solicitud(), null), s.tProf1, 200).get("movimientoDevolucionId")
				.asLong();
		long entrega = jdbc.queryForObject("SELECT entrega_caja_id FROM movimiento_caja WHERE id = ?", Long.class,
				movimiento);
		leer(anular("/api/caja/entregas/" + entrega + "/anular"), s.tPropietario, 200);
		assertEquals("ANULADO", jdbc.queryForObject("SELECT estado FROM movimiento_caja WHERE id = ?", String.class,
				movimiento));
		assertTrue(s.llevaEncima(c.cobro()), "el cobro vuelve a «Llevas encima»");
		Map<String, Object> sol = jdbc.queryForMap("SELECT * FROM solicitud WHERE id = ?", c.solicitud());
		assertTrue(Boolean.TRUE.equals(sol.get("devolucion_pendiente")));
		assertNull(sol.get("movimiento_devolucion_id"));
		assertEquals(saldos, s.saldos());
		leer(devolucionProfesor(c.solicitud(), null), s.tProf1, 200);
		assertEquals(saldos, s.saldos());

		Caido sede = s.caido(s.aProfA, null);
		s.entregar(s.e.sA, sede.cobro());
		BigDecimal antes = s.saldo(s.e.sA);
		long devolucion = leer(devolucionSede(sede.solicitud(), null), s.tGestor, 200).get("movimientoDevolucionId")
				.asLong();
		leer(anular("/api/caja/movimientos/" + devolucion + "/anular"), s.tPropietario, 200);
		assertEquals(antes, s.saldo(s.e.sA));
		assertTrue(Boolean.TRUE.equals(jdbc.queryForObject("SELECT devolucion_pendiente FROM solicitud WHERE id = ?",
				Boolean.class, sede.solicitud())));
		assertEquals(0L, devolucionesActivas(sede.cobro()));
	}

	@Test
	void entregaExponeSusMovimientosDeCobro() throws Exception {
		Caido c = s.caido(s.aProfA, null);
		long movimiento = leer(devolucionProfesor(c.solicitud(), null), s.tProf1, 200).get("movimientoDevolucionId")
				.asLong();
		long entrega = jdbc.queryForObject("SELECT entrega_caja_id FROM movimiento_caja WHERE id = ?", Long.class,
				movimiento);
		JsonNode e = entregaDelHistorial(entrega);
		assertTrue(e.get("movimientoId").isNull(), "la DEVOLUCION no es su SOBRANTE o FALTANTE");
		assertTrue(e.get("autoentrega").asBoolean());
		JsonNode movs = e.get("movimientosDeCobro");
		assertEquals(1, movs.size(), e.toString());
		assertEquals(movimiento, movs.get(0).get("id").asLong());
		assertEquals("DEVOLUCION", movs.get(0).get("tipo").asText());
		assertEquals(0, IMPORTE.negate().compareTo(movs.get(0).get("importe").decimalValue()));
		assertEquals(c.cobro(), movs.get(0).get("cobroId").asLong());

		// Decisión 193: el ERROR de un cobro deshecho ya entregado (tipo 2 aceptado).
		long cargo = leer(post("/api/productos-alumno/alumno/" + s.aProfA + "/producto/" + s.dobok)
				.contentType(MediaType.APPLICATION_JSON).content("{\"cantidad\":1,\"pagado\":false}"), s.tGestor, 200)
				.get("id").asLong();
		assertEquals(201, estado(post("/api/caja/profesor/cargos/" + cargo + "/cobrar"), s.tProf1));
		long cobro = cobroActivo(cargo);
		long entrega2 = s.entregar(s.e.sA, cobro);
		jdbc.update("""
				INSERT INTO solicitud (organizacion_id, sede_id, tipo, estado, solicitado_por_usuario_id, solicitado_en,
				  motivo, cobro_id, producto_alumno_id, alumno_id, concepto, importe, motivo_deshacer)
				SELECT organizacion_id, sede_id, 'DESHACER_COBRO_ENTREGADO', 'PENDIENTE', ?, NOW(6), 'No pagó',
				  id, producto_alumno_id, alumno_id, concepto, importe, 'AUN_NO_PAGADO'
				FROM cobro WHERE id = ?""", s.prof1, cobro);
		long tipo2 = jdbc.queryForObject("SELECT MAX(id) FROM solicitud WHERE organizacion_id = ?", Long.class,
				s.e.org);
		assertEquals("ACEPTADA", leer(post("/api/solicitudes/" + tipo2 + "/aceptar"), s.tPropietario, 200)
				.get("estado").asText());
		JsonNode e2 = entregaDelHistorial(entrega2);
		assertTrue(e2.get("movimientoId").isNull());
		assertEquals(0, e2.get("cobroIds").size(), "el cobro deshecho sale de la entrega");
		assertEquals(1, e2.get("movimientosDeCobro").size());
		assertEquals("ERROR", e2.get("movimientosDeCobro").get(0).get("tipo").asText());
		assertEquals(0, IMPORTE.compareTo(e2.get("movimientosDeCobro").get(0).get("importe").decimalValue()),
				"declarado = Σ cobros + Σ ERROR");
		assertEquals(cobro, e2.get("movimientosDeCobro").get(0).get("cobroId").asLong());
	}

	// ------------------------------------------------------------------ concurrencia (punto de revisión 3)

	@Test
	void devolverYEntregarALaVez_saldoCuadra() throws Exception {
		for (int vuelta = 0; vuelta < 3; vuelta++) {
			Caido c = s.caido(s.aProfA, null);
			BigDecimal antes = s.saldo(s.e.sA);
			List<MvcResult> r = enParalelo(() -> peticion(devolucionProfesor(c.solicitud(), null), s.tProf1),
					() -> peticion(s.entrega(s.e.sA, c.cobro()), s.tGestor));
			int devolver = r.get(0).getResponse().getStatus();
			int entregar = r.get(1).getResponse().getStatus();
			assertTrue((devolver == 200 && entregar == 409) || (devolver == 409 && entregar == 201),
					devolver + " / " + entregar + ": " + r.get(0).getResponse().getContentAsString() + " "
							+ r.get(1).getResponse().getContentAsString());
			assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM entrega_caja e JOIN cobro c "
					+ "ON c.entrega_caja_id = e.id WHERE c.id = ? AND e.estado = 'ACTIVA'", Long.class, c.cobro()));
			assertEquals(devolver == 200 ? 1L : 0L, devolucionesActivas(c.cobro()));
			assertEquals(devolver != 200, Boolean.TRUE.equals(jdbc.queryForObject(
					"SELECT devolucion_pendiente FROM solicitud WHERE id = ?", Boolean.class, c.solicitud())));
			assertEquals(entregar == 201 ? antes.add(IMPORTE) : antes, s.saldo(s.e.sA), "el saldo cuadra");
			if (entregar == 201) {
				leer(devolucionSede(c.solicitud(), null), s.tGestor, 200);
				assertEquals(antes, s.saldo(s.e.sA), "devuelta por la sede, el saldo vuelve");
			}
			assertEquals(1L, devolucionesActivas(c.cobro()));
		}
	}

	// ------------------------------------------------------------------ bandeja, contador y aislamiento

	@Test
	void bandejaYContador_devolucionPendienteDeLaSede() throws Exception {
		Caido c = s.caido(s.aProfA, null);
		long propietario = contador(s.tPropietario);
		long gestor = contador(s.tGestor);
		JsonNode fila = filaBandeja(c.solicitud());
		assertTrue(fila.get("devolucionPendiente").asBoolean());
		assertFalse(fila.get("devolucionEnSede").asBoolean(), "el dinero lo lleva el profesor");

		s.entregar(s.e.sA, c.cobro());
		assertTrue(filaBandeja(c.solicitud()).get("devolucionEnSede").asBoolean());
		assertEquals(propietario + 1, contador(s.tPropietario));
		assertEquals(gestor + 1, contador(s.tGestor));

		long movimiento = leer(devolucionSede(c.solicitud(), null), s.tPropietario, 200).get("movimientoDevolucionId")
				.asLong();
		assertEquals(propietario, contador(s.tPropietario));
		assertEquals(gestor, contador(s.tGestor));
		Set<Long> resueltas = new HashSet<>();
		leer(get("/api/solicitudes").param("segmento", "RESUELTAS"), s.tPropietario, 200)
				.forEach(n -> resueltas.add(n.get("id").asLong()));
		assertTrue(resueltas.contains(c.solicitud()), "devuelta, el encargo pasa a resueltas");
		boolean enSusPedidos = false;
		for (JsonNode n : leer(get("/api/caja/profesor/solicitudes"), s.tProf1, 200)) {
			if (n.get("id").asLong() == c.solicitud()) {
				enSusPedidos = true;
				assertEquals(movimiento, n.get("movimientoDevolucionId").asLong());
				assertTrue(n.get("devolucionEnSede").isNull());
			}
		}
		assertTrue(enSusPedidos);
	}

	@Test
	void aislamientoDeEscuela() throws Exception {
		Caido deOtra = otra.caido(otra.aProfA, null);
		otra.entregar(otra.e.sA, deOtra.cobro());
		assertEquals(404, estado(devolucionSede(deOtra.solicitud(), null), s.tPropietario));
		assertEquals(404, estado(devolucionSede(deOtra.solicitud(), null), s.tGestor));
		assertEquals(404, estado(devolucionProfesor(deOtra.solicitud(), null), s.tProf1));
		assertEquals(0L, devolucionesActivas(deOtra.cobro()));
		leer(devolucionSede(deOtra.solicitud(), null), otra.tPropietario, 200);
	}

	// ------------------------------------------------------------------ utilidades

	private MockHttpServletRequestBuilder devolucionProfesor(long solicitud, String nota) throws Exception {
		return conNota(post("/api/caja/profesor/solicitudes/" + solicitud + "/devolucion"), nota);
	}

	private MockHttpServletRequestBuilder devolucionSede(long solicitud, String nota) throws Exception {
		return conNota(post("/api/solicitudes/" + solicitud + "/devolucion"), nota);
	}

	private MockHttpServletRequestBuilder conNota(MockHttpServletRequestBuilder peticion, String nota)
			throws Exception {
		Map<String, Object> cuerpo = new HashMap<>();
		cuerpo.put("nota", nota);
		return peticion.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo));
	}

	private MockHttpServletRequestBuilder devolucionEnlazada(long sede, long cobro, String importe) {
		return post("/api/caja/sedes/" + sede + "/movimientos").contentType(MediaType.APPLICATION_JSON)
				.content("{\"tipo\":\"DEVOLUCION\"" + (importe == null ? "" : ",\"importe\":" + importe)
						+ ",\"cobroId\":" + cobro + ",\"nota\":\"Devolución\"}");
	}

	private MockHttpServletRequestBuilder rechazar(long id) throws Exception {
		return post("/api/solicitudes/" + id + "/rechazar").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("motivo", "Ya no lo quiere")));
	}

	private static MockHttpServletRequestBuilder revertir(long cargo) {
		return put("/api/productos-alumno/" + cargo).contentType(MediaType.APPLICATION_JSON)
				.content("{\"pagado\":false}");
	}

	private static MockHttpServletRequestBuilder anular(String ruta) {
		return post(ruta).contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Error al apuntarla\"}");
	}

	/** Un tipo 1 pendiente del gestor sobre un cobro de tesorería. */
	private long tipo1() throws Exception {
		long cobro = s.cobradoEnTesoreria(s.aProfA, "EFECTIVO");
		Map<String, Object> cuerpo = new HashMap<>();
		cuerpo.put("tipo", "REVERTIR_COBRO");
		cuerpo.put("cobroId", cobro);
		cuerpo.put("motivo", "x");
		return leer(post("/api/solicitudes").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(cuerpo)), s.tGestor, 201).get("id").asLong();
	}

	/** Inscripción a una convocatoria nueva con su cargo de derecho a examen pendiente (como al apuntarlo). */
	private long inscripcionConCargo(long alumno) {
		jdbc.update("INSERT INTO convocatoria (fecha_convocatoria, organizacion_id, disciplina_id) VALUES "
				+ "('2026-06-01', ?, ?)", s.e.org, s.e.disciplina);
		long convocatoria = jdbc.queryForObject("SELECT MAX(id) FROM convocatoria WHERE organizacion_id = ?",
				Long.class, s.e.org);
		jdbc.update("""
				INSERT INTO producto_alumno (alumno_id, concepto, precio, cantidad, pagado, fecha_asignacion,
				  organizacion_id, sede_id)
				VALUES (?, 'DERECHO A EXAMEN 6C6', 20.0, 1, 0, NOW(), ?,
				  (SELECT sede_principal_id FROM alumno WHERE id = ?))""", alumno, s.e.org, alumno);
		long cargo = jdbc.queryForObject("SELECT MAX(id) FROM producto_alumno WHERE organizacion_id = ?", Long.class,
				s.e.org);
		jdbc.update("""
				INSERT INTO alumno_convocatoria (cuantia_examen, pagado, alumno_id, convocatoria_id, grado_actual,
				  grado_siguiente, organizacion_id, producto_alumno_id) VALUES (20, 0, ?, ?, NULL, NULL, ?, ?)""",
				alumno, convocatoria, s.e.org, cargo);
		return jdbc.queryForObject("SELECT id FROM alumno_convocatoria WHERE alumno_id = ? AND convocatoria_id = ?",
				Long.class, alumno, convocatoria);
	}

	private MockHttpServletRequestBuilder quitar(long inscripcion, long alumno) {
		long convocatoria = jdbc.queryForObject("SELECT convocatoria_id FROM alumno_convocatoria WHERE id = ?",
				Long.class, inscripcion);
		return delete("/api/alumnos/" + convocatoria + "/alumno/" + alumno);
	}

	private static MockHttpServletRequestBuilder pagoConvocatoria(long inscripcion, String cuerpo) {
		return put("/api/convocatorias/alumno/" + inscripcion).contentType(MediaType.APPLICATION_JSON).content(cuerpo);
	}

	private JsonNode entregaDelHistorial(long entrega) throws Exception {
		JsonNode pagina = leer(get("/api/caja/sedes/" + s.e.sA + "/entregas").param("size", "100"), s.tPropietario,
				200);
		for (JsonNode e : pagina.get("content")) {
			if (e.get("id").asLong() == entrega) {
				return e;
			}
		}
		throw new AssertionError("La entrega " + entrega + " no sale en el historial");
	}

	private JsonNode filaTesoreria(long cargo) throws Exception {
		JsonNode pagina = leer(get("/api/tesoreria/movimientos").param("soloActivos", "false").param("size", "200")
				.param("sedeId", String.valueOf(s.e.sA)), s.tPropietario, 200);
		for (JsonNode fila : pagina.get("content")) {
			if (fila.get("productoAlumnoId").asLong() == cargo) {
				return fila;
			}
		}
		throw new AssertionError("El cargo " + cargo + " no sale en la tesorería");
	}

	private double importeDevuelto(Integer ano) throws Exception {
		MockHttpServletRequestBuilder peticion = get("/api/tesoreria/resumen").param("soloActivos", "false")
				.param("sedeId", String.valueOf(s.e.sA));
		if (ano != null) {
			peticion = peticion.param("ano", String.valueOf(ano));
		}
		return leer(peticion, s.tPropietario, 200).get("importeDevuelto").asDouble();
	}

	private JsonNode filaBandeja(long solicitud) throws Exception {
		for (JsonNode n : leer(get("/api/solicitudes").param("segmento", "ENCARGOS"), s.tPropietario, 200)) {
			if (n.get("id").asLong() == solicitud) {
				return n;
			}
		}
		throw new AssertionError("La solicitud " + solicitud + " no sale en «Encargos en curso»");
	}

	private long contador(String token) throws Exception {
		return leer(get("/api/solicitudes/contador"), token, 200).get("total").asLong();
	}

	private long cargoDe(long solicitud) {
		return jdbc.queryForObject("SELECT producto_alumno_id FROM solicitud WHERE id = ?", Long.class, solicitud);
	}

	private long cargoDeCobro(long cobro) {
		return jdbc.queryForObject("SELECT producto_alumno_id FROM cobro WHERE id = ?", Long.class, cobro);
	}

	private long cobroActivo(long cargo) {
		return jdbc.queryForObject("SELECT id FROM cobro WHERE producto_alumno_id = ? AND estado = 'ACTIVO'",
				Long.class, cargo);
	}

	private String estadoCobro(long cobro) {
		return jdbc.queryForObject("SELECT estado FROM cobro WHERE id = ?", String.class, cobro);
	}

	private long devolucionesActivas(long cobro) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM movimiento_caja WHERE cobro_id = ? AND tipo = 'DEVOLUCION' "
				+ "AND estado = 'ACTIVO'", Long.class, cobro);
	}

	private boolean pagado(long cargo) {
		return Boolean.TRUE.equals(
				jdbc.queryForObject("SELECT pagado FROM producto_alumno WHERE id = ?", Boolean.class, cargo));
	}

	/** El resumen del registro de acciones de la escuela que encaja con el patrón LIKE (o null). */
	private String registro(String patron) {
		List<String> filas = jdbc.queryForList("SELECT resumen FROM auditoria_evento WHERE organizacion_id = ? "
				+ "AND resumen LIKE ?", String.class, s.e.org, patron);
		return filas.isEmpty() ? null : filas.get(0);
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

	private String mensaje(MvcResult r) throws Exception {
		return json.readTree(r.getResponse().getContentAsString()).path("mensaje").asText(null);
	}

	private static long numero(Map<String, Object> fila, String columna) {
		return ((Number) fila.get(columna)).longValue();
	}

	/** Las dos a la vez; los resultados en el orden de las acciones. */
	private static List<MvcResult> enParalelo(Callable<MvcResult> primera, Callable<MvcResult> segunda)
			throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch preparados = new CountDownLatch(2);
		CountDownLatch salida = new CountDownLatch(1);
		try {
			List<Future<MvcResult>> futuros = new ArrayList<>();
			for (Callable<MvcResult> accion : List.of(primera, segunda)) {
				futuros.add(executor.submit(() -> {
					preparados.countDown();
					if (!salida.await(10, TimeUnit.SECONDS)) {
						throw new IllegalStateException("No arrancaron ambas peticiones.");
					}
					return accion.call();
				}));
			}
			assertTrue(preparados.await(10, TimeUnit.SECONDS));
			salida.countDown();
			List<MvcResult> resultados = new ArrayList<>();
			for (Future<MvcResult> futuro : futuros) {
				resultados.add(futuro.get(60, TimeUnit.SECONDS));
			}
			return resultados;
		} finally {
			salida.countDown();
			executor.shutdownNow();
		}
	}
}
