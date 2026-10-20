package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
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
 * V31_9 (pieza E8-4, decisión 223): {@code usuario.contrasena} pasa a admitir NULL (invitación pendiente = cuenta LOCAL
 * sin contraseña), con el mismo tipo, juego de caracteres y colación que tenía, sobre una base en V31_8 con cuentas.
 * Ninguna fila ni ninguna otra columna cambia; una cuenta con contraseña NULL se puede guardar; repetirla no hace nada.
 */
@Tag("migracion")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UsuarioContrasenaV31_9MigracionIntegrationTest extends MySqlIntegrationTest {

	private static final String ESQUEMA = "taemoi_v31_9_it";
	private static final String V31_9 = "db/migration/V31_9__usuario_contrasena_opcional.sql";
	private static final String COLUMNAS_USUARIO = """
			SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, CHARACTER_SET_NAME, COLLATION_NAME,
			       ORDINAL_POSITION
			FROM information_schema.COLUMNS
			WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'usuario' ORDER BY ORDINAL_POSITION""";

	DataSource dataSource;
	JdbcTemplate jdbc;
	List<Map<String, Object>> columnasAntes;
	List<Map<String, Object>> filasAntes;
	List<Map<String, Object>> indicesAntes;

	@BeforeAll
	void migrar() {
		dataSource = recrear(ESQUEMA);
		jdbc = new JdbcTemplate(dataSource);
		flyway(dataSource, "31.8").migrate();
		cuenta("v31-9-local@example.test", "LOCAL", "$2a$10$abcdefghijklmnopqrstuuJ0Xv1oN5o3rT1Qy3dYkq4m0x8vY7p2a");
		cuenta("v31-9-google@example.test", "GOOGLE", "$2a$10$zyxwvutsrqponmlkjihgfeJ0Xv1oN5o3rT1Qy3dYkq4m0x8vY7p2b");
		cuenta("v31-9-legado@example.test", null, "texto-plano-legado");
		jdbc.update("UPDATE usuario SET reset_token_hash = ?, reset_token_expires_at = '2026-10-21 10:00:00' "
				+ "WHERE email = 'v31-9-local@example.test'", "a".repeat(64));
		columnasAntes = jdbc.queryForList(COLUMNAS_USUARIO);
		filasAntes = jdbc.queryForList("SELECT * FROM usuario ORDER BY id");
		indicesAntes = indices();
		flyway(dataSource, "31.9").migrate();
	}

	@Test
	@Order(1)
	void llegaAV31_9() {
		assertEquals("31.9", jdbc.queryForObject(
				"SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1",
				String.class));
	}

	@Test
	@Order(2)
	void contrasenaAdmiteNull_conElMismoTipoJuegoYColacion() {
		Map<String, Object> antes = columna(columnasAntes, "contrasena");
		Map<String, Object> despues = columna(jdbc.queryForList(COLUMNAS_USUARIO), "contrasena");
		assertEquals("NO", antes.get("IS_NULLABLE"), "en V31_8 no admitía NULL");
		assertEquals("YES", despues.get("IS_NULLABLE"));
		assertEquals("varchar(255)", despues.get("COLUMN_TYPE"));
		for (String clave : List.of("COLUMN_TYPE", "CHARACTER_SET_NAME", "COLLATION_NAME", "ORDINAL_POSITION")) {
			assertEquals(antes.get(clave), despues.get(clave), clave);
		}
		assertNull(despues.get("COLUMN_DEFAULT"));
	}

	@Test
	@Order(3)
	void lasDemasColumnasLasFilasYLosIndicesNoCambian() {
		List<Map<String, Object>> despues = jdbc.queryForList(COLUMNAS_USUARIO);
		assertEquals(columnasAntes.size(), despues.size());
		for (int i = 0; i < despues.size(); i++) {
			if (!"contrasena".equals(despues.get(i).get("COLUMN_NAME"))) {
				assertEquals(columnasAntes.get(i), despues.get(i));
			}
		}
		assertEquals(filasAntes, jdbc.queryForList("SELECT * FROM usuario ORDER BY id"));
		assertEquals(indicesAntes, indices());
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM usuario WHERE contrasena IS NULL", Integer.class));
	}

	@Test
	@Order(4)
	void unaCuentaSinContrasenaSeGuarda() {
		cuenta("v31-9-invitada@example.test", "LOCAL", null);
		assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM usuario WHERE contrasena IS NULL", Integer.class));
	}

	@Test
	@Order(5)
	void repetirlaNoHaceNada() throws Exception {
		List<Map<String, Object>> columnas = jdbc.queryForList(COLUMNAS_USUARIO);
		List<Map<String, Object>> filas = jdbc.queryForList("SELECT * FROM usuario ORDER BY id");
		try (Connection conexion = dataSource.getConnection()) {
			ScriptUtils.executeSqlScript(conexion,
					new EncodedResource(new ClassPathResource(V31_9), StandardCharsets.UTF_8));
		}
		assertEquals(columnas, jdbc.queryForList(COLUMNAS_USUARIO));
		assertEquals(filas, jdbc.queryForList("SELECT * FROM usuario ORDER BY id"));
	}

	private void cuenta(String email, String proveedor, String contrasena) {
		jdbc.update("""
				INSERT INTO usuario (apellidos, contrasena, email, nombre, auth_provider)
				VALUES ('Prueba', ?, ?, 'Cuenta', ?)""", contrasena, email, proveedor);
	}

	private List<Map<String, Object>> indices() {
		return jdbc.queryForList("""
				SELECT INDEX_NAME, COLUMN_NAME, NON_UNIQUE, SEQ_IN_INDEX FROM information_schema.STATISTICS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'usuario' ORDER BY INDEX_NAME, SEQ_IN_INDEX""");
	}

	private static Map<String, Object> columna(List<Map<String, Object>> columnas, String nombre) {
		return columnas.stream().filter(c -> nombre.equals(c.get("COLUMN_NAME"))).findFirst().orElseThrow();
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
