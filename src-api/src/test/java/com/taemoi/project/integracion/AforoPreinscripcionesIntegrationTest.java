package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;
import org.mockito.invocation.Invocation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.taemoi.project.config.ValidacionRelojConfig;
import com.taemoi.project.entities.AuthProvider;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.PreinscripcionRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.AforoPreinscripcionService;
import com.taemoi.project.services.EmailService;
import com.taemoi.project.services.JwtService;
import com.taemoi.project.services.PreinscripcionService;
import com.taemoi.project.services.TemporadaService;
import com.taemoi.project.services.TurnoService;

import jakarta.servlet.http.Cookie;

/**
 * Red de seguridad de R2-P3 (aforo por lotes y promoción de la lista de espera de una vez), escrita y pasada en
 * 9ef8e11c ANTES del arreglo: fija lo que hoy hace la organización 1 (Moiskimdo) con una siembra determinista.
 *
 * <h2>Qué fija</h2>
 * <ul>
 * <li>JSON dorado ({@code src/test/resources/dorado/r2/aforo/}) de {@code public/grupos/{deporte}},
 * {@code public/turnos/{deporte}} y {@code /api/turnos/dto} con turnos llenos y con plaza, alumnos que no cuentan
 * (ficha inactiva, deporte del grupo inactivo, solo otro deporte), pendientes de otra temporada y finalizadas.</li>
 * <li>La promoción tras cancelar, cambiar turnos, dar de baja a un alumno y cambiar el límite: qué solicitudes pasan a
 * PENDIENTE, en qué orden (el de los correos), estados finales, posiciones que quedan y correos enviados (capturados
 * con {@link EmailService} simulado, en el hilo de la petición).</li>
 * <li>El límite de turno de la organización (no el de por defecto) y la regla de hoy: una solicitud de la cola sube si
 * ALGUNO de sus turnos tiene plaza, y al subir cuenta como pendiente en TODOS sus turnos de
 * {@code preinscripcion_turno} (aunque eso pase del límite en alguno); una solicitud antigua solo con
 * {@code turno_id} (sin filas en {@code preinscripcion_turno}) sube con la plaza de ese turno pero no la ocupa.</li>
 * <li>Lo que hoy es un error, tal cual: una solicitud de la cola con un turno sin grupo hace fallar la promoción entera
 * (400) cuando le llega el turno. Desde R2-P7d los avisos de plaza salen tras el commit: los de las anteriores ya no
 * salen (antes salían aunque su promoción se deshiciera).</li>
 * </ul>
 *
 * <h2>Datos</h2>
 * Sintéticos, ids en rangos propios (8.100.000 alumnos, 8.200.000 grupos, 8.300.000 turnos, 8.400.000 fichas
 * deportivas, 8.700.000 preinscripciones), límite de turno {@value #LIMITE} mientras dura la clase. Cada test siembra de
 * cero y borra al terminar. La organización 1 puede tener restos de otras clases (otras colas, otros turnos): los
 * listados se filtran a lo sembrado y los correos a los de la siembra; sus turnos no comparten plaza con los de aquí.
 *
 * <p>Ocupación sembrada (límite 3): T1 lunes = 1 alumno + P1 + P2 = 3; T2 miércoles = 2 alumnos + P2 = 3; T3 martes =
 * 1 alumno + P3 + P4 = 3; T4 jueves (kickboxing) = 1 + P5 + P6 = 3; T7 lunes tarde = 1 (con plaza). Cola (orden
 * {@code creadaEn, id}): EKB (T4), ET1A (T1), ET1B (T1, misma hora que ET1A y id mayor), EM (T2+T3), ELEG (antigua,
 * solo {@code turno_id}=T3), ET3 (T3).
 *
 * <p>Regenerar los dorados de esta clase (solo tras un cambio de contrato decidido):
 * {@code -Ddorado.aforo.regenerar=true}.
 */
