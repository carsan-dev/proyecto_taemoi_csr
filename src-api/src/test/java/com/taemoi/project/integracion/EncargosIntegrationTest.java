package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.math.BigDecimal;
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
import com.taemoi.project.services.GrupoService;
import com.taemoi.project.services.JwtService;

import jakarta.servlet.http.Cookie;

/**
 * Pieza 6c-5 contra MySQL: los encargos del profesor (decisiones 183 y 192, D6c-9; fase3-6c-plan.md §4b). Pedir crea
 * el cargo pendiente con el precio del catálogo; quien atiende lo marca encargado y recibido, o lo rechaza; el profesor
 * lo retira mientras está pedido y lo marca entregado. Si cae sin cobrar, el cargo se cancela; si ya estaba pagado, queda
 * la devolución pendiente (la 6c-6). Cancelar o borrar el cargo de un encargo en curso, 409. En cada paso, el saldo de
 * caja de las sedes igual. Escuela sintética de sedes A y B ({@link EscuelaConSedesDePrueba}); datos inventados.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EncargosIntegrationTest extends MySqlIntegrationTest {

	private static final String NO_PENDIENTE = "La solicitud ya no está pendiente";
	private static final String ENCARGO_EN_CURSO = "Tiene un encargo en curso: recházalo desde Solicitudes";
	private static final String NOTA = "Talla 150";

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;

	@MockitoBean JavaMailSender mailSender;

	/**
	 * Una escuela de prueba. prof1 (solo PROFESOR) da clase en gA, gB y gSin; prof2 (FAMILIA + PROFESOR) en gA2.
	 * aProfA está solo en gA; e.aGrupoA, en gA y gB (dos sedes); aSoloSin, solo en gSin (grupo sin sede).
	 */
	final class Escena {
		final EscuelaConSedesDePrueba e;
		final long propietario, prof1, prof2, aProfA, aSoloSin, aProf2;
		final long dobok, camiseta, mensualidad, licencia;
		final String tGestor, tPropietario, tResp, tProf1, tProf2, tFamilia;

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
			long familia = e.cuentaFamilia(usuarios, "familia");
			tGestor = e.token(jwtService, usuarios, e.gestor);
			tPropietario = e.token(jwtService, usuarios, propietario);
			tResp = e.token(jwtService, usuarios, e.respA);
			tProf1 = e.token(jwtService, usuarios, prof1);
			tProf2 = e.token(jwtService, usuarios, prof2);
			tFamilia = e.token(jwtService, usuarios, familia);
			dobok = producto("DOBOK 6C5 " + e.marca, "25.0", "OTRO", false);
			camiseta = producto("CAMISETA 6C5 " + e.marca, "10.5", "OTRO", true);
			mensualidad = producto("CUOTA 6C5 " + e.marca, "30.0", "MENSUALIDAD", true);
			licencia = producto("LICENCIA 6C5 " + e.marca, "40.0", "OTRO", true);
			aProfA = e.alumno("E6C5PROFA" + e.marca, e.sA, true);
			jdbc.update("INSERT INTO alumno_grupo (grupo_id, alumno_id) VALUES (?, ?)", e.gA, aProfA);
			aSoloSin = e.alumno("E6C5SIN" + e.marca, e.sA, true);
			jdbc.update("INSERT INTO alumno_grupo (grupo_id, alumno_id) VALUES (?, ?)", e.gSin, aSoloSin);
			aProf2 = e.alumno("E6C5PROF2" + e.marca, e.sA, true);
			jdbc.update("INSERT INTO alumno_grupo (grupo_id, alumno_id) VALUES (?, ?)", e.gA2, aProf2);
		}

		private void membresia(long usuario, String rol) {
			jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, ?)", usuario, e.org, rol);
		}

		private void asignar(long profesor, long grupo) {
			jdbc.update("INSERT INTO asignacion_profesor (organizacion_id, usuario_id, grupo_id, rol) "
					+ "VALUES (?, ?, ?, 'TITULAR')", e.org, profesor, grupo);
		}

		private long producto(String concepto, String precio, String tipo, boolean vendible) {
			jdbc.update("INSERT INTO producto (concepto, precio, codigo, organizacion_id, vendible_profesor, tipo) "
					+ "VALUES (?, ?, NULL, ?, ?, ?)", concepto, new BigDecimal(precio), e.org, vendible, tipo);
			return jdbc.queryForObject("SELECT MAX(id) FROM producto WHERE organizacion_id = ?", Long.class, e.org);
		}

		MvcResult pedir(String token, long alumno, Long producto, String nota, Long sede) throws Exception {
			Map<String, Object> cuerpo = new HashMap<>();
			cuerpo.put("productoId", producto);
			cuerpo.put("nota", nota);
			cuerpo.put("sedeId", sede);
			return peticion(post("/api/caja/profesor/alumnos/" + alumno + "/encargos")
					.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo)), token);
		}

		/** prof1 encarga un DOBOK para aProfA (sede A): devuelve la solicitud. */
		long encargo() throws Exception {
			return encargo(aProfA);
		}

		long encargo(long alumno) throws Exception {
			MvcResult r = pedir(tProf1, alumno, dobok, NOTA, null);
			assertEquals(201, r.getResponse().getStatus(), r.getResponse().getContentAsString());
			return json.readTree(r.getResponse().getContentAsString()).get("id").asLong();
		}

		BigDecimal saldo(long sede) throws Exception {
			return leer(get("/api/caja/sedes/" + sede + "/resumen"), tPropietario, 200).get("saldoCaja").decimalValue();
		}

		/** Saldo de las sedes A y B (para comprobar que un paso del encargo no mueve dinero). */
		List<BigDecimal> saldos() throws Exception {
			return List.of(saldo(e.sA), saldo(e.sB));
		}

		JsonNode segmento(String segmento) throws Exception {
			return leer(get("/api/solicitudes").param("segmento", segmento), tPropietario, 200);
		}
	}

	Escena s;
	Escena otra;

	@BeforeAll
	void preparar() {
		s = new Escena("it-6c5-enc");
		otra = new Escena("it-6c5-otra");
	}

	@AfterEach
	void sinFugas() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
		SecurityContextHolder.clearContext();
	}

	// ------------------------------------------------------------------ productos encargables (192)

	@Test
	void productosEncargables_otroSinGradoAunqueNoEsteMarcado() throws Exception {
		JsonNode lista = leer(get("/api/caja/profesor/productos-encargables"), s.tProf1, 200);
		Map<Long, JsonNode> porId = new HashMap<>();
		lista.forEach(p -> porId.put(p.get("id").asLong(), p));
		assertTrue(porId.containsKey(s.dobok), "sin marcar para Mi caja, pero encargable: " + lista);
		assertTrue(porId.containsKey(s.camiseta), lista.toString());
		assertEquals(0, new BigDecimal("25.00").compareTo(porId.get(s.dobok).get("precio").decimalValue()));
		assertFalse(porId.containsKey(s.mensualidad), "una mensualidad no se encarga");
		assertFalse(porId.containsKey(s.licencia), "un concepto con efectos no se encarga");
		assertFalse(porId.containsKey(otra.dobok), "ni los de otra escuela");
		assertEquals(403, estado(get("/api/caja/profesor/productos-encargables"), s.tGestor));
	}

	// ------------------------------------------------------------------ pedir

	@Test
	void pedirEncargo_creaElCargoPendienteConPrecioDeCatalogo_yQuedaPedido() throws Exception {
		List<BigDecimal> saldos = s.saldos();
		MvcResult r = s.pedir(s.tProf1, s.aProfA, s.dobok, NOTA, null);
		assertEquals(201, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		JsonNode dto = json.readTree(r.getResponse().getContentAsString());
		long id = dto.get("id").asLong();
		assertEquals("ENCARGO", dto.get("tipo").asText());
		assertEquals("PEDIDO", dto.get("estado").asText());
		assertEquals(s.e.sA, dto.get("sedeId").asLong());
		assertEquals(0, new BigDecimal("25.00").compareTo(dto.get("importe").decimalValue()));
		assertTrue(r.getResponse().getContentAsString().contains("\"importe\":25.00"), "dos decimales");
		assertEquals("DOBOK 6C5 " + s.e.marca, dto.get("concepto").asText());
		assertEquals(NOTA, dto.get("motivo").asText());
		assertEquals(s.dobok, dto.get("productoId").asLong());
		assertEquals(s.aProfA, dto.get("alumnoId").asLong());
		assertTrue(dto.get("propia").asBoolean());
		assertFalse(dto.get("devolucionPendiente").asBoolean());

		Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM solicitud WHERE id = ?", id);
		long cargo = numero(fila, "producto_alumno_id");
		assertEquals(s.prof1, numero(fila, "solicitado_por_usuario_id"));
		assertEquals(s.dobok, numero(fila, "producto_id"));
		assertEquals(s.aProfA, numero(fila, "alumno_id"));
		Map<String, Object> c = jdbc.queryForMap("SELECT * FROM producto_alumno WHERE id = ?", cargo);
		assertFalse(Boolean.TRUE.equals(c.get("pagado")));
		assertEquals(25.0, ((Number) c.get("precio")).doubleValue());
		assertEquals(1, ((Number) c.get("cantidad")).intValue());
		assertEquals(NOTA, c.get("notas"));
		assertNull(c.get("vendido_por_usuario_id"), "no es su venta (decisión 172)");
		assertNull(c.get("cancelado_en"));
		assertEquals(s.e.sA, numero(c, "sede_id"));
		assertEquals(s.aProfA, numero(c, "alumno_id"));
		assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM cobro WHERE producto_alumno_id = ?", Long.class,
				cargo));
		boolean enPorCobrar = false;
		for (JsonNode n : leer(get("/api/caja/profesor/cargos-pendientes"), s.tProf1, 200)) {
			enPorCobrar |= n.get("cargoId").asLong() == cargo;
		}
		assertTrue(enPorCobrar, "el cargo sale en «Por cobrar»");
		assertEquals(saldos, s.saldos(), "pedir no mueve dinero");
		assertEquals("SOLICITUDES ENCARGO_PEDIDO sede=#" + s.e.sA + " solicitud=#" + id + " cargo=#" + cargo
				+ " producto=#" + s.dobok, resumen(id, "ENCARGO_PEDIDO"));
	}

	@Test
	void pedirEncargo_sedeSoloSeExigeConVarias() throws Exception {
		// e.aGrupoA está en gA (sede A) y gB (sede B), los dos de prof1.
		assertEquals(400, s.pedir(s.tProf1, s.e.aGrupoA, s.dobok, NOTA, null).getResponse().getStatus());
		assertEquals(400, s.pedir(s.tProf1, s.e.aGrupoA, s.dobok, NOTA, s.e.sC).getResponse().getStatus());
		JsonNode enB = json.readTree(s.pedir(s.tProf1, s.e.aGrupoA, s.dobok, NOTA, s.e.sB).getResponse()
				.getContentAsString());
		assertEquals(s.e.sB, enB.get("sedeId").asLong());
		// aProfA, solo en gA: la sede se deduce; otra sede, 400.
		assertEquals(s.e.sA, json.readTree(s.pedir(s.tProf1, s.aProfA, s.dobok, NOTA, null).getResponse()
				.getContentAsString()).get("sedeId").asLong());
		assertEquals(400, s.pedir(s.tProf1, s.aProfA, s.dobok, NOTA, s.e.sB).getResponse().getStatus());
		// aSoloSin, solo en un grupo sin sede: el encargo no tiene sede.
		MvcResult sinSede = s.pedir(s.tProf1, s.aSoloSin, s.dobok, NOTA, null);
		assertEquals(201, sinSede.getResponse().getStatus(), sinSede.getResponse().getContentAsString());
		long id = json.readTree(sinSede.getResponse().getContentAsString()).get("id").asLong();
		assertNull(jdbc.queryForObject("SELECT sede_id FROM solicitud WHERE id = ?", Long.class, id));
	}

	@Test
	void pedirEncargo_validaciones_sinCrearNada() throws Exception {
		long solicitudes = cuenta("solicitud");
		long cargos = cuenta("producto_alumno");
		assertEquals(400, s.pedir(s.tProf1, s.aProfA, null, NOTA, null).getResponse().getStatus());
		assertEquals(400, s.pedir(s.tProf1, s.aProfA, s.dobok, "  ", null).getResponse().getStatus());
		// La nota va también a producto_alumno.notas (VARCHAR(255)): 255 como mucho, nunca un 409 ni un 500.
		MvcResult larga = s.pedir(s.tProf1, s.aProfA, s.dobok, "x".repeat(300), null);
		assertEquals(400, larga.getResponse().getStatus(), larga.getResponse().getContentAsString());
		assertEquals("La nota admite como máximo 255 caracteres.", mensaje(larga));
		assertEquals(400, s.pedir(s.tProf1, s.aProfA, s.dobok, "x".repeat(501), null).getResponse().getStatus());
		assertEquals(solicitudes, cuenta("solicitud"));
		assertEquals(cargos, cuenta("producto_alumno"));
		// 255 justos, sí (y se guardan enteros en el cargo).
		MvcResult justa = s.pedir(s.tProf1, s.aProfA, s.dobok, "y".repeat(255), null);
		assertEquals(201, justa.getResponse().getStatus(), justa.getResponse().getContentAsString());
		long creada = json.readTree(justa.getResponse().getContentAsString()).get("id").asLong();
		assertEquals("y".repeat(255), jdbc.queryForObject("SELECT notas FROM producto_alumno WHERE id = ?",
				String.class, cargoDe(creada)));
		solicitudes = cuenta("solicitud");
		cargos = cuenta("producto_alumno");
		assertEquals(404, s.pedir(s.tProf1, s.aProfA, 999_999_999L, NOTA, null).getResponse().getStatus());
		assertEquals(404, s.pedir(s.tProf1, s.aProfA, otra.dobok, NOTA, null).getResponse().getStatus());
		assertEquals(400, s.pedir(s.tProf1, s.aProfA, s.mensualidad, NOTA, null).getResponse().getStatus());
		assertEquals(400, s.pedir(s.tProf1, s.aProfA, s.licencia, NOTA, null).getResponse().getStatus());
		assertEquals(solicitudes, cuenta("solicitud"));
		assertEquals(cargos, cuenta("producto_alumno"));
	}

	@Test
	void pedirEncargo_soloElProfesorParaAlumnosDeSusGrupos() throws Exception {
		long solicitudes = cuenta("solicitud");
		long cargos = cuenta("producto_alumno");
		assertEquals(403, s.pedir(s.tProf1, s.e.aNada, s.dobok, NOTA, null).getResponse().getStatus(),
				"alumno ajeno");
		assertEquals(403, s.pedir(s.tProf2, s.aProfA, s.dobok, NOTA, null).getResponse().getStatus(),
				"alumno de otro profesor");
		for (String token : List.of(s.tFamilia, s.tGestor, s.tPropietario, s.tResp)) {
			assertEquals(403, s.pedir(token, s.aProfA, s.dobok, NOTA, null).getResponse().getStatus());
		}
		assertEquals(403, s.pedir(s.tProf1, otra.aProfA, otra.dobok, NOTA, null).getResponse().getStatus(),
				"alumno de otra escuela");
		assertEquals(solicitudes, cuenta("solicitud"));
		assertEquals(cargos, cuenta("producto_alumno"));
	}

	// ------------------------------------------------------------------ encargado, recibido y entregado

	@Test
	void encargadoYRecibido_porGestionYResponsableDeLaSede() throws Exception {
		long id = s.encargo();
		List<BigDecimal> saldos = s.saldos();
		assertEquals(409, estado(post("/api/solicitudes/" + id + "/recibido"), s.tPropietario),
				"recibido desde PEDIDO");

		JsonNode encargado = leer(post("/api/solicitudes/" + id + "/encargado"), s.tPropietario, 200);
		assertEquals("ENCARGADO", encargado.get("estado").asText());
		assertNotNull(encargado.get("encargadoEn").asText());
		Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM solicitud WHERE id = ?", id);
		assertEquals(s.propietario, numero(fila, "encargado_por_usuario_id"));
		assertNotNull(fila.get("encargado_en"));
		assertNull(fila.get("resuelto_por_usuario_id"));
		MvcResult otraVez = peticion(post("/api/solicitudes/" + id + "/encargado"), s.tGestor);
		assertEquals(409, otraVez.getResponse().getStatus());
		assertEquals(NO_PENDIENTE, mensaje(otraVez));

		JsonNode recibido = leer(post("/api/solicitudes/" + id + "/recibido"), s.tGestor, 200);
		assertEquals("RECIBIDO", recibido.get("estado").asText());
		assertEquals(s.e.gestor, numero(jdbc.queryForMap("SELECT * FROM solicitud WHERE id = ?", id),
				"recibido_por_usuario_id"));
		assertEquals("SOLICITUDES ENCARGO_ENCARGADO sede=#" + s.e.sA + " solicitud=#" + id + " cargo=#" + cargoDe(id)
				+ " producto=#" + s.dobok, resumen(id, "ENCARGO_ENCARGADO"));
		assertNotNull(resumen(id, "ENCARGO_RECIBIDO"));

		// Responsable de A: uno de A sí; uno de B, 403 y sin cambios.
		long deA = s.encargo();
		assertEquals("ENCARGADO", leer(post("/api/solicitudes/" + deA + "/encargado"), s.tResp, 200).get("estado")
				.asText());
		long deB = json.readTree(s.pedir(s.tProf1, s.e.aGrupoA, s.dobok, NOTA, s.e.sB).getResponse()
				.getContentAsString()).get("id").asLong();
		assertEquals(403, estado(post("/api/solicitudes/" + deB + "/encargado"), s.tResp));
		assertEquals("PEDIDO", estadoDe(deB));

		// Otros: un tipo 1, 400; inexistente, 404; la sesión solo-profesor, 403.
		long tipo1 = tipo1DelGestor();
		assertEquals(400, estado(post("/api/solicitudes/" + tipo1 + "/encargado"), s.tPropietario));
		assertEquals("PENDIENTE", estadoDe(tipo1));
		assertEquals(404, estado(post("/api/solicitudes/999999999/encargado"), s.tPropietario));
		assertEquals(403, estado(post("/api/solicitudes/" + deB + "/encargado"), s.tProf1));
		assertEquals(403, estado(post("/api/solicitudes/" + deB + "/encargado"), s.tFamilia));
		assertEquals("PEDIDO", estadoDe(deB));
		assertEquals(saldos, s.saldos());
	}

	@Test
	void entregado_soloElSolicitanteYEnRecibido_sinExigirCobro() throws Exception {
		long id = s.encargo();
		long cargo = cargoDe(id);
		assertEquals(409, estado(post("/api/caja/profesor/solicitudes/" + id + "/entregado"), s.tProf1),
				"en PEDIDO");
		leer(post("/api/solicitudes/" + id + "/encargado"), s.tGestor, 200);
		leer(post("/api/solicitudes/" + id + "/recibido"), s.tGestor, 200);
		assertEquals(403, estado(post("/api/caja/profesor/solicitudes/" + id + "/entregado"), s.tProf2));
		assertEquals(403, estado(post("/api/caja/profesor/solicitudes/" + id + "/entregado"), s.tGestor));
		List<BigDecimal> saldos = s.saldos();

		JsonNode entregado = leer(post("/api/caja/profesor/solicitudes/" + id + "/entregado"), s.tProf1, 200);
		assertEquals("ENTREGADO", entregado.get("estado").asText());
		assertNotNull(entregado.get("entregadoEn").asText());
		assertFalse(pagado(cargo), "no exige el cargo cobrado");
		assertNull(jdbc.queryForObject("SELECT cancelado_en FROM producto_alumno WHERE id = ?", Object.class, cargo));
		assertTrue(ids(s.segmento("RESUELTAS")).contains(id));
		assertFalse(ids(s.segmento("ENCARGOS")).contains(id));
		assertEquals(409, estado(post("/api/caja/profesor/solicitudes/" + id + "/entregado"), s.tProf1));
		assertNotNull(resumen(id, "ENCARGO_ENTREGADO"));
		assertEquals(saldos, s.saldos());
	}

	// ------------------------------------------------------------------ cae el encargo

	@Test
	void retirar_enPedido_cancelaElCargo() throws Exception {
		long id = s.encargo();
		long cargo = cargoDe(id);
		List<BigDecimal> saldos = s.saldos();
		assertEquals(403, estado(post("/api/caja/profesor/solicitudes/" + id + "/retirar"), s.tProf2));
		assertEquals("PEDIDO", estadoDe(id));

		JsonNode retirada = leer(post("/api/caja/profesor/solicitudes/" + id + "/retirar"), s.tProf1, 200);
		assertEquals("RETIRADA", retirada.get("estado").asText());
		assertEquals(s.prof1, retirada.get("resueltoPorUsuarioId").asLong());
		assertFalse(retirada.get("devolucionPendiente").asBoolean());
		Map<String, Object> c = jdbc.queryForMap("SELECT * FROM producto_alumno WHERE id = ?", cargo);
		assertNotNull(c.get("cancelado_en"));
		assertEquals(s.prof1, numero(c, "cancelado_por_usuario_id"));
		assertEquals("Encargo retirado (solicitud #" + id + ")", c.get("motivo_cancelacion"));
		assertFalse(Boolean.TRUE.equals(c.get("pagado")));
		assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM cobro WHERE producto_alumno_id = ?", Long.class,
				cargo));
		for (JsonNode n : leer(get("/api/caja/profesor/cargos-pendientes"), s.tProf1, 200)) {
			assertFalse(n.get("cargoId").asLong() == cargo, "el cargo cancelado ya no está por cobrar");
		}
		assertNotNull(resumen(id, "SOLICITUD_RETIRADA"));
		assertEquals("CARGO CARGO_CANCELADO sede=#" + s.e.sA + " cargo=#" + cargo + " solicitud=#" + id,
				jdbc.queryForObject("SELECT resumen FROM auditoria_evento WHERE organizacion_id = ? AND recurso_id = ? "
						+ "AND resumen LIKE 'CARGO CARGO_CANCELADO%'", String.class, s.e.org, cargo));
		assertEquals(saldos, s.saldos());

		// Ya encargado no se retira.
		long encargado = s.encargo();
		leer(post("/api/solicitudes/" + encargado + "/encargado"), s.tGestor, 200);
		MvcResult r = peticion(post("/api/caja/profesor/solicitudes/" + encargado + "/retirar"), s.tProf1);
		assertEquals(409, r.getResponse().getStatus());
		assertEquals(NO_PENDIENTE, mensaje(r));
		assertEquals("ENCARGADO", estadoDe(encargado));
		assertNull(jdbc.queryForObject("SELECT cancelado_en FROM producto_alumno WHERE id = ?", Object.class,
				cargoDe(encargado)));
	}

	@Test
	void retirarPorLaRutaDelProfesor_suTipo2Pendiente() throws Exception {
		long cargo = cargoDe(s.encargo());
		assertEquals(201, estado(post("/api/caja/profesor/cargos/" + cargo + "/cobrar"), s.tProf1));
		long cobro = jdbc.queryForObject("SELECT id FROM cobro WHERE producto_alumno_id = ? AND estado = 'ACTIVO'",
				Long.class, cargo);
		jdbc.update("""
				INSERT INTO solicitud (organizacion_id, sede_id, tipo, estado, solicitado_por_usuario_id, solicitado_en,
				  motivo, cobro_id, producto_alumno_id, alumno_id, concepto, importe, motivo_deshacer)
				SELECT organizacion_id, sede_id, 'DESHACER_COBRO_ENTREGADO', 'PENDIENTE', ?, NOW(6), 'No pagó',
				  id, producto_alumno_id, alumno_id, concepto, importe, 'AUN_NO_PAGADO'
				FROM cobro WHERE id = ?""", s.prof1, cobro);
		long tipo2 = jdbc.queryForObject("SELECT MAX(id) FROM solicitud WHERE organizacion_id = ?", Long.class,
				s.e.org);
		assertEquals("RETIRADA", leer(post("/api/caja/profesor/solicitudes/" + tipo2 + "/retirar"), s.tProf1, 200)
				.get("estado").asText());
		assertTrue(pagado(cargo), "retirar un tipo 2 no toca el cargo");
		long tipo1 = tipo1DelGestor();
		assertEquals(403, estado(post("/api/caja/profesor/solicitudes/" + tipo1 + "/retirar"), s.tProf1),
				"no es suya");
		assertEquals("PENDIENTE", estadoDe(tipo1));
		// Un tipo 1 suyo (una cuenta con varios roles) no se retira por Mi caja: 400.
		jdbc.update("UPDATE solicitud SET solicitado_por_usuario_id = ? WHERE id = ?", s.prof1, tipo1);
		assertEquals(400, estado(post("/api/caja/profesor/solicitudes/" + tipo1 + "/retirar"), s.tProf1));
		assertEquals("PENDIENTE", estadoDe(tipo1));
	}

	@Test
	void rechazar_sinCobrar_cancelaElCargo() throws Exception {
		long id = s.encargo();
		long cargo = cargoDe(id);
		leer(post("/api/solicitudes/" + id + "/encargado"), s.tGestor, 200);
		List<BigDecimal> saldos = s.saldos();
		assertEquals(400, estado(rechazar(id, " "), s.tGestor));
		assertEquals(400, estado(post("/api/solicitudes/" + id + "/rechazar"), s.tGestor), "sin cuerpo");
		assertEquals("ENCARGADO", estadoDe(id));

		JsonNode rechazado = leer(rechazar(id, "Sin existencias"), s.tGestor, 200);
		assertEquals("RECHAZADO", rechazado.get("estado").asText());
		assertEquals("Sin existencias", rechazado.get("respuesta").asText());
		assertEquals(s.e.gestor, rechazado.get("resueltoPorUsuarioId").asLong());
		assertFalse(rechazado.get("devolucionPendiente").asBoolean());
		Map<String, Object> c = jdbc.queryForMap("SELECT * FROM producto_alumno WHERE id = ?", cargo);
		assertNotNull(c.get("cancelado_en"));
		assertEquals(s.e.gestor, numero(c, "cancelado_por_usuario_id"));
		assertEquals("Encargo rechazado (solicitud #" + id + ")", c.get("motivo_cancelacion"));
		assertTrue(ids(s.segmento("RESUELTAS")).contains(id));
		assertFalse(ids(s.segmento("ENCARGOS")).contains(id));
		assertEquals(409, estado(rechazar(id, "otra vez"), s.tPropietario));
		String resumen = resumen(id, "ENCARGO_RECHAZADO");
		assertFalse(resumen.contains("existencias"), "el motivo no va al registro");
		assertEquals(saldos, s.saldos());

		long deB = json.readTree(s.pedir(s.tProf1, s.e.aGrupoA, s.dobok, NOTA, s.e.sB).getResponse()
				.getContentAsString()).get("id").asLong();
		assertEquals(403, estado(rechazar(deB, "x"), s.tResp));
		assertEquals("PEDIDO", estadoDe(deB));
		long deA = s.encargo();
		assertEquals("RECHAZADO", leer(rechazar(deA, "x"), s.tResp, 200).get("estado").asText());
	}

	@Test
	void caerPagado_dejaDevolucionPendiente_sinTocarCargoNiCobroNiCaja() throws Exception {
		for (boolean retirando : List.of(false, true)) {
			long id = s.encargo();
			long cargo = cargoDe(id);
			assertEquals(201, estado(post("/api/caja/profesor/cargos/" + cargo + "/cobrar"), s.tProf1));
			long cobro = jdbc.queryForObject(
					"SELECT id FROM cobro WHERE producto_alumno_id = ? AND estado = 'ACTIVO'", Long.class, cargo);
			List<BigDecimal> saldos = s.saldos();

			JsonNode caido = retirando
					? leer(post("/api/caja/profesor/solicitudes/" + id + "/retirar"), s.tProf1, 200)
					: leer(rechazar(id, "Ya no lo quiere"), s.tPropietario, 200);
			assertEquals(retirando ? "RETIRADA" : "RECHAZADO", caido.get("estado").asText());
			assertTrue(caido.get("devolucionPendiente").asBoolean());
			assertEquals(Boolean.TRUE, jdbc.queryForObject("SELECT devolucion_pendiente FROM solicitud WHERE id = ?",
					Boolean.class, id));
			assertTrue(pagado(cargo));
			assertNull(jdbc.queryForObject("SELECT cancelado_en FROM producto_alumno WHERE id = ?", Object.class,
					cargo));
			assertEquals("ACTIVO", jdbc.queryForObject("SELECT estado FROM cobro WHERE id = ?", String.class, cobro));
			assertTrue(ids(s.segmento("ENCARGOS")).contains(id), "sigue en «Encargos en curso»");
			assertFalse(ids(s.segmento("RESUELTAS")).contains(id));
			assertEquals(saldos, s.saldos());
		}
	}

	// ------------------------------------------------------------------ guardas en el cargo

	@Test
	void cargoDeEncargoEnCurso_409AlCancelarYAlBorrar() throws Exception {
		long id = s.encargo();
		long cargo = cargoDe(id);
		MvcResult cancelar = peticion(post("/api/productos-alumno/" + cargo + "/cancelar")
				.contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"x\"}"), s.tPropietario);
		assertEquals(409, cancelar.getResponse().getStatus());
		assertEquals(ENCARGO_EN_CURSO, mensaje(cancelar));
		assertEquals(409, estado(post("/api/productos-alumno/" + cargo + "/cancelar")
				.contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"x\"}"), s.tGestor));
		MvcResult borrar = peticion(delete("/api/productos-alumno/" + cargo), s.tPropietario);
		assertEquals(409, borrar.getResponse().getStatus());
		assertEquals(ENCARGO_EN_CURSO, mensaje(borrar));
		assertNull(jdbc.queryForObject("SELECT cancelado_en FROM producto_alumno WHERE id = ?", Object.class, cargo));

		// Caído (cargo cancelado y nunca cobrado), el cargo ya se puede borrar: la solicitud pierde el enlace.
		leer(post("/api/caja/profesor/solicitudes/" + id + "/retirar"), s.tProf1, 200);
		assertEquals(204, estado(delete("/api/productos-alumno/" + cargo), s.tPropietario));
		assertNull(jdbc.queryForObject("SELECT producto_alumno_id FROM solicitud WHERE id = ?", Long.class, id));
	}

	@Test
	void reactivarElCargoDeUnEncargoCaido_409() throws Exception {
		long retirado = s.encargo();
		leer(post("/api/caja/profesor/solicitudes/" + retirado + "/retirar"), s.tProf1, 200);
		long rechazado = s.encargo();
		leer(rechazar(rechazado, "Sin existencias"), s.tGestor, 200);
		for (long id : List.of(retirado, rechazado)) {
			long cargo = cargoDe(id);
			for (String token : List.of(s.tPropietario, s.tGestor)) {
				MvcResult r = peticion(post("/api/productos-alumno/" + cargo + "/reactivar"), token);
				assertEquals(409, r.getResponse().getStatus(), r.getResponse().getContentAsString());
				assertEquals("El cargo es de un encargo retirado o rechazado: no se reactiva. Si el alumno aún lo "
						+ "quiere, el profesor lo vuelve a encargar.", mensaje(r));
			}
			assertNotNull(jdbc.queryForObject("SELECT cancelado_en FROM producto_alumno WHERE id = ?", Object.class,
					cargo), "sigue cancelado");
		}
	}

	// ------------------------------------------------------------------ concurrencia

	@Test
	void rechazarYCobrarALaVez_nuncaCanceladoConCobroActivo() throws Exception {
		for (int vuelta = 0; vuelta < 3; vuelta++) {
			long id = s.encargo();
			long cargo = cargoDe(id);
			List<MvcResult> r = enParalelo(() -> peticion(rechazar(id, "x"), s.tPropietario),
					() -> peticion(post("/api/caja/profesor/cargos/" + cargo + "/cobrar"), s.tProf1));
			int rechazo = r.get(0).getResponse().getStatus();
			int cobro = r.get(1).getResponse().getStatus();
			assertEquals(200, rechazo, r.get(0).getResponse().getContentAsString());
			boolean pagado = pagado(cargo);
			boolean cancelado = jdbc.queryForObject("SELECT cancelado_en FROM producto_alumno WHERE id = ?",
					Object.class, cargo) != null;
			long activos = jdbc.queryForObject(
					"SELECT COUNT(*) FROM cobro WHERE producto_alumno_id = ? AND estado = 'ACTIVO'", Long.class, cargo);
			boolean devolucion = Boolean.TRUE.equals(jdbc.queryForObject(
					"SELECT devolucion_pendiente FROM solicitud WHERE id = ?", Boolean.class, id));
			if (cobro == 201) {
				assertTrue(pagado && !cancelado && activos == 1 && devolucion, "cobró antes: devolución pendiente");
			} else {
				assertEquals(409, cobro, r.get(1).getResponse().getContentAsString());
				assertTrue(!pagado && cancelado && activos == 0 && !devolucion, "rechazó antes: cancelado");
			}
			assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM producto_alumno pa JOIN cobro c "
					+ "ON c.producto_alumno_id = pa.id AND c.estado = 'ACTIVO' WHERE pa.organizacion_id = ? "
					+ "AND pa.cancelado_en IS NOT NULL", Long.class, s.e.org), "ningún cancelado con cobro ACTIVO");
		}
	}

	@Test
	void rechazarYRetirarALaVez_unoGana409ElOtro() throws Exception {
		for (int vuelta = 0; vuelta < 3; vuelta++) {
			long id = s.encargo();
			long cargo = cargoDe(id);
			List<MvcResult> r = enParalelo(() -> peticion(rechazar(id, "x"), s.tGestor),
					() -> peticion(post("/api/caja/profesor/solicitudes/" + id + "/retirar"), s.tProf1));
			Set<Integer> estados = new HashSet<>(List.of(r.get(0).getResponse().getStatus(),
					r.get(1).getResponse().getStatus()));
			assertEquals(Set.of(200, 409), estados);
			MvcResult perdedor = r.get(0).getResponse().getStatus() == 409 ? r.get(0) : r.get(1);
			assertEquals(NO_PENDIENTE, mensaje(perdedor));
			String fin = estadoDe(id);
			assertTrue(fin.equals("RECHAZADO") || fin.equals("RETIRADA"), fin);
			assertNotNull(jdbc.queryForObject("SELECT cancelado_en FROM producto_alumno WHERE id = ?", Object.class,
					cargo));
			assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM auditoria_evento WHERE organizacion_id = ? "
					+ "AND recurso_id = ? AND resumen LIKE 'CARGO CARGO_CANCELADO%'", Long.class, s.e.org, cargo));
		}
	}

	// ------------------------------------------------------------------ listados y aislamiento

	@Test
	void misSolicitudes_soloLasSuyas() throws Exception {
		long abierto = s.encargo();
		long cerrado = s.encargo();
		leer(post("/api/caja/profesor/solicitudes/" + cerrado + "/retirar"), s.tProf1, 200);
		MvcResult r = s.pedir(s.tProf2, s.aProf2, s.camiseta, "M", null);
		assertEquals(201, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		long deProf2 = json.readTree(r.getResponse().getContentAsString()).get("id").asLong();
		long deOtra = otra.encargo();

		Set<Long> suyas = ids(leer(get("/api/caja/profesor/solicitudes"), s.tProf1, 200));
		assertTrue(suyas.contains(abierto) && suyas.contains(cerrado), suyas.toString());
		assertFalse(suyas.contains(deProf2));
		assertFalse(suyas.contains(deOtra));
		Set<Long> dePr2 = ids(leer(get("/api/caja/profesor/solicitudes"), s.tProf2, 200));
		assertTrue(dePr2.contains(deProf2));
		assertFalse(dePr2.contains(abierto));
		assertEquals(403, estado(get("/api/caja/profesor/solicitudes"), s.tGestor));
		assertEquals(403, estado(get("/api/caja/profesor/solicitudes"), s.tFamilia));
	}

	@Test
	void aislamientoDeEscuela() throws Exception {
		long contador = leer(get("/api/solicitudes/contador"), s.tPropietario, 200).get("total").asLong();
		long deOtra = otra.encargo();
		assertEquals(contador, leer(get("/api/solicitudes/contador"), s.tPropietario, 200).get("total").asLong());
		assertFalse(ids(s.segmento("ENCARGOS")).contains(deOtra));
		assertEquals(404, estado(post("/api/solicitudes/" + deOtra + "/encargado"), s.tPropietario));
		assertEquals(404, estado(post("/api/solicitudes/" + deOtra + "/recibido"), s.tGestor));
		assertEquals(404, estado(rechazar(deOtra, "x"), s.tPropietario));
		assertEquals(404, estado(post("/api/caja/profesor/solicitudes/" + deOtra + "/retirar"), s.tProf1));
		assertEquals("PEDIDO", estadoDe(deOtra));
		assertEquals("ENCARGADO", leer(post("/api/solicitudes/" + deOtra + "/encargado"), otra.tPropietario, 200)
				.get("estado").asText());
	}

	// ------------------------------------------------------------------ utilidades

	/** Un tipo 1 pendiente del gestor sobre un cargo pagado sin cobro (decisión 191). */
	private long tipo1DelGestor() throws Exception {
		jdbc.update("""
				INSERT INTO producto_alumno (alumno_id, producto_id, concepto, precio, cantidad, pagado, fecha_asignacion,
				  fecha_pago, organizacion_id, sede_id)
				VALUES (?, ?, 'CAMISETA 6C5', 10.5, 1, 1, NOW(), NOW(), ?, ?)""", s.e.aA, s.camiseta, s.e.org, s.e.sA);
		long cargo = jdbc.queryForObject("SELECT MAX(id) FROM producto_alumno WHERE organizacion_id = ?", Long.class,
				s.e.org);
		Map<String, Object> cuerpo = new HashMap<>();
		cuerpo.put("tipo", "REVERTIR_COBRO");
		cuerpo.put("productoAlumnoId", cargo);
		cuerpo.put("motivo", "x");
		return leer(post("/api/solicitudes").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(cuerpo)), s.tGestor, 201).get("id").asLong();
	}

	private MockHttpServletRequestBuilder rechazar(long id, String motivo) throws Exception {
		return post("/api/solicitudes/" + id + "/rechazar").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("motivo", motivo)));
	}

	private long cargoDe(long solicitud) {
		return jdbc.queryForObject("SELECT producto_alumno_id FROM solicitud WHERE id = ?", Long.class, solicitud);
	}

	private String estadoDe(long solicitud) {
		return jdbc.queryForObject("SELECT estado FROM solicitud WHERE id = ?", String.class, solicitud);
	}

	private boolean pagado(long cargo) {
		return Boolean.TRUE.equals(
				jdbc.queryForObject("SELECT pagado FROM producto_alumno WHERE id = ?", Boolean.class, cargo));
	}

	private long cuenta(String tabla) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM " + tabla + " WHERE organizacion_id = ?", Long.class, s.e.org);
	}

	/** El resumen del registro de esa acción sobre la solicitud (o null). */
	private String resumen(long solicitud, String accion) {
		List<String> filas = jdbc.queryForList("SELECT resumen FROM auditoria_evento WHERE organizacion_id = ? "
				+ "AND recurso_id = ? AND resumen LIKE ?", String.class, s.e.org, solicitud,
				"SOLICITUDES " + accion + " %");
		return filas.isEmpty() ? null : filas.get(0);
	}

	private static Set<Long> ids(JsonNode lista) {
		Set<Long> ids = new HashSet<>();
		lista.forEach(n -> ids.add(n.get("id").asLong()));
		return ids;
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
