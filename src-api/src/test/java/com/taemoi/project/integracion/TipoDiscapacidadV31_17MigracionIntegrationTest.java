package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * V31_17: {@code alumno_salud} gana {@code discapacidad_tipos} y {@code discapacidad_otra}, sobre una base en V31_16 con
 * salud y discapacidad en dos escuelas. Solo amplía: ninguna columna, índice, restricción ni fila existente cambia y no
 * rellena nada. Repetirla, también tras un fallo a medias. Ejecuta {@code docs/multi-escuela/postcheck-v31-17.sql}. Datos
 * inventados.
 */
@Tag("migracion")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TipoDiscapacidadV31_17MigracionIntegrationTest extends MySqlIntegrationTest {

	private static final String ESQUEMA = "taemoi_v31_17_it";
	private static final String V31_17 = "db/migration/V31_17__tipo_discapacidad.sql";
	private static final String COLUMNAS_DEL_ESQUEMA = """
			SELECT TABLE_NAME, COLUMN_NAME, ORDINAL_POSITION, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, EXTRA,
			       CHARACTER_SET_NAME, COLLATION_NAME
			FROM information_schema.COLUMNS
			WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME <> 'flyway_schema_history'
			  AND NOT (TABLE_NAME = 'alumno_salud' AND COLUMN_NAME IN ('discapacidad_tipos', 'discapacidad_otra'))
			ORDER BY TABLE_NAME, ORDINAL_POSITION""";
	private static final String INDICES_DEL_ESQUEMA = """
			SELECT TABLE_NAME, INDEX_NAME, COLUMN_NAME, NON_UNIQUE, SEQ_IN_INDEX FROM information_schema.STATISTICS
			WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME <> 'flyway_schema_history'
			ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX""";
	private static final String RESTRICCIONES_DEL_ESQUEMA = """
			SELECT TABLE_NAME, CONSTRAINT_NAME, CONSTRAINT_TYPE FROM information_schema.TABLE_CONSTRAINTS
			WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME <> 'flyway_schema_history'
			ORDER BY TABLE_NAME, CONSTRAINT_NAME""";
	private static final String FILAS_SALUD = """
			SELECT alumno_id, organizacion_id, lesion_enfermedad, descripcion, impide_actividad, impide_detalle, alergias,
			       origen, actualizado_en
			FROM alumno_salud ORDER BY alumno_id""";
	private static final String FILAS_ALUMNO = "SELECT * FROM alumno ORDER BY id";

	DataSource dataSource;
	JdbcTemplate jdbc;
	long otra;
	long conFilaDe1, sinFilaDe1, conFilaDeOtra;
	List<Map<String, Object>> columnasAntes;
	List<Map<String, Object>> indicesAntes;
	List<Map<String, Object>> restriccionesAntes;
	Map<String, List<Map<String, Object>>> filasAntes = new LinkedHashMap<>();

	@BeforeAll
	void migrar() {
		dataSource = recrear(ESQUEMA);
		jdbc = new JdbcTemplate(dataSource);
		flyway(dataSource, "31.16").migrate();

		jdbc.update("INSERT INTO organizacion (slug, nombre) VALUES ('otra-v31-17', 'Otra escuela V31_17')");
		otra = jdbc.queryForObject("SELECT id FROM organizacion WHERE slug = 'otra-v31-17'", Long.class);
		conFilaDe1 = alumno(1L, 910001, true);
		sinFilaDe1 = alumno(1L, 910002, false);
		conFilaDeOtra = alumno(otra, 910003, false);
		salud(conFilaDe1, 1L, "v1:texto-cifrado-ficticio-1");
		salud(conFilaDeOtra, otra, "v1:texto-cifrado-ficticio-2");

		columnasAntes = jdbc.queryForList(COLUMNAS_DEL_ESQUEMA);
		indicesAntes = jdbc.queryForList(INDICES_DEL_ESQUEMA);
		restriccionesAntes = jdbc.queryForList(RESTRICCIONES_DEL_ESQUEMA);
		filasAntes.put(FILAS_SALUD, jdbc.queryForList(FILAS_SALUD));
		filasAntes.put(FILAS_ALUMNO, jdbc.queryForList(FILAS_ALUMNO));

		flyway(dataSource, "31.17").migrate();
	}

	@Test
	@Order(1)
	void llegaAV31_17() {
		assertEquals("31.17", jdbc.queryForObject(
				"SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1",
				String.class));
	}

	@Test
	@Order(2)
	void anadeLasDosColumnas_textoYOpcionales_alFinal() {
		assertEquals(List.of("discapacidad_tipos text YES", "discapacidad_otra text YES"), columnasNuevas());
		assertEquals(List.of("discapacidad_tipos", "discapacidad_otra"), jdbc.queryForList("""
				SELECT COLUMN_NAME FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'alumno_salud' ORDER BY ORDINAL_POSITION DESC LIMIT 2""",
				String.class).reversed());
		assertEquals(0, jdbc.queryForObject("""
				SELECT COUNT(*) FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'alumno_salud' AND COLUMN_DEFAULT IS NOT NULL
				  AND COLUMN_NAME IN ('discapacidad_tipos', 'discapacidad_otra')""", Integer.class));
	}

	@Test
	@Order(3)
	void soloAmplia() {
		assertEquals(columnasAntes, jdbc.queryForList(COLUMNAS_DEL_ESQUEMA), "ninguna columna existente cambia");
		assertEquals(indicesAntes, jdbc.queryForList(INDICES_DEL_ESQUEMA), "ningún índice cambia");
		assertEquals(restriccionesAntes, jdbc.queryForList(RESTRICCIONES_DEL_ESQUEMA), "ninguna restricción cambia");
		filasAntes.forEach((consulta, filas) -> assertEquals(filas, jdbc.queryForList(consulta), consulta));
	}

	@Test
	@Order(4)
	void noRellenaNada() {
		assertEquals(0, jdbc.queryForObject("""
				SELECT COUNT(*) FROM alumno_salud WHERE discapacidad_tipos IS NOT NULL OR discapacidad_otra IS NOT NULL""",
				Integer.class));
	}

	@Test
	@Order(5)
	void postcheck_justoTrasDesplegar() throws Exception {
		Map<String, Object> fila = postcheck();
		assertEquals(1L, numero(fila, "flyway_31_17"));
		assertEquals(1L, numero(fila, "ultima_es_31_17"));
		assertEquals(2L, numero(fila, "columnas_nuevas"));
		assertEquals(2L, numero(fila, "filas_alumno_salud"));
		assertEquals(0L, numero(fila, "filas_con_tipos"));
		assertEquals(1L, numero(fila, "alumnos_con_discapacidad"));
		assertEquals(0L, numero(fila, "tipos_sin_discapacidad"));
	}

	@Test
	@Order(6)
	void postcheck_cuentaFilasConTiposYTiposHuerfanos() throws Exception {
		jdbc.update("UPDATE alumno_salud SET discapacidad_tipos = 'v1:ficticio' WHERE alumno_id IN (?, ?)", conFilaDe1,
				conFilaDeOtra);

		Map<String, Object> fila = postcheck();

		assertEquals(2L, numero(fila, "filas_con_tipos"));
		assertEquals(1L, numero(fila, "tipos_sin_discapacidad"), "el alumno de la otra escuela no tiene discapacidad");
		jdbc.update("UPDATE alumno_salud SET discapacidad_tipos = NULL");
	}

	@Test
	@Order(7)
	void repetirla_conservaLoGuardado() throws Exception {
		jdbc.update("UPDATE alumno_salud SET discapacidad_tipos = 'v1:ficticio', discapacidad_otra = 'v1:otro'"
				+ " WHERE alumno_id = ?", conFilaDe1);

		ejecutarV31_17();
		ejecutarV31_17();

		assertEquals(1, jdbc.queryForObject("""
				SELECT COUNT(*) FROM alumno_salud
				WHERE alumno_id = ? AND discapacidad_tipos = 'v1:ficticio' AND discapacidad_otra = 'v1:otro'""",
				Integer.class, conFilaDe1));
		jdbc.update("UPDATE alumno_salud SET discapacidad_tipos = NULL, discapacidad_otra = NULL");
	}

	/** Si falló tras añadir solo una, volver a ejecutarla añade la que falta. */
	@Test
	@Order(8)
	void trasUnFalloAMedias_laCompleta() throws Exception {
		jdbc.execute("ALTER TABLE alumno_salud DROP COLUMN discapacidad_otra");

		ejecutarV31_17();

		assertEquals(List.of("discapacidad_tipos text YES", "discapacidad_otra text YES"), columnasNuevas());
	}

	// ------------------------------------------------------------------ utilidades

	private long alumno(long organizacion, long expediente, boolean discapacidad) {
		jdbc.update("""
				INSERT INTO alumno (activo, apellidos, autorizacion_web, direccion, fecha_nacimiento, nombre,
				  numero_expediente, telefono, tiene_derecho_examen, tiene_discapacidad, organizacion_id)
				VALUES (1, 'PRUEBA', 1, 'Calle 1', '2010-01-01', 'FICTICIO', ?, '600000000', 0, ?, ?)""",
				expediente, discapacidad, organizacion);
		return jdbc.queryForObject("SELECT id FROM alumno WHERE numero_expediente = ? AND organizacion_id = ?",
				Long.class, expediente, organizacion);
	}

	private void salud(long alumno, long organizacion, String alergias) {
		jdbc.update("""
				INSERT INTO alumno_salud (alumno_id, organizacion_id, alergias, origen, actualizado_en)
				VALUES (?, ?, ?, 'APP', '2026-10-01 10:00:00')""", alumno, organizacion, alergias);
	}

	private List<String> columnasNuevas() {
		return jdbc.queryForList("""
				SELECT CONCAT(COLUMN_NAME, ' ', COLUMN_TYPE, ' ', IS_NULLABLE) FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'alumno_salud'
				  AND COLUMN_NAME IN ('discapacidad_tipos', 'discapacidad_otra')
				ORDER BY ORDINAL_POSITION""", String.class);
	}

	private Map<String, Object> postcheck() throws Exception {
		return SedesVinculosMigracionIntegrationTest.ejecutarScriptDocs(dataSource, "postcheck-v31-17.sql").get(0).get(0);
	}

	private static long numero(Map<String, Object> fila, String columna) {
		return ((Number) fila.get(columna)).longValue();
	}

	private void ejecutarV31_17() throws Exception {
		try (Connection conexion = dataSource.getConnection()) {
			ScriptUtils.executeSqlScript(conexion,
					new EncodedResource(new ClassPathResource(V31_17), StandardCharsets.UTF_8));
		}
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
