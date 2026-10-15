package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * V29 (contracción de personas) sobre datos escritos por el código anterior (columnas heredadas
 * {@code alumno.responsable_legal_*} sincronizadas con el primer tutor responsable legal): rellena los
 * responsables que solo estaban en las columnas (solo NIF con nombre NULL), pasa a NULL los nombres
 * vacíos, crea la restricción «nombre o NIF», borra las columnas y se puede repetir; su comprobación
 * previa detiene la migración ANTES de cambiar nada si una columna heredada no coincide con el tutor; un
 * fallo a medias se completa con {@code repair} + {@code migrate}. También ejecuta
 * {@code docs/multi-escuela/precheck-v29.sql}. Todos los datos son sintéticos.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PersonasContraccionMigracionIntegrationTest extends MySqlIntegrationTest {

	private static final String ESQUEMA = "taemoi_v29_it";
	private static final String ESQUEMA_GUARDA = "taemoi_v29_guarda_it";
	private static final String ESQUEMA_REPETICION = "taemoi_v29_repeticion_it";
	private static final String V29 = "db/migration/V29__contraccion_personas.sql";

	DataSource dataSource;
	JdbcTemplate jdbc;
	long org2;
	long conTutor;
	long conTutorRepartido;
	long soloNif;
	long soloNifConTutorNoResponsable;
	long soloNombre;
	long enBlanco;
	long marcadorSinNif;
	long soloNifOtraEscuela;
	long tutorVacio;
	List<Map<String, Object>> tutoresConResponsableAntes;
	Map<String, Long> precheck;

	@BeforeAll
	void migrarConDatosDelCodigoAnterior() throws Exception {
		dataSource = recrear(ESQUEMA);
		jdbc = new JdbcTemplate(dataSource);
		flyway(dataSource, "28").migrate();

		jdbc.update("INSERT INTO organizacion (slug, nombre) VALUES ('it-v29-otra', 'Otra escuela')");
		org2 = jdbc.queryForObject("SELECT id FROM organizacion WHERE slug = 'it-v29-otra'", Long.class);

		// Nombre + NIF con su tutor (como lo dejaba V25 o el puente): no se toca.
		conTutor = alumno(jdbc, 1, 1, "  Lucía   Ruiz ", " 12345678z");
		tutor(jdbc, conTutor, "Lucía   Ruiz", null, "12345678Z", true, 1);
		// Tutor repartido en nombre y apellidos desde /tutores; la columna guarda el nombre completo.
		conTutorRepartido = alumno(jdbc, 1, 2, "Pedro Gómez", "11111111H");
		tutor(jdbc, conTutorRepartido, "Abuela", null, null, false, 1);
		tutor(jdbc, conTutorRepartido, "Pedro", "Gómez", "11111111H", true, 2);
		// Solo NIF sin tutor: V29 crea el responsable con nombre NULL.
		soloNif = alumno(jdbc, 1, 3, null, " 87654321x");
		// El puente al vaciar solo el nombre: el tutor deja de ser responsable y el NIF queda en la columna.
		soloNifConTutorNoResponsable = alumno(jdbc, 1, 4, "", "22222222J");
		tutor(jdbc, soloNifConTutorNoResponsable, "Ana López", null, "22222222J", false, 1);
		// Solo nombre sin tutor (V25 no lo creó: escrito después por SQL a mano).
		soloNombre = alumno(jdbc, 1, 5, "  Marta \t Gil ", null);
		// En blanco o con el marcador «sin NIF»: nada.
		enBlanco = alumno(jdbc, 1, 6, "   ", "");
		marcadorSinNif = alumno(jdbc, 1, 7, null, "00000000z");
		// Otra escuela: el tutor queda en la organización del alumno.
		soloNifOtraEscuela = alumno(jdbc, org2, 1, null, "33333333P");
		// Tutor con nombre vacío y NIF (escrito por el código del paso 1 antes de V29): nombre -> NULL.
		tutorVacio = alumno(jdbc, 1, 8, null, "44444444A");
		tutor(jdbc, tutorVacio, "", null, "44444444A", true, 1);

		tutoresConResponsableAntes = jdbc.queryForList(
				"SELECT * FROM tutor WHERE alumno_id IN (?, ?) ORDER BY id", conTutor, conTutorRepartido);
		precheck = ejecutarPrecheck(jdbc);
		flyway(dataSource, null).migrate();
	}

	@Test
	void llegaAV29() {
		assertEquals(1, versionAplicada(jdbc, "29"));
	}

	/** docs/multi-escuela/precheck-v29.sql antes de V29: solo recuentos. */
	@Test
	void precheckDeV29_soloRecuentos() {
		Map<String, Long> esperado = new LinkedHashMap<>();
		esperado.put("responsable_nombre_diferente", 0L);
		esperado.put("responsable_nif_diferente", 0L);
		esperado.put("tutores_sin_nombre_ni_nif", 0L);
		esperado.put("alumnos_solo_nif_sin_tutor", 3L); // soloNif, soloNifConTutorNoResponsable, otra escuela
		esperado.put("alumnos_nombre_sin_tutor", 1L); // soloNombre
		esperado.put("tutores_nombre_vacio", 1L); // tutorVacio
		assertEquals(esperado, precheck);
	}

	@Test
	void losTutoresQueYaCoincidianNoSeTocan() {
		assertEquals(tutoresConResponsableAntes, jdbc.queryForList(
				"SELECT * FROM tutor WHERE alumno_id IN (?, ?) ORDER BY id", conTutor, conTutorRepartido));
	}

	@Test
	void soloNifSinTutor_recibeUnResponsableConNombreNull() {
		assertEquals(List.of("null|null|87654321X|TUTOR_LEGAL|true|1|1"), filas(soloNif));
		assertEquals(List.of("Ana López|null|22222222J|OTRO|false|1|1", "null|null|22222222J|TUTOR_LEGAL|true|2|1"),
				filas(soloNifConTutorNoResponsable), "orden = máximo + 1; el tutor con nombre se conserva");
		assertEquals(List.of("null|null|33333333P|TUTOR_LEGAL|true|1|" + org2), filas(soloNifOtraEscuela));
	}

	@Test
	void soloNombreSinTutor_recibeUnResponsableConElNombreLimpio() {
		assertEquals(List.of("Marta Gil|null|null|TUTOR_LEGAL|true|1|1"), filas(soloNombre));
	}

	@Test
	void enBlancoOMarcadorSinNif_noRecibenTutor() {
		assertEquals(List.of(), filas(enBlanco));
		assertEquals(List.of(), filas(marcadorSinNif));
	}

	@Test
	void nombreVacioPasaANull() {
		assertEquals(List.of("null|null|44444444A|TUTOR_LEGAL|true|1|1"), filas(tutorVacio));
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM tutor WHERE TRIM(nombre) = ''", Integer.class));
	}

	@Test
	void esquemaFinal_columnasBorradas_nombreNullable_yRestriccion() {
		assertFalse(existeColumna(jdbc, "alumno", "responsable_legal_nombre"));
		assertFalse(existeColumna(jdbc, "alumno", "responsable_legal_nif"));
		assertEquals("YES", jdbc.queryForObject("""
				SELECT IS_NULLABLE FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tutor' AND COLUMN_NAME = 'nombre'""", String.class));
		assertTrue(existeRestriccionEn(jdbc));
	}

	@Test
	void laRestriccionRechazaUnTutorSinNombreNiNif() {
		for (String[] valores : List.of(new String[] { null, null }, new String[] { "   ", "" })) {
			DataAccessException error = assertThrows(DataAccessException.class,
					() -> tutor(jdbc, enBlanco, valores[0], null, valores[1], false, 9));
			assertTrue(mensajeCompleto(error).contains("ck_tutor_nombre_o_nif"), mensajeCompleto(error));
		}
		// Con nombre o con NIF, sí.
		tutor(jdbc, enBlanco, null, null, "55555555K", false, 9);
		tutor(jdbc, enBlanco, "Solo nombre", null, null, false, 10);
		assertEquals(2, filas(enBlanco).size());
		jdbc.update("DELETE FROM tutor WHERE alumno_id = ?", enBlanco);
	}

	@Test
	void repetirV29NoCambiaNada() throws Exception {
		List<Map<String, Object>> tutores = jdbc.queryForList("SELECT * FROM tutor ORDER BY id");
		List<Map<String, Object>> columnas = columnas(jdbc);
		try (Connection conexion = dataSource.getConnection()) {
			ScriptUtils.executeSqlScript(conexion, new EncodedResource(new ClassPathResource(V29), StandardCharsets.UTF_8));
		}
		assertEquals(tutores, jdbc.queryForList("SELECT * FROM tutor ORDER BY id"));
		assertEquals(columnas, columnas(jdbc));
		assertTrue(existeRestriccionEn(jdbc));
	}

	/**
	 * Comprobación previa: con una columna heredada distinta del tutor, V29 se detiene nombrando el motivo y
	 * NO cambia nada (ni rellena tutores, ni cambia tutor.nombre, ni borra columnas). Tras corregir,
	 * {@code repair} + {@code migrate} sigue con la siguiente incoherencia y, al final, termina.
	 */
	@Test
	void comprobacionPrevia_detieneAntesDeCambiarNada_yNombraElMotivo() {
		DataSource fuente = recrear(ESQUEMA_GUARDA);
		JdbcTemplate g = new JdbcTemplate(fuente);
		flyway(fuente, "28").migrate();
		long pendiente = alumno(g, 1, 1, null, "87654321X"); // se rellenaría si V29 siguiera
		long distinto = alumno(g, 1, 2, "Otra Persona", "12345678Z");
		tutor(g, distinto, "Lucía Ruiz", null, "12345678Z", true, 1);
		tutor(g, distinto, "Otra Persona", null, "12345678Z", true, 2); // no es el primero
		int tutoresAntes = contarTutores(g);

		// 1. Nombre distinto del primer responsable.
		assertFallaNombrando(fuente, "v29_responsable_legal_nombre_no_coincide_con_el_tutor");
		assertNadaCambiado(g, tutoresAntes);

		// 2. Corregido el nombre; ahora el NIF.
		g.update("UPDATE alumno SET responsable_legal_nombre = 'Lucía Ruiz', responsable_legal_nif = '99999999R' WHERE id = ?",
				distinto);
		assertFallaNombrando(fuente, "v29_responsable_legal_nif_no_coincide_con_el_tutor");
		assertNadaCambiado(g, tutoresAntes);

		// 3. Corregido; ahora un tutor sin nombre ni NIF (la restricción no se podría crear).
		g.update("UPDATE alumno SET responsable_legal_nif = ' 12345678z' WHERE id = ?", distinto);
		tutor(g, pendiente, "", null, null, false, 1);
		assertFallaNombrando(fuente, "v29_tutor_sin_nombre_ni_nif");
		assertNadaCambiado(g, tutoresAntes + 1);

		// 4. Corregido: termina.
		g.update("UPDATE tutor SET nif = '87654321X' WHERE alumno_id = ? AND nif IS NULL", pendiente);
		Flyway f = flyway(fuente, null);
		f.repair();
		f.migrate();
		assertEquals(1, versionAplicada(g, "29"));
		assertFalse(existeColumna(g, "alumno", "responsable_legal_nombre"));
		// pendiente ya tenía un tutor (no responsable) con su NIF: recibe además el responsable solo-NIF.
		assertEquals(List.of("null|null|87654321X|OTRO|false|1|1", "null|null|87654321X|TUTOR_LEGAL|true|2|1"),
				filasEn(g, pendiente));
	}

	/**
	 * Fallo a medias: V29 falla en el relleno (paso 2, con tutor.nombre ya NULL: la DDL del paso 1 se
	 * confirmó) y después al borrar las columnas (paso 5, con relleno, nombres a NULL y restricción ya
	 * confirmados por las DDL anteriores). Cada vez, {@code repair} + {@code migrate} retoma sin duplicar
	 * tutores ni saltarse pasos, y al final el esquema y los datos son los esperados.
	 */
	@Test
	void falloParcial_repairYMigrate_terminaBienSinDuplicar() {
		DataSource fuente = recrear(ESQUEMA_REPETICION);
		JdbcTemplate r = new JdbcTemplate(fuente);
		flyway(fuente, "28").migrate();
		long nif = alumno(r, 1, 1, null, "87654321X");
		long vacio = alumno(r, 1, 2, null, "44444444A");
		tutor(r, vacio, "", null, "44444444A", true, 1);

		// a) Falla al insertar el primer tutor de relleno.
		r.execute("""
				CREATE TRIGGER it_v29_fallo_insert BEFORE INSERT ON tutor FOR EACH ROW
				SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'fallo simulado de V29 (insert)'""");
		FlywayException error = assertThrows(FlywayException.class, () -> flyway(fuente, null).migrate());
		assertTrue(mensajeCompleto(error).contains("fallo simulado de V29 (insert)"), mensajeCompleto(error));
		assertEquals("YES", nullable(r), "el paso 1 ya se aplicó");
		assertTrue(existeColumna(r, "alumno", "responsable_legal_nif"));
		assertEquals(List.of(), filasEn(r, nif));
		r.execute("DROP TRIGGER it_v29_fallo_insert");

		// b) Falla al borrar las columnas (una columna generada depende de responsable_legal_nif).
		r.execute("ALTER TABLE alumno ADD COLUMN it_v29_dependiente VARCHAR(16) AS (responsable_legal_nif) VIRTUAL");
		Flyway segundo = flyway(fuente, null);
		segundo.repair();
		error = assertThrows(FlywayException.class, segundo::migrate);
		assertTrue(mensajeCompleto(error).contains("generated column"), mensajeCompleto(error));
		assertEquals(List.of("null|null|87654321X|TUTOR_LEGAL|true|1|1"), filasEn(r, nif));
		assertEquals(List.of("null|null|44444444A|TUTOR_LEGAL|true|1|1"), filasEn(r, vacio));
		assertTrue(existeRestriccionEn(r));
		assertTrue(existeColumna(r, "alumno", "responsable_legal_nif"), "el borrado de las dos columnas es atómico");
		assertTrue(existeColumna(r, "alumno", "responsable_legal_nombre"));
		r.execute("ALTER TABLE alumno DROP COLUMN it_v29_dependiente");

		// c) Termina: sin duplicar el relleno, nombres vacíos a NULL, restricción y columnas borradas.
		Flyway tercero = flyway(fuente, null);
		tercero.repair();
		tercero.migrate();
		assertEquals(1, versionAplicada(r, "29"));
		assertEquals(List.of("null|null|87654321X|TUTOR_LEGAL|true|1|1"), filasEn(r, nif));
		assertEquals(List.of("null|null|44444444A|TUTOR_LEGAL|true|1|1"), filasEn(r, vacio));
		assertTrue(existeRestriccionEn(r));
		assertFalse(existeColumna(r, "alumno", "responsable_legal_nombre"));
		assertFalse(existeColumna(r, "alumno", "responsable_legal_nif"));
	}

	// ---------------------------------------------------------------------------------

	private void assertFallaNombrando(DataSource fuente, String tabla) {
		Flyway f = flyway(fuente, null);
		f.repair();
		FlywayException error = assertThrows(FlywayException.class, f::migrate);
		assertTrue(mensajeCompleto(error).contains(tabla), mensajeCompleto(error));
	}

	/** Comprobación previa antes de todo: nada relleno, tutor.nombre NOT NULL, columnas y sin restricción. */
	private static void assertNadaCambiado(JdbcTemplate g, int tutores) {
		assertEquals(tutores, contarTutores(g));
		assertEquals("NO", nullable(g));
		assertTrue(existeColumna(g, "alumno", "responsable_legal_nombre"));
		assertTrue(existeColumna(g, "alumno", "responsable_legal_nif"));
		assertFalse(existeRestriccionEn(g));
		assertEquals(0, versionAplicada(g, "29"));
	}

	private List<String> filas(long alumnoId) {
		return filasEn(jdbc, alumnoId);
	}

	/** "nombre|apellidos|nif|parentesco|responsable|orden|organizacion" por (orden, id). */
	private static List<String> filasEn(JdbcTemplate j, long alumnoId) {
		return j.queryForList("SELECT * FROM tutor WHERE alumno_id = ? ORDER BY orden, id", alumnoId).stream()
				.map(f -> f.get("nombre") + "|" + f.get("apellidos") + "|" + f.get("nif") + "|" + f.get("parentesco") + "|"
						+ f.get("responsable_legal") + "|" + f.get("orden") + "|" + f.get("organizacion_id"))
				.toList();
	}

	private static long alumno(JdbcTemplate j, long organizacion, int expediente, String nombreResponsable,
			String nifResponsable) {
		j.update("""
				INSERT INTO alumno (activo, apellidos, autorizacion_web, direccion, fecha_nacimiento, nombre,
				  numero_expediente, telefono, tiene_derecho_examen, organizacion_id,
				  responsable_legal_nombre, responsable_legal_nif)
				VALUES (1, 'Prueba', 1, 'Calle 1', '2014-01-01', ?, ?, '600000000', 0, ?, ?, ?)
				""", "Alumno " + expediente, expediente, organizacion, nombreResponsable, nifResponsable);
		return j.queryForObject("SELECT id FROM alumno WHERE numero_expediente = ? AND organizacion_id = ?", Long.class,
				expediente, organizacion);
	}

	private static void tutor(JdbcTemplate j, long alumnoId, String nombre, String apellidos, String nif,
			boolean responsable, int orden) {
		long org = j.queryForObject("SELECT organizacion_id FROM alumno WHERE id = ?", Long.class, alumnoId);
		String parentesco = responsable ? "TUTOR_LEGAL" : "OTRO";
		j.update("""
				INSERT INTO tutor (organizacion_id, alumno_id, nombre, apellidos, nif, parentesco, responsable_legal, orden)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?)
				""", org, alumnoId, nombre, apellidos, nif, parentesco, responsable, orden);
	}

	private static int contarTutores(JdbcTemplate j) {
		return j.queryForObject("SELECT COUNT(*) FROM tutor", Integer.class);
	}

	private static String nullable(JdbcTemplate j) {
		return j.queryForObject("""
				SELECT IS_NULLABLE FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tutor' AND COLUMN_NAME = 'nombre'""", String.class);
	}

	private static boolean existeRestriccionEn(JdbcTemplate j) {
		return j.queryForObject("""
				SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
				WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'tutor'
				  AND CONSTRAINT_NAME = 'ck_tutor_nombre_o_nif' AND CONSTRAINT_TYPE = 'CHECK'""", Integer.class) == 1;
	}

	private static List<Map<String, Object>> columnas(JdbcTemplate j) {
		return j.queryForList("""
				SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, IS_NULLABLE, COLLATION_NAME FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE() ORDER BY TABLE_NAME, ORDINAL_POSITION""");
	}

	private static boolean existeColumna(JdbcTemplate j, String tabla, String columna) {
		return j.queryForObject("""
				SELECT COUNT(*) FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?""", Integer.class, tabla, columna) == 1;
	}

	private static int versionAplicada(JdbcTemplate j, String version) {
		return j.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version = ? AND success = 1",
				Integer.class, version);
	}

	private static String mensajeCompleto(Throwable error) {
		StringBuilder sb = new StringBuilder();
		for (Throwable t = error; t != null; t = t.getCause()) {
			sb.append(t.getMessage()).append(" | ");
		}
		return sb.toString();
	}

	/** docs/multi-escuela/precheck-v29.sql: una fila de recuentos. */
	private static Map<String, Long> ejecutarPrecheck(JdbcTemplate j) throws Exception {
		Path desdeModulo = Path.of("..", "docs", "multi-escuela", "precheck-v29.sql");
		Path ruta = Files.exists(desdeModulo) ? desdeModulo : Path.of("docs", "multi-escuela", "precheck-v29.sql");
		String texto = Files.readString(ruta, StandardCharsets.UTF_8);
		List<String> consultas = java.util.Arrays.stream(texto.replaceAll("(?m)^--.*$", "").split(";"))
				.map(String::trim).filter(s -> !s.isEmpty()).toList();
		assertEquals(1, consultas.size());
		List<Map<String, Object>> filas = j.queryForList(consultas.get(0));
		assertEquals(1, filas.size());
		Map<String, Long> real = new LinkedHashMap<>();
		filas.get(0).forEach((k, valor) -> real.put(k, ((Number) valor).longValue()));
		return real;
	}

	/** V27 y V28 llegan en una sola ejecución (guarda de pruebas desactivada); V29 va en la siguiente. */
	private static Flyway flyway(DataSource fuente, String objetivo) {
		var config = Flyway.configure().dataSource(fuente).locations("classpath:db/migration")
				.placeholders(Map.of(ContraccionCatalogoMigracionIntegrationTest.PERMITIR_CON_V27, "true"));
		if (objetivo != null) {
			config.target(objetivo);
		}
		return config.load();
	}

	private static DataSource recrear(String esquema) {
		JdbcTemplate root = new JdbcTemplate(fuente(""));
		root.execute("DROP DATABASE IF EXISTS " + esquema);
		root.execute("CREATE DATABASE " + esquema + " CHARACTER SET utf8mb4");
		return fuente(esquema);
	}

	private static DataSource fuente(String esquema) {
		String url = "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306) + "/" + esquema
				+ "?useSSL=false&allowPublicKeyRetrieval=true";
		return new DriverManagerDataSource(url, "root", MYSQL.getPassword());
	}
}
