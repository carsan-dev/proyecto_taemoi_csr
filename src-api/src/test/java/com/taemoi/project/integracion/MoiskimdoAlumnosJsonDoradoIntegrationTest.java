package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.taemoi.project.config.ValidacionRelojConfig;
import com.taemoi.project.entities.AuthProvider;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.JwtService;
import com.taemoi.project.services.TemporadaService;
import com.taemoi.project.services.TurnoService;

import jakarta.servlet.http.Cookie;

/**
 * Red de seguridad del rendimiento de alumnos (R2, pieza P0): el JSON que devuelven hoy los endpoints de alumnos,
 * grupos y turnos para Moiskimdo (organización 1), congelado en {@code src/test/resources/dorado/r2/*.json}, y un
 * límite de sentencias SQL por endpoint. Las piezas siguientes (ciclo EAGER, N+1, aforo por lotes...) deben dejar el
 * JSON idéntico salvo cambios decididos y solo pueden bajar los límites.
 *
 * <h2>Datos</h2>
 * Sintéticos, fijos y con prefijo propio ({@value #PREFIJO}) en la organización 1, con ids explícitos en rangos
 * reservados (9.100.000 alumnos, 9.200.000 grupos, 9.300.000 turnos...), para que las claves simbólicas no dependan
 * del orden de las clases de la batería. 30 alumnos, 3 grupos, 5 turnos y estos casos borde: alumno en 2 grupos con
 * 2 deportes (A05), alumno inactivo (A10, A29), deporte inactivo (A07 en kickboxing), con cuenta de usuario (A03),
 * con tutor responsable legal (A04) y con tutor que no lo es (A06), con foto (A02), fichas deportivas completas
 * (tarifa, categoría, peso, licencia, familia, competición: A05, A13, A21), documento y cargo con producto (A05),
 * examen en una convocatoria (A08), reto diario y racha (A03, A05), grupos con sede y uno sin sede,
 * grupo con rango de edad y grupos sin él (edades calculadas), turno sin grupo, preinscripción PENDIENTE en un turno
 * y ese turno lleno ({@code completo=true}, con el límite de turno fijado a {@value #LIMITE_TURNO} mientras dura la
 * clase). Al terminar se borra todo y se restauran el límite y los AUTO_INCREMENT.
 *
 * <h2>Filtrado a las filas sembradas</h2>
 * La batería comparte la organización 1 con otras clases (y sus restos dependen del orden de ejecución): los
 * listados de toda la organización se filtran a los ids sembrados conservando su orden; el listado paginado usa
 * {@code nombre=}{@value #PREFIJO}. Los totales globales ({@code /api/alumnos/count}, {@code /conteo-alumnos}) se
 * guardan como incremento respecto a la misma petición antes de sembrar.
 *
 * <h2>Normalización (y por qué)</h2>
 * <ul>
 * <li>Ids sembrados → claves simbólicas ({@code alumno:A05}, {@code grupo:TKD-INFANTIL},
 * {@code turno:TKD-INFANTIL/Lunes/17:00}, {@code alumnoDeporte:A05/KICKBOXING}...), también dentro de textos (la URL
 * de la foto lleva el id del alumno; con {@code app.base.url} fijado en la clase, la URL queda fija). Dentro de un
 * texto solo cuando el texto es más largo que el id: si un id pasara de número a texto, el cambio se vería. El resto de ids
 * (disciplinas, grados, sede) vienen de las migraciones y son fijos.</li>
 * <li>{@code antiguedad} y {@code antiguedadCompetidor} se calculan con la fecha de hoy: el valor se sustituye por un
 * marcador de su tipo ({@code "<texto>"}, {@code "<numero>"}; {@code null} se queda), así el campo sigue en el
 * contrato.</li>
 * <li>Campos {@code fecha*} en milisegundos → {@code "fecha:AAAA-MM-DD"}: el número es la medianoche de la zona de la
 * JVM y cambiaría entre la máquina local (Madrid) y la CI (UTC); la fecha de calendario no. El prefijo deja ver un
 * cambio de formato (p. ej. pasar a texto ISO).</li>
 * <li>{@code edadMinAlumnos}/{@code edadMaxAlumnos} de {@code /api/turnos/dto}: se fija el reloj de
 * {@code TurnoServiceImpl} a {@code 2026-10-01 10:00 Madrid} ({@link #HOY}); con nacimientos fijos la edad no cambia
 * con el día ni con la zona de la máquina (la CI va en UTC).</li>
 * <li>Arrays que salen de un {@code Set} sin orden (hoy, orden de HashSet: cambia entre ejecuciones): se comparan
 * ordenados por su {@code id} simbólico. Son los marcados en cada caso ({@code conjuntos}). Todos los demás arrays,
 * en orden estricto.</li>
 * <li>Objetos: con las claves ordenadas (el orden de campos de un objeto JSON no forma parte del contrato).</li>
 * </ul>
 * Lo que hoy es un error también queda congelado tal cual, a propósito: p. ej. {@code alumno_turnos.json} repite cada
 * turno de A05 (H2 del informe; decisión D1 al romper el ciclo EAGER), y {@code GET /api/admin/{id}} de un alumno con
 * convocatoria responde 500 ({@code admin_alumno_con_convocatoria.json} guarda solo el estado). Los arrays tipo bolsa
 * ({@code deportes}, {@code turnos}, {@code convocatorias}...) se comparan en orden estricto: su orden se ve en pantalla
 * y hoy es determinista.
 *
 * <h2>Regenerar</h2>
 * Solo tras un cambio de contrato decidido: {@code ./mvnw -o test -Dtest=MoiskimdoAlumnosJsonDoradoIntegrationTest
 * -Ddorado.regenerar=true} reescribe los ficheros (el diff en la PR es la evidencia del cambio). Sin la propiedad,
 * compara; si difiere, el fallo da la ruta JSON del primer cambio y deja la respuesta normalizada en
 * {@code target/dorado-r2-real/}.
 *
 * <h2>Límites de sentencias</h2>
 * {@link ContadorSentencias} cuenta las sentencias de Hibernate de la petición (tras una de calentamiento). En los
 * endpoints de toda la organización el número depende de lo que hayan dejado otras clases, así que el límite es
 * sobre el incremento respecto a la misma petición antes de sembrar ({@code Δ}); en los demás, absoluto. Valor =
 * medido sobre develop {@code a704511c} + un margen pequeño. Cada pieza que mejore un endpoint baja su límite.
 */
