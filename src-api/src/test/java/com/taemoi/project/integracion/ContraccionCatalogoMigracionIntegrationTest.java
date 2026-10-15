package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * V28 (contracción del catálogo) sobre datos escritos por el código de V26/V27: borra las columnas
 * heredadas, crea los índices equivalentes por disciplina_id, el catálogo de categorías y la marca
 * {@code organizacion.comportamiento_legado_taemoi}; se puede repetir; y su comprobación previa detiene
 * la migración ANTES de cambiar nada si una columna heredada no coincide con el catálogo. También
 * ejecuta {@code docs/multi-escuela/precheck-v28.sql}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ContraccionCatalogoMigracionIntegrationTest extends MySqlIntegrationTest {

	private static final String ESQUEMA = "taemoi_v28_it";
	private static final String ESQUEMA_GUARDA = "taemoi_v28_guarda_it";
	private static final String ESQUEMA_MISMO_DESPLIEGUE = "taemoi_v28_mismo_despliegue_it";
	private static final String ESQUEMA_REPETICION = "taemoi_v28_repeticion_it";

	/** Placeholder de Flyway que desactiva la guarda «V28 no va en el mismo despliegue que V27». */
	static final String PERMITIR_CON_V27 = "v28_permitir_mismo_despliegue_que_v27";

	/** Columnas que borra V28: tabla -> columna. */
	private static final List<String[]> HEREDADAS = List.of(
			new String[] { "alumno_deporte", "deporte" },
			new String[] { "alumno_deporte", "tipo_tarifa" },
			new String[] { "grupo", "deporte" },
			new String[] { "convocatoria", "deporte" },
			new String[] { "plantilla_preinscripcion", "deporte" },
			new String[] { "preinscripcion", "deporte" },
			new String[] { "grado", "tipo_grado" },
			new String[] { "categoria", "tipo_categoria" });

	DataSource dataSource;
	JdbcTemplate jdbc;

	long org2;
	long tkd;
	long kb;
	long tkdOrg2;
	long inscripcionTkd;
	long inscripcionOrg2;
	Map<String, Long> precheck;
	List<Map<String, Object>> inscripcionesAntes;
	List<Map<String, Object>> gradosAntes;

	@BeforeAll
	void migrarConDatosDeV27() throws Exception {
		JdbcTemplate root = new JdbcTemplate(fuente(""));
		root.execute("DROP DATABASE IF EXISTS " + ESQUEMA);
		root.execute("CREATE DATABASE " + ESQUEMA + " CHARACTER SET utf8mb4");
		dataSource = fuente(ESQUEMA);
		jdbc = new JdbcTemplate(dataSource);

		flyway(dataSource, "27").migrate();
		insertarDatosEscritosPorV27();
		precheck = ejecutarPrecheck(jdbc);
		inscripcionesAntes = jdbc.queryForList(
				"SELECT id, alumno_id, disciplina_id, tarifa_id, grado_id, categoria_id FROM alumno_deporte ORDER BY id");
		gradosAntes = jdbc.queryForList("SELECT id, codigo, sistema_grados_id, nombre, orden FROM grado ORDER BY id");
		// Hasta V29: V30 (catálogo visual) cambia los nombres de los grados de Taemoi a propósito.
		flyway(dataSource, "29").migrate();
	}

	@Test
	void llegaAV28() {
		assertEquals(1, jdbc.queryForObject(
				"SELECT COUNT(*) FROM flyway_schema_history WHERE version = '28' AND success = 1", Integer.class));
	}

	@Test
	void borraLasColumnasHeredadas_yNingunIndiceLasNombra() {
		for (String[] heredada : HEREDADAS) {
			assertFalse(existeColumna(jdbc, heredada[0], heredada[1]), heredada[0] + "." + heredada[1]);
		}
		// Las columnas del catálogo siguen ahí, con los mismos valores.
		assertEquals(inscripcionesAntes, jdbc.queryForList(
				"SELECT id, alumno_id, disciplina_id, tarifa_id, grado_id, categoria_id FROM alumno_deporte ORDER BY id"));
		assertEquals(gradosAntes, jdbc.queryForList("SELECT id, codigo, sistema_grados_id, nombre, orden FROM grado ORDER BY id"));
		// responsable_legal_* no es de V28: lo borra V29 (PersonasContraccionMigracionIntegrationTest), que
		// también se aplica aquí. El archivo de V22 no se toca.
		assertFalse(existeColumna(jdbc, "alumno", "responsable_legal_nombre"));
		assertFalse(existeColumna(jdbc, "alumno", "responsable_legal_nif"));
		assertTrue(existeColumna(jdbc, "alumno_monodeporte_archivo", "deporte"));
	}

	@Test
	void indicesEquivalentesPorDisciplina() {
		assertEquals("alumno_id,disciplina_id", columnasDelUnico("alumno_deporte", "uk_alumno_deporte_alumno_disciplina"));
		assertEquals("organizacion_id,disciplina_id,version",
				columnasDelUnico("plantilla_preinscripcion", "uk_plantilla_organizacion_disciplina_version"));
		assertEquals("clave_preinscripcion_activa", columnasDelUnico("preinscripcion", "uk_preinscripcion_activa"));
		assertEquals("disciplina_id,apto_para_examen", columnasDelIndice("alumno_deporte", "idx_alumno_deporte_disciplina_apto"));
		assertEquals("temporada,disciplina_id,estado", columnasDelIndice("preinscripcion", "idx_preinscripcion_filtros_disciplina"));
		assertEquals("dni,disciplina_id,temporada",
				columnasDelIndice("preinscripcion", "idx_preinscripcion_dni_disciplina_temporada"));

		// La unicidad (alumno, disciplina) sigue protegida.
		Long alumno = jdbc.queryForObject("SELECT alumno_id FROM alumno_deporte WHERE id = ?", Long.class, inscripcionTkd);
		assertThrows(DuplicateKeyException.class, () -> jdbc.update("""
				INSERT INTO alumno_deporte (activo, apto_para_examen, alumno_id, competidor, organizacion_id, disciplina_id)
				VALUES (1, 0, ?, 0, 1, ?)""", alumno, tkd));
	}

	@Test
	void claveDePreinscripcionActiva_usaLaDisciplina_yMantieneLaUnicidad() {
		String expresion = jdbc.queryForObject("""
				SELECT GENERATION_EXPRESSION FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'preinscripcion' AND COLUMN_NAME = 'clave_preinscripcion_activa'""",
				String.class);
		assertTrue(expresion.contains("disciplina_id") && !expresion.contains("deporte"), expresion);
		assertEquals("1|" + "c".repeat(64) + "|" + tkd + "|2026-2027", jdbc.queryForObject(
				"SELECT clave_preinscripcion_activa FROM preinscripcion WHERE referencia = 'V28-1'", String.class));
		Long plantilla = jdbc.queryForObject("SELECT plantilla_id FROM preinscripcion WHERE referencia = 'V28-1'", Long.class);
		assertThrows(DuplicateKeyException.class, () -> preinscripcion(jdbc, "V28-2", tkd, null, plantilla));
	}

	@Test
	void marcaDeComportamientoHeredado_soloTaemoi() {
		assertEquals(Boolean.TRUE, jdbc.queryForObject(
				"SELECT comportamiento_legado_taemoi FROM organizacion WHERE id = 1", Boolean.class));
		assertEquals(Boolean.FALSE, jdbc.queryForObject(
				"SELECT comportamiento_legado_taemoi FROM organizacion WHERE id = ?", Boolean.class, org2));
		assertEquals("NO", atributo(jdbc, "organizacion", "comportamiento_legado_taemoi", "IS_NULLABLE"));
		jdbc.update("INSERT INTO organizacion (slug, nombre) VALUES ('it-v28-nueva', 'Nueva')");
		assertEquals(Boolean.FALSE, jdbc.queryForObject(
				"SELECT comportamiento_legado_taemoi FROM organizacion WHERE slug = 'it-v28-nueva'", Boolean.class));
	}

	@Test
	void categorias_codigoDeTipoCategoria_yRangosDeTaekwondo() {
		Map<String, Map<String, Object>> org1 = new LinkedHashMap<>();
		jdbc.queryForList("SELECT nombre, codigo, disciplina_id, edad_min, edad_max, orden FROM categoria WHERE organizacion_id = 1")
				.forEach(f -> org1.put((String) f.get("nombre"), f));
		assertCategoria(org1.get("Infantil"), null, tkd, null, 9, 1);
		assertCategoria(org1.get("Precadete"), null, tkd, 10, 11, 2);
		assertCategoria(org1.get("Cadete"), "CADETE", tkd, 12, 13, 3);
		assertCategoria(org1.get("Junior"), null, tkd, 14, 16, 4);
		assertCategoria(org1.get("Senior"), "SENIOR", tkd, 17, null, 5);
		// Una categoría con otro nombre no la asignaba el código: sin disciplina ni rango.
		assertCategoria(org1.get("Veteranos"), null, null, null, null, 0);
		// Cada organización con taekwondo: la suya.
		Map<String, Object> deOrg2 = jdbc.queryForMap(
				"SELECT nombre, codigo, disciplina_id, edad_min, edad_max, orden FROM categoria WHERE organizacion_id = ?", org2);
		assertCategoria(deOrg2, null, tkdOrg2, null, 9, 1);
	}

	@Test
	void alumnoConvocatoria_gradosComoTexto_mismosValores() {
		assertEquals("varchar", atributo(jdbc, "alumno_convocatoria", "grado_actual", "DATA_TYPE"));
		assertEquals("varchar", atributo(jdbc, "alumno_convocatoria", "grado_siguiente", "DATA_TYPE"));
		Map<String, Object> fila = jdbc.queryForMap("SELECT grado_actual, grado_siguiente FROM alumno_convocatoria");
		assertEquals("AMARILLO", fila.get("grado_actual"));
		assertEquals("AMARILLO_NARANJA", fila.get("grado_siguiente"));
		// Un código de grado propio de otra escuela ya cabe.
		jdbc.update("UPDATE alumno_convocatoria SET grado_siguiente = 'CINTO_PROPIO'");
		jdbc.update("UPDATE alumno_convocatoria SET grado_siguiente = 'AMARILLO_NARANJA'");
	}

	@Test
	void gradoCodigoPasaANotNull() {
		assertEquals("NO", atributo(jdbc, "grado", "codigo", "IS_NULLABLE"));
	}

	/** docs/multi-escuela/precheck-v28.sql antes de V28: solo recuentos, todos a cero en datos coherentes. */
	@Test
	void precheckDeV28_soloRecuentos() {
		Map<String, Long> esperado = new LinkedHashMap<>();
		esperado.put("alumno_deporte_deporte_diferente", 0L);
		esperado.put("alumno_deporte_tipo_tarifa_diferente", 0L);
		esperado.put("grupo_deporte_diferente", 0L);
		esperado.put("convocatoria_deporte_diferente", 0L);
		esperado.put("plantilla_deporte_diferente", 0L);
		esperado.put("preinscripcion_deporte_diferente", 0L);
		esperado.put("grado_codigo_diferente", 0L);
		esperado.put("alumno_deporte_disciplina_repetida", 0L);
		esperado.put("plantilla_version_repetida", 0L);
		esperado.put("categorias_por_nombre_sin_taekwondo", 0L);
		assertEquals(esperado, precheck);
	}

	@Test
	void repetirV28NoCambiaNada() throws Exception {
		List<Map<String, Object>> columnas = jdbc.queryForList("""
				SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, IS_NULLABLE, COLUMN_DEFAULT FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE() ORDER BY TABLE_NAME, ORDINAL_POSITION""");
		List<Map<String, Object>> indices = jdbc.queryForList("""
				SELECT TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX, COLUMN_NAME, NON_UNIQUE FROM information_schema.STATISTICS
				WHERE TABLE_SCHEMA = DATABASE() ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX""");
		List<Map<String, Object>> categorias = jdbc.queryForList("SELECT * FROM categoria ORDER BY id");
		List<Map<String, Object>> organizaciones = jdbc.queryForList(
				"SELECT id, comportamiento_legado_taemoi FROM organizacion ORDER BY id");
		List<Map<String, Object>> preinscripciones = jdbc.queryForList(
				"SELECT id, clave_preinscripcion_activa FROM preinscripcion ORDER BY id");

		try (Connection conexion = dataSource.getConnection()) {
			ScriptUtils.executeSqlScript(conexion, new EncodedResource(
					new ClassPathResource("db/migration/V28__contraccion_catalogo.sql"), StandardCharsets.UTF_8));
		}

		assertEquals(columnas, jdbc.queryForList("""
				SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, IS_NULLABLE, COLUMN_DEFAULT FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE() ORDER BY TABLE_NAME, ORDINAL_POSITION"""));
		assertEquals(indices, jdbc.queryForList("""
				SELECT TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX, COLUMN_NAME, NON_UNIQUE FROM information_schema.STATISTICS
				WHERE TABLE_SCHEMA = DATABASE() ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX"""));
		assertEquals(categorias, jdbc.queryForList("SELECT * FROM categoria ORDER BY id"));
		assertEquals(organizaciones, jdbc.queryForList(
				"SELECT id, comportamiento_legado_taemoi FROM organizacion ORDER BY id"));
		assertEquals(preinscripciones, jdbc.queryForList(
				"SELECT id, clave_preinscripcion_activa FROM preinscripcion ORDER BY id"));
	}

	/**
	 * Comprobación previa: con una fila incoherente, V28 se detiene nombrando la tabla y la columna y NO
	 * cambia nada (ni borra columnas ni crea las nuevas). Tras corregirla, {@code repair} + {@code migrate}
	 * sigue con la siguiente incoherencia y, al final, termina.
	 */
	@Test
	void comprobacionPrevia_detieneAntesDeCambiarNada_yNombraLaTabla() {
		JdbcTemplate root = new JdbcTemplate(fuente(""));
		root.execute("DROP DATABASE IF EXISTS " + ESQUEMA_GUARDA);
		root.execute("CREATE DATABASE " + ESQUEMA_GUARDA + " CHARACTER SET utf8mb4");
		DataSource fuenteGuarda = fuente(ESQUEMA_GUARDA);
		JdbcTemplate g = new JdbcTemplate(fuenteGuarda);
		flyway(fuenteGuarda, "27").migrate();

		long tkdG = disciplina(g, 1, "TAEKWONDO");
		long kbG = disciplina(g, 1, "KICKBOXING");
		long alumno = alumno(g, 1, 501);

		// 1. grupo con deporte KICKBOXING y disciplina de taekwondo.
		g.update("INSERT INTO grupo (nombre, deporte, organizacion_id, disciplina_id) VALUES ('Mal', 'KICKBOXING', 1, ?)", tkdG);
		assertFallaNombrando(fuenteGuarda, "v28_grupo_deporte_no_coincide_con_el_catalogo");
		assertNadaCambiado(g);

		// 2. Corregida; ahora una inscripción con tipo_tarifa distinto de su tarifa.
		g.update("UPDATE grupo SET disciplina_id = ? WHERE nombre = 'Mal'", kbG);
		Long adulto = g.queryForObject("SELECT id FROM tarifa WHERE organizacion_id = 1 AND codigo = 'ADULTO'", Long.class);
		g.update("""
				INSERT INTO alumno_deporte (activo, apto_para_examen, alumno_id, deporte, competidor, tipo_tarifa, tarifa_id,
				  organizacion_id, disciplina_id) VALUES (1, 0, ?, 'TAEKWONDO', 0, 'INFANTIL', ?, 1, ?)""", alumno, adulto, tkdG);
		assertFallaNombrando(fuenteGuarda, "v28_alumno_deporte_tipo_tarifa_no_coincide_con_el_catalogo");
		assertNadaCambiado(g);

		// 3. Corregida; ahora un grado sin codigo (codigo pasa a NOT NULL).
		Long infantil = g.queryForObject("SELECT id FROM tarifa WHERE organizacion_id = 1 AND codigo = 'INFANTIL'", Long.class);
		g.update("UPDATE alumno_deporte SET tarifa_id = ? WHERE alumno_id = ?", infantil, alumno);
		g.update("INSERT INTO grado (tipo_grado, organizacion_id) VALUES ('BLANCO', 1)");
		assertFallaNombrando(fuenteGuarda, "v28_grado_tipo_grado_no_coincide_con_el_catalogo");
		assertNadaCambiado(g);

		// 4. Corregido: termina.
		g.update("DELETE FROM grado WHERE codigo IS NULL");
		Flyway f = flyway(fuenteGuarda, null);
		f.repair();
		f.migrate();
		for (String[] heredada : HEREDADAS) {
			assertFalse(existeColumna(g, heredada[0], heredada[1]), heredada[0] + "." + heredada[1]);
		}
		assertEquals(kbG, g.queryForObject("SELECT disciplina_id FROM grupo WHERE nombre = 'Mal'", Long.class));
	}

	/**
	 * Guarda de despliegue: V27 y V28 en la MISMA ejecución de Flyway (sin el placeholder de pruebas)
	 * hace que V28 se detenga antes de cambiar nada, con un error que lo explica. El despliegue
	 * siguiente (otra conexión, V27 ya aplicada) hace {@code repair} + {@code migrate} y V28 se aplica.
	 */
	@Test
	void v27YV28EnLaMismaEjecucion_v28SeNiegaSinCambiarNada_yElDespliegueSiguienteLaAplica() {
		DataSource fuenteM = recrear(ESQUEMA_MISMO_DESPLIEGUE);
		JdbcTemplate m = new JdbcTemplate(fuenteM);

		FlywayException error = assertThrows(FlywayException.class, () -> flyway(fuenteM, null).migrate());
		assertTrue(mensajeCompleto(error).contains("v28_no_puede_aplicarse_en_el_mismo_despliegue_que_v27"),
				mensajeCompleto(error));
		assertEquals(1, versionAplicada(m, "27"));
		assertEquals(0, versionAplicada(m, "28"));
		assertNadaCambiado(m);

		Flyway siguiente = flyway(fuenteM, null);
		siguiente.repair();
		siguiente.migrate();
		assertEquals(1, versionAplicada(m, "28"));
		assertEquals(Boolean.TRUE, m.queryForObject(
				"SELECT comportamiento_legado_taemoi FROM organizacion WHERE id = 1", Boolean.class));
	}

	/** Con el placeholder de pruebas a true, V27 y V28 sí se aplican en una sola ejecución. */
	@Test
	void conElPlaceholderDePruebas_v27YV28EnUnaSolaEjecucion() {
		DataSource fuenteM = recrear(ESQUEMA_MISMO_DESPLIEGUE);
		JdbcTemplate m = new JdbcTemplate(fuenteM);
		flyway(fuenteM, null, true).migrate();
		assertEquals(1, versionAplicada(m, "28"));
		assertEquals(Boolean.TRUE, m.queryForObject(
				"SELECT comportamiento_legado_taemoi FROM organizacion WHERE id = 1", Boolean.class));
	}

	/**
	 * Fallo parcial: V28 falla justo después de crear organizacion.comportamiento_legado_taemoi (la
	 * actualización de la organización 1 falla). Tras {@code repair} + {@code migrate} la columna ya
	 * existe y, aun así, la organización 1 termina en TRUE y las demás en FALSE.
	 */
	@Test
	void falloParcialTrasCrearLaMarca_alRepetirV28_laOrganizacionUnoQuedaEnTrue() {
		DataSource fuenteR = recrear(ESQUEMA_REPETICION);
		JdbcTemplate r = new JdbcTemplate(fuenteR);
		flyway(fuenteR, "27").migrate();
		r.update("INSERT INTO organizacion (slug, nombre) VALUES ('it-v28-repeticion', 'Otra')");
		Long otra = r.queryForObject("SELECT id FROM organizacion WHERE slug = 'it-v28-repeticion'", Long.class);

		r.execute("""
				CREATE TRIGGER it_v28_fallo_simulado BEFORE UPDATE ON organizacion FOR EACH ROW
				SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'fallo simulado de V28'""");
		FlywayException error = assertThrows(FlywayException.class, () -> flyway(fuenteR, null).migrate());
		assertTrue(mensajeCompleto(error).contains("fallo simulado de V28"), mensajeCompleto(error));
		assertTrue(existeColumna(r, "organizacion", "comportamiento_legado_taemoi"));
		assertEquals(Boolean.FALSE, r.queryForObject(
				"SELECT comportamiento_legado_taemoi FROM organizacion WHERE id = 1", Boolean.class));

		r.execute("DROP TRIGGER it_v28_fallo_simulado");
		Flyway reintento = flyway(fuenteR, null);
		reintento.repair();
		reintento.migrate();

		assertEquals(1, versionAplicada(r, "28"));
		assertEquals(Boolean.TRUE, r.queryForObject(
				"SELECT comportamiento_legado_taemoi FROM organizacion WHERE id = 1", Boolean.class));
		assertEquals(Boolean.FALSE, r.queryForObject(
				"SELECT comportamiento_legado_taemoi FROM organizacion WHERE id = ?", Boolean.class, otra));
		for (String[] heredada : HEREDADAS) {
			assertFalse(existeColumna(r, heredada[0], heredada[1]), heredada[0] + "." + heredada[1]);
		}
	}

	// ---------------------------------------------------------------------------------

	private static DataSource recrear(String esquema) {
		JdbcTemplate root = new JdbcTemplate(fuente(""));
		root.execute("DROP DATABASE IF EXISTS " + esquema);
		root.execute("CREATE DATABASE " + esquema + " CHARACTER SET utf8mb4");
		return fuente(esquema);
	}

	private static int versionAplicada(JdbcTemplate j, String version) {
		return j.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version = ? AND success = 1",
				Integer.class, version);
	}

	private void assertFallaNombrando(DataSource fuente, String tabla) {
		Flyway f = flyway(fuente, null);
		f.repair();
		FlywayException error = assertThrows(FlywayException.class, f::migrate);
		assertTrue(mensajeCompleto(error).contains(tabla), mensajeCompleto(error));
	}

	/** Ninguna columna heredada borrada y ninguna nueva creada: la comprobación va antes de todo. */
	private void assertNadaCambiado(JdbcTemplate g) {
		for (String[] heredada : HEREDADAS) {
			assertTrue(existeColumna(g, heredada[0], heredada[1]), heredada[0] + "." + heredada[1] + " borrada");
		}
		assertFalse(existeColumna(g, "organizacion", "comportamiento_legado_taemoi"));
		assertFalse(existeColumna(g, "categoria", "codigo"));
		assertEquals("enum", atributo(g, "alumno_convocatoria", "grado_actual", "DATA_TYPE"));
		assertEquals("YES", atributo(g, "grado", "codigo", "IS_NULLABLE"));
		assertTrue(columnasDelUnicoEn(g, "alumno_deporte", "UKloubbjnmncoc7hi1oasugm5fa") != null);
	}

	private static String mensajeCompleto(Throwable error) {
		StringBuilder sb = new StringBuilder();
		for (Throwable t = error; t != null; t = t.getCause()) {
			sb.append(t.getMessage()).append(" | ");
		}
		return sb.toString();
	}

	private static void assertCategoria(Map<String, Object> fila, String codigo, Long disciplina, Integer edadMin,
			Integer edadMax, int orden) {
		assertEquals(codigo, fila.get("codigo"), "codigo " + fila);
		assertEquals(disciplina, fila.get("disciplina_id") == null ? null : ((Number) fila.get("disciplina_id")).longValue(),
				"disciplina " + fila);
		assertEquals(edadMin, fila.get("edad_min") == null ? null : ((Number) fila.get("edad_min")).intValue(), "edad_min " + fila);
		assertEquals(edadMax, fila.get("edad_max") == null ? null : ((Number) fila.get("edad_max")).intValue(), "edad_max " + fila);
		assertEquals(orden, ((Number) fila.get("orden")).intValue(), "orden " + fila);
	}

	/** Lo que escribía el código de V26/V27: columnas heredadas y del catálogo en paralelo. */
	private void insertarDatosEscritosPorV27() {
		tkd = disciplina(jdbc, 1, "TAEKWONDO");
		kb = disciplina(jdbc, 1, "KICKBOXING");
		jdbc.update("INSERT INTO organizacion (slug, nombre) VALUES ('it-v28-otra', 'Otra escuela')");
		org2 = jdbc.queryForObject("SELECT id FROM organizacion WHERE slug = 'it-v28-otra'", Long.class);
		jdbc.update("""
				INSERT INTO disciplina (organizacion_id, codigo, nombre, edad_adulto, orden, activa)
				VALUES (?, 'TAEKWONDO', 'Taekwondo', 14, 1, 1)""", org2);
		tkdOrg2 = disciplina(jdbc, org2, "TAEKWONDO");
		jdbc.update("""
				INSERT INTO tarifa (organizacion_id, disciplina_id, codigo, nombre, importe)
				VALUES (?, ?, 'CUOTA_PROPIA', 'Cuota propia', 35.00)""", org2, tkdOrg2);
		Long cuotaPropia = jdbc.queryForObject("SELECT id FROM tarifa WHERE codigo = 'CUOTA_PROPIA'", Long.class);
		Long adulto = jdbc.queryForObject("SELECT id FROM tarifa WHERE organizacion_id = 1 AND codigo = 'ADULTO'", Long.class);
		Long amarilloTkd = jdbc.queryForObject("""
				SELECT g.id FROM grado g JOIN disciplina d ON d.sistema_grados_id = g.sistema_grados_id
				WHERE d.id = ? AND g.codigo = 'AMARILLO'""", Long.class, tkd);

		long alumno = alumno(jdbc, 1, 1);
		jdbc.update("""
				INSERT INTO alumno_deporte (activo, apto_para_examen, alumno_id, deporte, competidor, tipo_tarifa, tarifa_id,
				  cuantia_tarifa, grado_id, organizacion_id, disciplina_id) VALUES (1, 0, ?, 'TAEKWONDO', 0, 'ADULTO', ?, 30, ?, 1, ?)""",
				alumno, adulto, amarilloTkd, tkd);
		inscripcionTkd = jdbc.queryForObject("SELECT id FROM alumno_deporte WHERE alumno_id = ? AND disciplina_id = ?",
				Long.class, alumno, tkd);
		jdbc.update("""
				INSERT INTO alumno_deporte (activo, apto_para_examen, alumno_id, deporte, competidor, organizacion_id, disciplina_id)
				VALUES (1, 0, ?, 'KICKBOXING', 0, 1, ?)""", alumno, kb);

		long alumnoOrg2 = alumno(jdbc, org2, 1);
		jdbc.update("""
				INSERT INTO alumno_deporte (activo, apto_para_examen, alumno_id, deporte, competidor, tipo_tarifa, tarifa_id,
				  organizacion_id, disciplina_id) VALUES (1, 0, ?, 'TAEKWONDO', 0, NULL, ?, ?, ?)""",
				alumnoOrg2, cuotaPropia, org2, tkdOrg2);
		inscripcionOrg2 = jdbc.queryForObject("SELECT id FROM alumno_deporte WHERE alumno_id = ?", Long.class, alumnoOrg2);

		jdbc.update("INSERT INTO grupo (nombre, deporte, organizacion_id, disciplina_id) VALUES ('Grupo TKD', 'TAEKWONDO', 1, ?)", tkd);
		jdbc.update("INSERT INTO convocatoria (fecha_convocatoria, deporte, organizacion_id, disciplina_id) VALUES ('2026-06-01', 'KICKBOXING', 1, ?)", kb);
		Long convocatoria = jdbc.queryForObject("SELECT MAX(id) FROM convocatoria", Long.class);
		jdbc.update("""
				INSERT INTO alumno_convocatoria (cuantia_examen, pagado, alumno_id, convocatoria_id, grado_actual, grado_siguiente,
				  organizacion_id) VALUES (20, 0, ?, ?, 'AMARILLO', 'AMARILLO_NARANJA', 1)""", alumno, convocatoria);

		Long plantilla = jdbc.queryForObject(
				"SELECT id FROM plantilla_preinscripcion WHERE organizacion_id = 1 AND deporte = 'TAEKWONDO' ORDER BY id LIMIT 1",
				Long.class);
		preinscripcion(jdbc, "V28-1", tkd, "TAEKWONDO", plantilla);

		jdbc.update("INSERT INTO categoria (nombre, tipo_categoria, organizacion_id) VALUES ('Infantil', NULL, 1)");
		jdbc.update("INSERT INTO categoria (nombre, tipo_categoria, organizacion_id) VALUES ('Precadete', NULL, 1)");
		jdbc.update("INSERT INTO categoria (nombre, tipo_categoria, organizacion_id) VALUES ('Cadete', 'CADETE', 1)");
		jdbc.update("INSERT INTO categoria (nombre, tipo_categoria, organizacion_id) VALUES ('Junior', NULL, 1)");
		jdbc.update("INSERT INTO categoria (nombre, tipo_categoria, organizacion_id) VALUES ('Senior', 'SENIOR', 1)");
		jdbc.update("INSERT INTO categoria (nombre, tipo_categoria, organizacion_id) VALUES ('Veteranos', NULL, 1)");
		jdbc.update("INSERT INTO categoria (nombre, tipo_categoria, organizacion_id) VALUES ('Infantil', NULL, ?)", org2);
	}

	private static void preinscripcion(JdbcTemplate j, String referencia, long disciplina, String deporte, Long plantilla) {
		if (deporte == null) {
			j.update("""
					INSERT INTO preinscripcion (referencia, temporada, estado, nombre, apellidos, dni, identidad_hash,
					  fecha_nacimiento, direccion, telefono, email, aceptacion_normas, firmante_nombre, firma, plantilla_id,
					  plantilla_snapshot, pdf_firmado, token_documento_hash, creada_en, actualizada_en, organizacion_id, disciplina_id)
					VALUES (?, '2026-2027', 'PENDIENTE', 'Ana', 'Prueba', NULL, ?, '1990-01-01', 'Calle 1',
					  '600000000', 'ana@example.test', 1, 'Ana', x'00', ?, '{}', x'00', ?, NOW(6), NOW(6), 1, ?)
					""", referencia, "c".repeat(64), plantilla, "d".repeat(64), disciplina);
			return;
		}
		j.update("""
				INSERT INTO preinscripcion (referencia, temporada, deporte, estado, nombre, apellidos, dni, identidad_hash,
				  fecha_nacimiento, direccion, telefono, email, aceptacion_normas, firmante_nombre, firma, plantilla_id,
				  plantilla_snapshot, pdf_firmado, token_documento_hash, creada_en, actualizada_en, organizacion_id, disciplina_id)
				VALUES (?, '2026-2027', ?, 'PENDIENTE', 'Ana', 'Prueba', NULL, ?, '1990-01-01', 'Calle 1',
				  '600000000', 'ana@example.test', 1, 'Ana', x'00', ?, '{}', x'00', ?, NOW(6), NOW(6), 1, ?)
				""", referencia, deporte, "c".repeat(64), plantilla, "d".repeat(64), disciplina);
	}

	private static long disciplina(JdbcTemplate j, long organizacion, String codigo) {
		return j.queryForObject("SELECT id FROM disciplina WHERE organizacion_id = ? AND codigo = ?", Long.class,
				organizacion, codigo);
	}

	private static long alumno(JdbcTemplate j, long organizacion, int expediente) {
		j.update("""
				INSERT INTO alumno (activo, apellidos, autorizacion_web, direccion, fecha_nacimiento, nombre,
				  numero_expediente, telefono, tiene_derecho_examen, organizacion_id)
				VALUES (1, 'Prueba', 1, 'Calle 1', '2000-01-01', ?, ?, '600000000', 0, ?)
				""", "Alumno " + expediente, expediente, organizacion);
		return j.queryForObject("SELECT id FROM alumno WHERE numero_expediente = ? AND organizacion_id = ?", Long.class,
				expediente, organizacion);
	}

	/** docs/multi-escuela/precheck-v28.sql: una fila de recuentos. */
	private static Map<String, Long> ejecutarPrecheck(JdbcTemplate j) throws Exception {
		Path desdeModulo = Path.of("..", "docs", "multi-escuela", "precheck-v28.sql");
		Path ruta = Files.exists(desdeModulo) ? desdeModulo : Path.of("docs", "multi-escuela", "precheck-v28.sql");
		String texto = Files.readString(ruta, StandardCharsets.UTF_8);
		List<String> consultas = java.util.Arrays.stream(texto.replaceAll("(?m)^--.*$", "").split(";"))
				.map(String::trim).filter(s -> !s.isEmpty()).toList();
		assertEquals(1, consultas.size());
		List<Map<String, Object>> filas = j.queryForList(consultas.get(0));
		assertEquals(1, filas.size());
		Map<String, Long> real = new LinkedHashMap<>();
		filas.get(0).forEach((k, valor) -> real.put(k, ((Number) valor).longValue()));
		return real;
	}

	private static boolean existeColumna(JdbcTemplate j, String tabla, String columna) {
		return j.queryForObject("""
				SELECT COUNT(*) FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?""", Integer.class, tabla, columna) == 1;
	}

	private static String atributo(JdbcTemplate j, String tabla, String columna, String atributo) {
		return j.queryForObject("SELECT " + atributo + """
				 FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?""", String.class, tabla, columna);
	}

	private String columnasDelUnico(String tabla, String indice) {
		return columnasDelUnicoEn(jdbc, tabla, indice);
	}

	private static String columnasDelUnicoEn(JdbcTemplate j, String tabla, String indice) {
		return j.queryForObject("""
				SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) FROM information_schema.STATISTICS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ? AND NON_UNIQUE = 0""",
				String.class, tabla, indice);
	}

	private String columnasDelIndice(String tabla, String indice) {
		return jdbc.queryForObject("""
				SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) FROM information_schema.STATISTICS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?""", String.class, tabla, indice);
	}

	/** Como en producción: la guarda de V28 activa (V27 debe venir de una ejecución anterior). */
	private static Flyway flyway(DataSource fuente, String objetivo) {
		return flyway(fuente, objetivo, false);
	}

	private static Flyway flyway(DataSource fuente, String objetivo, boolean permitirV28ConV27) {
		var config = Flyway.configure().dataSource(fuente).locations("classpath:db/migration")
				.placeholders(Map.of(PERMITIR_CON_V27, Boolean.toString(permitirV28ConV27)));
		if (objetivo != null) {
			config.target(objetivo);
		}
		return config.load();
	}

	private static DataSource fuente(String esquema) {
		String url = "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306) + "/" + esquema
				+ "?useSSL=false&allowPublicKeyRetrieval=true";
		return new DriverManagerDataSource(url, "root", MYSQL.getPassword());
	}
}
