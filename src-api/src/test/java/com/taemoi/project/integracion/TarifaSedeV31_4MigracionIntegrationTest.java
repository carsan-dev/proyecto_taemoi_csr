package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
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
 * V31_4 (entrega 6-4): tabla {@code tarifa_sede}, vacía, sobre una base en V31_3 con datos. Solo añade: crea la tabla
 * con su clave única y sus FK, no escribe filas ni toca las demás tablas; repetirla no cambia nada. Ejecuta
 * {@code docs/multi-escuela/postcheck-v31_4.sql}.
 */
@Tag("migracion")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TarifaSedeV31_4MigracionIntegrationTest extends MySqlIntegrationTest {

	private static final String ESQUEMA = "taemoi_v31_4_it";
	private static final String V31_4 = "db/migration/V31_4__tarifa_sede.sql";

	DataSource dataSource;
	JdbcTemplate jdbc;
	List<Map<String, Object>> cargosAntes;
	List<Map<String, Object>> inscripcionesAntes;
	List<Map<String, Object>> tarifasAntes;

	@BeforeAll
	void migrar() {
		dataSource = recrear(ESQUEMA);
		jdbc = new JdbcTemplate(dataSource);
		flyway(dataSource, "31.3").migrate();
		long umbrete = jdbc.queryForObject("SELECT id FROM sede WHERE organizacion_id = 1", Long.class);
		long tkd = jdbc.queryForObject("SELECT id FROM disciplina WHERE organizacion_id = 1 AND codigo = 'TAEKWONDO'",
				Long.class);
		jdbc.update("""
				INSERT INTO alumno (activo, apellidos, autorizacion_web, direccion, fecha_nacimiento, nombre,
				  numero_expediente, telefono, tiene_derecho_examen, organizacion_id, sede_principal_id)
				VALUES (1, 'Prueba', 1, 'Calle 1', '2015-01-01', 'Alumno V31_4', 92001, '600000000', 0, 1, ?)""",
				umbrete);
		long alumno = jdbc.queryForObject("SELECT id FROM alumno WHERE numero_expediente = 92001", Long.class);
		jdbc.update("""
				INSERT INTO alumno_deporte (activo, apto_para_examen, alumno_id, competidor, principal, tiene_licencia,
				  organizacion_id, fecha_alta, fecha_alta_inicial, disciplina_id, cuantia_tarifa, tarifa_id)
				VALUES (TRUE, 0, ?, 0, 1, 0, 1, '2023-09-01', '2023-09-01', ?, 30.0,
				  (SELECT t.id FROM tarifa t WHERE t.organizacion_id = 1 AND t.codigo = 'ADULTO'))""", alumno, tkd);
		jdbc.update("""
				INSERT INTO producto_alumno (alumno_id, concepto, precio, cantidad, pagado, fecha_asignacion,
				  organizacion_id, sede_id)
				VALUES (?, 'MENSUALIDAD ENERO 2026 - TAEKWONDO', 30.0, 1, 0, '2026-01-05', 1, ?)""", alumno, umbrete);
		cargosAntes = jdbc.queryForList("SELECT * FROM producto_alumno ORDER BY id");
		inscripcionesAntes = jdbc.queryForList("SELECT * FROM alumno_deporte ORDER BY id");
		tarifasAntes = jdbc.queryForList("SELECT * FROM tarifa ORDER BY id");
		// Hasta V31_4: las migraciones posteriores tendrán su propio test.
		flyway(dataSource, "31.4").migrate();
	}

	@Test
	@Order(1)
	void llegaAV31_4() {
		assertEquals("31.4", jdbc.queryForObject(
				"SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1",
				String.class));
	}

	@Test
	@Order(2)
	void creaLaTablaVaciaConSuClaveUnicaYSusFk() {
		assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM tarifa_sede", Long.class));
		assertEquals(List.of("id", "organizacion_id", "tarifa_id", "sede_id", "importe", "importe_adulto",
				"importe_menor", "vigente_desde"), jdbc.queryForList("""
						SELECT COLUMN_NAME FROM information_schema.COLUMNS
						WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tarifa_sede' ORDER BY ORDINAL_POSITION""",
						String.class));
		assertEquals(List.of("NO", "YES", "YES"), jdbc.queryForList("""
				SELECT IS_NULLABLE FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tarifa_sede'
				  AND COLUMN_NAME IN ('importe', 'importe_adulto', 'importe_menor') ORDER BY ORDINAL_POSITION""",
				String.class));
		assertEquals(List.of("organizacion_id", "tarifa_id", "sede_id", "vigente_desde"), jdbc.queryForList("""
				SELECT COLUMN_NAME FROM information_schema.STATISTICS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tarifa_sede'
				  AND INDEX_NAME = 'uk_tarifa_sede_vigencia' AND NON_UNIQUE = 0 ORDER BY SEQ_IN_INDEX""", String.class));
		assertEquals(List.of("fk_tarifa_sede_organizacion", "fk_tarifa_sede_sede", "fk_tarifa_sede_tarifa"),
				jdbc.queryForList("""
						SELECT CONSTRAINT_NAME FROM information_schema.REFERENTIAL_CONSTRAINTS
						WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'tarifa_sede' ORDER BY CONSTRAINT_NAME""",
						String.class));
	}

	@Test
	@Order(3)
	void noTocaCargosInscripcionesNiTarifas() {
		assertEquals(cargosAntes, jdbc.queryForList("SELECT * FROM producto_alumno ORDER BY id"));
		assertEquals(inscripcionesAntes, jdbc.queryForList("SELECT * FROM alumno_deporte ORDER BY id"));
		assertEquals(tarifasAntes, jdbc.queryForList("SELECT * FROM tarifa ORDER BY id"));
	}

	@Test
	@Order(4)
	void postcheck_tablaVaciaYVersionAplicada() throws Exception {
		Map<String, Object> fila = SedesVinculosMigracionIntegrationTest
				.ejecutarScriptDocs(dataSource, "postcheck-v31_4.sql").get(0).get(0);
		assertEquals(1L, numero(fila, "tabla_tarifa_sede"));
		assertEquals(0L, numero(fila, "filas_tarifa_sede"));
		assertEquals(1L, numero(fila, "flyway_31_4"));
		assertEquals(0L, numero(fila, "filas_con_sede_de_otra_escuela"));
		assertEquals(0L, numero(fila, "filas_con_tarifa_de_otra_escuela"));
		assertEquals(0L, numero(fila, "filas_sin_dia_1"));
	}

	@Test
	@Order(5)
	void repetirla_noCambiaNada() throws Exception {
		jdbc.update("""
				INSERT INTO tarifa_sede (organizacion_id, tarifa_id, sede_id, importe, vigente_desde)
				VALUES (1, (SELECT id FROM tarifa WHERE organizacion_id = 1 AND codigo = 'ADULTO'),
				  (SELECT id FROM sede WHERE organizacion_id = 1), 25.00, '2027-01-01')""");
		List<Map<String, Object>> antes = jdbc.queryForList("SELECT * FROM tarifa_sede ORDER BY id");
		try (Connection conexion = dataSource.getConnection()) {
			ScriptUtils.executeSqlScript(conexion,
					new EncodedResource(new ClassPathResource(V31_4), StandardCharsets.UTF_8));
		}
		assertEquals(antes, jdbc.queryForList("SELECT * FROM tarifa_sede ORDER BY id"));
		jdbc.update("DELETE FROM tarifa_sede");
	}

	// ------------------------------------------------------------------ utilidades

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
		String url = "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306) + "/" + esquema
				+ "?useSSL=false&allowPublicKeyRetrieval=true";
		return new DriverManagerDataSource(url, "root", MYSQL.getPassword());
	}
}
