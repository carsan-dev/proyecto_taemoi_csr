package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * V31_2 (entrega 6-1b): sede principal de los alumnos dados de alta tras V31, sobre una base en V31_1 con datos
 * sintéticos. Solo datos e idempotente: rellena los NULL de las escuelas con una sede, no toca las filas ya rellenas
 * ni las escuelas con varias sedes, y repetirla no cambia nada. Ejecuta {@code docs/multi-escuela/postcheck-v31_2.sql}.
 */
@Tag("migracion")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SedePrincipalV31_2MigracionIntegrationTest extends MySqlIntegrationTest {

	private static final String ESQUEMA = "taemoi_v31_2_it";
	private static final String V31_2 = "db/migration/V31_2__sede_principal_alumnos_sin_sede.sql";

	DataSource dataSource;
	JdbcTemplate jdbc;

	long umbrete;
	long orgUna, sedeUna, orgDos, sedeDosA, sedeDosB, orgSinSede;
	long a1Nulo, a1Relleno, aUnaNulo, aUnaAjena, aDosNulo, aDosRelleno, aSinSede;

	@BeforeAll
	void migrarConDatosSinteticos() throws Exception {
		dataSource = recrear(ESQUEMA);
		jdbc = new JdbcTemplate(dataSource);
		flyway(dataSource, "31.1").migrate();

		umbrete = jdbc.queryForObject("SELECT id FROM sede WHERE organizacion_id = 1", Long.class);
		orgUna = organizacion("it-v312-una");
		sedeUna = sede(orgUna, "Única");
		orgDos = organizacion("it-v312-dos");
		sedeDosA = sede(orgDos, "A");
		sedeDosB = sede(orgDos, "B");
		orgSinSede = organizacion("it-v312-sin");

		a1Nulo = alumno(1, null);
		a1Relleno = alumno(1, umbrete);
		aUnaNulo = alumno(orgUna, null);
		// Fila ya rellena con una sede de otra escuela (FK simple): V31_2 no la toca aunque su escuela tenga una sede.
		aUnaAjena = alumno(orgUna, sedeDosB);
		aDosNulo = alumno(orgDos, null);
		aDosRelleno = alumno(orgDos, sedeDosA);
		aSinSede = alumno(orgSinSede, null);

		// Hasta V31_2: las versiones posteriores (V31_3, entrega 6-3) tienen su propio test.
		flyway(dataSource, "31.2").migrate();
	}

	@Test
	void llegaAV31_2() {
		assertEquals("31.2", jdbc.queryForObject(
				"SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1",
				String.class));
	}

	@Test
	void rellenaSoloLosNulosDeLasEscuelasConUnaSede() {
		assertEquals(umbrete, sedeDe(a1Nulo), "Moiskimdo: el alta posterior a V31 queda en Umbrete");
		assertEquals(umbrete, sedeDe(a1Relleno));
		assertEquals(sedeUna, sedeDe(aUnaNulo));
		assertEquals(sedeDosB, sedeDe(aUnaAjena), "no toca las filas ya rellenas");
		assertNull(sedeDe(aDosNulo), "escuela con dos sedes: no se elige ninguna");
		assertEquals(sedeDosA, sedeDe(aDosRelleno));
		assertNull(sedeDe(aSinSede), "escuela sin sede");
	}

	@Test
	void soloDatos_laColumnaSigueAdmitiendoNull() {
		assertEquals("YES", jdbc.queryForObject("""
				SELECT IS_NULLABLE FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'alumno' AND COLUMN_NAME = 'sede_principal_id'""",
				String.class));
	}

	@Test
	void postcheck_ceroEnLasEscuelasConUnaSede() throws Exception {
		List<List<Map<String, Object>>> postcheck = SedesVinculosMigracionIntegrationTest.ejecutarScriptDocs(dataSource,
				"postcheck-v31_2.sql");
		Map<String, Object> fila = postcheck.get(0).get(0);
		assertEquals(0L, ((Number) fila.get("alumnos_sin_sede_escuelas_una_sede")).longValue());
		assertEquals(2L, ((Number) fila.get("alumnos_sin_sede_escuelas_varias")).longValue(),
				"aDosNulo y aSinSede");
	}

	@Test
	void repetirlaNoCambiaNada() throws Exception {
		List<Map<String, Object>> antes = jdbc.queryForList("SELECT id, sede_principal_id FROM alumno ORDER BY id");
		ejecutarV31_2();
		ejecutarV31_2();
		assertEquals(antes, jdbc.queryForList("SELECT id, sede_principal_id FROM alumno ORDER BY id"));
	}

	// ------------------------------------------------------------------ utilidades

	private Long sedeDe(long alumno) {
		return jdbc.queryForObject("SELECT sede_principal_id FROM alumno WHERE id = ?", Long.class, alumno);
	}

	private long organizacion(String slug) {
		jdbc.update("INSERT INTO organizacion (slug, nombre) VALUES (?, 'Escuela IT')", slug);
		return jdbc.queryForObject("SELECT id FROM organizacion WHERE slug = ?", Long.class, slug);
	}

	private long sede(long org, String nombre) {
		jdbc.update("INSERT INTO sede (organizacion_id, nombre, activa) VALUES (?, ?, TRUE)", org, nombre);
		return jdbc.queryForObject("SELECT id FROM sede WHERE organizacion_id = ? AND nombre = ?", Long.class, org,
				nombre);
	}

	private long alumno(long org, Long sedePrincipal) {
		long expediente = jdbc.queryForObject("SELECT COALESCE(MAX(numero_expediente), 90000) + 1 FROM alumno",
				Long.class);
		jdbc.update("""
				INSERT INTO alumno (activo, apellidos, autorizacion_web, direccion, fecha_nacimiento, nombre,
				  numero_expediente, telefono, tiene_derecho_examen, organizacion_id, sede_principal_id)
				VALUES (1, 'Prueba', 1, 'Calle 1', '2015-01-01', 'Alumno V31_2', ?, '600000000', 0, ?, ?)""",
				expediente, org, sedePrincipal);
		return jdbc.queryForObject("SELECT id FROM alumno WHERE numero_expediente = ? AND organizacion_id = ?",
				Long.class, expediente, org);
	}

	private void ejecutarV31_2() throws Exception {
		try (Connection conexion = dataSource.getConnection()) {
			ScriptUtils.executeSqlScript(conexion,
					new EncodedResource(new ClassPathResource(V31_2), StandardCharsets.UTF_8));
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
		String url = "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306) + "/" + esquema
				+ "?useSSL=false&allowPublicKeyRetrieval=true";
		return new DriverManagerDataSource(url, "root", MYSQL.getPassword());
	}
}
