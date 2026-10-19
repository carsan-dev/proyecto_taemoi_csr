package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * V31_7 (pieza 6b-3b-1, decisión 182): columna {@code producto.vendible_profesor BOOLEAN NOT NULL DEFAULT FALSE}, al
 * final de la tabla, sobre una base en V31_6 con productos de dos escuelas. Ningún producto queda marcado y nada más
 * cambia; un INSERT sin la columna (el código anterior) queda sin marcar; el ADD COLUMN admite INSTANT; repetirla
 * (también tras un fallo antes de crear la columna) no cambia nada. Ejecuta {@code docs/multi-escuela/postcheck-v31_7.sql}.
 */
@Tag("migracion")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProductoVendibleProfesorV31_7MigracionIntegrationTest extends MySqlIntegrationTest {

	private static final String ESQUEMA = "taemoi_v31_7_it";
	private static final String V31_7 = "db/migration/V31_7__producto_vendible_profesor.sql";
	private static final String COLUMNA = "vendible_profesor";

	DataSource dataSource;
	JdbcTemplate jdbc;
	List<String> columnasAntes;
	List<Map<String, Object>> productosAntes;

	@BeforeAll
	void migrar() {
		dataSource = recrear(ESQUEMA);
		jdbc = new JdbcTemplate(dataSource);
		flyway(dataSource, "31.6").migrate();
		jdbc.update("INSERT INTO organizacion (slug, nombre) VALUES ('otra-v31-7', 'Otra escuela V31_7')");
		long otra = jdbc.queryForObject("SELECT id FROM organizacion WHERE slug = 'otra-v31-7'", Long.class);
		jdbc.update("INSERT INTO producto (concepto, precio, organizacion_id) VALUES ('CAMISETA V31_7', 12.5, 1)");
		jdbc.update("INSERT INTO producto (concepto, precio, organizacion_id) VALUES ('LLAVERO V31_7', 3.0, ?)", otra);
		columnasAntes = columnas();
		productosAntes = jdbc.queryForList("SELECT * FROM producto ORDER BY id");
		assertTrue(productosAntes.size() >= 2);
		// Hasta V31_7: las migraciones posteriores tendrán su propio test.
		flyway(dataSource, "31.7").migrate();
	}

	@Test
	@Order(1)
	void llegaAV31_7() {
		assertEquals("31.7", jdbc.queryForObject(
				"SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1",
				String.class));
	}

	@Test
	@Order(2)
	void añadeLaColumnaAlFinal_booleanaNoNulaConDefaultFalso() {
		List<String> esperadas = new ArrayList<>(columnasAntes);
		esperadas.add(COLUMNA);
		assertEquals(esperadas, columnas());
		Map<String, Object> columna = jdbc.queryForMap("""
				SELECT COLUMN_TYPE AS tipo, IS_NULLABLE AS nula, COLUMN_DEFAULT AS defecto
				FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'producto' AND COLUMN_NAME = ?""", COLUMNA);
		assertEquals("tinyint(1)", columna.get("tipo"));
		assertEquals("NO", columna.get("nula"));
		assertEquals("0", columna.get("defecto"));
	}

	@Test
	@Order(3)
	void ningunProductoQuedaMarcado_yNadaMasCambia() {
		List<Map<String, Object>> ahora = jdbc.queryForList("SELECT * FROM producto ORDER BY id");
		assertEquals(productosAntes.size(), ahora.size());
		for (Map<String, Object> fila : ahora) {
			assertEquals(Boolean.FALSE, fila.get(COLUMNA), "producto " + fila.get("id"));
		}
		assertEquals(productosAntes, sinNueva(ahora));
	}

	@Test
	@Order(4)
	void postcheck_columnaTipoNoNulaDefaultYVersion_sinMarcados() throws Exception {
		Map<String, Object> fila = postcheck();
		assertEquals(1L, numero(fila, "columna_vendible_profesor"));
		assertEquals(1L, numero(fila, "tipo_booleano"));
		assertEquals(1L, numero(fila, "no_nula"));
		assertEquals(1L, numero(fila, "default_falso"));
		assertEquals(1L, numero(fila, "flyway_31_7"));
		assertEquals(0L, numero(fila, "productos_marcados"));
		assertEquals((long) productosAntes.size(), numero(fila, "productos"));
	}

	/** El código anterior inserta sin la columna: el producto queda sin marcar (DEFAULT FALSE). */
	@Test
	@Order(5)
	void insertarSinLaColumna_quedaSinMarcar_yLaMarcaSeCuenta() throws Exception {
		jdbc.update("INSERT INTO producto (concepto, precio, organizacion_id) VALUES ('GORRA V31_7', 8.0, 1)");
		long id = jdbc.queryForObject("SELECT MAX(id) FROM producto", Long.class);
		assertEquals(Boolean.FALSE, jdbc.queryForObject("SELECT vendible_profesor FROM producto WHERE id = ?",
				Boolean.class, id));
		jdbc.update("UPDATE producto SET vendible_profesor = TRUE WHERE id = ?", id);
		assertEquals(1L, numero(postcheck(), "productos_marcados"));
		jdbc.update("DELETE FROM producto WHERE id = ?", id);
		assertEquals(0L, numero(postcheck(), "productos_marcados"));
	}

	/** La afirmación de coste, en el MySQL de los tests (8.0, como producción): el ADD COLUMN admite INSTANT. */
	@Test
	@Order(6)
	void elAddColumnEsInstant() {
		jdbc.execute("ALTER TABLE producto DROP COLUMN vendible_profesor");
		jdbc.execute("ALTER TABLE producto ADD COLUMN vendible_profesor BOOLEAN NOT NULL DEFAULT FALSE, ALGORITHM=INSTANT");
		assertEquals(1L, numero(postcheckSinError(), "default_falso"));
	}

	@Test
	@Order(7)
	void repetirla_tambienTrasUnFalloAntesDeLaColumna_noCambiaNada() throws Exception {
		jdbc.update("UPDATE producto SET vendible_profesor = TRUE ORDER BY id LIMIT 1");
		List<Map<String, Object>> antes = jdbc.queryForList("SELECT * FROM producto ORDER BY id");
		ejecutarV31_7();
		ejecutarV31_7();
		assertEquals(antes, jdbc.queryForList("SELECT * FROM producto ORDER BY id"), "conserva la marca puesta");
		assertEquals(1L, numero(postcheck(), "productos_marcados"));

		// Fallo antes de crear la columna: volver a ejecutarla la crea, sin marcar.
		jdbc.execute("ALTER TABLE producto DROP COLUMN vendible_profesor");
		ejecutarV31_7();
		ejecutarV31_7();
		Map<String, Object> fila = postcheck();
		assertEquals(1L, numero(fila, "columna_vendible_profesor"));
		assertEquals(1L, numero(fila, "no_nula"));
		assertEquals(1L, numero(fila, "default_falso"));
		assertEquals(0L, numero(fila, "productos_marcados"));
		List<String> esperadas = new ArrayList<>(columnasAntes);
		esperadas.add(COLUMNA);
		assertEquals(esperadas, columnas());
		assertEquals(productosAntes, sinNueva(jdbc.queryForList("SELECT * FROM producto ORDER BY id")));
	}

	// ------------------------------------------------------------------ utilidades

	private void ejecutarV31_7() throws Exception {
		try (Connection conexion = dataSource.getConnection()) {
			ScriptUtils.executeSqlScript(conexion,
					new EncodedResource(new ClassPathResource(V31_7), StandardCharsets.UTF_8));
		}
	}

	private static List<Map<String, Object>> sinNueva(List<Map<String, Object>> filas) {
		return filas.stream().map(f -> {
			Map<String, Object> copia = new LinkedHashMap<>(f);
			copia.remove(COLUMNA);
			return copia;
		}).toList();
	}

	private List<String> columnas() {
		return jdbc.queryForList("""
				SELECT COLUMN_NAME FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'producto' ORDER BY ORDINAL_POSITION""", String.class);
	}

	private Map<String, Object> postcheck() throws Exception {
		return SedesVinculosMigracionIntegrationTest.ejecutarScriptDocs(dataSource, "postcheck-v31_7.sql").get(0)
				.get(0);
	}

	private Map<String, Object> postcheckSinError() {
		try {
			return postcheck();
		} catch (Exception e) {
			throw new AssertionError(e);
		}
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
