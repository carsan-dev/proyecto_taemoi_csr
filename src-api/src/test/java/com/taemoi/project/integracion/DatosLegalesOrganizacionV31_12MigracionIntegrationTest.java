package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * V31_12 (pieza 279, recibo de pago): datos legales de Moiskimdo (organización 1) para la cabecera del recibo, sobre
 * una base en V31_11 con datos sintéticos. Solo datos e idempotente: rellena los campos vacíos (NULL, cadena vacía o
 * solo espacios) de la organización 1, no pisa los que ya tienen valor (incluidos correo y teléfono), no toca otras
 * escuelas, y no falla si la organización 1 no existe. Ejecuta {@code docs/multi-escuela/postcheck-recibo-279.sql}.
 */
@Tag("migracion")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DatosLegalesOrganizacionV31_12MigracionIntegrationTest extends MySqlIntegrationTest {

	private static final String ESQUEMA = "taemoi_v31_12_it";
	private static final String ESQUEMA_CONTACTO_CARGADO = "taemoi_v31_12_contacto_it";
	private static final String ESQUEMA_SIN_ORG_1 = "taemoi_v31_12_sin_org1_it";
	private static final String V31_12 = "db/migration/V31_12__datos_legales_organizacion_1.sql";

	private static final String NOMBRE_LEGAL = "Club Deportivo Moi's Kim do Taekwondo";
	private static final String CIF = "G90341173";
	private static final String DIRECCION = "Calle Parada de la Cigüeña, 36";
	private static final String CODIGO_POSTAL = "41806";
	private static final String PROVINCIA = "Sevilla";
	private static final String LOCALIDAD_PREVIA = "Localidad previa IT";
	private static final String EMAIL = "moiskimdotaekwondo@gmail.com";
	private static final String TELEFONO = "625753952";
	private static final String EMAIL_PREVIO = "previo.it@example.org";
	private static final String TELEFONO_PREVIO = "600000000";

	DataSource dataSource;
	JdbcTemplate jdbc;
	long otra;

	@BeforeAll
	void migrarConDatosSinteticos() {
		dataSource = recrear(ESQUEMA);
		jdbc = new JdbcTemplate(dataSource);
		flyway(dataSource, "31.11").migrate();

		// Moiskimdo antes de V31_12: vacíos de las tres formas (NULL, '' y espacios) y UN valor ya cargado a mano
		// (localidad), que no se debe pisar.
		jdbc.update("""
				UPDATE organizacion SET nombre_legal = NULL, cif = '', direccion = NULL, codigo_postal = '   ',
				  localidad = ?, provincia = NULL, email_contacto = NULL, telefono = NULL
				WHERE id = 1""", LOCALIDAD_PREVIA);
		jdbc.update("INSERT INTO organizacion (slug, nombre) VALUES ('it-v3112-otra', 'Escuela IT')");
		otra = jdbc.queryForObject("SELECT id FROM organizacion WHERE slug = 'it-v3112-otra'", Long.class);

		flyway(dataSource, "31.12").migrate();
	}

	@Test
	void llegaAV31_12() {
		assertEquals("31.12", jdbc.queryForObject(
				"SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1",
				String.class));
	}

	@Test
	void rellenaLosVaciosDeLaOrganizacionUno() {
		Map<String, Object> org = organizacion(1);
		assertEquals(NOMBRE_LEGAL, org.get("nombre_legal"), "NULL → valor");
		assertEquals(CIF, org.get("cif"), "'' → valor");
		assertEquals(DIRECCION, org.get("direccion"), "NULL → valor");
		assertEquals(CODIGO_POSTAL, org.get("codigo_postal"), "solo espacios → valor");
		assertEquals(PROVINCIA, org.get("provincia"));
		assertEquals(EMAIL, org.get("email_contacto"), "NULL → valor");
		assertEquals(TELEFONO, org.get("telefono"), "NULL → valor");
	}

	@Test
	void noPisaUnValorExistente() {
		assertEquals(LOCALIDAD_PREVIA, organizacion(1).get("localidad"));
	}

	@Test
	void noPisaCorreoNiTelefonoYaCargadosNiTocaOtrasEscuelas() throws Exception {
		DataSource cargados = recrear(ESQUEMA_CONTACTO_CARGADO);
		JdbcTemplate j = new JdbcTemplate(cargados);
		crearTablaOrganizacion(j);
		j.update("INSERT INTO organizacion (id, slug, nombre, email_contacto, telefono) VALUES (1, 'it-v3112-c', 'M', ?, ?)",
				EMAIL_PREVIO, TELEFONO_PREVIO);
		ejecutarV31_12(cargados);
		Map<String, Object> conservados = j.queryForMap("SELECT email_contacto, telefono FROM organizacion WHERE id = 1");
		assertEquals(EMAIL_PREVIO, conservados.get("email_contacto"));
		assertEquals(TELEFONO_PREVIO, conservados.get("telefono"));

		Map<String, Object> ajena = organizacion(otra);
		for (String columna : List.of("nombre_legal", "cif", "direccion", "codigo_postal", "localidad", "provincia",
				"email_contacto", "telefono")) {
			assertNull(ajena.get(columna), "otra escuela: " + columna);
		}
	}

	@Test
	void laDireccionSeGuardaEnUtf8SinDobleCodificacion() {
		Map<String, Object> medida = jdbc.queryForMap(
				"SELECT CHAR_LENGTH(direccion) AS caracteres, LENGTH(direccion) AS bytes, HEX(direccion) AS hex"
						+ " FROM organizacion WHERE id = 1");
		assertEquals(30L, ((Number) medida.get("caracteres")).longValue());
		assertEquals(32L, ((Number) medida.get("bytes")).longValue(), "ü y ñ, dos bytes cada una");
		assertEquals("43616C6C6520506172616461206465206C6120436967C3BC65C3B1612C203336", medida.get("hex"));
	}

	@Test
	void repetirlaNoCambiaNadaNiPisaLoCambiadoDespues() throws Exception {
		jdbc.update("UPDATE organizacion SET nombre_legal = 'Nombre legal cambiado IT' WHERE id = 1");
		List<Map<String, Object>> antes = jdbc.queryForList("SELECT * FROM organizacion ORDER BY id");
		ejecutarV31_12(dataSource);
		ejecutarV31_12(dataSource);
		assertEquals(antes, jdbc.queryForList("SELECT * FROM organizacion ORDER BY id"));
		jdbc.update("UPDATE organizacion SET nombre_legal = ? WHERE id = 1", NOMBRE_LEGAL);
	}

	@Test
	void sinOrganizacionUnoNoFalla() throws Exception {
		DataSource vacia = recrear(ESQUEMA_SIN_ORG_1);
		JdbcTemplate j = new JdbcTemplate(vacia);
		// Mismas columnas que V18; sin filas.
		crearTablaOrganizacion(j);
		j.update("INSERT INTO organizacion (id, slug, nombre) VALUES (2, 'it-v3112-solo-otra', 'Escuela IT')");
		ejecutarV31_12(vacia);
		assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM organizacion WHERE id = 1", Integer.class));
		assertNull(j.queryForObject("SELECT nombre_legal FROM organizacion WHERE id = 2", String.class));
	}

	@Test
	void postcheck_versionYDatosDeLaOrganizacionUno() throws Exception {
		List<List<Map<String, Object>>> postcheck = SedesVinculosMigracionIntegrationTest.ejecutarScriptDocs(dataSource,
				"postcheck-recibo-279.sql");
		assertEquals(3, postcheck.size());
		assertEquals(1L, ((Number) postcheck.get(0).get(0).get("flyway_31_12")).longValue());
		Map<String, Object> org = postcheck.get(1).get(0);
		assertEquals(1L, ((Number) org.get("id")).longValue());
		assertEquals(CIF, org.get("cif"));
		assertEquals(DIRECCION, org.get("direccion"));
		assertEquals(EMAIL, org.get("email_contacto"));
		assertEquals(TELEFONO, org.get("telefono"));
		Map<String, Object> comprobaciones = postcheck.get(2).get(0);
		assertEquals(1L, ((Number) comprobaciones.get("direccion_exacta")).longValue());
		assertEquals(30L, ((Number) comprobaciones.get("direccion_caracteres")).longValue());
		assertEquals(32L, ((Number) comprobaciones.get("direccion_bytes")).longValue());
	}

	// ------------------------------------------------------------------ utilidades

	private Map<String, Object> organizacion(long id) {
		return jdbc.queryForMap("""
				SELECT nombre_legal, cif, direccion, codigo_postal, localidad, provincia, email_contacto, telefono
				FROM organizacion WHERE id = ?""", id);
	}

	private static void crearTablaOrganizacion(JdbcTemplate j) {
		j.execute("""
				CREATE TABLE organizacion (
				  id BIGINT NOT NULL AUTO_INCREMENT, slug VARCHAR(60) NOT NULL, nombre VARCHAR(150) NOT NULL,
				  nombre_legal VARCHAR(200) NULL, cif VARCHAR(16) NULL, direccion VARCHAR(255) NULL,
				  localidad VARCHAR(100) NULL, provincia VARCHAR(100) NULL, codigo_postal VARCHAR(10) NULL,
				  email_contacto VARCHAR(180) NULL, telefono VARCHAR(20) NULL,
				  PRIMARY KEY (id)
				) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""");
	}

	private static void ejecutarV31_12(DataSource fuente) throws Exception {
		try (Connection conexion = fuente.getConnection()) {
			ScriptUtils.executeSqlScript(conexion,
					new EncodedResource(new ClassPathResource(V31_12), StandardCharsets.UTF_8));
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
		String url = "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306) + "/" + esquema
				+ "?useSSL=false&allowPublicKeyRetrieval=true";
		return new DriverManagerDataSource(url, "root", MYSQL.getPassword());
	}
}
