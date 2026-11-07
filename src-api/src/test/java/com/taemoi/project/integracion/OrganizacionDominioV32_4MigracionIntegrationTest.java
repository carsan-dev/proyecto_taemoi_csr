package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
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
 * V32_4 (fase 4, escuela por dominio, pieza 1; {@code docs/multi-escuela/fase4-dominio-plan.md} §4.1): tabla global
 * {@code organizacion_dominio} con la semilla {@code moiskimdo.es} → 1 (principal), sobre una base justo antes de V32_4
 * con otra escuela. Solo amplía: ninguna columna, índice, restricción ni fila existente cambia. Restricciones (host único
 * sin distinguir mayúsculas, clave ajena a organización), repetirla, también tras un fallo a medias, y
 * {@code docs/multi-escuela/postcheck-organizacion-dominio.sql}. Datos inventados.
 */
@Tag("migracion")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrganizacionDominioV32_4MigracionIntegrationTest extends MySqlIntegrationTest {

	private static final String ESQUEMA = "taemoi_v32_4_it";
	private static final String V32_4 = "db/migration/V32_4__organizacion_dominio.sql";
	private static final String NUEVAS = "('flyway_schema_history', 'organizacion_dominio')";
	private static final String COLUMNAS_DEL_ESQUEMA = """
			SELECT TABLE_NAME, COLUMN_NAME, ORDINAL_POSITION, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, EXTRA,
			       CHARACTER_SET_NAME, COLLATION_NAME
			FROM information_schema.COLUMNS
			WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME NOT IN %s
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
			"membresia", "SELECT * FROM membresia ORDER BY id");

	DataSource dataSource;
	JdbcTemplate jdbc;
	long otra;
	List<Map<String, Object>> columnasAntes;
	List<Map<String, Object>> indicesAntes;
	List<Map<String, Object>> restriccionesAntes;
	Map<String, List<Map<String, Object>>> filasAntes = new LinkedHashMap<>();

	@BeforeAll
	void migrar() {
		dataSource = recrear(ESQUEMA);
		jdbc = new JdbcTemplate(dataSource);
		// Justo antes de V32_4, sea cual sea la anterior (otra rama puede añadir una entre 32.2 y 32.4).
		flyway(dataSource, anteriorA("32.4")).migrate();

		jdbc.update("INSERT INTO organizacion (slug, nombre) VALUES ('otra-v32-4', 'Otra escuela V32_4')");
		otra = jdbc.queryForObject("SELECT id FROM organizacion WHERE slug = 'otra-v32-4'", Long.class);

		columnasAntes = jdbc.queryForList(COLUMNAS_DEL_ESQUEMA);
		indicesAntes = jdbc.queryForList(INDICES_DEL_ESQUEMA);
		restriccionesAntes = jdbc.queryForList(RESTRICCIONES_DEL_ESQUEMA);
		FILAS.forEach((tabla, consulta) -> filasAntes.put(tabla, jdbc.queryForList(consulta)));
		// Hasta V32_4: las migraciones posteriores tendrán su propio test.
		flyway(dataSource, "32.4").migrate();
	}

	@Test
	@Order(1)
	void llegaAV32_4() {
		assertEquals("32.4", jdbc.queryForObject(
				"SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1",
				String.class));
	}

	@Test
	@Order(2)
	void tabla_conLasColumnasEIndicesPedidos() {
		assertEquals(List.of("id bigint NO", "organizacion_id bigint NO", "host varchar(253) NO", "principal tinyint(1) NO",
				"creado_en datetime NO"), columnas("organizacion_dominio"));
		assertEquals(List.of("host"), indice("organizacion_dominio", "uk_organizacion_dominio_host", 0));
		assertEquals(List.of("organizacion_id"), indice("organizacion_dominio", "idx_organizacion_dominio_organizacion", 1));
		assertEquals(1L, jdbc.queryForObject("""
				SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS
				WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'organizacion_dominio'
				  AND REFERENCED_TABLE_NAME = 'organizacion' AND DELETE_RULE = 'NO ACTION'""", Long.class));
	}

	@Test
	@Order(3)
	void semilla_moiskimdoEnLaUnoYPrincipal() {
		assertEquals(List.of(Map.of("organizacion_id", 1L, "host", "moiskimdo.es", "principal", true)),
				jdbc.queryForList("SELECT organizacion_id, host, principal FROM organizacion_dominio"));
	}

	/** Solo amplía: nada del esquema cambia salvo la tabla nueva y las filas existentes siguen igual. */
	@Test
	@Order(4)
	void soloAmplia() {
		assertEquals(columnasAntes, jdbc.queryForList(COLUMNAS_DEL_ESQUEMA), "ninguna columna existente cambia");
		assertEquals(indicesAntes, jdbc.queryForList(INDICES_DEL_ESQUEMA), "ningún índice cambia");
		assertEquals(restriccionesAntes, jdbc.queryForList(RESTRICCIONES_DEL_ESQUEMA),
				"ninguna restricción existente cambia");
		FILAS.forEach((tabla, consulta) -> assertEquals(filasAntes.get(tabla), jdbc.queryForList(consulta), tabla));
	}

	@Test
	@Order(5)
	void postcheck_justoTrasDesplegar() throws Exception {
		Map<String, Object> fila = postcheck();
		for (String columna : List.of("flyway_32_4", "tabla", "clave_ajena", "host_unico", "moiskimdo_principal_en_1",
				"dominios")) {
			assertEquals(1L, numero(fila, columna), columna);
		}
		for (String columna : List.of("hosts_no_normalizados", "escuelas_con_varios_principales",
				"escuelas_sin_principal")) {
			assertEquals(0L, numero(fila, columna), columna);
		}
	}

	/** Host único sin distinguir mayúsculas (collation ai_ci) y escuela existente obligatoria. */
	@Test
	@Order(6)
	void restricciones() {
		assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
				"INSERT INTO organizacion_dominio (organizacion_id, host, principal) VALUES (?, 'MOISKIMDO.ES', FALSE)",
				otra), "host repetido");
		assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
				"INSERT INTO organizacion_dominio (organizacion_id, host, principal) VALUES (999999, 'nadie.test', TRUE)"),
				"escuela inexistente");
		jdbc.update("INSERT INTO organizacion_dominio (organizacion_id, host) VALUES (?, 'otra.test')", otra);
		assertEquals(Boolean.FALSE, jdbc.queryForObject(
				"SELECT principal FROM organizacion_dominio WHERE host = 'otra.test'", Boolean.class), "principal por defecto");
		jdbc.update("DELETE FROM organizacion_dominio WHERE host = 'otra.test'");
	}

	@Test
	@Order(7)
	void repetirla_noDuplicaNiPisaLoGuardado() throws Exception {
		jdbc.update("INSERT INTO organizacion_dominio (organizacion_id, host, principal) VALUES (?, 'otra.test', TRUE)",
				otra);
		ejecutarV32_4();
		ejecutarV32_4();
		assertEquals(2L, jdbc.queryForObject("SELECT COUNT(*) FROM organizacion_dominio", Long.class));
		assertEquals(1L, jdbc.queryForObject(
				"SELECT COUNT(*) FROM organizacion_dominio WHERE host = 'moiskimdo.es' AND organizacion_id = 1", Long.class));
		jdbc.update("DELETE FROM organizacion_dominio WHERE host = 'otra.test'");
	}

	/** Si se borró la semilla (o falló a medias), volver a ejecutarla la repone; sin la tabla, la crea. */
	@Test
	@Order(8)
	void trasUnFalloAMedias_laCompleta() throws Exception {
		jdbc.update("DELETE FROM organizacion_dominio");
		ejecutarV32_4();
		assertEquals(1L, numero(postcheck(), "moiskimdo_principal_en_1"));

		jdbc.execute("DROP TABLE organizacion_dominio");
		ejecutarV32_4();
		Map<String, Object> fila = postcheck();
		assertEquals(1L, numero(fila, "tabla"));
		assertEquals(1L, numero(fila, "clave_ajena"));
		assertEquals(1L, numero(fila, "moiskimdo_principal_en_1"));
	}

	/** Las comprobaciones «SIEMPRE 0» avisan de datos que el código no debe escribir nunca. */
	@Test
	@Order(9)
	void postcheck_avisaDeDatosRaros() throws Exception {
		jdbc.update("INSERT INTO organizacion_dominio (organizacion_id, host, principal) VALUES (?, 'Otra.Test.', TRUE)",
				otra);
		jdbc.update("INSERT INTO organizacion_dominio (organizacion_id, host, principal) VALUES (?, 'otra2.test:443', TRUE)",
				otra);
		jdbc.update("INSERT INTO organizacion_dominio (organizacion_id, host, principal) VALUES (1, 'www.moiskimdo.es', FALSE)");
		Map<String, Object> fila = postcheck();
		assertEquals(2L, numero(fila, "hosts_no_normalizados"));
		assertEquals(1L, numero(fila, "escuelas_con_varios_principales"));
		jdbc.update("UPDATE organizacion_dominio SET principal = FALSE WHERE organizacion_id = ?", otra);
		assertEquals(1L, numero(postcheck(), "escuelas_sin_principal"));
		jdbc.update("DELETE FROM organizacion_dominio WHERE host <> 'moiskimdo.es'");
	}

	// ------------------------------------------------------------------ utilidades

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

	private void ejecutarV32_4() throws Exception {
		try (Connection conexion = dataSource.getConnection()) {
			ScriptUtils.executeSqlScript(conexion,
					new EncodedResource(new ClassPathResource(V32_4), StandardCharsets.UTF_8));
		}
	}

	private Map<String, Object> postcheck() throws Exception {
		return SedesVinculosMigracionIntegrationTest
				.ejecutarScriptDocs(dataSource, "postcheck-organizacion-dominio.sql").get(0).get(0);
	}

	private static long numero(Map<String, Object> fila, String columna) {
		return ((Number) fila.get(columna)).longValue();
	}

	/** La última versión del repositorio anterior a {@code version}. */
	private String anteriorA(String version) {
		MigrationVersion limite = MigrationVersion.fromVersion(version);
		return Arrays.stream(Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
				.placeholders(Map.of(ContraccionCatalogoMigracionIntegrationTest.PERMITIR_CON_V27, "true")).load().info().all())
				.map(MigrationInfo::getVersion)
				.filter(v -> v != null && v.compareTo(limite) < 0)
				.max(MigrationVersion::compareTo).orElseThrow().getVersion();
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
