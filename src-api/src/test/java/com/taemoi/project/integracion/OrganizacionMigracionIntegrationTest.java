package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.dao.DataAccessException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * V18/V19 sobre datos con la forma de producción: se migra un esquema propio (dentro del mismo
 * contenedor, sin tocar el de los demás tests) hasta V17, se insertan filas representativas,
 * se migra a la última versión y se comprueba el resultado en information_schema.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrganizacionMigracionIntegrationTest extends MySqlIntegrationTest {

	private static final String ESQUEMA = "taemoi_migracion_it";

	private static final List<String> TABLAS_NEGOCIO = List.of(
			"alumno", "alumno_deporte", "alumno_convocatoria", "alumno_reto_diario_log", "auditoria_evento",
			"categoria", "convocatoria", "documento", "evento", "grado", "grupo", "imagen",
			"plantilla_preinscripcion", "preinscripcion", "producto", "producto_alumno", "registro_pendiente",
			"turno");

	private static final List<String> TABLAS_SIN_ORGANIZACION = List.of(
			"usuario", "usuario_rol", "alumno_grupo", "alumno_turno", "preinscripcion_turno",
			"configuracion_sistema", "flyway_schema_history");

	DataSource dataSource;
	JdbcTemplate jdbc;

	@BeforeAll
	void migrarDesdeV17ConDatos() throws Exception {
		JdbcTemplate root = new JdbcTemplate(fuente(""));
		root.execute("DROP DATABASE IF EXISTS " + ESQUEMA);
		root.execute("CREATE DATABASE " + ESQUEMA + " CHARACTER SET utf8mb4");
		dataSource = fuente(ESQUEMA);
		jdbc = new JdbcTemplate(dataSource);

		flyway("17").migrate();
		insertarDatosV17();
		// Producción se creó con Hibernate antes del baseline: el único de numero_expediente
		// puede tener otro nombre. V19 debe encontrarlo por sus columnas, no por su nombre.
		jdbc.execute("ALTER TABLE alumno RENAME INDEX UKi2otd4eccxkj7jwqeygr3wgoj TO uk_nombre_heredado_hibernate");

		flyway(null).migrate();
	}

	@Test
	void llegaALaUltimaVersion() {
		assertEquals("23", jdbc.queryForObject(
				"SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1",
				String.class));
	}

	@Test
	void v21IndexaLaRutaDeLosFicherosPorEscuelaYSePuedeRepetir() throws Exception {
		try (Connection conexion = dataSource.getConnection()) {
			ScriptUtils.executeSqlScript(conexion, new ClassPathResource("db/migration/V21__indices_ruta_archivos.sql"));
		}
		for (String[] tablaIndice : new String[][] { { "imagen", "idx_imagen_organizacion_ruta" },
				{ "documento", "idx_documento_organizacion_ruta" } }) {
			List<Map<String, Object>> columnas = jdbc.queryForList("""
					SELECT COLUMN_NAME, SUB_PART FROM information_schema.STATISTICS
					WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?
					ORDER BY SEQ_IN_INDEX""", tablaIndice[0], tablaIndice[1]);
			assertEquals(List.of("organizacion_id", "ruta"),
					columnas.stream().map(c -> String.valueOf(c.get("COLUMN_NAME"))).toList(), tablaIndice[1]);
			assertEquals(255, ((Number) columnas.get(1).get("SUB_PART")).intValue(), tablaIndice[1]);
		}
	}

	@Test
	void taemoiEsLaOrganizacionUnoSinDatosPersonales() {
		Map<String, Object> org = jdbc.queryForMap("SELECT * FROM organizacion WHERE id = 1");

		assertEquals("taemoi", org.get("slug"));
		assertEquals("Moi's Kim Do", org.get("nombre"));
		assertEquals("Europe/Madrid", org.get("zona_horaria"));
		assertEquals(Boolean.TRUE, org.get("activa"));
		assertNull(org.get("cif"));
		assertNull(org.get("firmante_nombre"));
		assertNull(org.get("firmante_cargo"));
		assertNull(org.get("firmante_nif"));
		assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM organizacion", Integer.class));
	}

	@Test
	void todasLasFilasDeNegocioSonDeTaemoiYLaColumnaEsObligatoria() {
		for (String tabla : TABLAS_NEGOCIO) {
			int filas = jdbc.queryForObject("SELECT COUNT(*) FROM " + tabla, Integer.class);
			int deTaemoi = jdbc.queryForObject("SELECT COUNT(*) FROM " + tabla + " WHERE organizacion_id = 1",
					Integer.class);
			assertTrue(filas > 0, "Sin filas de prueba en " + tabla);
			assertEquals(filas, deTaemoi, "Filas sin organización 1 en " + tabla);
			assertEquals("NO", columna(tabla, "IS_NULLABLE"), tabla + ".organizacion_id debe ser NOT NULL");
			assertEquals("bigint", columna(tabla, "DATA_TYPE"), tabla);
			assertNull(columna(tabla, "COLUMN_DEFAULT"), tabla + ".organizacion_id no debe tener valor por defecto");
			assertTrue(existeFk(tabla, "fk_" + tabla + "_organizacion"), "Falta FK en " + tabla);
			assertTrue(existeIndice(tabla, "idx_" + tabla + "_organizacion"), "Falta índice en " + tabla);
		}
	}

	@Test
	void insertarSinOrganizacionFalla() {
		assertThrows(DataAccessException.class, () -> jdbc.update(
				"INSERT INTO grupo (nombre, deporte) VALUES ('Sin organización', 'PILATES')"));
	}

	@Test
	void tablasGlobalesYDeUnionNoTienenOrganizacion() {
		for (String tabla : TABLAS_SIN_ORGANIZACION) {
			assertEquals(0, jdbc.queryForObject("""
					SELECT COUNT(*) FROM information_schema.COLUMNS
					WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = 'organizacion_id'
					""", Integer.class, tabla), tabla);
		}
	}

	@Test
	void configuracionCopiadaSinDuplicadosYConfiguracionSistemaIntacta() {
		assertEquals("42", jdbc.queryForObject(
				"SELECT valor FROM configuracion_organizacion WHERE organizacion_id = 1 AND clave = 'limite_turno'",
				String.class));
		assertEquals("7", jdbc.queryForObject(
				"SELECT valor FROM configuracion_organizacion WHERE organizacion_id = 1 AND clave = 'otra_clave'",
				String.class));
		assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM configuracion_organizacion", Integer.class));
		assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM configuracion_sistema", Integer.class));
		assertTrue(existeUnico("configuracion_organizacion", "uk_configuracion_organizacion_clave",
				"organizacion_id,clave"));
	}

	@Test
	void contadorDeExpedientesParteDelMaximo() {
		assertEquals(57L, jdbc.queryForObject(
				"SELECT valor FROM contador WHERE organizacion_id = 1 AND clave = 'numero_expediente'", Long.class));
		assertTrue(existeUnico("contador", "uk_contador_clave", "organizacion_id,clave"));
	}

	@Test
	void unicosPasanASerPorOrganizacion() {
		assertTrue(existeUnico("alumno", "uk_alumno_organizacion_expediente", "organizacion_id,numero_expediente"));
		assertEquals(0, jdbc.queryForObject("""
				SELECT COUNT(*) FROM (
				  SELECT INDEX_NAME FROM information_schema.STATISTICS
				  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'alumno' AND NON_UNIQUE = 0
				  GROUP BY INDEX_NAME HAVING COUNT(*) = 1 AND MAX(COLUMN_NAME) = 'numero_expediente'
				) x
				""", Integer.class), "Queda un único global sobre numero_expediente");
		assertTrue(existeUnico("plantilla_preinscripcion", "uk_plantilla_organizacion_deporte_version",
				"organizacion_id,deporte,version"));
		assertTrue(!existeIndice("plantilla_preinscripcion", "uk_plantilla_deporte_version"));
		assertTrue(existeUnico("preinscripcion", "uk_preinscripcion_activa", "clave_preinscripcion_activa"));
		String expresion = jdbc.queryForObject("""
				SELECT GENERATION_EXPRESSION FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'preinscripcion'
				  AND COLUMN_NAME = 'clave_preinscripcion_activa'
				""", String.class);
		assertTrue(expresion.contains("organizacion_id"), expresion);
		assertEquals("1|" + "c".repeat(64) + "|TAEKWONDO|2026-2027", jdbc.queryForObject(
				"SELECT clave_preinscripcion_activa FROM preinscripcion WHERE referencia = 'MIGR-1'", String.class));
	}

	@Test
	void misCambiosSonRepetiblesSobreUnaBaseYaMigrada() throws Exception {
		try (Connection conexion = dataSource.getConnection()) {
			ScriptUtils.executeSqlScript(conexion, new ClassPathResource("db/migration/V18__organizacion.sql"));
			ScriptUtils.executeSqlScript(conexion, new ClassPathResource("db/migration/V19__organizacion_id.sql"));
		}

		assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM organizacion", Integer.class));
		assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM configuracion_organizacion", Integer.class));
		assertEquals(57L, jdbc.queryForObject("SELECT valor FROM contador WHERE clave = 'numero_expediente'",
				Long.class));
		assertTrue(existeUnico("alumno", "uk_alumno_organizacion_expediente", "organizacion_id,numero_expediente"));
	}

	// ---------------------------------------------------------------------------------

	private void insertarDatosV17() {
		jdbc.update("INSERT INTO imagen (id, nombre, ruta, tipo) VALUES (1, 'foto.png', '/x/foto.png', 'image/png')");
		jdbc.update("INSERT INTO grado (id, tipo_grado) VALUES (1, 'BLANCO')");
		jdbc.update("INSERT INTO categoria (id, nombre, tipo_categoria) VALUES (1, 'Infantil', 'INFANTIL')");
		jdbc.update("INSERT INTO configuracion_sistema (clave, valor) VALUES ('limite_turno', 36)");
		jdbc.update("INSERT INTO configuracion_sistema (clave, valor) VALUES ('limite_turno', 42)");
		jdbc.update("INSERT INTO configuracion_sistema (clave, valor) VALUES ('otra_clave', 7)");
		for (int expediente : new int[] { 3, 57, 12 }) {
			jdbc.update("""
					INSERT INTO alumno (activo, apellidos, autorizacion_web, direccion, fecha_nacimiento, nombre,
					  numero_expediente, telefono, tiene_derecho_examen, grado_id)
					VALUES (1, 'Prueba', 1, 'Calle 1', '2010-01-01', ?, ?, 600000000, 0, 1)
					""", "Alumno " + expediente, expediente);
		}
		Long alumnoId = jdbc.queryForObject("SELECT id FROM alumno WHERE numero_expediente = 57", Long.class);
		jdbc.update("INSERT INTO alumno_deporte (activo, apto_para_examen, alumno_id, deporte, competidor) VALUES (1, 0, ?, 'TAEKWONDO', 0)",
				alumnoId);
		jdbc.update("INSERT INTO alumno_reto_diario_log (alumno_id, fecha_completado, anio_iso, semana_iso, created_at) VALUES (?, '2026-01-01', 2026, 1, NOW(6))",
				alumnoId);
		jdbc.update("INSERT INTO grupo (id, nombre, deporte) VALUES (1, 'Grupo migración', 'TAEKWONDO')");
		jdbc.update("INSERT INTO turno (id, dia_semana, hora_inicio, hora_fin, grupo_id) VALUES (1, 'Lunes', '17:00', '18:00', 1)");
		jdbc.update("INSERT INTO alumno_grupo (grupo_id, alumno_id) VALUES (1, ?)", alumnoId);
		jdbc.update("INSERT INTO alumno_turno (alumno_id, turno_id) VALUES (?, 1)", alumnoId);
		jdbc.update("INSERT INTO producto (id, concepto, precio) VALUES (1, 'MENSUALIDAD', 30)");
		jdbc.update("INSERT INTO producto_alumno (alumno_id, producto_id, concepto, pagado, precio) VALUES (?, 1, 'MENSUALIDAD ENERO 2026', 0, 30)",
				alumnoId);
		jdbc.update("INSERT INTO convocatoria (id, fecha_convocatoria, deporte) VALUES (1, '2026-06-01', 'TAEKWONDO')");
		jdbc.update("INSERT INTO alumno_convocatoria (cuantia_examen, pagado, alumno_id, convocatoria_id) VALUES (20, 0, ?, 1)", alumnoId);
		jdbc.update("INSERT INTO evento (id, titulo, descripcion) VALUES (1, 'Evento migración', '-')");
		jdbc.update("INSERT INTO documento (nombre, ruta, tipo, alumno_id) VALUES ('doc.pdf', '/x/doc.pdf', 'application/pdf', ?)",
				alumnoId);
		jdbc.update("INSERT INTO registro_pendiente (email, alumno_id, password_hash, token_hash, token_expires_at) VALUES ('pendiente@example.test', ?, 'x', 'y', NOW(6))", alumnoId);
		jdbc.update("INSERT INTO auditoria_evento (fecha_evento, accion, metodo_http, endpoint, estado_http, payload_truncado) VALUES (NOW(6), 'READ', 'GET', '/api/x', 200, 0)");
		Long plantillaId = jdbc.queryForObject(
				"SELECT id FROM plantilla_preinscripcion WHERE deporte = 'TAEKWONDO' ORDER BY id LIMIT 1", Long.class);
		jdbc.update("""
				INSERT INTO preinscripcion (referencia, temporada, deporte, estado, nombre, apellidos, dni, identidad_hash,
				  fecha_nacimiento, direccion, telefono, email, aceptacion_normas, firmante_nombre, firma, plantilla_id,
				  plantilla_snapshot, pdf_firmado, token_documento_hash, creada_en, actualizada_en)
				VALUES ('MIGR-1', '2026-2027', 'TAEKWONDO', 'PENDIENTE', 'Ana', 'Prueba', NULL, ?, '1990-01-01', 'Calle 1',
				  '600000000', 'ana@example.test', 1, 'Ana', x'00', ?, '{}', x'00', ?, NOW(6), NOW(6))
				""", "c".repeat(64), plantillaId, "d".repeat(64));
		Long preId = jdbc.queryForObject("SELECT id FROM preinscripcion WHERE referencia = 'MIGR-1'", Long.class);
		jdbc.update("INSERT INTO preinscripcion_turno (preinscripcion_id, turno_id) VALUES (?, 1)", preId);
		jdbc.update("""
				INSERT INTO usuario (email, nombre, apellidos, contrasena)
				VALUES ('usuario-migracion@example.test', 'Usu', 'Ario', 'x')
				""");
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

	private String columna(String tabla, String atributo) {
		return jdbc.queryForObject("SELECT " + atributo + """
				 FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = 'organizacion_id'
				""", String.class, tabla);
	}

	private boolean existeFk(String tabla, String nombre) {
		return jdbc.queryForObject("""
				SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS
				WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = ? AND CONSTRAINT_NAME = ?
				  AND REFERENCED_TABLE_NAME = 'organizacion'
				""", Integer.class, tabla, nombre) == 1;
	}

	private boolean existeIndice(String tabla, String nombre) {
		return jdbc.queryForObject("""
				SELECT COUNT(*) FROM information_schema.STATISTICS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?
				""", Integer.class, tabla, nombre) > 0;
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
