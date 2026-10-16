package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * V27 sobre datos escritos por el código anterior después de V24/V26: inscripciones con tipo_tarifa
 * sin tarifa_id (o desfasada), productos creados sin codigo ni tipo (mensualidad, la tarifa de
 * competidor creada al vuelo) y una variante de nombre. Después se aplica V27, se ejecuta
 * docs/multi-escuela/postcheck-v27.sql y se repite V27.
 */
@Tag("migracion")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SwitchTarifasMigracionIntegrationTest extends MySqlIntegrationTest {

	private static final String ESQUEMA = "taemoi_v27_it";

	DataSource dataSource;
	JdbcTemplate jdbc;

	long hermanos;
	long desfasada;
	long sinCuantia;
	long mensualidad;
	long variante;
	long competidorKb;

	@BeforeAll
	void migrarConDatosDelCodigoAnterior() {
		JdbcTemplate root = new JdbcTemplate(fuente(""));
		root.execute("DROP DATABASE IF EXISTS " + ESQUEMA);
		root.execute("CREATE DATABASE " + ESQUEMA + " CHARACTER SET utf8mb4");
		dataSource = fuente(ESQUEMA);
		jdbc = new JdbcTemplate(dataSource);

		flyway(dataSource, "26").migrate();
		insertarDatosEscritosAntesDeV27();
		flyway(dataSource, "27").migrate(); // fijado a V27 (V28 borra tipo_tarifa, que se comprueba aquí)
	}

	@Test
	void llegaAV27() {
		assertEquals(1, jdbc.queryForObject(
				"SELECT COUNT(*) FROM flyway_schema_history WHERE version = '27' AND success = 1", Integer.class));
	}

	@Test
	void tarifaId_sincronizadaConTipoTarifa() {
		assertEquals("HERMANOS", tarifaDe(hermanos));
		assertEquals("INFANTIL", tarifaDe(desfasada), "tipo_tarifa manda: la tarifa_id desfasada se corrige");
		assertEquals("ADULTO", tarifaDe(sinCuantia));
	}

	@Test
	void productosCreadosPorElCodigoAnterior_quedanClasificadosYEnlazados() {
		Map<String, Object> m = jdbc.queryForMap("SELECT codigo, tipo, disciplina_id FROM producto WHERE id = ?", mensualidad);
		assertEquals("MENSUALIDAD", m.get("codigo"));
		assertEquals("MENSUALIDAD", m.get("tipo"));
		assertNull(m.get("disciplina_id"));
		Map<String, Object> kb = jdbc.queryForMap("SELECT codigo, tipo FROM producto WHERE id = ?", competidorKb);
		assertEquals("COMPETIDOR_KICKBOXING", kb.get("codigo"));
		assertEquals("COMPETIDOR", kb.get("tipo"));
		assertEquals(competidorKb, jdbc.queryForObject(
				"SELECT producto_competidor_id FROM disciplina WHERE organizacion_id = 1 AND codigo = 'KICKBOXING'", Long.class));
		// La variante de nombre (otras mayúsculas) no se clasifica: el código usa la clasificada.
		Map<String, Object> v = jdbc.queryForMap("SELECT codigo, tipo FROM producto WHERE id = ?", variante);
		assertNull(v.get("codigo"));
		assertEquals("OTRO", v.get("tipo"));
	}

	/** docs/multi-escuela/postcheck-v27.sql: una fila de recuentos con los valores de este conjunto. */
	@Test
	void postcheckDeV27_soloRecuentos() throws Exception {
		Path desdeModulo = Path.of("..", "docs", "multi-escuela", "postcheck-v27.sql");
		Path ruta = Files.exists(desdeModulo) ? desdeModulo : Path.of("docs", "multi-escuela", "postcheck-v27.sql");
		String texto = Files.readString(ruta, StandardCharsets.UTF_8);
		List<String> consultas = java.util.Arrays.stream(texto.replaceAll("(?m)^--.*$", "").split(";"))
				.map(String::trim).filter(s -> !s.isEmpty()).toList();
		assertEquals(1, consultas.size());
		List<Map<String, Object>> filas = jdbc.queryForList(consultas.get(0));
		assertEquals(1, filas.size());
		Map<String, Long> real = new LinkedHashMap<>();
		filas.get(0).forEach((k, valor) -> real.put(k, ((Number) valor).longValue()));

		Map<String, Long> esperado = new LinkedHashMap<>();
		esperado.put("inscripciones_sin_tarifa", 0L);
		esperado.put("inscripciones_tarifa_distinta", 0L);
		esperado.put("inscripciones_activas_sin_cuantia", 1L);
		esperado.put("productos_mensualidad_generales", 1L);
		esperado.put("productos_reserva_generales", 0L);
		esperado.put("mensualidad_con_otro_nombre", 0L);
		esperado.put("competicion_sin_producto", 1L); // taekwondo: sin «TARIFA COMPETIDOR TAEKWONDO»
		esperado.put("competidor_con_otro_nombre", 0L);
		esperado.put("competidor_precio_no_es_20", 1L); // el de kickboxing cuesta 25
		esperado.put("licencias_sin_codigo", 8L);
		esperado.put("variantes_con_id_menor", 1L); // «Mensualidad», id menor que «MENSUALIDAD»
		esperado.put("cargos_periodicos_solo_por_tipo", 1L); // «Cuota especial» del producto de mensualidad
		esperado.put("cargos_examen_solo_por_tipo", 0L);
		esperado.put("cargos_categoria_solo_por_tipo", 1L);
		assertEquals(esperado, real);
	}

	@Test
	void repetirV27NoCambiaNada() throws Exception {
		List<Map<String, Object>> inscripciones = jdbc.queryForList(
				"SELECT id, tipo_tarifa, tarifa_id, disciplina_id, grado_id FROM alumno_deporte ORDER BY id");
		List<Map<String, Object>> productos = jdbc.queryForList(
				"SELECT id, codigo, tipo, disciplina_id, grado_id FROM producto ORDER BY id");
		List<Map<String, Object>> disciplinas = jdbc.queryForList(
				"SELECT id, producto_competidor_id, producto_licencia_id FROM disciplina ORDER BY id");

		try (Connection conexion = dataSource.getConnection()) {
			ScriptUtils.executeSqlScript(conexion, new EncodedResource(
					new ClassPathResource("db/migration/V27__switch_tarifas_productos.sql"), StandardCharsets.UTF_8));
		}

		assertEquals(inscripciones, jdbc.queryForList(
				"SELECT id, tipo_tarifa, tarifa_id, disciplina_id, grado_id FROM alumno_deporte ORDER BY id"));
		assertEquals(productos, jdbc.queryForList(
				"SELECT id, codigo, tipo, disciplina_id, grado_id FROM producto ORDER BY id"));
		assertEquals(disciplinas, jdbc.queryForList(
				"SELECT id, producto_competidor_id, producto_licencia_id FROM disciplina ORDER BY id"));
	}

	@Test
	void ningunaColumnaPasaANotNull_tarifaIdSigueAdmitiendoNull() {
		assertEquals("YES", jdbc.queryForObject("""
				SELECT IS_NULLABLE FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'alumno_deporte' AND COLUMN_NAME = 'tarifa_id'""",
				String.class));
		// tipo_tarifa se conserva (contracción posterior).
		assertTrue(jdbc.queryForObject("""
				SELECT COUNT(*) FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'alumno_deporte' AND COLUMN_NAME = 'tipo_tarifa'""",
				Integer.class) == 1);
	}

	// ---------------------------------------------------------------------------------

	/** Lo que escribía el código anterior a V27: tipo_tarifa sin tarifa_id y productos sin codigo ni tipo. */
	private void insertarDatosEscritosAntesDeV27() {
		long alumno = alumno(1);
		hermanos = inscripcion(alumno, "TAEKWONDO", "HERMANOS", null, 26.0);
		long otro = alumno(2);
		Long adulto = jdbc.queryForObject("SELECT id FROM tarifa WHERE organizacion_id = 1 AND codigo = 'ADULTO'", Long.class);
		desfasada = inscripcion(otro, "TAEKWONDO", "INFANTIL", adulto, 28.0);
		sinCuantia = inscripcion(otro, "KICKBOXING", "ADULTO", null, null);
		assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM alumno_deporte WHERE tarifa_id IS NULL OR tarifa_id = ?",
				Integer.class, adulto));

		variante = producto("Mensualidad", 30.0);
		mensualidad = producto("MENSUALIDAD", 28.0);
		competidorKb = producto("TARIFA COMPETIDOR KICKBOXING", 25.0);
		jdbc.update("""
				INSERT INTO producto_alumno (alumno_id, producto_id, concepto, precio, cantidad, pagado, fecha_asignacion,
				  organizacion_id) VALUES (?, ?, 'Cuota especial', 30.0, 1, 0, '2026-10-01', 1)""", alumno, mensualidad);
	}

	private long alumno(int expediente) {
		jdbc.update("""
				INSERT INTO alumno (activo, apellidos, autorizacion_web, direccion, fecha_nacimiento, nombre,
				  numero_expediente, telefono, tiene_derecho_examen, organizacion_id)
				VALUES (1, 'Prueba', 1, 'Calle 1', '2000-01-01', ?, ?, '600000000', 0, 1)
				""", "Alumno " + expediente, expediente);
		return jdbc.queryForObject("SELECT id FROM alumno WHERE numero_expediente = ?", Long.class, expediente);
	}

	private long inscripcion(long alumnoId, String deporte, String tipoTarifa, Long tarifaId, Double cuantia) {
		jdbc.update("""
				INSERT INTO alumno_deporte (activo, apto_para_examen, alumno_id, deporte, competidor, tipo_tarifa,
				  tarifa_id, cuantia_tarifa, organizacion_id, disciplina_id)
				VALUES (1, 0, ?, ?, 0, ?, ?, ?, 1,
				  (SELECT d.id FROM disciplina d WHERE d.organizacion_id = 1 AND d.codigo = ?))
				""", alumnoId, deporte, tipoTarifa, tarifaId, cuantia, deporte);
		return jdbc.queryForObject("SELECT id FROM alumno_deporte WHERE alumno_id = ? AND deporte = ?", Long.class,
				alumnoId, deporte);
	}

	private long producto(String concepto, double precio) {
		jdbc.update("INSERT INTO producto (concepto, precio, organizacion_id) VALUES (?, ?, 1)", concepto, precio);
		return jdbc.queryForObject("SELECT MAX(id) FROM producto", Long.class);
	}

	private String tarifaDe(long inscripcion) {
		return jdbc.queryForObject(
				"SELECT t.codigo FROM alumno_deporte ad JOIN tarifa t ON t.id = ad.tarifa_id WHERE ad.id = ?",
				String.class, inscripcion);
	}

	private static Flyway flyway(DataSource fuente, String objetivo) {
		var config = Flyway.configure().dataSource(fuente).locations("classpath:db/migration");
		if (objetivo != null) {
			config.target(objetivo);
		}
		return config.load();
	}

	private DataSource fuente(String esquema) {
		String url = "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306) + "/" + esquema
				+ "?useSSL=false&allowPublicKeyRetrieval=true";
		return new DriverManagerDataSource(url, "root", MYSQL.getPassword());
	}
}
