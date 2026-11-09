package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * V32_8 (rallas hacia el examen, decisiones 510-513): {@code alumno_deporte.medias_rallas} TINYINT NOT NULL DEFAULT 0 y
 * su CHECK de 0 a 10, y {@code disciplina.rallas_activas} BOOLEAN NOT NULL DEFAULT FALSE, al final de sus tablas, sobre
 * una base en V32_7 con dos escuelas y alumnos con disciplina. Todo queda en 0 y apagado y nada más cambia; el INSERT
 * sin las columnas (el código anterior) las deja en 0 y FALSE; las columnas admiten INSTANT; repetirla, también tras un
 * fallo a medias, no cambia nada y completa lo que faltara. Ejecuta {@code docs/multi-escuela/postcheck-rallas.sql}.
 * Datos inventados.
 */
@Tag("migracion")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RallasV32_8MigracionIntegrationTest extends MySqlIntegrationTest {

	private static final String ESQUEMA = "taemoi_v32_8_it";
	private static final String V32_8 = "db/migration/V32_8__rallas_examen.sql";
	private static final String MEDIAS = "medias_rallas";
	private static final String ACTIVAS = "rallas_activas";
	private static final String CHECK = "ck_alumno_deporte_medias_rallas";
	/** Todo el esquema salvo las dos columnas nuevas y el historial de Flyway (que gana su fila). */
	private static final String COLUMNAS_DEL_ESQUEMA = """
			SELECT TABLE_NAME, COLUMN_NAME, ORDINAL_POSITION, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, EXTRA,
			       CHARACTER_SET_NAME, COLLATION_NAME
			FROM information_schema.COLUMNS
			WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME <> 'flyway_schema_history'
			  AND NOT (TABLE_NAME = 'alumno_deporte' AND COLUMN_NAME = 'medias_rallas')
			  AND NOT (TABLE_NAME = 'disciplina' AND COLUMN_NAME = 'rallas_activas')
			ORDER BY TABLE_NAME, ORDINAL_POSITION""";
	private static final String INDICES_DEL_ESQUEMA = """
			SELECT TABLE_NAME, INDEX_NAME, COLUMN_NAME, NON_UNIQUE, SEQ_IN_INDEX FROM information_schema.STATISTICS
			WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME <> 'flyway_schema_history'
			ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX""";
	private static final String RESTRICCIONES_DEL_ESQUEMA = """
			SELECT TABLE_NAME, CONSTRAINT_NAME, CONSTRAINT_TYPE FROM information_schema.TABLE_CONSTRAINTS
			WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME <> 'flyway_schema_history'
			  AND CONSTRAINT_NAME <> 'ck_alumno_deporte_medias_rallas'
			ORDER BY TABLE_NAME, CONSTRAINT_NAME""";
	private static final List<String> TABLAS_CON_FILAS = List.of("alumno", "organizacion", "grado", "sistema_grados");

	DataSource dataSource;
	JdbcTemplate jdbc;
	long otra;
	long disciplinaOtra;
	List<String> columnasInscripcionAntes;
	List<String> columnasDisciplinaAntes;
	List<Map<String, Object>> columnasAntes;
	List<Map<String, Object>> indicesAntes;
	List<Map<String, Object>> restriccionesAntes;
	List<Map<String, Object>> inscripcionesAntes;
	List<Map<String, Object>> disciplinasAntes;
	Map<String, List<Map<String, Object>>> filasAntes = new LinkedHashMap<>();

	@BeforeAll
	void migrar() {
		dataSource = recrear(ESQUEMA);
		jdbc = new JdbcTemplate(dataSource);
		flyway(dataSource, "32.7").migrate();
		jdbc.update("INSERT INTO organizacion (slug, nombre) VALUES ('otra-v32-8', 'Otra escuela V32_8')");
		otra = jdbc.queryForObject("SELECT id FROM organizacion WHERE slug = 'otra-v32-8'", Long.class);
		jdbc.update("""
				INSERT INTO disciplina (organizacion_id, codigo, nombre, sistema_grados_id, edad_adulto, orden)
				VALUES (?, 'TAEKWONDO', 'Taekwondo V32_8', NULL, 14, 1)""", otra);
		disciplinaOtra = jdbc.queryForObject("SELECT id FROM disciplina WHERE organizacion_id = ?", Long.class, otra);
		inscripcion(otra, disciplinaOtra, "V328A");
		inscripcion(otra, disciplinaOtra, "V328B");
		Long disciplina1 = jdbc.queryForObject(
				"SELECT MIN(id) FROM disciplina WHERE organizacion_id = 1", Long.class);
		if (disciplina1 != null) {
			inscripcion(1L, disciplina1, "V328UNO");
		}

		columnasInscripcionAntes = columnas("alumno_deporte");
		columnasDisciplinaAntes = columnas("disciplina");
		columnasAntes = jdbc.queryForList(COLUMNAS_DEL_ESQUEMA);
		indicesAntes = jdbc.queryForList(INDICES_DEL_ESQUEMA);
		restriccionesAntes = jdbc.queryForList(RESTRICCIONES_DEL_ESQUEMA);
		inscripcionesAntes = inscripciones();
		disciplinasAntes = disciplinas();
		assertTrue(inscripcionesAntes.size() >= 2);
		assertTrue(disciplinasAntes.size() >= 2);
		for (String tabla : TABLAS_CON_FILAS) {
			filasAntes.put(tabla, jdbc.queryForList("SELECT * FROM " + tabla + " ORDER BY id"));
		}
		// Hasta V32_8: las migraciones posteriores tendrán su propio test.
		flyway(dataSource, "32.8").migrate();
	}

	@Test
	@Order(1)
	void llegaAV32_8() {
		assertEquals("32.8", jdbc.queryForObject(
				"SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1",
				String.class));
	}

	@Test
	@Order(2)
	void añadeLasColumnasAlFinal_noNulas_conSuDefecto() {
		assertEquals(con(columnasInscripcionAntes, MEDIAS), columnas("alumno_deporte"));
		assertEquals(con(columnasDisciplinaAntes, ACTIVAS), columnas("disciplina"));
		assertEquals("tinyint NO 0", definicion("alumno_deporte", MEDIAS));
		assertEquals("tinyint(1) NO 0", definicion("disciplina", ACTIVAS));
	}

	/** Solo amplía: nada del esquema cambia salvo las columnas y el CHECK; todo en 0 y apagado. */
	@Test
	@Order(3)
	void soloAmplia_todoEnCeroYApagado() {
		assertEquals(columnasAntes, jdbc.queryForList(COLUMNAS_DEL_ESQUEMA), "ninguna columna existente cambia");
		assertEquals(indicesAntes, jdbc.queryForList(INDICES_DEL_ESQUEMA), "ningún índice cambia");
		assertEquals(restriccionesAntes, jdbc.queryForList(RESTRICCIONES_DEL_ESQUEMA),
				"ninguna restricción existente cambia");
		for (String tabla : TABLAS_CON_FILAS) {
			assertEquals(filasAntes.get(tabla), jdbc.queryForList("SELECT * FROM " + tabla + " ORDER BY id"), tabla);
		}
		List<Map<String, Object>> ahora = inscripciones();
		ahora.forEach(f -> assertEquals(0, ((Number) f.get(MEDIAS)).intValue(), "inscripción " + f.get("id")));
		assertEquals(inscripcionesAntes, sin(ahora, MEDIAS));
		List<Map<String, Object>> discAhora = disciplinas();
		discAhora.forEach(f -> assertEquals(Boolean.FALSE, f.get(ACTIVAS), "disciplina " + f.get("id")));
		assertEquals(disciplinasAntes, sin(discAhora, ACTIVAS));
	}

	@Test
	@Order(4)
	void postcheck_justoTrasDesplegar() throws Exception {
		Map<String, Object> fila = postcheck().get(0).get(0);
		for (String columna : List.of("flyway_32_8", "ultima_es_32_8", "columna_medias_rallas", "check_medias_rallas",
				"columna_rallas_activas")) {
			assertEquals(1L, numero(fila, columna), columna);
		}
		for (String columna : List.of("inscripciones_con_rallas", "fuera_de_rango", "disciplinas_con_rallas")) {
			assertEquals(0L, numero(fila, columna), columna);
		}
		assertEquals((long) inscripcionesAntes.size(), numero(fila, "inscripciones"));
		assertEquals((long) disciplinasAntes.size(), numero(fila, "disciplinas"));
	}

	/** El código anterior inserta sin las columnas: quedan en 0 y FALSE; el cambio se cuenta en el postcheck. */
	@Test
	@Order(5)
	void insertarSinLasColumnas_quedaEnCeroYApagado_yElCambioSeCuenta() throws Exception {
		long nueva = inscripcion(otra, disciplinaOtra, "V328NUEVA");
		assertEquals(0, jdbc.queryForObject("SELECT " + MEDIAS + " FROM alumno_deporte WHERE alumno_id = ?",
				Integer.class, nueva));
		jdbc.update("UPDATE alumno_deporte SET " + MEDIAS + " = 3 WHERE alumno_id = ?", nueva);
		jdbc.update("UPDATE disciplina SET " + ACTIVAS + " = TRUE WHERE id = ?", disciplinaOtra);
		Map<String, Object> fila = postcheck().get(0).get(0);
		assertEquals(1L, numero(fila, "inscripciones_con_rallas"));
		assertEquals(1L, numero(fila, "disciplinas_con_rallas"));
		jdbc.update("DELETE FROM alumno_deporte WHERE alumno_id = ?", nueva);
		jdbc.update("DELETE FROM alumno WHERE id = ?", nueva);
		jdbc.update("UPDATE disciplina SET " + ACTIVAS + " = FALSE WHERE id = ?", disciplinaOtra);
		fila = postcheck().get(0).get(0);
		assertEquals(0L, numero(fila, "inscripciones_con_rallas"));
		assertEquals(0L, numero(fila, "disciplinas_con_rallas"));
	}

	@Test
	@Order(6)
	void elCheck_soloDejaDe0a10() {
		for (String valido : List.of("0", "1", "9", "10")) {
			medias(valido);
		}
		for (String invalido : List.of("-1", "11", "127", "NULL")) {
			assertThrows(DataAccessException.class, () -> medias(invalido), invalido);
		}
		medias("0");
	}

	/** Las afirmaciones de coste, en el MySQL de los tests (8.0, como producción). */
	@Test
	@Order(7)
	void lasColumnasSonInstant_yElCheckSeHaceConCopia() {
		jdbc.execute("ALTER TABLE alumno_deporte DROP CHECK " + CHECK);
		jdbc.execute("ALTER TABLE alumno_deporte DROP COLUMN " + MEDIAS);
		jdbc.execute("ALTER TABLE disciplina DROP COLUMN " + ACTIVAS);
		jdbc.execute("ALTER TABLE alumno_deporte ADD COLUMN " + MEDIAS
				+ " TINYINT NOT NULL DEFAULT 0, ALGORITHM=INSTANT");
		jdbc.execute("ALTER TABLE disciplina ADD COLUMN " + ACTIVAS
				+ " BOOLEAN NOT NULL DEFAULT FALSE, ALGORITHM=INSTANT");
		String expresion = "CHECK (" + MEDIAS + " BETWEEN 0 AND 10)";
		DataAccessException sinCopia = assertThrows(DataAccessException.class, () -> jdbc.execute(
				"ALTER TABLE alumno_deporte ADD CONSTRAINT " + CHECK + " " + expresion + ", ALGORITHM=INPLACE"));
		assertTrue(sinCopia.getMessage().contains("ALGORITHM=INPLACE is not supported"), sinCopia.getMessage());
		jdbc.execute("ALTER TABLE alumno_deporte ADD CONSTRAINT " + CHECK + " " + expresion + ", ALGORITHM=COPY");
		assertEquals(con(columnasInscripcionAntes, MEDIAS), columnas("alumno_deporte"));
		assertEquals(con(columnasDisciplinaAntes, ACTIVAS), columnas("disciplina"));
	}

	@Test
	@Order(8)
	void repetirla_conservaLoGuardado() throws Exception {
		medias("7");
		jdbc.update("UPDATE disciplina SET " + ACTIVAS + " = TRUE WHERE id = ?", disciplinaOtra);
		List<Map<String, Object>> antes = inscripciones();
		List<Map<String, Object>> discAntes = disciplinas();
		ejecutarV32_8();
		ejecutarV32_8();
		assertEquals(antes, inscripciones());
		assertEquals(discAntes, disciplinas());
		assertEquals(1L, numero(postcheck().get(0).get(0), "check_medias_rallas"));
		medias("0");
		jdbc.update("UPDATE disciplina SET " + ACTIVAS + " = FALSE WHERE id = ?", disciplinaOtra);
	}

	/** El DDL no es transaccional: se puede quedar a medias tras cada sentencia. Volver a ejecutarla lo completa. */
	@Test
	@Order(9)
	void trasUnFalloAMedias_laCompleta() throws Exception {
		// Solo faltaba la columna de disciplina.
		jdbc.execute("ALTER TABLE disciplina DROP COLUMN " + ACTIVAS);
		ejecutarV32_8();
		assertEquals(1L, numero(postcheck().get(0).get(0), "columna_rallas_activas"));

		// Faltaban el CHECK y la columna de disciplina.
		jdbc.execute("ALTER TABLE alumno_deporte DROP CHECK " + CHECK);
		jdbc.execute("ALTER TABLE disciplina DROP COLUMN " + ACTIVAS);
		ejecutarV32_8();
		Map<String, Object> fila = postcheck().get(0).get(0);
		assertEquals(1L, numero(fila, "check_medias_rallas"));
		assertEquals(1L, numero(fila, "columna_rallas_activas"));

		// No había llegado a hacer nada.
		jdbc.execute("ALTER TABLE alumno_deporte DROP CHECK " + CHECK);
		jdbc.execute("ALTER TABLE alumno_deporte DROP COLUMN " + MEDIAS);
		jdbc.execute("ALTER TABLE disciplina DROP COLUMN " + ACTIVAS);
		ejecutarV32_8();
		ejecutarV32_8();
		assertEquals(con(columnasInscripcionAntes, MEDIAS), columnas("alumno_deporte"));
		assertEquals(con(columnasDisciplinaAntes, ACTIVAS), columnas("disciplina"));
		fila = postcheck().get(0).get(0);
		for (String columna : List.of("columna_medias_rallas", "check_medias_rallas", "columna_rallas_activas")) {
			assertEquals(1L, numero(fila, columna), columna);
		}
		assertEquals(0L, numero(fila, "inscripciones_con_rallas"));
		assertEquals(0L, numero(fila, "disciplinas_con_rallas"));
		assertEquals(inscripcionesAntes, sin(inscripciones(), MEDIAS));
		assertThrows(DataAccessException.class, () -> medias("11"), "el CHECK sigue activo");
	}

	// ------------------------------------------------------------------ utilidades

	/** Un alumno de esa escuela con una inscripción en esa disciplina, insertados sin las columnas nuevas. */
	private long inscripcion(long organizacion, long disciplina, String nombre) {
		long expediente = jdbc.queryForObject("SELECT COALESCE(MAX(numero_expediente), 70000) + 1 FROM alumno",
				Long.class);
		jdbc.update("""
				INSERT INTO alumno (activo, apellidos, autorizacion_web, direccion, fecha_nacimiento, nombre,
				  numero_expediente, telefono, tiene_derecho_examen, organizacion_id)
				VALUES (TRUE, 'PRUEBA', 1, 'Calle 1', '2012-01-01', ?, ?, '600000000', 0, ?)""", nombre, expediente,
				organizacion);
		long alumno = jdbc.queryForObject("SELECT id FROM alumno WHERE numero_expediente = ? AND organizacion_id = ?",
				Long.class, expediente, organizacion);
		jdbc.update("""
				INSERT INTO alumno_deporte (activo, apto_para_examen, alumno_id, competidor, principal, tiene_licencia,
				  organizacion_id, fecha_alta, fecha_alta_inicial, disciplina_id)
				VALUES (TRUE, FALSE, ?, 0, 1, 0, ?, '2024-09-01', '2024-09-01', ?)""", alumno, organizacion, disciplina);
		return alumno;
	}

	/** Cambia las medias rallas de todas las inscripciones de la otra escuela. */
	private void medias(String valor) {
		jdbc.update("UPDATE alumno_deporte SET " + MEDIAS + " = " + valor + " WHERE organizacion_id = ?", otra);
	}

	private void ejecutarV32_8() throws Exception {
		try (Connection conexion = dataSource.getConnection()) {
			ScriptUtils.executeSqlScript(conexion,
					new EncodedResource(new ClassPathResource(V32_8), StandardCharsets.UTF_8));
		}
	}

	private List<Map<String, Object>> inscripciones() {
		return jdbc.queryForList("SELECT * FROM alumno_deporte ORDER BY id");
	}

	private List<Map<String, Object>> disciplinas() {
		return jdbc.queryForList("SELECT * FROM disciplina ORDER BY id");
	}

	private static List<Map<String, Object>> sin(List<Map<String, Object>> filas, String columna) {
		return filas.stream().map(f -> {
			Map<String, Object> copia = new LinkedHashMap<>(f);
			copia.remove(columna);
			return copia;
		}).toList();
	}

	private static List<String> con(List<String> antes, String nueva) {
		List<String> esperadas = new ArrayList<>(antes);
		esperadas.add(nueva);
		return esperadas;
	}

	private List<String> columnas(String tabla) {
		return jdbc.queryForList("""
				SELECT COLUMN_NAME FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? ORDER BY ORDINAL_POSITION""", String.class, tabla);
	}

	private String definicion(String tabla, String columna) {
		return jdbc.queryForObject("""
				SELECT CONCAT(COLUMN_TYPE, ' ', IS_NULLABLE, ' ', COLUMN_DEFAULT) FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?""", String.class, tabla, columna);
	}

	private List<List<Map<String, Object>>> postcheck() throws Exception {
		return SedesVinculosMigracionIntegrationTest.ejecutarScriptDocs(dataSource, "postcheck-rallas.sql");
	}

	private static long numero(Map<String, Object> fila, String columna) {
		return ((Number) fila.get(columna)).longValue();
	}

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
		return new DriverManagerDataSource("jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306) + "/"
				+ esquema + "?useSSL=false&allowPublicKeyRetrieval=true", "root", MYSQL.getPassword());
	}
}
