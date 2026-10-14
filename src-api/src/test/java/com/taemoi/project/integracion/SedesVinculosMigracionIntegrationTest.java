package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
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
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapperResultSetExtractor;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * V31 (sedes y vínculos) sobre una base en V30 con datos sintéticos de tres escuelas (Taemoi, una segunda con
 * grupos y una tercera sin grupos): sedes («Umbrete» para Taemoi, «Sede principal» para el resto),
 * {@code grupo.sede_id} y {@code alumno.sede_principal_id}, tablas nuevas, {@code usuario.es_admin_plataforma}
 * sin nadie marcado, el relleno de {@code usuario_alumno} regla a regla (R1–R4, fase3-sedes-permisos.md §5),
 * la relación por edad (D7), la FK de {@code usuario_rol} con borrado en cascada, la repetición del script y
 * la reanudación tras fallos a medias. Ejecuta también {@code docs/multi-escuela/precheck-v31.sql} (antes y
 * después) y {@code postcheck-v31.sql}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SedesVinculosMigracionIntegrationTest extends MySqlIntegrationTest {

	private static final String ESQUEMA = "taemoi_v31_it";
	private static final String ESQUEMA_FALLO = "taemoi_v31_fallo_it";
	private static final String ESQUEMA_FK = "taemoi_v31_fk_it";
	private static final String V31 = "db/migration/V31__sedes_y_vinculos.sql";

	DataSource dataSource;
	JdbcTemplate jdbc;
	Fixture fx;
	List<List<Map<String, Object>>> precheckAntes;

	@BeforeAll
	void migrarConDatosSinteticos() throws Exception {
		dataSource = recrear(ESQUEMA);
		jdbc = new JdbcTemplate(dataSource);
		flyway(dataSource, "30").migrate();
		fx = Fixture.cargar(jdbc);
		precheckAntes = ejecutarScriptDocs(dataSource, "precheck-v31.sql");
		flyway(dataSource, null).migrate();
	}

	@Test
	void llegaAV31() {
		assertEquals(1, versionAplicada(jdbc, "31"));
	}

	// --- sedes -------------------------------------------------------------------------------------

	@Test
	void sedes_unaPorOrganizacion_taemoiUmbrete_restoSedePrincipal() {
		Map<String, Object> umbrete = jdbc.queryForMap("SELECT * FROM sede WHERE organizacion_id = 1");
		assertEquals("Umbrete", umbrete.get("nombre"));
		assertEquals("Calle Parada de la Cigüeña 36", umbrete.get("direccion"));
		assertEquals("Umbrete", umbrete.get("localidad"));
		assertNull(umbrete.get("codigo_postal"));
		assertNull(umbrete.get("aforo_turno"), "NULL = limite_turno de la configuración");
		assertEquals(Boolean.TRUE, umbrete.get("activa"));
		for (long org : List.of(fx.org2, fx.org3)) {
			assertEquals(List.of("Sede principal"),
					jdbc.queryForList("SELECT nombre FROM sede WHERE organizacion_id = ?", String.class, org));
		}
		assertEquals(0, jdbc.queryForObject(
				"SELECT COUNT(*) FROM organizacion o WHERE NOT EXISTS (SELECT 1 FROM sede s WHERE s.organizacion_id = o.id)",
				Integer.class));
		assertThrows(DataAccessException.class,
				() -> jdbc.update("INSERT INTO sede (organizacion_id, nombre) VALUES (1, 'Umbrete')"),
				"UNIQUE (organizacion_id, nombre)");
	}

	@Test
	void grupos_conLaSedeDeSuOrganizacion() {
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM grupo WHERE sede_id IS NULL", Integer.class));
		assertEquals(0, jdbc.queryForObject(
				"SELECT COUNT(*) FROM grupo g JOIN sede s ON s.id = g.sede_id WHERE s.organizacion_id <> g.organizacion_id",
				Integer.class));
		assertEquals(3, jdbc.queryForObject(
				"SELECT COUNT(*) FROM grupo g JOIN sede s ON s.id = g.sede_id WHERE g.organizacion_id = 1 AND s.nombre = 'Umbrete'",
				Integer.class));
		Map<String, Object> columna = columna(jdbc, "grupo", "sede_id");
		assertEquals("YES", columna.get("IS_NULLABLE"), "V31 no pasa sede_id a NOT NULL (eso es V32)");
		assertTrue(existeRestriccion(jdbc, "grupo", "fk_grupo_sede"));
	}

	@Test
	void alumnos_conSedePrincipalDeSuOrganizacion() {
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM alumno WHERE sede_principal_id IS NULL", Integer.class));
		assertEquals(0, jdbc.queryForObject("""
				SELECT COUNT(*) FROM alumno a JOIN sede s ON s.id = a.sede_principal_id
				WHERE s.organizacion_id <> a.organizacion_id""", Integer.class));
		assertEquals("YES", columna(jdbc, "alumno", "sede_principal_id").get("IS_NULLABLE"));
		assertTrue(existeRestriccion(jdbc, "alumno", "fk_alumno_sede_principal"));
	}

	// --- tablas y columnas nuevas ------------------------------------------------------------------

	@Test
	void esAdminPlataforma_notNullFalse_nadieMarcado() {
		Map<String, Object> columna = columna(jdbc, "usuario", "es_admin_plataforma");
		assertEquals("NO", columna.get("IS_NULLABLE"));
		assertEquals("0", columna.get("COLUMN_DEFAULT"));
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM usuario WHERE es_admin_plataforma <> FALSE", Integer.class));
		assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM usuario", Integer.class) > 0);
	}

	@Test
	void membresiaSedeYAsignacionProfesor_clavesYBorradoEnCascada() {
		long sede1 = jdbc.queryForObject("SELECT id FROM sede WHERE organizacion_id = 1", Long.class);
		long membresia = jdbc.queryForObject("SELECT id FROM membresia WHERE usuario_id = ? AND organizacion_id = 1",
				Long.class, fx.u7Tutor);
		jdbc.update("INSERT INTO membresia_sede (membresia_id, sede_id) VALUES (?, ?)", membresia, sede1);
		assertThrows(DataAccessException.class,
				() -> jdbc.update("INSERT INTO membresia_sede (membresia_id, sede_id) VALUES (?, ?)", membresia, sede1));

		long grupoTemporal = Fixture.grupo(jdbc, 1, fx.disciplina1, "Temporal asignacion");
		jdbc.update("INSERT INTO asignacion_profesor (organizacion_id, usuario_id, grupo_id, rol) VALUES (1, ?, ?, 'TITULAR')",
				fx.u3Gestor, grupoTemporal);
		assertThrows(DataAccessException.class, () -> jdbc.update(
				"INSERT INTO asignacion_profesor (organizacion_id, usuario_id, grupo_id, rol) VALUES (1, ?, ?, 'AYUDANTE')",
				fx.u3Gestor, grupoTemporal), "PK (usuario_id, grupo_id)");
		assertThrows(DataAccessException.class, () -> jdbc.update(
				"INSERT INTO asignacion_profesor (organizacion_id, usuario_id, grupo_id, rol) VALUES (1, ?, ?, 'JEFE')",
				fx.u7Tutor, grupoTemporal));

		jdbc.update("DELETE FROM grupo WHERE id = ?", grupoTemporal);
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM asignacion_profesor WHERE grupo_id = ?", Integer.class,
				grupoTemporal));
		jdbc.update("DELETE FROM membresia_sede WHERE membresia_id = ?", membresia);
	}

	/** El código ya no mapea Usuario.roles: borrar un usuario con filas heredadas en usuario_rol las borra. */
	@Test
	void usuarioRol_borrarUnUsuarioBorraSusFilas() {
		Map<String, Object> fk = jdbc.queryForMap("""
				SELECT CONSTRAINT_NAME, DELETE_RULE FROM information_schema.REFERENTIAL_CONSTRAINTS
				WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'usuario_rol' AND REFERENCED_TABLE_NAME = 'usuario'""");
		assertEquals("fk_usuario_rol_usuario", fk.get("CONSTRAINT_NAME"));
		assertEquals("CASCADE", fk.get("DELETE_RULE"));

		long temporal = Fixture.usuario(jdbc, "borrable@v31.test", null);
		jdbc.update("INSERT INTO usuario_rol (usuario_id, roles) VALUES (?, 'ROLE_USER')", temporal);
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, 1, 'FAMILIA')", temporal);
		assertDoesNotThrow(() -> jdbc.update("DELETE FROM usuario WHERE id = ?", temporal));
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM usuario_rol WHERE usuario_id = ?", Integer.class, temporal));
	}

	// --- usuario_alumno: reglas una por una --------------------------------------------------------

	@Test
	void usuarioAlumno_filasExactas() {
		assertEquals(filasEsperadas(fx), filas(jdbc));
		assertEquals(0, jdbc.queryForObject("""
				SELECT COUNT(*) FROM usuario_alumno ua JOIN alumno a ON a.id = ua.alumno_id
				WHERE ua.organizacion_id <> a.organizacion_id OR ua.origen <> 'MIGRACION'""", Integer.class));
		assertEquals(0, jdbc.queryForObject("""
				SELECT COUNT(*) FROM usuario_alumno ua
				WHERE NOT EXISTS (SELECT 1 FROM membresia m WHERE m.usuario_id = ua.usuario_id
				                    AND m.organizacion_id = ua.organizacion_id)""", Integer.class),
				"toda fila tiene una membresía del usuario en la escuela del alumno");
	}

	/** R1: vínculo directo con el mismo email (mayúsculas distintas) -> fila. */
	@Test
	void r1_alumnoIdConMismoEmail_fila() {
		assertTrue(vinculado(fx.u1Familia, fx.a1Propio));
	}

	/** R1: solo familia y email distinto -> sin fila (hoy no le da acceso); su alumno por email sí (R2). */
	@Test
	void r1_soloFamiliaConEmailDistinto_sinFila() {
		assertFalse(vinculado(fx.u2Familia, fx.a4Otro));
		assertTrue(vinculado(fx.u2Familia, fx.a5Propio));
	}

	/** R1: gestión en la escuela del alumno -> fila aunque el email sea otro. */
	@Test
	void r1_gestionEnLaEscuelaDelAlumno_fila() {
		assertTrue(vinculado(fx.u3Gestor, fx.a6Ajeno));
	}

	/** R1: gestor degradado (membresía de gestión inactiva, familia activa) y email distinto -> sin fila. */
	@Test
	void r1_gestionInactiva_sinFila() {
		assertFalse(vinculado(fx.u11Degradado, fx.a14));
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM usuario_alumno WHERE usuario_id = ?", Integer.class,
				fx.u11Degradado));
	}

	/** R1: gestión solo en otra escuela -> sin fila. */
	@Test
	void r1_gestionEnOtraEscuela_sinFila() {
		assertFalse(vinculado(fx.u8GestorOtra, fx.a12));
	}

	/** R1 + R3: mismo email pero ninguna membresía en la escuela del alumno -> sin fila. */
	@Test
	void r1_mismoEmailSinMembresiaEnLaEscuela_sinFila() {
		assertFalse(vinculado(fx.u9Directo, fx.a13));
	}

	/** R2: todos los alumnos del email, activos o no, en escuelas con membresía; también membresía inactiva. */
	@Test
	void r2_alumnosDelEmailConMembresia_fila() {
		assertTrue(vinculado(fx.u1Familia, fx.a2Hermano));
		assertTrue(vinculado(fx.u1Familia, fx.a3Inactivo));
		assertTrue(vinculado(fx.u5Inactiva, fx.a9));
	}

	/** R2: email del alumno con espacios sobrantes: hoy no es visible y no gana acceso. */
	@Test
	void r2_emailDelAlumnoConEspacios_sinFila() {
		assertFalse(vinculado(fx.u4Espacios, fx.a7Espacios));
	}

	/** R2: cuenta sin ninguna membresía -> sin fila. */
	@Test
	void r2_cuentaSinMembresia_sinFila() {
		assertFalse(vinculado(fx.u6SinMembresia, fx.a10));
	}

	/** R3: alumno con el email de la cuenta en una escuela donde no tiene membresía -> sin fila. */
	@Test
	void r3_escuelaSinMembresia_sinFila() {
		assertFalse(vinculado(fx.u1Familia, fx.a8OtraEscuela));
	}

	/** R4: tutor.email nunca crea vínculos. */
	@Test
	void r4_emailDeTutor_sinFila() {
		assertFalse(vinculado(fx.u7Tutor, fx.a11Tutelado));
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM usuario_alumno WHERE usuario_id = ?", Integer.class,
				fx.u7Tutor));
	}

	/** D7: PROPIO con 18 años cumplidos hoy, TUTOR si no. */
	@Test
	void relacion_porEdad() {
		assertEquals("PROPIO", relacion(fx.u1Familia, fx.a1Propio), "30 años");
		assertEquals("TUTOR", relacion(fx.u1Familia, fx.a2Hermano), "10 años");
		assertEquals("PROPIO", relacion(fx.u1Familia, fx.a3Inactivo), "18 años cumplidos hoy");
		assertEquals("TUTOR", relacion(fx.u2Familia, fx.a5Propio), "cumple 18 mañana");
		assertEquals("PROPIO", relacion(fx.u3Gestor, fx.a6Ajeno), "25 años");
		assertEquals("TUTOR", relacion(fx.u5Inactiva, fx.a9), "12 años");
	}

	// --- comprobaciones previa y posterior ----------------------------------------------------------

	@Test
	void precheck_recuentosDeCadaCaso() {
		Map<String, Object> q1 = precheckAntes.get(0).get(0);
		assertEquals(6L, numero(q1, "usuarios_con_alumno_id"));
		assertEquals(0L, numero(q1, "alumno_id_inexistente"));
		assertEquals(2L, numero(q1, "alumno_id_escuela_sin_membresia"));
		assertEquals(3L, numero(q1, "alumno_id_email_distinto_familia"));
		assertEquals(1L, numero(q1, "alumno_id_email_distinto_gestion"));

		Map<String, Object> q2 = precheckAntes.get(1).get(0);
		assertEquals(1L, numero(q2, "alumnos_email_solo_mayusculas"));
		assertEquals(1L, numero(q2, "alumnos_email_solo_espacios"));
		assertEquals(3L, numero(q2, "alumno_cuenta_escuela_sin_membresia"));
		assertEquals(1L, numero(q2, "emails_compartidos_por_alumnos"));
		assertEquals(3L, numero(q2, "alumnos_con_email_compartido"));

		List<Map<String, Object>> distribucion = precheckAntes.get(2);
		Map<Long, Long> porNumero = new LinkedHashMap<>();
		for (Map<String, Object> fila : distribucion) {
			porNumero.put(numero(fila, "alumnos_por_email"), numero(fila, "cuentas"));
		}
		assertEquals(Map.of(1L, 2L, 3L, 1L), porNumero);

		Map<String, Object> q4 = precheckAntes.get(3).get(0);
		assertEquals(1L, numero(q4, "tutor_email_candidatos"));
		assertEquals(4L, numero(q4, "cuentas_solo_familia_sin_alumno"));
		assertEquals(1L, numero(q4, "membresias_responsable_o_profesor"));
		assertEquals(1L, numero(q4, "grupos_nombre_repetido"));
		assertEquals(3L, numero(q4, "organizaciones"));
		assertEquals(2L, numero(q4, "filas_usuario_alumno_r1"));
		assertEquals(5L, numero(q4, "filas_usuario_alumno_r2"));
		assertEquals(6L, numero(q4, "filas_usuario_alumno_total"));
		assertEquals(6, jdbc.queryForObject("SELECT COUNT(*) FROM usuario_alumno", Integer.class),
				"el precheck predice las filas que crea V31");
	}

	@Test
	void precheck_soloRecuentos_yTambienSeEjecutaTrasV31() throws Exception {
		for (List<Map<String, Object>> consulta : precheckAntes) {
			for (Map<String, Object> fila : consulta) {
				for (Object valor : fila.values()) {
					assertTrue(valor instanceof Number, "solo recuentos: " + fila);
				}
			}
		}
		assertEquals(4, ejecutarScriptDocs(dataSource, "precheck-v31.sql").size());
	}

	@Test
	void postcheck_limpioTrasV31() throws Exception {
		List<List<Map<String, Object>>> postcheck = ejecutarScriptDocs(dataSource, "postcheck-v31.sql");
		Map<String, Object> q1 = postcheck.get(0).get(0);
		for (String columna : List.of("familia_alumnos_email_sin_vinculo", "familia_alumnos_vinculo_sin_email",
				"grupos_sin_sede", "organizaciones_sin_sede", "mixtas_alumnos_email_sin_vinculo",
				"mixtas_alumnos_vinculo_sin_email", "alumnos_sin_sede_principal", "usuarios_admin_plataforma")) {
			assertEquals(0L, numero(q1, columna), columna);
		}
		assertEquals(6L, postcheck.get(1).stream().mapToLong(f -> numero(f, "filas")).sum());
	}

	// --- repetición y fallos a medias -------------------------------------------------------------

	@Test
	void repetirV31DosVecesNoCambiaNada() throws Exception {
		Map<String, List<Map<String, Object>>> antes = estado(jdbc);
		ejecutarV31(dataSource);
		ejecutarV31(dataSource);
		assertEquals(antes, estado(jdbc));
	}

	@Test
	void marcaTemporalParaLaGuardaDeV32_soloEnSuConexion() throws Exception {
		try (Connection conexion = dataSource.getConnection()) {
			ScriptUtils.executeSqlScript(conexion, new EncodedResource(new ClassPathResource(V31), StandardCharsets.UTF_8));
			try (Statement st = conexion.createStatement();
					ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM v31_aplicada_en_esta_conexion")) {
				rs.next();
				assertEquals(1, rs.getInt(1));
			}
		}
		assertThrows(DataAccessException.class,
				() -> jdbc.queryForObject("SELECT COUNT(*) FROM v31_aplicada_en_esta_conexion", Integer.class));
	}

	/**
	 * Fallo al rellenar grupo.sede_id (sede ya creada, columna ya añadida) y después al rellenar
	 * alumno.sede_principal_id (pasos 1-3 ya confirmados): cada vez {@code repair} + {@code migrate} retoma y el
	 * resultado final es el mismo que sin fallos.
	 */
	@Test
	void falloParcial_repairYMigrate_terminaBien() throws Exception {
		DataSource fuente = recrear(ESQUEMA_FALLO);
		JdbcTemplate f = new JdbcTemplate(fuente);
		flyway(fuente, "30").migrate();
		Fixture datos = Fixture.cargar(f);

		// a) Falla al rellenar grupo.sede_id.
		f.execute("""
				CREATE TRIGGER it_v31_fallo_grupo BEFORE UPDATE ON grupo FOR EACH ROW
				SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'fallo simulado de V31 (grupo)'""");
		FlywayException error = assertThrows(FlywayException.class, () -> flyway(fuente, null).migrate());
		assertTrue(mensajeCompleto(error).contains("fallo simulado de V31 (grupo)"), mensajeCompleto(error));
		assertEquals(3, f.queryForObject("SELECT COUNT(*) FROM sede", Integer.class));
		assertTrue(existeColumna(f, "grupo", "sede_id"));
		assertFalse(existeColumna(f, "alumno", "sede_principal_id"));
		f.execute("DROP TRIGGER it_v31_fallo_grupo");

		// b) Falla al rellenar alumno.sede_principal_id.
		f.execute("""
				CREATE TRIGGER it_v31_fallo_alumno BEFORE UPDATE ON alumno FOR EACH ROW
				SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'fallo simulado de V31 (alumno)'""");
		Flyway segundo = flyway(fuente, null);
		segundo.repair();
		error = assertThrows(FlywayException.class, segundo::migrate);
		assertTrue(mensajeCompleto(error).contains("fallo simulado de V31 (alumno)"), mensajeCompleto(error));
		assertEquals(3, f.queryForObject("SELECT COUNT(*) FROM sede", Integer.class), "la sede no se duplica");
		assertEquals(0, f.queryForObject("SELECT COUNT(*) FROM grupo WHERE sede_id IS NULL", Integer.class));
		assertTrue(existeColumna(f, "alumno", "sede_principal_id"));
		assertFalse(existeColumna(f, "usuario", "es_admin_plataforma"));
		assertEquals(List.of("NO ACTION"), reglaBorradoUsuarioRol(f), "el paso 9 aún no se ha aplicado");
		f.execute("DROP TRIGGER it_v31_fallo_alumno");

		// c) Termina.
		Flyway tercero = flyway(fuente, null);
		tercero.repair();
		tercero.migrate();
		assertEquals(1, versionAplicada(f, "31"));
		assertEquals(3, f.queryForObject("SELECT COUNT(*) FROM sede", Integer.class));
		assertEquals(0, f.queryForObject("SELECT COUNT(*) FROM grupo WHERE sede_id IS NULL", Integer.class));
		assertEquals(0, f.queryForObject("SELECT COUNT(*) FROM alumno WHERE sede_principal_id IS NULL", Integer.class));
		assertEquals(List.of("CASCADE"), reglaBorradoUsuarioRol(f));
		assertEquals(filasEsperadas(datos), filas(f));

		// d) Interrumpida tras crear usuario_alumno y antes de su relleno y del paso 9 (estado reconstruido a mano):
		// repetir V31 rellena lo mismo y cambia la FK.
		f.update("DELETE FROM usuario_alumno");
		f.execute("ALTER TABLE usuario_rol DROP FOREIGN KEY fk_usuario_rol_usuario");
		f.execute("ALTER TABLE usuario_rol ADD CONSTRAINT FKbyfgloj439r9wr9smrms9u33r FOREIGN KEY (usuario_id) REFERENCES usuario(id)");
		ejecutarV31(fuente);
		assertEquals(filasEsperadas(datos), filas(f));
		assertEquals(List.of("CASCADE"), reglaBorradoUsuarioRol(f));
	}

	private static List<String> filasEsperadas(Fixture d) {
		List<String> esperado = new ArrayList<>(List.of(
				par(d.u1Familia, d.a1Propio), par(d.u1Familia, d.a2Hermano), par(d.u1Familia, d.a3Inactivo),
				par(d.u2Familia, d.a5Propio), par(d.u3Gestor, d.a6Ajeno), par(d.u5Inactiva, d.a9)));
		esperado.sort(null);
		return esperado;
	}

	private static List<String> filas(JdbcTemplate j) {
		List<String> filas = new ArrayList<>(j.queryForList(
				"SELECT CONCAT(usuario_id, '-', alumno_id) FROM usuario_alumno", String.class));
		filas.sort(null);
		return filas;
	}

	/**
	 * Paso 9 interrumpido o ya hecho a mano: sin FK, la añade; con la FK nueva y una antigua sin cascada, quita la
	 * antigua; con la nueva sola, no cambia nada.
	 */
	@Test
	void usuarioRol_fkRepetible() throws Exception {
		DataSource fuente = recrear(ESQUEMA_FK);
		JdbcTemplate k = new JdbcTemplate(fuente);
		flyway(fuente, null).migrate();

		k.execute("ALTER TABLE usuario_rol DROP FOREIGN KEY fk_usuario_rol_usuario");
		ejecutarV31(fuente);
		assertEquals(List.of("CASCADE"), reglaBorradoUsuarioRol(k));

		k.execute("ALTER TABLE usuario_rol ADD CONSTRAINT fk_usuario_rol_antigua FOREIGN KEY (usuario_id) REFERENCES usuario(id)");
		assertEquals(2, reglaBorradoUsuarioRol(k).size());
		ejecutarV31(fuente);
		assertEquals(List.of("CASCADE"), reglaBorradoUsuarioRol(k));

		ejecutarV31(fuente);
		assertEquals(List.of("CASCADE"), reglaBorradoUsuarioRol(k));
	}

	// ---------------------------------------------------------------------------------------------

	private boolean vinculado(long usuario, long alumno) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM usuario_alumno WHERE usuario_id = ? AND alumno_id = ?",
				Integer.class, usuario, alumno) == 1;
	}

	private String relacion(long usuario, long alumno) {
		return jdbc.queryForObject("SELECT relacion FROM usuario_alumno WHERE usuario_id = ? AND alumno_id = ?",
				String.class, usuario, alumno);
	}

	private static String par(long usuario, long alumno) {
		return usuario + "-" + alumno;
	}

	private static long numero(Map<String, Object> fila, String columna) {
		Object valor = fila.get(columna);
		assertTrue(valor instanceof Number, columna + " = " + valor);
		return ((Number) valor).longValue();
	}

	/** Filas y esquema que V31 toca, para comparar antes y después de repetirla. */
	private static Map<String, List<Map<String, Object>>> estado(JdbcTemplate j) {
		Map<String, List<Map<String, Object>>> estado = new LinkedHashMap<>();
		estado.put("sede", j.queryForList("SELECT * FROM sede ORDER BY id"));
		estado.put("grupo", j.queryForList("SELECT id, organizacion_id, sede_id FROM grupo ORDER BY id"));
		estado.put("alumno", j.queryForList("SELECT id, organizacion_id, sede_principal_id FROM alumno ORDER BY id"));
		estado.put("usuario_alumno", j.queryForList(
				"SELECT usuario_id, alumno_id, organizacion_id, relacion, origen FROM usuario_alumno ORDER BY usuario_id, alumno_id"));
		estado.put("usuario", j.queryForList("SELECT id, es_admin_plataforma FROM usuario ORDER BY id"));
		estado.put("columnas", j.queryForList("""
				SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, IS_NULLABLE, COLUMN_DEFAULT FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE() ORDER BY TABLE_NAME, ORDINAL_POSITION"""));
		estado.put("restricciones", j.queryForList("""
				SELECT TABLE_NAME, CONSTRAINT_NAME, CONSTRAINT_TYPE FROM information_schema.TABLE_CONSTRAINTS
				WHERE CONSTRAINT_SCHEMA = DATABASE() ORDER BY TABLE_NAME, CONSTRAINT_NAME"""));
		estado.put("indices", j.queryForList("""
				SELECT TABLE_NAME, INDEX_NAME, COLUMN_NAME, SEQ_IN_INDEX FROM information_schema.STATISTICS
				WHERE TABLE_SCHEMA = DATABASE() ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX"""));
		return estado;
	}

	private static List<String> reglaBorradoUsuarioRol(JdbcTemplate j) {
		return j.queryForList("""
				SELECT DELETE_RULE FROM information_schema.REFERENTIAL_CONSTRAINTS
				WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'usuario_rol' AND REFERENCED_TABLE_NAME = 'usuario'
				ORDER BY CONSTRAINT_NAME""", String.class);
	}

	private static Map<String, Object> columna(JdbcTemplate j, String tabla, String columna) {
		return j.queryForMap("""
				SELECT IS_NULLABLE, COLUMN_DEFAULT, DATA_TYPE FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?""", tabla, columna);
	}

	private static boolean existeColumna(JdbcTemplate j, String tabla, String columna) {
		return j.queryForObject("""
				SELECT COUNT(*) FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?""", Integer.class, tabla, columna) == 1;
	}

	private static boolean existeRestriccion(JdbcTemplate j, String tabla, String nombre) {
		return j.queryForObject("""
				SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
				WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = ? AND CONSTRAINT_NAME = ?""", Integer.class, tabla,
				nombre) == 1;
	}

	private static int versionAplicada(JdbcTemplate j, String version) {
		return j.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version = ? AND success = 1",
				Integer.class, version);
	}

	private static void ejecutarV31(DataSource fuente) throws Exception {
		try (Connection conexion = fuente.getConnection()) {
			ScriptUtils.executeSqlScript(conexion, new EncodedResource(new ClassPathResource(V31), StandardCharsets.UTF_8));
		}
	}

	/** Ejecuta un script de docs/multi-escuela (solo SELECT) y devuelve las filas de cada consulta. */
	static List<List<Map<String, Object>>> ejecutarScriptDocs(DataSource fuente, String nombre) throws IOException {
		Path desdeModulo = Path.of("..", "docs", "multi-escuela", nombre);
		Path ruta = Files.exists(desdeModulo) ? desdeModulo : Path.of("docs", "multi-escuela", nombre);
		String sql = Files.readString(ruta, StandardCharsets.UTF_8);
		StringBuilder sinComentarios = new StringBuilder();
		for (String linea : sql.split("\\R")) {
			if (!linea.strip().startsWith("--")) {
				sinComentarios.append(linea).append('\n');
			}
		}
		JdbcTemplate j = new JdbcTemplate(fuente);
		// Statement.execute (como un cliente mysql): executeQuery de Connector/J rechaza algunas SELECT válidas con
		// su análisis previo del texto.
		RowMapperResultSetExtractor<Map<String, Object>> filas = new RowMapperResultSetExtractor<>(new ColumnMapRowMapper());
		List<List<Map<String, Object>>> resultados = new ArrayList<>();
		for (String sentencia : sinComentarios.toString().split(";")) {
			if (sentencia.isBlank()) {
				continue;
			}
			assertTrue(sentencia.stripLeading().toUpperCase().startsWith("SELECT"), "solo lectura: " + sentencia);
			resultados.add(j.execute((Statement st) -> {
				assertTrue(st.execute(sentencia.strip()), "debe devolver filas: " + sentencia);
				try (ResultSet rs = st.getResultSet()) {
					return filas.extractData(rs);
				}
			}));
		}
		return resultados;
	}

	private static String mensajeCompleto(Throwable error) {
		StringBuilder sb = new StringBuilder();
		for (Throwable t = error; t != null; t = t.getCause()) {
			sb.append(t.getMessage()).append(" | ");
		}
		return sb.toString();
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

	/**
	 * Datos sintéticos en V30. Escuelas: 1 (Taemoi), org2 (con grupos y una disciplina propia), org3 (sin
	 * grupos). Cada usuario y alumno cubre un caso de §5:
	 *
	 * <pre>
	 * u1 familia org1, alumno_id = a1 (mismo email, mayúsculas)   -> a1 (R1+R2), a2, a3 (R2); a8 no (R3, org2)
	 * u2 familia org1, alumno_id = a4 (otro email)                 -> a4 no (R1); a5 (R2, cumple 18 mañana)
	 * u3 ADMINISTRADOR org1, alumno_id = a6 (otro email)           -> a6 (R1 gestión)
	 * u4 familia org1; a7 con su email entre espacios               -> no (hoy no es visible)
	 * u5 familia org1 INACTIVA; a9 con su email                     -> a9 (R2: membresía activa o no)
	 * u6 sin membresías; a10 con su email                           -> no
	 * u7 familia org1; tutor de a11 con su email                    -> no (R4)
	 * u8 ADMINISTRADOR org2, alumno_id = a12 (org1, otro email)     -> no (gestión en otra escuela)
	 * u9 familia org2, alumno_id = a13 (org1, mismo email)          -> no (R3: sin membresía en org1)
	 * u10 PROFESOR org2                                             -> precheck membresias_responsable_o_profesor
 * u11 ADMINISTRADOR org1 INACTIVA + familia activa, alumno_id = a14 (otro email) -> no (gestor degradado)
	 * </pre>
	 */
	static final class Fixture {
		long org2;
		long org3;
		long disciplina1;
		long u1Familia, u2Familia, u3Gestor, u4Espacios, u5Inactiva, u6SinMembresia, u7Tutor, u8GestorOtra, u9Directo,
				u11Degradado;
		long a1Propio, a2Hermano, a3Inactivo, a4Otro, a5Propio, a6Ajeno, a7Espacios, a8OtraEscuela, a9, a10, a11Tutelado,
				a12, a13, a14;

		static Fixture cargar(JdbcTemplate j) {
			Fixture fx = new Fixture();
			j.update("INSERT INTO organizacion (slug, nombre) VALUES ('it-v31-dos', 'Escuela dos')");
			j.update("INSERT INTO organizacion (slug, nombre) VALUES ('it-v31-tres', 'Escuela tres')");
			fx.org2 = j.queryForObject("SELECT id FROM organizacion WHERE slug = 'it-v31-dos'", Long.class);
			fx.org3 = j.queryForObject("SELECT id FROM organizacion WHERE slug = 'it-v31-tres'", Long.class);
			fx.disciplina1 = j.queryForObject(
					"SELECT id FROM disciplina WHERE organizacion_id = 1 AND codigo = 'TAEKWONDO'", Long.class);
			j.update("""
					INSERT INTO disciplina (organizacion_id, codigo, nombre, sistema_grados_id, edad_adulto, orden)
					VALUES (?, 'TAEKWONDO', 'Taekwondo dos', NULL, 14, 1)""", fx.org2);
			long disciplina2 = j.queryForObject(
					"SELECT id FROM disciplina WHERE organizacion_id = ? AND codigo = 'TAEKWONDO'", Long.class, fx.org2);
			grupo(j, 1, fx.disciplina1, "Infantil");
			grupo(j, 1, fx.disciplina1, "Repetido");
			grupo(j, 1, fx.disciplina1, "Repetido");
			grupo(j, fx.org2, disciplina2, "Repetido");

			fx.a1Propio = alumno(j, 1, "Familia.Uno@V31.test", true, "DATE_SUB(CURDATE(), INTERVAL 30 YEAR)");
			fx.a2Hermano = alumno(j, 1, "familia.uno@v31.test", true, "DATE_SUB(CURDATE(), INTERVAL 10 YEAR)");
			fx.a3Inactivo = alumno(j, 1, "familia.uno@v31.test", false, "DATE_SUB(CURDATE(), INTERVAL 18 YEAR)");
			fx.a4Otro = alumno(j, 1, "otro@v31.test", true, "DATE_SUB(CURDATE(), INTERVAL 20 YEAR)");
			fx.a5Propio = alumno(j, 1, "familia.dos@v31.test", true,
					"DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 18 YEAR), INTERVAL 1 DAY)");
			fx.a6Ajeno = alumno(j, 1, "gestor.ajeno@v31.test", true, "DATE_SUB(CURDATE(), INTERVAL 25 YEAR)");
			fx.a7Espacios = alumno(j, 1, " espacios@v31.test ", true, "DATE_SUB(CURDATE(), INTERVAL 30 YEAR)");
			fx.a8OtraEscuela = alumno(j, fx.org2, "familia.uno@v31.test", true, "DATE_SUB(CURDATE(), INTERVAL 30 YEAR)");
			fx.a9 = alumno(j, 1, "inactiva@v31.test", true, "DATE_SUB(CURDATE(), INTERVAL 12 YEAR)");
			fx.a10 = alumno(j, 1, "sinmembresia@v31.test", true, "DATE_SUB(CURDATE(), INTERVAL 30 YEAR)");
			fx.a11Tutelado = alumno(j, 1, "tutelado@v31.test", true, "DATE_SUB(CURDATE(), INTERVAL 9 YEAR)");
			fx.a12 = alumno(j, 1, "ajeno2@v31.test", true, "DATE_SUB(CURDATE(), INTERVAL 30 YEAR)");
			fx.a13 = alumno(j, 1, "directo@v31.test", true, "DATE_SUB(CURDATE(), INTERVAL 30 YEAR)");
			fx.a14 = alumno(j, 1, "otro.degradado@v31.test", true, "DATE_SUB(CURDATE(), INTERVAL 30 YEAR)");
			j.update("""
					INSERT INTO tutor (organizacion_id, alumno_id, nombre, apellidos, nif, parentesco, email, responsable_legal, orden)
					VALUES (1, ?, 'Tutor', 'Prueba', NULL, 'MADRE', 'Tutor@v31.test', TRUE, 1)""", fx.a11Tutelado);

			fx.u1Familia = usuario(j, "familia.uno@v31.test", fx.a1Propio);
			membresia(j, fx.u1Familia, 1, "FAMILIA", true);
			fx.u2Familia = usuario(j, "familia.dos@v31.test", fx.a4Otro);
			membresia(j, fx.u2Familia, 1, "FAMILIA", true);
			fx.u3Gestor = usuario(j, "gestor@v31.test", fx.a6Ajeno);
			membresia(j, fx.u3Gestor, 1, "ADMINISTRADOR", true);
			fx.u4Espacios = usuario(j, "espacios@v31.test", null);
			membresia(j, fx.u4Espacios, 1, "FAMILIA", true);
			fx.u5Inactiva = usuario(j, "inactiva@v31.test", null);
			membresia(j, fx.u5Inactiva, 1, "FAMILIA", false);
			fx.u6SinMembresia = usuario(j, "sinmembresia@v31.test", null);
			fx.u7Tutor = usuario(j, "tutor@v31.test", null);
			membresia(j, fx.u7Tutor, 1, "FAMILIA", true);
			fx.u8GestorOtra = usuario(j, "gestor2@v31.test", fx.a12);
			membresia(j, fx.u8GestorOtra, fx.org2, "ADMINISTRADOR", true);
			fx.u9Directo = usuario(j, "directo@v31.test", fx.a13);
			membresia(j, fx.u9Directo, fx.org2, "FAMILIA", true);
			// Gestor degradado desde la pantalla de roles: ADMINISTRADOR desactivada, FAMILIA activa.
			fx.u11Degradado = usuario(j, "degradado@v31.test", fx.a14);
			membresia(j, fx.u11Degradado, 1, "ADMINISTRADOR", false);
			membresia(j, fx.u11Degradado, 1, "FAMILIA", true);
			long profesor = usuario(j, "profe@v31.test", null);
			membresia(j, profesor, fx.org2, "PROFESOR", true);
			// Filas heredadas de usuario_rol (sin escribirse desde V20).
			j.update("INSERT INTO usuario_rol (usuario_id, roles) VALUES (?, 'ROLE_USER')", fx.u1Familia);
			return fx;
		}

		static long grupo(JdbcTemplate j, long organizacion, long disciplina, String nombre) {
			j.update("INSERT INTO grupo (organizacion_id, nombre, tipo, disciplina_id) VALUES (?, ?, 'Infantil', ?)",
					organizacion, nombre, disciplina);
			return j.queryForObject("SELECT MAX(id) FROM grupo", Long.class);
		}

		private static long alumno(JdbcTemplate j, long organizacion, String email, boolean activo, String fechaSql) {
			long expediente = j.queryForObject("SELECT COALESCE(MAX(numero_expediente), 9000) + 1 FROM alumno", Long.class);
			j.update("""
					INSERT INTO alumno (activo, apellidos, autorizacion_web, direccion, fecha_nacimiento, nombre,
					  numero_expediente, telefono, tiene_derecho_examen, organizacion_id, email)
					VALUES (?, 'Prueba', 1, 'Calle 1', """ + fechaSql + """
					, ?, ?, '600000000', 0, ?, ?)""", activo, "Alumno " + expediente, expediente, organizacion, email);
			return j.queryForObject("SELECT id FROM alumno WHERE numero_expediente = ? AND organizacion_id = ?", Long.class,
					expediente, organizacion);
		}

		static long usuario(JdbcTemplate j, String email, Long alumnoId) {
			j.update("INSERT INTO usuario (email, nombre, apellidos, contrasena, alumno_id) VALUES (?, 'Usuario', 'Prueba', 'x', ?)",
					email, alumnoId);
			return j.queryForObject("SELECT id FROM usuario WHERE email = ?", Long.class, email);
		}

		private static void membresia(JdbcTemplate j, long usuario, long organizacion, String rol, boolean activa) {
			j.update("INSERT INTO membresia (usuario_id, organizacion_id, rol, activa) VALUES (?, ?, ?, ?)", usuario,
					organizacion, rol, activa);
		}
	}
}
