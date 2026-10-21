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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * V31_10 (pieza 8b-I, fase3-e8b-plan.md §3 y §6): tabla {@code diseno_panel_usuario}, VACÍA, con la clave única
 * (organizacion_id, usuario_id) y sus FK, sobre una base en V31_9 con cuentas y membresías de dos escuelas. Solo amplía:
 * ninguna otra tabla, columna, índice ni fila cambia; repetirla no hace nada. Datos inventados.
 */
@Tag("migracion")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DisenoPanelV31_10MigracionIntegrationTest extends MySqlIntegrationTest {

	private static final String ESQUEMA = "taemoi_v31_10_it";
	private static final String V31_10 = "db/migration/V31_10__diseno_panel_usuario.sql";
	private static final String TABLA = "diseno_panel_usuario";
	private static final List<String> COLUMNAS = List.of("id", "organizacion_id", "usuario_id", "permitido", "activo",
			"cambiado_por_usuario_id", "cambiado_en");
	/** Todo el esquema salvo la tabla nueva y el historial de Flyway (que gana su fila). */
	private static final String COLUMNAS_DEL_ESQUEMA = """
			SELECT TABLE_NAME, COLUMN_NAME, ORDINAL_POSITION, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, EXTRA,
			       CHARACTER_SET_NAME, COLLATION_NAME
			FROM information_schema.COLUMNS
			WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME NOT IN ('diseno_panel_usuario', 'flyway_schema_history')
			ORDER BY TABLE_NAME, ORDINAL_POSITION""";
	private static final String INDICES_DEL_ESQUEMA = """
			SELECT TABLE_NAME, INDEX_NAME, COLUMN_NAME, NON_UNIQUE, SEQ_IN_INDEX FROM information_schema.STATISTICS
			WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME NOT IN ('diseno_panel_usuario', 'flyway_schema_history')
			ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX""";
	private static final String RESTRICCIONES_DEL_ESQUEMA = """
			SELECT TABLE_NAME, CONSTRAINT_NAME, CONSTRAINT_TYPE FROM information_schema.TABLE_CONSTRAINTS
			WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME NOT IN ('diseno_panel_usuario', 'flyway_schema_history')
			ORDER BY TABLE_NAME, CONSTRAINT_NAME""";
	private static final List<String> TABLAS_CON_FILAS = List.of("usuario", "membresia", "organizacion",
			"configuracion_organizacion");

	DataSource dataSource;
	JdbcTemplate jdbc;
	long otra, propietario, gestorOtra;
	List<Map<String, Object>> columnasAntes;
	List<Map<String, Object>> indicesAntes;
	List<Map<String, Object>> restriccionesAntes;
	List<String> tablasAntes;
	Map<String, List<Map<String, Object>>> filasAntes = new LinkedHashMap<>();

	@BeforeAll
	void migrar() {
		dataSource = recrear(ESQUEMA);
		jdbc = new JdbcTemplate(dataSource);
		flyway(dataSource, "31.9").migrate();
		jdbc.update("INSERT INTO organizacion (slug, nombre) VALUES ('otra-v31-10', 'Otra escuela V31_10')");
		otra = jdbc.queryForObject("SELECT id FROM organizacion WHERE slug = 'otra-v31-10'", Long.class);
		propietario = cuenta("v31-10-propietario@example.test");
		gestorOtra = cuenta("v31-10-gestor-otra@example.test");
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, 1, 'PROPIETARIO')",
				propietario);
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, 'ADMINISTRADOR')",
				gestorOtra, otra);

		columnasAntes = jdbc.queryForList(COLUMNAS_DEL_ESQUEMA);
		indicesAntes = jdbc.queryForList(INDICES_DEL_ESQUEMA);
		restriccionesAntes = jdbc.queryForList(RESTRICCIONES_DEL_ESQUEMA);
		tablasAntes = tablas();
		for (String tabla : TABLAS_CON_FILAS) {
			filasAntes.put(tabla, jdbc.queryForList("SELECT * FROM " + tabla + " ORDER BY id"));
		}
		flyway(dataSource, "31.10").migrate();
	}

	@Test
	@Order(1)
	void llegaAV31_10() {
		assertEquals("31.10", jdbc.queryForObject(
				"SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1",
				String.class));
	}

	/** El caso del plan: la tabla nueva, vacía, y nada más cambia. */
	@Test
	@Order(2)
	void v31_10_soloAmplia() {
		List<String> esperadas = new java.util.ArrayList<>(tablasAntes);
		esperadas.add(TABLA);
		esperadas.sort(String::compareTo);
		assertEquals(esperadas, tablas(), "solo una tabla nueva");
		assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM " + TABLA, Long.class), "tabla vacía");
		assertEquals(columnasAntes, jdbc.queryForList(COLUMNAS_DEL_ESQUEMA), "ninguna columna existente cambia");
		assertEquals(indicesAntes, jdbc.queryForList(INDICES_DEL_ESQUEMA), "ningún índice existente cambia");
		assertEquals(restriccionesAntes, jdbc.queryForList(RESTRICCIONES_DEL_ESQUEMA),
				"ninguna restricción existente cambia");
		for (String tabla : TABLAS_CON_FILAS) {
			assertEquals(filasAntes.get(tabla), jdbc.queryForList("SELECT * FROM " + tabla + " ORDER BY id"), tabla);
		}
	}

	@Test
	@Order(3)
	void columnasTiposClaveUnicaYFk() {
		assertEquals(COLUMNAS, jdbc.queryForList("""
				SELECT COLUMN_NAME FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? ORDER BY ORDINAL_POSITION""", String.class, TABLA));
		assertEquals("bigint NO", tipo("organizacion_id"));
		assertEquals("bigint NO", tipo("usuario_id"));
		assertEquals("tinyint(1) NO", tipo("permitido"));
		assertEquals("tinyint(1) NO", tipo("activo"));
		assertEquals("bigint YES", tipo("cambiado_por_usuario_id"));
		assertEquals("datetime(6) NO", tipo("cambiado_en"));
		assertEquals("utf8mb4_0900_ai_ci", jdbc.queryForObject("""
				SELECT TABLE_COLLATION FROM information_schema.TABLES
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?""", String.class, TABLA));
		assertEquals(List.of("uk_diseno_panel_usuario_organizacion_usuario:organizacion_id,usuario_id"),
				jdbc.queryForList("""
						SELECT CONCAT(INDEX_NAME, ':', GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX))
						FROM information_schema.STATISTICS
						WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND NON_UNIQUE = 0 AND INDEX_NAME <> 'PRIMARY'
						GROUP BY INDEX_NAME ORDER BY INDEX_NAME""", String.class, TABLA));
		Map<String, String> fks = new LinkedHashMap<>();
		jdbc.queryForList("""
				SELECT k.COLUMN_NAME AS columna, k.REFERENCED_TABLE_NAME AS tabla, r.DELETE_RULE AS regla
				FROM information_schema.KEY_COLUMN_USAGE k
				JOIN information_schema.REFERENTIAL_CONSTRAINTS r
				  ON r.CONSTRAINT_SCHEMA = k.CONSTRAINT_SCHEMA AND r.CONSTRAINT_NAME = k.CONSTRAINT_NAME
				WHERE k.TABLE_SCHEMA = DATABASE() AND k.TABLE_NAME = ? AND k.REFERENCED_TABLE_NAME IS NOT NULL
				ORDER BY k.COLUMN_NAME""", TABLA)
				.forEach(f -> fks.put((String) f.get("columna"), f.get("tabla") + " " + f.get("regla")));
		Map<String, String> esperadas = new LinkedHashMap<>();
		esperadas.put("cambiado_por_usuario_id", "usuario SET NULL");
		esperadas.put("organizacion_id", "organizacion NO ACTION");
		esperadas.put("usuario_id", "usuario CASCADE");
		assertEquals(esperadas, fks);
	}

	@Test
	@Order(4)
	void unaFilaPorEscuelaYUsuario_laMismaCuentaEnDosEscuelas() {
		fila(1L, propietario);
		assertThrows(DuplicateKeyException.class, () -> fila(1L, propietario));
		fila(otra, propietario);
		assertEquals(2L, jdbc.queryForObject("SELECT COUNT(*) FROM " + TABLA + " WHERE usuario_id = ?", Long.class,
				propietario));
		jdbc.update("DELETE FROM " + TABLA);
	}

	@Test
	@Order(5)
	void repetirlaNoHaceNada() throws Exception {
		List<Map<String, Object>> columnas = jdbc.queryForList("""
				SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE() ORDER BY TABLE_NAME, ORDINAL_POSITION""");
		try (Connection conexion = dataSource.getConnection()) {
			ScriptUtils.executeSqlScript(conexion,
					new EncodedResource(new ClassPathResource(V31_10), StandardCharsets.UTF_8));
		}
		assertEquals(columnas, jdbc.queryForList("""
				SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE() ORDER BY TABLE_NAME, ORDINAL_POSITION"""));
		assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM " + TABLA, Long.class));
	}

	private void fila(long organizacion, long usuario) {
		jdbc.update("""
				INSERT INTO diseno_panel_usuario (organizacion_id, usuario_id, permitido, activo, cambiado_en)
				VALUES (?, ?, TRUE, TRUE, '2026-10-21 10:00:00')""", organizacion, usuario);
	}

	private long cuenta(String email) {
		jdbc.update("""
				INSERT INTO usuario (apellidos, contrasena, email, nombre, auth_provider)
				VALUES ('Prueba', 'fake-Password-123', ?, 'Cuenta', 'LOCAL')""", email);
		return jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", Long.class, email);
	}

	private List<String> tablas() {
		return jdbc.queryForList("""
				SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()
				ORDER BY TABLE_NAME""", String.class);
	}

	private String tipo(String columna) {
		return jdbc.queryForObject("""
				SELECT CONCAT(COLUMN_TYPE, ' ', IS_NULLABLE) FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?""", String.class, TABLA, columna);
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
