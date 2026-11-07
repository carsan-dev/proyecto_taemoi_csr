package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 * V32_6 (web por plantilla, pieza W2 de fase4-web-plan.md §4.2): columnas nuevas y nulas de {@code sede} (slug, lugar,
 * indicaciones de acceso y dos fotos) y de {@code evento} (hora, lugar y sede; decisión 466), sobre una base en V32_5
 * con datos de dos escuelas. Solo amplía: ninguna columna, índice, restricción ni fila existente cambia y las columnas
 * nuevas quedan a NULL (sin relleno). Slug único por escuela, claves ajenas y su regla de borrado, repetirla, también
 * tras un fallo a medias, y {@code docs/multi-escuela/postcheck-web-sede-evento.sql}. Datos inventados.
 */
@Tag("migracion")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WebSedeEventoV32_6MigracionIntegrationTest extends MySqlIntegrationTest {

	private static final String ESQUEMA = "taemoi_v32_6_it";
	private static final String V32_6 = "db/migration/V32_6__web_sede_evento.sql";
	private static final Set<String> COLUMNAS_NUEVAS = Set.of("sede.slug", "sede.lugar", "sede.indicaciones_acceso",
			"sede.foto_imagen_id", "sede.foto2_imagen_id", "evento.hora", "evento.lugar", "evento.sede_id");
	private static final Set<String> INDICES_NUEVOS = Set.of("uk_sede_organizacion_slug", "idx_sede_foto_imagen",
			"idx_sede_foto2_imagen", "idx_evento_sede");
	private static final Set<String> RESTRICCIONES_NUEVAS = Set.of("uk_sede_organizacion_slug", "fk_sede_foto_imagen",
			"fk_sede_foto2_imagen", "fk_evento_sede");
	private static final String COLUMNAS_DEL_ESQUEMA = """
			SELECT TABLE_NAME, COLUMN_NAME, ORDINAL_POSITION, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, EXTRA,
			       CHARACTER_SET_NAME, COLLATION_NAME
			FROM information_schema.COLUMNS
			WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME <> 'flyway_schema_history'
			ORDER BY TABLE_NAME, ORDINAL_POSITION""";
	private static final String INDICES_DEL_ESQUEMA = """
			SELECT TABLE_NAME, INDEX_NAME, COLUMN_NAME, NON_UNIQUE, SEQ_IN_INDEX FROM information_schema.STATISTICS
			WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME <> 'flyway_schema_history'
			ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX""";
	private static final String RESTRICCIONES_DEL_ESQUEMA = """
			SELECT TABLE_NAME, CONSTRAINT_NAME, CONSTRAINT_TYPE FROM information_schema.TABLE_CONSTRAINTS
			WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME <> 'flyway_schema_history'
			ORDER BY TABLE_NAME, CONSTRAINT_NAME""";
	private static final Map<String, String> FILAS = Map.of(
			"organizacion", "SELECT * FROM organizacion ORDER BY id",
			"sede", "SELECT * FROM sede ORDER BY id",
			"evento", "SELECT * FROM evento ORDER BY id",
			"imagen", "SELECT * FROM imagen ORDER BY id",
			"grupo", "SELECT * FROM grupo ORDER BY id");

	DataSource dataSource;
	JdbcTemplate jdbc;
	long otra, sedeOtra, sedeOtra2, sedeMoiskimdo, imagen, eventoOtra;
	List<Map<String, Object>> columnasAntes;
	List<Map<String, Object>> indicesAntes;
	List<Map<String, Object>> restriccionesAntes;
	Map<String, List<Map<String, Object>>> filasAntes = new LinkedHashMap<>();

	@BeforeAll
	void migrar() {
		dataSource = recrear(ESQUEMA);
		jdbc = new JdbcTemplate(dataSource);
		flyway(dataSource, "32.5").migrate();

		jdbc.update("INSERT INTO organizacion (slug, nombre) VALUES ('otra-v32-6', 'Otra escuela V32_6')");
		otra = jdbc.queryForObject("SELECT id FROM organizacion WHERE slug = 'otra-v32-6'", Long.class);
		jdbc.update("INSERT INTO sede (organizacion_id, nombre) VALUES (?, 'V32_6 sede')", otra);
		sedeOtra = jdbc.queryForObject("SELECT id FROM sede WHERE organizacion_id = ? AND nombre = 'V32_6 sede'",
				Long.class, otra);
		jdbc.update("INSERT INTO sede (organizacion_id, nombre) VALUES (?, 'V32_6 sede 2')", otra);
		sedeOtra2 = jdbc.queryForObject("SELECT id FROM sede WHERE organizacion_id = ? AND nombre = 'V32_6 sede 2'",
				Long.class, otra);
		sedeMoiskimdo = jdbc.queryForObject("SELECT MIN(id) FROM sede WHERE organizacion_id = 1", Long.class);
		jdbc.update("INSERT INTO imagen (nombre, ruta, tipo, url, organizacion_id) VALUES ('v32-6.jpg',"
				+ " 'org-x/web/v32-6.jpg', 'image/jpeg', '/imagenes/org-x/web/v32-6.jpg', ?)", otra);
		imagen = jdbc.queryForObject("SELECT MAX(id) FROM imagen WHERE organizacion_id = ?", Long.class, otra);
		jdbc.update("""
				INSERT INTO evento (titulo, descripcion, visible, orden, organizacion_id, fecha_evento)
				VALUES ('V32_6 evento', 'Descripción', TRUE, 1, ?, '2026-12-01')""", otra);
		eventoOtra = jdbc.queryForObject("SELECT MAX(id) FROM evento WHERE organizacion_id = ?", Long.class, otra);
		jdbc.update("""
				INSERT INTO evento (titulo, descripcion, visible, orden, organizacion_id, fecha_evento)
				VALUES ('V32_6 evento Moiskimdo', 'Descripción', TRUE, 1, 1, '2026-12-02')""");

		columnasAntes = jdbc.queryForList(COLUMNAS_DEL_ESQUEMA);
		indicesAntes = jdbc.queryForList(INDICES_DEL_ESQUEMA);
		restriccionesAntes = jdbc.queryForList(RESTRICCIONES_DEL_ESQUEMA);
		FILAS.forEach((tabla, consulta) -> filasAntes.put(tabla, jdbc.queryForList(consulta)));
		// Hasta V32_6: las migraciones posteriores tendrán su propio test.
		flyway(dataSource, "32.6").migrate();
	}

	@Test
	@Order(1)
	void llegaAV32_6() {
		assertEquals("32.6", jdbc.queryForObject(
				"SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1",
				String.class));
	}

	@Test
	@Order(2)
	void columnasNuevas_nulasYAlFinal() {
		List<String> sede = columnas("sede");
		assertEquals(List.of("slug varchar(60) YES", "lugar varchar(120) YES", "indicaciones_acceso varchar(300) YES",
				"foto_imagen_id bigint YES", "foto2_imagen_id bigint YES"), sede.subList(sede.size() - 5, sede.size()));
		List<String> evento = columnas("evento");
		assertEquals(List.of("hora time YES", "lugar varchar(160) YES", "sede_id bigint YES"),
				evento.subList(evento.size() - 3, evento.size()));
		assertEquals(List.of("organizacion_id,slug"), indice("sede", "uk_sede_organizacion_slug", 0));
		assertEquals(List.of("foto_imagen_id"), indice("sede", "idx_sede_foto_imagen", 1));
		assertEquals(List.of("foto2_imagen_id"), indice("sede", "idx_sede_foto2_imagen", 1));
		assertEquals(List.of("sede_id"), indice("evento", "idx_evento_sede", 1));
		assertEquals(List.of("fk_evento_sede evento sede SET NULL", "fk_sede_foto2_imagen sede imagen SET NULL",
				"fk_sede_foto_imagen sede imagen SET NULL"), jdbc.queryForList("""
						SELECT CONCAT(CONSTRAINT_NAME, ' ', TABLE_NAME, ' ', REFERENCED_TABLE_NAME, ' ', DELETE_RULE)
						FROM information_schema.REFERENTIAL_CONSTRAINTS
						WHERE CONSTRAINT_SCHEMA = DATABASE() AND CONSTRAINT_NAME IN
						  ('fk_evento_sede', 'fk_sede_foto_imagen', 'fk_sede_foto2_imagen')
						ORDER BY CONSTRAINT_NAME""", String.class));
	}

	/** Solo amplía: nada del esquema cambia salvo lo nuevo, las filas siguen igual y lo nuevo, a NULL. */
	@Test
	@Order(3)
	void soloAmplia() {
		assertEquals(columnasAntes, sin(jdbc.queryForList(COLUMNAS_DEL_ESQUEMA), f -> COLUMNAS_NUEVAS
				.contains(f.get("TABLE_NAME") + "." + f.get("COLUMN_NAME"))), "ninguna columna existente cambia");
		assertEquals(indicesAntes, sin(jdbc.queryForList(INDICES_DEL_ESQUEMA),
				f -> INDICES_NUEVOS.contains(f.get("INDEX_NAME"))), "ningún índice existente cambia");
		assertEquals(restriccionesAntes, sin(jdbc.queryForList(RESTRICCIONES_DEL_ESQUEMA),
				f -> RESTRICCIONES_NUEVAS.contains(f.get("CONSTRAINT_NAME"))), "ninguna restricción existente cambia");
		FILAS.forEach((tabla, consulta) -> {
			List<Map<String, Object>> despues = new ArrayList<>();
			for (Map<String, Object> fila : jdbc.queryForList(consulta)) {
				Map<String, Object> vieja = new LinkedHashMap<>(fila);
				for (String nueva : COLUMNAS_NUEVAS) {
					if (nueva.startsWith(tabla + ".")) {
						String columna = nueva.substring(tabla.length() + 1);
						assertNull(vieja.remove(columna), tabla + "." + columna + " nace NULL");
					}
				}
				despues.add(vieja);
			}
			assertEquals(filasAntes.get(tabla), despues, tabla);
		});
	}

	@Test
	@Order(4)
	void postcheck_justoTrasDesplegar() throws Exception {
		Map<String, Object> fila = postcheck();
		for (String columna : List.of("flyway_32_6", "ultima_es_32_6", "slug_unico_por_escuela")) {
			assertEquals(1L, numero(fila, columna), columna);
		}
		assertEquals(5L, numero(fila, "columnas_sede"));
		assertEquals(3L, numero(fila, "columnas_evento"));
		assertEquals(3L, numero(fila, "claves_ajenas"));
		assertEquals(jdbc.queryForObject("SELECT COUNT(*) FROM sede", Long.class), numero(fila, "sedes"));
		assertEquals(jdbc.queryForObject("SELECT COUNT(*) FROM evento", Long.class), numero(fila, "eventos"));
		for (String columna : List.of("sedes_con_datos_web", "eventos_con_datos_web", "sedes_moiskimdo_con_datos_web",
				"eventos_moiskimdo_con_datos_web", "eventos_con_sede_de_otra_escuela", "fotos_de_otra_escuela",
				"slugs_no_validos")) {
			assertEquals(0L, numero(fila, columna), columna);
		}
	}

	/** Slug único por escuela (varias sedes sin slug), claves ajenas y lo que pasa al borrar la imagen. */
	@Test
	@Order(5)
	void reglas() {
		jdbc.update("UPDATE sede SET slug = 'casariche', lugar = 'Pabellón', indicaciones_acceso = 'Puerta lateral',"
				+ " foto_imagen_id = ?, foto2_imagen_id = ? WHERE id = ?", imagen, imagen, sedeOtra);
		assertThrows(DataIntegrityViolationException.class,
				() -> jdbc.update("UPDATE sede SET slug = 'casariche' WHERE id = ?", sedeOtra2), "slug único por escuela");
		jdbc.update("UPDATE sede SET slug = 'casariche' WHERE id = ?", sedeMoiskimdo);
		assertEquals(2L, jdbc.queryForObject("SELECT COUNT(*) FROM sede WHERE slug = 'casariche'", Long.class),
				"el mismo slug en otra escuela vale");
		assertThrows(DataIntegrityViolationException.class,
				() -> jdbc.update("UPDATE sede SET foto_imagen_id = 999999 WHERE id = ?", sedeOtra), "FK a imagen");

		jdbc.update("UPDATE evento SET hora = '18:30:00', lugar = 'Polideportivo', sede_id = ? WHERE id = ?", sedeOtra,
				eventoOtra);
		assertEquals("18:30:00", jdbc.queryForObject("SELECT CAST(hora AS CHAR) FROM evento WHERE id = ?", String.class,
				eventoOtra));
		assertThrows(DataIntegrityViolationException.class,
				() -> jdbc.update("UPDATE evento SET sede_id = 999999 WHERE id = ?", eventoOtra), "FK a sede");

		jdbc.update("DELETE FROM imagen WHERE id = ?", imagen);
		assertEquals(Map.of("f1", 1L, "f2", 1L), jdbc.queryForMap("""
				SELECT CAST(foto_imagen_id IS NULL AS SIGNED) AS f1, CAST(foto2_imagen_id IS NULL AS SIGNED) AS f2
				FROM sede WHERE id = ?""", sedeOtra), "borrar la imagen deja la sede sin foto");

		jdbc.update("UPDATE sede SET slug = NULL, lugar = NULL, indicaciones_acceso = NULL");
		jdbc.update("UPDATE evento SET hora = NULL, lugar = NULL, sede_id = NULL");
		jdbc.update("INSERT INTO imagen (nombre, ruta, tipo, url, organizacion_id) VALUES ('v32-6.jpg',"
				+ " 'org-x/web/v32-6.jpg', 'image/jpeg', '/imagenes/org-x/web/v32-6.jpg', ?)", otra);
		imagen = jdbc.queryForObject("SELECT MAX(id) FROM imagen WHERE organizacion_id = ?", Long.class, otra);
	}

	@Test
	@Order(6)
	void repetirla_conservaLoGuardado() throws Exception {
		jdbc.update("UPDATE sede SET slug = 'estepa', lugar = 'Pabellón' WHERE id = ?", sedeOtra);
		jdbc.update("UPDATE evento SET hora = '10:00:00', sede_id = ? WHERE id = ?", sedeOtra, eventoOtra);
		ejecutarV32_6();
		ejecutarV32_6();
		assertEquals("estepa", jdbc.queryForObject("SELECT slug FROM sede WHERE id = ?", String.class, sedeOtra));
		assertEquals(sedeOtra, jdbc.queryForObject("SELECT sede_id FROM evento WHERE id = ?", Long.class, eventoOtra));
		assertEquals(5L, numero(postcheck(), "columnas_sede"));
		assertEquals(3L, numero(postcheck(), "claves_ajenas"));
		jdbc.update("UPDATE sede SET slug = NULL, lugar = NULL");
		jdbc.update("UPDATE evento SET hora = NULL, sede_id = NULL");
	}

	/** Si falló a medias, volver a ejecutarla crea lo que falta. */
	@Test
	@Order(7)
	void trasUnFalloAMedias_laCompleta() throws Exception {
		jdbc.execute("ALTER TABLE evento DROP FOREIGN KEY fk_evento_sede");
		jdbc.execute("ALTER TABLE evento DROP INDEX idx_evento_sede");
		jdbc.execute("ALTER TABLE evento DROP COLUMN sede_id");
		jdbc.execute("ALTER TABLE sede DROP INDEX uk_sede_organizacion_slug");
		ejecutarV32_6();
		Map<String, Object> fila = postcheck();
		assertEquals(3L, numero(fila, "columnas_evento"));
		assertEquals(3L, numero(fila, "claves_ajenas"));
		assertEquals(1L, numero(fila, "slug_unico_por_escuela"));
		assertEquals(List.of("sede_id"), indice("evento", "idx_evento_sede", 1));
	}

	/** Las comprobaciones «SIEMPRE 0» avisan de datos que el código no debe escribir nunca. */
	@Test
	@Order(8)
	void postcheck_avisaDeDatosRaros() throws Exception {
		jdbc.update("UPDATE sede SET slug = 'Con Mayúsculas', foto_imagen_id = ? WHERE id = ?", imagen, sedeMoiskimdo);
		jdbc.update("UPDATE evento SET sede_id = ? WHERE organizacion_id = 1", sedeOtra);
		Map<String, Object> fila = postcheck();
		assertEquals(1L, numero(fila, "slugs_no_validos"));
		assertEquals(1L, numero(fila, "fotos_de_otra_escuela"));
		assertEquals(1L, numero(fila, "eventos_con_sede_de_otra_escuela"));
		assertEquals(1L, numero(fila, "sedes_moiskimdo_con_datos_web"));
		assertEquals(1L, numero(fila, "eventos_moiskimdo_con_datos_web"));
		jdbc.update("UPDATE sede SET slug = NULL, foto_imagen_id = NULL");
		jdbc.update("UPDATE evento SET sede_id = NULL");
	}

	// ------------------------------------------------------------------ utilidades

	private static List<Map<String, Object>> sin(List<Map<String, Object>> filas,
			java.util.function.Predicate<Map<String, Object>> nueva) {
		return filas.stream().filter(nueva.negate()).toList();
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

	private void ejecutarV32_6() throws Exception {
		try (Connection conexion = dataSource.getConnection()) {
			ScriptUtils.executeSqlScript(conexion,
					new EncodedResource(new ClassPathResource(V32_6), StandardCharsets.UTF_8));
		}
	}

	private Map<String, Object> postcheck() throws Exception {
		return SedesVinculosMigracionIntegrationTest.ejecutarScriptDocs(dataSource, "postcheck-web-sede-evento.sql")
				.get(0).get(0);
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
