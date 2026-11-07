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
 * V32_5 (web por plantilla, pieza W1 de fase4-web-plan.md): las tablas {@code web_escuela} y {@code web_bloque}, sobre
 * una base en V32_4 (la anterior en su pila: escuela por dominio) con datos de dos escuelas. Solo amplía: ninguna columna,
 * índice, restricción ni fila existente cambia, y no crea filas (Moiskimdo, la 1, queda sin web de plantilla). Reglas
 * de borrado de cada clave ajena, clave única por escuela, repetirla, también tras un fallo a medias, y
 * {@code docs/multi-escuela/postcheck-web-escuela.sql}. Datos inventados.
 */
@Tag("migracion")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WebEscuelaV32_5MigracionIntegrationTest extends MySqlIntegrationTest {

	private static final String ESQUEMA = "taemoi_v32_5_it";
	private static final String V32_5 = "db/migration/V32_5__web_escuela.sql";
	private static final String NUEVAS = "('flyway_schema_history', 'web_escuela', 'web_bloque')";
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
			"sede", "SELECT * FROM sede ORDER BY id",
			"evento", "SELECT * FROM evento ORDER BY id",
			"imagen", "SELECT * FROM imagen ORDER BY id",
			"usuario", "SELECT id, email FROM usuario ORDER BY id");

	DataSource dataSource;
	JdbcTemplate jdbc;
	long otra, imagen, cuenta;
	List<Map<String, Object>> columnasAntes;
	List<Map<String, Object>> indicesAntes;
	List<Map<String, Object>> restriccionesAntes;
	Map<String, List<Map<String, Object>>> filasAntes = new LinkedHashMap<>();

	@BeforeAll
	void migrar() {
		dataSource = recrear(ESQUEMA);
		jdbc = new JdbcTemplate(dataSource);
		flyway(dataSource, "32.4").migrate();

		jdbc.update("INSERT INTO organizacion (slug, nombre) VALUES ('otra-v32-5', 'Otra escuela V32_5')");
		otra = jdbc.queryForObject("SELECT id FROM organizacion WHERE slug = 'otra-v32-5'", Long.class);
		jdbc.update("INSERT INTO sede (organizacion_id, nombre) VALUES (?, 'V32_5 sede')", otra);
		jdbc.update("INSERT INTO imagen (nombre, ruta, tipo, url, organizacion_id) VALUES ('v32-5.jpg',"
				+ " 'org-x/web/v32-5.jpg', 'image/jpeg', '/imagenes/org-x/web/v32-5.jpg', ?)", otra);
		imagen = jdbc.queryForObject("SELECT MAX(id) FROM imagen WHERE organizacion_id = ?", Long.class, otra);
		jdbc.update("""
				INSERT INTO evento (titulo, descripcion, visible, orden, organizacion_id, fecha_evento)
				VALUES ('V32_5 evento', 'Descripción', TRUE, 1, ?, '2026-12-01')""", otra);
		jdbc.update("""
				INSERT INTO usuario (apellidos, contrasena, email, nombre, auth_provider)
				VALUES ('Prueba', 'fake-Password-123', 'v32-5-propietario@example.test', 'Cuenta', 'LOCAL')""");
		cuenta = jdbc.queryForObject("SELECT id FROM usuario WHERE email = 'v32-5-propietario@example.test'", Long.class);

		columnasAntes = jdbc.queryForList(COLUMNAS_DEL_ESQUEMA);
		indicesAntes = jdbc.queryForList(INDICES_DEL_ESQUEMA);
		restriccionesAntes = jdbc.queryForList(RESTRICCIONES_DEL_ESQUEMA);
		FILAS.forEach((tabla, consulta) -> filasAntes.put(tabla, jdbc.queryForList(consulta)));
		// Hasta V32_5: las migraciones posteriores tendrán su propio test.
		flyway(dataSource, "32.5").migrate();
	}

	@Test
	@Order(1)
	void llegaAV32_5() {
		assertEquals("32.5", jdbc.queryForObject(
				"SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1",
				String.class));
	}

	@Test
	@Order(2)
	void tablas_conLasColumnasPedidas() {
		assertEquals(List.of("organizacion_id bigint NO", "tema varchar(30) NO", "publicada tinyint(1) NO",
				"publicada_en datetime YES", "publicada_por bigint YES", "actualizada_en datetime NO"),
				columnas("web_escuela"));
		assertEquals(List.of("id bigint NO", "organizacion_id bigint NO", "clave varchar(60) NO", "texto text YES",
				"imagen_id bigint YES", "orden int NO"), columnas("web_bloque"));
		assertEquals(List.of("organizacion_id"), indice("web_escuela", "PRIMARY", 0));
		assertEquals(List.of("organizacion_id,clave"), indice("web_bloque", "uk_web_bloque", 0));
		assertEquals(List.of("imagen_id"), indice("web_bloque", "idx_web_bloque_imagen", 1));
	}

	/** Solo amplía: nada del esquema cambia salvo lo nuevo, las filas siguen igual y las tablas nuevas, vacías. */
	@Test
	@Order(3)
	void soloAmplia() {
		assertEquals(columnasAntes, jdbc.queryForList(COLUMNAS_DEL_ESQUEMA), "ninguna columna existente cambia");
		assertEquals(indicesAntes, jdbc.queryForList(INDICES_DEL_ESQUEMA), "ningún índice cambia");
		assertEquals(restriccionesAntes, jdbc.queryForList(RESTRICCIONES_DEL_ESQUEMA),
				"ninguna restricción existente cambia");
		FILAS.forEach((tabla, consulta) -> assertEquals(filasAntes.get(tabla), jdbc.queryForList(consulta), tabla));
		assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM web_escuela", Long.class));
		assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM web_bloque", Long.class));
	}

	@Test
	@Order(4)
	void postcheck_justoTrasDesplegar() throws Exception {
		Map<String, Object> fila = postcheck();
		for (String columna : List.of("flyway_32_5", "ultima_es_32_5")) {
			assertEquals(1L, numero(fila, columna), columna);
		}
		assertEquals(2L, numero(fila, "tablas"));
		assertEquals(3L, numero(fila, "claves_ajenas"));
		for (String columna : List.of("webs", "bloques", "web_de_moiskimdo", "bloques_de_moiskimdo", "temas_desconocidos",
				"publicadas_sin_fecha", "imagenes_de_otra_escuela")) {
			assertEquals(0L, numero(fila, columna), columna);
		}
	}

	/** Una web por escuela, una clave por escuela, valores por defecto; publicada_por sin clave ajena a usuario. */
	@Test
	@Order(5)
	void reglas() {
		jdbc.update("INSERT INTO web_escuela (organizacion_id, tema) VALUES (?, 'chandal')", otra);
		assertEquals(Map.of("publicada", 0L, "publicada_en", 1L, "actualizada_en", 0L), jdbc.queryForMap("""
				SELECT CAST(publicada AS SIGNED) AS publicada, CAST(publicada_en IS NULL AS SIGNED) AS publicada_en,
				  CAST(actualizada_en IS NULL AS SIGNED) AS actualizada_en
				FROM web_escuela WHERE organizacion_id = ?""", otra), "sin publicar, sin fecha y con actualizada_en");
		assertThrows(DataIntegrityViolationException.class,
				() -> jdbc.update("INSERT INTO web_escuela (organizacion_id, tema) VALUES (?, 'estandar')", otra),
				"una fila por escuela");
		assertThrows(DataIntegrityViolationException.class,
				() -> jdbc.update("INSERT INTO web_escuela (organizacion_id, tema) VALUES (999999, 'estandar')"),
				"FK a organizacion");

		jdbc.update("INSERT INTO web_bloque (organizacion_id, clave, texto, imagen_id) VALUES (?, 'galeria.1', NULL, ?)",
				otra, imagen);
		assertEquals(0, jdbc.queryForObject("SELECT orden FROM web_bloque WHERE clave = 'galeria.1'", Integer.class));
		assertThrows(DataIntegrityViolationException.class,
				() -> jdbc.update("INSERT INTO web_bloque (organizacion_id, clave) VALUES (?, 'galeria.1')", otra),
				"clave única por escuela");
		jdbc.update("INSERT INTO web_bloque (organizacion_id, clave) VALUES (1, 'galeria.1')");
		assertThrows(DataIntegrityViolationException.class,
				() -> jdbc.update("DELETE FROM imagen WHERE id = ?", imagen), "la imagen de un bloque no se borra sola");

		jdbc.update("UPDATE web_escuela SET publicada = TRUE, publicada_en = '2026-11-07 10:00:00', publicada_por = ?"
				+ " WHERE organizacion_id = ?", cuenta, otra);
		assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE"
				+ " WHERE TABLE_SCHEMA = DATABASE() AND REFERENCED_TABLE_NAME = 'usuario'"
				+ " AND TABLE_NAME IN ('web_escuela', 'web_bloque')", Long.class),
				"sin claves ajenas a usuario (CancelarInvitacionIntegrationTest las clasifica todas)");

		jdbc.update("DELETE FROM web_bloque");
		jdbc.update("DELETE FROM web_escuela");
	}

	@Test
	@Order(6)
	void repetirla_conservaLoGuardado() throws Exception {
		jdbc.update("INSERT INTO web_escuela (organizacion_id, tema, publicada, publicada_en) VALUES (?, 'chandal', TRUE,"
				+ " '2026-11-07 10:00:00')", otra);
		jdbc.update("INSERT INTO web_bloque (organizacion_id, clave, texto) VALUES (?, 'portada.titular', 'Hola')", otra);
		ejecutarV32_5();
		ejecutarV32_5();
		assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM web_escuela", Long.class));
		assertEquals("Hola", jdbc.queryForObject("SELECT texto FROM web_bloque WHERE clave = 'portada.titular'",
				String.class));
		jdbc.update("DELETE FROM web_bloque");
		jdbc.update("DELETE FROM web_escuela");
	}

	/** Si falló a medias, volver a ejecutarla crea lo que falta. */
	@Test
	@Order(7)
	void trasUnFalloAMedias_laCompleta() throws Exception {
		jdbc.execute("DROP TABLE web_bloque");
		ejecutarV32_5();
		assertEquals(2L, numero(postcheck(), "tablas"));
		assertEquals(3L, numero(postcheck(), "claves_ajenas"));
	}

	/** Las comprobaciones «SIEMPRE 0» avisan de datos que el código no debe escribir nunca. */
	@Test
	@Order(8)
	void postcheck_avisaDeDatosRaros() throws Exception {
		jdbc.update("INSERT INTO web_escuela (organizacion_id, tema, publicada) VALUES (1, 'otro', TRUE)");
		jdbc.update("INSERT INTO web_bloque (organizacion_id, clave, imagen_id) VALUES (1, 'galeria.1', ?)", imagen);
		Map<String, Object> fila = postcheck();
		assertEquals(1L, numero(fila, "web_de_moiskimdo"));
		assertEquals(1L, numero(fila, "bloques_de_moiskimdo"));
		assertEquals(1L, numero(fila, "temas_desconocidos"));
		assertEquals(1L, numero(fila, "publicadas_sin_fecha"));
		assertEquals(1L, numero(fila, "imagenes_de_otra_escuela"));
		jdbc.update("DELETE FROM web_bloque");
		jdbc.update("DELETE FROM web_escuela");
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

	private void ejecutarV32_5() throws Exception {
		try (Connection conexion = dataSource.getConnection()) {
			ScriptUtils.executeSqlScript(conexion,
					new EncodedResource(new ClassPathResource(V32_5), StandardCharsets.UTF_8));
		}
	}

	private Map<String, Object> postcheck() throws Exception {
		return SedesVinculosMigracionIntegrationTest.ejecutarScriptDocs(dataSource, "postcheck-web-escuela.sql").get(0)
				.get(0);
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
