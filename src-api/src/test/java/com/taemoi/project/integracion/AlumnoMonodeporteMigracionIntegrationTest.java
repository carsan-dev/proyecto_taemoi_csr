package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * V22 sobre datos con la forma de producción: esquema propio migrado hasta V21, alumnos con las
 * columnas de deporte único rellenas (y la clave ajena de grado_id con un nombre heredado, como
 * en las bases anteriores a Flyway), y migración a la última versión.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AlumnoMonodeporteMigracionIntegrationTest extends MySqlIntegrationTest {

	private static final String ESQUEMA = "taemoi_v22_it";

	private static final List<String> COLUMNAS_ELIMINADAS = List.of("deporte", "grado_id", "fecha_grado",
			"apto_para_examen", "tipo_tarifa", "cuantia_tarifa", "rol_familiar", "grupo_familiar", "competidor",
			"peso", "fecha_peso", "tiene_licencia", "numero_licencia", "fecha_licencia");

	DataSource dataSource;
	JdbcTemplate jdbc;
	long alumnoA;
	long alumnoB;
	List<Map<String, Object>> precheck;

	@BeforeAll
	void migrarDesdeV21ConDatos() {
		JdbcTemplate root = new JdbcTemplate(fuente(""));
		root.execute("DROP DATABASE IF EXISTS " + ESQUEMA);
		root.execute("CREATE DATABASE " + ESQUEMA + " CHARACTER SET utf8mb4");
		dataSource = fuente(ESQUEMA);
		jdbc = new JdbcTemplate(dataSource);

		flyway("21").migrate();

		// Nombres heredados de Hibernate: V22 debe encontrar la clave ajena y el índice por columna.
		jdbc.execute("ALTER TABLE alumno DROP FOREIGN KEY FKid01ntlqpypy38pi2bk0nlof3");
		jdbc.execute("ALTER TABLE alumno RENAME INDEX FKid01ntlqpypy38pi2bk0nlof3 TO idx_heredado_grado");
		jdbc.execute("ALTER TABLE alumno ADD CONSTRAINT fk_heredado_grado FOREIGN KEY (grado_id) REFERENCES grado (id)");

		jdbc.update("INSERT INTO grado (id, tipo_grado, organizacion_id) VALUES (1, 'BLANCO', 1), (2, 'AMARILLO', 1)");
		alumnoA = insertarAlumno(101, "Ana", 2, 31.0);
		alumnoB = insertarAlumno(102, "Bea", 1, 27.0);

		// A: su deporte ya tiene tarifa propia. B: tarifa NULL en el deporte (el código caía a la del alumno).
		insertarDeporte(alumnoA, "TAEKWONDO", 2, 45.0);
		insertarDeporte(alumnoB, "KICKBOXING", 1, null);
		insertarDeporte(alumnoB, "PILATES", null, 33.0);

		precheck = ejecutarPrecheck();

		flyway(null).migrate();
	}

	@Test
	void elPrecheckDeV22FuncionaSobreElEsquemaV21() {
		assertEquals(2, precheck.size(), "dos consultas de recuento");
		Map<String, Object> recuentos = precheck.get(0);
		assertEquals(0L, ((Number) recuentos.get("sin_alumno_deporte")).longValue());
		// B tiene TAEKWONDO en la columna heredada pero solo KICKBOXING y PILATES como deportes.
		assertEquals(1L, ((Number) recuentos.get("deporte_no_esta_en_ad")).longValue());
		assertEquals(1L, ((Number) recuentos.get("grado_perdido")).longValue());
		assertEquals(1L, ((Number) recuentos.get("cuantia_a_rellenar")).longValue());
		assertEquals(1L, ((Number) recuentos.get("cuantia_a_rellenar_multideporte")).longValue());
		assertEquals(0L, ((Number) precheck.get(1).get("cargos_sin_deporte_reatribuibles")).longValue());
	}

	@Test
	void elArchivoConservaLosValoresHeredadosConSusTiposOriginales() {
		assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM alumno_monodeporte_archivo", Integer.class));
		Map<String, Object> a = jdbc.queryForMap("SELECT * FROM alumno_monodeporte_archivo WHERE id = ?", alumnoA);
		assertEquals(1L, ((Number) a.get("organizacion_id")).longValue());
		assertEquals("TAEKWONDO", a.get("deporte"));
		assertEquals(2L, ((Number) a.get("grado_id")).longValue());
		assertEquals(java.sql.Date.valueOf("2024-01-01"), a.get("fecha_grado"));
		assertEquals(Boolean.TRUE, a.get("apto_para_examen"));
		assertEquals("ADULTO", a.get("tipo_tarifa"));
		assertEquals(31.0, ((Number) a.get("cuantia_tarifa")).doubleValue());
		assertEquals("NINGUNO", a.get("rol_familiar"));
		assertEquals("FAM-1", a.get("grupo_familiar"));
		assertEquals(Boolean.TRUE, a.get("competidor"));
		assertEquals(60.5, ((Number) a.get("peso")).doubleValue());
		assertEquals(java.sql.Date.valueOf("2024-02-01"), a.get("fecha_peso"));
		assertEquals(Boolean.TRUE, a.get("tiene_licencia"));
		assertEquals(1234, ((Number) a.get("numero_licencia")).intValue());
		assertEquals(java.sql.Date.valueOf("2024-03-01"), a.get("fecha_licencia"));
		assertNotNull(a.get("archivado_en"));
		assertEquals(27.0, jdbc.queryForObject(
				"SELECT cuantia_tarifa FROM alumno_monodeporte_archivo WHERE id = ?", Double.class, alumnoB));
		for (String[] tipo : new String[][] { { "deporte", "enum(" }, { "tipo_tarifa", "enum(" },
				{ "rol_familiar", "enum(" }, { "grado_id", "bigint" }, { "apto_para_examen", "bit(1)" },
				{ "cuantia_tarifa", "double" }, { "grupo_familiar", "varchar(50)" }, { "fecha_grado", "date" } }) {
			String tipoColumna = jdbc.queryForObject("""
					SELECT COLUMN_TYPE FROM information_schema.COLUMNS
					WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'alumno_monodeporte_archivo' AND COLUMN_NAME = ?
					""", String.class, tipo[0]);
			assertTrue(tipoColumna.startsWith(tipo[1]), tipo[0] + " -> " + tipoColumna);
		}
	}

	@Test
	void archivarDosVecesConLasColumnasPresentesNoDuplica() throws Exception {
		String esquema = "taemoi_v22_archivo_it";
		JdbcTemplate root = new JdbcTemplate(fuente(""));
		root.execute("DROP DATABASE IF EXISTS " + esquema);
		root.execute("CREATE DATABASE " + esquema + " CHARACTER SET utf8mb4");
		DataSource fuenteArchivo = fuente(esquema);
		JdbcTemplate jdbcArchivo = new JdbcTemplate(fuenteArchivo);
		Flyway.configure().dataSource(fuenteArchivo).locations("classpath:db/migration").target("21").load().migrate();
		jdbcArchivo.update("INSERT INTO grado (id, tipo_grado, organizacion_id) VALUES (1, 'BLANCO', 1)");
		insertarAlumno(jdbcArchivo, 201, "Uno", 1, 30.0);

		String completo = new String(new ClassPathResource("db/migration/V22__eliminar_columnas_alumno_monodeporte.sql")
				.getContentAsByteArray(), StandardCharsets.UTF_8);
		String soloArchivo = completo.substring(0, completo.indexOf("-- 1. Conservar"));
		ejecutar(fuenteArchivo, soloArchivo);
		ejecutar(fuenteArchivo, soloArchivo);
		assertEquals(1, jdbcArchivo.queryForObject("SELECT COUNT(*) FROM alumno_monodeporte_archivo", Integer.class));

		insertarAlumno(jdbcArchivo, 202, "Dos", 1, 30.0);
		ejecutar(fuenteArchivo, soloArchivo);
		assertEquals(2, jdbcArchivo.queryForObject("SELECT COUNT(*) FROM alumno_monodeporte_archivo", Integer.class));
		root.execute("DROP DATABASE " + esquema);
	}

	@Test
	void llegaALaUltimaVersion() {
		assertEquals("24", jdbc.queryForObject(
				"SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1",
				String.class));
	}

	@Test
	void lasCatorceColumnasYaNoExisten() {
		for (String columna : COLUMNAS_ELIMINADAS) {
			assertEquals(0, contarColumna(columna), "alumno." + columna + " sigue existiendo");
		}
	}

	@Test
	void sinClaveAjenaNiIndiceSobreGrado() {
		assertEquals(0, jdbc.queryForObject("""
				SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
				WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'alumno'
				  AND CONSTRAINT_NAME IN ('fk_heredado_grado', 'FKid01ntlqpypy38pi2bk0nlof3')
				""", Integer.class));
		assertEquals(0, jdbc.queryForObject("""
				SELECT COUNT(*) FROM information_schema.STATISTICS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'alumno' AND INDEX_NAME = 'idx_heredado_grado'
				""", Integer.class));
		// Las demás claves ajenas de alumno siguen ahí.
		assertTrue(jdbc.queryForObject("""
				SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS
				WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'alumno'
				  AND CONSTRAINT_NAME = 'fk_alumno_organizacion'
				""", Integer.class) == 1);
	}

	@Test
	void elRestoDeDatosDelAlumnoQuedaIntacto() {
		Map<String, Object> a = jdbc.queryForMap("SELECT * FROM alumno WHERE id = ?", alumnoA);
		assertEquals("Ana", a.get("nombre"));
		assertEquals("Prueba", a.get("apellidos"));
		assertEquals(101, ((Number) a.get("numero_expediente")).intValue());
		assertEquals("ana@example.test", a.get("email"));
		assertEquals("Calle 1", a.get("direccion"));
		assertEquals(600000000, ((Number) a.get("telefono")).intValue());
		assertEquals(1L, ((Number) a.get("organizacion_id")).longValue());
		assertEquals(java.sql.Date.valueOf("2010-01-01"), a.get("fecha_nacimiento"));
		assertEquals(java.sql.Date.valueOf("2020-09-01"), a.get("fecha_alta"));
		assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM grado WHERE id IN (1, 2)", Integer.class));
	}

	@Test
	void losDeportesConservanSusDatosYHeredanLaTarifaQueUsabaElFallback() {
		Map<String, Object> taekwondoA = deporte(alumnoA, "TAEKWONDO");
		assertEquals(45.0, ((Number) taekwondoA.get("cuantia_tarifa")).doubleValue(), "no se pisa la tarifa propia");
		assertEquals(2L, ((Number) taekwondoA.get("grado_id")).longValue());
		assertEquals("ADULTO", taekwondoA.get("tipo_tarifa"));

		Map<String, Object> kickB = deporte(alumnoB, "KICKBOXING");
		assertEquals(27.0, ((Number) kickB.get("cuantia_tarifa")).doubleValue(), "tarifa NULL rellenada con la del alumno");
		assertEquals(1L, ((Number) kickB.get("grado_id")).longValue());

		Map<String, Object> pilatesB = deporte(alumnoB, "PILATES");
		assertEquals(33.0, ((Number) pilatesB.get("cuantia_tarifa")).doubleValue());
		assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM alumno_deporte", Integer.class));
	}

	@Test
	void v22SePuedeRepetir() throws Exception {
		try (Connection conexion = dataSource.getConnection()) {
			ScriptUtils.executeSqlScript(conexion,
					new ClassPathResource("db/migration/V22__eliminar_columnas_alumno_monodeporte.sql"));
		}
		for (String columna : COLUMNAS_ELIMINADAS) {
			assertEquals(0, contarColumna(columna), columna);
		}
		assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM alumno", Integer.class));
		assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM alumno_monodeporte_archivo", Integer.class));
	}

	private long insertarAlumno(int expediente, String nombre, long gradoId, double cuantia) {
		return insertarAlumno(jdbc, expediente, nombre, gradoId, cuantia);
	}

	private static long insertarAlumno(JdbcTemplate jdbc, int expediente, String nombre, long gradoId,
			double cuantia) {
		jdbc.update("""
				INSERT INTO alumno (activo, apellidos, autorizacion_web, direccion, fecha_nacimiento, nombre,
				  numero_expediente, telefono, tiene_derecho_examen, email, fecha_alta, organizacion_id,
				  deporte, grado_id, fecha_grado, apto_para_examen, tipo_tarifa, cuantia_tarifa, rol_familiar,
				  grupo_familiar, competidor, peso, fecha_peso, tiene_licencia, numero_licencia, fecha_licencia)
				VALUES (1, 'Prueba', 1, 'Calle 1', '2010-01-01', ?, ?, 600000000, 0, ?, '2020-09-01', 1,
				  'TAEKWONDO', ?, '2024-01-01', 1, 'ADULTO', ?, 'NINGUNO', 'FAM-1', 1, 60.5, '2024-02-01', 1, 1234,
				  '2024-03-01')
				""", nombre, expediente, nombre.toLowerCase() + "@example.test", gradoId, cuantia);
		return jdbc.queryForObject("SELECT id FROM alumno WHERE numero_expediente = ?", Long.class, expediente);
	}

	private void insertarDeporte(long alumnoId, String deporte, Integer gradoId, Double cuantia) {
		jdbc.update("""
				INSERT INTO alumno_deporte (activo, apto_para_examen, alumno_id, deporte, competidor, principal,
				  grado_id, tipo_tarifa, cuantia_tarifa, organizacion_id)
				VALUES (1, 0, ?, ?, 0, 0, ?, 'ADULTO', ?, 1)
				""", alumnoId, deporte, gradoId, cuantia);
	}

	/** Ejecuta docs/multi-escuela/precheck-v22.sql (solo SELECT) y devuelve la fila de cada consulta. */
	private List<Map<String, Object>> ejecutarPrecheck() {
		String sql;
		try {
			sql = Files.readString(Path.of("..", "docs", "multi-escuela", "precheck-v22.sql"), StandardCharsets.UTF_8);
		} catch (java.io.IOException e) {
			throw new IllegalStateException(e);
		}
		StringBuilder sinComentarios = new StringBuilder();
		for (String linea : sql.split("\\R")) {
			if (!linea.strip().startsWith("--")) {
				sinComentarios.append(linea).append('\n');
			}
		}
		List<Map<String, Object>> filas = new ArrayList<>();
		for (String consulta : sinComentarios.toString().split(";")) {
			if (!consulta.isBlank()) {
				filas.add(jdbc.queryForMap(consulta));
			}
		}
		return filas;
	}

	private static void ejecutar(DataSource fuente, String script) throws Exception {
		try (Connection conexion = fuente.getConnection()) {
			ScriptUtils.executeSqlScript(conexion, new ByteArrayResource(script.getBytes(StandardCharsets.UTF_8)));
		}
	}

	private Map<String, Object> deporte(long alumnoId, String deporte) {
		return jdbc.queryForMap("SELECT * FROM alumno_deporte WHERE alumno_id = ? AND deporte = ?", alumnoId, deporte);
	}

	private int contarColumna(String columna) {
		return jdbc.queryForObject("""
				SELECT COUNT(*) FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'alumno' AND COLUMN_NAME = ?
				""", Integer.class, columna);
	}

	private Flyway flyway(String objetivo) {
		var config = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration");
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