@SpringBootTest(properties = {
		"spring.jpa.properties.hibernate.session_factory.statement_inspector=com.taemoi.project.integracion.ContadorSentencias",
		"app.preinscripciones.admin-email=" + AforoPreinscripcionesIntegrationTest.ADMIN })
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AforoPreinscripcionesIntegrationTest extends MySqlIntegrationTest {

	static final String ADMIN = "admin-aforo-r2p3@example.test";
	static final int LIMITE = 3;
	private static final String DOMINIO = "@aforo-r2p3.test";
	private static final String PREFIJO = "AFORO-R2P3";
	private static final String OTRA_TEMPORADA = "1999-2000";
	private static final Instant HOY = ZonedDateTime.of(2026, 10, 1, 10, 0, 0, 0, ValidacionRelojConfig.ZONA).toInstant();
	private static final boolean REGENERAR = Boolean.getBoolean("dorado.aforo.regenerar");
	private static final String RECURSOS = "dorado/r2/aforo/";
	private static final Path FUENTES = Path.of("src", "test", "resources", "dorado", "r2", "aforo");
	private static final Path REALES = Path.of("target", "dorado-r2-aforo-real");

	private static final long ALUMNO = 8_100_000L;
	private static final long GRUPO = 8_200_000L;
	private static final long TURNO = 8_300_000L;
	private static final long ALUMNO_DEPORTE = 8_400_000L;
	private static final long PREINSCRIPCION = 8_700_000L;
	private static final long ANCHO = 9_999L;
	private static final int EXPEDIENTE = 8_800_000;
	private static final List<String> TABLAS_CON_RANGO = List.of("preinscripcion", "alumno_deporte", "alumno", "turno",
			"grupo");

	/**
	 * Límites de sentencias SQL (Δ sobre la misma petición antes de sembrar en los listados de toda la organización;
	 * absolutos en las escrituras). Se añadieron con el arreglo de R2-P3 (medido + margen); en 9ef8e11c no se
	 * comprobaban (el aforo N+1 los hacía depender del tamaño de la escuela). Clave = nombre del dorado o escritura.
	 */
	private static final Map<String, Integer> LIMITES = Map.ofEntries(
			// Δ medido tras R2-P3 + 2 (en 9ef8e11c: grupos TKD Δ52, KB Δ11, turnos TKD Δ15, KB Δ3, /turnos/dto Δ35).
			Map.entry("public_grupos_tkd", 8), Map.entry("public_grupos_kb", 4), Map.entry("public_turnos_tkd", 2),
			Map.entry("public_turnos_kb", 2), Map.entry("turnos_dto", 3), Map.entry("turnos_dto_anonimo", 3),
			// Escrituras, absolutas: medido tras R2-P3 + margen para lo que dejen otras clases en la organización 1 (en
			// 9ef8e11c: cancelar 52, cambiar turnos 56, baja 45, límite 3/4/5: 40/48/57, y crecían con la escuela).
			Map.entry("cancelar", 30), Map.entry("cambiar_turnos", 34), Map.entry("baja_alumno", 28),
			Map.entry("limite_3", 15), Map.entry("limite_4", 28), Map.entry("limite_5", 38));

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired TemporadaService temporadaService;
	@Autowired TurnoService turnoService;
	@Autowired AforoPreinscripcionService aforo;
	@Autowired PreinscripcionService preinscripcionService;
	@Autowired PreinscripcionRepository preinscripciones;
	@Autowired PlatformTransactionManager txManager;

	@MockitoBean EmailService email;

	final Map<Long, String> simbolos = new HashMap<>();
	final Map<String, Long> ids = new HashMap<>();
	final Map<String, String> medidas = new TreeMap<>();
	String temporada;
	String token;
	Long gestor;
	String limiteOriginal;
	boolean limiteLeido;
	long plantilla;
	int ipSecuencia = 1;

	// ------------------------------------------------------------------ ciclo de vida

	@BeforeAll
	void preparar() {
		borrarRangos();
		temporada = temporadaService.actual();
		limiteOriginal = jdbc.query("SELECT valor FROM configuracion_organizacion WHERE organizacion_id = 1 AND clave = "
				+ "'limite_turno'", rs -> rs.next() ? rs.getString(1) : null);
		limiteLeido = true;
		ReflectionTestUtils.setField((Object) AopTestUtils.getUltimateTargetObject(turnoService), "reloj",
				Clock.fixed(HOY, ValidacionRelojConfig.ZONA));
		plantilla = jdbc.queryForObject("""
				SELECT p.id FROM plantilla_preinscripcion p JOIN disciplina d ON d.id = p.disciplina_id
				WHERE p.organizacion_id = 1 AND d.codigo = 'TAEKWONDO' ORDER BY p.id LIMIT 1""", Long.class);
		token = tokenGestor();
	}

	@BeforeEach
	void limpiar() {
		borrarRangos();
		simbolos.clear();
		ids.clear();
		fijarLimiteTurno(String.valueOf(LIMITE));
		Mockito.clearInvocations(email);
	}

	@AfterEach
	void retirarSiembra() {
		borrarRangos();
	}

	@AfterAll
	void retirar() {
		try {
			borrarRangos();
			for (String tabla : TABLAS_CON_RANGO) {
				try {
					jdbc.execute("ALTER TABLE " + tabla + " AUTO_INCREMENT = 1");
				} catch (RuntimeException e) {
					System.err.println("Aforo R2-P3: no se pudo restaurar AUTO_INCREMENT de " + tabla + ": " + e);
				}
			}
		} finally {
			try {
				if (limiteLeido) {
					fijarLimiteTurno(limiteOriginal);
				}
			} finally {
				try {
					ReflectionTestUtils.setField((Object) AopTestUtils.getUltimateTargetObject(turnoService), "reloj",
							Clock.system(ValidacionRelojConfig.ZONA));
				} finally {
					if (gestor != null) {
						jdbc.update("UPDATE membresia SET activa = FALSE WHERE usuario_id = ?", gestor);
					}
					System.out.println("Aforo R2-P3 · sentencias (Δ = sobre la petición antes de sembrar):");
					medidas.forEach((k, v) -> System.out.println("  " + k + " = " + v));
				}
			}
		}
	}

	// ------------------------------------------------------------------ (a) JSON dorado

	record Dorado(String nombre, String ruta, boolean anonimo, String tipo, Set<String> ordenados) {
	}

	static List<Dorado> dorados() {
		return List.of(
				// Orden garantizado: comparadorGrupos (edad mínima, máxima, primer día, primera hora, id) y comparadorTurnos.
				new Dorado("public_grupos_tkd", "/api/preinscripciones/public/grupos/TAEKWONDO", true, "grupo:",
						Set.of("$", "$[*].turnos")),
				new Dorado("public_grupos_kb", "/api/preinscripciones/public/grupos/KICKBOXING", true, "grupo:",
						Set.of("$", "$[*].turnos")),
				// findAllWithAlumnos sin ORDER BY: multiconjunto.
				new Dorado("public_turnos_tkd", "/api/preinscripciones/public/turnos/TAEKWONDO", false, "turno:", Set.of()),
				new Dorado("public_turnos_kb", "/api/preinscripciones/public/turnos/KICKBOXING", false, "turno:", Set.of()),
				new Dorado("turnos_dto", "/api/turnos/dto", false, "turno:", Set.of()),
				new Dorado("turnos_dto_anonimo", "/api/turnos/dto", true, "turno:", Set.of()));
	}

	@Test
	void jsonDeAforoIgualAlDorado() throws Exception {
		Map<String, Integer> antes = new HashMap<>();
		for (Dorado d : dorados()) {
			peticion(d.ruta(), d.anonimo());
			antes.put(d.nombre(), ContadorSentencias.contar(() -> peticion(d.ruta(), d.anonimo())).total());
		}
		sembrar();
		List<org.junit.jupiter.api.function.Executable> comprobaciones = new ArrayList<>();
		for (Dorado d : dorados()) {
			peticion(d.ruta(), d.anonimo());
			ContadorSentencias.Conteo<String> conteo = ContadorSentencias.contar(() -> peticion(d.ruta(), d.anonimo()));
			int delta = conteo.total() - antes.get(d.nombre());
			medidas.put(d.nombre(), "Δ" + delta + " (total " + conteo.total() + ")");
			JsonNode real = normalizar(filtrar(json.readTree(conteo.resultado()), d.tipo()), "$", d.ordenados());
			comprobaciones.add(() -> compararConDorado(d.nombre() + ".json", real));
			Integer limite = LIMITES.get(d.nombre());
			if (limite != null) {
				comprobaciones.add(() -> assertTrue(delta <= limite, d.nombre() + ": Δ " + delta
						+ " sentencias SQL > límite " + limite));
			}
		}
		assertAll(comprobaciones);
	}

	// ------------------------------------------------------------------ (b) promoción

	@Test
	void cancelarPendienteLiberaPlazaYPromocionaLaPrimeraDeLaColaQueCabe() throws Exception {
		sembrar();
		MvcResult r = escritura("cancelar", () -> cancelar("P1"));
		assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());

		assertEquals(estadosConCambios(Map.of("P1", "CANCELADA"), "ET1A"), estados());
		assertEquals(List.of(plaza("ET1A"), avisoAdmin("ET1A"), cancelacion("P1")), correos());
		assertEquals(Map.of("EKB", 1L, "ET1B", 1L, "EM", 1L, "ELEG", 1L, "ET3", 2L), posiciones());
		assertEquals(Map.of("T1", 3, "T2", 3, "T3", 3, "T4", 3, "T7", 1), ocupaciones());
	}

	@Test
	void cambiarTurnosDeUnaPendienteLiberaElAnteriorYPromocionaPorAlgunaPlaza() throws Exception {
		sembrar();
		MvcResult r = escritura("cambiar_turnos", () -> mvc.perform(put("/api/preinscripciones/" + ref("P3") + "/turnos")
				.cookie(new Cookie("jwt", token)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"turnoIds\":[" + ids.get("turno:T7") + "]}")).andReturn());
		assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());

		// EM pide T2 (lleno) y T3 (con la plaza de P3): sube por T3 y pasa a contar también en T2 (4 > límite 3).
		assertEquals(estadosConCambios(Map.of(), "EM"), estados());
		assertEquals(List.of(plaza("EM"), avisoAdmin("EM"), cambioTurnos("P3")), correos());
		assertEquals(Map.of("EKB", 1L, "ET1A", 1L, "ET1B", 2L, "ELEG", 1L, "ET3", 1L), posiciones());
		assertEquals(Map.of("T1", 3, "T2", 4, "T3", 3, "T4", 3, "T7", 2), ocupaciones());
	}

	@Test
	void bajaDeAlumnoLiberaSusTurnosYPromocionaEnOrdenFifo() throws Exception {
		sembrar();
		MvcResult r = escritura("baja_alumno", () -> mvc.perform(put("/api/alumnos/" + ids.get("alumno:A1") + "/baja")
				.cookie(new Cookie("jwt", token))).andReturn());
		assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());

		assertEquals(estadosConCambios(Map.of(), "ET1A", "EM"), estados());
		assertEquals(List.of(plaza("ET1A"), avisoAdmin("ET1A"), plaza("EM"), avisoAdmin("EM")), correos());
		assertEquals(Map.of("EKB", 1L, "ET1B", 1L, "ELEG", 1L, "ET3", 1L), posiciones());
		assertEquals(Map.of("T1", 3, "T2", 3, "T3", 4, "T4", 3, "T7", 1), ocupaciones());
	}

	// ------------------------------------------------------------------ (c) límite de la organización

	@Test
	void subirElLimiteACincoPromocionaTodaLaColaYLaAntiguaNoOcupaPlaza() throws Exception {
		sembrar();
		MvcResult r = escritura("limite_5", () -> limite(5));
		assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());

		// ELEG (solo turno_id=T3) sube con T3 en 4 y no cuenta: ET3 aún encuentra T3 en 4 y sube.
		assertEquals(estadosConCambios(Map.of(), "EKB", "ET1A", "ET1B", "EM", "ELEG", "ET3"), estados());
		assertEquals(List.of(plaza("EKB"), avisoAdmin("EKB"), plaza("ET1A"), avisoAdmin("ET1A"), plaza("ET1B"),
				avisoAdmin("ET1B"), plaza("EM"), avisoAdmin("EM"), plaza("ELEG"), avisoAdmin("ELEG"), plaza("ET3"),
				avisoAdmin("ET3")), correos());
		assertEquals(Map.of(), posiciones());
		assertEquals(Map.of("T1", 5, "T2", 4, "T3", 5, "T4", 4, "T7", 1), ocupaciones());
	}

	@Test
	void subirElLimiteACuatroRespetaElLimiteEnLosTurnosQueDanLaPlaza() throws Exception {
		sembrar();
		MvcResult r = escritura("limite_4", () -> limite(4));
		assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());

		assertEquals(estadosConCambios(Map.of(), "EKB", "ET1A", "EM"), estados());
		assertEquals(List.of(plaza("EKB"), avisoAdmin("EKB"), plaza("ET1A"), avisoAdmin("ET1A"), plaza("EM"),
				avisoAdmin("EM")), correos());
		assertEquals(Map.of("ET1B", 1L, "ELEG", 1L, "ET3", 1L), posiciones());
		assertEquals(Map.of("T1", 4, "T2", 4, "T3", 4, "T4", 4, "T7", 1), ocupaciones());
	}

	@Test
	void sinCambioDeLimiteNiPlazaNoPromocionaNada() throws Exception {
		sembrar();
		MvcResult r = escritura("limite_3", () -> limite(LIMITE));
		assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());

		assertEquals(estadosConCambios(Map.of()), estados());
		assertEquals(List.of(), correos());
		assertEquals(Map.of("EKB", 1L, "ET1A", 1L, "ET1B", 2L, "EM", 1L, "ELEG", 1L, "ET3", 2L), posiciones());
	}

	@Test
	void turnoSinGrupoEnLaColaHaceFallarLaPromocionSinAvisarANadie() throws Exception {
		sembrar();
		long sinGrupo = turno(6, "T6", null, "Sábado", "10:00", "11:00");
		preinscripcion(40, "EROTO", "EN_LISTA_ESPERA", temporada, "TAEKWONDO", "2026-09-05 10:00:00", null, sinGrupo);

		MvcResult r = cancelar("P1");

		// Hoy: 400 «Alguno de los turnos seleccionados no existe.», todo se deshace (P1 sigue pendiente, ET1A en espera)
		// y, sin commit, no sale ningún correo (R2-P7d: antes ya habían salido los de la promoción de ET1A).
		assertEquals(400, r.getResponse().getStatus());
		assertTrue(r.getResponse().getContentAsString(StandardCharsets.UTF_8).contains("Alguno de los turnos"));
		Map<String, String> esperado = new TreeMap<>(estadosConCambios(Map.of()));
		esperado.put("EROTO", "EN_LISTA_ESPERA");
		assertEquals(esperado, estados());
		assertEquals(List.of(), correos());
	}

	/**
	 * R2-P7d: los avisos de plaza (familia y administración) salen tras el commit de la promoción. Una promoción que se
	 * deshace (aquí, la transacción de quien llama) no avisa a nadie ni deja ninguna solicitud promocionada.
	 */
	@Test
	void unaPromocionDeshechaNoAvisaANadie() throws Exception {
		sembrar();
		fijarLimiteTurno("4");

		TenantContext.ejecutarCon(1L, () -> new TransactionTemplate(txManager).executeWithoutResult(s -> {
			preinscripcionService.promocionarTodas();
			s.setRollbackOnly();
		}));

		assertEquals(estadosConCambios(Map.of()), estados());
		assertEquals(List.of(), correos());
	}

	// ------------------------------------------------------------------ concurrencia

	/**
	 * Dos cancelaciones a la vez de las dos pendientes de T1 (P2 también ocupa T2): en cualquier orden suben ET1A, ET1B y
	 * EM, cada una UNA vez (un aviso de plaza por solicitud), y ningún turno pasa del límite por encima de lo que la
	 * regla «alguna plaza» ya permite en serie. Se repite para cruzar los dos órdenes.
	 */
	@Test
	void dosCancelacionesSimultaneasNoPromocionanDosVecesNiSuperanElAforo() throws Exception {
		for (int ronda = 0; ronda < 6; ronda++) {
			limpiar();
			sembrar();
			List<Integer> estados = enParalelo(() -> cancelar("P1").getResponse().getStatus(),
					() -> cancelar("P2").getResponse().getStatus());
			String contexto = "ronda " + ronda;
			assertEquals(List.of(200, 200), estados, contexto);
			assertEquals(estadosConCambios(Map.of("P1", "CANCELADA", "P2", "CANCELADA"), "ET1A", "ET1B", "EM"),
					estados(), contexto);
			List<String> avisos = correos().stream().filter(c -> c.contains("Ya tienes plaza")).sorted().toList();
			assertEquals(List.of(plaza("EM"), plaza("ET1A"), plaza("ET1B")), avisos, contexto);
			assertEquals(Map.of("T1", 3, "T2", 3, "T3", 4, "T4", 3, "T7", 1), ocupaciones(), contexto);
		}
	}

	/**
	 * Cancelación de P2 (libera T1 y T2) y subida del límite a 4 (promoción de toda la cola) a la vez. En serie, en
	 * cualquier orden, suben EKB, ET1A, ET1B y EM, una vez cada una, y queda T1 = 4. Tras la primera promoción sigue
	 * habiendo plaza: una promoción que leyera la cola ANTES de bloquear sus turnos (como en la ronda 1 de R2-P3) volvería
	 * a subir ET1A (obsoleta, aún «en espera» en su contexto) con un segundo aviso y dejaría fuera a ET1B. Para cruzarlas,
	 * el test retiene los turnos de la cola (T1-T4) hasta que las dos esperan: la cancelación, que llega antes, promociona
	 * mientras la otra espera. Antes de R2-P7d lo forzaba un aviso de plaza lento (150 ms) dentro de la transacción; desde
	 * entonces los avisos salen tras el commit y ya no retienen los turnos.
	 */
	@Test
	void cancelacionYPromocionDeTodaLaColaSimultaneasPromocionanUnaVez() throws Exception {
		for (int ronda = 0; ronda < 6; ronda++) {
			limpiar();
			sembrar();
			List<Integer> estados = enParaleloConTurnosRetenidos(
					List.of(ids.get("turno:T1"), ids.get("turno:T2"), ids.get("turno:T3"), ids.get("turno:T4")),
					() -> cancelar("P2").getResponse().getStatus(), () -> limite(4).getResponse().getStatus());
			String contexto = "ronda " + ronda;
			assertEquals(List.of(200, 200), estados, contexto);
			assertEquals(estadosConCambios(Map.of("P2", "CANCELADA"), "EKB", "ET1A", "ET1B", "EM"), estados(), contexto);
			List<String> avisos = correos().stream().filter(c -> c.contains("Ya tienes plaza")).sorted().toList();
			assertEquals(List.of(plaza("EKB"), plaza("EM"), plaza("ET1A"), plaza("ET1B")), avisos, contexto);
			assertEquals(Map.of("T1", 4, "T2", 3, "T3", 4, "T4", 4, "T7", 1), ocupaciones(), contexto);
		}
	}

	/**
	 * Finalizar P3 (vinculándola a la ficha A9) y cambiar sus turnos a T7 a la vez: las dos bloquean turnos y después la
	 * solicitud, así que se serializan. En serie: o finaliza primero (y el cambio responde 409) o cambia primero (T3 queda
	 * con plaza, sube EM, y la finalización asigna A9 a T7). Nunca 500. Para cruzarlas de verdad, el test retiene T3 y T7
	 * (FOR UPDATE desde otra conexión) hasta que las dos peticiones esperan: con el orden antiguo de finalizar (solicitud y
	 * luego turnos) finalizar esperaba con la solicitud bloqueada y el cambio de turnos (que arranca antes y se pone primero
	 * en la cola de T3), con los turnos, la pedía: interbloqueo y 500.
	 */
	@Test
	void finalizarYCambiarTurnosDeLaMismaSolicitudSimultaneosSeSerializan() throws Exception {
		for (int ronda = 0; ronda < 6; ronda++) {
			limpiar();
			sembrar();
			alumno(9, true, "1990-05-05");
			jdbc.update("UPDATE alumno SET nombre = 'Solicitud P3', apellidos = 'Sintetica Aforo', email = ? WHERE id = ?",
					correoDe("P3"), ALUMNO + 9);
			ficha(9, disciplina("TAEKWONDO"), true);
			long a9 = ALUMNO + 9;
			long t3 = ids.get("turno:T3");
			long t7 = ids.get("turno:T7");
			String cuerpo = "{\"accionAlumno\":\"VINCULAR_EXISTENTE\",\"alumnoId\":" + a9 + ",\"camposActualizar\":[],"
					+ "\"discapacidadHistorica\":false,\"decisionEmail\":\"CONSERVAR_FICHA\",\"datosDeporte\":{"
					+ "\"tipoTarifa\":\"ADULTO\",\"cuantiaTarifa\":30.0,\"rolFamiliar\":\"NINGUNO\",\"grupoFamiliar\":\"\"}}";
			List<Integer> estados = enParaleloConTurnosRetenidos(List.of(t3, t7),
					() -> mvc.perform(put("/api/preinscripciones/" + ref("P3") + "/turnos").cookie(new Cookie("jwt", token))
							.contentType(MediaType.APPLICATION_JSON).content("{\"turnoIds\":[" + t7 + "]}")).andReturn()
							.getResponse().getStatus(),
					() -> mvc.perform(post("/api/preinscripciones/" + ref("P3") + "/finalizar")
					.cookie(new Cookie("jwt", token)).contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andReturn()
					.getResponse().getStatus());
			String contexto = "ronda " + ronda;
			List<Long> turnosA9 = jdbc.queryForList("SELECT turno_id FROM alumno_turno WHERE alumno_id = ?", Long.class, a9);
			Map<String, String> real = estados();
			if (estados.equals(List.of(200, 409))) { // finaliza primero
				assertEquals(estadosConCambios(Map.of("P3", "FINALIZADA")), real, contexto);
				assertEquals(List.of(t3), turnosA9, contexto);
			} else { // cambia primero
				assertEquals(List.of(200, 200), estados, contexto);
				assertEquals(estadosConCambios(Map.of("P3", "FINALIZADA"), "EM"), real, contexto);
				assertEquals(List.of(t7), turnosA9, contexto);
			}
		}
	}

	/**
	 * Dos cancelaciones a la vez de la MISMA solicitud: una cancela (y promociona ET1A), la otra ve la solicitud ya
	 * cancelada y responde 409, sin segundo aviso de cancelación ni segunda promoción.
	 */
	@Test
	void dosCancelacionesSimultaneasDeLaMismaSolicitudAvisanUnaVez() throws Exception {
		for (int ronda = 0; ronda < 6; ronda++) {
			limpiar();
			sembrar();
			List<Integer> estados = enParalelo(() -> cancelar("P1").getResponse().getStatus(),
					() -> cancelar("P1").getResponse().getStatus());
			String contexto = "ronda " + ronda;
			assertEquals(List.of(200, 409), estados, contexto);
			assertEquals(estadosConCambios(Map.of("P1", "CANCELADA"), "ET1A"), estados(), contexto);
			assertEquals(List.of(plaza("ET1A"), avisoAdmin("ET1A"), cancelacion("P1")), correos(), contexto);
		}
	}

	/**
	 * Cambio de turnos de ET1A (en espera, a T7) y subida del límite a 4 (que la promociona) a la vez. En serie: o se
	 * cambia primero (ET1A pasa a pendiente en T7 sin promoción y el límite sube a EKB, ET1B y EM) o se promociona
	 * primero (EKB, ET1A, EM) y el cambio de ET1A libera T1 para ET1B. Sin interbloqueo ni 500, un aviso por solicitud y
	 * la misma ocupación final en los dos órdenes.
	 */
	@Test
	void cambioDeTurnosYPromocionQueLaSubeSimultaneosSinInterbloqueo() throws Exception {
		for (int ronda = 0; ronda < 6; ronda++) {
			limpiar();
			sembrar();
			long t7 = ids.get("turno:T7");
			List<Integer> estados = enParalelo(() -> mvc.perform(put("/api/preinscripciones/" + ref("ET1A") + "/turnos")
					.cookie(new Cookie("jwt", token)).contentType(MediaType.APPLICATION_JSON)
					.content("{\"turnoIds\":[" + t7 + "]}")).andReturn().getResponse().getStatus(),
					() -> limite(4).getResponse().getStatus());
			String contexto = "ronda " + ronda;
			assertEquals(List.of(200, 200), estados, contexto);
			Map<String, String> real = estados();
			Map<String, String> cambioPrimero = estadosConCambios(Map.of("ET1A", "PENDIENTE"), "EKB", "ET1B", "EM");
			Map<String, String> promocionPrimero = estadosConCambios(Map.of(), "EKB", "ET1A", "ET1B", "EM");
			assertTrue(real.equals(cambioPrimero) || real.equals(promocionPrimero), contexto + ": " + real);
			List<String> avisos = correos().stream().filter(c -> c.contains("Ya tienes plaza")).sorted().toList();
			List<String> esperados = real.equals(cambioPrimero) ? List.of(plaza("EKB"), plaza("EM"), plaza("ET1B"))
					: List.of(plaza("EKB"), plaza("EM"), plaza("ET1A"), plaza("ET1B"));
			assertEquals(esperados, avisos, contexto);
			assertEquals(1, correos().stream().filter(c -> c.equals(cambioTurnos("ET1A"))).count(), contexto);
			assertEquals(Map.of("T1", 4, "T2", 4, "T3", 4, "T4", 4, "T7", 2), ocupaciones(), contexto);
		}
	}

	/**
	 * Los FOR UPDATE de cancelar (con cola), del alta pública y de borrar un turno bloquean solo filas de {@code turno} y
	 * de {@code preinscripcion}, sin JOIN: antes, el {@code JOIN FETCH t.grupo} bloqueaba también filas de {@code grupo}.
	 */
	@Test
	void losBloqueosSonSoloDeTurnoYDeLaSolicitud() throws Exception {
		sembrar();
		List<String> cancelacion = ContadorSentencias.contar(() -> cancelar("P1")).sentencias();
		List<String> alta = ContadorSentencias.contar(() -> alta("71234567X", ids.get("turno:T3"), firma())).sentencias();
		ContadorSentencias.Conteo<MvcResult> borrado = ContadorSentencias.contar(() -> mvc.perform(
				org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/turnos/" + ids.get("turno:T5"))
						.cookie(new Cookie("jwt", token))).andReturn());
		assertEquals(204, borrado.resultado().getResponse().getStatus(), borrado.resultado().getResponse().getContentAsString());
		for (List<String> sentencias : List.of(cancelacion, alta, borrado.sentencias())) {
			List<String> bloqueos = sentencias.stream().filter(s -> s.toLowerCase().contains("for update")).toList();
			assertTrue(bloqueos.stream().anyMatch(s -> s.toLowerCase().contains("from turno")), String.valueOf(bloqueos));
			assertTrue(bloqueos.stream().noneMatch(s -> s.toLowerCase().contains(" join ")), String.valueOf(bloqueos));
		}
	}

	/**
	 * Cancelación de una pendiente de T3 y alta pública en T3 a la vez: la plaza liberada va a la cola (EM) o al alta,
	 * nunca a las dos; T3 no pasa del límite.
	 */
	@Test
	void cancelacionYAltaPublicaSimultaneasNoSuperanElAforo() throws Exception {
		String firma = firma();
		for (int ronda = 0; ronda < 4; ronda++) {
			limpiar();
			sembrar();
			String dni = String.format("%08d", 71_000_000 + ronda) + "X";
			List<Integer> estados = enParalelo(() -> cancelar("P3").getResponse().getStatus(),
					() -> alta(dni, ids.get("turno:T3"), firma).getResponse().getStatus());
			String contexto = "ronda " + ronda;
			assertEquals(List.of(200, 201), estados, contexto);
			int t3 = ocupacion(ids.get("turno:T3"));
			assertTrue(t3 <= LIMITE, contexto + ": T3 con " + t3 + " > límite " + LIMITE);
			String estadoAlta = jdbc.queryForObject("SELECT estado FROM preinscripcion WHERE dni = ?", String.class, dni);
			String estadoEm = estados().get("EM");
			assertTrue(!(estadoAlta.equals("PENDIENTE") && estadoEm.equals("PENDIENTE")),
					contexto + ": el alta (" + estadoAlta + ") y EM (" + estadoEm + ") se llevan la misma plaza");
			jdbc.update("DELETE FROM preinscripcion_turno WHERE preinscripcion_id IN (SELECT id FROM preinscripcion WHERE dni = ?)", dni);
			jdbc.update("DELETE FROM preinscripcion WHERE dni = ?", dni);
		}
	}

	// ------------------------------------------------------------------ siembra

	private void sembrar() {
		long sede = jdbc.queryForObject("SELECT MIN(id) FROM sede WHERE organizacion_id = 1", Long.class);
		long tkd = disciplina("TAEKWONDO");
		long kb = disciplina("KICKBOXING");

		long infantil = grupo(1, "TKD-INFANTIL", "Infantil", tkd, sede, 6, 12);
		long adultos = grupo(2, "TKD-ADULTOS", "Adultos", tkd, sede, null, null);
		grupo(3, "TKD-COMPETICION", "Competicion", tkd, sede, null, null); // oculto en el formulario público
		long kickboxing = grupo(4, "KB", "Kickboxing", kb, null, null, null);
		grupo(5, "TKD-SIN-TURNOS", "Sin turnos", tkd, sede, null, null);
		long t1 = turno(1, "T1", infantil, "Lunes", "17:00", "18:00");
		long t2 = turno(2, "T2", infantil, "Miércoles", "17:00", "18:00");
		long t3 = turno(3, "T3", adultos, "Martes", "19:00", "20:00");
		long t4 = turno(4, "T4", kickboxing, "Jueves", "18:00", "19:00");
		turno(5, "T5", ids.get("grupo:TKD-COMPETICION"), "Viernes", "19:00", "20:00");
		long t7 = turno(7, "T7", infantil, "Lunes", "18:00", "19:00");

		// A1 cuenta en T1 y T2; A2 (taekwondo de baja), A3 (ficha inactiva) y A4 (solo kickboxing) no cuentan en T1.
		alumno(1, true, "2015-03-04");
		alumno(2, true, "2014-05-06");
		alumno(3, false, "2016-07-08");
		alumno(4, true, "1990-01-02");
		alumno(5, true, "2013-09-10");
		alumno(6, true, "1985-11-12");
		alumno(8, true, "2017-02-03");
		ficha(1, tkd, true);
		ficha(2, tkd, false);
		ficha(3, tkd, true);
		ficha(4, kb, true);
		ficha(5, tkd, true);
		ficha(6, tkd, true);
		ficha(8, tkd, true);
		apuntar(1, t1);
		apuntar(1, t2);
		apuntar(2, t1);
		apuntar(3, t1);
		apuntar(4, t1);
		apuntar(4, t4);
		apuntar(5, t2);
		apuntar(6, t3);
		apuntar(8, t7);

		preinscripcion(1, "P1", "PENDIENTE", temporada, "TAEKWONDO", "2026-09-01 10:00:00", null, t1);
		preinscripcion(2, "P2", "PENDIENTE", temporada, "TAEKWONDO", "2026-09-01 11:00:00", null, t1, t2);
		preinscripcion(3, "P3", "PENDIENTE", temporada, "TAEKWONDO", "2026-09-01 12:00:00", null, t3);
		preinscripcion(4, "P4", "PENDIENTE", temporada, "TAEKWONDO", "2026-09-01 13:00:00", null, t3);
		preinscripcion(5, "P5", "PENDIENTE", temporada, "KICKBOXING", "2026-09-01 14:00:00", null, t4);
		preinscripcion(6, "P6", "PENDIENTE", temporada, "KICKBOXING", "2026-09-01 15:00:00", null, t4);
		preinscripcion(7, "POTRA", "PENDIENTE", OTRA_TEMPORADA, "TAEKWONDO", "2026-09-01 16:00:00", null, t1);
		preinscripcion(8, "PFIN", "FINALIZADA", temporada, "TAEKWONDO", "2026-09-01 17:00:00", null, t1);

		preinscripcion(39, "EKB", "EN_LISTA_ESPERA", temporada, "KICKBOXING", "2026-09-02 09:00:00", null, t4);
		preinscripcion(31, "ET1A", "EN_LISTA_ESPERA", temporada, "TAEKWONDO", "2026-09-02 10:00:00", null, t1);
		preinscripcion(32, "ET1B", "EN_LISTA_ESPERA", temporada, "TAEKWONDO", "2026-09-02 10:00:00", null, t1);
		preinscripcion(33, "EM", "EN_LISTA_ESPERA", temporada, "TAEKWONDO", "2026-09-02 10:30:00", null, t2, t3);
		preinscripcion(34, "ELEG", "EN_LISTA_ESPERA", temporada, "TAEKWONDO", "2026-09-02 11:00:00", t3);
		preinscripcion(35, "ET3", "EN_LISTA_ESPERA", temporada, "TAEKWONDO", "2026-09-03 10:00:00", null, t3);
		preinscripcion(36, "EOTRA", "EN_LISTA_ESPERA", OTRA_TEMPORADA, "TAEKWONDO", "2026-09-01 08:00:00", null, t1);
		preinscripcion(37, "ECAN", "CANCELADA", temporada, "TAEKWONDO", "2026-09-01 08:30:00", null, t1);
	}

	/** Estados de partida de todas las preinscripciones sembradas. */
	private static final Map<String, String> ESTADOS_INICIALES = Map.ofEntries(Map.entry("P1", "PENDIENTE"),
			Map.entry("P2", "PENDIENTE"), Map.entry("P3", "PENDIENTE"), Map.entry("P4", "PENDIENTE"),
			Map.entry("P5", "PENDIENTE"), Map.entry("P6", "PENDIENTE"), Map.entry("POTRA", "PENDIENTE"),
			Map.entry("PFIN", "FINALIZADA"), Map.entry("EKB", "EN_LISTA_ESPERA"), Map.entry("ET1A", "EN_LISTA_ESPERA"),
			Map.entry("ET1B", "EN_LISTA_ESPERA"), Map.entry("EM", "EN_LISTA_ESPERA"), Map.entry("ELEG", "EN_LISTA_ESPERA"),
			Map.entry("ET3", "EN_LISTA_ESPERA"), Map.entry("EOTRA", "EN_LISTA_ESPERA"), Map.entry("ECAN", "CANCELADA"));

	/** Estados esperados: los de partida con los cambios dados y las promocionadas en PENDIENTE (con promocionada_en). */
	private Map<String, String> estadosConCambios(Map<String, String> cambios, String... promocionadas) {
		Map<String, String> m = new TreeMap<>(ESTADOS_INICIALES);
		m.putAll(cambios);
		for (String p : promocionadas) {
			m.put(p, "PENDIENTE+promocionada");
		}
		return m;
	}

	private Map<String, String> estados() {
		Map<String, String> m = new TreeMap<>();
		jdbc.query("SELECT referencia, estado, promocionada_en FROM preinscripcion WHERE id BETWEEN ? AND ?", rs -> {
			m.put(rs.getString(1).substring(PREFIJO.length() + 1),
					rs.getString(2) + (rs.getTimestamp(3) != null ? "+promocionada" : ""));
		}, PREINSCRIPCION, PREINSCRIPCION + ANCHO);
		return m;
	}

	/** Posición en lista de espera de cada solicitud sembrada que sigue en la cola de la temporada actual. */
	private Map<String, Long> posiciones() {
		return TenantContext.calcularCon(1L, () -> new TransactionTemplate(txManager).execute(s -> {
			Map<String, Long> m = new TreeMap<>();
			preinscripciones.findAll().stream()
					.filter(p -> p.getId() >= PREINSCRIPCION && p.getId() <= PREINSCRIPCION + ANCHO)
					.filter(p -> temporada.equals(p.getTemporada()))
					.filter(p -> p.getEstado() == com.taemoi.project.entities.EstadoPreinscripcion.EN_LISTA_ESPERA)
					.forEach(p -> m.put(p.getReferencia().substring(PREFIJO.length() + 1), aforo.posicion(p)));
			return m;
		}));
	}

	/** Ocupación efectiva medida por SQL (alumnos que cuentan + pendientes de la temporada en preinscripcion_turno). */
	private Map<String, Integer> ocupaciones() {
		Map<String, Integer> m = new TreeMap<>();
		for (String t : List.of("T1", "T2", "T3", "T4", "T7")) {
			m.put(t, ocupacion(ids.get("turno:" + t)));
		}
		return m;
	}

	private int ocupacion(long turno) {
		return jdbc.queryForObject("""
				SELECT (SELECT COUNT(DISTINCT a.id) FROM alumno_turno at JOIN alumno a ON a.id = at.alumno_id
				          JOIN turno t ON t.id = at.turno_id JOIN grupo g ON g.id = t.grupo_id
				        WHERE at.turno_id = ? AND a.activo = 1 AND EXISTS (SELECT 1 FROM alumno_deporte ad
				          WHERE ad.alumno_id = a.id AND ad.activo = 1 AND ad.disciplina_id = g.disciplina_id))
				     + (SELECT COUNT(DISTINCT p.id) FROM preinscripcion_turno pt JOIN preinscripcion p ON p.id = pt.preinscripcion_id
				        WHERE pt.turno_id = ? AND p.estado = 'PENDIENTE' AND p.temporada = ?)""", Integer.class,
				turno, turno, temporada);
	}

	/** Correos de la siembra en el orden en que se pidieron: «método · destinatario · asunto». */
	private List<String> correos() {
		List<String> lista = new ArrayList<>();
		for (Invocation i : Mockito.mockingDetails(email).getInvocations()) {
			String metodo = i.getMethod().getName();
			if (!metodo.startsWith("send")) {
				continue;
			}
			Object para0 = i.getArgument(0);
			String para = String.valueOf(para0);
			Object asunto0 = i.getArgument(1);
			String asunto = String.valueOf(asunto0);
			if (para.endsWith(DOMINIO) || asunto.contains(PREFIJO)) {
				lista.add(metodo + " · " + para + " · " + asunto);
			}
		}
		return lista;
	}

	private String plaza(String r) {
		return "sendEmail · " + correoDe(r) + " · Ya tienes plaza · " + ref(r);
	}

	private String avisoAdmin(String r) {
		return "sendEmail · " + ADMIN + " · Lista de espera promocionada: " + ref(r);
	}

	private String cancelacion(String r) {
		return "sendEmailSync · " + correoDe(r) + " · Tu preinscripción " + ref(r) + " ha sido cancelada";
	}

	private String cambioTurnos(String r) {
		return "sendEmailSync · " + correoDe(r) + " · Horarios actualizados · " + ref(r);
	}

	private static String ref(String r) {
		return PREFIJO + "-" + r;
	}

	private static String correoDe(String r) {
		return r.toLowerCase() + DOMINIO;
	}

	private MvcResult cancelar(String r) throws Exception {
		return mvc.perform(post("/api/preinscripciones/" + ref(r) + "/cancelar").cookie(new Cookie("jwt", token))
				.contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"\"}")).andReturn();
	}

	private MvcResult limite(int valor) throws Exception {
		return mvc.perform(put("/api/admin/configuracion/limite-turno").cookie(new Cookie("jwt", token))
				.contentType(MediaType.APPLICATION_JSON).content(String.valueOf(valor))).andReturn();
	}

	private MvcResult alta(String dni, long turno, String firma) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("deporte", "TAEKWONDO");
		body.put("turnoIds", List.of(turno));
		body.put("nombre", "Alta");
		body.put("apellidos", "Concurrente Aforo");
		body.put("dni", dni);
		body.put("fechaNacimiento", "1990-01-01");
		body.put("direccion", "Calle Sintética 3");
		body.put("telefono", "612345678");
		body.put("telefono2", "");
		body.put("email", "alta" + DOMINIO);
		body.put("observaciones", "");
		body.put("tutorNombre", "");
		body.put("tutorDni", "");
		body.put("tieneDiscapacidad", false);
		body.put("consentimientoFotografico", false);
		body.put("aceptacionNormas", true);
		body.put("firmanteNombre", "Firmante Aforo");
		body.put("firmaBase64", firma);
		return mvc.perform(post("/api/preinscripciones").contentType(MediaType.APPLICATION_JSON)
				.header("X-Forwarded-For", "10.83.0." + (ipSecuencia++ % 250 + 1)).content(json.writeValueAsString(body)))
				.andReturn();
	}

	private static String firma() throws IOException {
		BufferedImage imagen = new BufferedImage(320, 100, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = imagen.createGraphics();
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, imagen.getWidth(), imagen.getHeight());
		g.setColor(Color.BLACK);
		g.drawString("Firma de prueba de aforo", 20, 50);
		g.dispose();
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		ImageIO.write(imagen, "png", bytes);
		return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes.toByteArray());
	}

	private MvcResult escritura(String nombre, Callable<MvcResult> accion) throws Exception {
		ContadorSentencias.Conteo<MvcResult> conteo = ContadorSentencias.contar(accion);
		medidas.put(nombre, String.valueOf(conteo.total()));
		Integer limite = LIMITES.get(nombre);
		if (limite != null) {
			assertTrue(conteo.total() <= limite, nombre + ": " + conteo.total() + " sentencias SQL > límite " + limite);
		}
		return conteo.resultado();
	}

	/**
	 * Como {@link #enParalelo}, pero con los turnos dados bloqueados (FOR UPDATE) en otra transacción hasta que las dos
	 * peticiones han tenido tiempo de llegar a esperarlos; la primera arranca 250 ms antes.
	 */
	private List<Integer> enParaleloConTurnosRetenidos(List<Long> turnos, Callable<Integer> primera,
			Callable<Integer> segunda) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			List<Future<Integer>> futuros = new ArrayList<>();
			TenantContext.ejecutarCon(1L, () -> new TransactionTemplate(txManager).executeWithoutResult(s -> {
				jdbc.queryForList("SELECT id FROM turno WHERE id IN (" + String.join(",", turnos.stream().map(String::valueOf)
						.toList()) + ") ORDER BY id FOR UPDATE");
				try {
					futuros.add(executor.submit(primera));
					Thread.sleep(250); // la primera llega antes a la cola de espera del bloqueo
					futuros.add(executor.submit(segunda));
					Thread.sleep(400);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}));
			List<Integer> estados = new ArrayList<>();
			for (Future<Integer> futuro : futuros) {
				estados.add(futuro.get(60, TimeUnit.SECONDS));
			}
			estados.sort(Integer::compareTo);
			return estados;
		} finally {
			executor.shutdownNow();
		}
	}

	private List<Integer> enParalelo(Callable<Integer> primera, Callable<Integer> segunda) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch preparados = new CountDownLatch(2);
		CountDownLatch salida = new CountDownLatch(1);
		try {
			List<Future<Integer>> futuros = new ArrayList<>();
			for (Callable<Integer> accion : List.of(primera, segunda)) {
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
			List<Integer> estados = new ArrayList<>();
			for (Future<Integer> futuro : futuros) {
				estados.add(futuro.get(60, TimeUnit.SECONDS));
			}
			estados.sort(Integer::compareTo);
			return estados;
		} finally {
			salida.countDown();
			executor.shutdownNow();
		}
	}

	// ------------------------------------------------------------------ utilidades de siembra

	private long disciplina(String codigo) {
		return jdbc.queryForObject("SELECT id FROM disciplina WHERE organizacion_id = 1 AND codigo = ?", Long.class,
				codigo);
	}

	private void registrar(long id, String simbolo) {
		simbolos.put(id, simbolo);
		ids.put(simbolo, id);
	}

	private long grupo(int n, String clave, String nombre, long disciplina, Long sede, Integer min, Integer max) {
		long id = GRUPO + n;
		jdbc.update("""
				INSERT INTO grupo (id, nombre, tipo, rango_edad_min, rango_edad_max, organizacion_id, disciplina_id, sede_id)
				VALUES (?, ?, NULL, ?, ?, 1, ?, ?)""", id, PREFIJO + " " + nombre, min, max, disciplina, sede);
		registrar(id, "grupo:" + clave);
		return id;
	}

	private long turno(int n, String clave, Long grupo, String dia, String inicio, String fin) {
		long id = TURNO + n;
		jdbc.update("""
				INSERT INTO turno (id, dia_semana, hora_inicio, hora_fin, tipo, grupo_id, organizacion_id)
				VALUES (?, ?, ?, ?, NULL, ?, 1)""", id, dia, inicio, fin, grupo);
		registrar(id, "turno:" + clave);
		return id;
	}

	private void alumno(int n, boolean activo, String nacimiento) {
		long id = ALUMNO + n;
		jdbc.update("""
				INSERT INTO alumno (id, activo, nombre, apellidos, numero_expediente, fecha_nacimiento, nif, direccion,
				  telefono, telefono2, genero, localidad, codigo_postal, email, fecha_alta, fecha_alta_inicial,
				  fecha_baja, autorizacion_web, observaciones, tiene_discapacidad, tiene_derecho_examen,
				  organizacion_id, sede_principal_id, foto_alumno_id)
				VALUES (?, ?, ?, 'Sintetico Aforo', ?, ?, NULL, 'Calle Aforo', '600000000', NULL, 'FEMENINO', 'Sevilla',
				  '41001', ?, '2020-09-01', '2019-09-01', ?, 1, NULL, 0, 0, 1, NULL, NULL)""",
				id, activo, PREFIJO + " A" + n, EXPEDIENTE + n, Date.valueOf(nacimiento), "a" + n + DOMINIO,
				activo ? null : Date.valueOf("2025-06-30"));
		registrar(id, "alumno:A" + n);
	}

	private int fichas;

	private void ficha(int alumno, long disciplina, boolean activo) {
		long id = ALUMNO_DEPORTE + (++fichas);
		jdbc.update("""
				INSERT INTO alumno_deporte (id, activo, apto_para_examen, alumno_id, competidor, principal, tiene_licencia,
				  organizacion_id, fecha_alta, fecha_alta_inicial, fecha_grado, fecha_baja, disciplina_id, grado_id)
				VALUES (?, ?, 0, ?, 0, 1, 0, 1, '2020-09-01', '2019-09-01', NULL, ?, ?, NULL)""",
				id, activo, ALUMNO + alumno, activo ? null : Date.valueOf("2025-06-30"), disciplina);
		registrar(id, "alumnoDeporte:A" + alumno);
	}

	private void apuntar(int alumno, long turno) {
		jdbc.update("INSERT INTO alumno_turno (alumno_id, turno_id) VALUES (?, ?)", ALUMNO + alumno, turno);
	}

	/**
	 * @param turnoLegacy {@code turno_id} de las solicitudes antiguas (sin filas en {@code preinscripcion_turno}).
	 * @param turnos      filas de {@code preinscripcion_turno}.
	 */
	private void preinscripcion(int n, String clave, String estado, String temporadaFila, String deporte,
			String creadaEn, Long turnoLegacy, long... turnos) {
		long id = PREINSCRIPCION + n;
		long grupo = jdbc.queryForObject("SELECT grupo_id FROM turno WHERE id = ?", Long.class,
				turnoLegacy != null ? turnoLegacy : turnos[0]) instanceof Long g ? g : 0L;
		jdbc.update("""
				INSERT INTO preinscripcion (id, referencia, temporada, estado, nombre, apellidos, dni, identidad_hash,
				  fecha_nacimiento, direccion, telefono, email, tiene_discapacidad, consentimiento_fotografico,
				  aceptacion_normas, firmante_nombre, firma, plantilla_id, plantilla_snapshot, pdf_firmado,
				  token_documento_hash, creada_en, actualizada_en, turno_id, grupo_id, organizacion_id, disciplina_id)
				VALUES (?, ?, ?, ?, ?, 'Sintetica Aforo', NULL, ?, '1990-05-05', 'Calle 1', '600000000', ?, 0, 0, 1,
				  'Firmante Aforo', x'00', ?, '{}', x'00', ?, ?, ?, ?, ?, 1, ?)""",
				id, ref(clave), temporadaFila, estado, "Solicitud " + clave, String.format("%064d", id), correoDe(clave),
				plantilla, "f".repeat(64), creadaEn, creadaEn, turnoLegacy, grupo == 0L ? null : grupo,
				disciplina(deporte));
		for (long t : turnos) {
			jdbc.update("INSERT INTO preinscripcion_turno (preinscripcion_id, turno_id) VALUES (?, ?)", id, t);
		}
		registrar(id, "preinscripcion:" + clave);
	}

	private void borrarRangos() {
		// Lo que cuelga de las fichas de la siembra (finalizar crea consentimientos, asignaciones...): todas las tablas que
		// referencian alumno o alumno_deporte, salvo las que se borran abajo.
		for (Map<String, Object> fk : jdbc.queryForList("""
				SELECT TABLE_NAME AS tabla, COLUMN_NAME AS columna, REFERENCED_TABLE_NAME AS referida
				FROM information_schema.KEY_COLUMN_USAGE
				WHERE TABLE_SCHEMA = DATABASE() AND REFERENCED_TABLE_NAME IN ('alumno', 'alumno_deporte')
				  AND TABLE_NAME NOT IN ('alumno_deporte', 'alumno_turno', 'alumno_grupo', 'preinscripcion')""")) {
			String condicion = "alumno".equals(fk.get("referida")) ? fk.get("columna") + " BETWEEN ? AND ?"
					: fk.get("columna") + " IN (SELECT id FROM alumno_deporte WHERE alumno_id BETWEEN ? AND ?)";
			borrar("DELETE FROM " + fk.get("tabla") + " WHERE " + condicion, ALUMNO);
		}
		borrar("DELETE FROM preinscripcion_turno WHERE preinscripcion_id BETWEEN ? AND ?", PREINSCRIPCION);
		borrar("DELETE FROM preinscripcion_turno WHERE turno_id BETWEEN ? AND ?", TURNO);
		borrar("DELETE FROM preinscripcion WHERE id BETWEEN ? AND ?", PREINSCRIPCION);
		borrar("DELETE FROM preinscripcion WHERE turno_id BETWEEN ? AND ?", TURNO);
		borrar("DELETE FROM preinscripcion WHERE grupo_id BETWEEN ? AND ?", GRUPO); // altas públicas del test
		borrar("DELETE FROM alumno_turno WHERE alumno_id BETWEEN ? AND ?", ALUMNO);
		borrar("DELETE FROM alumno_turno WHERE turno_id BETWEEN ? AND ?", TURNO);
		borrar("DELETE FROM alumno_grupo WHERE alumno_id BETWEEN ? AND ?", ALUMNO);
		borrar("DELETE FROM alumno_deporte WHERE id BETWEEN ? AND ?", ALUMNO_DEPORTE);
		borrar("DELETE FROM alumno_deporte WHERE alumno_id BETWEEN ? AND ?", ALUMNO);
		borrar("DELETE FROM alumno WHERE id BETWEEN ? AND ?", ALUMNO);
		borrar("DELETE FROM turno WHERE id BETWEEN ? AND ?", TURNO);
		borrar("DELETE FROM grupo WHERE id BETWEEN ? AND ?", GRUPO);
		fichas = 0;
	}

	private void borrar(String sql, long base) {
		jdbc.update(sql, base, base + ANCHO);
	}

	private void fijarLimiteTurno(String valor) {
		jdbc.update("DELETE FROM configuracion_organizacion WHERE organizacion_id = 1 AND clave = 'limite_turno'");
		if (valor != null) {
			jdbc.update("INSERT INTO configuracion_organizacion (organizacion_id, clave, valor) VALUES (1, "
					+ "'limite_turno', ?)", valor);
		}
	}

	private String tokenGestor() {
		Usuario usuario = TenantContext.calcularCon(1L, () -> {
			Usuario u = new Usuario();
			u.setNombre("Gestor");
			u.setApellidos("Aforo R2P3");
			u.setEmail("aforo-r2p3-gestor-" + System.nanoTime() + "@example.test");
			u.setContrasena("fake-Password-123");
			u.setAuthProvider(AuthProvider.LOCAL);
			return usuarios.save(u);
		});
		gestor = usuario.getId();
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, 1, 'PROPIETARIO')", gestor);
		return jwtService.generateToken(usuario, 3_600_000, 1L);
	}

	// ------------------------------------------------------------------ JSON

	private String peticion(String ruta, boolean anonimo) throws Exception {
		MockHttpServletRequestBuilder req = get(ruta);
		if (!anonimo) {
			req = req.cookie(new Cookie("jwt", token));
		}
		MvcResult r = mvc.perform(req).andReturn();
		assertEquals(200, r.getResponse().getStatus(), ruta + ": " + r.getResponse().getContentAsString());
		return r.getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

	private JsonNode filtrar(JsonNode lista, String tipo) {
		assertTrue(lista.isArray(), "Se esperaba una lista: " + lista);
		ArrayNode filtrada = JsonNodeFactory.instance.arrayNode();
		Predicate<JsonNode> sembrado = n -> n.has("id") && n.get("id").isIntegralNumber()
				&& Optional.ofNullable(simbolos.get(n.get("id").asLong())).map(s -> s.startsWith(tipo)).orElse(false);
		lista.forEach(n -> {
			if (sembrado.test(n)) {
				filtrada.add(n);
			}
		});
		return filtrada;
	}

	private JsonNode normalizar(JsonNode nodo, String ruta, Set<String> ordenados) {
		if (nodo.isObject()) {
			ObjectNode o = JsonNodeFactory.instance.objectNode();
			List<String> campos = new ArrayList<>();
			nodo.fieldNames().forEachRemaining(campos::add);
			Collections.sort(campos);
			for (String campo : campos) {
				o.set(campo, normalizar(nodo.get(campo), ruta + "." + campo, ordenados));
			}
			return o;
		}
		if (nodo.isArray()) {
			List<JsonNode> hijos = new ArrayList<>();
			nodo.forEach(h -> hijos.add(normalizar(h, ruta + "[*]", ordenados)));
			if (!ordenados.contains(ruta)) {
				hijos.sort(Comparator.comparing(n -> n.isObject() && n.has("id") ? n.get("id").asText() + "\u0000" + n
						: n.toString()));
			}
			ArrayNode a = JsonNodeFactory.instance.arrayNode();
			hijos.forEach(a::add);
			return a;
		}
		if (nodo.isIntegralNumber() && simbolos.containsKey(nodo.asLong())) {
			return TextNode.valueOf(simbolos.get(nodo.asLong()));
		}
		return nodo;
	}

	private void compararConDorado(String fichero, JsonNode real) throws IOException {
		if (REGENERAR) {
			Files.createDirectories(FUENTES);
			Files.writeString(FUENTES.resolve(fichero), aTexto(real), StandardCharsets.UTF_8);
			return;
		}
		JsonNode esperado;
		try (InputStream in = getClass().getClassLoader().getResourceAsStream(RECURSOS + fichero)) {
			assertNotNull(in, RECURSOS + fichero + " no existe: genéralo con -Ddorado.aforo.regenerar=true");
			esperado = json.readTree(in);
		}
		Optional<String> diferencia = MoiskimdoAlumnosJsonDoradoIntegrationTest.primeraDiferencia("$", esperado, real);
		if (diferencia.isPresent()) {
			Files.createDirectories(REALES);
			Files.writeString(REALES.resolve(fichero), aTexto(real), StandardCharsets.UTF_8);
			fail(RECURSOS + fichero + " difiere del JSON actual en " + diferencia.get() + "\n(respuesta normalizada en "
					+ REALES.resolve(fichero) + ")");
		}
	}

	private String aTexto(JsonNode nodo) throws IOException {
		DefaultPrettyPrinter impresora = new DefaultPrettyPrinter()
				.withObjectIndenter(new DefaultIndenter("  ", "\n"))
				.withArrayIndenter(new DefaultIndenter("  ", "\n"));
		return json.writer(impresora).writeValueAsString(nodo) + "\n";
	}
}
