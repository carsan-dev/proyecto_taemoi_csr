package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import com.taemoi.project.services.GrupoService;
import com.taemoi.project.services.JwtService;

import jakarta.servlet.http.Cookie;

/**
 * Pieza 6c-4 contra MySQL (fase3-6c-plan.md §4d): el profesor pide deshacer un cobro ya entregado a la sede (tipo 2,
 * decisión 193) por sus rutas de {@code /api/caja/profesor/**} (su lista blanca) y ve sus «Entregados recientes»
 * (D6c-14). Pedir valida lo que la 6c-3 revalida al aceptar; aceptar (el propietario, por {@code /api/solicitudes})
 * deja el saldo de la sede igual. prof1 es una cuenta solo PROFESOR (sesión solo-profesor); prof2, FAMILIA + PROFESOR.
 * Escuela sintética de sedes A y B ({@link EscuelaConSedesDePrueba}); datos inventados.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PedirDeshacerIntegrationTest extends MySqlIntegrationTest {

	private static final BigDecimal IMPORTE = new BigDecimal("10.50");
	private static final ZoneId ZONA = ZoneId.of("Europe/Madrid");
	private static final String RECIENTES = "/api/caja/profesor/cobros-entregados-recientes";

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;

	@MockitoBean JavaMailSender mailSender;

	/** Una escuela de prueba: prof1 (solo PROFESOR) en gA y prof2 (FAMILIA + PROFESOR) en gA2, cada uno con su alumno. */
	final class Escena {
		final EscuelaConSedesDePrueba e;
		final long propietario, prof1, prof2, producto, aProfA, aProf2;
		final String tGestor, tPropietario, tProf1, tProf2;

		Escena(String prefijo) {
			e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, prefijo);
			propietario = e.cuenta(usuarios, "propietario");
			membresia(propietario, "PROPIETARIO");
			prof1 = e.cuenta(usuarios, "prof1");
			membresia(prof1, "PROFESOR");
			asignar(prof1, e.gA);
			prof2 = e.cuentaFamilia(usuarios, "prof2");
			membresia(prof2, "PROFESOR");
			asignar(prof2, e.gA2);
			tGestor = e.token(jwtService, usuarios, e.gestor);
			tPropietario = e.token(jwtService, usuarios, propietario);
			tProf1 = e.token(jwtService, usuarios, prof1);
			tProf2 = e.token(jwtService, usuarios, prof2);
			jdbc.update("INSERT INTO producto (concepto, precio, codigo, organizacion_id, vendible_profesor) VALUES "
					+ "(?, 10.5, NULL, ?, TRUE)", "CAMISETA 6C4 " + e.marca, e.org);
			producto = jdbc.queryForObject("SELECT MAX(id) FROM producto WHERE organizacion_id = ?", Long.class, e.org);
			aProfA = e.alumno("E6C4PROFA" + e.marca, e.sA, true);
			jdbc.update("INSERT INTO alumno_grupo (grupo_id, alumno_id) VALUES (?, ?)", e.gA, aProfA);
			aProf2 = e.alumno("E6C4PROF2" + e.marca, e.sA, true);
			jdbc.update("INSERT INTO alumno_grupo (grupo_id, alumno_id) VALUES (?, ?)", e.gA2, aProf2);
		}

		private void membresia(long usuario, String rol) {
			jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, ?)", usuario, e.org, rol);
		}

		private void asignar(long profesor, long grupo) {
			jdbc.update("INSERT INTO asignacion_profesor (organizacion_id, usuario_id, grupo_id, rol) "
					+ "VALUES (?, ?, ?, 'TITULAR')", e.org, profesor, grupo);
		}

		/** Cargo pendiente (10,50) del alumno, creado por el gestor como siempre (sede = su sede principal). */
		long cargo(long alumno) throws Exception {
			MvcResult r = peticion(post("/api/productos-alumno/alumno/" + alumno + "/producto/" + producto)
					.contentType(MediaType.APPLICATION_JSON).content("{\"cantidad\":1,\"pagado\":false}"), tGestor);
			assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
			return json.readTree(r.getResponse().getContentAsString()).get("id").asLong();
		}

		/** El profesor cobra en efectivo un cargo nuevo de su alumno (sin entregar): devuelve el cobro. */
		long cobroDe(String token, long alumno) throws Exception {
			long cargo = cargo(alumno);
			MvcResult r = peticion(post("/api/caja/profesor/cargos/" + cargo + "/cobrar"), token);
			assertEquals(201, r.getResponse().getStatus(), r.getResponse().getContentAsString());
			return json.readTree(r.getResponse().getContentAsString()).get("cobroId").asLong();
		}

		long cobroDelProfesor() throws Exception {
			return cobroDe(tProf1, aProfA);
		}

		/** prof1 apunta y cobra el producto (su venta, decisión 172): devuelve el cobro. */
		long ventaDelProfesor() throws Exception {
			MvcResult r = peticion(post("/api/caja/profesor/alumnos/" + aProfA + "/productos/" + producto + "/cobrar"),
					tProf1);
			assertEquals(201, r.getResponse().getStatus(), r.getResponse().getContentAsString());
			return json.readTree(r.getResponse().getContentAsString()).get("cobroId").asLong();
		}

		/** El gestor recibe del profesor la entrega de esos cobros (contado = declarado): devuelve la entrega. */
		long entregar(long profesor, long... cobros) throws Exception {
			List<Long> ids = new ArrayList<>();
			for (long c : cobros) {
				ids.add(c);
			}
			Map<String, Object> cuerpo = new HashMap<>();
			cuerpo.put("entregadoPorUsuarioId", profesor);
			cuerpo.put("cobroIds", ids);
			cuerpo.put("importeContado", IMPORTE.multiply(BigDecimal.valueOf(cobros.length)));
			return leer(post("/api/caja/sedes/" + e.sA + "/entregas").contentType(MediaType.APPLICATION_JSON)
					.content(json.writeValueAsString(cuerpo)), tGestor, 201).get("id").asLong();
		}

		/** prof1 cobra y entrega: devuelve el cobro. */
		long cobroEntregado() throws Exception {
			long cobro = cobroDelProfesor();
			entregar(prof1, cobro);
			return cobro;
		}

		MvcResult pedir(String token, long cobro, String motivo, String motivoDeshacer) throws Exception {
			Map<String, Object> cuerpo = new HashMap<>();
			cuerpo.put("motivo", motivo);
			cuerpo.put("motivoDeshacer", motivoDeshacer);
			return peticion(post("/api/caja/profesor/cobros/" + cobro + "/pedir-deshacer")
					.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo)), token);
		}

		/** prof1 pide deshacer (AUN_NO_PAGADO): devuelve la solicitud. */
		long pedido(long cobro) throws Exception {
			MvcResult r = pedir(tProf1, cobro, "No pagó al final", "AUN_NO_PAGADO");
			assertEquals(201, r.getResponse().getStatus(), r.getResponse().getContentAsString());
			return json.readTree(r.getResponse().getContentAsString()).get("id").asLong();
		}

		BigDecimal saldo(long sede) throws Exception {
			return leer(get("/api/caja/sedes/" + sede + "/resumen"), tPropietario, 200).get("saldoCaja").decimalValue();
		}

		/** La entrega tal como sale en el historial de la sede A. */
		JsonNode entregaDelHistorial(long entrega) throws Exception {
			JsonNode pagina = leer(get("/api/caja/sedes/" + e.sA + "/entregas").param("size", "100"), tPropietario,
					200);
			for (JsonNode n : pagina.get("content")) {
				if (n.get("id").asLong() == entrega) {
					return n;
				}
			}
			throw new AssertionError("La entrega " + entrega + " no está en el historial: " + pagina);
		}

		/** Los entregados recientes de prof1, por id de cobro. */
		Map<Long, JsonNode> recientes() throws Exception {
			return porCobro(leer(get(RECIENTES), tProf1, 200));
		}

		long solicitudes() {
			return jdbc.queryForObject("SELECT COUNT(*) FROM solicitud WHERE organizacion_id = ?", Long.class, e.org);
		}
	}

	Escena s;
	Escena otra;

	@BeforeAll
	void preparar() {
		s = new Escena("it-6c4-desh");
		otra = new Escena("it-6c4-otra");
	}

	@AfterEach
	void sinFugas() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
		SecurityContextHolder.clearContext();
	}

	// ------------------------------------------------------------------ pedir y aceptar

	@Test
	void pedirYAceptar_dePuntaAPunta_saldoIgual() throws Exception {
		long cobro = s.cobroDelProfesor();
		long cargo = cargoDe(cobro);
		long entrega = s.entregar(s.prof1, cobro);
		JsonNode fila = s.recientes().get(cobro);
		assertNotNull(fila, "el cobro entregado sale en entregados recientes");
		assertEquals(entrega, fila.get("entregaId").asLong());
		assertEquals(cargo, fila.get("cargoId").asLong());
		assertEquals(s.aProfA, fila.get("alumnoId").asLong());
		assertEquals("E6C4PROFA" + s.e.marca, fila.get("alumnoNombre").asText());
		assertEquals("CAMISETA 6C4 " + s.e.marca, fila.get("concepto").asText());
		assertEquals(0, IMPORTE.compareTo(fila.get("importe").decimalValue()));
		assertEquals(s.e.sA, fila.get("sedeId").asLong());
		assertFalse(fila.get("sedeNombre").isNull());
		assertFalse(fila.get("fechaEntrega").isNull());
		assertFalse(fila.get("venta").asBoolean(), "el cargo lo creó la gestión");
		assertTrue(fila.get("solicitudPendienteId").isNull());
		assertTrue(fila.get("noSePuedePedir").isNull());
		BigDecimal saldo = s.saldo(s.e.sA);

		MvcResult r = s.pedir(s.tProf1, cobro, "  MOTIVO SECRETO 6C4  ", "AUN_NO_PAGADO");
		assertEquals(201, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		JsonNode dto = json.readTree(r.getResponse().getContentAsString());
		long id = dto.get("id").asLong();
		assertEquals("DESHACER_COBRO_ENTREGADO", dto.get("tipo").asText());
		assertEquals("PENDIENTE", dto.get("estado").asText());
		assertEquals(s.e.sA, dto.get("sedeId").asLong());
		assertEquals(cobro, dto.get("cobroId").asLong());
		assertEquals(cargo, dto.get("productoAlumnoId").asLong());
		assertEquals(s.aProfA, dto.get("alumnoId").asLong());
		assertEquals("AUN_NO_PAGADO", dto.get("motivoDeshacer").asText());
		assertEquals("MOTIVO SECRETO 6C4", dto.get("motivo").asText());
		assertTrue(r.getResponse().getContentAsString().contains("\"importe\":10.50"), "dos decimales");
		assertTrue(dto.get("propia").asBoolean());
		Map<String, Object> enBase = jdbc.queryForMap("SELECT * FROM solicitud WHERE id = ?", id);
		assertEquals(s.prof1, numero(enBase, "solicitado_por_usuario_id"));
		assertEquals("AUN_NO_PAGADO", enBase.get("motivo_deshacer"));
		assertEquals(s.e.org, numero(enBase, "organizacion_id"));
		assertEquals(0, saldo.compareTo(s.saldo(s.e.sA)), "pedir no mueve dinero");
		assertEquals("ACTIVO", jdbc.queryForObject("SELECT estado FROM cobro WHERE id = ?", String.class, cobro));
		assertEquals(id, s.recientes().get(cobro).get("solicitudPendienteId").asLong());
		boolean enSusSolicitudes = false;
		for (JsonNode n : leer(get("/api/caja/profesor/solicitudes"), s.tProf1, 200)) {
			enSusSolicitudes |= n.get("id").asLong() == id;
		}
		assertTrue(enSusSolicitudes, "sale en sus solicitudes (6c-5) para Mi caja");
		List<Map<String, Object>> eventos = jdbc.queryForList(
				"SELECT * FROM auditoria_evento WHERE organizacion_id = ? AND modulo = 'solicitudes' AND recurso_id = ?",
				s.e.org, id);
		assertEquals(1, eventos.size());
		assertEquals(s.prof1, numero(eventos.get(0), "usuario_id"));
		assertEquals("SOLICITUDES SOLICITUD_CREADA sede=#" + s.e.sA + " solicitud=#" + id + " cobro=#" + cobro
				+ " cargo=#" + cargo + " entrega=#" + entrega, eventos.get(0).get("resumen"));

		// El propietario acepta por la bandeja (6c-3, decisión 193).
		JsonNode aceptada = leer(post("/api/solicitudes/" + id + "/aceptar"), s.tPropietario, 200);
		assertEquals("ACEPTADA", aceptada.get("estado").asText(), aceptada.toString());
		assertEquals(0, saldo.compareTo(s.saldo(s.e.sA)), "anulado + ERROR: el saldo no cambia");
		Map<String, Object> c = jdbc.queryForMap("SELECT * FROM cobro WHERE id = ?", cobro);
		assertEquals("ANULADO", c.get("estado"));
		assertNull(c.get("entrega_caja_id"));
		Map<String, Object> error = jdbc.queryForMap(
				"SELECT * FROM movimiento_caja WHERE entrega_caja_id = ? AND tipo = 'ERROR'", entrega);
		assertEquals("ACTIVO", error.get("estado"));
		assertEquals(0, IMPORTE.compareTo((BigDecimal) error.get("importe")));
		assertEquals(cobro, numero(error, "cobro_id"));
		JsonNode enHistorial = s.entregaDelHistorial(entrega);
		assertEquals("ACTIVA", enHistorial.get("estado").asText());
		assertTrue(enHistorial.get("movimientoId").isNull());
		JsonNode deCobro = enHistorial.get("movimientosDeCobro");
		assertNotNull(deCobro, "la entrega enseña el ERROR para que «declarado» cuadre: " + enHistorial);
		assertEquals(1, deCobro.size());
		assertEquals("ERROR", deCobro.get(0).get("tipo").asText());
		assertEquals(numero(error, "id"), deCobro.get(0).get("id").asLong());
		assertEquals(cobro, deCobro.get(0).get("cobroId").asLong());
		assertEquals(0, IMPORTE.compareTo(deCobro.get(0).get("importe").decimalValue()));
		assertFalse(s.recientes().containsKey(cobro), "deshecho: ya no está entregado");
		for (Map<String, Object> evento : jdbc.queryForList(
				"SELECT resumen FROM auditoria_evento WHERE organizacion_id = ? AND resumen LIKE '%solicitud=#" + id + "%'",
				s.e.org)) {
			assertFalse(((String) evento.get("resumen")).contains("SECRETO"), evento.toString());
		}
	}

	@Test
	void pedirDeshacer_noHuboVenta_soloDeSuVenta() throws Exception {
		long venta = s.ventaDelProfesor();
		s.entregar(s.prof1, venta);
		assertTrue(s.recientes().get(venta).get("venta").asBoolean(), "admite NO_HUBO_VENTA");
		MvcResult r = s.pedir(s.tProf1, venta, "No se la llevó", "NO_HUBO_VENTA");
		assertEquals(201, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		assertEquals("NO_HUBO_VENTA", json.readTree(r.getResponse().getContentAsString()).get("motivoDeshacer").asText());

		long ajena = s.cobroEntregado();
		long antes = s.solicitudes();
		MvcResult noVenta = s.pedir(s.tProf1, ajena, "x", "NO_HUBO_VENTA");
		assertEquals(400, noVenta.getResponse().getStatus());
		assertEquals("No fue una venta suya: no se deshace como «no hubo venta».", mensaje(noVenta));
		assertEquals(antes, s.solicitudes());

		// Sin motivoDeshacer: AUN_NO_PAGADO, como el cuerpo vacío de A4.
		MvcResult sinMotivoDeshacer = peticion(post("/api/caja/profesor/cobros/" + ajena + "/pedir-deshacer")
				.contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"x\"}"), s.tProf1);
		assertEquals(201, sinMotivoDeshacer.getResponse().getStatus());
		assertEquals("AUN_NO_PAGADO",
				json.readTree(sinMotivoDeshacer.getResponse().getContentAsString()).get("motivoDeshacer").asText());
	}

	// ------------------------------------------------------------------ validaciones

	@Test
	void pedirDeshacer_409PorCadaCondicion() throws Exception {
		long antes = s.solicitudes();
		// Sin entregar: lo deshace él mismo (A4).
		long sinEntregar = s.cobroDelProfesor();
		conflicto(sinEntregar, "El cobro no va en una entrega: deshazlo tú mismo desde lo que llevas encima.");

		// Entrega anulada por el propietario: el cobro vuelve a pendiente de entrega.
		long liberado = s.cobroDelProfesor();
		long entregaAnulada = s.entregar(s.prof1, liberado);
		assertEquals(200, peticion(post("/api/caja/entregas/" + entregaAnulada + "/anular")
				.contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"x\"}"), s.tPropietario).getResponse()
				.getStatus());
		conflicto(liberado, "El cobro no va en una entrega: deshazlo tú mismo desde lo que llevas encima.");

		// La entrega del cobro ya no está ACTIVA (estado forzado por SQL: la red del servicio).
		long enAnulada = s.cobroEntregado();
		long entrega = entregaDe(enAnulada);
		jdbc.update("UPDATE entrega_caja SET estado = 'ANULADA' WHERE id = ?", entrega);
		try {
			conflicto(enAnulada, "La entrega ya está anulada.");
		} finally {
			jdbc.update("UPDATE entrega_caja SET estado = 'ACTIVA' WHERE id = ?", entrega);
		}

		// Cobro anulado (A4 antes de entregarlo).
		long anulado = s.cobroDelProfesor();
		assertEquals(204, peticion(post("/api/caja/profesor/cobros/" + anulado + "/deshacer"), s.tProf1).getResponse()
				.getStatus());
		conflicto(anulado, "El cobro ya no está activo.");

		// Cargo borrado (el cobro entregado sigue ACTIVO sin cargo).
		long sinCargo = s.cobroEntregado();
		jdbc.update("UPDATE cobro SET producto_alumno_id = NULL WHERE id = ?", sinCargo);
		conflicto(sinCargo, "El cargo del cobro ya no existe.");

		// Devolución registrada.
		long devuelto = s.cobroEntregado();
		jdbc.update("""
				INSERT INTO movimiento_caja (organizacion_id, sede_id, tipo, importe, alumno_id, nota,
				  creado_por_usuario_id, fecha, estado, cobro_id, producto_alumno_id)
				VALUES (?, ?, 'DEVOLUCION', -10.50, ?, 'devuelto', ?, NOW(6), 'ACTIVO', ?, ?)""", s.e.org, s.e.sA,
				s.aProfA, s.e.gestor, devuelto, cargoDe(devuelto));
		conflicto(devuelto, "El cobro tiene una devolución registrada.");

		// Encargo con la devolución pendiente sobre el cargo.
		long pendienteDevolver = s.cobroEntregado();
		jdbc.update("""
				INSERT INTO solicitud (organizacion_id, sede_id, tipo, estado, solicitado_por_usuario_id, solicitado_en,
				  motivo, producto_id, producto_alumno_id, alumno_id, concepto, importe, devolucion_pendiente)
				VALUES (?, ?, 'ENCARGO', 'RECHAZADO', ?, NOW(6), 'talla M', ?, ?, ?, 'CAMISETA 6C4', 10.50, TRUE)""",
				s.e.org, s.e.sA, s.prof1, s.producto, cargoDe(pendienteDevolver), s.aProfA);
		long conEncargo = antes + 1;
		conflicto(pendienteDevolver, "El encargo tiene una devolución pendiente: regístrala antes.");

		// Una sola pendiente por cobro: su tipo 2...
		long yaPedido = s.cobroEntregado();
		s.pedido(yaPedido);
		conflicto(yaPedido, "Ya hay una solicitud pendiente sobre este recurso.");
		// ... y un tipo 1 de la gestión pedido antes de entregarlo.
		long conTipo1 = s.cobroDelProfesor();
		Map<String, Object> cuerpo = new HashMap<>();
		cuerpo.put("tipo", "REVERTIR_COBRO");
		cuerpo.put("cobroId", conTipo1);
		cuerpo.put("motivo", "x");
		leer(post("/api/solicitudes").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo)),
				s.tGestor, 201);
		s.entregar(s.prof1, conTipo1);
		conflicto(conTipo1, "Ya hay una solicitud pendiente sobre este recurso.");

		assertEquals(conEncargo + 2, s.solicitudes(), "solo el encargo, el tipo 2 y el tipo 1 de la preparación");
	}

	/** Dos peticiones a la vez sobre el mismo cobro: una crea (201) y la otra 409, nunca 500; queda una sola PENDIENTE. */
	@Test
	void dosPedirALaVez_201Y409() throws Exception {
		for (int i = 0; i < 3; i++) {
			long cobro = s.cobroEntregado();
			List<MvcResult> resultados = enParalelo(() -> s.pedir(s.tProf1, cobro, "a la vez 1", "AUN_NO_PAGADO"),
					() -> s.pedir(s.tProf1, cobro, "a la vez 2", "AUN_NO_PAGADO"));
			List<Integer> estados = new ArrayList<>();
			resultados.forEach(r -> estados.add(r.getResponse().getStatus()));
			estados.sort(Integer::compareTo);
			assertEquals(List.of(201, 409), estados);
			MvcResult perdedor = resultados.stream().filter(r -> r.getResponse().getStatus() == 409).findFirst()
					.orElseThrow();
			assertEquals("Ya hay una solicitud pendiente sobre este recurso.", mensaje(perdedor));
			assertEquals(1L, jdbc.queryForObject(
					"SELECT COUNT(*) FROM solicitud WHERE cobro_id = ? AND estado = 'PENDIENTE'", Long.class, cobro));
		}
	}

	@Test
	void pedirDeshacer_motivoObligatorio() throws Exception {
		long cobro = s.cobroEntregado();
		long antes = s.solicitudes();
		assertEquals(400, peticion(post("/api/caja/profesor/cobros/" + cobro + "/pedir-deshacer"), s.tProf1)
				.getResponse().getStatus());
		assertEquals(400, s.pedir(s.tProf1, cobro, null, "AUN_NO_PAGADO").getResponse().getStatus());
		MvcResult vacio = s.pedir(s.tProf1, cobro, "   ", "AUN_NO_PAGADO");
		assertEquals(400, vacio.getResponse().getStatus());
		assertEquals("El motivo es obligatorio.", mensaje(vacio));
		MvcResult largo = s.pedir(s.tProf1, cobro, "x".repeat(501), "AUN_NO_PAGADO");
		assertEquals(400, largo.getResponse().getStatus());
		assertEquals("El motivo no puede superar 500 caracteres.", mensaje(largo));
		assertEquals(antes, s.solicitudes());
		MvcResult justo = s.pedir(s.tProf1, cobro, "y".repeat(500), "AUN_NO_PAGADO");
		assertEquals(201, justo.getResponse().getStatus(), justo.getResponse().getContentAsString());
		assertEquals(500, jdbc.queryForObject("SELECT CHAR_LENGTH(motivo) FROM solicitud WHERE id = ?", Integer.class,
				json.readTree(justo.getResponse().getContentAsString()).get("id").asLong()));
	}

	@Test
	void pedirDeshacer_cobroAjeno403_yAislamiento() throws Exception {
		long cobro = s.cobroEntregado();
		long antes = s.solicitudes();
		assertEquals(403, s.pedir(s.tProf2, cobro, "x", "AUN_NO_PAGADO").getResponse().getStatus(), "de otro profesor");
		assertEquals(403, s.pedir(s.tGestor, cobro, "x", "AUN_NO_PAGADO").getResponse().getStatus());
		assertEquals(403, s.pedir(s.tPropietario, cobro, "x", "AUN_NO_PAGADO").getResponse().getStatus());
		// Un cobro de tesorería (origen distinto de PROFESOR), aunque lo cobrase alguien de la escuela.
		long cargoTesoreria = s.cargo(s.aProfA);
		assertEquals(200, peticion(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.put("/api/productos-alumno/" + cargoTesoreria).contentType(MediaType.APPLICATION_JSON)
				.content("{\"pagado\":true,\"metodoPago\":\"EFECTIVO\"}"), s.tGestor).getResponse().getStatus());
		long cobroTesoreria = jdbc.queryForObject(
				"SELECT id FROM cobro WHERE producto_alumno_id = ? AND estado = 'ACTIVO'", Long.class, cargoTesoreria);
		assertEquals(403, s.pedir(s.tProf1, cobroTesoreria, "x", "AUN_NO_PAGADO").getResponse().getStatus());
		// Otra escuela: su profesor no lo ve (403), y el de aquí no ve el de allí.
		assertEquals(403, s.pedir(otra.tProf1, cobro, "x", "AUN_NO_PAGADO").getResponse().getStatus());
		long deOtra = otra.cobroEntregado();
		assertEquals(403, s.pedir(s.tProf1, deOtra, "x", "AUN_NO_PAGADO").getResponse().getStatus());
		assertEquals(403, s.pedir(s.tProf1, 999_999_999L, "x", "AUN_NO_PAGADO").getResponse().getStatus());
		assertEquals(antes, s.solicitudes());
		assertEquals("ACTIVO", jdbc.queryForObject("SELECT estado FROM cobro WHERE id = ?", String.class, cobro));
	}

	// ------------------------------------------------------------------ retirar (ruta de la 6c-5)

	@Test
	void retirarSuTipo2_porLaRutaDelProfesor_ajena403() throws Exception {
		long cobro = s.cobroEntregado();
		long id = s.pedido(cobro);
		assertEquals(403, peticion(post("/api/caja/profesor/solicitudes/" + id + "/retirar"), s.tProf2).getResponse()
				.getStatus(), "ajena");
		assertEquals(403, peticion(post("/api/solicitudes/" + id + "/retirar"), s.tGestor).getResponse().getStatus(),
				"la gestión no retira lo que no pidió");
		assertEquals(403, peticion(post("/api/solicitudes/" + id + "/retirar"), s.tProf1).getResponse().getStatus(),
				"la ruta de gestión está fuera de su lista blanca");
		assertEquals("PENDIENTE", estadoDe(id));

		JsonNode retirada = leer(post("/api/caja/profesor/solicitudes/" + id + "/retirar"), s.tProf1, 200);
		assertEquals("RETIRADA", retirada.get("estado").asText());
		assertEquals(s.prof1, retirada.get("resueltoPorUsuarioId").asLong());
		assertEquals("ACTIVO", jdbc.queryForObject("SELECT estado FROM cobro WHERE id = ?", String.class, cobro));
		assertTrue(s.recientes().get(cobro).get("solicitudPendienteId").isNull());
		assertEquals(409, peticion(post("/api/caja/profesor/solicitudes/" + id + "/retirar"), s.tProf1).getResponse()
				.getStatus(), "ya no está pendiente");
		s.pedido(cobro);
	}

	// ------------------------------------------------------------------ entregados recientes (D6c-14)

	@Test
	void entregadosRecientes_30Dias_soloLosSuyos_conSuEstado() throws Exception {
		Escena n = new Escena("it-6c4-rec");
		long viejo = n.cobroDelProfesor();
		long entregaVieja = n.entregar(n.prof1, viejo);
		long medio = n.cobroDelProfesor();
		long entregaMedia = n.entregar(n.prof1, medio);
		long nuevo1 = n.cobroDelProfesor();
		long nuevo2 = n.cobroDelProfesor();
		n.entregar(n.prof1, nuevo1, nuevo2);
		LocalDateTime ahora = LocalDateTime.now(ZONA);
		jdbc.update("UPDATE entrega_caja SET fecha = ? WHERE id = ?", ahora.minusDays(31), entregaVieja);
		jdbc.update("UPDATE entrega_caja SET fecha = ? WHERE id = ?", ahora.minusDays(29), entregaMedia);
		long sinEntregar = n.cobroDelProfesor();
		long delOtro = n.cobroDe(n.tProf2, n.aProf2);
		n.entregar(n.prof2, delOtro);
		long devuelto = n.cobroDelProfesor();
		n.entregar(n.prof1, devuelto);
		jdbc.update("""
				INSERT INTO movimiento_caja (organizacion_id, sede_id, tipo, importe, alumno_id, nota,
				  creado_por_usuario_id, fecha, estado, cobro_id, producto_alumno_id)
				VALUES (?, ?, 'DEVOLUCION', -10.50, ?, 'devuelto', ?, NOW(6), 'ACTIVO', ?, ?)""", n.e.org, n.e.sA,
				n.aProfA, n.e.gestor, devuelto, cargoDe(devuelto));
		otra.cobroEntregado();

		JsonNode lista = leer(get(RECIENTES), n.tProf1, 200);
		List<Long> orden = new ArrayList<>();
		lista.forEach(f -> orden.add(f.get("cobroId").asLong()));
		assertEquals(List.of(devuelto, nuevo2, nuevo1, medio), orden,
				"de la entrega más reciente a la más antigua; 31 días, fuera; sin entregar y de otros, fuera");
		Map<Long, JsonNode> porId = porCobro(lista);
		assertEquals("El cobro tiene una devolución registrada.", porId.get(devuelto).get("noSePuedePedir").asText());
		assertTrue(porId.get(medio).get("noSePuedePedir").isNull());
		// Cargo desmarcado con el cobro aún ACTIVO (estado forzado por SQL): el mismo 409 que al pedir.
		jdbc.update("UPDATE producto_alumno SET pagado = 0 WHERE id = ?", cargoDe(medio));
		assertEquals("El cargo ya no está pagado.",
				porCobro(leer(get(RECIENTES), n.tProf1, 200)).get(medio).get("noSePuedePedir").asText());
		MvcResult noPagado = n.pedir(n.tProf1, medio, "x", "AUN_NO_PAGADO");
		assertEquals(409, noPagado.getResponse().getStatus());
		assertEquals("El cargo ya no está pagado.", mensaje(noPagado));
		assertFalse(orden.contains(viejo) || orden.contains(sinEntregar) || orden.contains(delOtro));
		assertEquals(List.of(delOtro), new ArrayList<>(porCobro(leer(get(RECIENTES), n.tProf2, 200)).keySet()));

		assertEquals(403, estado(get(RECIENTES), n.tGestor));
		assertEquals(403, estado(get(RECIENTES), n.tPropietario));
	}

	@Test
	void entregadosRecientes_tope50() throws Exception {
		Escena n = new Escena("it-6c4-tope");
		long ultimo = n.cobroDelProfesor();
		long entrega = n.entregar(n.prof1, ultimo);
		// 51 cobros más en esa entrega (por SQL, sin cargo: solo cuenta el tope).
		for (int i = 0; i < 51; i++) {
			jdbc.update("""
					INSERT INTO cobro (organizacion_id, producto_alumno_id, sede_id, alumno_id, concepto, importe, metodo,
					  fecha_cobro, cobrado_por_usuario_id, origen, estado, entrega_caja_id)
					VALUES (?, NULL, ?, ?, 'CAMISETA 6C4', 10.50, 'EFECTIVO', NOW(6), ?, 'PROFESOR', 'ACTIVO', ?)""",
					n.e.org, n.e.sA, n.aProfA, n.prof1, entrega);
		}
		long masNuevo = jdbc.queryForObject("SELECT MAX(id) FROM cobro WHERE organizacion_id = ?", Long.class, n.e.org);
		JsonNode lista = leer(get(RECIENTES), n.tProf1, 200);
		assertEquals(50, lista.size());
		assertEquals(masNuevo, lista.get(0).get("cobroId").asLong(), "dentro de una entrega, el cobro más nuevo primero");
		assertFalse(porCobro(lista).containsKey(ultimo), "el más antiguo queda fuera del tope");
		assertEquals("El cargo del cobro ya no existe.", lista.get(0).get("noSePuedePedir").asText());
	}

	// ------------------------------------------------------------------ utilidades

	private void conflicto(long cobro, String mensaje) throws Exception {
		MvcResult r = s.pedir(s.tProf1, cobro, "x", "AUN_NO_PAGADO");
		assertEquals(409, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		assertEquals(mensaje, mensaje(r));
	}

	private static Map<Long, JsonNode> porCobro(JsonNode lista) {
		Map<Long, JsonNode> porId = new java.util.LinkedHashMap<>();
		lista.forEach(f -> porId.put(f.get("cobroId").asLong(), f));
		return porId;
	}

	private long cargoDe(long cobro) {
		return jdbc.queryForObject("SELECT producto_alumno_id FROM cobro WHERE id = ?", Long.class, cobro);
	}

	private long entregaDe(long cobro) {
		return jdbc.queryForObject("SELECT entrega_caja_id FROM cobro WHERE id = ?", Long.class, cobro);
	}

	private String estadoDe(long solicitud) {
		return jdbc.queryForObject("SELECT estado FROM solicitud WHERE id = ?", String.class, solicitud);
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
		return json.readTree(r.getResponse().getContentAsString()).get("mensaje").asText();
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
