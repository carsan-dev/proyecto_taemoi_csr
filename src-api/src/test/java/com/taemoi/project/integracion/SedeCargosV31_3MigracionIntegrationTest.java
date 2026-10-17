package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
 * V31_3 (entrega 6-3a, D6-9): sede de los cargos de tesorería, sobre una base en V31_2 con datos sintéticos.
 * Ampliación (columna NULL, índice y FK ON DELETE SET NULL) y relleno idempotente: escuelas con una sede → esa sede
 * (Moiskimdo, todos sus cargos); con varias → la sede principal actual del alumno del cargo, si es de la escuela;
 * sin sede principal o sin sede → NULL. Repetirla, también tras un fallo a medias, no cambia nada. Ejecuta
 * {@code docs/multi-escuela/postcheck-v31_3.sql}.
 */
@Tag("migracion")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SedeCargosV31_3MigracionIntegrationTest extends MySqlIntegrationTest {

	private static final String ESQUEMA = "taemoi_v31_3_it";
	private static final String V31_3 = "db/migration/V31_3__sede_de_los_cargos.sql";

	DataSource dataSource;
	JdbcTemplate jdbc;

	long umbrete, tkdUno;
	long orgDos, sedeDosA, sedeDosB, orgSinSede, orgUna, sedeUna, tkdDos;
	long c1Alumno, c1SoloDeporte, c1AlumnoSinSede, c1SinAlumno, c1Pagado;
	long cDosA, cDosSoloDeporteB, cDosSinSede, cDosIncoherente, cDosSedeAjena, cSinSede;
	List<Map<String, Object>> importesAntes;

	@BeforeAll
	void migrarConDatosSinteticos() throws Exception {
		dataSource = recrear(ESQUEMA);
		jdbc = new JdbcTemplate(dataSource);
		flyway(dataSource, "31.2").migrate();

		umbrete = jdbc.queryForObject("SELECT id FROM sede WHERE organizacion_id = 1", Long.class);
		tkdUno = disciplina(1);
		orgDos = organizacion("it-v313-dos");
		sedeDosA = sede(orgDos, "A");
		sedeDosB = sede(orgDos, "B");
		tkdDos = disciplina(orgDos);
		orgSinSede = organizacion("it-v313-sin");
		orgUna = organizacion("it-v313-una");
		sedeUna = sede(orgUna, "Única");

		// Moiskimdo (una sede): todos a Umbrete, tenga o no el alumno sede principal o el cargo alumno resoluble.
		long a1 = alumno(1, umbrete);
		long a1SinSede = alumno(1, null);
		c1Alumno = cargo(1, a1, null, 30.0, false);
		c1SoloDeporte = cargo(1, null, inscripcion(1, a1, tkdUno), 25.5, false);
		c1AlumnoSinSede = cargo(1, a1SinSede, null, 10.0, true);
		c1SinAlumno = cargo(1, null, null, 5.0, false);
		c1Pagado = cargo(1, a1, null, 42.0, true);

		// Escuela con dos sedes: la sede principal actual del alumno (COALESCE(alumno_id, alumno_deporte.alumno_id)).
		long aDosA = alumno(orgDos, sedeDosA);
		long aDosB = alumno(orgDos, sedeDosB);
		long aDosSinSede = alumno(orgDos, null);
		// Sede principal de OTRA escuela (FK simple): no se copia al cargo.
		long aDosAjena = alumno(orgDos, sedeUna);
		cDosA = cargo(orgDos, aDosA, null, 30.0, false);
		cDosSoloDeporteB = cargo(orgDos, null, inscripcion(orgDos, aDosB, tkdDos), 30.0, false);
		cDosSinSede = cargo(orgDos, aDosSinSede, null, 30.0, false);
		cDosIncoherente = cargo(orgDos, aDosA, inscripcion(orgDos, alumno(orgDos, sedeDosB), tkdDos), 30.0, false);
		cDosSedeAjena = cargo(orgDos, aDosAjena, null, 30.0, false);

		// Escuela sin sede.
		cSinSede = cargo(orgSinSede, alumno(orgSinSede, null), null, 30.0, false);

		importesAntes = importes();
		flyway(dataSource, null).migrate();
	}

	@Test
	@Order(1)
	void llegaAV31_3() {
		assertEquals("31.3", jdbc.queryForObject(
				"SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1",
				String.class));
	}

	@Test
	@Order(2)
	void moiskimdo_todosSusCargosEnSuUnicaSede() {
		for (long cargo : List.of(c1Alumno, c1SoloDeporte, c1AlumnoSinSede, c1SinAlumno, c1Pagado)) {
			assertEquals(umbrete, sedeDe(cargo), "cargo " + cargo);
		}
		assertEquals(0L, jdbc.queryForObject(
				"SELECT COUNT(*) FROM producto_alumno WHERE organizacion_id = 1 AND sede_id IS NULL", Long.class));
	}

	@Test
	@Order(3)
	void variasSedes_laSedePrincipalActualDelAlumno() {
		assertEquals(sedeDosA, sedeDe(cDosA));
		assertEquals(sedeDosB, sedeDe(cDosSoloDeporteB), "alumno por la inscripción");
		assertEquals(sedeDosA, sedeDe(cDosIncoherente), "como la tesorería: primero alumno_id");
		assertNull(sedeDe(cDosSinSede), "alumno sin sede principal");
		assertNull(sedeDe(cDosSedeAjena), "una sede de otra escuela no se copia");
		assertNull(sedeDe(cSinSede), "escuela sin sede");
	}

	@Test
	@Order(4)
	void mismosImportesYEstados() {
		assertEquals(importesAntes, importes(), "el relleno no toca importes, estados ni fechas");
	}

	@Test
	@Order(5)
	void ampliacion_columnaNullConIndiceYFkSetNull() {
		assertEquals("YES", jdbc.queryForObject("""
				SELECT IS_NULLABLE FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'producto_alumno' AND COLUMN_NAME = 'sede_id'""",
				String.class));
		assertEquals(1L, jdbc.queryForObject("""
				SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'producto_alumno'
				  AND INDEX_NAME = 'idx_producto_alumno_sede'""", Long.class));
		assertEquals("SET NULL", jdbc.queryForObject("""
				SELECT DELETE_RULE FROM information_schema.REFERENTIAL_CONSTRAINTS
				WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'producto_alumno'
				  AND CONSTRAINT_NAME = 'fk_producto_alumno_sede'""", String.class));
		// El código anterior inserta sin la columna: sigue funcionando (queda NULL).
		jdbc.update("""
				INSERT INTO producto_alumno (alumno_id, concepto, precio, cantidad, pagado, organizacion_id)
				VALUES (NULL, 'CARGO CODIGO ANTERIOR', 1.0, 1, 0, 1)""");
		assertNull(jdbc.queryForObject(
				"SELECT sede_id FROM producto_alumno WHERE concepto = 'CARGO CODIGO ANTERIOR'", Long.class));
		jdbc.update("DELETE FROM producto_alumno WHERE concepto = 'CARGO CODIGO ANTERIOR'");
	}

	@Test
	@Order(6)
	void postcheck_ceroSinSedeEnEscuelasDeUnaSede() throws Exception {
		List<List<Map<String, Object>>> postcheck = SedesVinculosMigracionIntegrationTest.ejecutarScriptDocs(dataSource,
				"postcheck-v31_3.sql");
		Map<String, Object> fila = postcheck.get(0).get(0);
		assertEquals(0L, numero(fila, "cargos_sin_sede_escuelas_una_sede"));
		assertEquals(0L, numero(fila, "cargos_con_sede_de_otra_escuela"));
		assertEquals(3L, numero(fila, "cargos_sin_sede_escuelas_varias"), "cDosSinSede, cDosSedeAjena y cSinSede");
		Map<String, Object> moiskimdo = postcheck.get(1).stream()
				.filter(f -> numero(f, "organizacion_id") == 1L).findFirst().orElseThrow();
		assertEquals(0L, numero(moiskimdo, "sin_sede"));
		assertEquals(1L, numero(moiskimdo, "sedes_distintas"));
	}

	@Test
	@Order(7)
	void repetirla_tambienTrasUnFalloAMedias_noCambiaNada() throws Exception {
		List<Map<String, Object>> antes = jdbc.queryForList("SELECT id, sede_id FROM producto_alumno ORDER BY id");
		ejecutarV31_3();
		ejecutarV31_3();
		assertEquals(antes, jdbc.queryForList("SELECT id, sede_id FROM producto_alumno ORDER BY id"));

		// Fallo a medias: la columna existe y quedan cargos sin rellenar, sin FK ni índice.
		jdbc.execute("ALTER TABLE producto_alumno DROP FOREIGN KEY fk_producto_alumno_sede");
		jdbc.execute("ALTER TABLE producto_alumno DROP INDEX idx_producto_alumno_sede");
		jdbc.update("UPDATE producto_alumno SET sede_id = NULL WHERE id IN (?, ?)", c1Alumno, cDosA);
		ejecutarV31_3();
		assertEquals(antes, jdbc.queryForList("SELECT id, sede_id FROM producto_alumno ORDER BY id"));
		assertTrue(jdbc.queryForObject("""
				SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS
				WHERE CONSTRAINT_SCHEMA = DATABASE() AND CONSTRAINT_NAME = 'fk_producto_alumno_sede'""",
				Long.class) == 1L);
	}

	@Test
	@Order(8)
	void borrarLaSede_dejaElCargoSinSede() {
		long sede = sede(orgUna, "Temporal");
		long cargo = cargo(orgUna, alumno(orgUna, null), null, 1.0, false);
		jdbc.update("UPDATE producto_alumno SET sede_id = ? WHERE id = ?", sede, cargo);
		jdbc.update("DELETE FROM sede WHERE id = ?", sede);
		assertNull(sedeDe(cargo));
		jdbc.update("DELETE FROM producto_alumno WHERE id = ?", cargo);
	}

	// ------------------------------------------------------------------ utilidades

	private List<Map<String, Object>> importes() {
		return jdbc.queryForList("""
				SELECT id, organizacion_id, alumno_id, alumno_deporte_id, concepto, precio, cantidad, pagado,
				  fecha_asignacion, fecha_pago FROM producto_alumno ORDER BY id""");
	}

	private Long sedeDe(long cargo) {
		return jdbc.queryForObject("SELECT sede_id FROM producto_alumno WHERE id = ?", Long.class, cargo);
	}

	private static long numero(Map<String, Object> fila, String columna) {
		return ((Number) fila.get(columna)).longValue();
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

	private long disciplina(long org) {
		Long existente = jdbc.queryForObject(
				"SELECT MIN(id) FROM disciplina WHERE organizacion_id = ? AND codigo = 'TAEKWONDO'", Long.class, org);
		if (existente != null) {
			return existente;
		}
		jdbc.update("""
				INSERT INTO disciplina (organizacion_id, codigo, nombre, sistema_grados_id, edad_adulto, orden)
				VALUES (?, 'TAEKWONDO', 'Taekwondo IT', NULL, 14, 1)""", org);
		return jdbc.queryForObject("SELECT id FROM disciplina WHERE organizacion_id = ? AND codigo = 'TAEKWONDO'",
				Long.class, org);
	}

	private long alumno(long org, Long sedePrincipal) {
		long expediente = jdbc.queryForObject("SELECT COALESCE(MAX(numero_expediente), 91000) + 1 FROM alumno",
				Long.class);
		jdbc.update("""
				INSERT INTO alumno (activo, apellidos, autorizacion_web, direccion, fecha_nacimiento, nombre,
				  numero_expediente, telefono, tiene_derecho_examen, organizacion_id, sede_principal_id)
				VALUES (1, 'Prueba', 1, 'Calle 1', '2015-01-01', 'Alumno V31_3', ?, '600000000', 0, ?, ?)""",
				expediente, org, sedePrincipal);
		return jdbc.queryForObject("SELECT id FROM alumno WHERE numero_expediente = ? AND organizacion_id = ?",
				Long.class, expediente, org);
	}

	private long inscripcion(long org, long alumno, long disciplina) {
		jdbc.update("""
				INSERT INTO alumno_deporte (activo, apto_para_examen, alumno_id, competidor, principal, tiene_licencia,
				  organizacion_id, fecha_alta, fecha_alta_inicial, disciplina_id)
				VALUES (TRUE, 0, ?, 0, 1, 0, ?, '2023-09-01', '2023-09-01', ?)""", alumno, org, disciplina);
		return jdbc.queryForObject("SELECT MAX(id) FROM alumno_deporte WHERE alumno_id = ?", Long.class, alumno);
	}

	private long cargo(long org, Long alumno, Long inscripcion, double precio, boolean pagado) {
		jdbc.update("""
				INSERT INTO producto_alumno (alumno_id, alumno_deporte_id, concepto, precio, cantidad, pagado,
				  fecha_asignacion, organizacion_id)
				VALUES (?, ?, 'MENSUALIDAD ENERO 2026', ?, 1, ?, '2026-01-05', ?)""", alumno, inscripcion, precio, pagado,
				org);
		return jdbc.queryForObject("SELECT MAX(id) FROM producto_alumno WHERE organizacion_id = ?", Long.class, org);
	}

	private void ejecutarV31_3() throws Exception {
		try (Connection conexion = dataSource.getConnection()) {
			ScriptUtils.executeSqlScript(conexion,
					new EncodedResource(new ClassPathResource(V31_3), StandardCharsets.UTF_8));
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
