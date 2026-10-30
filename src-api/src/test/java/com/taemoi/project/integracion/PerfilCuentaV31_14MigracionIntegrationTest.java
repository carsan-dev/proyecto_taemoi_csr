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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * V31_14 (decisión 322): {@code usuario.nombre_preferido}, {@code cumple_dia} y {@code cumple_mes}, todas NULL, al final
 * de la tabla, y el CHECK {@code ck_usuario_cumple}, sobre una base en V31_13 con cuentas de dos escuelas. Todas las filas
 * quedan a NULL y nada más cambia; un INSERT sin las columnas (el código anterior) las deja a NULL; las columnas admiten
 * INSTANT; repetirla, también tras un fallo a medias, no cambia nada y completa lo que faltara. Ejecuta
 * {@code docs/multi-escuela/postcheck-perfil-cuenta.sql}. Datos inventados.
 */
@Tag("migracion")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PerfilCuentaV31_14MigracionIntegrationTest extends MySqlIntegrationTest {

	private static final String ESQUEMA = "taemoi_v31_14_it";
	private static final String V31_14 = "db/migration/V31_14__perfil_cuenta.sql";
	private static final List<String> NUEVAS = List.of("nombre_preferido", "cumple_dia", "cumple_mes");
	private static final String CHECK = "ck_usuario_cumple";
	/** Todo el esquema salvo las columnas nuevas y el historial de Flyway (que gana su fila). */
	private static final String COLUMNAS_DEL_ESQUEMA = """
			SELECT TABLE_NAME, COLUMN_NAME, ORDINAL_POSITION, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, EXTRA,
			       CHARACTER_SET_NAME, COLLATION_NAME
			FROM information_schema.COLUMNS
			WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME <> 'flyway_schema_history'
			  AND NOT (TABLE_NAME = 'usuario' AND COLUMN_NAME IN ('nombre_preferido', 'cumple_dia', 'cumple_mes'))
			ORDER BY TABLE_NAME, ORDINAL_POSITION""";
	private static final String INDICES_DEL_ESQUEMA = """
			SELECT TABLE_NAME, INDEX_NAME, COLUMN_NAME, NON_UNIQUE, SEQ_IN_INDEX FROM information_schema.STATISTICS
			WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME <> 'flyway_schema_history'
			ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX""";
	private static final String RESTRICCIONES_DEL_ESQUEMA = """
			SELECT TABLE_NAME, CONSTRAINT_NAME, CONSTRAINT_TYPE FROM information_schema.TABLE_CONSTRAINTS
			WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME <> 'flyway_schema_history'
			  AND CONSTRAINT_NAME <> 'ck_usuario_cumple'
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
		flyway(dataSource, "31.13").migrate();
		jdbc.update("INSERT INTO organizacion (slug, nombre) VALUES ('otra-v31-14', 'Otra escuela V31_14')");
		long otra = jdbc.queryForObject("SELECT id FROM organizacion WHERE slug = 'otra-v31-14'", Long.class);
		cuenta = cuenta("v31-14-propietario@example.test");
		long gestorOtra = cuenta("v31-14-gestor-otra@example.test");
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, 1, 'PROPIETARIO')", cuenta);
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, 'ADMINISTRADOR')",
				gestorOtra, otra);

		columnasUsuarioAntes = columnasUsuario();
		columnasAntes = jdbc.queryForList(COLUMNAS_DEL_ESQUEMA);
		indicesAntes = jdbc.queryForList(INDICES_DEL_ESQUEMA);
		restriccionesAntes = jdbc.queryForList(RESTRICCIONES_DEL_ESQUEMA);
		usuariosAntes = usuarios();
		assertTrue(usuariosAntes.size() >= 2);
		for (String tabla : TABLAS_CON_FILAS) {
			filasAntes.put(tabla, jdbc.queryForList("SELECT * FROM " + tabla + " ORDER BY id"));
		}
		// Hasta V31_14: las migraciones posteriores tendrán su propio test.
		flyway(dataSource, "31.14").migrate();
	}

	@Test
	@Order(1)
	void llegaAV31_14() {
		assertEquals("31.14", jdbc.queryForObject(
				"SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1",
				String.class));
	}

	@Test
	@Order(2)
	void añadeLasColumnasAlFinal_nulas_yEnUtf8mb4() {
		assertEquals(conLasNuevas(), columnasUsuario());
		assertEquals("varchar(40) YES", tipo("nombre_preferido"));
		assertEquals("tinyint YES", tipo("cumple_dia"));
		assertEquals("tinyint YES", tipo("cumple_mes"));
		assertEquals("utf8mb4", jdbc.queryForObject("""
				SELECT CHARACTER_SET_NAME FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'usuario' AND COLUMN_NAME = 'nombre_preferido'""",
				String.class));
		assertEquals(0L, jdbc.queryForObject("""
				SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'usuario'
				  AND COLUMN_NAME IN ('nombre_preferido', 'cumple_dia', 'cumple_mes') AND COLUMN_DEFAULT IS NOT NULL""",
				Long.class), "sin valor por defecto");
	}

	/** Solo amplía: nada del esquema cambia salvo las tres columnas y el CHECK, y las filas siguen igual, a NULL. */
	@Test
	@Order(3)
	void soloAmplia_todasLasFilasANull() {
		assertEquals(columnasAntes, jdbc.queryForList(COLUMNAS_DEL_ESQUEMA), "ninguna columna existente cambia");
		assertEquals(indicesAntes, jdbc.queryForList(INDICES_DEL_ESQUEMA), "ningún índice cambia");
		assertEquals(restriccionesAntes, jdbc.queryForList(RESTRICCIONES_DEL_ESQUEMA),
				"ninguna restricción existente cambia");
		for (String tabla : TABLAS_CON_FILAS) {
			assertEquals(filasAntes.get(tabla), jdbc.queryForList("SELECT * FROM " + tabla + " ORDER BY id"), tabla);
		}
		List<Map<String, Object>> ahora = usuarios();
		for (Map<String, Object> fila : ahora) {
			for (String columna : NUEVAS) {
				assertEquals(null, fila.get(columna), "usuario " + fila.get("id"));
			}
		}
		assertEquals(usuariosAntes, sinNuevas(ahora));
	}

	@Test
	@Order(4)
	void postcheck_justoTrasDesplegar() throws Exception {
		Map<String, Object> fila = postcheck();
		for (String columna : List.of("flyway_31_14", "ultima_es_31_14", "columna_nombre_preferido", "columna_cumple_dia",
				"columna_cumple_mes", "check_cumple")) {
			assertEquals(1L, numero(fila, columna), columna);
		}
		assertEquals(0L, numero(fila, "cuentas_con_perfil"));
		assertEquals((long) usuariosAntes.size(), numero(fila, "cuentas"));
	}

	/** El código anterior inserta sin las columnas: quedan a NULL. */
	@Test
	@Order(5)
	void insertarSinLasColumnas_quedanANull_yElPerfilSeCuenta() throws Exception {
		long nueva = cuenta("v31-14-nueva@example.test");
		for (String columna : NUEVAS) {
			assertEquals(null, jdbc.queryForObject("SELECT " + columna + " FROM usuario WHERE id = ?", Object.class, nueva));
		}
		jdbc.update("UPDATE usuario SET nombre_preferido = 'Luci' WHERE id = ?", nueva);
		assertEquals(1L, numero(postcheck(), "cuentas_con_perfil"));
		jdbc.update("DELETE FROM usuario WHERE id = ?", nueva);
		assertEquals(0L, numero(postcheck(), "cuentas_con_perfil"));
	}

	@Test
	@Order(6)
	void elCheck_soloDejaLosDosNulosOLosDosEnRango() {
		for (String[] valido : new String[][] { { "NULL", "NULL" }, { "1", "1" }, { "31", "12" }, { "29", "2" } }) {
			cumple(valido[0], valido[1]);
		}
		for (String[] invalido : new String[][] { { "14", "NULL" }, { "NULL", "3" }, { "0", "1" }, { "32", "1" },
				{ "1", "0" }, { "1", "13" }, { "-1", "1" }, { "1", "-1" }, { "127", "127" } }) {
			assertThrows(DataAccessException.class, () -> cumple(invalido[0], invalido[1]), String.join(",", invalido));
		}
		cumple("NULL", "NULL");
	}

	/** 40 caracteres de cuatro bytes caben: el límite es de puntos de código, como en PerfilCuentaService. */
	@Test
	@Order(7)
	void nombrePreferido_cabenCuarentaEmojis_yNoCuarentaYUno() {
		String cuarenta = "😀".repeat(40);
		jdbc.update("UPDATE usuario SET nombre_preferido = ? WHERE id = ?", cuarenta, cuenta);
		assertEquals(cuarenta,
				jdbc.queryForObject("SELECT nombre_preferido FROM usuario WHERE id = ?", String.class, cuenta));
		assertThrows(DataIntegrityViolationException.class, () -> jdbc
				.update("UPDATE usuario SET nombre_preferido = ? WHERE id = ?", cuarenta + "😀", cuenta));
		jdbc.update("UPDATE usuario SET nombre_preferido = NULL WHERE id = ?", cuenta);
	}

	/** Las afirmaciones de coste, en el MySQL de los tests (8.0, como producción). */
	@Test
	@Order(8)
	void lasColumnasSonInstant_yElCheckSeHaceConCopia() {
		jdbc.execute("ALTER TABLE usuario DROP CHECK " + CHECK);
		jdbc.execute("ALTER TABLE usuario DROP COLUMN cumple_mes, DROP COLUMN cumple_dia, DROP COLUMN nombre_preferido");
		jdbc.execute("""
				ALTER TABLE usuario ADD COLUMN nombre_preferido VARCHAR(40)
				  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL, ALGORITHM=INSTANT""");
		jdbc.execute("ALTER TABLE usuario ADD COLUMN cumple_dia TINYINT NULL, ALGORITHM=INSTANT");
		jdbc.execute("ALTER TABLE usuario ADD COLUMN cumple_mes TINYINT NULL, ALGORITHM=INSTANT");
		String expresion = """
				CHECK ((cumple_dia IS NULL AND cumple_mes IS NULL)
				       OR (cumple_dia IS NOT NULL AND cumple_mes IS NOT NULL
				           AND cumple_dia BETWEEN 1 AND 31 AND cumple_mes BETWEEN 1 AND 12))""";
		DataAccessException sinCopia = assertThrows(DataAccessException.class, () -> jdbc
				.execute("ALTER TABLE usuario ADD CONSTRAINT " + CHECK + " " + expresion + ", ALGORITHM=INPLACE"));
		assertTrue(sinCopia.getMessage().contains("ALGORITHM=INPLACE is not supported"), sinCopia.getMessage());
		jdbc.execute("ALTER TABLE usuario ADD CONSTRAINT " + CHECK + " " + expresion + ", ALGORITHM=COPY");
		assertEquals(conLasNuevas(), columnasUsuario());
	}

	@Test
	@Order(9)
	void repetirla_conservaLoGuardado() throws Exception {
		jdbc.update("UPDATE usuario SET nombre_preferido = 'Luci', cumple_dia = 14, cumple_mes = 3 WHERE id = ?", cuenta);
		List<Map<String, Object>> antes = usuarios();
		ejecutarV31_14();
		ejecutarV31_14();
		assertEquals(antes, usuarios());
		assertEquals(conLasNuevas(), columnasUsuario());
		assertEquals(1L, numero(postcheck(), "check_cumple"));
		jdbc.update("UPDATE usuario SET nombre_preferido = NULL, cumple_dia = NULL, cumple_mes = NULL WHERE id = ?",
				cuenta);
	}

	/** El DDL no es transaccional: se puede quedar a medias tras cada sentencia. Volver a ejecutarla lo completa. */
	@Test
	@Order(10)
	void trasUnFalloAMedias_laCompleta() throws Exception {
		// Solo faltaba el CHECK.
		jdbc.execute("ALTER TABLE usuario DROP CHECK " + CHECK);
		ejecutarV31_14();
		assertEquals(1L, numero(postcheck(), "check_cumple"));

		// Faltaban el mes y el CHECK.
		jdbc.execute("ALTER TABLE usuario DROP CHECK " + CHECK);
		jdbc.execute("ALTER TABLE usuario DROP COLUMN cumple_mes");
		ejecutarV31_14();
		assertEquals(conLasNuevas(), columnasUsuario());
		assertEquals(1L, numero(postcheck(), "check_cumple"));

		// Faltaban el día, el mes y el CHECK.
		jdbc.execute("ALTER TABLE usuario DROP CHECK " + CHECK);
		jdbc.execute("ALTER TABLE usuario DROP COLUMN cumple_mes, DROP COLUMN cumple_dia");
		ejecutarV31_14();
		assertEquals(conLasNuevas(), columnasUsuario());

		// No había llegado a hacer nada.
		jdbc.execute("ALTER TABLE usuario DROP CHECK " + CHECK);
		jdbc.execute("ALTER TABLE usuario DROP COLUMN cumple_mes, DROP COLUMN cumple_dia, DROP COLUMN nombre_preferido");
		ejecutarV31_14();
		ejecutarV31_14();
		assertEquals(conLasNuevas(), columnasUsuario());
		Map<String, Object> fila = postcheck();
		for (String columna : List.of("columna_nombre_preferido", "columna_cumple_dia", "columna_cumple_mes",
				"check_cumple")) {
			assertEquals(1L, numero(fila, columna), columna);
		}
		assertEquals(0L, numero(fila, "cuentas_con_perfil"));
		assertEquals(usuariosAntes, sinNuevas(usuarios()));
		assertThrows(DataAccessException.class, () -> cumple("14", "NULL"), "el CHECK sigue activo");
	}

	// ------------------------------------------------------------------ utilidades

	private void cumple(String dia, String mes) {
		jdbc.update("UPDATE usuario SET cumple_dia = " + dia + ", cumple_mes = " + mes + " WHERE id = ?", cuenta);
	}

	private void ejecutarV31_14() throws Exception {
		try (Connection conexion = dataSource.getConnection()) {
			ScriptUtils.executeSqlScript(conexion,
					new EncodedResource(new ClassPathResource(V31_14), StandardCharsets.UTF_8));
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

	private static List<Map<String, Object>> sinNuevas(List<Map<String, Object>> filas) {
		return filas.stream().map(f -> {
			Map<String, Object> copia = new LinkedHashMap<>(f);
			NUEVAS.forEach(copia::remove);
			return copia;
		}).toList();
	}

	private List<String> conLasNuevas() {
		List<String> esperadas = new ArrayList<>(columnasUsuarioAntes);
		esperadas.addAll(NUEVAS);
		return esperadas;
	}

	private List<String> columnasUsuario() {
		return jdbc.queryForList("""
				SELECT COLUMN_NAME FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'usuario' ORDER BY ORDINAL_POSITION""", String.class);
	}

	private String tipo(String columna) {
		return jdbc.queryForObject("""
				SELECT CONCAT(COLUMN_TYPE, ' ', IS_NULLABLE) FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'usuario' AND COLUMN_NAME = ?""", String.class, columna);
	}

	private Map<String, Object> postcheck() throws Exception {
		return SedesVinculosMigracionIntegrationTest.ejecutarScriptDocs(dataSource, "postcheck-perfil-cuenta.sql").get(0)
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
