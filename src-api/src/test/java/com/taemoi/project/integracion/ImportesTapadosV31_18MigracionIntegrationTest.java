package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * V31_18 (decisión 419): {@code usuario.importes_tapados}, {@code BOOLEAN NOT NULL DEFAULT FALSE}, al final de la tabla,
 * sobre una base en V31_17 con cuentas de dos escuelas, una con perfil guardado. Todas las filas quedan a falso y nada más
 * cambia; un INSERT sin la columna (el código anterior) la deja a falso; la columna admite INSTANT; repetirla, también
 * tras un fallo a medias, no cambia nada y conserva lo guardado. Ejecuta
 * {@code docs/multi-escuela/postcheck-importes-tapados.sql}. Datos inventados.
 */
@Tag("migracion")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ImportesTapadosV31_18MigracionIntegrationTest extends MySqlIntegrationTest {

	private static final String ESQUEMA = "taemoi_v31_18_it";
	private static final String V31_18 = "db/migration/V31_18__importes_tapados.sql";
	private static final String NUEVA = "importes_tapados";
	/** Todo el esquema salvo la columna nueva y el historial de Flyway (que gana su fila). */
	private static final String COLUMNAS_DEL_ESQUEMA = """
			SELECT TABLE_NAME, COLUMN_NAME, ORDINAL_POSITION, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, EXTRA,
			       CHARACTER_SET_NAME, COLLATION_NAME
			FROM information_schema.COLUMNS
			WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME <> 'flyway_schema_history'
			  AND NOT (TABLE_NAME = 'usuario' AND COLUMN_NAME = 'importes_tapados')
			ORDER BY TABLE_NAME, ORDINAL_POSITION""";
	private static final String INDICES_DEL_ESQUEMA = """
			SELECT TABLE_NAME, INDEX_NAME, COLUMN_NAME, NON_UNIQUE, SEQ_IN_INDEX FROM information_schema.STATISTICS
			WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME <> 'flyway_schema_history'
			ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX""";
	private static final String RESTRICCIONES_DEL_ESQUEMA = """
			SELECT TABLE_NAME, CONSTRAINT_NAME, CONSTRAINT_TYPE FROM information_schema.TABLE_CONSTRAINTS
			WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME <> 'flyway_schema_history'
			ORDER BY TABLE_NAME, CONSTRAINT_NAME""";
	private static final List<String> TABLAS_CON_FILAS = List.of("membresia", "organizacion");

	DataSource dataSource;
	JdbcTemplate jdbc;
	long cuenta;
	List<String> columnasUsuarioAntes;
	List<Map<String, Object>> columnasAntes;
	List<Map<String, Object>> indicesAntes;
	List<Map<String, Object>> restriccionesAntes;
	List<Map<String, Object>> usuariosAntes;
	Map<String, List<Map<String, Object>>> filasAntes = new LinkedHashMap<>();

	@BeforeAll
	void migrar() {
		dataSource = recrear(ESQUEMA);
		jdbc = new JdbcTemplate(dataSource);
		flyway(dataSource, "31.17").migrate();
		jdbc.update("INSERT INTO organizacion (slug, nombre) VALUES ('otra-v31-18', 'Otra escuela V31_18')");
		long otra = jdbc.queryForObject("SELECT id FROM organizacion WHERE slug = 'otra-v31-18'", Long.class);
		cuenta = cuenta("v31-18-propietario@example.test");
		long gestorOtra = cuenta("v31-18-gestor-otra@example.test");
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, 1, 'PROPIETARIO')", cuenta);
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, 'ADMINISTRADOR')",
				gestorOtra, otra);
		jdbc.update("UPDATE usuario SET nombre_preferido = 'Luci', cumple_dia = 14, cumple_mes = 3 WHERE id = ?", cuenta);

		columnasUsuarioAntes = columnasUsuario();
		columnasAntes = jdbc.queryForList(COLUMNAS_DEL_ESQUEMA);
		indicesAntes = jdbc.queryForList(INDICES_DEL_ESQUEMA);
		restriccionesAntes = jdbc.queryForList(RESTRICCIONES_DEL_ESQUEMA);
		usuariosAntes = usuarios();
		assertTrue(usuariosAntes.size() >= 2);
		for (String tabla : TABLAS_CON_FILAS) {
			filasAntes.put(tabla, jdbc.queryForList("SELECT * FROM " + tabla + " ORDER BY id"));
		}
		// Hasta V31_18: las migraciones posteriores tendrán su propio test.
		flyway(dataSource, "31.18").migrate();
	}

	@Test
	@Order(1)
	void llegaAV31_18() {
		assertEquals("31.18", jdbc.queryForObject(
				"SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1",
				String.class));
	}

	@Test
	@Order(2)
	void anadeLaColumnaAlFinal_noNula_yConDefectoFalso() {
		assertEquals(conLaNueva(), columnasUsuario());
		assertEquals("tinyint(1) NO 0", jdbc.queryForObject("""
				SELECT CONCAT(COLUMN_TYPE, ' ', IS_NULLABLE, ' ', COLUMN_DEFAULT) FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'usuario' AND COLUMN_NAME = 'importes_tapados'""",
				String.class));
	}

	/** Solo amplía: nada del esquema cambia salvo la columna, y las filas siguen igual, a falso. */
	@Test
	@Order(3)
	void soloAmplia_todasLasFilasAFalso() {
		assertEquals(columnasAntes, jdbc.queryForList(COLUMNAS_DEL_ESQUEMA), "ninguna columna existente cambia");
		assertEquals(indicesAntes, jdbc.queryForList(INDICES_DEL_ESQUEMA), "ningún índice cambia");
		assertEquals(restriccionesAntes, jdbc.queryForList(RESTRICCIONES_DEL_ESQUEMA), "ninguna restricción cambia");
		for (String tabla : TABLAS_CON_FILAS) {
			assertEquals(filasAntes.get(tabla), jdbc.queryForList("SELECT * FROM " + tabla + " ORDER BY id"), tabla);
		}
		List<Map<String, Object>> ahora = usuarios();
		for (Map<String, Object> fila : ahora) {
			assertEquals(Boolean.FALSE, fila.get(NUEVA), "usuario " + fila.get("id"));
		}
		assertEquals(usuariosAntes, sinLaNueva(ahora));
	}

	@Test
	@Order(4)
	void postcheck_justoTrasDesplegar() throws Exception {
		Map<String, Object> fila = postcheck();
		for (String columna : List.of("flyway_31_18", "ultima_es_31_18", "columna_importes_tapados")) {
			assertEquals(1L, numero(fila, columna), columna);
		}
		assertEquals(0L, numero(fila, "cuentas_con_importes_tapados"));
		assertEquals((long) usuariosAntes.size(), numero(fila, "cuentas"));
		assertEquals(5, fila.size(), "solo recuentos: " + fila.keySet());
	}

	/** El código anterior inserta sin la columna: queda a falso. */
	@Test
	@Order(5)
	void insertarSinLaColumna_quedaAFalso_yElTapadoSeCuenta() throws Exception {
		long nueva = cuenta("v31-18-nueva@example.test");
		assertEquals(Boolean.FALSE, jdbc.queryForObject("SELECT importes_tapados FROM usuario WHERE id = ?",
				Boolean.class, nueva));
		jdbc.update("UPDATE usuario SET importes_tapados = TRUE WHERE id = ?", nueva);
		assertEquals(1L, numero(postcheck(), "cuentas_con_importes_tapados"));
		jdbc.update("DELETE FROM usuario WHERE id = ?", nueva);
		assertEquals(0L, numero(postcheck(), "cuentas_con_importes_tapados"));
	}

	@Test
	@Order(6)
	void noAdmiteNull() {
		assertThrows(DataAccessException.class,
				() -> jdbc.update("UPDATE usuario SET importes_tapados = NULL WHERE id = ?", cuenta));
		assertFalse(tapado(cuenta));
	}

	/** La afirmación de coste, en el MySQL de los tests (8.0, como producción), con el CHECK de V31_14 en la tabla. */
	@Test
	@Order(7)
	void laColumnaEsInstant() {
		jdbc.execute("ALTER TABLE usuario DROP COLUMN importes_tapados");
		jdbc.execute("ALTER TABLE usuario ADD COLUMN importes_tapados BOOLEAN NOT NULL DEFAULT FALSE, ALGORITHM=INSTANT");
		assertEquals(conLaNueva(), columnasUsuario());
		assertEquals(restriccionesAntes, jdbc.queryForList(RESTRICCIONES_DEL_ESQUEMA));
	}

	@Test
	@Order(8)
	void repetirla_conservaLoGuardado() throws Exception {
		jdbc.update("UPDATE usuario SET importes_tapados = TRUE WHERE id = ?", cuenta);
		List<Map<String, Object>> antes = usuarios();
		ejecutarV31_18();
		ejecutarV31_18();
		assertEquals(antes, usuarios());
		assertEquals(conLaNueva(), columnasUsuario());
		assertTrue(tapado(cuenta));
		jdbc.update("UPDATE usuario SET importes_tapados = FALSE WHERE id = ?", cuenta);
	}

	/** Una sola sentencia: si no llegó a ejecutarse, volver a lanzarla la añade, y una vez más no cambia nada. */
	@Test
	@Order(9)
	void trasUnFalloAMedias_laCompleta() throws Exception {
		jdbc.execute("ALTER TABLE usuario DROP COLUMN importes_tapados");
		ejecutarV31_18();
		ejecutarV31_18();
		assertEquals(conLaNueva(), columnasUsuario());
		Map<String, Object> fila = postcheck();
		assertEquals(1L, numero(fila, "columna_importes_tapados"));
		assertEquals(0L, numero(fila, "cuentas_con_importes_tapados"));
		assertEquals(usuariosAntes, sinLaNueva(usuarios()));
	}

	// ------------------------------------------------------------------ utilidades

	private boolean tapado(long id) {
		return jdbc.queryForObject("SELECT importes_tapados FROM usuario WHERE id = ?", Boolean.class, id);
	}

	private void ejecutarV31_18() throws Exception {
		try (Connection conexion = dataSource.getConnection()) {
			ScriptUtils.executeSqlScript(conexion,
					new EncodedResource(new ClassPathResource(V31_18), StandardCharsets.UTF_8));
		}
	}

	private long cuenta(String email) {
		jdbc.update("""
				INSERT INTO usuario (apellidos, contrasena, email, nombre, auth_provider)
				VALUES ('Prueba', 'fake-Password-123', ?, 'Cuenta', 'LOCAL')""", email);
		return jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", Long.class, email);
	}

	private List<Map<String, Object>> usuarios() {
		return jdbc.queryForList("SELECT * FROM usuario ORDER BY id");
	}

	private static List<Map<String, Object>> sinLaNueva(List<Map<String, Object>> filas) {
		return filas.stream().map(f -> {
			Map<String, Object> copia = new LinkedHashMap<>(f);
			copia.remove(NUEVA);
			return copia;
		}).toList();
	}

	private List<String> conLaNueva() {
		List<String> esperadas = new ArrayList<>(columnasUsuarioAntes);
		esperadas.add(NUEVA);
		return esperadas;
	}

	private List<String> columnasUsuario() {
		return jdbc.queryForList("""
				SELECT COLUMN_NAME FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'usuario' ORDER BY ORDINAL_POSITION""", String.class);
	}

	private Map<String, Object> postcheck() throws Exception {
		return SedesVinculosMigracionIntegrationTest.ejecutarScriptDocs(dataSource, "postcheck-importes-tapados.sql")
				.get(0).get(0);
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
