package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * V25 sobre datos con la forma de producción: esquema propio migrado hasta V23, alumnos con
 * teléfonos INT y responsable legal en las columnas heredadas (también solo NIF o en blanco), una
 * segunda escuela y preinscripciones finalizadas (menor y adulto), pendientes y sin alumno.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PersonasMigracionIntegrationTest extends MySqlIntegrationTest {

	private static final String ESQUEMA = "taemoi_v25_it";
	private static final String V25 = "db/migration/V25__personas_tutores_salud_consentimientos.sql";

	DataSource dataSource;
	JdbcTemplate jdbc;
	long org2;
	long conTutor;
	long soloNif;
	long enBlanco;
	long sinTelefono2;
	long deOtraEscuela;
	long preMenor;
	long preAdulto;
	long preVeranoNoche;
	long preInviernoNoche;

	@BeforeAll
	void migrarDesdeV23ConDatos() {
		JdbcTemplate root = new JdbcTemplate(fuente(""));
		root.execute("DROP DATABASE IF EXISTS " + ESQUEMA);
		root.execute("CREATE DATABASE " + ESQUEMA + " CHARACTER SET utf8mb4");
		dataSource = fuente(ESQUEMA);
		jdbc = new JdbcTemplate(dataSource);

		flyway("23").migrate();
		assertEquals("int", tipoColumna("telefono"), "punto de partida: teléfono INT");

		jdbc.update("INSERT INTO organizacion (slug, nombre) VALUES ('otra-v25', 'Otra escuela')");
		org2 = jdbc.queryForObject("SELECT id FROM organizacion WHERE slug = 'otra-v25'", Long.class);

		conTutor = alumno(1, 301, 600111222, 955123456, "  MARÍA PÉREZ GÓMEZ ", " 12345678z");
		soloNif = alumno(1, 302, 699000111, null, null, "87654321X");
		enBlanco = alumno(1, 303, 911222333, null, "   ", "");
		sinTelefono2 = alumno(1, 304, 712345678, null, null, null);
		deOtraEscuela = alumno(org2, 1, 633444555, 644555666, "Tutor Org Dos", null);

		long plantilla = jdbc.queryForObject(
				"SELECT id FROM plantilla_preinscripcion WHERE deporte = 'TAEKWONDO' ORDER BY id LIMIT 1", Long.class);
		// Menor al firmar (nacido en 2012, firmó en 2025), sin consentimiento fotográfico.
		preMenor = preinscripcion("V25-MENOR", "FINALIZADA", conTutor, "2012-03-01", false, "MARÍA PÉREZ GÓMEZ",
				"2025-09-10 18:30:00", plantilla, 1);
		// Adulto, con consentimiento.
		preAdulto = preinscripcion("V25-ADULTO", "FINALIZADA", soloNif, "1990-01-01", true, "Adulto Firmante",
				"2025-09-11 10:00:00", plantilla, 1);
		// No generan consentimientos: pendiente, cancelada, y finalizada sin alumno.
		preinscripcion("V25-PEND", "PENDIENTE", null, "1990-01-01", true, "Pendiente", "2025-09-12 10:00:00",
				plantilla, 1);
		preinscripcion("V25-CANC", "CANCELADA", enBlanco, "1990-01-01", true, "Cancelada", "2025-09-12 10:00:00",
				plantilla, 1);
		// creada_en está en UTC: 22:30 UTC en verano es ya el día siguiente en Madrid (+2 h); 23:30 UTC en
		// invierno también (+1 h). La primera, además, cruza un cumpleaños de 18 años.
		long nocturno = alumno(1, 305, 655000111, null, null, null);
		preVeranoNoche = preinscripcion("V25-VERANO", "FINALIZADA", nocturno, "2007-09-11", true, "Nocturno",
				"2025-09-10 22:30:00", plantilla, 1);
		preInviernoNoche = preinscripcion("V25-INVIERNO", "FINALIZADA", nocturno, "2007-09-11", true, "Nocturno",
				"2025-01-15 23:30:00", plantilla, 1);

		// Hasta V25 (la versión que se prueba); V26 la cubre SwitchGradosMigracionIntegrationTest.
		flyway("25").migrate();
	}

	@Test
	void llegaAV25() {
		assertEquals("25", jdbc.queryForObject(
				"SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1",
				String.class));
	}

	@Test
	void losTelefonosPasanAVarcharConservandoLosDigitos() {
		assertEquals("varchar", tipoColumna("telefono"));
		assertEquals("varchar", tipoColumna("telefono2"));
		assertEquals("YES", jdbc.queryForObject("""
				SELECT IS_NULLABLE FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'alumno' AND COLUMN_NAME = 'telefono'
				""", String.class));
		Map<String, Object> a = fila(conTutor);
		assertEquals("600111222", a.get("telefono"));
		assertEquals("955123456", a.get("telefono2"));
		assertEquals("712345678", fila(sinTelefono2).get("telefono"));
		assertNull(fila(sinTelefono2).get("telefono2"));
		assertEquals("633444555", fila(deOtraEscuela).get("telefono"));

		jdbc.update("UPDATE alumno SET telefono = NULL WHERE id = ?", sinTelefono2);
		assertNull(fila(sinTelefono2).get("telefono"), "telefono admite NULL");
		jdbc.update("UPDATE alumno SET telefono = '712345678' WHERE id = ?", sinTelefono2);
	}

	@Test
	void columnasNuevasDeAlumno() {
		assertEquals("enum('FEMENINO','MASCULINO')", jdbc.queryForObject("""
				SELECT COLUMN_TYPE FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'alumno' AND COLUMN_NAME = 'genero'
				""", String.class));
		Map<String, Object> a = fila(conTutor);
		assertTrue(a.containsKey("localidad") && a.get("localidad") == null);
		assertTrue(a.containsKey("codigo_postal") && a.get("codigo_postal") == null);
		assertNull(a.get("genero"));
	}

	@Test
	void elResponsableLegalPasaATutorYLasColumnasHeredadasSeConservan() {
		List<Map<String, Object>> tutores = jdbc.queryForList("SELECT * FROM tutor WHERE alumno_id = ?", conTutor);
		assertEquals(1, tutores.size());
		Map<String, Object> t = tutores.get(0);
		assertEquals("MARÍA PÉREZ GÓMEZ", t.get("nombre"), "nombre completo, sin espacios sobrantes");
		assertNull(t.get("apellidos"));
		assertEquals("12345678Z", t.get("nif"));
		assertEquals("TUTOR_LEGAL", t.get("parentesco"));
		assertEquals(Boolean.TRUE, t.get("responsable_legal"));
		assertEquals(1L, ((Number) t.get("organizacion_id")).longValue());

		Map<String, Object> a = fila(conTutor);
		// Este test se queda en V25: las columnas heredadas existen hasta V29, que las borra tras rellenar
		// los responsables solo-NIF (PersonasContraccionMigracionIntegrationTest).
		assertEquals("  MARÍA PÉREZ GÓMEZ ", a.get("responsable_legal_nombre"), "V25 no toca la columna heredada");
		assertEquals(" 12345678z", a.get("responsable_legal_nif"));

		assertEquals(0, contarTutores(soloNif), "en V25, sin nombre no hay tutor (V29 lo crea con nombre NULL)");
		assertEquals(0, contarTutores(enBlanco));
		assertEquals(0, contarTutores(sinTelefono2));

		Map<String, Object> otra = jdbc.queryForMap("SELECT * FROM tutor WHERE alumno_id = ?", deOtraEscuela);
		assertEquals(org2, ((Number) otra.get("organizacion_id")).longValue(), "el tutor queda en la escuela del alumno");
		assertNull(otra.get("nif"));
	}

	@Test
	void consentimientosDesdePreinscripcionesFinalizadas() {
		assertEquals(8, jdbc.queryForObject("SELECT COUNT(*) FROM consentimiento", Integer.class),
				"2 por cada preinscripción finalizada con alumno; ninguna de las demás");

		Map<String, Object> imagenMenor = consentimiento(preMenor, "USO_IMAGEN");
		assertEquals(Boolean.FALSE, imagenMenor.get("otorgado"));
		assertEquals(java.sql.Date.valueOf("2025-09-10"), imagenMenor.get("fecha"));
		assertEquals(Boolean.FALSE, imagenMenor.get("fecha_aproximada"));
		assertEquals("MARÍA PÉREZ GÓMEZ", imagenMenor.get("firmante_nombre"));
		assertEquals("TUTOR", imagenMenor.get("firmante_relacion"));
		assertEquals("PREINSCRIPCION", imagenMenor.get("origen"));
		assertEquals(conTutor, ((Number) imagenMenor.get("alumno_id")).longValue());
		assertNull(imagenMenor.get("documento_id"));

		Map<String, Object> normasMenor = consentimiento(preMenor, "NORMAS");
		assertEquals(Boolean.TRUE, normasMenor.get("otorgado"));

		Map<String, Object> imagenAdulto = consentimiento(preAdulto, "USO_IMAGEN");
		assertEquals(Boolean.TRUE, imagenAdulto.get("otorgado"));
		assertEquals("PROPIO", imagenAdulto.get("firmante_relacion"));
		assertEquals(java.sql.Date.valueOf("2025-09-11"), imagenAdulto.get("fecha"));
		assertEquals(1L, ((Number) imagenAdulto.get("organizacion_id")).longValue());
	}

	@Test
	void laFechaDeLaFirmaEsElDiaEnMadridNoEnUtc() {
		Map<String, Object> verano = consentimiento(preVeranoNoche, "NORMAS");
		assertEquals(java.sql.Date.valueOf("2025-09-11"), verano.get("fecha"), "22:30 UTC = 00:30 CEST del día 11");
		assertEquals("PROPIO", verano.get("firmante_relacion"), "cumple 18 el día 11 (hora de Madrid)");
		assertEquals(java.sql.Date.valueOf("2025-01-16"), consentimiento(preInviernoNoche, "NORMAS").get("fecha"),
				"23:30 UTC = 00:30 CET del día 16");
		assertEquals("TUTOR", consentimiento(preInviernoNoche, "USO_IMAGEN").get("firmante_relacion"));
	}

	@Test
	void autorizacionWebNoSeModifica() {
		// Insertada con autorizacion_web = 1 aunque la preinscripción del menor no la otorga:
		// la migración no la recalcula (pudo editarse a mano después de finalizar).
		assertEquals(Boolean.TRUE, fila(conTutor).get("autorizacion_web"));
	}

	@Test
	void saludVaciaConLasColumnasEsperadas() {
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM alumno_salud", Integer.class));
		assertEquals("text", jdbc.queryForObject("""
				SELECT DATA_TYPE FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'alumno_salud' AND COLUMN_NAME = 'descripcion'
				""", String.class));
	}

	@Test
	void v25SePuedeRepetirSinDuplicar() throws Exception {
		try (Connection conexion = dataSource.getConnection()) {
			ScriptUtils.executeSqlScript(conexion, new ClassPathResource(V25));
		}
		assertEquals(1, contarTutores(conTutor));
		assertEquals(1, contarTutores(deOtraEscuela));
		assertEquals(8, jdbc.queryForObject("SELECT COUNT(*) FROM consentimiento", Integer.class));
		assertEquals("600111222", fila(conTutor).get("telefono"));
		assertEquals("varchar", tipoColumna("telefono"));
	}

	@Test
	void borrarUnAlumnoArrastraSusTutoresYConsentimientos() {
		long efimero = alumno(1, 399, 600999888, null, "Efímero", null);
		jdbc.update("INSERT INTO tutor (organizacion_id, alumno_id, nombre) VALUES (1, ?, 'X')", efimero);
		jdbc.update("""
				INSERT INTO consentimiento (organizacion_id, alumno_id, tipo, otorgado, origen)
				VALUES (1, ?, 'USO_IMAGEN', 1, 'APP')
				""", efimero);
		jdbc.update("""
				INSERT INTO alumno_salud (alumno_id, organizacion_id, origen, actualizado_en)
				VALUES (?, 1, 'APP', NOW(6))
				""", efimero);
		jdbc.update("DELETE FROM alumno WHERE id = ?", efimero);
		assertEquals(0, contarTutores(efimero));
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM consentimiento WHERE alumno_id = ?", Integer.class,
				efimero));
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM alumno_salud WHERE alumno_id = ?", Integer.class,
				efimero));
	}

	@Test
	void borrarUnaPreinscripcionConservaSusConsentimientosSinEnlace() {
		long plantilla = jdbc.queryForObject(
				"SELECT id FROM plantilla_preinscripcion WHERE deporte = 'TAEKWONDO' ORDER BY id LIMIT 1", Long.class);
		long pre = preinscripcion("V25-BORRADA", "FINALIZADA", sinTelefono2, "1990-01-01", true, "Firmante",
				"2025-09-13 10:00:00", plantilla, 1);
		jdbc.update("""
				INSERT INTO consentimiento (organizacion_id, alumno_id, tipo, otorgado, origen, preinscripcion_id)
				VALUES (1, ?, 'USO_IMAGEN', 1, 'PREINSCRIPCION', ?)
				""", sinTelefono2, pre);
		jdbc.update("DELETE FROM preinscripcion WHERE id = ?", pre);
		assertEquals(1, jdbc.queryForObject("""
				SELECT COUNT(*) FROM consentimiento WHERE alumno_id = ? AND preinscripcion_id IS NULL
				""", Integer.class, sinTelefono2));
		jdbc.update("DELETE FROM consentimiento WHERE alumno_id = ?", sinTelefono2);
	}

	private long alumno(long organizacion, int expediente, Integer telefono, Integer telefono2,
			String responsableNombre, String responsableNif) {
		jdbc.update("""
				INSERT INTO alumno (activo, apellidos, autorizacion_web, direccion, fecha_nacimiento, nombre,
				  numero_expediente, telefono, telefono2, tiene_derecho_examen, email, fecha_alta, organizacion_id,
				  responsable_legal_nombre, responsable_legal_nif)
				VALUES (1, 'Prueba', 1, 'Calle 1', '2012-03-01', 'Alumno', ?, ?, ?, 0, 'a@example.test', '2020-09-01', ?,
				  ?, ?)
				""", expediente, telefono, telefono2, organizacion, responsableNombre, responsableNif);
		return jdbc.queryForObject("SELECT id FROM alumno WHERE organizacion_id = ? AND numero_expediente = ?",
				Long.class, organizacion, expediente);
	}

	private long preinscripcion(String referencia, String estado, Long alumnoId, String nacimiento, boolean foto,
			String firmante, String creadaEn, long plantilla, long organizacion) {
		jdbc.update("""
				INSERT INTO preinscripcion (referencia, temporada, deporte, estado, nombre, apellidos, dni, identidad_hash,
				  fecha_nacimiento, direccion, telefono, email, consentimiento_fotografico, aceptacion_normas,
				  firmante_nombre, firma, plantilla_id, plantilla_snapshot, pdf_firmado, token_documento_hash,
				  creada_en, actualizada_en, alumno_id, organizacion_id, finalizada_en)
				VALUES (?, '2025-2026', 'TAEKWONDO', ?, 'Ana', 'Prueba', NULL, ?, ?, 'Calle 1', '600000000',
				  'ana@example.test', ?, 1, ?, x'00', ?, '{}', x'00', ?, ?, ?, ?, ?, NULL)
				""", referencia, estado, (referencia + "-hash").repeat(3), nacimiento, foto, firmante, plantilla,
				"f".repeat(64), creadaEn, creadaEn, alumnoId, organizacion);
		return jdbc.queryForObject("SELECT id FROM preinscripcion WHERE referencia = ?", Long.class, referencia);
	}

	private Map<String, Object> consentimiento(long preinscripcionId, String tipo) {
		return jdbc.queryForMap("SELECT * FROM consentimiento WHERE preinscripcion_id = ? AND tipo = ?",
				preinscripcionId, tipo);
	}

	private Map<String, Object> fila(long alumnoId) {
		return jdbc.queryForMap("SELECT * FROM alumno WHERE id = ?", alumnoId);
	}

	private int contarTutores(long alumnoId) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM tutor WHERE alumno_id = ?", Integer.class, alumnoId);
	}

	private String tipoColumna(String columna) {
		return jdbc.queryForObject("""
				SELECT DATA_TYPE FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'alumno' AND COLUMN_NAME = ?
				""", String.class, columna);
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
}
