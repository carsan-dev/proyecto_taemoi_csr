package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
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
 * V32 (E10-3, contraer): quita el rol antiguo ({@code usuario_rol}) y el vínculo antiguo ({@code usuario.alumno_id}).
 * <ul>
 * <li>Antes de borrar, la última resincronización R1 (la misma sentencia que V31_21): las filas de
 * {@code usuario_alumno} que falten, origen MIGRACION; los pares que R1 excluye y los anonimizados siguen sin fila.</li>
 * <li>Después, la clave ajena de {@code usuario.alumno_id} (buscada por definición), la columna con su índice único y
 * la tabla {@code usuario_rol}.</li>
 * </ul>
 * Repetirla no falla ni cambia nada; tras un fallo a medias y {@code flyway repair} se completa. Datos inventados.
 */
@Tag("migracion")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ContraccionPermisosV32MigracionIntegrationTest extends MySqlIntegrationTest {

	private static final String ESQUEMA = "taemoi_v32_it";
	private static final String ESQUEMA_FALLO = "taemoi_v32_fallo_it";
	private static final String ESQUEMA_CLAVES = "taemoi_v32_claves_it";
	private static final String V32 = "db/migration/V32__contraccion_permisos.sql";

	DataSource dataSource;
	JdbcTemplate jdbc;
	long org2;

	// R1 sin fila (aparecen tras V31_21): V32 les da su fila
	long uMismoEmail;
	long aMismoEmail;
	long uGestion;
	long aGestion;
	// R1 los excluye: siguen sin fila
	long uOtroEmail;
	long aOtroEmail;
	long uSinMembresia;
	long aSinMembresia;
	long uAnonimizado;
	long aAnonimizado;
	// Ya tenía fila
	long uConFila;
	long aConFila;

	List<Map<String, Object>> vinculosAntes;
	List<Map<String, Object>> usuariosAntes;

	@BeforeAll
	void migrar() {
		dataSource = recrear(ESQUEMA);
		jdbc = new JdbcTemplate(dataSource);
		flyway(dataSource, "31.21").migrate();
		cargarDatos(jdbc);
		vinculosAntes = vinculos(jdbc);
		usuariosAntes = usuariosSinAlumnoId(jdbc);
		assertEquals(3, contar(jdbc, "SELECT COUNT(*) FROM usuario_rol"), "filas heredadas antes de V32");
		flyway(dataSource, "32").migrate();
	}

	@Test
	@Order(1)
	void llegaAV32() {
		assertEquals("32", ultimaVersion(jdbc));
	}

	/** Paso 1: los pares R1 sin fila la ganan (MIGRACION, organización del alumno, relación por edad). */
	@Test
	@Order(2)
	void paresR1SinFila_ganaFilaMigracion() {
		assertEquals(Map.of("organizacion_id", 1L, "relacion", "PROPIO", "origen", "MIGRACION"),
				fila(uMismoEmail, aMismoEmail));
		assertEquals(Map.of("organizacion_id", 1L, "relacion", "TUTOR", "origen", "MIGRACION"), fila(uGestion, aGestion));
	}

	/** Paso 1: los pares que R1 excluye y los alumnos anonimizados siguen sin fila (se pierden con la columna). */
	@Test
	@Order(3)
	void paresFueraDeR1_yAnonimizados_sinFila() {
		for (long alumno : List.of(aOtroEmail, aSinMembresia, aAnonimizado)) {
			assertEquals(0, contar(jdbc, "SELECT COUNT(*) FROM usuario_alumno WHERE alumno_id = ?", alumno),
					"alumno " + alumno);
		}
	}

	/** La fila que ya existía no cambia; solo se añaden las dos que faltaban; el resto de usuario sigue igual. */
	@Test
	@Order(4)
	void filaExistente_noCambia_ysoloSeAnadenLasDosNuevas() {
		List<Map<String, Object>> despues = vinculos(jdbc);
		assertEquals(vinculosAntes.size() + 2, despues.size(), "solo las dos filas R1 que faltaban");
		assertTrue(despues.containsAll(vinculosAntes), "las de antes siguen igual");
		assertEquals(Map.of("organizacion_id", 1L, "relacion", "TUTOR", "origen", "MANUAL"), fila(uConFila, aConFila));
		assertEquals(usuariosAntes, usuariosSinAlumnoId(jdbc), "usuario no cambia salvo la columna quitada");
	}

	/** Pasos 2 a 4: ni la clave ajena, ni el índice único, ni la columna, ni la tabla usuario_rol. */
	@Test
	@Order(5)
	void sinColumna_sinClave_sinIndice_sinUsuarioRol() {
		assertContraido(jdbc);
	}

	/** El postcheck de la E10 da 0 en sus tres primeras columnas. */
	@Test
	@Order(6)
	void postcheckE10_limpio() throws Exception {
		List<List<Map<String, Object>>> resultados = SedesVinculosMigracionIntegrationTest.ejecutarScriptDocs(dataSource,
				"postcheck-e10.sql");
		Map<String, Object> q1 = resultados.get(0).get(0);
		for (String columna : List.of("tabla_usuario_rol", "columna_alumno_id", "vinculos_de_otra_escuela")) {
			assertEquals(0L, ((Number) q1.get(columna)).longValue(), columna);
		}
	}

	/** Repetirla (dos veces a mano y otra por Flyway tras borrar su fila del historial) no falla ni cambia nada. */
	@Test
	@Order(7)
	void repetirla_noFallaNiCambiaNada() throws Exception {
		Map<String, List<Map<String, Object>>> antes = estado(jdbc);
		ejecutarV32(dataSource);
		ejecutarV32(dataSource);
		assertEquals(antes, estado(jdbc));

		jdbc.update("DELETE FROM flyway_schema_history WHERE version = '32'");
		flyway(dataSource, "32").migrate();
		assertEquals("32", ultimaVersion(jdbc));
		assertEquals(antes, estado(jdbc));
	}

	/**
	 * Fallo a medias en el paso 1 (antes de cualquier DDL): Flyway la marca fallida y no quita nada; tras
	 * {@code repair}, {@code migrate} la completa con el mismo resultado.
	 */
	@Test
	@Order(8)
	void falloEnElPaso1_repairYMigrate_terminaBien() {
		DataSource fuente = recrear(ESQUEMA_FALLO);
		JdbcTemplate f = new JdbcTemplate(fuente);
		flyway(fuente, "31.21").migrate();
		Datos datos = cargarDatosEn(f);
		f.execute("""
				CREATE TRIGGER it_v32_fallo BEFORE INSERT ON usuario_alumno FOR EACH ROW
				SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'fallo simulado de V32'""");
		FlywayException error = assertThrows(FlywayException.class, () -> flyway(fuente, "32").migrate());
		assertTrue(mensajeCompleto(error).contains("fallo simulado de V32"), mensajeCompleto(error));
		assertEquals(1, contar(f, "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()"
				+ " AND TABLE_NAME = 'usuario' AND COLUMN_NAME = 'alumno_id'"), "la columna sigue");
		assertEquals(1, contar(f, "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()"
				+ " AND TABLE_NAME = 'usuario_rol'"), "usuario_rol sigue");
		f.execute("DROP TRIGGER it_v32_fallo");

		Flyway segundo = flyway(fuente, "32");
		segundo.repair();
		segundo.migrate();
		assertEquals("32", ultimaVersion(f));
		assertContraido(f);
		assertEquals(Map.of("organizacion_id", 1L, "relacion", "PROPIO", "origen", "MIGRACION"),
				fila(f, datos.uMismoEmail, datos.aMismoEmail));
		assertEquals(Map.of("organizacion_id", 1L, "relacion", "TUTOR", "origen", "MIGRACION"),
				fila(f, datos.uGestion, datos.aGestion));
	}

	/**
	 * Estados intermedios reconstruidos a mano (tras un fallo entre DDL): sin clave pero con columna; con dos claves
	 * antiguas; sin la columna pero con usuario_rol. La repetición termina en todos.
	 */
	@Test
	@Order(9)
	void estadosIntermedios_laRepeticionLaCompleta() throws Exception {
		DataSource fuente = recrear(ESQUEMA_CLAVES);
		JdbcTemplate k = new JdbcTemplate(fuente);
		flyway(fuente, "31.21").migrate();

		// Dos claves (la de V31_21 y otra antigua puesta por Hibernate): caen las dos.
		k.execute("ALTER TABLE usuario ADD CONSTRAINT FKotra_hibernate_alumno FOREIGN KEY (alumno_id) REFERENCES alumno(id)");
		assertEquals(2, clavesDeAlumnoId(k));
		ejecutarV32(fuente);
		assertContraido(k);

		// Sin clave y con la columna (fallo tras el paso 2).
		k.execute("CREATE TABLE usuario_rol (usuario_id BIGINT NOT NULL, roles VARCHAR(20) NULL)");
		k.execute("ALTER TABLE usuario ADD COLUMN alumno_id BIGINT NULL, ADD UNIQUE KEY uk_it_alumno_id (alumno_id)");
		ejecutarV32(fuente);
		assertContraido(k);

		// Sin la columna y con usuario_rol (fallo tras el paso 3).
		k.execute("CREATE TABLE usuario_rol (usuario_id BIGINT NOT NULL, roles VARCHAR(20) NULL)");
		ejecutarV32(fuente);
		assertContraido(k);
	}

	// ------------------------------------------------------------------ datos

	private void cargarDatos(JdbcTemplate j) {
		Datos d = cargarDatosEn(j);
		org2 = d.org2;
		uMismoEmail = d.uMismoEmail;
		aMismoEmail = d.aMismoEmail;
		uGestion = d.uGestion;
		aGestion = d.aGestion;
		uOtroEmail = d.uOtroEmail;
		aOtroEmail = d.aOtroEmail;
		uSinMembresia = d.uSinMembresia;
		aSinMembresia = d.aSinMembresia;
		uAnonimizado = d.uAnonimizado;
		aAnonimizado = d.aAnonimizado;
		uConFila = d.uConFila;
		aConFila = d.aConFila;
	}

	private static final class Datos {
		long org2;
		long uMismoEmail;
		long aMismoEmail;
		long uGestion;
		long aGestion;
		long uOtroEmail;
		long aOtroEmail;
		long uSinMembresia;
		long aSinMembresia;
		long uAnonimizado;
		long aAnonimizado;
		long uConFila;
		long aConFila;
	}

	/** Esquema en 31.21: pares que aparecen después de V31_21 (entre el precheck y el despliegue de V32). */
	private static Datos cargarDatosEn(JdbcTemplate j) {
		Datos d = new Datos();
		j.update("INSERT INTO organizacion (slug, nombre) VALUES ('otra-v32', 'Otra escuela V32')");
		d.org2 = j.queryForObject("SELECT id FROM organizacion WHERE slug = 'otra-v32'", Long.class);

		// R1 por email (otras mayúsculas y espacios en la cuenta), mayor de edad: PROPIO.
		d.aMismoEmail = alumno(j, 32201, 1, "ana.v32@example.test", "2000-01-01");
		d.uMismoEmail = usuario(j, "  Ana.V32@Example.test ", d.aMismoEmail);
		membresia(j, d.uMismoEmail, 1, "FAMILIA", true);
		// R1 por gestión activa en la escuela del alumno, otro email, menor: TUTOR.
		d.aGestion = alumno(j, 32202, 1, "hijo.v32@example.test", "2015-06-01");
		d.uGestion = usuario(j, "gestor.v32@example.test", d.aGestion);
		membresia(j, d.uGestion, 1, "ADMINISTRADOR", true);

		// Excluidos por R1: solo familia con otro email; mismo email sin membresía en la escuela; anonimizado.
		d.aOtroEmail = alumno(j, 32203, 1, "otro.v32@example.test", "2000-01-01");
		d.uOtroEmail = usuario(j, "cuenta.otro.v32@example.test", d.aOtroEmail);
		membresia(j, d.uOtroEmail, 1, "FAMILIA", true);
		d.aSinMembresia = alumno(j, 32204, 1, "sin.v32@example.test", "2000-01-01");
		d.uSinMembresia = usuario(j, "sin.v32@example.test", d.aSinMembresia);
		membresia(j, d.uSinMembresia, d.org2, "FAMILIA", true);
		d.aAnonimizado = alumno(j, 32205, 1, "anon.v32@example.test", "2000-01-01");
		d.uAnonimizado = usuario(j, "anon.v32@example.test", d.aAnonimizado);
		membresia(j, d.uAnonimizado, 1, "FAMILIA", true);
		j.update("UPDATE alumno SET anonimizado_en = '2026-10-20 10:00:00' WHERE id = ?", d.aAnonimizado);

		// Ya tenía fila (MANUAL, TUTOR aunque es mayor): no cambia.
		d.aConFila = alumno(j, 32206, 1, "confila.v32@example.test", "2000-01-01");
		d.uConFila = usuario(j, "confila.v32@example.test", d.aConFila);
		membresia(j, d.uConFila, 1, "FAMILIA", true);
		j.update("""
				INSERT INTO usuario_alumno (usuario_id, alumno_id, organizacion_id, relacion, origen, creado_en)
				VALUES (?, ?, 1, 'TUTOR', 'MANUAL', '2026-10-01 09:00:00')""", d.uConFila, d.aConFila);

		// Roles heredados (sin clave primaria: puede haber repetidas y con rol NULL).
		j.update("INSERT INTO usuario_rol (usuario_id, roles) VALUES (?, 'ROLE_USER')", d.uMismoEmail);
		j.update("INSERT INTO usuario_rol (usuario_id, roles) VALUES (?, 'ROLE_USER')", d.uMismoEmail);
		j.update("INSERT INTO usuario_rol (usuario_id, roles) VALUES (?, NULL)", d.uGestion);
		return d;
	}

	// ------------------------------------------------------------------ utilidades

	private static void assertContraido(JdbcTemplate j) {
		assertEquals(0, contar(j, """
				SELECT COUNT(*) FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'usuario' AND COLUMN_NAME = 'alumno_id'"""),
				"sin la columna usuario.alumno_id");
		assertEquals(0, clavesDeAlumnoId(j), "sin la clave ajena hacia alumno");
		assertEquals(0, contar(j, """
				SELECT COUNT(*) FROM information_schema.STATISTICS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'usuario' AND COLUMN_NAME = 'alumno_id'"""),
				"sin el índice único");
		assertEquals(0, contar(j, """
				SELECT COUNT(*) FROM information_schema.TABLES
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'usuario_rol'"""), "sin la tabla usuario_rol");
	}

	private static int clavesDeAlumnoId(JdbcTemplate j) {
		return contar(j, """
				SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'usuario' AND REFERENCED_TABLE_NAME = 'alumno'""");
	}

	/** Filas y esquema que V32 toca, para comparar antes y después de repetirla. */
	private static Map<String, List<Map<String, Object>>> estado(JdbcTemplate j) {
		Map<String, List<Map<String, Object>>> estado = new LinkedHashMap<>();
		estado.put("usuario_alumno", vinculos(j));
		estado.put("usuario", j.queryForList("SELECT * FROM usuario ORDER BY id"));
		estado.put("columnas", j.queryForList("""
				SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, IS_NULLABLE FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE() ORDER BY TABLE_NAME, ORDINAL_POSITION"""));
		estado.put("restricciones", j.queryForList("""
				SELECT TABLE_NAME, CONSTRAINT_NAME, CONSTRAINT_TYPE FROM information_schema.TABLE_CONSTRAINTS
				WHERE CONSTRAINT_SCHEMA = DATABASE() ORDER BY TABLE_NAME, CONSTRAINT_NAME"""));
		return estado;
	}

	private Map<String, Object> fila(long usuario, long alumno) {
		return fila(jdbc, usuario, alumno);
	}

	private static Map<String, Object> fila(JdbcTemplate j, long usuario, long alumno) {
		List<Map<String, Object>> filas = j.queryForList("""
				SELECT organizacion_id, relacion, origen FROM usuario_alumno WHERE usuario_id = ? AND alumno_id = ?""",
				usuario, alumno);
		assertEquals(1, filas.size(), "usuario " + usuario + " / alumno " + alumno);
		Map<String, Object> f = filas.get(0);
		return Map.of("organizacion_id", ((Number) f.get("organizacion_id")).longValue(), "relacion", f.get("relacion"),
				"origen", f.get("origen"));
	}

	private static List<Map<String, Object>> vinculos(JdbcTemplate j) {
		return j.queryForList("SELECT * FROM usuario_alumno ORDER BY usuario_id, alumno_id");
	}

	private static List<Map<String, Object>> usuariosSinAlumnoId(JdbcTemplate j) {
		return j.queryForList("SELECT * FROM usuario ORDER BY id").stream().map(f -> {
			Map<String, Object> copia = new LinkedHashMap<>(f);
			copia.remove("alumno_id");
			return copia;
		}).toList();
	}

	private static long alumno(JdbcTemplate j, int expediente, long organizacion, String email, String nacimiento) {
		j.update("""
				INSERT INTO alumno (activo, apellidos, autorizacion_web, direccion, fecha_nacimiento, nombre,
				  numero_expediente, telefono, tiene_derecho_examen, organizacion_id, email)
				VALUES (1, 'PRUEBA', 1, 'Calle 1', ?, 'V32', ?, '600000000', 0, ?, ?)""", nacimiento, expediente,
				organizacion, email);
		return j.queryForObject("SELECT id FROM alumno WHERE numero_expediente = ? AND organizacion_id = ?", Long.class,
				expediente, organizacion);
	}

	private static long usuario(JdbcTemplate j, String email, long alumno) {
		j.update("INSERT INTO usuario (email, nombre, apellidos, contrasena, alumno_id) VALUES (?, 'Usuario', 'Prueba', 'x', ?)",
				email, alumno);
		return j.queryForObject("SELECT id FROM usuario WHERE email = ?", Long.class, email);
	}

	private static void membresia(JdbcTemplate j, long usuario, long organizacion, String rol, boolean activa) {
		j.update("INSERT INTO membresia (usuario_id, organizacion_id, rol, activa) VALUES (?, ?, ?, ?)", usuario,
				organizacion, rol, activa);
	}

	private static int contar(JdbcTemplate j, String sql, Object... args) {
		return j.queryForObject(sql, Integer.class, args);
	}

	private static String ultimaVersion(JdbcTemplate j) {
		return j.queryForObject(
				"SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1",
				String.class);
	}

	private static String mensajeCompleto(Throwable error) {
		StringBuilder sb = new StringBuilder();
		for (Throwable t = error; t != null; t = t.getCause()) {
			sb.append(t.getMessage()).append(" | ");
		}
		return sb.toString();
	}

	private static void ejecutarV32(DataSource fuente) throws Exception {
		try (Connection conexion = fuente.getConnection()) {
			ScriptUtils.executeSqlScript(conexion, new EncodedResource(new ClassPathResource(V32), StandardCharsets.UTF_8));
		}
	}

	private static Flyway flyway(DataSource fuente, String objetivo) {
		return Flyway.configure().dataSource(fuente).locations("classpath:db/migration")
				.placeholders(Map.of(ContraccionCatalogoMigracionIntegrationTest.PERMITIR_CON_V27, "true"))
				.target(objetivo).load();
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
