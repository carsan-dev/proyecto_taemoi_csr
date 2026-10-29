package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * V31_13 (retirada del interruptor del diseño nuevo, decisión del propietario del 29/10): contracción en versión
 * decimal sobre una base en V31_12 con filas en {@code diseno_panel_usuario} y la clave
 * {@code panel.diseno_por_defecto} en dos escuelas. Borra la tabla y la clave en todas las escuelas; las demás tablas,
 * columnas, claves de configuración y filas no cambian; repetirla no falla ni cambia nada. Datos inventados.
 */
@Tag("migracion")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DisenoPanelV31_13MigracionIntegrationTest extends MySqlIntegrationTest {

	private static final String ESQUEMA = "taemoi_v31_13_it";
	private static final String V31_13 = "db/migration/V31_13__retirar_interruptor_diseno.sql";
	private static final String TABLA = "diseno_panel_usuario";
	private static final String CLAVE = "panel.diseno_por_defecto";
	/** Todo el esquema salvo la tabla que se borra y el historial de Flyway (que gana su fila). */
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
	private static final String OTRAS_CLAVES = """
			SELECT * FROM configuracion_organizacion WHERE clave <> 'panel.diseno_por_defecto' ORDER BY id""";
	private static final List<String> TABLAS_CON_FILAS = List.of("usuario", "membresia", "organizacion");

	DataSource dataSource;
	JdbcTemplate jdbc;
	long otra, propietario, gestorOtra;
	List<Map<String, Object>> columnasAntes;
	List<Map<String, Object>> indicesAntes;
	List<Map<String, Object>> restriccionesAntes;
	List<String> tablasAntes;
	List<Map<String, Object>> otrasClavesAntes;
	Map<String, List<Map<String, Object>>> filasAntes = new LinkedHashMap<>();

	@BeforeAll
	void migrar() {
		dataSource = recrear(ESQUEMA);
		jdbc = new JdbcTemplate(dataSource);
		flyway(dataSource, "31.12").migrate();
		jdbc.update("INSERT INTO organizacion (slug, nombre) VALUES ('otra-v31-13', 'Otra escuela V31_13')");
		otra = jdbc.queryForObject("SELECT id FROM organizacion WHERE slug = 'otra-v31-13'", Long.class);
		propietario = cuenta("v31-13-propietario@example.test");
		gestorOtra = cuenta("v31-13-gestor-otra@example.test");
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, 1, 'PROPIETARIO')",
				propietario);
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, ?, 'ADMINISTRADOR')",
				gestorOtra, otra);
		// El interruptor usado en las dos escuelas: filas propias y la clave con los dos valores.
		fila(1L, propietario, propietario);
		fila(otra, gestorOtra, propietario);
		fila(otra, propietario, null);
		clave(1L, CLAVE, "NUEVO");
		clave(otra, CLAVE, "VIEJO");
		// Otras claves de configuración en las dos escuelas, también con un nombre parecido: no se tocan.
		clave(1L, "v31_13.otra_clave", "SI");
		clave(otra, "v31_13.otra_clave", "NO");
		clave(otra, "panel.diseno_por_defecto_x", "NUEVO");

		columnasAntes = jdbc.queryForList(COLUMNAS_DEL_ESQUEMA);
		indicesAntes = jdbc.queryForList(INDICES_DEL_ESQUEMA);
		restriccionesAntes = jdbc.queryForList(RESTRICCIONES_DEL_ESQUEMA);
		tablasAntes = tablas();
		otrasClavesAntes = jdbc.queryForList(OTRAS_CLAVES);
		for (String tabla : TABLAS_CON_FILAS) {
			filasAntes.put(tabla, jdbc.queryForList("SELECT * FROM " + tabla + " ORDER BY id"));
		}
		assertEquals(3L, jdbc.queryForObject("SELECT COUNT(*) FROM " + TABLA, Long.class), "filas de partida");
		assertEquals(2L, cuentaClave(), "la clave en dos escuelas");
		flyway(dataSource, "31.13").migrate();
	}

	@Test
	@Order(1)
	void llegaAV31_13() {
		assertEquals("31.13", jdbc.queryForObject(
				"SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1",
				String.class));
	}

	/** La tabla ya no existe y ninguna otra tabla desaparece ni aparece. */
	@Test
	@Order(2)
	void laTablaDesaparece() {
		List<String> esperadas = new java.util.ArrayList<>(tablasAntes);
		esperadas.remove(TABLA);
		assertEquals(esperadas, tablas(), "solo desaparece la tabla del interruptor");
		assertEquals(0L, jdbc.queryForObject("""
				SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?""",
				Long.class, TABLA));
	}

	/** La clave desaparece en todas las escuelas; las demás claves siguen igual, con su id y su valor. */
	@Test
	@Order(3)
	void laClaveDesapareceEnTodasLasEscuelas_lasDemasIntactas() {
		assertEquals(0L, cuentaClave());
		assertEquals(otrasClavesAntes, jdbc.queryForList(OTRAS_CLAVES));
		assertEquals(List.of("1 v31_13.otra_clave SI", otra + " panel.diseno_por_defecto_x NUEVO",
				otra + " v31_13.otra_clave NO"), jdbc.queryForList("""
						SELECT CONCAT(organizacion_id, ' ', clave, ' ', valor) FROM configuracion_organizacion
						WHERE clave LIKE 'v31_13.%' OR clave LIKE 'panel.%' ORDER BY organizacion_id, clave""",
						String.class), "las claves de prueba ajenas siguen, con su valor");
	}

	/** Nada más cambia: columnas, índices, restricciones y filas de cuentas, membresías y escuelas. */
	@Test
	@Order(4)
	void nadaMasCambia() {
		assertEquals(columnasAntes, jdbc.queryForList(COLUMNAS_DEL_ESQUEMA), "ninguna columna cambia");
		assertEquals(indicesAntes, jdbc.queryForList(INDICES_DEL_ESQUEMA), "ningún índice cambia");
		assertEquals(restriccionesAntes, jdbc.queryForList(RESTRICCIONES_DEL_ESQUEMA), "ninguna restricción cambia");
		for (String tabla : TABLAS_CON_FILAS) {
			assertEquals(filasAntes.get(tabla), jdbc.queryForList("SELECT * FROM " + tabla + " ORDER BY id"), tabla);
		}
	}

	/** La comprobación posterior del checklist de despliegue: Flyway en 31.13, sin tabla y sin la clave. */
	@Test
	@Order(5)
	void comprobacionPosterior() {
		Map<String, Object> postcheck = jdbc.queryForMap("""
				SELECT (SELECT version FROM flyway_schema_history WHERE success = 1
				        ORDER BY installed_rank DESC LIMIT 1) AS version,
				       (SELECT COUNT(*) FROM information_schema.TABLES
				        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'diseno_panel_usuario') AS tabla,
				       (SELECT COUNT(*) FROM configuracion_organizacion
				        WHERE clave = 'panel.diseno_por_defecto') AS clave""");
		assertEquals("31.13", postcheck.get("version"));
		assertEquals(0L, ((Number) postcheck.get("tabla")).longValue());
		assertEquals(0L, ((Number) postcheck.get("clave")).longValue());
	}

	@Test
	@Order(6)
	void repetirlaNoFallaNiCambiaNada() throws Exception {
		List<String> tablas = tablas();
		List<Map<String, Object>> configuracion = jdbc
				.queryForList("SELECT * FROM configuracion_organizacion ORDER BY id");
		try (Connection conexion = dataSource.getConnection()) {
			ScriptUtils.executeSqlScript(conexion,
					new EncodedResource(new ClassPathResource(V31_13), StandardCharsets.UTF_8));
		}
		assertEquals(tablas, tablas());
		assertEquals(configuracion, jdbc.queryForList("SELECT * FROM configuracion_organizacion ORDER BY id"));
	}

	private void fila(long organizacion, long usuario, Long cambiadoPor) {
		jdbc.update("""
				INSERT INTO diseno_panel_usuario
				  (organizacion_id, usuario_id, permitido, activo, cambiado_por_usuario_id, cambiado_en)
				VALUES (?, ?, TRUE, TRUE, ?, '2026-10-21 10:00:00')""", organizacion, usuario, cambiadoPor);
	}

	private void clave(long organizacion, String clave, String valor) {
		jdbc.update("INSERT INTO configuracion_organizacion (organizacion_id, clave, valor) VALUES (?, ?, ?)",
				organizacion, clave, valor);
	}

	private long cuentaClave() {
		return jdbc.queryForObject("SELECT COUNT(*) FROM configuracion_organizacion WHERE clave = ?", Long.class,
				CLAVE);
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
