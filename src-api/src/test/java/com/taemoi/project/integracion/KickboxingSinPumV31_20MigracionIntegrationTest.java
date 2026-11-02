package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
 * V31_20: en kickboxing no existen los grados PUM. V23 copió de taekwondo los tres MARRÓN-NEGRO nº PUM (ROJO_NEGRO_n_PUM)
 * con su bloque de temario (b07/b08/b09), así que el material de examen de kickboxing decía «Alumnos de Negro 1º DAN a
 * Marrón-negro 1º PUM». V31_20 deja sin bloque esos tres grados del sistema de la disciplina KICKBOXING de la organización
 * 1, solo si el bloque es exactamente el sembrado y ninguna inscripción tiene ese grado. Las filas siguen (nombre, código,
 * orden, colores y aplica_a iguales); ni taekwondo, ni otros sistemas, ni otras escuelas, ni alumnos ni productos cambian.
 * Repetirla no cambia nada; tras un fallo (también a mitad de la sentencia) no aplica nada y se completa. Ejecuta
 * {@code docs/multi-escuela/postcheck-kb-sin-pum.sql}. Datos inventados.
 */
@Tag("migracion")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class KickboxingSinPumV31_20MigracionIntegrationTest extends MySqlIntegrationTest {

	private static final String ESQUEMA = "taemoi_v31_20_it";
	private static final String V31_20 = "db/migration/V31_20__kickboxing_sin_pum.sql";

	/** codigo -> bloque sembrado por V23 (copiado de taekwondo). */
	private static final Map<String, String> BLOQUE_SEMBRADO = new LinkedHashMap<>();

	static {
		BLOQUE_SEMBRADO.put("ROJO_NEGRO_1_PUM", "b07_negro_1_a_negro_2");
		BLOQUE_SEMBRADO.put("ROJO_NEGRO_2_PUM", "b08_negro_2_a_negro_3");
		BLOQUE_SEMBRADO.put("ROJO_NEGRO_3_PUM", "b09_negro_3_a_negro_4");
	}

	private static final List<String> TABLAS_SIN_CAMBIOS = List.of("sistema_grados", "disciplina", "producto", "alumno",
			"alumno_deporte", "organizacion");
	private static final String COLUMNAS_DEL_ESQUEMA = """
			SELECT TABLE_NAME, COLUMN_NAME, ORDINAL_POSITION, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
			FROM information_schema.COLUMNS
			WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME <> 'flyway_schema_history'
			ORDER BY TABLE_NAME, ORDINAL_POSITION""";

	DataSource dataSource;
	JdbcTemplate jdbc;
	long kb;
	long tkd;
	long disciplinaKb;
	long otroSistemaTaemoi;
	long otra;
	List<Map<String, Object>> gradosAntes;
	List<Map<String, Object>> columnasAntes;
	Map<String, List<Map<String, Object>>> filasAntes = new LinkedHashMap<>();

	@BeforeAll
	void migrar() {
		dataSource = recrear(ESQUEMA);
		jdbc = new JdbcTemplate(dataSource);
		flyway(dataSource, "31.19").migrate();
		kb = jdbc.queryForObject(
				"SELECT sistema_grados_id FROM disciplina WHERE organizacion_id = 1 AND codigo = 'KICKBOXING'", Long.class);
		tkd = jdbc.queryForObject(
				"SELECT sistema_grados_id FROM disciplina WHERE organizacion_id = 1 AND codigo = 'TAEKWONDO'", Long.class);
		disciplinaKb = jdbc.queryForObject("SELECT id FROM disciplina WHERE organizacion_id = 1 AND codigo = 'KICKBOXING'",
				Long.class);

		// Taemoi: otro sistema con plantilla KICKBOXING que no es el de la disciplina KICKBOXING.
		jdbc.update("INSERT INTO sistema_grados (organizacion_id, nombre, plantilla_origen) VALUES (1, 'Kick ligero', 'KICKBOXING')");
		otroSistemaTaemoi = jdbc.queryForObject(
				"SELECT MAX(id) FROM sistema_grados WHERE organizacion_id = 1 AND nombre = 'Kick ligero'", Long.class);
		jdbc.update("""
				INSERT INTO grado (organizacion_id, sistema_grados_id, codigo, nombre, orden, bloque_temario)
				VALUES (1, ?, 'ROJO_NEGRO_1_PUM', 'MARRÓN-NEGRO 1º PUM', 1, 'b07_negro_1_a_negro_2')""", otroSistemaTaemoi);

		// Otra escuela con kickboxing y los mismos códigos y bloques.
		jdbc.update("INSERT INTO organizacion (slug, nombre) VALUES ('otra-v31-20', 'Otra escuela V31_20')");
		otra = jdbc.queryForObject("SELECT id FROM organizacion WHERE slug = 'otra-v31-20'", Long.class);
		jdbc.update("INSERT INTO sistema_grados (organizacion_id, nombre, plantilla_origen) VALUES (?, 'Kick', 'KICKBOXING')",
				otra);
		long sistemaOtra = jdbc.queryForObject("SELECT id FROM sistema_grados WHERE organizacion_id = ?", Long.class, otra);
		jdbc.update("""
				INSERT INTO disciplina (organizacion_id, codigo, nombre, sistema_grados_id, edad_adulto, orden)
				VALUES (?, 'KICKBOXING', 'Kickboxing', ?, 16, 1)""", otra, sistemaOtra);
		jdbc.update("""
				INSERT INTO grado (organizacion_id, sistema_grados_id, codigo, nombre, orden, bloque_temario)
				VALUES (?, ?, 'ROJO_NEGRO_1_PUM', 'MARRÓN-NEGRO 1º PUM', 1, 'b07_negro_1_a_negro_2'),
				       (?, ?, 'ROJO_NEGRO_2_PUM', 'MARRÓN-NEGRO 2º PUM', 2, 'b08_negro_2_a_negro_3')""",
				otra, sistemaOtra, otra, sistemaOtra);

		for (Map.Entry<String, String> e : BLOQUE_SEMBRADO.entrySet()) {
			assertEquals(e.getValue(), bloque(kb, e.getKey()), "semilla de V23 en kickboxing: " + e.getKey());
			assertEquals(e.getValue(), bloque(tkd, e.getKey()), "semilla de V23 en taekwondo: " + e.getKey());
		}
		gradosAntes = grados();
		columnasAntes = jdbc.queryForList(COLUMNAS_DEL_ESQUEMA);
		for (String tabla : TABLAS_SIN_CAMBIOS) {
			filasAntes.put(tabla, jdbc.queryForList("SELECT * FROM " + tabla + " ORDER BY id"));
		}
		// Hasta V31_20: las migraciones posteriores tendrán su propio test.
		flyway(dataSource, "31.20").migrate();
	}

	@Test
	@Order(1)
	void llegaAV31_20() {
		assertEquals("31.20", jdbc.queryForObject(
				"SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1",
				String.class));
	}

	@Test
	@Order(2)
	void kickboxingDeTaemoi_losTresPumSinBloque_ylosDanLoConservan() {
		for (String codigo : BLOQUE_SEMBRADO.keySet()) {
			assertNull(bloque(kb, codigo), codigo);
		}
		assertEquals("b07_negro_1_a_negro_2", bloque(kb, "NEGRO_1_DAN"));
		assertEquals("b08_negro_2_a_negro_3", bloque(kb, "NEGRO_2_DAN"));
		assertEquals("b09_negro_3_a_negro_4", bloque(kb, "NEGRO_3_DAN"));
	}

	/** Del resto de la tabla grado nada cambia; de esas tres filas, solo bloque_temario (nombre, orden, colores… igual). */
	@Test
	@Order(3)
	void soloCambiaElBloqueDeEsasTresFilas() {
		assertEquals(esperadoTrasV31_20(gradosAntes, List.of()), grados());
	}

	@Test
	@Order(4)
	void taekwondo_alumnos_productos_otrosSistemasYOtrasEscuelas_sinCambios() {
		for (Map.Entry<String, String> e : BLOQUE_SEMBRADO.entrySet()) {
			assertEquals(e.getValue(), bloque(tkd, e.getKey()), "taekwondo: " + e.getKey());
		}
		assertEquals("b07_negro_1_a_negro_2", bloque(otroSistemaTaemoi, "ROJO_NEGRO_1_PUM"));
		assertEquals(List.of("b07_negro_1_a_negro_2", "b08_negro_2_a_negro_3"), jdbc.queryForList(
				"SELECT bloque_temario FROM grado WHERE organizacion_id = ? ORDER BY orden", String.class, otra));
		for (String tabla : TABLAS_SIN_CAMBIOS) {
			assertEquals(filasAntes.get(tabla), jdbc.queryForList("SELECT * FROM " + tabla + " ORDER BY id"), tabla);
		}
		assertEquals(columnasAntes, jdbc.queryForList(COLUMNAS_DEL_ESQUEMA), "el esquema no cambia");
	}

	@Test
	@Order(5)
	void postcheck_justoTrasDesplegar() throws Exception {
		Map<String, Object> fila = postcheck();
		assertEquals(1L, numero(fila, "flyway_31_20"));
		assertEquals(1L, numero(fila, "ultima_es_31_20"));
		assertEquals(3L, numero(fila, "pum_kb"));
		assertEquals(0L, numero(fila, "pum_kb_con_bloque"));
		assertEquals(0L, numero(fila, "inscripciones_kb_con_pum"));
		assertEquals(3L, numero(fila, "dan_kb_con_bloque_b07_a_b09"));
		assertEquals(3L, numero(fila, "pum_tk_con_bloque_sembrado"));
		assertEquals(7, fila.size(), "solo recuentos: " + fila.keySet());
	}

	/**
	 * Un PUM de kickboxing con alguien inscrito en él (activo o no) conserva su bloque: ese alumno sigue viendo su material
	 * de examen. Los otros PUM, sin inscritos, sí lo pierden. El postcheck lo delata.
	 */
	@Test
	@Order(6)
	void unPumConAlumnoInscrito_conservaElBloque_losDemasNo() throws Exception {
		restaurarAntesDeV31_20();
		long alumno = alumno(3120);
		long pum2 = idGrado(kb, "ROJO_NEGRO_2_PUM");
		jdbc.update("""
				INSERT INTO alumno_deporte (activo, apto_para_examen, alumno_id, competidor, organizacion_id, disciplina_id,
				  grado_id) VALUES (0, 0, ?, 0, 1, ?, ?)""", alumno, disciplinaKb, pum2);
		try {
			ejecutarV31_20();
			assertNull(bloque(kb, "ROJO_NEGRO_1_PUM"));
			assertEquals("b08_negro_2_a_negro_3", bloque(kb, "ROJO_NEGRO_2_PUM"), "con inscripción: no se toca");
			assertNull(bloque(kb, "ROJO_NEGRO_3_PUM"));
			Map<String, Object> fila = postcheck();
			assertEquals(1L, numero(fila, "pum_kb_con_bloque"), "el postcheck lo delata");
			assertEquals(1L, numero(fila, "inscripciones_kb_con_pum"), "el postcheck lo delata");
		} finally {
			jdbc.update("DELETE FROM alumno_deporte WHERE alumno_id = ?", alumno);
			jdbc.update("DELETE FROM alumno WHERE id = ?", alumno);
		}
		restaurarAntesDeV31_20();
		ejecutarV31_20();
		assertEquals(esperadoTrasV31_20(gradosAntes, List.of()), grados());
	}

	/** Un bloque que no es exactamente el sembrado (otro texto, otras mayúsculas, un espacio final) no se toca. */
	@Test
	@Order(7)
	void unBloqueDistintoDelSembrado_noSeToca() throws Exception {
		restaurarAntesDeV31_20();
		jdbc.update("UPDATE grado SET bloque_temario = 'B07_negro_1_a_negro_2' WHERE sistema_grados_id = ? AND codigo = ?",
				kb, "ROJO_NEGRO_1_PUM");
		jdbc.update("UPDATE grado SET bloque_temario = 'b08_negro_2_a_negro_3 ' WHERE sistema_grados_id = ? AND codigo = ?",
				kb, "ROJO_NEGRO_2_PUM");
		ejecutarV31_20();
		assertEquals("B07_negro_1_a_negro_2", bloque(kb, "ROJO_NEGRO_1_PUM"));
		assertEquals("b08_negro_2_a_negro_3 ", bloque(kb, "ROJO_NEGRO_2_PUM"));
		assertNull(bloque(kb, "ROJO_NEGRO_3_PUM"));
		assertEquals(2L, numero(postcheck(), "pum_kb_con_bloque"), "el postcheck lo delata");
		restaurarAntesDeV31_20();
		ejecutarV31_20();
		assertEquals(esperadoTrasV31_20(gradosAntes, List.of()), grados());
	}

	@Test
	@Order(8)
	void repetirla_noCambiaNada() throws Exception {
		List<Map<String, Object>> antes = grados();
		ejecutarV31_20();
		ejecutarV31_20();
		assertEquals(antes, grados());
		assertEquals(esperadoTrasV31_20(gradosAntes, List.of()), grados());
	}

	/** Una sola sentencia UPDATE: si falla no aplica nada, y volver a lanzarla la completa. */
	@Test
	@Order(9)
	void trasUnFallo_laCompleta() throws Exception {
		restaurarAntesDeV31_20();
		jdbc.execute("""
				CREATE TRIGGER it_v31_20_fallo BEFORE UPDATE ON grado FOR EACH ROW
				SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'fallo simulado de V31_20'""");
		try {
			assertThrows(Exception.class, this::ejecutarV31_20);
			assertEquals(gradosAntes, grados(), "nada aplicado");
		} finally {
			jdbc.execute("DROP TRIGGER IF EXISTS it_v31_20_fallo");
		}
		ejecutarV31_20();
		assertEquals(esperadoTrasV31_20(gradosAntes, List.of()), grados());
	}

	/**
	 * Fallo a mitad de la sentencia: el trigger deja pasar la primera fila afectada y falla en la segunda (cuenta con una
	 * variable de sesión; cada ejecución abre una conexión nueva). InnoDB deshace la sentencia entera: nada aplicado.
	 */
	@Test
	@Order(10)
	void falloEnLaSegundaFila_noAplicaNada_yRepetirLaCompleta() throws Exception {
		restaurarAntesDeV31_20();
		try {
			jdbc.execute("""
					CREATE TRIGGER it_v31_20_fallo_a_mitad BEFORE UPDATE ON grado FOR EACH ROW
					BEGIN
					  SET @it_v31_20_filas = COALESCE(@it_v31_20_filas, 0) + 1;
					  IF @it_v31_20_filas = 2 THEN
					    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'fallo simulado en la segunda fila de V31_20';
					  END IF;
					END""");
			Exception fallo = assertThrows(Exception.class, this::ejecutarV31_20);
			assertTrue(String.valueOf(causaRaiz(fallo).getMessage()).contains("segunda fila"), fallo.toString());
			assertEquals(gradosAntes, grados(), "nada aplicado");
		} finally {
			jdbc.execute("DROP TRIGGER IF EXISTS it_v31_20_fallo_a_mitad");
		}
		ejecutarV31_20();
		assertEquals(esperadoTrasV31_20(gradosAntes, List.of()), grados());
	}

	/**
	 * Si la disciplina KICKBOXING de la organización 1 apunta a un sistema cuya plantilla_origen no es exactamente
	 * KICKBOXING (el de taekwondo, el suyo marcado como TAEKWONDO, o «KICKBOXING » con espacio final), nada cambia.
	 */
	@Test
	@Order(11)
	void disciplinaKickboxingEnUnSistemaSinPlantillaKickboxing_noCambiaNada() throws Exception {
		try {
			restaurarAntesDeV31_20();
			jdbc.update("UPDATE disciplina SET sistema_grados_id = ? WHERE id = ?", tkd, disciplinaKb);
			ejecutarV31_20();
			assertEquals(gradosAntes, grados(), "disciplina KICKBOXING en el sistema de taekwondo");
			jdbc.update("UPDATE disciplina SET sistema_grados_id = ? WHERE id = ?", kb, disciplinaKb);

			for (String plantilla : List.of("TAEKWONDO", "KICKBOXING ")) {
				jdbc.update("UPDATE sistema_grados SET plantilla_origen = ? WHERE id = ?", plantilla, kb);
				ejecutarV31_20();
				assertEquals(gradosAntes, grados(), "plantilla_origen «" + plantilla + "»");
			}
		} finally {
			jdbc.update("UPDATE disciplina SET sistema_grados_id = ? WHERE id = ?", kb, disciplinaKb);
			jdbc.update("UPDATE sistema_grados SET plantilla_origen = 'KICKBOXING' WHERE id = ?", kb);
		}
		assertEquals(filasAntes.get("disciplina"), jdbc.queryForList("SELECT * FROM disciplina ORDER BY id"));
		assertEquals(filasAntes.get("sistema_grados"), jdbc.queryForList("SELECT * FROM sistema_grados ORDER BY id"));
		ejecutarV31_20();
		assertEquals(esperadoTrasV31_20(gradosAntes, List.of()), grados());
	}

	private static Throwable causaRaiz(Throwable t) {
		while (t.getCause() != null && t.getCause() != t) {
			t = t.getCause();
		}
		return t;
	}

	// ------------------------------------------------------------------ utilidades

	/** Los grados de antes con bloque NULL en los PUM del kickboxing de Taemoi, salvo los códigos de {@code conservados}. */
	private List<Map<String, Object>> esperadoTrasV31_20(List<Map<String, Object>> antes, List<String> conservados) {
		List<Map<String, Object>> esperado = new ArrayList<>();
		for (Map<String, Object> fila : antes) {
			Map<String, Object> copia = new LinkedHashMap<>(fila);
			Object sistema = fila.get("sistema_grados_id");
			Object codigo = fila.get("codigo");
			if (sistema != null && ((Number) sistema).longValue() == kb && BLOQUE_SEMBRADO.containsKey(codigo)
					&& !conservados.contains(codigo)) {
				copia.put("bloque_temario", null);
			}
			esperado.add(copia);
		}
		return esperado;
	}

	private void restaurarAntesDeV31_20() {
		for (Map.Entry<String, String> e : BLOQUE_SEMBRADO.entrySet()) {
			jdbc.update("UPDATE grado SET bloque_temario = ? WHERE sistema_grados_id = ? AND codigo = ?", e.getValue(), kb,
					e.getKey());
		}
		assertEquals(gradosAntes, grados());
	}

	private long alumno(int expediente) {
		jdbc.update("""
				INSERT INTO alumno (activo, apellidos, autorizacion_web, direccion, fecha_nacimiento, nombre,
				  numero_expediente, telefono, tiene_derecho_examen, organizacion_id)
				VALUES (1, 'PRUEBA', 1, 'Calle 1', '2000-01-01', 'V31_20', ?, '600000000', 0, 1)""", expediente);
		return jdbc.queryForObject("SELECT id FROM alumno WHERE numero_expediente = ? AND organizacion_id = 1", Long.class,
				expediente);
	}

	private long idGrado(long sistema, String codigo) {
		return jdbc.queryForObject("SELECT id FROM grado WHERE sistema_grados_id = ? AND codigo = ?", Long.class, sistema,
				codigo);
	}

	private String bloque(long sistema, String codigo) {
		return jdbc.queryForObject("SELECT bloque_temario FROM grado WHERE sistema_grados_id = ? AND codigo = ?",
				String.class, sistema, codigo);
	}

	private List<Map<String, Object>> grados() {
		return jdbc.queryForList("SELECT * FROM grado ORDER BY id");
	}

	private Map<String, Object> postcheck() throws Exception {
		List<List<Map<String, Object>>> resultados = SedesVinculosMigracionIntegrationTest.ejecutarScriptDocs(dataSource,
				"postcheck-kb-sin-pum.sql");
		assertTrue(resultados.size() == 1 && resultados.get(0).size() == 1, "una sola fila de recuentos");
		return resultados.get(0).get(0);
	}

	private static long numero(Map<String, Object> fila, String columna) {
		return ((Number) fila.get(columna)).longValue();
	}

	private void ejecutarV31_20() throws Exception {
		try (Connection conexion = dataSource.getConnection()) {
			ScriptUtils.executeSqlScript(conexion,
					new EncodedResource(new ClassPathResource(V31_20), StandardCharsets.UTF_8));
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
