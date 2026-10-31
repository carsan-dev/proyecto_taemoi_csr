package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

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
 * Pieza 9-1 (decisión 369) contra MySQL: cambio de sede del alumno.
 * <ul>
 * <li>Solo propietario y gestión; la lectura previa no modifica nada.</li>
 * <li>Mueve a la sede nueva los cargos que siguen sin cobrar (también sin sede o de otra sede, y por su inscripción).
 * No toca los pagados, los cancelados, los cobros, las entregas, los grupos ni los importes.</li>
 * <li>Tras el cambio, un cobro en efectivo de un cargo movido queda en la caja de la sede nueva y se entrega allí; la
 * caja de la sede antigua no cambia. Un alumno sin sede vuelve a tener efectivo entregable.</li>
 * <li>Concurrencia con el cobro: el cobro va en una sede coherente con su cargo, gane quien gane el bloqueo.</li>
 * <li>La ficha del panel lleva la sede principal; la familia y los listados, no.</li>
 * </ul>
 * Escuelas sintéticas de {@link EscuelaConSedesDePrueba} (A y B activas, C inactiva) más una sede D activa. Cada test
 * crea su alumno y mira solo sus filas. Datos inventados.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CambioSedeAlumnoIntegrationTest extends MySqlIntegrationTest {

	private static final String YA_ESTA = "El alumno ya está en esa sede.";
	private static final Set<String> CLAVES = Set.of("sedeActualId", "sedeActualNombre", "sedeNuevaId",
			"sedeNuevaNombre", "cargosPendientes", "importePendiente", "cargosCobrados", "importeCobrado",
			"gruposEnSedeActual");

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired DataSource dataSource;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired GrupoService grupoService;

	@MockitoBean JavaMailSender mailSender;

	EscuelaConSedesDePrueba e;
	EscuelaConSedesDePrueba otra;
	long propietario, prof, profB, sD;
	String tPropietario, tGestor, tResp, tFamilia, tProf, tProfB, tOtra;

	@BeforeAll
	void preparar() {
		e = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-e9-1-cambio");
		otra = EscuelaConSedesDePrueba.crear(jdbc, grupoService, usuarios, "it-e9-1-cambio-otra");
		propietario = e.cuenta(usuarios, "propietario");
		membresia(e.org, propietario, "PROPIETARIO");
		long propietarioOtra = otra.cuenta(usuarios, "propietario");
		membresia(otra.org, propietarioOtra, "PROPIETARIO");
		prof = profesor("prof", e.gA);
		profB = profesor("profb", e.gB);
		long familia = e.cuentaFamilia(usuarios, "familia");
		jdbc.update("INSERT INTO sede (organizacion_id, nombre, activa) VALUES (?, ?, TRUE)", e.org, "IT E9 D " + e.marca);
		sD = jdbc.queryForObject("SELECT MAX(id) FROM sede WHERE organizacion_id = ?", Long.class, e.org);

		tPropietario = e.token(jwtService, usuarios, propietario);
		tGestor = e.token(jwtService, usuarios, e.gestor);
		tResp = e.token(jwtService, usuarios, e.respA);
		tFamilia = e.token(jwtService, usuarios, familia);
		tProf = e.token(jwtService, usuarios, prof);
		tProfB = e.token(jwtService, usuarios, profB);
		tOtra = otra.token(jwtService, usuarios, propietarioOtra);
	}

	@AfterEach
	void sinFugas() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
		SecurityContextHolder.clearContext();
	}

	// ------------------------------------------------------------------ lectura previa

	@Test
	void lecturaPrevia_cuentasEImportes_sinModificarNada() throws Exception {
		long alumno = alumnoEnGrupo("PREVIA", e.sA, e.gA);
		jdbc.update("INSERT INTO alumno_grupo (grupo_id, alumno_id) VALUES (?, ?)", e.gB, alumno);
		cargo(alumno, e.sA, 12.50, 2, false); // 25,00
		cargo(alumno, null, 10.0, 1, false); // 10,00, sin sede
		cargo(alumno, sD, 5.25, 1, false); // 5,25, de otra sede
		cargo(alumno, e.sB, 100.0, 1, false); // ya en la sede nueva: no «pasa» a ella
		long cancelado = cargo(alumno, e.sA, 7.0, 1, false);
		jdbc.update("UPDATE producto_alumno SET cancelado_en = NOW() WHERE id = ?", cancelado);
		long pagado = cargo(alumno, e.sA, 30.0, 3, false); // 90,00
		cobrarEnTesoreria(pagado);
		long pagadoOtraSede = cargo(alumno, sD, 11.0, 1, false); // de otra sede: no se cuenta
		cobrarEnTesoreria(pagadoOtraSede);
		cargo(e.aA, e.sA, 1000.0, 1, false); // de otro alumno
		Foto antes = foto(alumno);

		JsonNode previa = leer(get(rutaPrevia(alumno, e.sB)), tGestor, 200);

		assertEquals(CLAVES, campos(previa));
		assertEquals(e.sA, previa.get("sedeActualId").asLong());
		assertEquals(nombreDe(e.sA), previa.get("sedeActualNombre").asText());
		assertEquals(e.sB, previa.get("sedeNuevaId").asLong());
		assertEquals(nombreDe(e.sB), previa.get("sedeNuevaNombre").asText());
		assertEquals(3, previa.get("cargosPendientes").asLong());
		assertImporte("40.25", previa.get("importePendiente"));
		assertEquals(1, previa.get("cargosCobrados").asLong());
		assertImporte("90.00", previa.get("importeCobrado"));
		assertEquals(1, previa.get("gruposEnSedeActual").asLong());
		assertEquals(antes, foto(alumno), "la lectura previa no modifica nada");
	}

	@Test
	void lecturaPrevia_alumnoSinSede_sedeActualNula() throws Exception {
		long alumno = e.alumno("E9PREVIASIN" + marca(), null, true);
		cargo(alumno, null, 10.0, 1, false);
		long pagado = cargo(alumno, null, 20.0, 1, false);
		cobrarEnTesoreria(pagado);

		JsonNode previa = leer(get(rutaPrevia(alumno, e.sB)), tGestor, 200);

		assertTrue(previa.get("sedeActualId").isNull());
		assertTrue(previa.get("sedeActualNombre").isNull());
		assertEquals(1, previa.get("cargosPendientes").asLong());
		assertImporte("10.00", previa.get("importePendiente"));
		assertEquals(1, previa.get("cargosCobrados").asLong(), "los cobrados sin sede se quedan sin sede");
		assertImporte("20.00", previa.get("importeCobrado"));
		assertEquals(0, previa.get("gruposEnSedeActual").asLong());
	}

	// ------------------------------------------------------------------ cambio: qué se mueve

	@Test
	void cambio_mueveSoloLoQueSigueSinCobrar() throws Exception {
		long alumno = alumnoEnGrupo("MUEVE", e.sA, e.gA);
		long inscripcion = alumnoDeporte(alumno);
		long pendiente = cargo(alumno, e.sA, 25.0, 3, false);
		long sinSede = cargo(alumno, null, 10.0, 1, false);
		long deOtraSede = cargo(alumno, sD, 10.0, 1, false);
		long soloInscripcion = cargoSoloInscripcion(inscripcion, e.sA);
		long pagadoNulo = cargo(alumno, e.sA, 10.0, 1, false);
		jdbc.update("UPDATE producto_alumno SET pagado = NULL WHERE id = ?", pagadoNulo);
		long yaEnLaNueva = cargo(alumno, e.sB, 10.0, 1, false);
		long cancelado = cargo(alumno, e.sA, 10.0, 1, false);
		jdbc.update("UPDATE producto_alumno SET cancelado_en = NOW() WHERE id = ?", cancelado);
		long cobrado = cargo(alumno, e.sA, 20.0, 1, false);
		long cobro = cobrarComoProfesor(cobrado, tProf);
		long entregado = cargo(alumno, e.sA, 30.0, 1, false);
		long cobroEntregado = cobrarComoProfesor(entregado, tProf);
		long entrega = entregar(e.sA, prof, cobroEntregado, "30.00");
		long deOtro = cargo(e.aA, e.sA, 10.0, 1, false);
		String importes = importes(alumno);
		long primero = ultimoEvento();

		JsonNode r = leer(cambio(alumno, e.sB), tGestor, 200);

		assertEquals(CLAVES, campos(r));
		assertEquals(e.sA, r.get("sedeActualId").asLong(), "la sede de la que viene");
		assertEquals(e.sB, r.get("sedeNuevaId").asLong());
		assertEquals(5, r.get("cargosPendientes").asLong(), "los cargos movidos");

		assertEquals(e.sB, e.sedeDe(alumno));
		for (long movido : List.of(pendiente, sinSede, deOtraSede, soloInscripcion, pagadoNulo, yaEnLaNueva)) {
			assertEquals(e.sB, sedeDelCargo(movido), "cargo " + movido);
		}
		assertEquals(e.sA, sedeDelCargo(cancelado), "un cargo cancelado no se mueve");
		assertEquals(e.sA, sedeDelCargo(cobrado), "un cargo cobrado no se mueve");
		assertEquals(e.sA, sedeDelCargo(entregado));
		assertEquals(e.sA, sedeDelCargo(deOtro), "ni los de otro alumno");
		assertEquals(e.sA, jdbc.queryForObject("SELECT sede_id FROM cobro WHERE id = ?", Long.class, cobro));
		assertEquals(e.sA, jdbc.queryForObject("SELECT sede_id FROM cobro WHERE id = ?", Long.class, cobroEntregado));
		Map<String, Object> filaEntrega = jdbc.queryForMap("SELECT sede_id, estado FROM entrega_caja WHERE id = ?", entrega);
		assertEquals(e.sA, ((Number) filaEntrega.get("sede_id")).longValue());
		assertEquals("ACTIVA", filaEntrega.get("estado"));
		assertEquals(entrega,
				jdbc.queryForObject("SELECT entrega_caja_id FROM cobro WHERE id = ?", Long.class, cobroEntregado));
		assertEquals(importes, importes(alumno), "los importes no cambian");
		assertEquals(List.of(e.gA), gruposDe(alumno), "los grupos no cambian");

		List<Map<String, Object>> eventos = jdbc.queryForList(
				"SELECT * FROM auditoria_evento WHERE id > ? AND organizacion_id = ? ORDER BY id", primero, e.org);
		assertEquals(1, eventos.size(), "una sola fila de registro: " + eventos);
		Map<String, Object> evento = eventos.get(0);
		assertEquals("sedes", evento.get("modulo"));
		assertEquals(alumno, ((Number) evento.get("recurso_id")).longValue());
		assertEquals("SEDES ALUMNO_SEDE_CAMBIADA alumno=#" + alumno + " sedeAnterior=#" + e.sA + " sedeNueva=#" + e.sB
				+ " cargosMovidos=#5", evento.get("resumen"));
		assertEquals(e.gestor, ((Number) evento.get("usuario_id")).longValue());
	}

	@Test
	void cambio_conCobroActivoSobreUnCargoSinPagar_noLoMueve() throws Exception {
		// No debería existir (el pago y el cobro cambian juntos, con el cargo bloqueado), pero es dinero: se excluye.
		long alumno = alumnoEnGrupo("INCOHERENTE", e.sA, e.gA);
		long cargo = cargo(alumno, e.sA, 10.0, 1, false);
		long cobro = cobrarComoProfesor(cargo, tProf);
		jdbc.update("UPDATE producto_alumno SET pagado = FALSE, fecha_pago = NULL WHERE id = ?", cargo);

		JsonNode previa = leer(get(rutaPrevia(alumno, e.sB)), tGestor, 200);
		assertEquals(0, previa.get("cargosPendientes").asLong());
		JsonNode r = leer(cambio(alumno, e.sB), tGestor, 200);

		assertEquals(0, r.get("cargosPendientes").asLong());
		assertEquals(e.sA, sedeDelCargo(cargo));
		assertEquals(e.sA, jdbc.queryForObject("SELECT sede_id FROM cobro WHERE id = ?", Long.class, cobro));
	}

	@Test
	void cambio_lasCuentasDeLaLecturaPreviaCoincidenConLasDelResultado() throws Exception {
		long alumno = alumnoEnGrupo("IGUALES", e.sA, e.gA);
		cargo(alumno, e.sA, 12.50, 2, false);
		long pagado = cargo(alumno, e.sA, 30.0, 1, false);
		cobrarEnTesoreria(pagado);
		JsonNode previa = leer(get(rutaPrevia(alumno, e.sB)), tPropietario, 200);
		JsonNode resultado = leer(cambio(alumno, e.sB), tPropietario, 200);
		assertEquals(previa, resultado);
	}

	// ------------------------------------------------------------------ caja tras el cambio

	@Test
	void cargoMovido_seCobraYSeEntregaEnLaSedeNueva_yLaAntiguaNoCambia() throws Exception {
		long alumno = alumnoEnGrupo("CAJA", e.sA, e.gA);
		long cobradoAntes = cargo(alumno, e.sA, 15.0, 1, false);
		cobrarComoProfesor(cobradoAntes, tProf);
		long pendiente = cargo(alumno, e.sA, 40.0, 1, false);
		JsonNode cajaAntigua = leer(get("/api/caja/sedes/" + e.sA + "/resumen"), tGestor, 200);
		JsonNode pendienteAntigua = leer(get("/api/caja/sedes/" + e.sA + "/pendiente-entrega"), tGestor, 200);

		leer(cambio(alumno, e.sB), tGestor, 200);
		long cobro = cobrarComoProfesor(pendiente, tProf);

		assertEquals(e.sB, jdbc.queryForObject("SELECT sede_id FROM cobro WHERE id = ?", Long.class, cobro),
				"el cobro nace en la sede nueva del cargo");
		assertEquals(400, estado(peticionEntrega(e.sA, prof, cobro, "40.00"), tGestor),
				"en la sede antigua no se entrega");
		assertEquals(cajaAntigua, leer(get("/api/caja/sedes/" + e.sA + "/resumen"), tGestor, 200));
		assertEquals(pendienteAntigua, leer(get("/api/caja/sedes/" + e.sA + "/pendiente-entrega"), tGestor, 200));

		entregar(e.sB, prof, cobro, "40.00");
		assertNotNull(jdbc.queryForObject("SELECT entrega_caja_id FROM cobro WHERE id = ?", Long.class, cobro));
		assertEquals(cajaAntigua, leer(get("/api/caja/sedes/" + e.sA + "/resumen"), tGestor, 200));
	}

	@Test
	void alumnoSinSede_elCambioFunciona_ySuEfectivoVuelveAPoderEntregarse() throws Exception {
		long alumno = e.alumno("E9SINSEDE" + marca(), null, true);
		jdbc.update("INSERT INTO alumno_grupo (grupo_id, alumno_id) VALUES (?, ?)", e.gA, alumno);
		long pendiente = cargo(alumno, null, 18.0, 1, false);

		JsonNode r = leer(cambio(alumno, sD), tPropietario, 200);

		assertTrue(r.get("sedeActualId").isNull());
		assertEquals(1, r.get("cargosPendientes").asLong());
		assertEquals(sD, e.sedeDe(alumno));
		assertEquals(sD, sedeDelCargo(pendiente));
		long cobro = cobrarComoProfesor(pendiente, tProf);
		assertEquals(sD, jdbc.queryForObject("SELECT sede_id FROM cobro WHERE id = ?", Long.class, cobro));
		entregar(sD, prof, cobro, "18.00");
		assertTrue(jdbc.queryForList(
				"SELECT resumen FROM auditoria_evento WHERE organizacion_id = ? AND recurso_id = ? AND modulo = 'sedes'",
				String.class, e.org, alumno).stream().anyMatch(x -> x.contains("sedeAnterior=NINGUNO")));
	}

	// ------------------------------------------------------------------ permisos y validaciones

	@Test
	void permisos_soloPropietarioYGestion() throws Exception {
		long alumno = alumnoEnGrupo("PERMISOS", e.sA, e.gA);
		long cargo = cargo(alumno, e.sA, 10.0, 1, false);

		for (String denegado : List.of(tResp, tFamilia, tProf)) {
			assertEquals(403, estado(get(rutaPrevia(alumno, e.sB)), denegado));
			assertEquals(403, estado(cambio(alumno, e.sB), denegado));
		}
		assertEquals(404, estado(get(rutaPrevia(alumno, otra.sA)), tOtra), "otra escuela");
		assertEquals(404, estado(cambio(alumno, otra.sA), tOtra), "otra escuela");
		assertEquals(404, estado(cambio(alumno, e.sB), tOtra), "otra escuela, con una sede de esta");
		assertTrue(List.of(401, 403).contains(mvc.perform(get(rutaPrevia(alumno, e.sB))).andReturn().getResponse().getStatus()),
				"anónimo");
		assertTrue(List.of(401, 403).contains(mvc.perform(cambio(alumno, e.sB)).andReturn().getResponse().getStatus()),
				"anónimo");
		assertEquals(e.sA, e.sedeDe(alumno));
		assertEquals(e.sA, sedeDelCargo(cargo));

		assertEquals(200, estado(cambio(alumno, e.sB), tPropietario));
		assertEquals(200, estado(cambio(alumno, sD), tGestor));
		assertEquals(sD, e.sedeDe(alumno));
	}

	@Test
	void validaciones_sinCambiarNada() throws Exception {
		long alumno = alumnoEnGrupo("VALIDA", e.sA, e.gA);
		long cargo = cargo(alumno, e.sA, 10.0, 1, false);
		long primero = ultimoEvento();

		assertEquals(400, estado(cambio(alumno, e.sC), tGestor), "sede inactiva");
		assertEquals(400, estado(get(rutaPrevia(alumno, e.sC)), tGestor), "sede inactiva");
		assertEquals(400, estado(cambio(alumno, otra.sA), tGestor), "sede de otra escuela");
		assertEquals(400, estado(get(rutaPrevia(alumno, otra.sA)), tGestor), "sede de otra escuela");
		assertEquals(400, estado(conCuerpo(put("/api/alumnos/" + alumno + "/sede"), "{}"), tGestor), "sin sede");
		assertEquals(400, estado(get("/api/alumnos/" + alumno + "/cambio-sede"), tGestor), "sin sede");
		MvcResult misma = peticion(cambio(alumno, e.sA), tGestor);
		assertEquals(400, misma.getResponse().getStatus());
		assertTrue(contenido(misma).contains(YA_ESTA), contenido(misma));
		assertEquals(400, estado(get(rutaPrevia(alumno, e.sA)), tGestor), "misma sede");
		assertEquals(404, estado(cambio(Long.MAX_VALUE, e.sB), tGestor), "no existe");
		assertEquals(404, estado(get(rutaPrevia(Long.MAX_VALUE, e.sB)), tGestor), "no existe");

		assertEquals(e.sA, e.sedeDe(alumno));
		assertEquals(e.sA, sedeDelCargo(cargo));
		assertEquals(0L, jdbc.queryForObject("""
				SELECT COUNT(*) FROM auditoria_evento WHERE id > ? AND organizacion_id = ? AND modulo = 'sedes'
				  AND resumen LIKE 'SEDES ALUMNO_SEDE_CAMBIADA%'""", Long.class, primero, e.org));
	}

	@Test
	void alumnoAnonimizado_noSeCambia409() throws Exception {
		long alumno = alumnoEnGrupo("ANONIMO", e.sA, e.gA);
		jdbc.update("UPDATE alumno SET anonimizado_en = NOW() WHERE id = ?", alumno);
		assertEquals(409, estado(cambio(alumno, e.sB), tPropietario));
		assertEquals(409, estado(get(rutaPrevia(alumno, e.sB)), tPropietario));
		assertEquals(e.sA, e.sedeDe(alumno));
	}

	// ------------------------------------------------------------------ concurrencia con el cobro

	/**
	 * Un cobro en curso (JDBC sin confirmar: cargo bloqueado y ya pagado) gana el bloqueo: el cambio espera, ve el cargo
	 * pagado y no lo mueve. El cargo y el cobro siguen en la sede vieja, los dos.
	 */
	@Test
	void cobroEnCurso_elCambioEsperaYNoMueveElCargoCobrado() throws Exception {
		long alumno = alumnoEnGrupo("CARRERA1", e.sA, e.gA);
		long cargo = cargo(alumno, e.sA, 10.0, 1, false);
		long otro = cargo(alumno, e.sA, 10.0, 1, false);
		ExecutorService hilo = Executors.newSingleThreadExecutor();
		try (Connection conexion = dataSource.getConnection()) {
			conexion.setAutoCommit(false);
			try (PreparedStatement bloqueo = conexion
					.prepareStatement("SELECT id FROM producto_alumno WHERE id = ? FOR UPDATE")) {
				bloqueo.setLong(1, cargo);
				bloqueo.executeQuery().close();
			}
			try (PreparedStatement pago = conexion
					.prepareStatement("UPDATE producto_alumno SET pagado = TRUE, fecha_pago = NOW() WHERE id = ?")) {
				pago.setLong(1, cargo);
				pago.executeUpdate();
			}
			try (PreparedStatement alta = conexion.prepareStatement("""
					INSERT INTO cobro (organizacion_id, producto_alumno_id, sede_id, alumno_id, concepto, importe, metodo,
					  fecha_cobro, origen, estado) VALUES (?, ?, ?, ?, 'CARGO IT', 10.00, 'EFECTIVO', NOW(6), 'TESORERIA',
					  'ACTIVO')""")) {
				alta.setLong(1, e.org);
				alta.setLong(2, cargo);
				alta.setLong(3, e.sA);
				alta.setLong(4, alumno);
				alta.executeUpdate();
			}
			Future<MvcResult> peticion = hilo.submit(() -> peticion(cambio(alumno, e.sB), tGestor));
			Thread.sleep(1500);
			assertFalse(peticion.isDone(), "el cambio debe esperar al bloqueo del cargo");
			conexion.commit();
			MvcResult r = peticion.get(60, TimeUnit.SECONDS);
			assertEquals(200, r.getResponse().getStatus(), contenido(r));
			assertEquals(1, json.readTree(contenido(r)).get("cargosPendientes").asLong(), "solo el otro cargo");
		} finally {
			hilo.shutdownNow();
		}
		assertEquals(e.sB, e.sedeDe(alumno));
		assertEquals(e.sA, sedeDelCargo(cargo), "el cargo cobrado se queda");
		assertEquals(e.sB, sedeDelCargo(otro));
		assertEquals(e.sA, jdbc.queryForObject("SELECT sede_id FROM cobro WHERE producto_alumno_id = ?", Long.class, cargo));
	}

	/**
	 * El cambio gana el bloqueo del cargo: una conexión retiene al alumno, así que el cambio se queda con el cargo
	 * bloqueado esperando al alumno. El cobro real espera al cargo y, al soltarse todo, lo relee y nace en la sede nueva.
	 */
	@Test
	void cambioEnCurso_elCobroEsperaYNaceEnLaSedeNueva() throws Exception {
		long alumno = alumnoEnGrupo("CARRERA2", e.sA, e.gA);
		long cargo = cargo(alumno, e.sA, 10.0, 1, false);
		ExecutorService hilos = Executors.newFixedThreadPool(2);
		try (Connection conexion = dataSource.getConnection()) {
			conexion.setAutoCommit(false);
			try (PreparedStatement bloqueo = conexion.prepareStatement("SELECT id FROM alumno WHERE id = ? FOR UPDATE")) {
				bloqueo.setLong(1, alumno);
				bloqueo.executeQuery().close();
			}
			Future<MvcResult> cambio = hilos.submit(() -> peticion(cambio(alumno, e.sB), tGestor));
			Thread.sleep(1500);
			assertFalse(cambio.isDone(), "el cambio espera al alumno, con el cargo ya bloqueado");
			Future<MvcResult> cobro = hilos.submit(() -> peticion(post("/api/caja/profesor/cargos/" + cargo + "/cobrar"), tProf));
			Thread.sleep(1500);
			assertFalse(cobro.isDone(), "el cobro espera al bloqueo del cargo");
			conexion.rollback();
			MvcResult r = cambio.get(60, TimeUnit.SECONDS);
			assertEquals(200, r.getResponse().getStatus(), contenido(r));
			assertEquals(1, json.readTree(contenido(r)).get("cargosPendientes").asLong());
			MvcResult c = cobro.get(60, TimeUnit.SECONDS);
			assertEquals(201, c.getResponse().getStatus(), contenido(c));
		} finally {
			hilos.shutdownNow();
		}
		assertEquals(e.sB, sedeDelCargo(cargo));
		assertEquals(e.sB, jdbc.queryForObject("SELECT sede_id FROM cobro WHERE producto_alumno_id = ?", Long.class, cargo),
				"el cobro nace con la sede del cargo ya movido");
	}

	// ------------------------------------------------------------------ ficha

	@Test
	void ficha_llevaLaSedePrincipal_soloEnLaFichaDelPanel() throws Exception {
		long alumno = alumnoEnGrupo("FICHA", e.sA, e.gA);
		JsonNode ficha = leer(get("/api/alumnos/" + alumno), tGestor, 200);
		assertEquals(e.sA, ficha.get("sedePrincipalId").asLong());
		assertEquals(nombreDe(e.sA), ficha.get("sedePrincipalNombre").asText());
		assertEquals(alumno, ficha.get("id").asLong(), "el resto de la ficha sigue igual");
		assertEquals(leer(get("/api/alumnos/" + alumno), tResp, 200), ficha, "la misma para el responsable");

		long sinSede = e.alumno("E9FICHASIN" + marca(), null, true);
		JsonNode vacia = leer(get("/api/alumnos/" + sinSede), tGestor, 200);
		assertTrue(vacia.has("sedePrincipalId") && vacia.get("sedePrincipalId").isNull());
		assertTrue(vacia.has("sedePrincipalNombre") && vacia.get("sedePrincipalNombre").isNull());

		assertEquals(403, estado(get("/api/alumnos/" + alumno), tFamilia), "la familia no lee esta ruta");
		assertEquals(403, estado(get("/api/alumnos/" + alumno + "/completo"), tFamilia));
		JsonNode completo = leer(get("/api/alumnos/" + alumno + "/completo"), tGestor, 200);
		assertFalse(completo.toString().contains("sedePrincipal"), "las demás lecturas del alumno no cambian");
		JsonNode listado = leer(get("/api/alumnos").param("page", "1").param("size", "5"), tGestor, 200);
		assertFalse(listado.toString().contains("sedePrincipal"), "el listado no cambia");
	}

	// ------------------------------------------------------------------ datos

	private void membresia(long organizacion, long usuario, String rol) {
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, ?)", usuario, organizacion,
				rol);
	}

	private long profesor(String nombre, long grupo) {
		long id = e.cuentaFamilia(usuarios, nombre);
		membresia(e.org, id, "PROFESOR");
		jdbc.update("INSERT INTO asignacion_profesor (organizacion_id, usuario_id, grupo_id, rol) "
				+ "VALUES (?, ?, ?, 'TITULAR')", e.org, id, grupo);
		return id;
	}

	private static String marca() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

	private long alumnoEnGrupo(String nombre, long sede, long grupo) {
		long alumno = e.alumno("E9" + nombre + marca(), sede, true);
		jdbc.update("INSERT INTO alumno_grupo (grupo_id, alumno_id) VALUES (?, ?)", grupo, alumno);
		return alumno;
	}

	private long alumnoDeporte(long alumno) {
		e.taekwondo(alumno, false);
		return jdbc.queryForObject("SELECT id FROM alumno_deporte WHERE alumno_id = ?", Long.class, alumno);
	}

	private long cargo(long alumno, Long sede, double precio, int cantidad, boolean pagado) {
		jdbc.update("""
				INSERT INTO producto_alumno (alumno_id, concepto, precio, cantidad, pagado, fecha_asignacion,
				  organizacion_id, sede_id) VALUES (?, 'CARGO IT', ?, ?, ?, NOW(), ?, ?)""", alumno, precio, cantidad,
				pagado, e.org, sede);
		return jdbc.queryForObject("SELECT MAX(id) FROM producto_alumno WHERE alumno_id = ?", Long.class, alumno);
	}

	/** Cargo antiguo: sin {@code alumno_id}, llega al alumno solo por la inscripción. */
	private long cargoSoloInscripcion(long inscripcion, long sede) {
		jdbc.update("""
				INSERT INTO producto_alumno (alumno_deporte_id, concepto, precio, cantidad, pagado, fecha_asignacion,
				  organizacion_id, sede_id) VALUES (?, 'CARGO IT', 10.0, 1, 0, NOW(), ?, ?)""", inscripcion, e.org, sede);
		return jdbc.queryForObject("SELECT MAX(id) FROM producto_alumno WHERE alumno_deporte_id = ?", Long.class,
				inscripcion);
	}

	private void cobrarEnTesoreria(long cargo) throws Exception {
		assertEquals(200, estado(conCuerpo(put("/api/productos-alumno/" + cargo),
				"{\"pagado\":true,\"metodoPago\":\"TRANSFERENCIA\"}"), tGestor));
	}

	private long cobrarComoProfesor(long cargo, String token) throws Exception {
		JsonNode r = leer(post("/api/caja/profesor/cargos/" + cargo + "/cobrar"), token, 201);
		return r.get("cobroId").asLong();
	}

	private long entregar(long sede, long profesor, long cobro, String importe) throws Exception {
		return leer(peticionEntrega(sede, profesor, cobro, importe), tGestor, 201).get("id").asLong();
	}

	private MockHttpServletRequestBuilder peticionEntrega(long sede, long profesor, long cobro, String importe)
			throws Exception {
		return conCuerpo(post("/api/caja/sedes/" + sede + "/entregas"), json.writeValueAsString(Map.of(
				"entregadoPorUsuarioId", profesor, "cobroIds", List.of(cobro), "importeContado", new BigDecimal(importe))));
	}

	private Long sedeDelCargo(long cargo) {
		return jdbc.queryForObject("SELECT sede_id FROM producto_alumno WHERE id = ?", Long.class, cargo);
	}

	private String nombreDe(long sede) {
		return jdbc.queryForObject("SELECT nombre FROM sede WHERE id = ?", String.class, sede);
	}

	private List<Long> gruposDe(long alumno) {
		return jdbc.queryForList("SELECT grupo_id FROM alumno_grupo WHERE alumno_id = ? ORDER BY grupo_id", Long.class,
				alumno);
	}

	private String importes(long alumno) {
		return jdbc.queryForList("""
				SELECT pa.id, pa.precio, pa.cantidad, pa.pagado FROM producto_alumno pa
				WHERE pa.alumno_id = ? ORDER BY pa.id""", alumno).toString();
	}

	private long ultimoEvento() {
		return jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM auditoria_evento", Long.class);
	}

	/** Todo lo que el cambio de sede podría tocar del alumno. */
	record Foto(String alumno, String cargos, String cobros, String grupos, long eventos) {
	}

	private Foto foto(long alumno) {
		return new Foto(jdbc.queryForList("SELECT * FROM alumno WHERE id = ?", alumno).toString(),
				jdbc.queryForList("SELECT * FROM producto_alumno WHERE alumno_id = ? ORDER BY id", alumno).toString(),
				jdbc.queryForList("SELECT * FROM cobro WHERE alumno_id = ? ORDER BY id", alumno).toString(),
				gruposDe(alumno).toString(), jdbc.queryForObject("""
						SELECT COUNT(*) FROM auditoria_evento WHERE organizacion_id = ? AND modulo = 'sedes'""", Long.class,
						e.org));
	}

	// ------------------------------------------------------------------ HTTP

	private static String rutaPrevia(long alumno, long sede) {
		return "/api/alumnos/" + alumno + "/cambio-sede?sedeId=" + sede;
	}

	private MockHttpServletRequestBuilder cambio(long alumno, long sede) {
		return conCuerpo(put("/api/alumnos/" + alumno + "/sede"), "{\"sedeId\":" + sede + "}");
	}

	private static MockHttpServletRequestBuilder conCuerpo(MockHttpServletRequestBuilder peticion, String cuerpo) {
		return peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
	}

	private JsonNode leer(MockHttpServletRequestBuilder peticion, String token, int esperado) throws Exception {
		MvcResult r = peticion(peticion, token);
		assertEquals(esperado, r.getResponse().getStatus(), contenido(r));
		return json.readTree(contenido(r));
	}

	private int estado(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		return peticion(peticion, token).getResponse().getStatus();
	}

	private MvcResult peticion(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		return mvc.perform(peticion.cookie(new Cookie("jwt", token))).andReturn();
	}

	private static String contenido(MvcResult r) throws Exception {
		return r.getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

	private static Set<String> campos(JsonNode nodo) {
		Set<String> campos = new HashSet<>();
		for (Iterator<String> it = nodo.fieldNames(); it.hasNext();) {
			campos.add(it.next());
		}
		return campos;
	}

	private static void assertImporte(String esperado, JsonNode valor) {
		assertEquals(0, new BigDecimal(esperado).compareTo(valor.decimalValue()), "importe " + valor);
	}
}
