package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
 * V31_19: en kickboxing el cinturón que el código llama ROJO es marrón. V30 ya llamó «MARRÓN» (#8B4513) al grado ROJO del
 * sistema de kickboxing de la organización 1; V31_19 corrige los cuatro intermedios que aún decían «ROJO» y llevaban la
 * franja roja #FF0000 (AZUL-ROJO y ROJO-NEGRO 1º/2º/3º PUM): nombre con MARRÓN y franja #8B4513. Sobre una base en V31_18
 * con las semillas de Taemoi, otro sistema de Taemoi con plantilla KICKBOXING que no es el de la disciplina y una segunda
 * escuela con los mismos valores: solo cambian nombre y colores de esas cuatro filas; ni códigos, ni taekwondo, ni
 * productos, ni las otras escuelas. Un valor que no sea exactamente el sembrado (byte a byte, también con un espacio
 * final) no se toca (los demás de la fila, sí); si la disciplina apunta a un sistema sin plantilla KICKBOXING, nada.
 * Repetirla no cambia nada; tras un fallo (también a mitad de la sentencia) no aplica nada y se completa. Ejecuta {@code docs/multi-escuela/postcheck-grados-kickboxing.sql}.
 * Datos inventados.
 */
@Tag("migracion")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GradosKickboxingV31_19MigracionIntegrationTest extends MySqlIntegrationTest {

	private static final String ESQUEMA = "taemoi_v31_19_it";
	private static final String V31_19 = "db/migration/V31_19__grados_kickboxing_marron.sql";

	private static final String MARRON = "#8B4513";

	/**
	 * codigo -> nombre, color_1, color_2 antes (V30) y después (V31_19) en el sistema de kickboxing de la organización 1:
	 * { nombre antes, nombre después, color_1 antes, color_1 después, color_2 antes, color_2 después }.
	 */
	private static final Map<String, String[]> RENOMBRADOS = new LinkedHashMap<>();

	static {
		RENOMBRADOS.put("AZUL_ROJO",
				new String[] { "AZUL-ROJO", "AZUL-MARRÓN", "#0000FF", "#0000FF", "#FF0000", MARRON });
		for (int n = 1; n <= 3; n++) {
			RENOMBRADOS.put("ROJO_NEGRO_" + n + "_PUM", new String[] { "ROJO-NEGRO " + n + "º PUM",
					"MARRÓN-NEGRO " + n + "º PUM", "#FF0000", MARRON, "#000000", "#000000" });
		}
	}

	private static final List<String> TABLAS_SIN_CAMBIOS = List.of("sistema_grados", "disciplina", "producto", "alumno",
			"organizacion");
	private static final String COLUMNAS_DEL_ESQUEMA = """
			SELECT TABLE_NAME, COLUMN_NAME, ORDINAL_POSITION, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
			FROM information_schema.COLUMNS
			WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME <> 'flyway_schema_history'
			ORDER BY TABLE_NAME, ORDINAL_POSITION""";

	DataSource dataSource;
	JdbcTemplate jdbc;
	long kb;
	long tkd;
	long otroSistemaTaemoi;
	long otra;
	List<Map<String, Object>> gradosAntes;
	List<Map<String, Object>> columnasAntes;
	Map<String, List<Map<String, Object>>> filasAntes = new LinkedHashMap<>();

	@BeforeAll
	void migrar() {
		dataSource = recrear(ESQUEMA);
		jdbc = new JdbcTemplate(dataSource);
		flyway(dataSource, "31.18").migrate();
		kb = jdbc.queryForObject(
				"SELECT sistema_grados_id FROM disciplina WHERE organizacion_id = 1 AND codigo = 'KICKBOXING'", Long.class);
		tkd = jdbc.queryForObject(
				"SELECT sistema_grados_id FROM disciplina WHERE organizacion_id = 1 AND codigo = 'TAEKWONDO'", Long.class);

		// Taemoi: otro sistema con plantilla KICKBOXING que no es el de la disciplina KICKBOXING.
		jdbc.update("INSERT INTO sistema_grados (organizacion_id, nombre, plantilla_origen) VALUES (1, 'Kick ligero', 'KICKBOXING')");
		otroSistemaTaemoi = jdbc.queryForObject(
				"SELECT MAX(id) FROM sistema_grados WHERE organizacion_id = 1 AND nombre = 'Kick ligero'", Long.class);
		jdbc.update("""
				INSERT INTO grado (organizacion_id, sistema_grados_id, codigo, nombre, orden)
				VALUES (1, ?, 'AZUL_ROJO', 'AZUL-ROJO', 1), (1, ?, 'ROJO_NEGRO_1_PUM', 'ROJO-NEGRO 1º PUM', 2)""",
				otroSistemaTaemoi, otroSistemaTaemoi);

		// Otra escuela con kickboxing y los mismos códigos y nombres.
		jdbc.update("INSERT INTO organizacion (slug, nombre) VALUES ('otra-v31-19', 'Otra escuela V31_19')");
		otra = jdbc.queryForObject("SELECT id FROM organizacion WHERE slug = 'otra-v31-19'", Long.class);
		jdbc.update("INSERT INTO sistema_grados (organizacion_id, nombre, plantilla_origen) VALUES (?, 'Kick', 'KICKBOXING')",
				otra);
		long sistemaOtra = jdbc.queryForObject("SELECT id FROM sistema_grados WHERE organizacion_id = ?", Long.class, otra);
		jdbc.update("""
				INSERT INTO disciplina (organizacion_id, codigo, nombre, sistema_grados_id, edad_adulto, orden)
				VALUES (?, 'KICKBOXING', 'Kickboxing', ?, 16, 1)""", otra, sistemaOtra);
		jdbc.update("""
				INSERT INTO grado (organizacion_id, sistema_grados_id, codigo, nombre, orden, color_1, color_2)
				VALUES (?, ?, 'AZUL_ROJO', 'AZUL-ROJO', 1, '#0000FF', '#FF0000'),
				       (?, ?, 'ROJO_NEGRO_1_PUM', 'ROJO-NEGRO 1º PUM', 2, '#FF0000', '#000000')""",
				otra, sistemaOtra, otra, sistemaOtra);

		for (Map.Entry<String, String[]> e : RENOMBRADOS.entrySet()) {
			String[] v = e.getValue();
			assertEquals(List.of(v[0], v[2], v[4]), valores(kb, e.getKey()), "semilla de V30: " + e.getKey());
		}
		assertEquals(List.of("MARRÓN", MARRON, "null"), valores(kb, "ROJO"), "V30: el ROJO de kickboxing ya es marrón");
		gradosAntes = grados();
		columnasAntes = jdbc.queryForList(COLUMNAS_DEL_ESQUEMA);
		for (String tabla : TABLAS_SIN_CAMBIOS) {
			filasAntes.put(tabla, jdbc.queryForList("SELECT * FROM " + tabla + " ORDER BY id"));
		}
		// Hasta V31_19: las migraciones posteriores tendrán su propio test.
		flyway(dataSource, "31.19").migrate();
	}

	@Test
	@Order(1)
	void llegaAV31_19() {
		assertEquals("31.19", jdbc.queryForObject(
				"SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1",
				String.class));
	}

	@Test
	@Order(2)
	void kickboxingDeTaemoi_losCuatroIntermediosPasanAMarron_nombreYFranja() {
		for (Map.Entry<String, String[]> e : RENOMBRADOS.entrySet()) {
			String[] v = e.getValue();
			assertEquals(List.of(v[1], v[3], v[5]), valores(kb, e.getKey()), e.getKey());
		}
		assertEquals(List.of("MARRÓN", MARRON, "null"), valores(kb, "ROJO"));
		assertEquals(0L, jdbc.queryForObject(
				"SELECT COUNT(*) FROM grado WHERE sistema_grados_id = ? AND UPPER(nombre) LIKE '%ROJO%'", Long.class, kb));
		assertEquals(0L, jdbc.queryForObject("""
				SELECT COUNT(*) FROM grado WHERE sistema_grados_id = ?
				  AND (UPPER(color_1) = '#FF0000' OR UPPER(color_2) = '#FF0000')""", Long.class, kb));
	}

	/** Del resto de la tabla grado nada cambia; de esas cuatro filas, solo nombre y colores (código, orden… igual). */
	@Test
	@Order(3)
	void soloCambiaElNombreDeEsasCuatroFilas() {
		assertEquals(esperadoTrasV31_19(gradosAntes), grados());
	}

	@Test
	@Order(4)
	void taekwondo_productos_otrosSistemasYOtrasEscuelas_sinCambios() {
		assertEquals(List.of("AZUL-ROJO", "#0000FF", "#FF0000"), valores(tkd, "AZUL_ROJO"));
		assertEquals(List.of("ROJO", "#FF0000", "null"), valores(tkd, "ROJO"));
		for (int n = 1; n <= 3; n++) {
			assertEquals(List.of("ROJO-NEGRO " + n + "º PUM", "#FF0000", "#000000"), valores(tkd, "ROJO_NEGRO_" + n + "_PUM"));
		}
		assertEquals("AZUL-ROJO", nombre(otroSistemaTaemoi, "AZUL_ROJO"));
		assertEquals(List.of("AZUL-ROJO|#0000FF|#FF0000", "ROJO-NEGRO 1º PUM|#FF0000|#000000"), jdbc.queryForList(
				"SELECT CONCAT_WS('|', nombre, color_1, color_2) FROM grado WHERE organizacion_id = ? ORDER BY orden",
				String.class, otra));
		for (String tabla : TABLAS_SIN_CAMBIOS) {
			assertEquals(filasAntes.get(tabla), jdbc.queryForList("SELECT * FROM " + tabla + " ORDER BY id"), tabla);
		}
		assertEquals(columnasAntes, jdbc.queryForList(COLUMNAS_DEL_ESQUEMA), "el esquema no cambia");
	}

	@Test
	@Order(5)
	void postcheck_justoTrasDesplegar() throws Exception {
		Map<String, Object> fila = postcheck();
		assertEquals(1L, numero(fila, "flyway_31_19"));
		assertEquals(1L, numero(fila, "ultima_es_31_19"));
		assertEquals(0L, numero(fila, "grados_kb_con_rojo"));
		assertEquals(4L, numero(fila, "grados_kb_renombrados"));
		assertEquals(5L, numero(fila, "grados_kb_marron"));
		assertEquals(5L, numero(fila, "grados_tk_con_rojo"));
		assertEquals(5L, numero(fila, "grados_tk_sembrados_con_rojo"));
		assertEquals(0L, numero(fila, "grados_tk_marron"));
		assertEquals(0L, numero(fila, "grados_kb_con_ff0000"));
		assertEquals(4L, numero(fila, "colores_kb_cambiados"));
		assertEquals(5L, numero(fila, "grados_tk_con_ff0000"));
		assertEquals(0L, numero(fila, "grados_tk_con_8b4513"));
		assertEquals(12, fila.size(), "solo recuentos: " + fila.keySet());
	}

	/**
	 * Un valor que la escuela hubiera cambiado (otro texto u otro color, o el mismo con otras mayúsculas) no se toca; los
	 * demás valores de la fila, sí.
	 */
	@Test
	@Order(6)
	void unValorDistintoDelSembrado_noSeToca_elRestoDeLaFilaSi() throws Exception {
		restaurarAntesDeV31_19();
		jdbc.update("UPDATE grado SET nombre = 'Azul-Rojo' WHERE sistema_grados_id = ? AND codigo = 'AZUL_ROJO'", kb);
		jdbc.update("UPDATE grado SET nombre = 'ROJO-NEGRO 1er PUM' WHERE sistema_grados_id = ? AND codigo = 'ROJO_NEGRO_1_PUM'",
				kb);
		jdbc.update("UPDATE grado SET color_1 = '#ff0000' WHERE sistema_grados_id = ? AND codigo = 'ROJO_NEGRO_2_PUM'", kb);
		jdbc.update("UPDATE grado SET color_1 = '#CC0000' WHERE sistema_grados_id = ? AND codigo = 'ROJO_NEGRO_3_PUM'", kb);
		ejecutarV31_19();
		assertEquals(List.of("Azul-Rojo", "#0000FF", MARRON), valores(kb, "AZUL_ROJO"));
		assertEquals(List.of("ROJO-NEGRO 1er PUM", MARRON, "#000000"), valores(kb, "ROJO_NEGRO_1_PUM"));
		assertEquals(List.of("MARRÓN-NEGRO 2º PUM", "#ff0000", "#000000"), valores(kb, "ROJO_NEGRO_2_PUM"));
		assertEquals(List.of("MARRÓN-NEGRO 3º PUM", "#CC0000", "#000000"), valores(kb, "ROJO_NEGRO_3_PUM"));
		Map<String, Object> fila = postcheck();
		assertEquals(2L, numero(fila, "grados_kb_con_rojo"), "el postcheck lo delata");
		assertEquals(2L, numero(fila, "grados_kb_renombrados"));
		assertEquals(1L, numero(fila, "grados_kb_con_ff0000"), "el postcheck lo delata");
		assertEquals(2L, numero(fila, "colores_kb_cambiados"));
		restaurarAntesDeV31_19();
		ejecutarV31_19();
		assertEquals(esperadoTrasV31_19(gradosAntes), grados());
	}

	@Test
	@Order(7)
	void repetirla_noCambiaNada() throws Exception {
		List<Map<String, Object>> antes = grados();
		ejecutarV31_19();
		ejecutarV31_19();
		assertEquals(antes, grados());
		assertEquals(esperadoTrasV31_19(gradosAntes), grados());
	}

	/** Una sola sentencia UPDATE: si falla no aplica nada, y volver a lanzarla la completa. */
	@Test
	@Order(8)
	void trasUnFallo_laCompleta() throws Exception {
		restaurarAntesDeV31_19();
		jdbc.execute("""
				CREATE TRIGGER it_v31_19_fallo BEFORE UPDATE ON grado FOR EACH ROW
				SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'fallo simulado de V31_19'""");
		assertThrows(Exception.class, this::ejecutarV31_19);
		assertEquals(gradosAntes, grados(), "nada aplicado");
		jdbc.execute("DROP TRIGGER it_v31_19_fallo");
		ejecutarV31_19();
		assertEquals(esperadoTrasV31_19(gradosAntes), grados());
	}

	/**
	 * Comparación sin relleno: con PAD SPACE (utf8mb4_bin) «AZUL-ROJO » con un espacio final sería igual a «AZUL-ROJO» y
	 * se sobrescribiría. Un valor con espacio final es una personalización: no se toca; el resto de la fila, sí. (color_1 y
	 * color_2 son VARCHAR(7): «#FF0000 » no cabe, así que no hay color con espacio final que probar.)
	 */
	@Test
	@Order(9)
	void unNombreConEspacioFinal_noSeToca_elRestoDeLaFilaSi() throws Exception {
		restaurarAntesDeV31_19();
		jdbc.update("UPDATE grado SET nombre = 'AZUL-ROJO ' WHERE sistema_grados_id = ? AND codigo = 'AZUL_ROJO'", kb);
		jdbc.update("UPDATE grado SET nombre = 'ROJO-NEGRO 3º PUM  ' WHERE sistema_grados_id = ? AND codigo = 'ROJO_NEGRO_3_PUM'",
				kb);
		ejecutarV31_19();
		assertEquals(List.of("AZUL-ROJO ", "#0000FF", MARRON), valores(kb, "AZUL_ROJO"));
		assertEquals(List.of("ROJO-NEGRO 3º PUM  ", MARRON, "#000000"), valores(kb, "ROJO_NEGRO_3_PUM"));
		assertEquals(List.of("MARRÓN-NEGRO 1º PUM", MARRON, "#000000"), valores(kb, "ROJO_NEGRO_1_PUM"));
		assertEquals(List.of("MARRÓN-NEGRO 2º PUM", MARRON, "#000000"), valores(kb, "ROJO_NEGRO_2_PUM"));
		restaurarAntesDeV31_19();
		ejecutarV31_19();
		assertEquals(esperadoTrasV31_19(gradosAntes), grados());
	}

	/**
	 * Si la disciplina KICKBOXING de la organización 1 apunta a un sistema cuya plantilla_origen no es exactamente
	 * KICKBOXING (el de taekwondo, el suyo marcado como TAEKWONDO, o «KICKBOXING » con espacio final), la migración no
	 * toca nada: la tabla grado queda íntegra.
	 */
	@Test
	@Order(10)
	void disciplinaKickboxingEnUnSistemaSinPlantillaKickboxing_noCambiaNada() throws Exception {
		try {
			restaurarAntesDeV31_19();
			jdbc.update("UPDATE disciplina SET sistema_grados_id = ? WHERE organizacion_id = 1 AND codigo = 'KICKBOXING'", tkd);
			ejecutarV31_19();
			assertEquals(gradosAntes, grados(), "disciplina KICKBOXING en el sistema de taekwondo");
			jdbc.update("UPDATE disciplina SET sistema_grados_id = ? WHERE organizacion_id = 1 AND codigo = 'KICKBOXING'", kb);

			for (String plantilla : List.of("TAEKWONDO", "KICKBOXING ")) {
				jdbc.update("UPDATE sistema_grados SET plantilla_origen = ? WHERE id = ?", plantilla, kb);
				ejecutarV31_19();
				assertEquals(gradosAntes, grados(), "plantilla_origen «" + plantilla + "»");
			}
		} finally {
			jdbc.update("UPDATE disciplina SET sistema_grados_id = ? WHERE organizacion_id = 1 AND codigo = 'KICKBOXING'", kb);
			jdbc.update("UPDATE sistema_grados SET plantilla_origen = 'KICKBOXING' WHERE id = ?", kb);
		}
		assertEquals(filasAntes.get("disciplina"), jdbc.queryForList("SELECT * FROM disciplina ORDER BY id"));
		assertEquals(filasAntes.get("sistema_grados"), jdbc.queryForList("SELECT * FROM sistema_grados ORDER BY id"));
		ejecutarV31_19();
		assertEquals(esperadoTrasV31_19(gradosAntes), grados());
	}

	/**
	 * Fallo a mitad de la sentencia: el trigger deja pasar las dos primeras filas afectadas y falla en la tercera (cuenta
	 * con una variable de sesión; cada ejecución abre una conexión nueva). InnoDB deshace la sentencia entera: nada
	 * aplicado. Sin el trigger, volver a lanzarla la completa.
	 */
	@Test
	@Order(11)
	void falloEnLaTerceraFila_noAplicaNada_yRepetirLaCompleta() throws Exception {
		restaurarAntesDeV31_19();
		try {
			jdbc.execute("""
					CREATE TRIGGER it_v31_19_fallo_a_mitad BEFORE UPDATE ON grado FOR EACH ROW
					BEGIN
					  SET @it_v31_19_filas = COALESCE(@it_v31_19_filas, 0) + 1;
					  IF @it_v31_19_filas = 3 THEN
					    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'fallo simulado en la tercera fila de V31_19';
					  END IF;
					END""");
			Exception fallo = assertThrows(Exception.class, this::ejecutarV31_19);
			assertTrue(String.valueOf(causaRaiz(fallo).getMessage()).contains("tercera fila"), fallo.toString());
			assertEquals(gradosAntes, grados(), "nada aplicado");
		} finally {
			jdbc.execute("DROP TRIGGER IF EXISTS it_v31_19_fallo_a_mitad");
		}
		ejecutarV31_19();
		assertEquals(esperadoTrasV31_19(gradosAntes), grados());
	}

	private static Throwable causaRaiz(Throwable t) {
		while (t.getCause() != null && t.getCause() != t) {
			t = t.getCause();
		}
		return t;
	}

	// ------------------------------------------------------------------ utilidades

	private List<Map<String, Object>> esperadoTrasV31_19(List<Map<String, Object>> antes) {
		List<Map<String, Object>> esperado = new ArrayList<>();
		for (Map<String, Object> fila : antes) {
			Map<String, Object> copia = new LinkedHashMap<>(fila);
			Object sistema = fila.get("sistema_grados_id");
			if (sistema != null && ((Number) sistema).longValue() == kb && RENOMBRADOS.containsKey(fila.get("codigo"))) {
				String[] v = RENOMBRADOS.get(fila.get("codigo"));
				copia.put("nombre", v[1]);
				copia.put("color_1", v[3]);
				copia.put("color_2", v[5]);
			}
			esperado.add(copia);
		}
		return esperado;
	}

	private void restaurarAntesDeV31_19() {
		for (Map.Entry<String, String[]> e : RENOMBRADOS.entrySet()) {
			String[] v = e.getValue();
			jdbc.update("UPDATE grado SET nombre = ?, color_1 = ?, color_2 = ? WHERE sistema_grados_id = ? AND codigo = ?",
					v[0], v[2], v[4], kb, e.getKey());
		}
		assertEquals(gradosAntes, grados());
	}

	private String nombre(long sistema, String codigo) {
		return jdbc.queryForObject("SELECT nombre FROM grado WHERE sistema_grados_id = ? AND codigo = ?", String.class,
				sistema, codigo);
	}

	/** nombre, color_1, color_2 («null» si es NULL). */
	private List<String> valores(long sistema, String codigo) {
		Map<String, Object> g = jdbc.queryForMap(
				"SELECT nombre, color_1, color_2 FROM grado WHERE sistema_grados_id = ? AND codigo = ?", sistema, codigo);
		return List.of(String.valueOf(g.get("nombre")), String.valueOf(g.get("color_1")), String.valueOf(g.get("color_2")));
	}

	private List<Map<String, Object>> grados() {
		return jdbc.queryForList("SELECT * FROM grado ORDER BY id");
	}

	private Map<String, Object> postcheck() throws Exception {
		List<List<Map<String, Object>>> resultados = SedesVinculosMigracionIntegrationTest.ejecutarScriptDocs(dataSource,
				"postcheck-grados-kickboxing.sql");
		assertTrue(resultados.size() == 1 && resultados.get(0).size() == 1, "una sola fila de recuentos");
		return resultados.get(0).get(0);
	}

	private static long numero(Map<String, Object> fila, String columna) {
		return ((Number) fila.get(columna)).longValue();
	}

	private void ejecutarV31_19() throws Exception {
		try (Connection conexion = dataSource.getConnection()) {
			ScriptUtils.executeSqlScript(conexion,
					new EncodedResource(new ClassPathResource(V31_19), StandardCharsets.UTF_8));
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
		return new DriverManagerDataSource("jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306) + "/"
				+ esquema + "?useSSL=false&allowPublicKeyRetrieval=true", "root", MYSQL.getPassword());
	}
}
