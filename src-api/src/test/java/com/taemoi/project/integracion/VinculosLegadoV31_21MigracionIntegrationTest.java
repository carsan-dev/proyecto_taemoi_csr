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
 * V31_21 (E10-1, ampliar): pone al día el vínculo antiguo {@code usuario.alumno_id} antes de que el código deje de
 * leerlo (E10-2) y de que V32 quite la columna (E10-3).
 * <ul>
 * <li>Inserta en {@code usuario_alumno} las filas R1 que falten, con la misma regla que V31 (email del alumno igual al
 * de la cuenta con membresía en su escuela, o membresía de gestión activa allí), sin alumnos anonimizados, origen
 * MIGRACION y relación por edad. No duplica ni toca las filas que ya existen.</li>
 * <li>La clave ajena de {@code usuario.alumno_id} pasa a {@code ON DELETE SET NULL}, una sola y con nombre fijo
 * {@code fk_usuario_alumno_legado}, buscada por definición (el nombre de V1 lo puso Hibernate).</li>
 * </ul>
 * Repetirla no cambia nada; tras un fallo a medias y {@code flyway repair} se completa. Datos inventados.
 */
@Tag("migracion")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class VinculosLegadoV31_21MigracionIntegrationTest extends MySqlIntegrationTest {

	private static final String ESQUEMA = "taemoi_v31_21_it";
	private static final String V31_21 = "db/migration/V31_21__vinculos_legado_al_dia.sql";
	private static final String FK_V1 = "FKs2jdpk0wmqgyj2i2jer600y8b";
	private static final String FK_NUEVA = "fk_usuario_alumno_legado";

	private static final String CLAVES_DE_ALUMNO_ID = """
			SELECT rc.CONSTRAINT_NAME AS nombre, rc.DELETE_RULE AS al_borrar
			FROM information_schema.REFERENTIAL_CONSTRAINTS rc
			JOIN information_schema.KEY_COLUMN_USAGE k
			  ON k.CONSTRAINT_SCHEMA = rc.CONSTRAINT_SCHEMA AND k.CONSTRAINT_NAME = rc.CONSTRAINT_NAME
			 AND k.TABLE_NAME = rc.TABLE_NAME
			WHERE rc.CONSTRAINT_SCHEMA = DATABASE() AND rc.TABLE_NAME = 'usuario'
			  AND k.COLUMN_NAME = 'alumno_id' AND k.REFERENCED_TABLE_NAME = 'alumno'
			ORDER BY rc.CONSTRAINT_NAME""";

	DataSource dataSource;
	JdbcTemplate jdbc;
	long org2;

	// (1) cumplen R1 sin fila
	long uMismoEmail;
	long aMismoEmail;
	long uGestion;
	long aGestion;
	// (2) no cumplen R1
	long uOtroEmail;
	long aOtroEmail;
	long uSinMembresia;
	long aSinMembresia;
	long uGestionInactiva;
	long aGestionInactiva;
	long uGestionOtraEscuela;
	long aGestionOtraEscuela;
	// (3) anonimizados
	long uAnonimizadoEmail;
	long aAnonimizadoEmail;
	long uAnonimizadoGestion;
	long aAnonimizadoGestion;
	// (4) ya tenían fila
	long uConFila;
	long aConFila;

	List<Map<String, Object>> vinculosAntes;
	List<Map<String, Object>> usuariosAntes;

	@BeforeAll
	void migrar() {
		dataSource = recrear(ESQUEMA);
		jdbc = new JdbcTemplate(dataSource);
		flyway(dataSource, "31.20").migrate();
		assertEquals(List.of(Map.of("nombre", FK_V1, "al_borrar", "NO ACTION")), claves(), "la FK de V1, sin regla");

		jdbc.update("INSERT INTO organizacion (slug, nombre) VALUES ('otra-v31-21', 'Otra escuela V31_21')");
		org2 = jdbc.queryForObject("SELECT id FROM organizacion WHERE slug = 'otra-v31-21'", Long.class);

		// (1) Familia con el mismo email (otras mayúsculas y espacios en la cuenta): fila PROPIO (mayor de edad).
		aMismoEmail = alumno(32101, 1, "ana.v3121@example.test", "2000-01-01");
		uMismoEmail = usuario("  Ana.V3121@Example.test ", aMismoEmail);
		membresia(uMismoEmail, 1, "FAMILIA", true);
		// (1) Gestión activa en la escuela del alumno, con otro email: fila TUTOR (menor).
		aGestion = alumno(32102, 1, "hijo.v3121@example.test", "2015-06-01");
		uGestion = usuario("gestor.v3121@example.test", aGestion);
		membresia(uGestion, 1, "ADMINISTRADOR", true);

		// (2) Solo familia y otro email: sin fila.
		aOtroEmail = alumno(32103, 1, "otro.v3121@example.test", "2000-01-01");
		uOtroEmail = usuario("cuenta.otro.v3121@example.test", aOtroEmail);
		membresia(uOtroEmail, 1, "FAMILIA", true);
		// (2) Mismo email pero sin membresía en la escuela del alumno: sin fila.
		aSinMembresia = alumno(32104, 1, "sin.v3121@example.test", "2000-01-01");
		uSinMembresia = usuario("sin.v3121@example.test", aSinMembresia);
		membresia(uSinMembresia, org2, "FAMILIA", true);
		// (2) Gestor degradado (membresía de gestión inactiva) con otro email: sin fila.
		aGestionInactiva = alumno(32105, 1, "inactiva.v3121@example.test", "2000-01-01");
		uGestionInactiva = usuario("degradado.v3121@example.test", aGestionInactiva);
		membresia(uGestionInactiva, 1, "ADMINISTRADOR", false);
		membresia(uGestionInactiva, 1, "FAMILIA", true);
		// (2) Gestión en otra escuela: sin fila.
		aGestionOtraEscuela = alumno(32106, 1, "ajeno.v3121@example.test", "2000-01-01");
		uGestionOtraEscuela = usuario("gestor.otra.v3121@example.test", aGestionOtraEscuela);
		membresia(uGestionOtraEscuela, org2, "PROPIETARIO", true);

		// (3) Anonimizados: ni por email ni por gestión.
		aAnonimizadoEmail = alumno(32107, 1, "anon.v3121@example.test", "2000-01-01");
		uAnonimizadoEmail = usuario("anon.v3121@example.test", aAnonimizadoEmail);
		membresia(uAnonimizadoEmail, 1, "FAMILIA", true);
		aAnonimizadoGestion = alumno(32108, 1, "anon.hijo.v3121@example.test", "2015-06-01");
		uAnonimizadoGestion = usuario("anon.gestor.v3121@example.test", aAnonimizadoGestion);
		membresia(uAnonimizadoGestion, 1, "PROFESOR", true);
		jdbc.update("UPDATE alumno SET anonimizado_en = '2026-10-20 10:00:00' WHERE id IN (?, ?)", aAnonimizadoEmail,
				aAnonimizadoGestion);

		// (4) Ya tenía fila (MANUAL, TUTOR aunque es mayor): no se duplica ni cambia.
		aConFila = alumno(32109, 1, "confila.v3121@example.test", "2000-01-01");
		uConFila = usuario("confila.v3121@example.test", aConFila);
		membresia(uConFila, 1, "FAMILIA", true);
		jdbc.update("""
				INSERT INTO usuario_alumno (usuario_id, alumno_id, organizacion_id, relacion, origen, creado_en)
				VALUES (?, ?, 1, 'TUTOR', 'MANUAL', '2026-10-01 09:00:00')""", uConFila, aConFila);

		vinculosAntes = vinculos();
		usuariosAntes = jdbc.queryForList("SELECT * FROM usuario ORDER BY id");
		flyway(dataSource, "31.21").migrate();
	}

	@Test
	@Order(1)
	void llegaAV31_21() {
		assertEquals("31.21", jdbc.queryForObject(
				"SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1",
				String.class));
	}

	/** (1) Los pares que cumplen R1 y no tenían fila la tienen: MIGRACION, organización del alumno, relación por edad. */
	@Test
	@Order(2)
	void paresR1SinFila_ganaFilaMigracion() {
		assertEquals(Map.of("organizacion_id", 1L, "relacion", "PROPIO", "origen", "MIGRACION"),
				fila(uMismoEmail, aMismoEmail));
		assertEquals(Map.of("organizacion_id", 1L, "relacion", "TUTOR", "origen", "MIGRACION"), fila(uGestion, aGestion));
	}

	/** (2) y (3) Los pares que R1 excluye y los alumnos anonimizados siguen sin fila. */
	@Test
	@Order(3)
	void paresFueraDeR1_yAnonimizados_sinFila() {
		for (long[] par : List.of(new long[] { uOtroEmail, aOtroEmail }, new long[] { uSinMembresia, aSinMembresia },
				new long[] { uGestionInactiva, aGestionInactiva }, new long[] { uGestionOtraEscuela, aGestionOtraEscuela },
				new long[] { uAnonimizadoEmail, aAnonimizadoEmail },
				new long[] { uAnonimizadoGestion, aAnonimizadoGestion })) {
			assertEquals(0, contar("SELECT COUNT(*) FROM usuario_alumno WHERE alumno_id = ?", par[1]),
					"usuario " + par[0] + " / alumno " + par[1]);
		}
	}

	/** (4) La fila que ya existía no se duplica ni cambia; nada más se añade ni se quita. */
	@Test
	@Order(4)
	void filaExistente_noSeDuplica_niCambia_ysoloSeAnadenLasDosNuevas() {
		assertEquals(1, contar("SELECT COUNT(*) FROM usuario_alumno WHERE alumno_id = ?", aConFila));
		Map<String, Object> existente = jdbc.queryForMap(
				"SELECT relacion, origen, CAST(creado_en AS CHAR) AS creado_en FROM usuario_alumno WHERE usuario_id = ? AND alumno_id = ?",
				uConFila, aConFila);
		assertEquals(Map.of("relacion", "TUTOR", "origen", "MANUAL", "creado_en", "2026-10-01 09:00:00"), existente);
		List<Map<String, Object>> despues = vinculos();
		assertEquals(vinculosAntes.size() + 2, despues.size(), "solo las dos filas R1 que faltaban");
		assertTrue(despues.containsAll(vinculosAntes), "las de antes siguen igual");
		assertEquals(usuariosAntes, jdbc.queryForList("SELECT * FROM usuario ORDER BY id"), "usuario no cambia");
	}

	/** (5) Una sola clave ajena en usuario.alumno_id, con nombre fijo y ON DELETE SET NULL. */
	@Test
	@Order(5)
	void laClaveDeAlumnoIdQuedaUnaSola_enSetNull() {
		assertEquals(List.of(Map.of("nombre", FK_NUEVA, "al_borrar", "SET NULL")), claves());
		assertEquals(1, contar("""
				SELECT COUNT(*) FROM information_schema.STATISTICS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'usuario' AND COLUMN_NAME = 'alumno_id'
				  AND NON_UNIQUE = 0"""), "el índice único de V1 sigue");
	}

	/** Borrar una ficha cuyo id está en usuario.alumno_id deja la columna a NULL y no falla; la cuenta sigue. */
	@Test
	@Order(6)
	void borrarLaFicha_dejaAlumnoIdANull() {
		long alumno = alumno(32110, 1, "borrar.v3121@example.test", "2000-01-01");
		long usuario = usuario("borrar.v3121@example.test", alumno);
		jdbc.update("DELETE FROM alumno WHERE id = ?", alumno);
		assertNull(jdbc.queryForObject("SELECT alumno_id FROM usuario WHERE id = ?", Long.class, usuario));
		jdbc.update("DELETE FROM usuario WHERE id = ?", usuario);
	}

	/** El precheck de la E10 da sin_fila_r1 = 0 y una sola clave en SET NULL para usuario.alumno_id. */
	@Test
	@Order(7)
	void precheckE10_alDia() throws Exception {
		List<List<Map<String, Object>>> resultados = SedesVinculosMigracionIntegrationTest.ejecutarScriptDocs(dataSource,
				"precheck-e10.sql");
		Map<String, Object> consulta3 = resultados.stream().filter(r -> r.size() == 1 && r.get(0).containsKey("sin_fila_r1"))
				.findFirst().orElseThrow().get(0);
		assertEquals(0L, numero(consulta3, "sin_fila_r1"));
		assertEquals(2L, numero(consulta3, "sin_fila_anonimizado"));
		List<Map<String, Object>> consulta8 = resultados.stream()
				.filter(r -> !r.isEmpty() && r.get(0).containsKey("al_borrar")).findFirst().orElseThrow();
		List<Map<String, Object>> alumnoId = consulta8.stream().filter(f -> "usuario".equals(f.get("tabla"))).toList();
		assertEquals(1, alumnoId.size(), alumnoId.toString());
		assertEquals("SET NULL", alumnoId.get(0).get("al_borrar"));
		assertEquals(1L, numero(alumnoId.get(0), "claves"));
	}

	/** (6) Repetirla (dos veces más) no falla ni cambia nada. */
	@Test
	@Order(8)
	void repetirla_noCambiaNada() throws Exception {
		List<Map<String, Object>> antes = vinculos();
		ejecutarV31_21();
		ejecutarV31_21();
		assertEquals(antes, vinculos());
		assertEquals(List.of(Map.of("nombre", FK_NUEVA, "al_borrar", "SET NULL")), claves());
	}

	/** Tras un fallo antes del cambio de clave (filas ya insertadas, FK de V1 intacta): la repetición la completa. */
	@Test
	@Order(9)
	void conLaClaveDeV1_laCambia() throws Exception {
		jdbc.execute("ALTER TABLE usuario DROP FOREIGN KEY " + FK_NUEVA + ", ADD CONSTRAINT " + FK_V1
				+ " FOREIGN KEY (alumno_id) REFERENCES alumno(id)");
		assertEquals(List.of(Map.of("nombre", FK_V1, "al_borrar", "NO ACTION")), claves());
		List<Map<String, Object>> antes = vinculos();
		ejecutarV31_21();
		assertEquals(List.of(Map.of("nombre", FK_NUEVA, "al_borrar", "SET NULL")), claves());
		assertEquals(antes, vinculos());
	}

	/** Si hubiera dos claves antiguas (otro nombre puesto por Hibernate), caen las dos y queda una sola. */
	@Test
	@Order(10)
	void conDosClavesAntiguas_quedaUnaSola() throws Exception {
		jdbc.execute("ALTER TABLE usuario DROP FOREIGN KEY " + FK_NUEVA + ", ADD CONSTRAINT " + FK_V1
				+ " FOREIGN KEY (alumno_id) REFERENCES alumno(id), ADD CONSTRAINT FKotra_hibernate_alumno"
				+ " FOREIGN KEY (alumno_id) REFERENCES alumno(id) ON DELETE RESTRICT");
		assertEquals(2, claves().size());
		ejecutarV31_21();
		assertEquals(List.of(Map.of("nombre", FK_NUEVA, "al_borrar", "SET NULL")), claves());
	}

	/** Sin ninguna clave (quitada a mano): la crea. */
	@Test
	@Order(11)
	void sinClave_laCrea() throws Exception {
		jdbc.execute("ALTER TABLE usuario DROP FOREIGN KEY " + FK_NUEVA);
		assertEquals(List.of(), claves());
		ejecutarV31_21();
		assertEquals(List.of(Map.of("nombre", FK_NUEVA, "al_borrar", "SET NULL")), claves());
	}

	/** Con la nueva ya en SET NULL y una antigua al lado: solo cae la antigua. */
	@Test
	@Order(12)
	void conLaNuevaYUnaAntigua_soloCaeLaAntigua() throws Exception {
		jdbc.execute("ALTER TABLE usuario ADD CONSTRAINT " + FK_V1 + " FOREIGN KEY (alumno_id) REFERENCES alumno(id)");
		assertEquals(2, claves().size());
		ejecutarV31_21();
		assertEquals(List.of(Map.of("nombre", FK_NUEVA, "al_borrar", "SET NULL")), claves());
	}

	/** Un par R1 que aparece después (p. ej. entre el precheck y el despliegue): la repetición le da su fila. */
	@Test
	@Order(13)
	void unParR1Nuevo_laRepeticionLeDaFila() throws Exception {
		jdbc.update("DELETE FROM usuario_alumno WHERE usuario_id = ? AND alumno_id = ?", uMismoEmail, aMismoEmail);
		List<Map<String, Object>> sinElla = vinculos();
		ejecutarV31_21();
		assertEquals(Map.of("organizacion_id", 1L, "relacion", "PROPIO", "origen", "MIGRACION"),
				fila(uMismoEmail, aMismoEmail));
		assertEquals(sinElla.size() + 1, vinculos().size());
	}

	// ------------------------------------------------------------------ utilidades

	private List<Map<String, Object>> claves() {
		return jdbc.queryForList(CLAVES_DE_ALUMNO_ID).stream()
				.map(f -> Map.of("nombre", f.get("nombre"), "al_borrar", f.get("al_borrar"))).toList();
	}

	private Map<String, Object> fila(long usuario, long alumno) {
		List<Map<String, Object>> filas = jdbc.queryForList("""
				SELECT organizacion_id, relacion, origen FROM usuario_alumno WHERE usuario_id = ? AND alumno_id = ?""",
				usuario, alumno);
		assertEquals(1, filas.size(), "usuario " + usuario + " / alumno " + alumno);
		Map<String, Object> f = filas.get(0);
		return Map.of("organizacion_id", ((Number) f.get("organizacion_id")).longValue(), "relacion", f.get("relacion"),
				"origen", f.get("origen"));
	}

	private List<Map<String, Object>> vinculos() {
		return jdbc.queryForList("SELECT * FROM usuario_alumno ORDER BY usuario_id, alumno_id");
	}

	private long alumno(int expediente, long organizacion, String email, String nacimiento) {
		jdbc.update("""
				INSERT INTO alumno (activo, apellidos, autorizacion_web, direccion, fecha_nacimiento, nombre,
				  numero_expediente, telefono, tiene_derecho_examen, organizacion_id, email)
				VALUES (1, 'PRUEBA', 1, 'Calle 1', ?, 'V31_21', ?, '600000000', 0, ?, ?)""", nacimiento, expediente,
				organizacion, email);
		return jdbc.queryForObject("SELECT id FROM alumno WHERE numero_expediente = ? AND organizacion_id = ?", Long.class,
				expediente, organizacion);
	}

	private long usuario(String email, long alumno) {
		jdbc.update("INSERT INTO usuario (email, nombre, apellidos, contrasena, alumno_id) VALUES (?, 'Usuario', 'Prueba', 'x', ?)",
				email, alumno);
		return jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", Long.class, email);
	}

	private void membresia(long usuario, long organizacion, String rol, boolean activa) {
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol, activa) VALUES (?, ?, ?, ?)", usuario,
				organizacion, rol, activa);
	}

	private int contar(String sql, Object... args) {
		return jdbc.queryForObject(sql, Integer.class, args);
	}

	private static long numero(Map<String, Object> fila, String columna) {
		return ((Number) fila.get(columna)).longValue();
	}

	private void ejecutarV31_21() throws Exception {
		try (Connection conexion = dataSource.getConnection()) {
			ScriptUtils.executeSqlScript(conexion,
					new EncodedResource(new ClassPathResource(V31_21), StandardCharsets.UTF_8));
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
