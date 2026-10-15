package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Tag;
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
 * V26 sobre datos con la forma de producción: esquema migrado hasta V21 con grados de ids propios
 * e inscripciones de todas las disciplinas; migrado hasta V25 y, encima, filas escritas por el
 * código anterior después de V23 (sin disciplina_id) y un producto de examen creado después de
 * V24 (sin clasificar). Después se aplica V26 y se repite.
 */
@Tag("migracion")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SwitchGradosMigracionIntegrationTest extends MySqlIntegrationTest {

	private static final String ESQUEMA = "taemoi_v26_it";
	private static final String ESQUEMA_FALLO = "taemoi_v26_fallo_it";
	private static final List<String> TABLAS_CON_DEPORTE = List.of("alumno_deporte", "grupo", "convocatoria",
			"plantilla_preinscripcion", "preinscripcion");

	DataSource dataSource;
	JdbcTemplate jdbc;

	long tkdYKb;
	long kbAzulRojo;
	long pilatesYDpf;
	long kbSinGrado;
	long nuevoKb;
	long nuevoTkd;
	long productoTrasV24;

	@BeforeAll
	void migrarConDatosDeProduccion() {
		JdbcTemplate root = new JdbcTemplate(fuente(""));
		root.execute("DROP DATABASE IF EXISTS " + ESQUEMA);
		root.execute("CREATE DATABASE " + ESQUEMA + " CHARACTER SET utf8mb4");
		dataSource = fuente(ESQUEMA);
		jdbc = new JdbcTemplate(dataSource);

		flyway(dataSource, "21").migrate();
		insertarDatosV21();
		flyway(dataSource, "25").migrate();
		insertarDatosEscritosTrasV23();
		flyway(dataSource, "26").migrate(); // fijado a V26 (V28 borra las columnas heredadas que se comprueban aquí)
	}

	/** Id heredado de cada grado: inverso al orden del enum (BLANCO = 119 … NEGRO_5_DAN = 101). */
	private static long idHeredado(TipoGrado tipo) {
		return 119 - tipo.ordinal();
	}

	@Test
	void llegaAV26() {
		assertEquals(1, jdbc.queryForObject(
				"SELECT COUNT(*) FROM flyway_schema_history WHERE version = '26' AND success = 1", Integer.class));
	}

	@Test
	void inscripcionesDeKickboxing_pasanAlSistemaDeKickboxing_conElMismoCodigo() {
		long kb = sistema("KICKBOXING");
		assertEquals(gradoDe(kb, "ROJO"), gradoInscripcion(tkdYKb, "KICKBOXING"));
		assertEquals(gradoDe(kb, "AZUL_ROJO"), gradoInscripcion(kbAzulRojo, "KICKBOXING"));
		assertEquals(gradoDe(kb, "VERDE"), gradoInscripcion(nuevoKb, "KICKBOXING"), "fila escrita tras V23");
		// Ninguna inscripción de kickboxing queda en un grado de otro sistema.
		assertEquals(0, jdbc.queryForObject("""
				SELECT COUNT(*) FROM alumno_deporte ad JOIN grado g ON g.id = ad.grado_id
				WHERE ad.deporte = 'KICKBOXING' AND g.sistema_grados_id <> ?""", Integer.class, kb));
	}

	@Test
	void taekwondoConservaSusGrados_yDisciplinasSinGradosNoCambian() {
		assertEquals(idHeredado(TipoGrado.ROJO), gradoInscripcion(tkdYKb, "TAEKWONDO"));
		assertEquals(idHeredado(TipoGrado.NARANJA), gradoInscripcion(nuevoTkd, "TAEKWONDO"));
		assertNull(gradoInscripcion(pilatesYDpf, "PILATES"));
		assertNull(gradoInscripcion(kbSinGrado, "KICKBOXING"));
		// Inscripción heredada de defensa personal con grado: conserva el de taekwondo (sin sistema propio).
		assertEquals(idHeredado(TipoGrado.BLANCO), gradoInscripcion(pilatesYDpf, "DEFENSA_PERSONAL_FEMENINA"));
	}

	@Test
	void disciplinaId_rellenadaCoherenteYNotNull_enLasCincoTablas() {
		for (String tabla : TABLAS_CON_DEPORTE) {
			int filas = jdbc.queryForObject("SELECT COUNT(*) FROM " + tabla, Integer.class);
			assertTrue(filas > 0, "Sin filas de prueba en " + tabla);
			assertEquals(filas, jdbc.queryForObject("SELECT COUNT(*) FROM " + tabla
					+ " t JOIN disciplina d ON d.id = t.disciplina_id AND d.codigo = t.deporte"
					+ " AND d.organizacion_id = t.organizacion_id", Integer.class), tabla);
			assertEquals("NO", jdbc.queryForObject("""
					SELECT IS_NULLABLE FROM information_schema.COLUMNS
					WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = 'disciplina_id'
					""", String.class, tabla), tabla);
			// La columna heredada se conserva (contracción posterior).
			assertEquals(1, jdbc.queryForObject("""
					SELECT COUNT(*) FROM information_schema.COLUMNS
					WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = 'deporte'
					""", Integer.class, tabla), tabla);
		}
		// Fila con disciplina_id incoherente con su deporte (escrita a mano): manda deporte.
		assertEquals("PILATES", jdbc.queryForObject("""
				SELECT d.codigo FROM grupo g JOIN disciplina d ON d.id = g.disciplina_id WHERE g.nombre = 'Grupo incoherente'
				""", String.class));
	}

	@Test
	void ningunaOtraTablaGuardaGradosPorDisciplina() {
		List<String> referencias = jdbc.queryForList("""
				SELECT CONCAT(TABLE_NAME, '.', COLUMN_NAME) FROM information_schema.KEY_COLUMN_USAGE
				WHERE TABLE_SCHEMA = DATABASE() AND REFERENCED_TABLE_NAME = 'grado'
				ORDER BY 1""", String.class);
		assertEquals(List.of("alumno_deporte.grado_id", "grado.equivalente_adulto_id", "producto.grado_id"),
				referencias);
		// producto.grado_id apunta a taekwondo a propósito: el producto se comparte entre disciplinas.
		assertEquals(0, jdbc.queryForObject("""
				SELECT COUNT(*) FROM producto p JOIN grado g ON g.id = p.grado_id
				WHERE g.sistema_grados_id <> ?""", Integer.class, sistema("TAEKWONDO")));
	}

	@Test
	void productoDeExamenCreadoTrasV24_quedaClasificadoConSuGrado() {
		Map<String, Object> p = jdbc.queryForMap("""
				SELECT p.codigo, p.tipo, g.codigo AS grado FROM producto p LEFT JOIN grado g ON g.id = p.grado_id
				WHERE p.id = ?""", productoTrasV24);
		assertEquals("EXAMEN_NEGRO_3_DAN", p.get("codigo"));
		assertEquals("EXAMEN", p.get("tipo"));
		assertEquals("NEGRO_3_DAN", p.get("grado"));
	}

	@Test
	void v26IncluyeElResyncDeV24Literalmente() throws Exception {
		String v26 = leer("db/migration/V26__switch_grados_disciplinas.sql");
		String resync = leer("db/resync/resync_catalogo_v24.sql");
		assertTrue(v26.contains(resync), "V26 debe contener el texto exacto de resync_catalogo_v24.sql");
	}

	/** docs/multi-escuela/postcheck-v26.sql se ejecuta y, tras V26, da 0 donde debe. */
	@Test
	void postcheckDeV26_soloRecuentos_ySinFilasPendientes() throws Exception {
		java.nio.file.Path desdeModulo = java.nio.file.Path.of("..", "docs", "multi-escuela", "postcheck-v26.sql");
		java.nio.file.Path ruta = java.nio.file.Files.exists(desdeModulo) ? desdeModulo
				: java.nio.file.Path.of("docs", "multi-escuela", "postcheck-v26.sql");
		String texto = java.nio.file.Files.readString(ruta, StandardCharsets.UTF_8);
		List<String> consultas = java.util.Arrays.stream(texto.replaceAll("(?m)^--.*$", "").split(";"))
				.map(String::trim).filter(s -> !s.isEmpty()).toList();
		assertEquals(4, consultas.size());
		for (Map<String, Object> fila : jdbc.queryForList(consultas.get(0))) {
			assertEquals(0L, ((Number) fila.get("sin_disciplina")).longValue(), fila.toString());
		}
		assertEquals(1L, ((Number) jdbc.queryForList(consultas.get(1)).get(0).get("con_grado")).longValue(),
				"la inscripción heredada de defensa personal con grado");
		assertEquals(0L, ((Number) jdbc.queryForMap(consultas.get(2)).get("kickboxing_fuera_de_su_sistema")).longValue());
		assertTrue(((Number) jdbc.queryForMap(consultas.get(3)).get("productos_examen_o_recompensa_sin_grado"))
				.longValue() >= 0);
	}

	@Test
	void repetirV26NoCambiaNada() throws Exception {
		List<Map<String, Object>> inscripciones = jdbc.queryForList(
				"SELECT id, grado_id, disciplina_id, tarifa_id FROM alumno_deporte ORDER BY id");
		List<Map<String, Object>> productos = jdbc.queryForList(
				"SELECT id, codigo, tipo, disciplina_id, grado_id FROM producto ORDER BY id");
		List<Map<String, Object>> grupos = jdbc.queryForList("SELECT id, disciplina_id FROM grupo ORDER BY id");

		try (Connection conexion = dataSource.getConnection()) {
			ScriptUtils.executeSqlScript(conexion, new EncodedResource(
					new ClassPathResource("db/migration/V26__switch_grados_disciplinas.sql"), StandardCharsets.UTF_8));
		}

		assertEquals(inscripciones, jdbc.queryForList(
				"SELECT id, grado_id, disciplina_id, tarifa_id FROM alumno_deporte ORDER BY id"));
		assertEquals(productos, jdbc.queryForList(
				"SELECT id, codigo, tipo, disciplina_id, grado_id FROM producto ORDER BY id"));
		assertEquals(grupos, jdbc.queryForList("SELECT id, disciplina_id FROM grupo ORDER BY id"));
	}

	/**
	 * Una fila de una organización sin catálogo no puede tener disciplina: V26 se detiene nombrando la
	 * primera tabla afectada ANTES de cambiar ninguna columna (ninguna de las cinco queda NOT NULL), y
	 * tras corregir la fila y {@code flyway repair} se puede repetir.
	 */
	@Test
	void filaSinDisciplinaPosible_detieneLaMigracionAntesDeCualquierAlter() {
		JdbcTemplate root = new JdbcTemplate(fuente(""));
		root.execute("DROP DATABASE IF EXISTS " + ESQUEMA_FALLO);
		root.execute("CREATE DATABASE " + ESQUEMA_FALLO + " CHARACTER SET utf8mb4");
		DataSource fuenteFallo = fuente(ESQUEMA_FALLO);
		JdbcTemplate fallo = new JdbcTemplate(fuenteFallo);
		flyway(fuenteFallo, "25").migrate();
		fallo.update("INSERT INTO organizacion (id, slug, nombre) VALUES (2, 'sin-catalogo-it', 'Sin catálogo')");
		// Solo la SEGUNDA tabla comprobada tiene la fila sin disciplina: alumno_deporte (la primera) no
		// debe haberse pasado a NOT NULL.
		fallo.update("INSERT INTO grupo (nombre, deporte, organizacion_id) VALUES ('Grupo sin catálogo', 'TAEKWONDO', 2)");

		FlywayException error = assertThrows(FlywayException.class, () -> flyway(fuenteFallo, "26").migrate());
		assertTrue(error.getMessage().contains("v26_grupo_tiene_filas_sin_disciplina"), error.getMessage());
		for (String tabla : TABLAS_CON_DEPORTE) {
			assertEquals("YES", fallo.queryForObject("""
					SELECT IS_NULLABLE FROM information_schema.COLUMNS
					WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = 'disciplina_id'
					""", String.class, tabla), tabla);
		}

		// Se corrige la fila, se repara el historial y V26 termina.
		fallo.update("DELETE FROM grupo WHERE organizacion_id = 2");
		Flyway reintento = flyway(fuenteFallo, "26");
		reintento.repair();
		reintento.migrate();
		for (String tabla : TABLAS_CON_DEPORTE) {
			assertEquals("NO", fallo.queryForObject("""
					SELECT IS_NULLABLE FROM information_schema.COLUMNS
					WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = 'disciplina_id'
					""", String.class, tabla), tabla);
		}
		root.execute("DROP DATABASE IF EXISTS " + ESQUEMA_FALLO);
	}

	// ---------------------------------------------------------------------------------

	private void insertarDatosV21() {
		for (TipoGrado tipo : TipoGrado.values()) {
			jdbc.update("INSERT INTO grado (id, tipo_grado, organizacion_id) VALUES (?, ?, 1)", idHeredado(tipo),
					tipo.name());
		}
		tkdYKb = alumnoV21(1, "TAEKWONDO", idHeredado(TipoGrado.ROJO));
		kbAzulRojo = alumnoV21(2, "KICKBOXING", idHeredado(TipoGrado.AZUL_ROJO));
		pilatesYDpf = alumnoV21(3, null, null);
		kbSinGrado = alumnoV21(4, null, null);
		inscripcion(tkdYKb, "TAEKWONDO", idHeredado(TipoGrado.ROJO));
		inscripcion(tkdYKb, "KICKBOXING", idHeredado(TipoGrado.ROJO));
		inscripcion(kbAzulRojo, "KICKBOXING", idHeredado(TipoGrado.AZUL_ROJO));
		inscripcion(pilatesYDpf, "PILATES", null);
		inscripcion(pilatesYDpf, "DEFENSA_PERSONAL_FEMENINA", idHeredado(TipoGrado.BLANCO));
		inscripcion(kbSinGrado, "KICKBOXING", null);
		for (String deporte : List.of("TAEKWONDO", "KICKBOXING", "PILATES", "DEFENSA_PERSONAL_FEMENINA")) {
			jdbc.update("INSERT INTO grupo (nombre, deporte, organizacion_id) VALUES (?, ?, 1)", "Grupo " + deporte,
					deporte);
		}
		jdbc.update("INSERT INTO convocatoria (fecha_convocatoria, deporte, organizacion_id) VALUES ('2026-06-01', 'TAEKWONDO', 1)");
		jdbc.update("INSERT INTO producto (concepto, precio, organizacion_id) VALUES ('DERECHO A EXAMEN AZUL', 10.0, 1)");
	}

	/** Lo que escribe el código anterior a V26 después de V23: deporte sí, disciplina_id no. */
	private void insertarDatosEscritosTrasV23() {
		// Grados resueltos con la búsqueda heredada (sistema de taekwondo) también para kickboxing.
		nuevoKb = alumnoV25(10);
		inscripcion(nuevoKb, "KICKBOXING", idHeredado(TipoGrado.VERDE));
		nuevoTkd = alumnoV25(11);
		inscripcion(nuevoTkd, "TAEKWONDO", idHeredado(TipoGrado.NARANJA));
		jdbc.update("INSERT INTO grupo (nombre, deporte, organizacion_id) VALUES ('Grupo nuevo', 'KICKBOXING', 1)");
		long kb = jdbc.queryForObject("SELECT id FROM disciplina WHERE organizacion_id = 1 AND codigo = 'KICKBOXING'",
				Long.class);
		jdbc.update("INSERT INTO grupo (nombre, deporte, disciplina_id, organizacion_id) VALUES ('Grupo incoherente', 'PILATES', ?, 1)",
				kb);
		jdbc.update("INSERT INTO convocatoria (fecha_convocatoria, deporte, organizacion_id) VALUES ('2026-12-01', 'KICKBOXING', 1)");
		jdbc.update("""
				INSERT INTO plantilla_preinscripcion (deporte, version, activa, contenido, instrucciones, creada_en, organizacion_id)
				VALUES ('PILATES', 99, 0, '{}', 'Plantilla escrita tras V23', NOW(6), 1)""");
		Long plantillaKb = jdbc.queryForObject(
				"SELECT id FROM plantilla_preinscripcion WHERE deporte = 'KICKBOXING' ORDER BY id LIMIT 1", Long.class);
		jdbc.update("""
				INSERT INTO preinscripcion (referencia, temporada, deporte, estado, nombre, apellidos, dni, identidad_hash,
				  fecha_nacimiento, direccion, telefono, email, aceptacion_normas, firmante_nombre, firma, plantilla_id,
				  plantilla_snapshot, pdf_firmado, token_documento_hash, creada_en, actualizada_en, organizacion_id)
				VALUES ('V26-1', '2026-2027', 'KICKBOXING', 'PENDIENTE', 'Ana', 'Prueba', NULL, ?, '1990-01-01', 'Calle 1',
				  '600000000', 'ana@example.test', 1, 'Ana', x'00', ?, '{}', x'00', ?, NOW(6), NOW(6), 1)
				""", "e".repeat(64), plantillaKb, "f".repeat(64));
		// Producto creado por el alta manual de productos después de V24: sin codigo, tipo ni grado.
		jdbc.update("INSERT INTO producto (concepto, precio, organizacion_id) VALUES ('DERECHO A EXAMEN NEGRO 3º DAN', 60.0, 1)");
		productoTrasV24 = jdbc.queryForObject(
				"SELECT id FROM producto WHERE concepto = 'DERECHO A EXAMEN NEGRO 3º DAN'", Long.class);
		assertNull(jdbc.queryForObject("SELECT grado_id FROM producto WHERE id = ?", Long.class, productoTrasV24));
		for (String tabla : TABLAS_CON_DEPORTE) {
			assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM " + tabla + " WHERE disciplina_id IS NULL",
					Integer.class) > 0, "Sin filas «tras V23» en " + tabla);
		}
	}

	private long alumnoV21(int expediente, String deporte, Long gradoId) {
		jdbc.update("""
				INSERT INTO alumno (activo, apellidos, autorizacion_web, direccion, fecha_nacimiento, nombre,
				  numero_expediente, telefono, tiene_derecho_examen, deporte, grado_id, organizacion_id)
				VALUES (1, 'Prueba', 1, 'Calle 1', '2010-01-01', ?, ?, 600000000, 0, ?, ?, 1)
				""", "Alumno " + expediente, expediente, deporte, gradoId);
		return jdbc.queryForObject("SELECT id FROM alumno WHERE numero_expediente = ?", Long.class, expediente);
	}

	private long alumnoV25(int expediente) {
		jdbc.update("""
				INSERT INTO alumno (activo, apellidos, autorizacion_web, direccion, fecha_nacimiento, nombre,
				  numero_expediente, telefono, tiene_derecho_examen, organizacion_id)
				VALUES (1, 'Prueba', 1, 'Calle 1', '2012-01-01', ?, ?, '600000000', 0, 1)
				""", "Alumno " + expediente, expediente);
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

	private static String leer(String recurso) throws Exception {
		return new String(new ClassPathResource(recurso).getInputStream().readAllBytes(), StandardCharsets.UTF_8)
				.replace("\r\n", "\n");
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
