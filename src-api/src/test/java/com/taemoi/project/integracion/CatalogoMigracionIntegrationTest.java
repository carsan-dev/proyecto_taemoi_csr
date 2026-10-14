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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import com.taemoi.project.legado.TipoGrado;

/**
 * V23 sobre datos con la forma de producción: esquema propio migrado hasta V21, 19 grados con
 * ids propios (no consecutivos con el enum), inscripciones de taekwondo, kickboxing y
 * disciplinas sin grados; se migra a la última versión (la V22 real borra las columnas
 * monodeporte de alumno antes de V23) y se repite V23. V23 solo amplía: ningún grado_id cambia.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CatalogoMigracionIntegrationTest extends MySqlIntegrationTest {

	private static final String ESQUEMA = "taemoi_catalogo_it";
	private static final List<String> TABLAS_CON_DEPORTE = List.of("alumno_deporte", "grupo", "convocatoria",
			"plantilla_preinscripcion", "preinscripcion");

	DataSource dataSource;
	JdbcTemplate jdbc;

	long a1;
	long a2;
	long a3;

	@BeforeAll
	void migrarDesdeV21ConDatos() {
		JdbcTemplate root = new JdbcTemplate(fuente(""));
		root.execute("DROP DATABASE IF EXISTS " + ESQUEMA);
		root.execute("CREATE DATABASE " + ESQUEMA + " CHARACTER SET utf8mb4");
		dataSource = fuente(ESQUEMA);
		jdbc = new JdbcTemplate(dataSource);

		flyway("21").migrate();
		insertarDatosV21();
		// Hasta V25: V26 (cambio de llamadores) reasigna los grados de kickboxing y pone disciplina_id
		// NOT NULL; lo comprueba SwitchGradosMigracionIntegrationTest.
		flyway("25").migrate();
	}

	/** Id heredado de cada grado: inverso al orden del enum (BLANCO = 119 … NEGRO_5_DAN = 101). */
	private static long idHeredado(TipoGrado tipo) {
		return 119 - tipo.ordinal();
	}

	@Test
	void llegaAV23() {
		assertEquals(1, jdbc.queryForObject(
				"SELECT COUNT(*) FROM flyway_schema_history WHERE version = '23' AND success = 1", Integer.class));
	}

	@Test
	void losGradosExistentesConservanSuIdYSonElSistemaDeTaekwondo() {
		long tkd = sistema("TAEKWONDO");
		for (TipoGrado tipo : TipoGrado.values()) {
			Map<String, Object> g = jdbc.queryForMap("SELECT * FROM grado WHERE id = ?", idHeredado(tipo));
			assertEquals(tipo.name(), g.get("tipo_grado"));
			assertEquals(tipo.name(), g.get("codigo"));
			assertEquals(tipo.getNombre(), g.get("nombre"));
			assertEquals(tkd, ((Number) g.get("sistema_grados_id")).longValue());
			assertEquals(tipo.ordinal() + 1, ((Number) g.get("orden")).intValue());
			assertEquals(1L, ((Number) g.get("organizacion_id")).longValue());
		}
		assertEquals(38, jdbc.queryForObject("SELECT COUNT(*) FROM grado", Integer.class));
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM grado WHERE sistema_grados_id IS NULL", Integer.class));
	}

	@Test
	void kickboxingTieneSuCopiaConNombresYOrdenPropios() {
		long kb = sistema("KICKBOXING");
		List<Map<String, Object>> copia = jdbc.queryForList(
				"SELECT * FROM grado WHERE sistema_grados_id = ? ORDER BY orden", kb);
		assertEquals(19, copia.size());
		List<String> ordenEsperado = List.of("BLANCO", "BLANCO_AMARILLO", "AMARILLO", "AMARILLO_NARANJA", "NARANJA",
				"NARANJA_VERDE", "VERDE", "VERDE_AZUL", "AZUL", "AZUL_ROJO", "ROJO", "NEGRO_1_DAN", "NEGRO_2_DAN",
				"NEGRO_3_DAN", "NEGRO_4_DAN", "NEGRO_5_DAN", "ROJO_NEGRO_1_PUM", "ROJO_NEGRO_2_PUM", "ROJO_NEGRO_3_PUM");
		assertEquals(ordenEsperado, copia.stream().map(g -> (String) g.get("codigo")).toList());
		for (Map<String, Object> g : copia) {
			TipoGrado tipo = TipoGrado.valueOf((String) g.get("codigo"));
			assertEquals(tipo.name(), g.get("tipo_grado"));
			assertEquals(tipo == TipoGrado.ROJO ? "MARRÓN" : tipo.getNombre(), g.get("nombre"));
			assertTrue(((Number) g.get("id")).longValue() > 119, "La copia usa ids nuevos");
			boolean intermedioOPum = tipo.name().contains("_") && !tipo.name().startsWith("NEGRO_");
			assertEquals(intermedioOPum ? "NINGUNO" : "TODOS", g.get("aplica_a"), tipo.name());
		}
		assertTrue(existeUnico("grado", "uk_grado_sistema_codigo", "sistema_grados_id,codigo"));
	}

	@Test
	void noSeReasignaNingunGradoExistente_lasInscripcionesSiguenEnTaekwondo() {
		assertEquals(idHeredado(TipoGrado.ROJO), gradoInscripcion(a1, "TAEKWONDO"));
		assertEquals(idHeredado(TipoGrado.ROJO), gradoInscripcion(a1, "KICKBOXING"));
		assertEquals(idHeredado(TipoGrado.AZUL_ROJO), gradoInscripcion(a2, "KICKBOXING"));
		assertNull(gradoInscripcion(a2, "PILATES"));
		assertNull(gradoInscripcion(a3, "KICKBOXING"));
		assertEquals(idHeredado(TipoGrado.BLANCO), gradoInscripcion(a3, "DEFENSA_PERSONAL_FEMENINA"));
		// La copia de kickboxing existe pero nadie la usa todavía.
		assertEquals(0, jdbc.queryForObject("""
				SELECT COUNT(*) FROM alumno_deporte ad JOIN grado g ON g.id = ad.grado_id
				WHERE g.sistema_grados_id = ?""", Integer.class, sistema("KICKBOXING")));
	}

	@Test
	void v23NoDependeDeLasColumnasMonodeporteDeAlumno() {
		for (String columna : List.of("deporte", "grado_id")) {
			assertEquals(0, jdbc.queryForObject("""
					SELECT COUNT(*) FROM information_schema.COLUMNS
					WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'alumno' AND COLUMN_NAME = ?
					""", Integer.class, columna), columna);
		}
	}

	@Test
	void disciplinaIdRellenadaEnTodasLasTablas() {
		for (String tabla : TABLAS_CON_DEPORTE) {
			int filas = jdbc.queryForObject("SELECT COUNT(*) FROM " + tabla, Integer.class);
			assertTrue(filas > 0, "Sin filas de prueba en " + tabla);
			assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM " + tabla + " WHERE disciplina_id IS NULL",
					Integer.class), tabla);
			assertEquals(filas, jdbc.queryForObject("SELECT COUNT(*) FROM " + tabla
					+ " t JOIN disciplina d ON d.id = t.disciplina_id AND d.codigo = t.deporte"
					+ " AND d.organizacion_id = t.organizacion_id", Integer.class), tabla);
			assertTrue(existeIndice(tabla, "idx_" + tabla + "_disciplina"), tabla);
			assertTrue(existeFk(tabla, "fk_" + tabla + "_disciplina", "disciplina"), tabla);
			assertEquals("YES", jdbc.queryForObject("""
					SELECT IS_NULLABLE FROM information_schema.COLUMNS
					WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = 'disciplina_id'
					""", String.class, tabla));
		}
		assertTrue(existeFk("grado", "fk_grado_sistema_grados", "sistema_grados"));
		assertTrue(existeFk("grado", "fk_grado_equivalente_adulto", "grado"));
		assertTrue(existeUnico("disciplina", "uk_disciplina_organizacion_codigo", "organizacion_id,codigo"));
	}

	@Test
	void disciplinasYSistemasDeTaemoi() {
		List<Map<String, Object>> filas = jdbc.queryForList("""
				SELECT d.codigo, d.edad_adulto, d.permite_competicion, d.requiere_licencia, sg.plantilla_origen
				FROM disciplina d LEFT JOIN sistema_grados sg ON sg.id = d.sistema_grados_id
				WHERE d.organizacion_id = 1 ORDER BY d.orden""");
		assertEquals(List.of("TAEKWONDO", "KICKBOXING", "PILATES", "DEFENSA_PERSONAL_FEMENINA"),
				filas.stream().map(f -> f.get("codigo")).toList());
		assertEquals(List.of(14, 15, 14, 14),
				filas.stream().map(f -> ((Number) f.get("edad_adulto")).intValue()).toList());
		assertEquals(java.util.Arrays.asList("TAEKWONDO", "KICKBOXING", null, null),
				filas.stream().map(f -> f.get("plantilla_origen")).toList());
		assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM sistema_grados", Integer.class));
	}

	@Test
	void repetirV23NoDuplicaNiCambiaNada() throws Exception {
		List<Map<String, Object>> gradosAntes = jdbc.queryForList("SELECT * FROM grado ORDER BY id");
		List<Map<String, Object>> inscripcionesAntes = jdbc.queryForList(
				"SELECT id, grado_id, disciplina_id FROM alumno_deporte ORDER BY id");
		List<Map<String, Object>> disciplinasAntes = jdbc.queryForList("SELECT * FROM disciplina ORDER BY id");

		try (Connection conexion = dataSource.getConnection()) {
			ScriptUtils.executeSqlScript(conexion, new EncodedResource(
					new ClassPathResource("db/migration/V23__catalogo_disciplinas_grados.sql"), StandardCharsets.UTF_8));
		}

		assertEquals(gradosAntes, jdbc.queryForList("SELECT * FROM grado ORDER BY id"));
		assertEquals(inscripcionesAntes,
				jdbc.queryForList("SELECT id, grado_id, disciplina_id FROM alumno_deporte ORDER BY id"));
		assertEquals(disciplinasAntes, jdbc.queryForList("SELECT * FROM disciplina ORDER BY id"));
		assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM sistema_grados", Integer.class));
	}

	// ---------------------------------------------------------------------------------

	private void insertarDatosV21() {
		for (TipoGrado tipo : TipoGrado.values()) {
			jdbc.update("INSERT INTO grado (id, tipo_grado, organizacion_id) VALUES (?, ?, 1)", idHeredado(tipo),
					tipo.name());
		}
		a1 = alumno(1, "TAEKWONDO", idHeredado(TipoGrado.ROJO));
		a2 = alumno(2, "KICKBOXING", idHeredado(TipoGrado.AZUL_ROJO));
		a3 = alumno(3, null, null);
		inscripcion(a1, "TAEKWONDO", idHeredado(TipoGrado.ROJO));
		inscripcion(a1, "KICKBOXING", idHeredado(TipoGrado.ROJO));
		inscripcion(a2, "KICKBOXING", idHeredado(TipoGrado.AZUL_ROJO));
		inscripcion(a2, "PILATES", null);
		inscripcion(a3, "KICKBOXING", null);
		inscripcion(a3, "DEFENSA_PERSONAL_FEMENINA", idHeredado(TipoGrado.BLANCO));
		for (String deporte : List.of("TAEKWONDO", "KICKBOXING", "PILATES", "DEFENSA_PERSONAL_FEMENINA")) {
			jdbc.update("INSERT INTO grupo (nombre, deporte, organizacion_id) VALUES (?, ?, 1)", "Grupo " + deporte,
					deporte);
		}
		jdbc.update("INSERT INTO convocatoria (fecha_convocatoria, deporte, organizacion_id) VALUES ('2026-06-01', 'TAEKWONDO', 1)");
		jdbc.update("INSERT INTO convocatoria (fecha_convocatoria, deporte, organizacion_id) VALUES ('2026-06-02', 'KICKBOXING', 1)");
		Long plantillaKb = jdbc.queryForObject(
				"SELECT id FROM plantilla_preinscripcion WHERE deporte = 'KICKBOXING' ORDER BY id LIMIT 1", Long.class);
		jdbc.update("""
				INSERT INTO preinscripcion (referencia, temporada, deporte, estado, nombre, apellidos, dni, identidad_hash,
				  fecha_nacimiento, direccion, telefono, email, aceptacion_normas, firmante_nombre, firma, plantilla_id,
				  plantilla_snapshot, pdf_firmado, token_documento_hash, creada_en, actualizada_en, organizacion_id)
				VALUES ('CAT-1', '2026-2027', 'KICKBOXING', 'PENDIENTE', 'Ana', 'Prueba', NULL, ?, '1990-01-01', 'Calle 1',
				  '600000000', 'ana@example.test', 1, 'Ana', x'00', ?, '{}', x'00', ?, NOW(6), NOW(6), 1)
				""", "e".repeat(64), plantillaKb, "f".repeat(64));
	}

	private long alumno(int expediente, String deporte, Long gradoId) {
		jdbc.update("""
				INSERT INTO alumno (activo, apellidos, autorizacion_web, direccion, fecha_nacimiento, nombre,
				  numero_expediente, telefono, tiene_derecho_examen, deporte, grado_id, organizacion_id)
				VALUES (1, 'Prueba', 1, 'Calle 1', '2010-01-01', ?, ?, 600000000, 0, ?, ?, 1)
				""", "Alumno " + expediente, expediente, deporte, gradoId);
		return jdbc.queryForObject("SELECT id FROM alumno WHERE numero_expediente = ?", Long.class, expediente);
	}

	private void inscripcion(long alumnoId, String deporte, Long gradoId) {
		jdbc.update("""
				INSERT INTO alumno_deporte (activo, apto_para_examen, alumno_id, deporte, competidor, grado_id, organizacion_id)
				VALUES (1, 0, ?, ?, 0, ?, 1)
				""", alumnoId, deporte, gradoId);
	}

	private long sistema(String plantilla) {
		return jdbc.queryForObject(
				"SELECT id FROM sistema_grados WHERE organizacion_id = 1 AND plantilla_origen = ?", Long.class, plantilla);
	}

	private Long gradoDe(long sistemaId, String codigo) {
		return jdbc.queryForObject("SELECT id FROM grado WHERE sistema_grados_id = ? AND codigo = ?", Long.class,
				sistemaId, codigo);
	}

	private Long gradoInscripcion(long alumnoId, String deporte) {
		return jdbc.queryForObject("SELECT grado_id FROM alumno_deporte WHERE alumno_id = ? AND deporte = ?",
				Long.class, alumnoId, deporte);
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

	private boolean existeIndice(String tabla, String nombre) {
		return jdbc.queryForObject("""
				SELECT COUNT(*) FROM information_schema.STATISTICS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?
				""", Integer.class, tabla, nombre) > 0;
	}

	private boolean existeFk(String tabla, String nombre, String referenciada) {
		return jdbc.queryForObject("""
				SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS
				WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = ? AND CONSTRAINT_NAME = ?
				  AND REFERENCED_TABLE_NAME = ?
				""", Integer.class, tabla, nombre, referenciada) == 1;
	}

	private boolean existeUnico(String tabla, String nombre, String columnas) {
		String reales = jdbc.queryForObject("""
				SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',')
				FROM information_schema.STATISTICS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ? AND NON_UNIQUE = 0
				""", String.class, tabla, nombre);
		return columnas.equals(reales);
	}
}
