package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * V20 (membresia) sobre usuarios y roles con la forma de producción: se migra un esquema propio
 * hasta V19, se cargan usuario/usuario_rol, se migra a la última versión y se comprueba el relleno.
 */
@Tag("migracion")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MembresiaMigracionIntegrationTest extends MySqlIntegrationTest {

	private static final String ESQUEMA = "taemoi_membresia_it";

	DataSource dataSource;
	JdbcTemplate jdbc;
	long admin;
	long manager;
	long familia;
	long adminYFamilia;
	long sinRoles;

	@BeforeAll
	void migrarDesdeV19ConUsuarios() {
		JdbcTemplate root = new JdbcTemplate(fuente(""));
		root.execute("DROP DATABASE IF EXISTS " + ESQUEMA);
		root.execute("CREATE DATABASE " + ESQUEMA + " CHARACTER SET utf8mb4");
		dataSource = fuente(ESQUEMA);
		jdbc = new JdbcTemplate(dataSource);

		flyway("19").migrate();
		admin = usuario("admin", "ROLE_ADMIN");
		manager = usuario("manager", "ROLE_MANAGER");
		familia = usuario("familia", "ROLE_USER");
		adminYFamilia = usuario("admin-familia", "ROLE_ADMIN", "ROLE_USER");
		sinRoles = usuario("sin-roles");
		// usuario_rol no tiene clave primaria: puede haber filas repetidas.
		jdbc.update("INSERT INTO usuario_rol (usuario_id, roles) VALUES (?, 'ROLE_USER')", familia);
		jdbc.update("INSERT INTO usuario_rol (usuario_id, roles) VALUES (?, NULL)", sinRoles);

		flyway(null).migrate();
	}

	@Test
	void llegaALaUltimaVersion() {
		assertEquals("31.6", jdbc.queryForObject(
				"SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1",
				String.class));
	}

	@Test
	void rellenaUnaMembresiaPorRolEnLaOrganizacionUno() {
		assertEquals(List.of("PROPIETARIO"), roles(admin));
		assertEquals(List.of("ADMINISTRADOR"), roles(manager));
		assertEquals(List.of("FAMILIA"), roles(familia));
		assertEquals(List.of("FAMILIA", "PROPIETARIO"), roles(adminYFamilia));
		assertEquals(List.of(), roles(sinRoles));
		assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM membresia", Integer.class));
		assertEquals(5, jdbc.queryForObject(
				"SELECT COUNT(*) FROM membresia WHERE organizacion_id = 1 AND activa = TRUE AND cargo IS NULL"
						+ " AND creada_en IS NOT NULL",
				Integer.class));
	}

	@Test
	void usuarioRolSeConservaYUltimaOrganizacionEsOpcional() {
		assertEquals(7, jdbc.queryForObject("SELECT COUNT(*) FROM usuario_rol", Integer.class));
		assertEquals("YES", jdbc.queryForObject("""
				SELECT IS_NULLABLE FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'usuario' AND COLUMN_NAME = 'ultima_organizacion_id'
				""", String.class));
		assertEquals(0, jdbc.queryForObject(
				"SELECT COUNT(*) FROM usuario WHERE ultima_organizacion_id IS NOT NULL", Integer.class));
	}

	@Test
	void restriccionesDeLaTabla() {
		assertThrows(DataAccessException.class, () -> jdbc.update(
				"INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, 1, 'PROPIETARIO')", admin));
		assertThrows(DataAccessException.class, () -> jdbc.update(
				"INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, 1, 'INVENTADO')", manager));
		assertThrows(DataAccessException.class, () -> jdbc.update(
				"INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, 999, 'FAMILIA')", manager));
		assertThrows(DataAccessException.class, () -> jdbc.update(
				"UPDATE usuario SET ultima_organizacion_id = 999 WHERE id = ?", manager));
	}

	@Test
	void borrarUnUsuarioBorraSusMembresias() {
		long temporal = usuario("temporal");
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, 1, 'PROFESOR')", temporal);

		jdbc.update("DELETE FROM usuario WHERE id = ?", temporal);

		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM membresia WHERE usuario_id = ?", Integer.class,
				temporal));
	}

	@Test
	void repetirV20NoDuplicaNada() throws Exception {
		int antes = jdbc.queryForObject("SELECT COUNT(*) FROM membresia", Integer.class);
		try (Connection conexion = dataSource.getConnection()) {
			ScriptUtils.executeSqlScript(conexion, new ClassPathResource("db/migration/V20__membresia.sql"));
			ScriptUtils.executeSqlScript(conexion, new ClassPathResource("db/migration/V20__membresia.sql"));
		}

		assertEquals(antes, jdbc.queryForObject("SELECT COUNT(*) FROM membresia", Integer.class));
		assertEquals(List.of("FAMILIA", "PROPIETARIO"), roles(adminYFamilia));
		assertTrue(jdbc.queryForObject("""
				SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
				WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'usuario'
				  AND CONSTRAINT_NAME = 'fk_usuario_ultima_organizacion'
				""", Integer.class) == 1);
	}

	// ---------------------------------------------------------------------------------

	private long usuario(String nombre, String... roles) {
		jdbc.update("INSERT INTO usuario (email, nombre, apellidos, contrasena) VALUES (?, ?, 'Prueba', 'x')",
				nombre + "@membresia.test", nombre);
		long id = jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", Long.class, nombre + "@membresia.test");
		for (String rol : roles) {
			jdbc.update("INSERT INTO usuario_rol (usuario_id, roles) VALUES (?, ?)", id, rol);
		}
		return id;
	}

	private List<String> roles(long usuarioId) {
		return jdbc.queryForList(
				"SELECT rol FROM membresia WHERE usuario_id = ? AND organizacion_id = 1 ORDER BY CAST(rol AS CHAR)",
				String.class, usuarioId);
	}

	private Flyway flyway(String objetivo) {
		// V20..V29 en una sola ejecución: desactiva la guarda de V28 (como los entornos de prueba).
		var config = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
				.placeholders(Map.of(ContraccionCatalogoMigracionIntegrationTest.PERMITIR_CON_V27, "true"));
		if (objetivo != null) {
			config.target(objetivo);
		}
		return config.load();
	}

	private DataSource fuente(String esquema) {
		String url = "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306) + "/" + esquema
				+ "?useSSL=false&allowPublicKeyRetrieval=true";
		return new DriverManagerDataSource(url, "root", MYSQL.getPassword());
	}
}
