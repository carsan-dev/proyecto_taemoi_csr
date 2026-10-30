package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
 * V31_15 (decisión 324): {@code clase_asistencia} y {@code asistencia_alumno}, sobre una base en V31_14 con turnos,
 * grupos, alumnos y cuentas de dos escuelas. Solo amplía: ninguna columna, índice, restricción ni fila existente cambia.
 * Reglas de borrado de cada clave ajena, UNIQUE (también con varias filas de turno borrado) y repetirla, también tras un
 * fallo a medias. Ejecuta {@code docs/multi-escuela/postcheck-pasar-lista.sql}. Datos inventados.
 */
@Tag("migracion")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AsistenciaV31_15MigracionIntegrationTest extends MySqlIntegrationTest {

	private static final String ESQUEMA = "taemoi_v31_15_it";
	private static final String V31_15 = "db/migration/V31_15__asistencia.sql";
	/** Todo el esquema salvo las tablas nuevas y el historial de Flyway (que gana su fila). */
	private static final String COLUMNAS_DEL_ESQUEMA = """
			SELECT TABLE_NAME, COLUMN_NAME, ORDINAL_POSITION, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, EXTRA,
			       CHARACTER_SET_NAME, COLLATION_NAME
			FROM information_schema.COLUMNS
			WHERE TABLE_SCHEMA = DATABASE()
			  AND TABLE_NAME NOT IN ('flyway_schema_history', 'clase_asistencia', 'asistencia_alumno')
			ORDER BY TABLE_NAME, ORDINAL_POSITION""";
	private static final String INDICES_DEL_ESQUEMA = """
			SELECT TABLE_NAME, INDEX_NAME, COLUMN_NAME, NON_UNIQUE, SEQ_IN_INDEX FROM information_schema.STATISTICS
			WHERE TABLE_SCHEMA = DATABASE()
			  AND TABLE_NAME NOT IN ('flyway_schema_history', 'clase_asistencia', 'asistencia_alumno')
			ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX""";
	private static final String RESTRICCIONES_DEL_ESQUEMA = """
			SELECT TABLE_NAME, CONSTRAINT_NAME, CONSTRAINT_TYPE FROM information_schema.TABLE_CONSTRAINTS
			WHERE CONSTRAINT_SCHEMA = DATABASE()
			  AND TABLE_NAME NOT IN ('flyway_schema_history', 'clase_asistencia', 'asistencia_alumno')
			ORDER BY TABLE_NAME, CONSTRAINT_NAME""";
	private static final List<String> TABLAS_CON_FILAS = List.of("organizacion", "grupo", "turno", "alumno");

	DataSource dataSource;
	JdbcTemplate jdbc;
	long otra, disciplina, grupo, turno, turno2, alumno, cuenta;
	List<Map<String, Object>> columnasAntes;
	List<Map<String, Object>> indicesAntes;
	List<Map<String, Object>> restriccionesAntes;
	Map<String, List<Map<String, Object>>> filasAntes = new LinkedHashMap<>();

	@BeforeAll
	void migrar() {
		dataSource = recrear(ESQUEMA);
		jdbc = new JdbcTemplate(dataSource);
		flyway(dataSource, "31.14").migrate();

		jdbc.update("INSERT INTO organizacion (slug, nombre) VALUES ('otra-v31-15', 'Otra escuela V31_15')");
		otra = jdbc.queryForObject("SELECT id FROM organizacion WHERE slug = 'otra-v31-15'", Long.class);
		jdbc.update("""
				INSERT INTO disciplina (organizacion_id, codigo, nombre, sistema_grados_id, edad_adulto, orden)
				VALUES (?, 'TAEKWONDO', 'Taekwondo V31_15', NULL, 14, 1)""", otra);
		disciplina = jdbc.queryForObject("SELECT id FROM disciplina WHERE organizacion_id = ?", Long.class, otra);
		grupo = grupo("V31_15 grupo");
		turno = turno(grupo, "Lunes");
		turno2 = turno(grupo, "Martes");
		alumno = alumno(61001L);
		cuenta = cuenta("v31-15-profesor@example.test");

		columnasAntes = jdbc.queryForList(COLUMNAS_DEL_ESQUEMA);
		indicesAntes = jdbc.queryForList(INDICES_DEL_ESQUEMA);
		restriccionesAntes = jdbc.queryForList(RESTRICCIONES_DEL_ESQUEMA);
		for (String tabla : TABLAS_CON_FILAS) {
			filasAntes.put(tabla, jdbc.queryForList("SELECT * FROM " + tabla + " ORDER BY id"));
		}
		// Hasta V31_15: las migraciones posteriores tendrán su propio test.
		flyway(dataSource, "31.15").migrate();
	}

	@Test
	@Order(1)
	void llegaAV31_15() {
		assertEquals("31.15", jdbc.queryForObject(
				"SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1",
				String.class));
	}

	@Test
	@Order(2)
	void creaLasDosTablas_conLasColumnasPedidas() {
		assertEquals(List.of("id bigint NO", "organizacion_id bigint NO", "grupo_id bigint NO", "turno_id bigint YES",
				"fecha date NO", "hora_inicio varchar(5) NO", "hora_fin varchar(5) NO",
				"registrado_por_usuario_id bigint YES", "registrado_en datetime(6) NO",
				"modificado_por_usuario_id bigint YES", "modificado_en datetime(6) YES"), columnas("clase_asistencia"));
		assertEquals(List.of("id bigint NO", "organizacion_id bigint NO", "clase_id bigint NO", "alumno_id bigint NO",
				"presente bit(1) NO"), columnas("asistencia_alumno"));
	}

	@Test
	@Order(3)
	void indicesYUnicas() {
		assertEquals(List.of("turno_id,fecha"), indice("clase_asistencia", "uk_clase_asistencia_turno_fecha", 0));
		assertEquals(List.of("organizacion_id,grupo_id,fecha"),
				indice("clase_asistencia", "idx_clase_asistencia_organizacion_grupo_fecha", 1));
		assertEquals(List.of("clase_id,alumno_id"), indice("asistencia_alumno", "uk_asistencia_alumno_clase_alumno", 0));
		assertEquals(List.of("organizacion_id,alumno_id"),
				indice("asistencia_alumno", "idx_asistencia_alumno_organizacion_alumno", 1));
	}

	/** Solo amplía: nada del esquema cambia salvo las dos tablas y las filas siguen igual. */
	@Test
	@Order(4)
	void soloAmplia() {
		assertEquals(columnasAntes, jdbc.queryForList(COLUMNAS_DEL_ESQUEMA), "ninguna columna existente cambia");
		assertEquals(indicesAntes, jdbc.queryForList(INDICES_DEL_ESQUEMA), "ningún índice cambia");
		assertEquals(restriccionesAntes, jdbc.queryForList(RESTRICCIONES_DEL_ESQUEMA),
				"ninguna restricción existente cambia");
		for (String tabla : TABLAS_CON_FILAS) {
			assertEquals(filasAntes.get(tabla), jdbc.queryForList("SELECT * FROM " + tabla + " ORDER BY id"), tabla);
		}
		assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM clase_asistencia", Long.class));
		assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM asistencia_alumno", Long.class));
	}

	@Test
	@Order(5)
	void postcheck_justoTrasDesplegar() throws Exception {
		Map<String, Object> fila = postcheck();
		for (String columna : List.of("flyway_31_15", "ultima_es_31_15")) {
			assertEquals(1L, numero(fila, columna), columna);
		}
		assertEquals(2L, numero(fila, "tablas"));
		assertEquals(8L, numero(fila, "claves_ajenas"));
		assertEquals(2L, numero(fila, "unicas"));
		assertEquals(0L, numero(fila, "clases_con_lista"));
		assertEquals(0L, numero(fila, "filas_de_asistencia"));
		assertEquals(0L, numero(fila, "turnos_con_horario_raro"));
		assertEquals((long) filasAntes.get("turno").size(), numero(fila, "turnos"));
	}

	@Test
	@Order(6)
	void unaListaPorTurnoYFecha_yUnaFilaPorAlumno() {
		long clase = clase(grupo, turno, "2026-10-26");
		assertThrows(DataIntegrityViolationException.class, () -> clase(grupo, turno, "2026-10-26"));
		fila(clase, alumno, true);
		assertThrows(DataIntegrityViolationException.class, () -> fila(clase, alumno, false));
		assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM asistencia_alumno WHERE presente = TRUE", Long.class));
		jdbc.update("DELETE FROM clase_asistencia WHERE id = ?", clase);
		assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM asistencia_alumno", Long.class), "cascada");
	}

	/** Borrar el turno conserva sus listas con turno NULL; varias en la misma fecha no chocan en la UNIQUE. */
	@Test
	@Order(7)
	void borrarTurno_conservaElHistorico_conVariosNulosEnLaMismaFecha() {
		long grupoNuevo = grupo("V31_15 grupo borrar turno");
		long unTurno = turno(grupoNuevo, "Lunes");
		long otroTurno = turno(grupoNuevo, "Lunes");
		long clase1 = clase(grupoNuevo, unTurno, "2026-10-26");
		long clase2 = clase(grupoNuevo, otroTurno, "2026-10-26");
		fila(clase1, alumno, true);

		jdbc.update("DELETE FROM turno WHERE id = ?", unTurno);
		jdbc.update("DELETE FROM turno WHERE id = ?", otroTurno);

		assertEquals(2L, jdbc.queryForObject(
				"SELECT COUNT(*) FROM clase_asistencia WHERE id IN (?, ?) AND turno_id IS NULL", Long.class, clase1, clase2));
		assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM asistencia_alumno WHERE clase_id = ?", Long.class,
				clase1));

		jdbc.update("DELETE FROM grupo WHERE id = ?", grupoNuevo);
		assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM clase_asistencia WHERE id IN (?, ?)", Long.class,
				clase1, clase2), "borrar el grupo se lleva sus listas");
		assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM asistencia_alumno", Long.class));
	}

	@Test
	@Order(8)
	void borrarAlumno_seLlevaSusFilas_yLaListaSigue() {
		long grupoNuevo = grupo("V31_15 grupo borrar alumno");
		long clase = clase(grupoNuevo, null, "2026-10-26");
		long seVa = alumno(61002L);
		fila(clase, seVa, true);
		fila(clase, alumno, false);

		jdbc.update("DELETE FROM alumno WHERE id = ?", seVa);

		assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM asistencia_alumno WHERE clase_id = ?", Long.class,
				clase));
		jdbc.update("DELETE FROM grupo WHERE id = ?", grupoNuevo);
	}

	@Test
	@Order(9)
	void borrarCuenta_dejaLaListaSinUsuario() {
		long seVa = cuenta("v31-15-se-va@example.test");
		long clase = clase(grupo, turno2, "2026-10-27");
		jdbc.update("UPDATE clase_asistencia SET registrado_por_usuario_id = ?, modificado_por_usuario_id = ?, "
				+ "modificado_en = '2026-10-28 10:00:00' WHERE id = ?", seVa, seVa, clase);

		jdbc.update("DELETE FROM usuario WHERE id = ?", seVa);

		assertEquals(2L, jdbc.queryForObject("""
				SELECT (registrado_por_usuario_id IS NULL) + (modificado_por_usuario_id IS NULL)
				FROM clase_asistencia WHERE id = ?""", Long.class, clase));
		jdbc.update("DELETE FROM clase_asistencia WHERE id = ?", clase);
	}

	@Test
	@Order(10)
	void repetirla_conservaLoGuardado() throws Exception {
		long clase = clase(grupo, turno, "2026-10-26");
		fila(clase, alumno, true);
		ejecutarV31_15();
		ejecutarV31_15();
		assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM asistencia_alumno WHERE clase_id = ?", Long.class,
				clase));
		jdbc.update("DELETE FROM clase_asistencia WHERE id = ?", clase);
	}

	/** Si falló entre las dos sentencias, volver a ejecutarla crea lo que falta. */
	@Test
	@Order(11)
	void trasUnFalloAMedias_laCompleta() throws Exception {
		jdbc.execute("DROP TABLE asistencia_alumno");
		ejecutarV31_15();
		assertEquals(2L, numero(postcheck(), "tablas"));
		assertEquals(8L, numero(postcheck(), "claves_ajenas"));

		jdbc.execute("DROP TABLE asistencia_alumno, clase_asistencia");
		ejecutarV31_15();
		Map<String, Object> fila = postcheck();
		assertEquals(2L, numero(fila, "tablas"));
		assertEquals(8L, numero(fila, "claves_ajenas"));
		assertEquals(2L, numero(fila, "unicas"));
	}

	@Test
	@Order(12)
	void postcheck_avisaDeUnTurnoConHorarioRaro() throws Exception {
		jdbc.update("UPDATE turno SET hora_inicio = '18:00:00' WHERE id = ?", turno2);
		assertEquals(1L, numero(postcheck(), "turnos_con_horario_raro"));
		jdbc.update("UPDATE turno SET hora_inicio = '18:00' WHERE id = ?", turno2);
		assertTrue(numero(postcheck(), "turnos") >= 2);
	}

	// ------------------------------------------------------------------ utilidades

	private long grupo(String nombre) {
		jdbc.update("INSERT INTO grupo (nombre, organizacion_id, disciplina_id) VALUES (?, ?, ?)", nombre, otra,
				disciplina);
		return jdbc.queryForObject("SELECT MAX(id) FROM grupo WHERE organizacion_id = ?", Long.class, otra);
	}

	private long turno(long g, String dia) {
		jdbc.update("""
				INSERT INTO turno (dia_semana, hora_inicio, hora_fin, tipo, grupo_id, organizacion_id)
				VALUES (?, '18:00', '19:00', NULL, ?, ?)""", dia, g, otra);
		return jdbc.queryForObject("SELECT MAX(id) FROM turno WHERE organizacion_id = ?", Long.class, otra);
	}

	private long alumno(long expediente) {
		jdbc.update("""
				INSERT INTO alumno (activo, apellidos, autorizacion_web, direccion, fecha_nacimiento, nombre,
				  numero_expediente, telefono, tiene_derecho_examen, organizacion_id)
				VALUES (1, 'PRUEBA', 1, 'Calle 1', '2015-01-01', 'V31_15', ?, '600000000', 0, ?)""", expediente, otra);
		return jdbc.queryForObject("SELECT id FROM alumno WHERE numero_expediente = ? AND organizacion_id = ?",
				Long.class, expediente, otra);
	}

	private long cuenta(String email) {
		jdbc.update("""
				INSERT INTO usuario (apellidos, contrasena, email, nombre, auth_provider)
				VALUES ('Prueba', 'fake-Password-123', ?, 'Cuenta', 'LOCAL')""", email);
		return jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", Long.class, email);
	}

	private long clase(long g, Long t, String fecha) {
		jdbc.update("""
				INSERT INTO clase_asistencia (organizacion_id, grupo_id, turno_id, fecha, hora_inicio, hora_fin, registrado_en)
				VALUES (?, ?, ?, ?, '18:00', '19:00', '2026-10-26 19:00:00')""", otra, g, t, fecha);
		return jdbc.queryForObject("SELECT MAX(id) FROM clase_asistencia", Long.class);
	}

	private void fila(long clase, long a, boolean presente) {
		jdbc.update("INSERT INTO asistencia_alumno (organizacion_id, clase_id, alumno_id, presente) VALUES (?, ?, ?, ?)",
				otra, clase, a, presente);
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

	private void ejecutarV31_15() throws Exception {
		try (Connection conexion = dataSource.getConnection()) {
			ScriptUtils.executeSqlScript(conexion,
					new EncodedResource(new ClassPathResource(V31_15), StandardCharsets.UTF_8));
		}
	}

	private Map<String, Object> postcheck() throws Exception {
		return SedesVinculosMigracionIntegrationTest.ejecutarScriptDocs(dataSource, "postcheck-pasar-lista.sql").get(0)
				.get(0);
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
