package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * V32_2 (decisiones 455-463): {@code asistencia_alumno.motivo_falta} y las tablas {@code festivo} y {@code festivo_sede},
 * sobre una base en V32_1 con una lista guardada. Solo amplía: ninguna columna, índice, restricción ni fila existente
 * cambia (las filas de asistencia quedan con motivo NULL). Reglas de borrado de cada clave ajena, repetirla, también tras
 * un fallo a medias, y {@code docs/multi-escuela/postcheck-asistencia-motivos-festivos.sql}. Datos inventados.
 */
@Tag("migracion")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AsistenciaMotivosFestivosV32_2MigracionIntegrationTest extends MySqlIntegrationTest {

	private static final String ESQUEMA = "taemoi_v32_2_it";
	private static final String V32_2 = "db/migration/V32_2__asistencia_motivos_festivos.sql";
	private static final String NUEVAS = "('flyway_schema_history', 'festivo', 'festivo_sede')";
	/** Todo el esquema salvo las tablas nuevas, el historial de Flyway y la columna nueva. */
	private static final String COLUMNAS_DEL_ESQUEMA = """
			SELECT TABLE_NAME, COLUMN_NAME, ORDINAL_POSITION, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, EXTRA,
			       CHARACTER_SET_NAME, COLLATION_NAME
			FROM information_schema.COLUMNS
			WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME NOT IN %s
			  AND NOT (TABLE_NAME = 'asistencia_alumno' AND COLUMN_NAME = 'motivo_falta')
			ORDER BY TABLE_NAME, ORDINAL_POSITION""".formatted(NUEVAS);
	private static final String INDICES_DEL_ESQUEMA = """
			SELECT TABLE_NAME, INDEX_NAME, COLUMN_NAME, NON_UNIQUE, SEQ_IN_INDEX FROM information_schema.STATISTICS
			WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME NOT IN %s
			ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX""".formatted(NUEVAS);
	private static final String RESTRICCIONES_DEL_ESQUEMA = """
			SELECT TABLE_NAME, CONSTRAINT_NAME, CONSTRAINT_TYPE FROM information_schema.TABLE_CONSTRAINTS
			WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME NOT IN %s
			ORDER BY TABLE_NAME, CONSTRAINT_NAME""".formatted(NUEVAS);
	private static final Map<String, String> FILAS = Map.of(
			"organizacion", "SELECT * FROM organizacion ORDER BY id",
			"sede", "SELECT * FROM sede ORDER BY id",
			"grupo", "SELECT * FROM grupo ORDER BY id",
			"clase_asistencia", "SELECT * FROM clase_asistencia ORDER BY id",
			"asistencia_alumno", "SELECT id, organizacion_id, clase_id, alumno_id, presente FROM asistencia_alumno ORDER BY id");

	DataSource dataSource;
	JdbcTemplate jdbc;
	long otra, disciplina, sede, sede2, grupo, turno, alumno, cuenta;
	List<Map<String, Object>> columnasAntes;
	List<Map<String, Object>> indicesAntes;
	List<Map<String, Object>> restriccionesAntes;
	Map<String, List<Map<String, Object>>> filasAntes = new LinkedHashMap<>();

	@BeforeAll
	void migrar() {
		dataSource = recrear(ESQUEMA);
		jdbc = new JdbcTemplate(dataSource);
		flyway(dataSource, "32.1").migrate();

		jdbc.update("INSERT INTO organizacion (slug, nombre) VALUES ('otra-v32-2', 'Otra escuela V32_2')");
		otra = jdbc.queryForObject("SELECT id FROM organizacion WHERE slug = 'otra-v32-2'", Long.class);
		jdbc.update("""
				INSERT INTO disciplina (organizacion_id, codigo, nombre, sistema_grados_id, edad_adulto, orden)
				VALUES (?, 'TAEKWONDO', 'Taekwondo V32_2', NULL, 14, 1)""", otra);
		disciplina = jdbc.queryForObject("SELECT id FROM disciplina WHERE organizacion_id = ?", Long.class, otra);
		sede = sede("V32_2 sede");
		sede2 = sede("V32_2 sede dos");
		jdbc.update("INSERT INTO grupo (nombre, organizacion_id, disciplina_id, sede_id) VALUES ('V32_2 grupo', ?, ?, ?)",
				otra, disciplina, sede);
		grupo = jdbc.queryForObject("SELECT MAX(id) FROM grupo WHERE organizacion_id = ?", Long.class, otra);
		jdbc.update("""
				INSERT INTO turno (dia_semana, hora_inicio, hora_fin, tipo, grupo_id, organizacion_id)
				VALUES ('Lunes', '18:00', '19:00', NULL, ?, ?)""", grupo, otra);
		turno = jdbc.queryForObject("SELECT MAX(id) FROM turno WHERE organizacion_id = ?", Long.class, otra);
		jdbc.update("""
				INSERT INTO alumno (activo, apellidos, autorizacion_web, direccion, fecha_nacimiento, nombre,
				  numero_expediente, telefono, tiene_derecho_examen, organizacion_id)
				VALUES (1, 'PRUEBA', 1, 'Calle 1', '2015-01-01', 'V32_2', 62001, '600000000', 0, ?)""", otra);
		alumno = jdbc.queryForObject("SELECT id FROM alumno WHERE numero_expediente = 62001 AND organizacion_id = ?",
				Long.class, otra);
		jdbc.update("""
				INSERT INTO usuario (apellidos, contrasena, email, nombre, auth_provider)
				VALUES ('Prueba', 'fake-Password-123', 'v32-2-gestor@example.test', 'Cuenta', 'LOCAL')""");
		cuenta = jdbc.queryForObject("SELECT id FROM usuario WHERE email = 'v32-2-gestor@example.test'", Long.class);
		jdbc.update("""
				INSERT INTO clase_asistencia (organizacion_id, grupo_id, turno_id, fecha, hora_inicio, hora_fin, registrado_en)
				VALUES (?, ?, ?, '2026-10-26', '18:00', '19:00', '2026-10-26 19:00:00')""", otra, grupo, turno);
		long clase = jdbc.queryForObject("SELECT MAX(id) FROM clase_asistencia", Long.class);
		jdbc.update("INSERT INTO asistencia_alumno (organizacion_id, clase_id, alumno_id, presente) VALUES (?, ?, ?, FALSE)",
				otra, clase, alumno);

		columnasAntes = jdbc.queryForList(COLUMNAS_DEL_ESQUEMA);
		indicesAntes = jdbc.queryForList(INDICES_DEL_ESQUEMA);
		restriccionesAntes = jdbc.queryForList(RESTRICCIONES_DEL_ESQUEMA);
		FILAS.forEach((tabla, consulta) -> filasAntes.put(tabla, jdbc.queryForList(consulta)));
		// Hasta V32_2: las migraciones posteriores tendrán su propio test.
		flyway(dataSource, "32.2").migrate();
	}

	@Test
	@Order(1)
	void llegaAV32_2() {
		assertEquals("32.2", jdbc.queryForObject(
				"SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1",
				String.class));
	}

	@Test
	@Order(2)
	void columnaYTablas_conLasColumnasPedidas() {
		assertEquals(List.of("id bigint NO", "organizacion_id bigint NO", "clase_id bigint NO", "alumno_id bigint NO",
				"presente bit(1) NO", "motivo_falta varchar(20) YES"), columnas("asistencia_alumno"));
		assertEquals(List.of("id bigint NO", "organizacion_id bigint NO", "tipo varchar(10) NO", "fecha_desde date YES",
				"fecha_hasta date YES", "mes tinyint YES", "dia tinyint YES", "nombre varchar(60) YES",
				"creado_por_usuario_id bigint YES", "creado_en datetime NO", "modificado_por_usuario_id bigint YES",
				"modificado_en datetime YES"), columnas("festivo"));
		assertEquals(List.of("festivo_id bigint NO", "sede_id bigint NO", "organizacion_id bigint NO"),
				columnas("festivo_sede"));
		assertEquals(List.of("festivo_id,sede_id"), indice("festivo_sede", "PRIMARY", 0));
		assertEquals(List.of("sede_id"), indice("festivo_sede", "idx_festivo_sede_sede", 1));
		assertEquals(List.of("organizacion_id,fecha_hasta"), indice("festivo", "idx_festivo_organizacion_fecha_hasta", 1));
	}

	/** Solo amplía: nada del esquema cambia salvo lo nuevo, las filas siguen igual y los motivos quedan NULL. */
	@Test
	@Order(3)
	void soloAmplia() {
		assertEquals(columnasAntes, jdbc.queryForList(COLUMNAS_DEL_ESQUEMA), "ninguna columna existente cambia");
		assertEquals(indicesAntes, jdbc.queryForList(INDICES_DEL_ESQUEMA), "ningún índice cambia");
		assertEquals(restriccionesAntes, jdbc.queryForList(RESTRICCIONES_DEL_ESQUEMA),
				"ninguna restricción existente cambia");
		FILAS.forEach((tabla, consulta) -> assertEquals(filasAntes.get(tabla), jdbc.queryForList(consulta), tabla));
		assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM asistencia_alumno WHERE motivo_falta IS NOT NULL",
				Long.class));
		assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM festivo", Long.class));
		assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM festivo_sede", Long.class));
	}

	@Test
	@Order(4)
	void postcheck_justoTrasDesplegar() throws Exception {
		Map<String, Object> fila = postcheck();
		for (String columna : List.of("flyway_32_2", "ultima_es_32_2", "columna_motivo")) {
			assertEquals(1L, numero(fila, columna), columna);
		}
		assertEquals(2L, numero(fila, "tablas"));
		assertEquals(6L, numero(fila, "claves_ajenas"));
		for (String columna : List.of("faltas_con_motivo", "presentes_con_motivo", "motivos_desconocidos", "festivos",
				"festivos_sin_sede", "sedes_de_otra_escuela", "festivos_con_forma_rara")) {
			assertEquals(0L, numero(fila, columna), columna);
		}
		assertEquals(1L, numero(fila, "filas_de_asistencia"));
	}

	/** Borrar el festivo o la sede se lleva sus filas de festivo_sede; borrar la cuenta deja el festivo sin usuario. */
	@Test
	@Order(5)
	void reglasDeBorrado() {
		long festivo = festivo("DIA", "'2026-12-08'", "'2026-12-08'", "NULL", "NULL", cuenta);
		festivoSede(festivo, sede);
		festivoSede(festivo, sede2);
		assertThrows(DataIntegrityViolationException.class, () -> festivoSede(festivo, sede), "PK (festivo, sede)");

		jdbc.update("DELETE FROM sede WHERE id = ?", sede2);
		assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM festivo_sede WHERE festivo_id = ?", Long.class, festivo));

		jdbc.update("UPDATE festivo SET modificado_por_usuario_id = ?, modificado_en = '2026-11-06 10:00:00' WHERE id = ?",
				cuenta, festivo);
		jdbc.update("DELETE FROM usuario WHERE id = ?", cuenta);
		assertEquals(2L, jdbc.queryForObject("""
				SELECT (creado_por_usuario_id IS NULL) + (modificado_por_usuario_id IS NULL) FROM festivo WHERE id = ?""",
				Long.class, festivo));

		jdbc.update("DELETE FROM festivo WHERE id = ?", festivo);
		assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM festivo_sede", Long.class), "cascada");
	}

	@Test
	@Order(6)
	void repetirla_conservaLoGuardado() throws Exception {
		jdbc.update("UPDATE asistencia_alumno SET motivo_falta = 'MEDICA'");
		long festivo = festivo("ANUAL", "NULL", "NULL", "12", "25", null);
		festivoSede(festivo, sede);
		ejecutarV32_2();
		ejecutarV32_2();
		assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM asistencia_alumno WHERE motivo_falta = 'MEDICA'",
				Long.class));
		assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM festivo_sede WHERE festivo_id = ?", Long.class, festivo));
		jdbc.update("DELETE FROM festivo WHERE id = ?", festivo);
		jdbc.update("UPDATE asistencia_alumno SET motivo_falta = NULL");
	}

	/** Si falló a medias, volver a ejecutarla crea lo que falta. */
	@Test
	@Order(7)
	void trasUnFalloAMedias_laCompleta() throws Exception {
		jdbc.execute("DROP TABLE festivo_sede");
		ejecutarV32_2();
		assertEquals(2L, numero(postcheck(), "tablas"));
		assertEquals(6L, numero(postcheck(), "claves_ajenas"));

		jdbc.execute("DROP TABLE festivo_sede, festivo");
		jdbc.execute("ALTER TABLE asistencia_alumno DROP COLUMN motivo_falta");
		ejecutarV32_2();
		Map<String, Object> fila = postcheck();
		assertEquals(1L, numero(fila, "columna_motivo"));
		assertEquals(2L, numero(fila, "tablas"));
		assertEquals(6L, numero(fila, "claves_ajenas"));
	}

	/** Las comprobaciones «SIEMPRE 0» avisan de datos que el código no debe escribir nunca. */
	@Test
	@Order(8)
	void postcheck_avisaDeDatosRaros() throws Exception {
		jdbc.update("UPDATE asistencia_alumno SET presente = TRUE, motivo_falta = 'OTRO'");
		long sinSede = festivo("DIA", "'2026-12-08'", "'2026-12-09'", "NULL", "NULL", null);
		long anualSinDia = festivo("ANUAL", "NULL", "NULL", "12", "NULL", null);
		festivoSede(anualSinDia, sede);
		Map<String, Object> fila = postcheck();
		assertEquals(1L, numero(fila, "presentes_con_motivo"));
		assertEquals(1L, numero(fila, "motivos_desconocidos"));
		assertEquals(1L, numero(fila, "festivos_sin_sede"));
		assertEquals(2L, numero(fila, "festivos_con_forma_rara"));
		jdbc.update("DELETE FROM festivo WHERE id IN (?, ?)", sinSede, anualSinDia);
		jdbc.update("UPDATE asistencia_alumno SET presente = FALSE, motivo_falta = NULL");
	}

	// ------------------------------------------------------------------ utilidades

	private long sede(String nombre) {
		jdbc.update("INSERT INTO sede (organizacion_id, nombre) VALUES (?, ?)", otra, nombre);
		return jdbc.queryForObject("SELECT id FROM sede WHERE organizacion_id = ? AND nombre = ?", Long.class, otra,
				nombre);
	}

	private long festivo(String tipo, String desde, String hasta, String mes, String dia, Long creadoPor) {
		jdbc.update("INSERT INTO festivo (organizacion_id, tipo, fecha_desde, fecha_hasta, mes, dia, creado_por_usuario_id,"
				+ " creado_en) VALUES (?, ?, " + desde + ", " + hasta + ", " + mes + ", " + dia + ", ?, '2026-11-06 09:00:00')",
				otra, tipo, creadoPor);
		return jdbc.queryForObject("SELECT MAX(id) FROM festivo", Long.class);
	}

	private void festivoSede(long festivo, long sedeId) {
		jdbc.update("INSERT INTO festivo_sede (festivo_id, sede_id, organizacion_id) VALUES (?, ?, ?)", festivo, sedeId,
				otra);
	}

	private List<String> columnas(String tabla) {
		return jdbc.queryForList("""
				SELECT CONCAT(COLUMN_NAME, ' ', COLUMN_TYPE, ' ', IS_NULLABLE) FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? ORDER BY ORDINAL_POSITION""", String.class, tabla);
	}

	private List<String> indice(String tabla, String indice, int noUnico) {
		return jdbc.queryForList("""
				SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) FROM information_schema.STATISTICS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ? AND NON_UNIQUE = ?
				GROUP BY INDEX_NAME""", String.class, tabla, indice, noUnico);
	}

	private void ejecutarV32_2() throws Exception {
		try (Connection conexion = dataSource.getConnection()) {
			ScriptUtils.executeSqlScript(conexion,
					new EncodedResource(new ClassPathResource(V32_2), StandardCharsets.UTF_8));
		}
	}

	private Map<String, Object> postcheck() throws Exception {
		return SedesVinculosMigracionIntegrationTest
				.ejecutarScriptDocs(dataSource, "postcheck-asistencia-motivos-festivos.sql").get(0).get(0);
	}

	private static long numero(Map<String, Object> fila, String columna) {
		return ((Number) fila.get(columna)).longValue();
	}

	private static Flyway flyway(DataSource fuente, String objetivo) {
		return Flyway.configure().dataSource(fuente).locations("classpath:db/migration")
				.placeholders(Map.of(ContraccionCatalogoMigracionIntegrationTest.PERMITIR_CON_V27, "true"))
				.target(objetivo).load();
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
