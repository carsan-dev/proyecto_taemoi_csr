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
 * V32_7 (mascota del panel por escuela): {@code organizacion.mascota} VARCHAR(16) NOT NULL DEFAULT 'TAMI' al final de
 * la tabla, el CHECK {@code ck_organizacion_mascota} (TAMI o MAKI) y Moiskimdo (organización 1) en MAKI, sobre una base
 * en V32_6 con dos escuelas. Las demás quedan en TAMI y nada más cambia; el INSERT sin la columna (el código anterior y
 * el alta nativa) la deja en TAMI; la columna admite INSTANT; repetirla, también tras un fallo a medias, no cambia nada
 * y completa lo que faltara. Ejecuta {@code docs/multi-escuela/postcheck-32_7.sql}. Datos inventados.
 */
@Tag("migracion")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MascotaV32_7MigracionIntegrationTest extends MySqlIntegrationTest {

	private static final String ESQUEMA = "taemoi_v32_7_it";
	private static final String V32_7 = "db/migration/V32_7__organizacion_mascota.sql";
	private static final String NUEVA = "mascota";
	private static final String CHECK = "ck_organizacion_mascota";
	/** Todo el esquema salvo la columna nueva y el historial de Flyway (que gana su fila). */
	private static final String COLUMNAS_DEL_ESQUEMA = """
			SELECT TABLE_NAME, COLUMN_NAME, ORDINAL_POSITION, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, EXTRA,
			       CHARACTER_SET_NAME, COLLATION_NAME
			FROM information_schema.COLUMNS
			WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME <> 'flyway_schema_history'
			  AND NOT (TABLE_NAME = 'organizacion' AND COLUMN_NAME = 'mascota')
			ORDER BY TABLE_NAME, ORDINAL_POSITION""";
	private static final String INDICES_DEL_ESQUEMA = """
			SELECT TABLE_NAME, INDEX_NAME, COLUMN_NAME, NON_UNIQUE, SEQ_IN_INDEX FROM information_schema.STATISTICS
			WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME <> 'flyway_schema_history'
			ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX""";
	private static final String RESTRICCIONES_DEL_ESQUEMA = """
			SELECT TABLE_NAME, CONSTRAINT_NAME, CONSTRAINT_TYPE FROM information_schema.TABLE_CONSTRAINTS
			WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME <> 'flyway_schema_history'
			  AND CONSTRAINT_NAME <> 'ck_organizacion_mascota'
			ORDER BY TABLE_NAME, CONSTRAINT_NAME""";
	private static final List<String> TABLAS_CON_FILAS = List.of("membresia", "usuario");

	DataSource dataSource;
	JdbcTemplate jdbc;
	long otra;
	List<String> columnasOrganizacionAntes;
	List<Map<String, Object>> columnasAntes;
	List<Map<String, Object>> indicesAntes;
	List<Map<String, Object>> restriccionesAntes;
	List<Map<String, Object>> organizacionesAntes;
	Map<String, List<Map<String, Object>>> filasAntes = new LinkedHashMap<>();

	@BeforeAll
	void migrar() {
		dataSource = recrear(ESQUEMA);
		jdbc = new JdbcTemplate(dataSource);
		flyway(dataSource, "32.6").migrate();
		jdbc.update("INSERT INTO organizacion (slug, nombre) VALUES ('otra-v32-7', 'Otra escuela V32_7')");
		otra = jdbc.queryForObject("SELECT id FROM organizacion WHERE slug = 'otra-v32-7'", Long.class);
		long cuenta = cuenta("v32-7-propietario@example.test");
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, 'PROPIETARIO')", cuenta,
				otra);

		columnasOrganizacionAntes = columnasOrganizacion();
		columnasAntes = jdbc.queryForList(COLUMNAS_DEL_ESQUEMA);
		indicesAntes = jdbc.queryForList(INDICES_DEL_ESQUEMA);
		restriccionesAntes = jdbc.queryForList(RESTRICCIONES_DEL_ESQUEMA);
		organizacionesAntes = organizaciones();
		assertTrue(organizacionesAntes.size() >= 2);
		assertTrue(organizacionesAntes.stream().anyMatch(f -> ((Number) f.get("id")).longValue() == 1L),
				"la base de partida tiene la organización 1");
		for (String tabla : TABLAS_CON_FILAS) {
			filasAntes.put(tabla, jdbc.queryForList("SELECT * FROM " + tabla + " ORDER BY id"));
		}
		// Hasta V32_7: las migraciones posteriores tendrán su propio test.
		flyway(dataSource, "32.7").migrate();
	}

	@Test
	@Order(1)
	void llegaAV32_7() {
		assertEquals("32.7", jdbc.queryForObject(
				"SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1",
				String.class));
	}

	@Test
	@Order(2)
	void añadeLaColumnaAlFinal_noNula_conDefectoTami() {
		assertEquals(conLaNueva(), columnasOrganizacion());
		assertEquals("varchar(16) NO TAMI", jdbc.queryForObject("""
				SELECT CONCAT(COLUMN_TYPE, ' ', IS_NULLABLE, ' ', COLUMN_DEFAULT) FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'organizacion' AND COLUMN_NAME = ?""", String.class,
				NUEVA));
	}

	/** Solo amplía: nada del esquema cambia salvo la columna y el CHECK; Moiskimdo en MAKI y el resto en TAMI. */
	@Test
	@Order(3)
	void soloAmplia_moiskimdoMaki_elRestoTami() {
		assertEquals(columnasAntes, jdbc.queryForList(COLUMNAS_DEL_ESQUEMA), "ninguna columna existente cambia");
		assertEquals(indicesAntes, jdbc.queryForList(INDICES_DEL_ESQUEMA), "ningún índice cambia");
		assertEquals(restriccionesAntes, jdbc.queryForList(RESTRICCIONES_DEL_ESQUEMA),
				"ninguna restricción existente cambia");
		for (String tabla : TABLAS_CON_FILAS) {
			assertEquals(filasAntes.get(tabla), jdbc.queryForList("SELECT * FROM " + tabla + " ORDER BY id"), tabla);
		}
		List<Map<String, Object>> ahora = organizaciones();
		for (Map<String, Object> fila : ahora) {
			long id = ((Number) fila.get("id")).longValue();
			assertEquals(id == 1L ? "MAKI" : "TAMI", fila.get(NUEVA), "organización " + id);
		}
		assertEquals(organizacionesAntes, sinLaNueva(ahora));
	}

	@Test
	@Order(4)
	void postcheck_justoTrasDesplegar() throws Exception {
		List<List<Map<String, Object>>> resultados = postcheck();
		Map<String, Object> fila = resultados.get(0).get(0);
		for (String columna : List.of("flyway_32_7", "ultima_es_32_7", "columna_mascota", "check_mascota",
				"organizacion_1_maki")) {
			assertEquals(1L, numero(fila, columna), columna);
		}
		assertEquals(0L, numero(fila, "otras_en_maki"));
		assertEquals((long) organizacionesAntes.size(), numero(fila, "escuelas"));

		List<Map<String, Object>> lista = resultados.get(1);
		assertEquals(organizacionesAntes.size(), lista.size());
		for (Map<String, Object> escuela : lista) {
			assertEquals(List.of("id", "nombre", "mascota"), new ArrayList<>(escuela.keySet()));
			long id = ((Number) escuela.get("id")).longValue();
			assertEquals(id == 1L ? "MAKI" : "TAMI", escuela.get("mascota"), "organización " + id);
		}
	}

	/** El código anterior y el alta nativa insertan sin la columna: queda en TAMI. */
	@Test
	@Order(5)
	void insertarSinLaColumna_quedaEnTami_yElCambioSeCuenta() throws Exception {
		jdbc.update("INSERT INTO organizacion (slug, nombre) VALUES ('nueva-v32-7', 'Nueva V32_7')");
		long nueva = jdbc.queryForObject("SELECT id FROM organizacion WHERE slug = 'nueva-v32-7'", Long.class);
		assertEquals("TAMI",
				jdbc.queryForObject("SELECT " + NUEVA + " FROM organizacion WHERE id = ?", String.class, nueva));
		jdbc.update("UPDATE organizacion SET " + NUEVA + " = 'MAKI' WHERE id = ?", nueva);
		assertEquals(1L, numero(postcheck().get(0).get(0), "otras_en_maki"));
		jdbc.update("DELETE FROM organizacion WHERE id = ?", nueva);
		assertEquals(0L, numero(postcheck().get(0).get(0), "otras_en_maki"));
	}

	@Test
	@Order(6)
	void elCheck_soloDejaTamiOMaki() {
		for (String valida : List.of("'MAKI'", "'TAMI'")) {
			mascota(valida);
		}
		for (String invalida : List.of("'OTRA'", "''", "'maki'", "'Tami'", "'MAKI '", "NULL")) {
			assertThrows(DataAccessException.class, () -> mascota(invalida), invalida);
		}
		mascota("'TAMI'");
	}

	/** Las afirmaciones de coste, en el MySQL de los tests (8.0, como producción). */
	@Test
	@Order(7)
	void laColumnaEsInstant_yElCheckSeHaceConCopia() {
		jdbc.execute("ALTER TABLE organizacion DROP CHECK " + CHECK);
		jdbc.execute("ALTER TABLE organizacion DROP COLUMN " + NUEVA);
		jdbc.execute("ALTER TABLE organizacion ADD COLUMN " + NUEVA
				+ " VARCHAR(16) NOT NULL DEFAULT 'TAMI', ALGORITHM=INSTANT");
		String expresion = "CHECK (" + NUEVA + " COLLATE utf8mb4_0900_bin IN ('TAMI', 'MAKI'))";
		DataAccessException sinCopia = assertThrows(DataAccessException.class, () -> jdbc
				.execute("ALTER TABLE organizacion ADD CONSTRAINT " + CHECK + " " + expresion + ", ALGORITHM=INPLACE"));
		assertTrue(sinCopia.getMessage().contains("ALGORITHM=INPLACE is not supported"), sinCopia.getMessage());
		jdbc.execute("ALTER TABLE organizacion ADD CONSTRAINT " + CHECK + " " + expresion + ", ALGORITHM=COPY");
		assertEquals(conLaNueva(), columnasOrganizacion());
		// Deja la organización 1 como la deja V32_7.
		jdbc.update("UPDATE organizacion SET " + NUEVA + " = 'MAKI' WHERE id = 1");
	}

	@Test
	@Order(8)
	void repetirla_conservaLoGuardado() throws Exception {
		mascota("'MAKI'");
		List<Map<String, Object>> antes = organizaciones();
		ejecutarV32_7();
		ejecutarV32_7();
		assertEquals(antes, organizaciones());
		assertEquals(conLaNueva(), columnasOrganizacion());
		assertEquals(1L, numero(postcheck().get(0).get(0), "check_mascota"));
		mascota("'TAMI'");
	}

	/** El DDL no es transaccional: se puede quedar a medias tras cada sentencia. Volver a ejecutarla lo completa. */
	@Test
	@Order(9)
	void trasUnFalloAMedias_laCompleta() throws Exception {
		// Solo faltaba el CHECK.
		jdbc.execute("ALTER TABLE organizacion DROP CHECK " + CHECK);
		ejecutarV32_7();
		assertEquals(1L, numero(postcheck().get(0).get(0), "check_mascota"));

		// Faltaba la organización 1 en MAKI.
		jdbc.update("UPDATE organizacion SET " + NUEVA + " = 'TAMI' WHERE id = 1");
		ejecutarV32_7();
		assertEquals(1L, numero(postcheck().get(0).get(0), "organizacion_1_maki"));

		// No había llegado a hacer nada.
		jdbc.execute("ALTER TABLE organizacion DROP CHECK " + CHECK);
		jdbc.execute("ALTER TABLE organizacion DROP COLUMN " + NUEVA);
		ejecutarV32_7();
		ejecutarV32_7();
		assertEquals(conLaNueva(), columnasOrganizacion());
		Map<String, Object> fila = postcheck().get(0).get(0);
		for (String columna : List.of("columna_mascota", "check_mascota", "organizacion_1_maki")) {
			assertEquals(1L, numero(fila, columna), columna);
		}
		assertEquals(0L, numero(fila, "otras_en_maki"));
		assertEquals(organizacionesAntes, sinLaNueva(organizaciones()));
		assertThrows(DataAccessException.class, () -> mascota("'OTRA'"), "el CHECK sigue activo");
	}

	// ------------------------------------------------------------------ utilidades

	private void mascota(String valor) {
		jdbc.update("UPDATE organizacion SET " + NUEVA + " = " + valor + " WHERE id = ?", otra);
	}

	private void ejecutarV32_7() throws Exception {
		try (Connection conexion = dataSource.getConnection()) {
			ScriptUtils.executeSqlScript(conexion,
					new EncodedResource(new ClassPathResource(V32_7), StandardCharsets.UTF_8));
		}
	}

	private long cuenta(String email) {
		jdbc.update("""
				INSERT INTO usuario (apellidos, contrasena, email, nombre, auth_provider)
				VALUES ('Prueba', 'fake-Password-123', ?, 'Cuenta', 'LOCAL')""", email);
		return jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", Long.class, email);
	}

	private List<Map<String, Object>> organizaciones() {
		return jdbc.queryForList("SELECT * FROM organizacion ORDER BY id");
	}

	private static List<Map<String, Object>> sinLaNueva(List<Map<String, Object>> filas) {
		return filas.stream().map(f -> {
			Map<String, Object> copia = new LinkedHashMap<>(f);
			copia.remove(NUEVA);
			return copia;
		}).toList();
	}

	private List<String> conLaNueva() {
		List<String> esperadas = new ArrayList<>(columnasOrganizacionAntes);
		esperadas.add(NUEVA);
		return esperadas;
	}

	private List<String> columnasOrganizacion() {
		return jdbc.queryForList("""
				SELECT COLUMN_NAME FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'organizacion' ORDER BY ORDINAL_POSITION""",
				String.class);
	}

	private List<List<Map<String, Object>>> postcheck() throws Exception {
		return SedesVinculosMigracionIntegrationTest.ejecutarScriptDocs(dataSource, "postcheck-32_7.sql");
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
