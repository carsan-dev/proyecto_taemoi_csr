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
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * V31_16 (decisión 342): tabla {@code evento_foto}, sobre una base en V31_15 con eventos e imágenes de dos escuelas. Solo
 * amplía: ninguna columna, índice, restricción ni fila existente cambia. La imagen anterior de la API, que no conoce la
 * tabla, puede seguir borrando eventos con fotos (cascada a nivel de base de datos). Repetirla, también tras un fallo.
 * Ejecuta {@code docs/multi-escuela/postcheck-v31-16.sql}. Datos inventados.
 */
@Tag("migracion")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FotosEventoV31_16MigracionIntegrationTest extends MySqlIntegrationTest {

	private static final String ESQUEMA = "taemoi_v31_16_it";
	private static final String V31_16 = "db/migration/V31_16__fotos_evento.sql";
	private static final String COLUMNAS_DEL_ESQUEMA = """
			SELECT TABLE_NAME, COLUMN_NAME, ORDINAL_POSITION, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, EXTRA,
			       CHARACTER_SET_NAME, COLLATION_NAME
			FROM information_schema.COLUMNS
			WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME NOT IN ('flyway_schema_history', 'evento_foto')
			ORDER BY TABLE_NAME, ORDINAL_POSITION""";
	private static final String INDICES_DEL_ESQUEMA = """
			SELECT TABLE_NAME, INDEX_NAME, COLUMN_NAME, NON_UNIQUE, SEQ_IN_INDEX FROM information_schema.STATISTICS
			WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME NOT IN ('flyway_schema_history', 'evento_foto')
			ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX""";
	private static final String RESTRICCIONES_DEL_ESQUEMA = """
			SELECT TABLE_NAME, CONSTRAINT_NAME, CONSTRAINT_TYPE FROM information_schema.TABLE_CONSTRAINTS
			WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME NOT IN ('flyway_schema_history', 'evento_foto')
			ORDER BY TABLE_NAME, CONSTRAINT_NAME""";
	private static final List<String> TABLAS_CON_FILAS = List.of("organizacion", "evento", "imagen");

	DataSource dataSource;
	JdbcTemplate jdbc;
	long otra, eventoDe1, eventoDeOtra;
	List<Map<String, Object>> columnasAntes;
	List<Map<String, Object>> indicesAntes;
	List<Map<String, Object>> restriccionesAntes;
	Map<String, List<Map<String, Object>>> filasAntes = new LinkedHashMap<>();

	@BeforeAll
	void migrar() {
		dataSource = recrear(ESQUEMA);
		jdbc = new JdbcTemplate(dataSource);
		flyway(dataSource, "31.15").migrate();

		jdbc.update("INSERT INTO organizacion (slug, nombre) VALUES ('otra-v31-16', 'Otra escuela V31_16')");
		otra = jdbc.queryForObject("SELECT id FROM organizacion WHERE slug = 'otra-v31-16'", Long.class);
		eventoDe1 = evento(1L, "Evento de la escuela 1");
		eventoDeOtra = evento(otra, "Evento de la otra escuela");
		imagen(1L, "cartel.webp", "org-1/eventos/cartel.webp");

		columnasAntes = jdbc.queryForList(COLUMNAS_DEL_ESQUEMA);
		indicesAntes = jdbc.queryForList(INDICES_DEL_ESQUEMA);
		restriccionesAntes = jdbc.queryForList(RESTRICCIONES_DEL_ESQUEMA);
		for (String tabla : TABLAS_CON_FILAS) {
			filasAntes.put(tabla, jdbc.queryForList("SELECT * FROM " + tabla + " ORDER BY id"));
		}
		// Hasta V31_16: las migraciones posteriores tendrán su propio test.
		flyway(dataSource, "31.16").migrate();
	}

	@Test
	@Order(1)
	void llegaAV31_16() {
		assertEquals("31.16", jdbc.queryForObject(
				"SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1",
				String.class));
	}

	@Test
	@Order(2)
	void creaLaTabla_conLasColumnasPedidas() {
		assertEquals(List.of("id bigint NO", "organizacion_id bigint NO", "evento_id bigint NO", "imagen_id bigint NO",
				"miniatura_id bigint NO", "orden int NO"), columnas("evento_foto"));
	}

	@Test
	@Order(3)
	void indicesYUnicas() {
		assertEquals(List.of("imagen_id"), indice("evento_foto", "uk_evento_foto_imagen", 0));
		assertEquals(List.of("miniatura_id"), indice("evento_foto", "uk_evento_foto_miniatura", 0));
		assertEquals(List.of("evento_id,orden"), indice("evento_foto", "idx_evento_foto_evento_orden", 1));
		assertEquals(List.of("organizacion_id,evento_id"), indice("evento_foto", "idx_evento_foto_organizacion_evento", 1));
	}

	@Test
	@Order(4)
	void soloAmplia() {
		assertEquals(columnasAntes, jdbc.queryForList(COLUMNAS_DEL_ESQUEMA), "ninguna columna existente cambia");
		assertEquals(indicesAntes, jdbc.queryForList(INDICES_DEL_ESQUEMA), "ningún índice cambia");
		assertEquals(restriccionesAntes, jdbc.queryForList(RESTRICCIONES_DEL_ESQUEMA),
				"ninguna restricción existente cambia");
		for (String tabla : TABLAS_CON_FILAS) {
			assertEquals(filasAntes.get(tabla), jdbc.queryForList("SELECT * FROM " + tabla + " ORDER BY id"), tabla);
		}
		assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM evento_foto", Long.class));
	}

	@Test
	@Order(41)
	void postcheck_justoTrasDesplegar() throws Exception {
		Map<String, Object> fila = postcheck();
		for (String columna : List.of("flyway_31_16", "ultima_es_31_16", "tabla_evento_foto", "miniatura_obligatoria")) {
			assertEquals(1L, numero(fila, columna), columna);
		}
		assertEquals(6L, numero(fila, "columnas_evento_foto"));
		assertEquals(2L, numero(fila, "unicas"));
		assertEquals(4L, numero(fila, "claves_ajenas"));
		assertEquals(0L, numero(fila, "filas_evento_foto"));
		assertEquals(0L, numero(fila, "fotos_sin_miniatura"));
	}

	@Test
	@Order(42)
	void postcheck_conFotos() throws Exception {
		long imagen = imagen(1L, "p.webp", "org-1/fotos-eventos/p.webp");
		long miniatura = imagen(1L, "p-mini.webp", "org-1/fotos-eventos/p-mini.webp");
		long foto = foto(1L, eventoDe1, imagen, miniatura, 1);

		Map<String, Object> fila = postcheck();

		assertEquals(1L, numero(fila, "filas_evento_foto"));
		assertEquals(0L, numero(fila, "fotos_sin_miniatura"));
		jdbc.update("DELETE FROM evento_foto WHERE id = ?", foto);
		jdbc.update("DELETE FROM imagen WHERE id IN (?, ?)", imagen, miniatura);
	}

	/** Lo que hace la imagen anterior de la API: un DELETE del evento, sin saber nada de la tabla nueva. */
	@Test
	@Order(5)
	void borrarUnEventoConFotos_sinConocerLaTabla_seLlevaSusFilas() {
		long evento = evento(1L, "Con fotos");
		long imagen = imagen(1L, "a.webp", "org-1/fotos-eventos/a.webp");
		long miniatura = imagen(1L, "a-mini.webp", "org-1/fotos-eventos/a-mini.webp");
		foto(1L, evento, imagen, miniatura, 1);

		jdbc.update("DELETE FROM evento WHERE id = ?", evento);

		assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM evento_foto WHERE evento_id = ?", Long.class, evento));
		assertEquals(2L, jdbc.queryForObject("SELECT COUNT(*) FROM imagen WHERE id IN (?, ?)", Long.class, imagen, miniatura),
				"las filas de imagen quedan: sin referencias, y privadas");
		jdbc.update("DELETE FROM imagen WHERE id IN (?, ?)", imagen, miniatura);
	}

	@Test
	@Order(6)
	void unaImagenNoSeRepite_niSePuedeBorrarMientrasSeUsa() {
		long imagen = imagen(1L, "b.webp", "org-1/fotos-eventos/b.webp");
		long miniatura = imagen(1L, "b-mini.webp", "org-1/fotos-eventos/b-mini.webp");
		long otra = imagen(1L, "b2.webp", "org-1/fotos-eventos/b2.webp");
		long foto = foto(1L, eventoDe1, imagen, miniatura, 1);

		assertThrows(DataIntegrityViolationException.class, () -> foto(1L, eventoDe1, imagen, otra, 2));
		assertThrows(DataIntegrityViolationException.class, () -> foto(1L, eventoDe1, otra, miniatura, 2),
				"Una miniatura tampoco se repite");
		assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("DELETE FROM imagen WHERE id = ?", imagen));
		assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("DELETE FROM imagen WHERE id = ?", miniatura),
				"Una miniatura en uso tampoco se borra");

		jdbc.update("DELETE FROM evento_foto WHERE id = ?", foto);
		jdbc.update("DELETE FROM imagen WHERE id IN (?, ?, ?)", imagen, miniatura, otra);
	}

	@Test
	@Order(61)
	void laMiniaturaEsObligatoriaYDebeExistir() {
		long imagen = imagen(1L, "g.webp", "org-1/fotos-eventos/g.webp");

		assertThrows(DataAccessException.class, () -> jdbc.update(
				"INSERT INTO evento_foto (organizacion_id, evento_id, imagen_id, orden) VALUES (1, ?, ?, 1)", eventoDe1, imagen),
				"Sin miniatura no hay foto");
		assertThrows(DataIntegrityViolationException.class, () -> foto(1L, eventoDe1, imagen, 987654321L, 1));

		jdbc.update("DELETE FROM imagen WHERE id = ?", imagen);
	}

	/** Sin UNIQUE en el orden: reordenar escribe fila a fila y pasaría por duplicados. */
	@Test
	@Order(7)
	void elOrdenPuedeRepetirseMientrasSeReordena() {
		long a = imagen(1L, "c.webp", "org-1/fotos-eventos/c.webp");
		long b = imagen(1L, "d.webp", "org-1/fotos-eventos/d.webp");
		long miniaturaA = imagen(1L, "c-mini.webp", "org-1/fotos-eventos/c-mini.webp");
		long miniaturaB = imagen(1L, "d-mini.webp", "org-1/fotos-eventos/d-mini.webp");
		long fotoA = foto(1L, eventoDe1, a, miniaturaA, 1);
		long fotoB = foto(1L, eventoDe1, b, miniaturaB, 2);

		jdbc.update("UPDATE evento_foto SET orden = 2 WHERE id = ?", fotoA);
		jdbc.update("UPDATE evento_foto SET orden = 1 WHERE id = ?", fotoB);

		jdbc.update("DELETE FROM evento_foto WHERE id IN (?, ?)", fotoA, fotoB);
		jdbc.update("DELETE FROM imagen WHERE id IN (?, ?, ?, ?)", a, b, miniaturaA, miniaturaB);
	}

	@Test
	@Order(8)
	void laEscuelaDebeExistir() {
		long imagen = imagen(otra, "e.webp", "org-" + otra + "/fotos-eventos/e.webp");
		long miniatura = imagen(otra, "e-mini.webp", "org-" + otra + "/fotos-eventos/e-mini.webp");
		assertThrows(DataIntegrityViolationException.class, () -> foto(987654L, eventoDeOtra, imagen, miniatura, 1));
		jdbc.update("DELETE FROM imagen WHERE id IN (?, ?)", imagen, miniatura);
	}

	@Test
	@Order(9)
	void repetirla_conservaLoGuardado() throws Exception {
		long imagen = imagen(1L, "f.webp", "org-1/fotos-eventos/f.webp");
		long miniatura = imagen(1L, "f-mini.webp", "org-1/fotos-eventos/f-mini.webp");
		long foto = foto(1L, eventoDe1, imagen, miniatura, 1);

		ejecutarV31_16();
		ejecutarV31_16();

		assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM evento_foto WHERE id = ?", Long.class, foto));
		jdbc.update("DELETE FROM evento_foto WHERE id = ?", foto);
		jdbc.update("DELETE FROM imagen WHERE id IN (?, ?)", imagen, miniatura);
	}

	/** Si falló, volver a ejecutarla crea lo que falta. */
	@Test
	@Order(10)
	void trasUnFallo_laCompleta() throws Exception {
		jdbc.execute("DROP TABLE evento_foto");
		ejecutarV31_16();
		assertEquals(List.of("id bigint NO", "organizacion_id bigint NO", "evento_id bigint NO", "imagen_id bigint NO",
				"miniatura_id bigint NO", "orden int NO"), columnas("evento_foto"));
	}

	// ------------------------------------------------------------------ utilidades

	private long evento(long organizacion, String titulo) {
		jdbc.update("INSERT INTO evento (titulo, descripcion, visible, organizacion_id) VALUES (?, 'Descripción', 1, ?)",
				titulo, organizacion);
		return jdbc.queryForObject("SELECT MAX(id) FROM evento WHERE organizacion_id = ?", Long.class, organizacion);
	}

	private long imagen(long organizacion, String nombre, String ruta) {
		jdbc.update("INSERT INTO imagen (nombre, ruta, tipo, url, organizacion_id) VALUES (?, ?, 'image/webp', ?, ?)",
				nombre, ruta, "/imagenes/" + ruta, organizacion);
		return jdbc.queryForObject("SELECT MAX(id) FROM imagen WHERE organizacion_id = ?", Long.class, organizacion);
	}

	private long foto(long organizacion, long evento, long imagen, long miniatura, int orden) {
		jdbc.update("INSERT INTO evento_foto (organizacion_id, evento_id, imagen_id, miniatura_id, orden)"
				+ " VALUES (?, ?, ?, ?, ?)", organizacion, evento, imagen, miniatura, orden);
		return jdbc.queryForObject("SELECT MAX(id) FROM evento_foto", Long.class);
	}

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

	private Map<String, Object> postcheck() throws Exception {
		return SedesVinculosMigracionIntegrationTest.ejecutarScriptDocs(dataSource, "postcheck-v31-16.sql").get(0).get(0);
	}

	private static long numero(Map<String, Object> fila, String columna) {
		return ((Number) fila.get(columna)).longValue();
	}

	private void ejecutarV31_16() throws Exception {
		try (Connection conexion = dataSource.getConnection()) {
			ScriptUtils.executeSqlScript(conexion,
					new EncodedResource(new ClassPathResource(V31_16), StandardCharsets.UTF_8));
		}
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
