package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
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
 * V32_1 (E10-5, contraer): {@code grupo.sede_id} pasa a NOT NULL y el nombre del grupo es único en su sede
 * ({@code uk_grupo_sede_nombre (sede_id, nombre)}, con la colación de la columna: «Infantil», «infantil» e «Infantíl»
 * chocan).
 * <ul>
 * <li>Si hay un grupo sin sede o dos nombres iguales en una sede, se detiene antes de cambiar nada (truco de la
 * tabla inexistente, como V28) y, tras corregir los datos, {@code repair} y {@code migrate} la completan.</li>
 * <li>La clave ajena {@code fk_grupo_sede} se conserva.</li>
 * </ul>
 * Repetirla no falla ni cambia nada, también desde un estado intermedio. Datos inventados.
 */
@Tag("migracion")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GrupoSedeObligatoriaV32_1MigracionIntegrationTest extends MySqlIntegrationTest {

	private static final String ESQUEMA = "taemoi_v32_1_it";
	private static final String ESQUEMA_SIN_SEDE = "taemoi_v32_1_sin_sede_it";
	private static final String ESQUEMA_REPETIDOS = "taemoi_v32_1_repetidos_it";
	private static final String ESQUEMA_INTERMEDIO = "taemoi_v32_1_intermedio_it";
	private static final String V32_1 = "db/migration/V32_1__grupo_sede_obligatoria.sql";
	private static final String ERROR_SIN_SEDE = "v32_1_hay_grupos_sin_sede";
	private static final String ERROR_REPETIDOS = "v32_1_hay_nombres_de_grupo_repetidos_en_una_sede";

	DataSource dataSource;
	JdbcTemplate jdbc;
	long sedeA;
	long sedeB;
	List<Map<String, Object>> gruposAntes;

	@BeforeAll
	void migrar() {
		dataSource = recrear(ESQUEMA);
		jdbc = new JdbcTemplate(dataSource);
		flyway(dataSource, "32").migrate();
		sedeA = sedeDeLaOrganizacion1(jdbc);
		sedeB = nuevaSede(jdbc, "Segunda sede V32_1");
		// Mismo nombre en sedes distintas: permitido.
		grupo(jdbc, "Infantil", sedeA);
		grupo(jdbc, "Infantil", sedeB);
		grupo(jdbc, "Adultos", sedeA);
		gruposAntes = grupos(jdbc);
		flyway(dataSource, "32.1").migrate();
	}

	@Test
	@Order(1)
	void llegaAV32_1_sinCambiarLosGrupos() {
		assertEquals("32.1", ultimaVersion(jdbc));
		assertEquals(gruposAntes, grupos(jdbc));
	}

	/** Caso limpio: la columna es NOT NULL, el índice único existe y la clave ajena sigue sin SET NULL. */
	@Test
	@Order(2)
	void sedeObligatoria_indiceUnico_yClaveAjenaConservada() {
		assertContraido(jdbc);
		String regla = reglaDeBorradoDeLaClaveAjena(jdbc);
		assertTrue(List.of("RESTRICT", "NO ACTION").contains(regla), "fk_grupo_sede sin ON DELETE SET NULL: " + regla);
	}

	/** Un nombre repetido en la sede (otras mayúsculas o tildes) y un grupo sin sede dan error de integridad. */
	@Test
	@Order(3)
	void insertarRepetidoOSinSede_daErrorDeIntegridad() {
		for (String nombre : List.of("Infantil", "infantil", "INFANTÍL")) {
			assertThrows(DataIntegrityViolationException.class, () -> grupo(jdbc, nombre, sedeA), nombre);
		}
		assertThrows(DataIntegrityViolationException.class, () -> grupo(jdbc, "Sin sede", null));
		assertEquals(gruposAntes, grupos(jdbc));
	}

	/** La consulta «Solo tras V32_1» del postcheck de la E10 da NO y 1. */
	@Test
	@Order(4)
	void postcheckE10_trasV32_1_daNoY1() throws Exception {
		assertEquals(Map.of("sede_id_admite_null", "NO", "indice_unico", 1L), postcheckV32_1(dataSource));
	}

	/** Repetirla (dos veces a mano y otra por Flyway tras borrar su fila del historial) no falla ni cambia nada. */
	@Test
	@Order(5)
	void repetirla_noFallaNiCambiaNada() throws Exception {
		Map<String, List<Map<String, Object>>> antes = estado(jdbc);
		ejecutarV32_1(dataSource);
		ejecutarV32_1(dataSource);
		assertEquals(antes, estado(jdbc));

		jdbc.update("DELETE FROM flyway_schema_history WHERE version = '32.1'");
		flyway(dataSource, "32.1").migrate();
		assertEquals("32.1", ultimaVersion(jdbc));
		assertEquals(antes, estado(jdbc));
	}

	/** (a) Con un grupo sin sede se detiene sin cambiar nada; con la sede puesta, repair y migrate la completan. */
	@Test
	@Order(6)
	void grupoSinSede_fallaSinCambiarNada_yTrasCorregirloTerminaBien() {
		DataSource fuente = recrear(ESQUEMA_SIN_SEDE);
		JdbcTemplate f = new JdbcTemplate(fuente);
		flyway(fuente, "32").migrate();
		long sede = sedeDeLaOrganizacion1(f);
		grupo(f, "Con sede", sede);
		long sinSede = grupo(f, "Heredado sin sede", null);
		Map<String, List<Map<String, Object>>> antes = estado(f);

		FlywayException error = assertThrows(FlywayException.class, () -> flyway(fuente, "32.1").migrate());
		assertTrue(mensajeCompleto(error).contains(ERROR_SIN_SEDE), mensajeCompleto(error));
		assertEquals(antes, estado(f), "no cambia nada");
		assertEquals("32", ultimaVersion(f));

		f.update("UPDATE grupo SET sede_id = ? WHERE id = ?", sede, sinSede);
		Flyway segundo = flyway(fuente, "32.1");
		segundo.repair();
		segundo.migrate();
		assertEquals("32.1", ultimaVersion(f));
		assertContraido(f);
	}

	/** (b) «Infantil» e «infantil» en la misma sede: se detiene sin cambiar nada. */
	@Test
	@Order(7)
	void nombresRepetidosEnUnaSede_fallaSinCambiarNada() {
		DataSource fuente = recrear(ESQUEMA_REPETIDOS);
		JdbcTemplate r = new JdbcTemplate(fuente);
		flyway(fuente, "32").migrate();
		long sede = sedeDeLaOrganizacion1(r);
		grupo(r, "Infantil", sede);
		grupo(r, "infantil", sede);
		Map<String, List<Map<String, Object>>> antes = estado(r);

		FlywayException error = assertThrows(FlywayException.class, () -> flyway(fuente, "32.1").migrate());
		assertTrue(mensajeCompleto(error).contains(ERROR_REPETIDOS), mensajeCompleto(error));
		assertEquals(antes, estado(r), "no cambia nada");
		assertEquals("32", ultimaVersion(r));
		assertEquals("YES", admiteNull(r));

		// El postcheck no da por bueno un índice con ese nombre que no sea único.
		assertEquals(Map.of("sede_id_admite_null", "YES", "indice_unico", 0L), postcheckV32_1(fuente));
		r.execute("ALTER TABLE grupo ADD INDEX uk_grupo_sede_nombre (sede_id, nombre)");
		assertEquals(Map.of("sede_id_admite_null", "YES", "indice_unico", 0L), postcheckV32_1(fuente));
	}

	/** Estado intermedio (fallo entre los dos DDL): columna ya NOT NULL y sin índice. La repetición lo completa. */
	@Test
	@Order(8)
	void estadoIntermedio_laRepeticionLoCompleta() throws Exception {
		DataSource fuente = recrear(ESQUEMA_INTERMEDIO);
		JdbcTemplate k = new JdbcTemplate(fuente);
		flyway(fuente, "32").migrate();
		grupo(k, "Infantil", sedeDeLaOrganizacion1(k));
		k.execute("ALTER TABLE grupo MODIFY sede_id BIGINT NOT NULL");
		assertEquals(0, indiceUnico(k).size());
		ejecutarV32_1(fuente);
		assertContraido(k);
	}

	// ------------------------------------------------------------------ utilidades

	/** La última consulta de postcheck-e10.sql («Solo tras V32_1»). */
	private static Map<String, Object> postcheckV32_1(DataSource fuente) {
		try {
			List<List<Map<String, Object>>> resultados = SedesVinculosMigracionIntegrationTest.ejecutarScriptDocs(fuente,
					"postcheck-e10.sql");
			Map<String, Object> ultima = resultados.get(resultados.size() - 1).get(0);
			return Map.of("sede_id_admite_null", ultima.get("sede_id_admite_null"), "indice_unico",
					((Number) ultima.get("indice_unico")).longValue());
		} catch (java.io.IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
	}

	private static void assertContraido(JdbcTemplate j) {
		assertEquals("NO", admiteNull(j), "grupo.sede_id NOT NULL");
		assertEquals("bigint", j.queryForObject("""
				SELECT DATA_TYPE FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'grupo' AND COLUMN_NAME = 'sede_id'""", String.class));
		assertEquals(List.of(Map.of("NON_UNIQUE", 0L, "SEQ_IN_INDEX", 1L, "COLUMN_NAME", "sede_id"),
				Map.of("NON_UNIQUE", 0L, "SEQ_IN_INDEX", 2L, "COLUMN_NAME", "nombre")), indiceUnico(j));
		assertNotEquals("SET NULL", reglaDeBorradoDeLaClaveAjena(j));
	}

	private static List<Map<String, Object>> indiceUnico(JdbcTemplate j) {
		return j.queryForList("""
				SELECT NON_UNIQUE, SEQ_IN_INDEX, COLUMN_NAME FROM information_schema.STATISTICS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'grupo' AND INDEX_NAME = 'uk_grupo_sede_nombre'
				ORDER BY SEQ_IN_INDEX""").stream().map(f -> Map.<String, Object>of(
				"NON_UNIQUE", ((Number) f.get("NON_UNIQUE")).longValue(),
				"SEQ_IN_INDEX", ((Number) f.get("SEQ_IN_INDEX")).longValue(),
				"COLUMN_NAME", f.get("COLUMN_NAME"))).toList();
	}

	private static String admiteNull(JdbcTemplate j) {
		return j.queryForObject("""
				SELECT IS_NULLABLE FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'grupo' AND COLUMN_NAME = 'sede_id'""", String.class);
	}

	private static String reglaDeBorradoDeLaClaveAjena(JdbcTemplate j) {
		return j.queryForObject("""
				SELECT DELETE_RULE FROM information_schema.REFERENTIAL_CONSTRAINTS
				WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'grupo' AND CONSTRAINT_NAME = 'fk_grupo_sede'
				  AND REFERENCED_TABLE_NAME = 'sede'""", String.class);
	}

	/** Filas y esquema que V32_1 toca, para comparar antes y después. */
	private static Map<String, List<Map<String, Object>>> estado(JdbcTemplate j) {
		Map<String, List<Map<String, Object>>> estado = new LinkedHashMap<>();
		estado.put("grupo", grupos(j));
		estado.put("columnas", j.queryForList("""
				SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'grupo' ORDER BY ORDINAL_POSITION"""));
		estado.put("indices", j.queryForList("""
				SELECT INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX, COLUMN_NAME FROM information_schema.STATISTICS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'grupo' ORDER BY INDEX_NAME, SEQ_IN_INDEX"""));
		estado.put("restricciones", j.queryForList("""
				SELECT CONSTRAINT_NAME, CONSTRAINT_TYPE FROM information_schema.TABLE_CONSTRAINTS
				WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'grupo' ORDER BY CONSTRAINT_NAME"""));
		return estado;
	}

	private static List<Map<String, Object>> grupos(JdbcTemplate j) {
		return j.queryForList("SELECT * FROM grupo ORDER BY id");
	}

	private static long sedeDeLaOrganizacion1(JdbcTemplate j) {
		return j.queryForObject("SELECT MIN(id) FROM sede WHERE organizacion_id = 1", Long.class);
	}

	private static long nuevaSede(JdbcTemplate j, String nombre) {
		j.update("INSERT INTO sede (organizacion_id, nombre) VALUES (1, ?)", nombre);
		return j.queryForObject("SELECT id FROM sede WHERE organizacion_id = 1 AND nombre = ?", Long.class, nombre);
	}

	private static long grupo(JdbcTemplate j, String nombre, Long sede) {
		j.update("""
				INSERT INTO grupo (nombre, organizacion_id, disciplina_id, sede_id)
				VALUES (?, 1, (SELECT MIN(d.id) FROM disciplina d WHERE d.organizacion_id = 1), ?)""", nombre, sede);
		return j.queryForObject("SELECT MAX(id) FROM grupo", Long.class);
	}

	private static String ultimaVersion(JdbcTemplate j) {
		return j.queryForObject(
				"SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1",
				String.class);
	}

	private static String mensajeCompleto(Throwable error) {
		StringBuilder sb = new StringBuilder();
		for (Throwable t = error; t != null; t = t.getCause()) {
			sb.append(t.getMessage()).append(" | ");
		}
		return sb.toString();
	}

	private static void ejecutarV32_1(DataSource fuente) throws Exception {
		try (Connection conexion = fuente.getConnection()) {
			ScriptUtils.executeSqlScript(conexion,
					new EncodedResource(new ClassPathResource(V32_1), StandardCharsets.UTF_8));
		}
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
