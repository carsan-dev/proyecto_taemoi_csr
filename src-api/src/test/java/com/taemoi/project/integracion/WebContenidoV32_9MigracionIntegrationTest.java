package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
 * V32_9 (pieza W12a de la web por plantilla): {@code web_bloque.oculto} NOT NULL DEFAULT FALSE, la constancia del
 * permiso de los profesores ({@code permiso_origen}, {@code permiso_por}, {@code permiso_en}, NULL), la tabla
 * {@code web_contenido} (versión del contenido) y {@code sede.foto_alt} y {@code foto2_alt} VARCHAR(150) NULL, sobre una
 * base en V32_8 con una escuela con web y bloques. Nada más cambia; los INSERT sin las columnas (el código anterior)
 * siguen valiendo; repetirla, también tras un fallo a medias, no cambia nada y completa lo que faltara. Ejecuta
 * {@code docs/multi-escuela/postcheck-v32-9.sql}. Datos inventados.
 */
@Tag("migracion")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WebContenidoV32_9MigracionIntegrationTest extends MySqlIntegrationTest {

	private static final String ESQUEMA = "taemoi_v32_9_it";
	private static final String V32_9 = "db/migration/V32_9__web_contenido.sql";
	private static final List<String> NUEVAS_BLOQUE = List.of("oculto", "permiso_origen", "permiso_por", "permiso_en");
	private static final List<String> NUEVAS_SEDE = List.of("foto_alt", "foto2_alt");
	/** Todo el esquema salvo lo nuevo y el historial de Flyway (que gana su fila). */
	private static final String COLUMNAS_DEL_ESQUEMA = """
			SELECT TABLE_NAME, COLUMN_NAME, ORDINAL_POSITION, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, EXTRA,
			       CHARACTER_SET_NAME, COLLATION_NAME
			FROM information_schema.COLUMNS
			WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME NOT IN ('flyway_schema_history', 'web_contenido')
			  AND NOT (TABLE_NAME = 'web_bloque' AND COLUMN_NAME IN ('oculto', 'permiso_origen', 'permiso_por',
			                                                         'permiso_en'))
			  AND NOT (TABLE_NAME = 'sede' AND COLUMN_NAME IN ('foto_alt', 'foto2_alt'))
			ORDER BY TABLE_NAME, ORDINAL_POSITION""";
	private static final String INDICES_DEL_ESQUEMA = """
			SELECT TABLE_NAME, INDEX_NAME, COLUMN_NAME, NON_UNIQUE, SEQ_IN_INDEX FROM information_schema.STATISTICS
			WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME NOT IN ('flyway_schema_history', 'web_contenido')
			ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX""";
	private static final String RESTRICCIONES_DEL_ESQUEMA = """
			SELECT TABLE_NAME, CONSTRAINT_NAME, CONSTRAINT_TYPE FROM information_schema.TABLE_CONSTRAINTS
			WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME NOT IN ('flyway_schema_history', 'web_contenido')
			ORDER BY TABLE_NAME, CONSTRAINT_NAME""";
	private static final List<String> TABLAS_CON_FILAS = List.of("organizacion", "web_escuela", "imagen", "evento");

	DataSource dataSource;
	JdbcTemplate jdbc;
	long otra;
	List<String> columnasBloqueAntes;
	List<String> columnasSedeAntes;
	List<Map<String, Object>> columnasAntes;
	List<Map<String, Object>> indicesAntes;
	List<Map<String, Object>> restriccionesAntes;
	List<Map<String, Object>> bloquesAntes;
	List<Map<String, Object>> sedesAntes;
	Map<String, List<Map<String, Object>>> filasAntes = new LinkedHashMap<>();

	@BeforeAll
	void migrar() {
		dataSource = recrear(ESQUEMA);
		jdbc = new JdbcTemplate(dataSource);
		flyway(dataSource, "32.8").migrate();
		jdbc.update("INSERT INTO organizacion (slug, nombre) VALUES ('otra-v32-9', 'Otra escuela V32_9')");
		otra = jdbc.queryForObject("SELECT id FROM organizacion WHERE slug = 'otra-v32-9'", Long.class);
		jdbc.update("INSERT INTO web_escuela (organizacion_id, tema, publicada) VALUES (?, 'linea-comarcal', TRUE)",
				otra);
		jdbc.update("INSERT INTO sede (organizacion_id, nombre, lugar) VALUES (?, 'V32_9 sede', 'Pabellón')", otra);
		jdbc.update("INSERT INTO imagen (nombre, ruta, tipo, url, organizacion_id) VALUES ('v32-9.jpg',"
				+ " 'org-x/web/bloques/v32-9.jpg', 'image/jpeg', '/imagenes/org-x/web/bloques/v32-9.jpg', ?)", otra);
		long imagen = jdbc.queryForObject("SELECT MAX(id) FROM imagen WHERE organizacion_id = ?", Long.class, otra);
		bloque("portada.titular", "Titular V32_9", null, 1);
		bloque("profesor.1.nombre", "Profesora V32_9", null, 3);
		bloque("galeria.1", "Descripción", imagen, 2);

		columnasBloqueAntes = columnas("web_bloque");
		columnasSedeAntes = columnas("sede");
		columnasAntes = jdbc.queryForList(COLUMNAS_DEL_ESQUEMA);
		indicesAntes = jdbc.queryForList(INDICES_DEL_ESQUEMA);
		restriccionesAntes = jdbc.queryForList(RESTRICCIONES_DEL_ESQUEMA);
		bloquesAntes = bloques();
		sedesAntes = sedes();
		assertTrue(sedesAntes.stream().anyMatch(f -> ((Number) f.get("organizacion_id")).longValue() == 1L),
				"la base de partida tiene sedes de la organización 1");
		for (String tabla : TABLAS_CON_FILAS) {
			filasAntes.put(tabla, filas(tabla));
		}
		// Hasta V32_9: las migraciones posteriores tendrán su propio test.
		flyway(dataSource, "32.9").migrate();
	}

	@Test
	@Order(1)
	void llegaAV32_9() {
		assertEquals("32.9", jdbc.queryForObject(
				"SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1",
				String.class));
	}

	@Test
	@Order(2)
	void añadeLasColumnasAlFinal_yLaTabla() {
		assertEquals(conLas(columnasBloqueAntes, NUEVAS_BLOQUE), columnas("web_bloque"));
		assertEquals(conLas(columnasSedeAntes, NUEVAS_SEDE), columnas("sede"));
		assertEquals("tinyint(1) NO 0", definicion("web_bloque", "oculto"));
		assertEquals("varchar(10) YES", definicion("web_bloque", "permiso_origen"));
		assertEquals("bigint YES", definicion("web_bloque", "permiso_por"));
		assertEquals("datetime YES", definicion("web_bloque", "permiso_en"));
		assertEquals("varchar(150) YES", definicion("sede", "foto_alt"));
		assertEquals("varchar(150) YES", definicion("sede", "foto2_alt"));
		assertEquals(List.of("organizacion_id", "version", "guardado_en", "guardado_por"), columnas("web_contenido"));
		assertEquals("bigint NO 0", definicion("web_contenido", "version"));
		assertEquals(1, jdbc.queryForObject("""
				SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS
				WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'web_contenido'
				  AND REFERENCED_TABLE_NAME = 'organizacion' AND CONSTRAINT_NAME = 'fk_web_contenido_organizacion'""",
				Integer.class));
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM web_contenido", Integer.class), "tabla vacía");
	}

	/** Solo amplía: nada del esquema ni de los datos cambia; los bloques, visibles y sin permiso; las sedes, sin alt. */
	@Test
	@Order(3)
	void soloAmplia_sinRelleno() {
		assertEquals(columnasAntes, jdbc.queryForList(COLUMNAS_DEL_ESQUEMA), "ninguna columna existente cambia");
		assertEquals(indicesAntes, jdbc.queryForList(INDICES_DEL_ESQUEMA), "ningún índice cambia");
		assertEquals(restriccionesAntes, jdbc.queryForList(RESTRICCIONES_DEL_ESQUEMA),
				"ninguna restricción existente cambia");
		for (String tabla : TABLAS_CON_FILAS) {
			assertEquals(filasAntes.get(tabla), filas(tabla), tabla);
		}
		List<Map<String, Object>> bloques = bloques();
		assertEquals(bloquesAntes, sin(bloques, NUEVAS_BLOQUE));
		for (Map<String, Object> fila : bloques) {
			assertEquals(Boolean.FALSE, fila.get("oculto"), fila.toString());
			assertNull(fila.get("permiso_origen"));
			assertNull(fila.get("permiso_por"));
			assertNull(fila.get("permiso_en"));
		}
		List<Map<String, Object>> sedes = sedes();
		assertEquals(sedesAntes, sin(sedes, NUEVAS_SEDE));
		for (Map<String, Object> fila : sedes) {
			assertNull(fila.get("foto_alt"));
			assertNull(fila.get("foto2_alt"));
		}
	}

	@Test
	@Order(4)
	void postcheck_justoTrasDesplegar() throws Exception {
		Map<String, Object> fila = postcheck();
		for (String columna : List.of("flyway_32_9", "ultima_es_32_9", "columna_oculto", "tabla_web_contenido")) {
			assertEquals(1L, numero(fila, columna), columna);
		}
		assertEquals(3L, numero(fila, "columnas_permiso"));
		assertEquals(2L, numero(fila, "columnas_sede"));
		assertEquals((long) bloquesAntes.size(), numero(fila, "bloques"));
		assertEquals((long) sedesAntes.size(), numero(fila, "sedes"));
		for (String columna : List.of("bloques_ocultos", "bloques_con_permiso", "versiones_de_contenido",
				"sedes_con_descripcion", "moiskimdo_bloques", "moiskimdo_contenido", "moiskimdo_web",
				"moiskimdo_sedes_con_descripcion")) {
			assertEquals(0L, numero(fila, columna), columna);
		}
	}

	/** El código anterior inserta sin las columnas nuevas: el bloque queda visible y sin permiso; el postcheck cuenta. */
	@Test
	@Order(5)
	void insertarSinLasColumnas_quedaVisible_yLosCambiosSeCuentan() throws Exception {
		bloque("presentacion.texto", "Texto V32_9", null, 4);
		Map<String, Object> nueva = jdbc.queryForMap(
				"SELECT oculto, permiso_origen FROM web_bloque WHERE organizacion_id = ? AND clave = 'presentacion.texto'",
				otra);
		assertEquals(Boolean.FALSE, nueva.get("oculto"));
		assertNull(nueva.get("permiso_origen"));
		jdbc.update("UPDATE web_bloque SET oculto = TRUE WHERE organizacion_id = ? AND clave = 'presentacion.texto'",
				otra);
		jdbc.update("""
				UPDATE web_bloque SET permiso_origen = 'ALTA', permiso_en = '2026-11-01 10:00:00'
				WHERE organizacion_id = ? AND clave = 'profesor.1.nombre'""", otra);
		jdbc.update("INSERT INTO web_contenido (organizacion_id) VALUES (?)", otra);
		jdbc.update("UPDATE sede SET foto_alt = 'Pabellón con el tatami' WHERE organizacion_id = ?", otra);
		Map<String, Object> fila = postcheck();
		assertEquals(1L, numero(fila, "bloques_ocultos"));
		assertEquals(1L, numero(fila, "bloques_con_permiso"));
		assertEquals(1L, numero(fila, "versiones_de_contenido"));
		assertEquals(1L, numero(fila, "sedes_con_descripcion"));
		assertEquals(0L, numero(fila, "moiskimdo_sedes_con_descripcion"));
		assertEquals(0L, jdbc.queryForObject("SELECT version FROM web_contenido WHERE organizacion_id = ?", Long.class,
				otra), "versión 0 por defecto");
		assertThrows(DataAccessException.class,
				() -> jdbc.update("UPDATE sede SET foto2_alt = REPEAT('x', 151) WHERE organizacion_id = ?", otra),
				"máximo 150");
		assertThrows(DataAccessException.class,
				() -> jdbc.update("INSERT INTO web_contenido (organizacion_id) VALUES (999999999)"),
				"clave ajena a organizacion");

		jdbc.update("DELETE FROM web_bloque WHERE organizacion_id = ? AND clave = 'presentacion.texto'", otra);
		jdbc.update("UPDATE web_bloque SET permiso_origen = NULL, permiso_en = NULL WHERE organizacion_id = ?", otra);
		jdbc.update("DELETE FROM web_contenido WHERE organizacion_id = ?", otra);
		jdbc.update("UPDATE sede SET foto_alt = NULL WHERE organizacion_id = ?", otra);
		assertEquals(0L, numero(postcheck(), "bloques_ocultos"));
	}

	@Test
	@Order(6)
	void repetirla_conservaLoGuardado() throws Exception {
		jdbc.update("UPDATE web_bloque SET oculto = TRUE WHERE organizacion_id = ? AND clave = 'galeria.1'", otra);
		jdbc.update("UPDATE sede SET foto2_alt = 'Entrada' WHERE organizacion_id = ?", otra);
		jdbc.update("INSERT INTO web_contenido (organizacion_id, version) VALUES (?, 7)", otra);
		List<Map<String, Object>> bloques = bloques();
		List<Map<String, Object>> sedes = sedes();
		ejecutarV32_9();
		ejecutarV32_9();
		assertEquals(bloques, bloques());
		assertEquals(sedes, sedes());
		assertEquals(7L, jdbc.queryForObject("SELECT version FROM web_contenido WHERE organizacion_id = ?", Long.class,
				otra));
		assertEquals(conLas(columnasBloqueAntes, NUEVAS_BLOQUE), columnas("web_bloque"));
		assertEquals(conLas(columnasSedeAntes, NUEVAS_SEDE), columnas("sede"));

		jdbc.update("UPDATE web_bloque SET oculto = FALSE WHERE organizacion_id = ?", otra);
		jdbc.update("UPDATE sede SET foto2_alt = NULL WHERE organizacion_id = ?", otra);
		jdbc.update("DELETE FROM web_contenido WHERE organizacion_id = ?", otra);
	}

	/** El DDL no es transaccional: se puede quedar a medias tras cada sentencia. Volver a ejecutarla lo completa. */
	@Test
	@Order(7)
	void trasUnFalloAMedias_laCompleta() throws Exception {
		// Solo había llegado a web_bloque.
		jdbc.execute("ALTER TABLE sede DROP COLUMN foto2_alt");
		jdbc.execute("ALTER TABLE sede DROP COLUMN foto_alt");
		jdbc.execute("DROP TABLE web_contenido");
		jdbc.execute("ALTER TABLE web_bloque DROP COLUMN permiso_en");
		ejecutarV32_9();
		assertEquals(conLas(columnasBloqueAntes, NUEVAS_BLOQUE), columnas("web_bloque"));
		assertEquals(conLas(columnasSedeAntes, NUEVAS_SEDE), columnas("sede"));
		assertEquals(1L, numero(postcheck(), "tabla_web_contenido"));

		// No había llegado a hacer nada.
		jdbc.execute("ALTER TABLE sede DROP COLUMN foto2_alt");
		jdbc.execute("ALTER TABLE sede DROP COLUMN foto_alt");
		jdbc.execute("DROP TABLE web_contenido");
		for (String columna : NUEVAS_BLOQUE.reversed()) {
			jdbc.execute("ALTER TABLE web_bloque DROP COLUMN " + columna);
		}
		ejecutarV32_9();
		ejecutarV32_9();
		assertEquals(conLas(columnasBloqueAntes, NUEVAS_BLOQUE), columnas("web_bloque"));
		assertEquals(conLas(columnasSedeAntes, NUEVAS_SEDE), columnas("sede"));
		assertEquals(columnasAntes, jdbc.queryForList(COLUMNAS_DEL_ESQUEMA));
		assertEquals(bloquesAntes, sin(bloques(), NUEVAS_BLOQUE));
		assertEquals(sedesAntes, sin(sedes(), NUEVAS_SEDE));
		Map<String, Object> fila = postcheck();
		for (String columna : List.of("columna_oculto", "tabla_web_contenido")) {
			assertEquals(1L, numero(fila, columna), columna);
		}
		assertEquals(3L, numero(fila, "columnas_permiso"));
		assertEquals(2L, numero(fila, "columnas_sede"));
	}

	// ------------------------------------------------------------------ utilidades

	private void bloque(String clave, String texto, Long imagen, int orden) {
		jdbc.update("INSERT INTO web_bloque (organizacion_id, clave, texto, imagen_id, orden) VALUES (?, ?, ?, ?, ?)",
				otra, clave, texto, imagen, orden);
	}

	private void ejecutarV32_9() throws Exception {
		try (Connection conexion = dataSource.getConnection()) {
			ScriptUtils.executeSqlScript(conexion,
					new EncodedResource(new ClassPathResource(V32_9), StandardCharsets.UTF_8));
		}
	}

	private List<Map<String, Object>> bloques() {
		return filas("web_bloque");
	}

	private List<Map<String, Object>> sedes() {
		return filas("sede");
	}

	private List<Map<String, Object>> filas(String tabla) {
		String orden = "web_escuela".equals(tabla) ? "organizacion_id" : "id";
		return jdbc.queryForList("SELECT * FROM " + tabla + " ORDER BY " + orden);
	}

	private static List<Map<String, Object>> sin(List<Map<String, Object>> filas, List<String> columnas) {
		return filas.stream().map(f -> {
			Map<String, Object> copia = new LinkedHashMap<>(f);
			columnas.forEach(copia::remove);
			return copia;
		}).toList();
	}

	private static List<String> conLas(List<String> antes, List<String> nuevas) {
		List<String> esperadas = new ArrayList<>(antes);
		esperadas.addAll(nuevas);
		return esperadas;
	}

	private List<String> columnas(String tabla) {
		return jdbc.queryForList("""
				SELECT COLUMN_NAME FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? ORDER BY ORDINAL_POSITION""", String.class, tabla);
	}

	private String definicion(String tabla, String columna) {
		return jdbc.queryForObject("""
				SELECT CONCAT_WS(' ', COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT) FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?""", String.class, tabla, columna);
	}

	private Map<String, Object> postcheck() throws Exception {
		return SedesVinculosMigracionIntegrationTest.ejecutarScriptDocs(dataSource, "postcheck-v32-9.sql").get(0)
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
