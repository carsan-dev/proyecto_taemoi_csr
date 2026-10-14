package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * V30 (catálogo visual) sobre una base en V29 con las semillas de Taemoi y una segunda escuela sintética:
 * nombres y colores de los grados de la organización 1 (también la copia de kickboxing, con ROJO = «MARRÓN»
 * marrón), nombre de defensa personal femenina, {@code tarifa.orden} (Taemoi con el orden de crear-alumno,
 * el resto por id) y nada más de las otras escuelas; repetible, y un fallo a medias se completa con
 * {@code repair} + {@code migrate}. Todos los datos son sintéticos.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CatalogoVisualMigracionIntegrationTest extends MySqlIntegrationTest {

	private static final String ESQUEMA = "taemoi_v30_it";
	private static final String ESQUEMA_FALLO = "taemoi_v30_fallo_it";
	private static final String ESQUEMA_RELLENO = "taemoi_v30_relleno_it";
	private static final String V30 = "db/migration/V30__catalogo_visual.sql";

	/** codigo -> nombre, color_1, color_2 de V30 (taekwondo; en kickboxing igual salvo ROJO). */
	static final Map<String, String[]> ESPERADO = new LinkedHashMap<>();

	static {
		ESPERADO.put("BLANCO", new String[] { "BLANCO", "#FFFFFF", null });
		ESPERADO.put("BLANCO_AMARILLO", new String[] { "BLANCO-AMARILLO", "#FFFFFF", "#FFFF00" });
		ESPERADO.put("AMARILLO", new String[] { "AMARILLO", "#FFFF00", null });
		ESPERADO.put("AMARILLO_NARANJA", new String[] { "AMARILLO-NARANJA", "#FFFF00", "#FFA500" });
		ESPERADO.put("NARANJA", new String[] { "NARANJA", "#FFA500", null });
		ESPERADO.put("NARANJA_VERDE", new String[] { "NARANJA-VERDE", "#FFA500", "#008000" });
		ESPERADO.put("VERDE", new String[] { "VERDE", "#008000", null });
		ESPERADO.put("VERDE_AZUL", new String[] { "VERDE-AZUL", "#008000", "#0000FF" });
		ESPERADO.put("AZUL", new String[] { "AZUL", "#0000FF", null });
		ESPERADO.put("AZUL_ROJO", new String[] { "AZUL-ROJO", "#0000FF", "#FF0000" });
		ESPERADO.put("ROJO", new String[] { "ROJO", "#FF0000", null });
		ESPERADO.put("ROJO_NEGRO_1_PUM", new String[] { "ROJO-NEGRO 1º PUM", "#FF0000", "#000000" });
		ESPERADO.put("ROJO_NEGRO_2_PUM", new String[] { "ROJO-NEGRO 2º PUM", "#FF0000", "#000000" });
		ESPERADO.put("ROJO_NEGRO_3_PUM", new String[] { "ROJO-NEGRO 3º PUM", "#FF0000", "#000000" });
		ESPERADO.put("NEGRO_1_DAN", new String[] { "NEGRO 1º DAN", "#000000", null });
		ESPERADO.put("NEGRO_2_DAN", new String[] { "NEGRO 2º DAN", "#000000", null });
		ESPERADO.put("NEGRO_3_DAN", new String[] { "NEGRO 3º DAN", "#000000", null });
		ESPERADO.put("NEGRO_4_DAN", new String[] { "NEGRO 4º DAN", "#000000", null });
		ESPERADO.put("NEGRO_5_DAN", new String[] { "NEGRO 5º DAN", "#000000", null });
	}

	static final List<String> ORDEN_TAEMOI = List.of("ADULTO", "ADULTO_GRUPO", "FAMILIAR", "INFANTIL", "INFANTIL_GRUPO",
			"HERMANOS", "PADRES_HIJOS", "KICKBOXING", "PILATES", "DEFENSA_PERSONAL_FEMENINA");

	DataSource dataSource;
	JdbcTemplate jdbc;
	long org2;
	List<Map<String, Object>> otrasEscuelasAntes;
	List<Map<String, Object>> tarifasOrg2Antes;
	Map<String, Object> gradoPropioTaemoiAntes;
	List<Map<String, Object>> otroSistemaTaemoiAntes;

	@BeforeAll
	void migrarConSegundaEscuela() {
		dataSource = recrear(ESQUEMA);
		jdbc = new JdbcTemplate(dataSource);
		flyway(dataSource, "29").migrate();
		org2 = segundaEscuela(jdbc, "it-v30-otra");
		// Taemoi: una tarifa y un grado con códigos propios (dados de alta después de V24/V23).
		jdbc.update("INSERT INTO tarifa (organizacion_id, codigo, nombre, importe) VALUES (1, 'BONO_VERANO', 'Bono', 10.00)");
		long tkd = sistemaDe(jdbc, 1, "TAEKWONDO");
		jdbc.update("""
				INSERT INTO grado (organizacion_id, sistema_grados_id, codigo, nombre, orden)
				VALUES (1, ?, 'CINTO_ESPECIAL', 'Especial', 50)""", tkd);
		gradoPropioTaemoiAntes = jdbc.queryForMap("SELECT * FROM grado WHERE codigo = 'CINTO_ESPECIAL'");
		// Taemoi: otro sistema creado después de V23 (plantilla propia) con códigos iguales a los de V23.
		jdbc.update("INSERT INTO sistema_grados (organizacion_id, nombre, plantilla_origen) VALUES (1, 'Hapkido', 'HAPKIDO')");
		long hapkido = sistemaDe(jdbc, 1, "HAPKIDO");
		jdbc.update("""
				INSERT INTO grado (organizacion_id, sistema_grados_id, codigo, nombre, orden, color_1, color_2)
				VALUES (1, ?, 'ROJO', 'Rojo hapkido', 1, NULL, NULL),
				       (1, ?, 'NEGRO_1_DAN', 'Primer dan hapkido', 2, '#111111', NULL)""", hapkido, hapkido);
		otroSistemaTaemoiAntes = jdbc.queryForList("SELECT * FROM grado WHERE sistema_grados_id = ? ORDER BY id", hapkido);
		otrasEscuelasAntes = otrasEscuelas(jdbc);
		tarifasOrg2Antes = tarifasSinOrden(jdbc, org2);
		flyway(dataSource, null).migrate();
	}

	@Test
	void llegaAV30() {
		assertEquals(1, versionAplicada(jdbc, "30"));
	}

	@Test
	void gradosDeTaemoi_nombresYColores_enLosDosSistemas() {
		assertGradosTaemoi(jdbc);
	}

	/**
	 * Los colores guardados reproducen exactamente getGradoColors del frontend (grado-colors.ts): color_1 =
	 * colors[0] (mitad inferior), color_2 = colors[1] (mitad superior) o NULL si es de un solo color.
	 */
	@Test
	void coloresDeTaekwondo_igualesQueGetGradoColorsDelFrontend() {
		long tkd = sistemaDe(jdbc, 1, "TAEKWONDO");
		for (String codigo : ESPERADO.keySet()) {
			Map<String, Object> g = grado(jdbc, tkd, codigo);
			List<String> guardados = new ArrayList<>();
			guardados.add((String) g.get("color_1"));
			if (g.get("color_2") != null) {
				guardados.add((String) g.get("color_2"));
			}
			assertEquals(coloresFrontend(codigo), guardados, codigo);
		}
	}

	@Test
	void gradoPropioDeTaemoi_noSeToca() {
		assertEquals(gradoPropioTaemoiAntes, jdbc.queryForMap("SELECT * FROM grado WHERE codigo = 'CINTO_ESPECIAL'"));
	}

	/** Solo los sistemas de V23 (plantilla TAEKWONDO o KICKBOXING): otro sistema de Taemoi no se toca. */
	@Test
	void otroSistemaDeTaemoi_noSeToca() {
		assertEquals(otroSistemaTaemoiAntes, jdbc.queryForList("SELECT * FROM grado WHERE sistema_grados_id = ? ORDER BY id",
				sistemaDe(jdbc, 1, "HAPKIDO")));
	}

	@Test
	void defensaPersonalFemenina_conElNombreDecidido() {
		assertEquals("Defensa Personal Femenina", jdbc.queryForObject(
				"SELECT nombre FROM disciplina WHERE organizacion_id = 1 AND codigo = 'DEFENSA_PERSONAL_FEMENINA'", String.class));
		assertEquals("Kickboxing", jdbc.queryForObject(
				"SELECT nombre FROM disciplina WHERE organizacion_id = 1 AND codigo = 'KICKBOXING'", String.class));
	}

	@Test
	void ordenDeTarifas_taemoiComoCrearAlumno_propiasDetras_otrasPorId() {
		List<String> esperado = new ArrayList<>(ORDEN_TAEMOI);
		esperado.add("BONO_VERANO");
		assertEquals(esperado, codigosPorOrden(jdbc, 1));
		assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11), ordenes(jdbc, 1));
		// Segunda escuela: el orden de alta (ZETA, ADULTO, ALFA), 1..3.
		assertEquals(List.of("ZETA", "ADULTO", "ALFA"), codigosPorOrden(jdbc, org2));
		assertEquals(List.of(1, 2, 3), ordenes(jdbc, org2));
		Map<String, Object> columna = jdbc.queryForMap("""
				SELECT IS_NULLABLE, COLUMN_DEFAULT, DATA_TYPE FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tarifa' AND COLUMN_NAME = 'orden'""");
		assertEquals("NO", columna.get("IS_NULLABLE"));
		assertEquals("0", columna.get("COLUMN_DEFAULT"));
		assertEquals("int", columna.get("DATA_TYPE"));
	}

	@Test
	void otrasEscuelas_gradosYDisciplinasSinCambios_tarifasSoloGananOrden() {
		assertEquals(otrasEscuelasAntes, otrasEscuelas(jdbc));
		assertEquals(tarifasOrg2Antes, tarifasSinOrden(jdbc, org2));
	}

	@Test
	void unaTarifaNuevaSinOrdenRecibeCero() {
		jdbc.update("INSERT INTO tarifa (organizacion_id, codigo, nombre, importe) VALUES (?, 'NUEVA', 'Nueva', 5.00)", org2);
		assertEquals(0, jdbc.queryForObject("SELECT orden FROM tarifa WHERE organizacion_id = ? AND codigo = 'NUEVA'",
				Integer.class, org2));
		jdbc.update("DELETE FROM tarifa WHERE organizacion_id = ? AND codigo = 'NUEVA'", org2);
	}

	@Test
	void repetirV30NoCambiaNada() throws Exception {
		List<Map<String, Object>> grados = jdbc.queryForList("SELECT * FROM grado ORDER BY id");
		List<Map<String, Object>> disciplinas = jdbc.queryForList("SELECT * FROM disciplina ORDER BY id");
		List<Map<String, Object>> tarifas = jdbc.queryForList("SELECT * FROM tarifa ORDER BY id");
		List<Map<String, Object>> columnas = columnas(jdbc);
		ejecutarV30(dataSource);
		assertEquals(grados, jdbc.queryForList("SELECT * FROM grado ORDER BY id"));
		assertEquals(disciplinas, jdbc.queryForList("SELECT * FROM disciplina ORDER BY id"));
		assertEquals(tarifas, jdbc.queryForList("SELECT * FROM tarifa ORDER BY id"));
		assertEquals(columnas, columnas(jdbc));
	}

	/**
	 * Fallo a medias: primero en los grados (paso 1, nada aplicado), después en el relleno de tarifa.orden (con
	 * nombres, colores y la columna ya confirmados). Cada vez {@code repair} + {@code migrate} retoma y al final
	 * todo es lo esperado.
	 */
	@Test
	void falloParcial_repairYMigrate_terminaBien() {
		DataSource fuente = recrear(ESQUEMA_FALLO);
		JdbcTemplate f = new JdbcTemplate(fuente);
		flyway(fuente, "29").migrate();
		long otra = segundaEscuela(f, "it-v30-fallo");
		List<Map<String, Object>> otrasAntes = otrasEscuelas(f);

		// a) Falla al actualizar los grados: nada cambia.
		f.execute("""
				CREATE TRIGGER it_v30_fallo_grado BEFORE UPDATE ON grado FOR EACH ROW
				SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'fallo simulado de V30 (grado)'""");
		FlywayException error = assertThrows(FlywayException.class, () -> flyway(fuente, null).migrate());
		assertTrue(mensajeCompleto(error).contains("fallo simulado de V30 (grado)"), mensajeCompleto(error));
		assertEquals("Rojo", f.queryForObject("SELECT nombre FROM grado WHERE sistema_grados_id = ? AND codigo = 'ROJO'",
				String.class, sistemaDe(f, 1, "TAEKWONDO")));
		assertFalse(existeColumna(f, "tarifa", "orden"));
		f.execute("DROP TRIGGER it_v30_fallo_grado");

		// b) Falla al rellenar tarifa.orden: grados, disciplina y columna (NULL) ya aplicados.
		f.execute("""
				CREATE TRIGGER it_v30_fallo_tarifa BEFORE UPDATE ON tarifa FOR EACH ROW
				SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'fallo simulado de V30 (tarifa)'""");
		Flyway segundo = flyway(fuente, null);
		segundo.repair();
		error = assertThrows(FlywayException.class, segundo::migrate);
		assertTrue(mensajeCompleto(error).contains("fallo simulado de V30 (tarifa)"), mensajeCompleto(error));
		assertGradosTaemoi(f);
		assertTrue(existeColumna(f, "tarifa", "orden"));
		assertEquals(0, f.queryForObject("SELECT COUNT(*) FROM tarifa WHERE orden IS NOT NULL", Integer.class));
		f.execute("DROP TRIGGER it_v30_fallo_tarifa");

		// c) Termina.
		Flyway tercero = flyway(fuente, null);
		tercero.repair();
		tercero.migrate();
		assertEquals(1, versionAplicada(f, "30"));
		assertGradosTaemoi(f);
		assertEquals(ORDEN_TAEMOI, codigosPorOrden(f, 1));
		assertEquals(List.of("ZETA", "ADULTO", "ALFA"), codigosPorOrden(f, otra));
		assertEquals(otrasAntes, otrasEscuelas(f));
		assertEquals("NO", f.queryForObject("""
				SELECT IS_NULLABLE FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tarifa' AND COLUMN_NAME = 'orden'""", String.class));
	}

	/**
	 * Fallo justo antes del NOT NULL (columna añadida y rellena): al repetir, el relleno no pisa los valores ya
	 * escritos y solo falta la restricción.
	 */
	@Test
	void falloAntesDelNotNull_repetirConservaElRelleno() throws Exception {
		DataSource fuente = recrear(ESQUEMA_RELLENO);
		JdbcTemplate r = new JdbcTemplate(fuente);
		flyway(fuente, "29").migrate();
		r.execute("ALTER TABLE tarifa ADD COLUMN orden INT NULL AFTER activa");
		r.update("UPDATE tarifa SET orden = 100 + id WHERE organizacion_id = 1 AND codigo <> 'PILATES'");

		ejecutarV30(fuente);

		assertEquals(100 + r.queryForObject("SELECT id FROM tarifa WHERE codigo = 'ADULTO'", Integer.class),
				r.queryForObject("SELECT orden FROM tarifa WHERE codigo = 'ADULTO'", Integer.class));
		assertEquals(9, r.queryForObject("SELECT orden FROM tarifa WHERE codigo = 'PILATES'", Integer.class),
				"solo se rellenan las filas sin orden");
		assertEquals("NO", r.queryForObject("""
				SELECT IS_NULLABLE FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tarifa' AND COLUMN_NAME = 'orden'""", String.class));
		assertGradosTaemoi(r);
	}

	// ---------------------------------------------------------------------------------

	private static void assertGradosTaemoi(JdbcTemplate j) {
		long tkd = sistemaDe(j, 1, "TAEKWONDO");
		long kb = sistemaDe(j, 1, "KICKBOXING");
		assertEquals(kb, j.queryForObject(
				"SELECT sistema_grados_id FROM disciplina WHERE organizacion_id = 1 AND codigo = 'KICKBOXING'", Long.class));
		for (Map.Entry<String, String[]> e : ESPERADO.entrySet()) {
			String codigo = e.getKey();
			assertGrado(j, tkd, codigo, e.getValue());
			String[] kickboxing = codigo.equals("ROJO") ? new String[] { "MARRÓN", "#8B4513", null } : e.getValue();
			assertGrado(j, kb, codigo, kickboxing);
		}
		assertEquals(38, j.queryForObject("""
				SELECT COUNT(*) FROM grado g JOIN sistema_grados s ON s.id = g.sistema_grados_id
				WHERE s.organizacion_id = 1 AND s.plantilla_origen IN ('TAEKWONDO', 'KICKBOXING') AND g.color_1 IS NOT NULL""", Integer.class));
	}

	private static void assertGrado(JdbcTemplate j, long sistema, String codigo, String[] esperado) {
		Map<String, Object> g = grado(j, sistema, codigo);
		assertEquals(List.of(String.valueOf(esperado[0]), String.valueOf(esperado[1]), String.valueOf(esperado[2])),
				List.of(String.valueOf(g.get("nombre")), String.valueOf(g.get("color_1")), String.valueOf(g.get("color_2"))),
				sistema + "/" + codigo);
	}

	private static Map<String, Object> grado(JdbcTemplate j, long sistema, String codigo) {
		return j.queryForMap("SELECT * FROM grado WHERE sistema_grados_id = ? AND codigo = ?", sistema, codigo);
	}

	/** Réplica de getGradoColors (src-frontend/src/app/utilities/grado-colors.ts). */
	static List<String> coloresFrontend(String tipoGrado) {
		Map<String, String> paleta = new LinkedHashMap<>();
		paleta.put("BLANCO", "#FFFFFF");
		paleta.put("AMARILLO", "#FFFF00");
		paleta.put("NARANJA", "#FFA500");
		paleta.put("VERDE", "#008000");
		paleta.put("AZUL", "#0000FF");
		paleta.put("ROJO", "#FF0000");
		paleta.put("NEGRO", "#000000");
		String upper = tipoGrado.toUpperCase(Locale.ROOT);
		List<String> colores = new ArrayList<>();
		for (String parte : upper.split("[\\s/_-]+")) {
			if (!parte.isEmpty() && paleta.containsKey(parte)) {
				colores.add(paleta.get(parte));
			}
		}
		if (colores.size() >= 2) {
			return colores.subList(0, 2);
		}
		for (String nombre : List.of("BLANCO", "AMARILLO", "NARANJA", "VERDE", "AZUL", "ROJO")) {
			if (upper.startsWith(nombre)) {
				return List.of(paleta.get(nombre));
			}
		}
		if (upper.contains("NEGRO") || upper.contains("DAN")) {
			return List.of(paleta.get("NEGRO"));
		}
		return List.of("#CCCCCC");
	}

	/**
	 * Segunda escuela con los mismos códigos que Taemoi (KICKBOXING, DEFENSA_PERSONAL_FEMENINA, ROJO…) para
	 * comprobar que V30 no la toca. Tarifas dadas de alta en el orden ZETA, ADULTO, ALFA.
	 */
	private static long segundaEscuela(JdbcTemplate j, String slug) {
		j.update("INSERT INTO organizacion (slug, nombre) VALUES (?, 'Otra escuela')", slug);
		long org = j.queryForObject("SELECT id FROM organizacion WHERE slug = ?", Long.class, slug);
		j.update("INSERT INTO sistema_grados (organizacion_id, nombre, plantilla_origen) VALUES (?, 'Kick', 'KICKBOXING')", org);
		long sistema = sistemaDe(j, org, "KICKBOXING");
		j.update("""
				INSERT INTO disciplina (organizacion_id, codigo, nombre, sistema_grados_id, edad_adulto, orden)
				VALUES (?, 'KICKBOXING', 'Kick de la otra', ?, 16, 1),
				       (?, 'DEFENSA_PERSONAL_FEMENINA', 'Defensa personal femenina', NULL, 14, 2)""", org, sistema, org);
		j.update("""
				INSERT INTO grado (organizacion_id, sistema_grados_id, codigo, nombre, orden, color_1, color_2)
				VALUES (?, ?, 'ROJO', 'Rojo', 1, NULL, NULL),
				       (?, ?, 'BLANCO_AMARILLO', 'Blanco y amarillo', 2, '#EEEEEE', '#DDDD00'),
				       (?, ?, 'NEGRO_1_DAN', 'Primer dan', 3, NULL, NULL)""", org, sistema, org, sistema, org, sistema);
		for (String codigo : List.of("ZETA", "ADULTO", "ALFA")) {
			j.update("INSERT INTO tarifa (organizacion_id, codigo, nombre, importe) VALUES (?, ?, ?, 1.00)", org, codigo,
					codigo.toLowerCase(Locale.ROOT));
		}
		return org;
	}

	private static long sistemaDe(JdbcTemplate j, long organizacion, String plantilla) {
		return j.queryForObject(
				"SELECT id FROM sistema_grados WHERE organizacion_id = ? AND plantilla_origen = ? ORDER BY id LIMIT 1",
				Long.class, organizacion, plantilla);
	}

	/** Grados, disciplinas y sistemas de las organizaciones distintas de 1. */
	private static List<Map<String, Object>> otrasEscuelas(JdbcTemplate j) {
		List<Map<String, Object>> filas = new ArrayList<>();
		filas.addAll(j.queryForList("SELECT * FROM grado WHERE organizacion_id <> 1 ORDER BY id"));
		filas.addAll(j.queryForList("SELECT * FROM disciplina WHERE organizacion_id <> 1 ORDER BY id"));
		filas.addAll(j.queryForList("SELECT * FROM sistema_grados WHERE organizacion_id <> 1 ORDER BY id"));
		return filas;
	}

	private static List<Map<String, Object>> tarifasSinOrden(JdbcTemplate j, long organizacion) {
		return j.queryForList("""
				SELECT id, organizacion_id, disciplina_id, codigo, nombre, importe, importe_adulto, importe_menor, activa
				FROM tarifa WHERE organizacion_id = ? ORDER BY id""", organizacion);
	}

	private static List<String> codigosPorOrden(JdbcTemplate j, long organizacion) {
		return j.queryForList("SELECT codigo FROM tarifa WHERE organizacion_id = ? ORDER BY orden, id", String.class,
				organizacion);
	}

	private static List<Integer> ordenes(JdbcTemplate j, long organizacion) {
		return j.queryForList("SELECT orden FROM tarifa WHERE organizacion_id = ? ORDER BY orden, id", Integer.class,
				organizacion);
	}

	private static void ejecutarV30(DataSource fuente) throws Exception {
		try (Connection conexion = fuente.getConnection()) {
			ScriptUtils.executeSqlScript(conexion, new EncodedResource(new ClassPathResource(V30), StandardCharsets.UTF_8));
		}
	}

	private static List<Map<String, Object>> columnas(JdbcTemplate j) {
		return j.queryForList("""
				SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, IS_NULLABLE, COLUMN_DEFAULT FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE() ORDER BY TABLE_NAME, ORDINAL_POSITION""");
	}

	private static boolean existeColumna(JdbcTemplate j, String tabla, String columna) {
		return j.queryForObject("""
				SELECT COUNT(*) FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?""", Integer.class, tabla, columna) == 1;
	}

	private static int versionAplicada(JdbcTemplate j, String version) {
		return j.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version = ? AND success = 1",
				Integer.class, version);
	}

	private static String mensajeCompleto(Throwable error) {
		StringBuilder sb = new StringBuilder();
		for (Throwable t = error; t != null; t = t.getCause()) {
			sb.append(t.getMessage()).append(" | ");
		}
		return sb.toString();
	}

	/** V27 y V28 llegan en una sola ejecución (guarda de pruebas desactivada). */
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