@SpringBootTest(properties = {
		"spring.jpa.properties.hibernate.session_factory.statement_inspector=com.taemoi.project.integracion.ContadorSentencias",
		// La URL de la foto se construye con app.base.url: fija aquí para que un APP_BASE_URL del entorno no la cambie.
		"app.base.url=http://localhost:8080" })
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MoiskimdoAlumnosJsonDoradoIntegrationTest extends MySqlIntegrationTest {

	static final String PREFIJO = "DORADOR2";
	static final int LIMITE_TURNO = 9;
	static final Instant HOY = ZonedDateTime.of(2026, 10, 1, 10, 0, 0, 0, ValidacionRelojConfig.ZONA).toInstant();
	private static final boolean REGENERAR = Boolean.getBoolean("dorado.regenerar");
	private static final String RECURSOS = "dorado/r2/";
	private static final Path FUENTES = Path.of("src", "test", "resources", "dorado", "r2");
	private static final Path REALES = Path.of("target", "dorado-r2-real");
	/** Campos que dependen de la fecha de hoy: se conservan con un marcador de su tipo en lugar del valor. */
	private static final Set<String> CAMPOS_DE_HOY = Set.of("antiguedad", "antiguedadCompetidor");
	private static final Pattern ID_EN_TEXTO = Pattern.compile("(?<!\\d)9[1-8]\\d{5}(?!\\d)");
	private static final Pattern MARCADOR = Pattern.compile("\\{([^}]+)\\}");

	// Rangos de ids reservados para esta clase (ninguna otra los usa; el AUTO_INCREMENT se restaura al final).
	private static final long ALUMNO = 9_100_000L;
	private static final long GRUPO = 9_200_000L;
	private static final long TURNO = 9_300_000L;
	private static final long ALUMNO_DEPORTE = 9_400_000L;
	private static final long TUTOR = 9_500_000L;
	private static final long USUARIO = 9_600_000L;
	private static final long PREINSCRIPCION = 9_700_000L;
	private static final long IMAGEN = 9_800_000L;
	private static final long DOCUMENTO = 9_810_000L;
	private static final long PRODUCTO = 9_820_000L;
	private static final long PRODUCTO_ALUMNO = 9_830_000L;
	private static final long CONVOCATORIA = 9_840_000L;
	private static final long ALUMNO_CONVOCATORIA = 9_850_000L;
	/** Ancho de cada rango (de {@code base} a {@code base + ANCHO}). */
	private static final long ANCHO = 9_999L;
	private static final int EXPEDIENTE = 7_700_000;
	private static final List<String> TABLAS_CON_RANGO = List.of("alumno_convocatoria", "convocatoria",
			"producto_alumno", "producto", "documento", "preinscripcion", "usuario", "tutor", "alumno_deporte", "alumno",
			"turno", "grupo", "imagen");

	enum Filtro { NINGUNO, ALUMNOS, IDS_ALUMNOS, GRUPOS, TURNOS }

	/**
	 * Un endpoint capturado.
	 *
	 * @param incremental si el límite de sentencias es sobre el incremento respecto a la petición antes de sembrar
	 *                    (endpoints de toda la organización).
	 * @param jsonIncremental si el JSON es un total numérico que se guarda como incremento.
	 * @param conjuntos rutas JSON ({@code $}, {@code .campo}, {@code [*]}) de los arrays sin orden (Set).
	 */
	record Caso(String nombre, String ruta, boolean anonimo, Filtro filtro, boolean incremental,
			boolean jsonIncremental, int limiteSql, Set<String> conjuntos, int estado) {

		@Override
		public String toString() {
			return nombre;
		}
	}

	private static Caso caso(String nombre, String ruta, int limiteSql, String... conjuntos) {
		return new Caso(nombre, ruta, false, Filtro.NINGUNO, false, false, limiteSql, Set.of(conjuntos), 200);
	}

	private static Caso deLaOrganizacion(String nombre, String ruta, Filtro filtro, int limiteDelta,
			String... conjuntos) {
		boolean anonimo = ruta.startsWith("ANON:");
		return new Caso(nombre, anonimo ? ruta.substring(5) : ruta, anonimo, filtro, true, false, limiteDelta,
				Set.of(conjuntos), 200);
	}

	private static Caso total(String nombre, String ruta, int limiteDelta) {
		return new Caso(nombre, ruta, false, Filtro.NINGUNO, true, true, limiteDelta, Set.of(), 200);
	}

	/**
	 * Endpoint que HOY falla: se congela su estado HTTP (el dorado es {@code {"estado": N}}), no el cuerpo. Arreglarlo
	 * es un cambio de contrato decidido que regenera ese fichero.
	 */
	private static Caso fallaHoy(String nombre, String ruta, int limiteSql, int estado) {
		return new Caso(nombre, ruta, false, Filtro.NINGUNO, false, false, limiteSql, Set.of(), estado);
	}

	/** Endpoints de la tabla §1 del informe R2 (y el de turnos de un alumno en un grupo). */
	static Stream<Caso> casos() {
		return Stream.of(
				// Límites: medido hoy (a704511c) + margen (2, o ~10 % en los grandes). Δ = incremento por la siembra.
				total("alumnos_count", "/api/alumnos/count", 2), // Δ0
				caso("alumnos_pagina", "/api/alumnos?page=1&size=9&nombre=" + PREFIJO + "&incluirInactivos=false",
						23), // 21
				caso("alumnos_pagina_inactivos",
						"/api/alumnos?page=2&size=9&nombre=" + PREFIJO + "&incluirInactivos=true", 25), // 23 (pág. 2: A10 inactivo)
				deLaOrganizacion("alumnos_sin_paginar", "/api/alumnos?incluirInactivos=false", Filtro.ALUMNOS,
						39), // Δ35
				deLaOrganizacion("alumnos_con_deportes", "/api/alumnos/con-deportes?incluirInactivos=false",
						Filtro.ALUMNOS, 39), // Δ35
				deLaOrganizacion("alumnos_ids", "/api/alumnos/ids?incluirInactivos=false", Filtro.IDS_ALUMNOS,
						2), // Δ0
				caso("alumno_ficha", "/api/alumnos/{alumno:A05}", 11), // 9
				caso("alumno_completo", "/api/alumnos/{alumno:A05}/completo", 11), // 9
				caso("alumno_grupos", "/api/alumnos/{alumno:A05}/grupos", 9, "$"), // 7
				caso("alumno_turnos", "/api/alumnos/{alumno:A05}/turnos", 9), // 7
				caso("alumno_deportes", "/api/alumnos/{alumno:A05}/deportes", 8), // 6
				caso("admin_alumno_entidad", "/api/admin/{alumno:A05}", 15, "$.grupos"), // 13
				caso("admin_alumno_con_usuario", "/api/admin/{alumno:A03}", 18, "$.grupos"), // 16
				fallaHoy("admin_alumno_con_convocatoria", "/api/admin/{alumno:A08}", 15, 500), // 13
				deLaOrganizacion("grupos", "/api/grupos", Filtro.GRUPOS, 5, "$[*].alumnos"), // Δ3
				caso("grupo_ficha", "/api/grupos/{grupo:TKD-INFANTIL}", 11, "$.alumnos"), // 9
				caso("grupo_turnos", "/api/grupos/{grupo:TKD-INFANTIL}/turnos", 13), // 11
				caso("grupo_alumno_turnos", "/api/grupos/{grupo:KB-INICIACION}/alumnos/{alumno:A05}/turnos",
						11), // 9
				total("grupos_conteo", "/api/grupos/conteo-alumnos", 2), // Δ0
				deLaOrganizacion("turnos", "/api/turnos", Filtro.TURNOS, 7), // Δ5
				caso("turno_ficha", "/api/turnos/{turno:TKD-INFANTIL/Lunes/17:00}", 26), // 23
				deLaOrganizacion("turnos_dto", "/api/turnos/dto", Filtro.TURNOS, 128), // Δ116
				deLaOrganizacion("turnos_dto_anonimo", "ANON:/api/turnos/dto", Filtro.TURNOS, 128)); // Δ116
	}

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired TurnoService turnoService;
	@Autowired TemporadaService temporadaService;

	@MockitoBean JavaMailSender mailSender;

	/** id sembrado → clave simbólica, y al revés. */
	final Map<Long, String> simbolos = new HashMap<>();
	final Map<String, Long> ids = new HashMap<>();
	final Map<String, JsonNode> jsonAntes = new HashMap<>();
	final Map<String, Integer> sqlAntes = new HashMap<>();
	final Map<String, String> medidas = new TreeMap<>();
	String token;
	Long gestor;
	String limiteOriginal;
	boolean limiteLeido;

	// ------------------------------------------------------------------ siembra

	@BeforeAll
	void preparar() throws Exception {
		borrarRangos();
		limiteOriginal = jdbc.query("SELECT valor FROM configuracion_organizacion WHERE organizacion_id = 1 AND clave = "
				+ "'limite_turno'", rs -> rs.next() ? rs.getString(1) : null);
		limiteLeido = true;
		fijarLimiteTurno(String.valueOf(LIMITE_TURNO));
		ReflectionTestUtils.setField((Object) AopTestUtils.getUltimateTargetObject(turnoService), "reloj",
				Clock.fixed(HOY, ValidacionRelojConfig.ZONA));
		token = tokenGestor();

		// Línea base de los endpoints de toda la organización, antes de sembrar.
		casos().filter(Caso::incremental).forEach(c -> {
			try {
				peticion(c, c.ruta());
				ContadorSentencias.Conteo<String> conteo = ContadorSentencias.contar(() -> peticion(c, c.ruta()));
				sqlAntes.put(c.nombre(), conteo.total());
				jsonAntes.put(c.nombre(), json.readTree(conteo.resultado()));
			} catch (Exception e) {
				throw new IllegalStateException(c.nombre(), e);
			}
		});
		sembrar();
	}

	private void sembrar() {
		long sede = jdbc.queryForObject("SELECT MIN(id) FROM sede WHERE organizacion_id = 1", Long.class);
		long tkd = disciplina("TAEKWONDO");
		long kb = disciplina("KICKBOXING");
		long grado = jdbc.queryForObject("SELECT MIN(id) FROM grado", Long.class);

		long infantil = grupo(1, "TKD-INFANTIL", "Taekwondo Infantil", tkd, sede, 6, 12);
		long adultos = grupo(2, "TKD-ADULTOS", "Taekwondo Adultos", tkd, sede, null, null);
		long kbIni = grupo(3, "KB-INICIACION", "Kickboxing Iniciacion", kb, null, null, null);
		long lunes = turno(1, "TKD-INFANTIL", infantil, "Lunes", "17:00", "18:00", "Taekwondo");
		long miercoles = turno(2, "TKD-INFANTIL", infantil, "Miércoles", "17:00", "18:00", null);
		long martes = turno(3, "TKD-ADULTOS", adultos, "Martes", "19:00", "20:00", null);
		long jueves = turno(4, "KB-INICIACION", kbIni, "Jueves", "18:00", "19:00", null);
		long sabado = turno(5, "SIN-GRUPO", null, "Sábado", "10:00", "11:00", null);

		jdbc.update("INSERT INTO imagen (id, nombre, ruta, tipo, url, organizacion_id) VALUES (?, 'foto-a02.jpg', "
				+ "'target/it-files/imagenes/foto-a02.jpg', 'image/jpeg', NULL, 1)", IMAGEN + 1);
		registrar(IMAGEN + 1, "imagen:A02");

		for (int i = 1; i <= 30; i++) {
			String codigo = String.format("A%02d", i);
			long id = ALUMNO + i;
			boolean activo = i != 10 && i != 29;
			int anio = i <= 12 ? 2013 + (i % 7) : i <= 20 ? 1975 + i : 1990 + i % 9;
			jdbc.update("""
					INSERT INTO alumno (id, activo, nombre, apellidos, numero_expediente, fecha_nacimiento, nif, direccion,
					  telefono, telefono2, genero, localidad, codigo_postal, email, fecha_alta, fecha_alta_inicial,
					  fecha_baja, autorizacion_web, observaciones, tiene_discapacidad, tiene_derecho_examen,
					  organizacion_id, sede_principal_id, foto_alumno_id)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'Sevilla', '41001', ?, '2020-09-01', '2019-09-01', ?, ?, ?, ?, ?,
					  1, ?, ?)""",
					id, activo, PREFIJO + " " + codigo, "Apellido" + (char) ('A' + (i * 7) % 26) + " Sintetico",
					EXPEDIENTE + i, Date.valueOf(String.format("%d-%02d-%02d", anio, 1 + i % 12, 1 + i % 28)),
					i % 3 == 0 ? String.format("X77000%02dD", i) : null, "Calle Dorado " + i,
					String.format("6000000%02d", i), i % 4 == 0 ? String.format("6990000%02d", i) : null,
					i % 2 == 0 ? "FEMENINO" : "MASCULINO", String.format("dorado-r2-a%02d@example.test", i),
					activo ? null : Date.valueOf("2025-06-30"), i % 5 != 0, i == 8 ? "Observación de prueba" : null,
					i == 9, i % 6 == 0, i == 30 ? null : sede, i == 2 ? IMAGEN + 1 : null);
			registrar(id, "alumno:" + codigo);
		}

		// Deportes: taekwondo principal salvo los de kickboxing (A21-A26); A05 con los dos activos; A07 con
		// kickboxing dado de baja.
		for (int i = 1; i <= 30; i++) {
			if (i >= 21 && i <= 26) {
				deporte(i, kb, "KICKBOXING", true, true, null);
			} else {
				deporte(i, tkd, "TAEKWONDO", true, true, grado);
			}
		}
		deporte(5, kb, "KICKBOXING", true, false, null);
		deporte(7, kb, "KICKBOXING", false, false, null);
		// Fichas deportivas completas (tarifa, categoría, peso, licencia, familia, competición): las asociaciones
		// EAGER de AlumnoDeporte (tarifa, categoría) y los campos que hoy solo rellenan las fichas reales.
		long categoria = jdbc.queryForObject("SELECT MIN(id) FROM categoria WHERE organizacion_id = 1", Long.class);
		fichaCompleta(5, "TAEKWONDO", tarifa("INFANTIL"), 25.0, categoria, 28.5, 1001, "HIJO", "FAM-DORADO-1");
		fichaCompleta(5, "KICKBOXING", tarifa("KICKBOXING"), 20.0, null, 28.5, 1002, "HIJO", "FAM-DORADO-1");
		fichaCompleta(13, "TAEKWONDO", tarifa("ADULTO"), 30.0, categoria, 72.0, 1003, "PADRE", "FAM-DORADO-1");
		fichaCompleta(21, "KICKBOXING", tarifa("KICKBOXING"), 20.0, null, 65.25, 1004, "NINGUNO", null);

		for (int i = 1; i <= 12; i++) {
			apuntarGrupo(i, infantil);
			apuntarTurno(i, lunes);
			if (i % 2 == 0) {
				apuntarTurno(i, miercoles);
			}
		}
		for (int i = 13; i <= 20; i++) {
			apuntarGrupo(i, adultos);
			apuntarTurno(i, martes);
		}
		for (int i : new int[] { 21, 22, 23, 24, 25, 26, 5, 7 }) {
			apuntarGrupo(i, kbIni);
			apuntarTurno(i, jueves);
		}
		apuntarTurno(27, sabado);
		apuntarTurno(30, sabado);

		jdbc.update("""
				INSERT INTO usuario (id, nombre, apellidos, email, contrasena, auth_provider, alumno_id,
				  recordatorio_racha_email_habilitado) VALUES (?, 'Familia', 'Dorado A03', 'dorado-r2-familia-a03@example.test',
				  'fake-hash-no-usado', 'LOCAL', ?, 0)""", USUARIO + 1, ALUMNO + 3);
		registrar(USUARIO + 1, "usuario:A03");
		// Reto diario y racha: campos de la entidad que solo salen en /api/admin.
		jdbc.update("UPDATE usuario SET recordatorio_racha_email_habilitado = 1, ultimo_recordatorio_racha_reset = "
				+ "'2026-09-28' WHERE id = ?", USUARIO + 1);
		jdbc.update("UPDATE alumno SET fecha_reto_diario_completado = '2026-09-30', racha_reto_diario = 4 WHERE id IN "
				+ "(?, ?)", ALUMNO + 3, ALUMNO + 5);
		tutor(1, 4, "Tutora Legal", "Dorado", "X7700099T", "MADRE", true);
		tutor(2, 6, "Tutor Contacto", "Dorado", null, "OTRO", false);

		// Preinscripción PENDIENTE en el turno de adultos: 8 alumnos + 1 pendiente = límite → completo.
		long plantilla = jdbc.queryForObject("""
				SELECT p.id FROM plantilla_preinscripcion p JOIN disciplina d ON d.id = p.disciplina_id
				WHERE p.organizacion_id = 1 AND d.codigo = 'TAEKWONDO' ORDER BY p.id LIMIT 1""", Long.class);
		jdbc.update("""
				INSERT INTO preinscripcion (id, referencia, temporada, estado, nombre, apellidos, dni, identidad_hash,
				  fecha_nacimiento, direccion, telefono, email, tiene_discapacidad, consentimiento_fotografico,
				  aceptacion_normas, firmante_nombre, firma, plantilla_id, plantilla_snapshot, pdf_firmado,
				  token_documento_hash, creada_en, actualizada_en, grupo_id, organizacion_id, disciplina_id)
				VALUES (?, 'DORADO-R2-P1', ?, 'PENDIENTE', 'Pendiente', 'Dorado', NULL, ?, '1990-05-05', 'Calle 1',
				  '600000000', 'dorado-r2-p1@example.test', 0, 0, 1, 'Firmante Dorado', x'00', ?, '{}', x'00', ?,
				  '2026-09-10 12:00:00', '2026-09-10 12:00:00', ?, 1, ?)""", PREINSCRIPCION + 1,
				temporadaService.actual(), "d".repeat(32), plantilla, "f".repeat(64), adultos, tkd);
		jdbc.update("INSERT INTO preinscripcion_turno (preinscripcion_id, turno_id) VALUES (?, ?)",
				PREINSCRIPCION + 1, martes);
		registrar(PREINSCRIPCION + 1, "preinscripcion:P1");

		// Documento, cargo (producto_alumno con su producto) y examen (alumno_convocatoria con su convocatoria) de A05:
		// colecciones de la entidad Alumno (/api/admin) y @ManyToOne EAGER hacia Alumno.
		jdbc.update("""
				INSERT INTO documento (id, nombre, ruta, tipo, url, alumno_id, organizacion_id)
				VALUES (?, 'autorizacion-a05.pdf', 'target/it-files/documentos/autorizacion-a05.pdf', 'application/pdf',
				  NULL, ?, 1)""", DOCUMENTO + 1, ALUMNO + 5);
		registrar(DOCUMENTO + 1, "documento:A05");
		jdbc.update("""
				INSERT INTO producto (id, concepto, precio, codigo, tipo, organizacion_id, disciplina_id, grado_id)
				VALUES (?, 'Dorado R2 camiseta', 15.5, 'DORADO_R2_CAMISETA', 'OTRO', 1, ?, NULL)""", PRODUCTO + 1, tkd);
		registrar(PRODUCTO + 1, "producto:CAMISETA");
		jdbc.update("""
				INSERT INTO producto_alumno (id, producto_id, alumno_id, alumno_deporte_id, concepto, fecha_asignacion,
				  cantidad, precio, pagado, fecha_pago, notas, organizacion_id, sede_id)
				VALUES (?, ?, ?, ?, 'Camiseta talla S', '2026-09-15 10:30:00', 2, 31.0, 1, '2026-09-20 12:00:00',
				  'Nota de prueba', 1, ?)""", PRODUCTO_ALUMNO + 1, PRODUCTO + 1, ALUMNO + 5,
				ids.get("alumnoDeporte:A05/TAEKWONDO"), sede);
		registrar(PRODUCTO_ALUMNO + 1, "productoAlumno:A05/CAMISETA");
		jdbc.update("INSERT INTO convocatoria (id, fecha_convocatoria, organizacion_id, disciplina_id) VALUES (?, "
				+ "'2026-06-15', 1, ?)", CONVOCATORIA + 1, tkd);
		registrar(CONVOCATORIA + 1, "convocatoria:TKD-2026-06");
		jdbc.update("""
				INSERT INTO alumno_convocatoria (id, cuantia_examen, pagado, fecha_pago, grado_actual, grado_siguiente,
				  alumno_id, alumno_deporte_id, convocatoria_id, producto_alumno_id, organizacion_id)
				VALUES (?, 40.0, 1, '2026-06-01', 'BLANCO', 'BLANCO_AMARILLO', ?, ?, ?, NULL, 1)""",
				ALUMNO_CONVOCATORIA + 1, ALUMNO + CONVOCADO, ids.get(String.format("alumnoDeporte:A%02d/TAEKWONDO",
						CONVOCADO)), CONVOCATORIA + 1);
		registrar(ALUMNO_CONVOCATORIA + 1, String.format("alumnoConvocatoria:A%02d/TKD-2026-06", CONVOCADO));
	}

	/**
	 * Alumno con examen en una convocatoria. No es A05: con una convocatoria, {@code GET /api/admin/{id}} responde hoy
	 * 500 (Alumno.convocatorias → AlumnoConvocatoria.alumno → Alumno... sin referencia de Jackson: anidamiento > 1000),
	 * y A05 es la muestra de la entidad. Ese 500 se congela en {@code admin_alumno_con_convocatoria}.
	 */
	private static final int CONVOCADO = 8;

	private long tarifa(String codigo) {
		return jdbc.queryForObject("SELECT id FROM tarifa WHERE organizacion_id = 1 AND codigo = ?", Long.class, codigo);
	}

	private void fichaCompleta(int alumno, String deporte, long tarifa, double cuantia, Long categoria, double peso,
			int licencia, String rolFamiliar, String grupoFamiliar) {
		jdbc.update("""
				UPDATE alumno_deporte SET tarifa_id = ?, cuantia_tarifa = ?, categoria_id = ?, peso = ?,
				  fecha_peso = '2026-09-05', tiene_licencia = 1, numero_licencia = ?, fecha_licencia = '2026-01-10',
				  rol_familiar = ?, grupo_familiar = ?, competidor = 1, fecha_alta_competicion = '2025-02-01',
				  fecha_alta_competidor_inicial = '2024-02-01'
				WHERE id = ?""", tarifa, cuantia, categoria, peso, licencia, rolFamiliar, grupoFamiliar,
				ids.get(String.format("alumnoDeporte:A%02d/%s", alumno, deporte)));
	}

	/** Cada paso en su propio try/finally: un fallo (p. ej. al borrar) no deja sin restaurar los siguientes. */
	@AfterAll
	void retirar() {
		try {
			borrarRangos();
		} finally {
			try {
				for (String tabla : TABLAS_CON_RANGO) {
					try {
						// Con un valor menor que el máximo, MySQL lo deja en max(id)+1: las clases siguientes no heredan
						// ids altos.
						jdbc.execute("ALTER TABLE " + tabla + " AUTO_INCREMENT = 1");
					} catch (RuntimeException e) {
						System.err.println("Dorado R2: no se pudo restaurar AUTO_INCREMENT de " + tabla + ": " + e);
					}
				}
			} finally {
				try {
					if (limiteLeido) { // si preparar() falló antes de leerlo, no se toca
						fijarLimiteTurno(limiteOriginal);
					}
				} finally {
					try {
						ReflectionTestUtils.setField((Object) AopTestUtils.getUltimateTargetObject(turnoService), "reloj",
								Clock.system(ValidacionRelojConfig.ZONA));
					} finally {
						try {
							if (gestor != null) {
								jdbc.update("UPDATE membresia SET activa = FALSE WHERE usuario_id = ?", gestor);
							}
						} finally {
							System.out.println("Dorado R2 · sentencias por endpoint (Δ = sobre la petición antes de sembrar):");
							medidas.forEach((k, v) -> System.out.println("  " + k + " = " + v));
						}
					}
				}
			}
		}
	}

	private void borrarRangos() {
		borrar("DELETE FROM alumno_convocatoria WHERE id BETWEEN ? AND ?", ALUMNO_CONVOCATORIA);
		borrar("DELETE FROM convocatoria WHERE id BETWEEN ? AND ?", CONVOCATORIA);
		borrar("DELETE FROM producto_alumno WHERE id BETWEEN ? AND ?", PRODUCTO_ALUMNO);
		borrar("DELETE FROM producto WHERE id BETWEEN ? AND ?", PRODUCTO);
		borrar("DELETE FROM documento WHERE id BETWEEN ? AND ?", DOCUMENTO);
		borrar("DELETE FROM preinscripcion_turno WHERE preinscripcion_id BETWEEN ? AND ?", PREINSCRIPCION);
		borrar("DELETE FROM preinscripcion WHERE id BETWEEN ? AND ?", PREINSCRIPCION);
		borrar("DELETE FROM usuario WHERE id BETWEEN ? AND ?", USUARIO);
		borrar("DELETE FROM tutor WHERE id BETWEEN ? AND ?", TUTOR);
		borrar("DELETE FROM alumno_turno WHERE alumno_id BETWEEN ? AND ?", ALUMNO);
		borrar("DELETE FROM alumno_turno WHERE turno_id BETWEEN ? AND ?", TURNO);
		borrar("DELETE FROM alumno_grupo WHERE alumno_id BETWEEN ? AND ?", ALUMNO);
		borrar("DELETE FROM alumno_grupo WHERE grupo_id BETWEEN ? AND ?", GRUPO);
		borrar("DELETE FROM alumno_deporte WHERE id BETWEEN ? AND ?", ALUMNO_DEPORTE);
		borrar("DELETE FROM alumno WHERE id BETWEEN ? AND ?", ALUMNO);
		borrar("DELETE FROM turno WHERE id BETWEEN ? AND ?", TURNO);
		borrar("DELETE FROM grupo WHERE id BETWEEN ? AND ?", GRUPO);
		borrar("DELETE FROM imagen WHERE id BETWEEN ? AND ?", IMAGEN);
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
			u.setApellidos("Dorado R2");
			u.setEmail("dorado-r2-gestor-" + System.nanoTime() + "@example.test");
			u.setContrasena("fake-Password-123");
			u.setAuthProvider(AuthProvider.LOCAL);
			return usuarios.save(u);
		});
		gestor = usuario.getId();
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, 1, 'PROPIETARIO')", gestor);
		return jwtService.generateToken(usuario, 3_600_000, 1L);
	}

	private void registrar(long id, String simbolo) {
		simbolos.put(id, simbolo);
		ids.put(simbolo, id);
	}

	private long disciplina(String codigo) {
		return jdbc.queryForObject("SELECT id FROM disciplina WHERE organizacion_id = 1 AND codigo = ?", Long.class,
				codigo);
	}

	private long grupo(int n, String clave, String nombre, long disciplina, Long sede, Integer min, Integer max) {
		long id = GRUPO + n;
		jdbc.update("""
				INSERT INTO grupo (id, nombre, tipo, rango_edad_min, rango_edad_max, organizacion_id, disciplina_id, sede_id)
				VALUES (?, ?, NULL, ?, ?, 1, ?, ?)""", id, PREFIJO + " " + nombre, min, max, disciplina, sede);
		registrar(id, "grupo:" + clave);
		return id;
	}

	private long turno(int n, String grupoClave, Long grupo, String dia, String inicio, String fin, String tipo) {
		long id = TURNO + n;
		jdbc.update("""
				INSERT INTO turno (id, dia_semana, hora_inicio, hora_fin, tipo, grupo_id, organizacion_id)
				VALUES (?, ?, ?, ?, ?, ?, 1)""", id, dia, inicio, fin, tipo, grupo);
		registrar(id, "turno:" + grupoClave + "/" + dia + "/" + inicio);
		return id;
	}

	private int deportesSembrados;

	private void deporte(int alumno, long disciplina, String codigo, boolean activo, boolean principal, Long grado) {
		long id = ALUMNO_DEPORTE + (++deportesSembrados);
		jdbc.update("""
				INSERT INTO alumno_deporte (id, activo, apto_para_examen, alumno_id, competidor, principal, tiene_licencia,
				  organizacion_id, fecha_alta, fecha_alta_inicial, fecha_grado, fecha_baja, disciplina_id, grado_id)
				VALUES (?, ?, ?, ?, 0, ?, ?, 1, '2020-09-01', '2019-09-01', '2021-01-15', ?, ?, ?)""",
				id, activo, alumno % 4 == 1, ALUMNO + alumno, principal, alumno % 3 == 1,
				activo ? null : Date.valueOf("2025-06-30"), disciplina, grado);
		registrar(id, String.format("alumnoDeporte:A%02d/%s", alumno, codigo));
	}

	private void tutor(int n, int alumno, String nombre, String apellidos, String nif, String parentesco,
			boolean legal) {
		jdbc.update("""
				INSERT INTO tutor (id, organizacion_id, alumno_id, nombre, apellidos, nif, parentesco, telefono, email,
				  responsable_legal, orden) VALUES (?, 1, ?, ?, ?, ?, ?, '611000000', NULL, ?, 0)""",
				TUTOR + n, ALUMNO + alumno, nombre, apellidos, nif, parentesco, legal);
		registrar(TUTOR + n, String.format("tutor:A%02d/%d", alumno, n));
	}

	private void apuntarGrupo(int alumno, long grupo) {
		jdbc.update("INSERT INTO alumno_grupo (grupo_id, alumno_id) VALUES (?, ?)", grupo, ALUMNO + alumno);
	}

	private void apuntarTurno(int alumno, long turno) {
		jdbc.update("INSERT INTO alumno_turno (alumno_id, turno_id) VALUES (?, ?)", ALUMNO + alumno, turno);
	}

	// ------------------------------------------------------------------ test

	@ParameterizedTest(name = "{0}")
	@MethodSource("casos")
	void jsonIgualAlDoradoYSentenciasDentroDelLimite(Caso caso) throws Exception {
		String ruta = resolver(caso.ruta());
		peticion(caso, ruta); // calentamiento: la primera petición puede cargar cachés
		ContadorSentencias.Conteo<String> conteo = ContadorSentencias.contar(() -> peticion(caso, ruta));
		int sql = conteo.total() - (caso.incremental() ? sqlAntes.get(caso.nombre()) : 0);
		medidas.put(caso.nombre(), (caso.incremental() ? "Δ" : "") + sql + " (límite " + caso.limiteSql() + ", total "
				+ conteo.total() + ")");

		JsonNode real = caso.estado() == 200 ? normalizar(caso, json.readTree(conteo.resultado()))
				: JsonNodeFactory.instance.objectNode().put("estado", caso.estado());
		String fichero = caso.nombre() + ".json";
		if (REGENERAR) {
			Files.createDirectories(FUENTES);
			Files.writeString(FUENTES.resolve(fichero), aTexto(real), StandardCharsets.UTF_8);
		}
		assertAll(
				() -> {
					if (!REGENERAR) {
						compararConDorado(fichero, real);
					}
				},
				() -> assertTrue(sql <= caso.limiteSql(), caso.nombre() + ": " + (caso.incremental() ? "Δ " : "")
						+ sql + " sentencias SQL > límite " + caso.limiteSql()));
	}

	private void compararConDorado(String fichero, JsonNode real) throws IOException {
		JsonNode esperado;
		try (InputStream in = getClass().getClassLoader().getResourceAsStream(RECURSOS + fichero)) {
			assertNotNull(in, RECURSOS + fichero + " no existe: genéralo con -Ddorado.regenerar=true");
			esperado = json.readTree(in);
		}
		Optional<String> diferencia = primeraDiferencia("$", esperado, real);
		if (diferencia.isPresent()) {
			Files.createDirectories(REALES);
			Files.writeString(REALES.resolve(fichero), aTexto(real), StandardCharsets.UTF_8);
			fail(RECURSOS + fichero + " difiere del JSON actual en " + diferencia.get()
					+ "\n(respuesta normalizada en " + REALES.resolve(fichero)
					+ "; si el cambio es decidido, regenera con -Ddorado.regenerar=true)");
		}
	}

	private String peticion(Caso caso, String ruta) throws Exception {
		MockHttpServletRequestBuilder req = get(ruta);
		if (!caso.anonimo()) {
			req = req.cookie(new Cookie("jwt", token));
		}
		MvcResult r = mvc.perform(req).andReturn();
		assertEquals(caso.estado(), r.getResponse().getStatus(), ruta + ": " + r.getResponse().getContentAsString());
		return r.getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

	private String resolver(String ruta) {
		Matcher m = MARCADOR.matcher(ruta);
		StringBuilder sb = new StringBuilder();
		while (m.find()) {
			Long id = ids.get(m.group(1));
			assertNotNull(id, "Clave simbólica desconocida: " + m.group(1));
			m.appendReplacement(sb, String.valueOf(id));
		}
		m.appendTail(sb);
		return sb.toString();
	}

	// ------------------------------------------------------------------ normalización

	JsonNode normalizar(Caso caso, JsonNode respuesta) {
		JsonNode nodo = caso.jsonIncremental() ? restar(respuesta, jsonAntes.get(caso.nombre())) : respuesta;
		nodo = switch (caso.filtro()) {
			case NINGUNO -> nodo;
			case ALUMNOS -> filtrar(nodo, n -> esSembrado(n.get("id"), "alumno:"));
			case IDS_ALUMNOS -> filtrar(nodo, n -> esSembrado(n, "alumno:"));
			case GRUPOS -> filtrar(nodo, n -> esSembrado(n.get("id"), "grupo:"));
			case TURNOS -> filtrar(nodo, n -> esSembrado(n.get("id"), "turno:"));
		};
		return normalizarNodo(nodo, "$", caso.conjuntos());
	}

	private boolean esSembrado(JsonNode id, String tipo) {
		if (id == null || !id.isIntegralNumber()) {
			return false;
		}
		String simbolo = simbolos.get(id.asLong());
		return simbolo != null && simbolo.startsWith(tipo);
	}

	private static JsonNode filtrar(JsonNode lista, java.util.function.Predicate<JsonNode> sembrado) {
		assertTrue(lista.isArray(), "Se esperaba una lista: " + lista);
		ArrayNode filtrada = JsonNodeFactory.instance.arrayNode();
		lista.forEach(n -> {
			if (sembrado.test(n)) {
				filtrada.add(n);
			}
		});
		return filtrada;
	}

	/** Totales: diferencia campo a campo con la respuesta antes de sembrar. */
	private static JsonNode restar(JsonNode despues, JsonNode antes) {
		if (despues.isIntegralNumber()) {
			return LongNode.valueOf(despues.asLong() - (antes != null && antes.isNumber() ? antes.asLong() : 0));
		}
		if (despues.isObject()) {
			ObjectNode o = JsonNodeFactory.instance.objectNode();
			despues.fields().forEachRemaining(e -> o.set(e.getKey(),
					restar(e.getValue(), antes != null ? antes.get(e.getKey()) : null)));
			return o;
		}
		return despues;
	}

	private JsonNode normalizarNodo(JsonNode nodo, String ruta, Set<String> conjuntos) {
		if (nodo.isObject()) {
			ObjectNode o = JsonNodeFactory.instance.objectNode();
			List<String> campos = new ArrayList<>();
			nodo.fieldNames().forEachRemaining(campos::add);
			Collections.sort(campos);
			for (String campo : campos) {
				JsonNode valor = nodo.get(campo);
				o.set(campo, CAMPOS_DE_HOY.contains(campo) ? marcadorDeTipo(valor)
						: normalizarNodo(valor, ruta + "." + campo, conjuntos));
			}
			return o;
		}
		if (nodo.isArray()) {
			List<JsonNode> hijos = new ArrayList<>();
			nodo.forEach(h -> hijos.add(normalizarNodo(h, ruta + "[*]", conjuntos)));
			if (conjuntos.contains(ruta)) {
				hijos.sort(Comparator.comparing(MoiskimdoAlumnosJsonDoradoIntegrationTest::claveDeOrden));
			}
			ArrayNode a = JsonNodeFactory.instance.arrayNode();
			hijos.forEach(a::add);
			return a;
		}
		if (nodo.isIntegralNumber() && esCampoFecha(ruta)) {
			return TextNode.valueOf(fecha(nodo.asLong()));
		}
		if (nodo.isIntegralNumber()) {
			String simbolo = simbolos.get(nodo.asLong());
			return simbolo != null ? TextNode.valueOf(simbolo) : nodo;
		}
		if (nodo.isTextual()) {
			String texto = nodo.asText();
			Matcher m = ID_EN_TEXTO.matcher(texto);
			StringBuilder sb = new StringBuilder();
			while (m.find()) {
				// Solo dentro de un texto más largo (una URL...): un id que pasara de número a texto debe verse.
				String simbolo = m.group().length() < texto.length() ? simbolos.get(Long.parseLong(m.group())) : null;
				m.appendReplacement(sb, Matcher.quoteReplacement(simbolo != null ? simbolo : m.group()));
			}
			m.appendTail(sb);
			return TextNode.valueOf(sb.toString());
		}
		return nodo;
	}

	private static JsonNode marcadorDeTipo(JsonNode valor) {
		if (valor.isNull()) {
			return valor;
		}
		return TextNode.valueOf(valor.isTextual() ? "<texto>" : valor.isNumber() ? "<numero>" : "<" + valor.getNodeType() + ">");
	}

	private static boolean esCampoFecha(String ruta) {
		return ruta.substring(ruta.lastIndexOf('.') + 1).startsWith("fecha");
	}

	/**
	 * Fechas que Jackson escribe en milisegundos: un {@code java.sql.Date} leído de MySQL es la medianoche de la zona
	 * de la JVM, así que el número cambia con la zona (la CI va en UTC). Se vuelve a la fecha de calendario en esa
	 * misma zona, que es la que guarda la base de datos.
	 */
	private static String fecha(long milisegundos) {
		LocalDateTime local = LocalDateTime.ofInstant(Instant.ofEpochMilli(milisegundos), ZoneId.systemDefault());
		return "fecha:" + (local.toLocalTime().equals(LocalTime.MIDNIGHT) ? local.toLocalDate() : local);
	}

	private static String claveDeOrden(JsonNode n) {
		return n.isObject() && n.has("id") ? n.get("id").asText() + "\u0000" + n : n.toString();
	}

	/** Ruta JSON y detalle de la primera diferencia (objetos sin orden de campos; arrays en orden). */
	static Optional<String> primeraDiferencia(String ruta, JsonNode esperado, JsonNode real) {
		if (esperado.isObject() && real.isObject()) {
			Set<String> campos = new TreeSet<>();
			esperado.fieldNames().forEachRemaining(campos::add);
			real.fieldNames().forEachRemaining(campos::add);
			for (String campo : campos) {
				String r = ruta + "." + campo;
				if (!esperado.has(campo)) {
					return Optional.of(r + ": campo nuevo, no está en el dorado (valor " + resumen(real.get(campo)) + ")");
				}
				if (!real.has(campo)) {
					return Optional.of(r + ": falta (en el dorado vale " + resumen(esperado.get(campo)) + ")");
				}
				Optional<String> d = primeraDiferencia(r, esperado.get(campo), real.get(campo));
				if (d.isPresent()) {
					return d;
				}
			}
			return Optional.empty();
		}
		if (esperado.isArray() && real.isArray()) {
			for (int i = 0; i < Math.min(esperado.size(), real.size()); i++) {
				Optional<String> d = primeraDiferencia(ruta + "[" + i + "]", esperado.get(i), real.get(i));
				if (d.isPresent()) {
					return d.map(s -> s + (esperado.size() != real.size()
							? " (además, " + esperado.size() + " elementos en el dorado y " + real.size() + " ahora)"
							: ""));
				}
			}
			if (esperado.size() != real.size()) {
				return Optional.of(ruta + ": " + esperado.size() + " elementos en el dorado y " + real.size()
						+ " ahora; primero distinto en [" + Math.min(esperado.size(), real.size()) + "]: "
						+ resumen(esperado.size() > real.size() ? esperado.get(real.size()) : real.get(esperado.size())));
			}
			return Optional.empty();
		}
		if (esperado.isNumber() && real.isNumber() ? esperado.decimalValue().compareTo(real.decimalValue()) == 0
				: esperado.equals(real)) {
			return Optional.empty();
		}
		return Optional.of(ruta + ": esperado " + resumen(esperado) + " pero llegó " + resumen(real));
	}

	private static String resumen(JsonNode n) {
		String s = n.toString();
		return s.length() > 200 ? s.substring(0, 200) + "…" : s;
	}

	private String aTexto(JsonNode nodo) throws IOException {
		DefaultPrettyPrinter impresora = new DefaultPrettyPrinter()
				.withObjectIndenter(new DefaultIndenter("  ", "\n"))
				.withArrayIndenter(new DefaultIndenter("  ", "\n"));
		return json.writer(impresora).writeValueAsString(nodo) + "\n";
	}
}
