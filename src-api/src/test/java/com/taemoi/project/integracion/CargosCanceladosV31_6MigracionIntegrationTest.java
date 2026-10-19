package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
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
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * V31_6 (pieza 6b-2c): columnas {@code cancelado_en}, {@code cancelado_por_usuario_id} y {@code motivo_cancelacion} en
 * {@code producto_alumno}, índice y FK a usuario ON DELETE SET NULL, sobre una base en V31_5 con cargos y cobros. Solo
 * añade (todas las filas quedan a NULL, nada más cambia); la FK se añade INPLACE con foreign_key_checks = 0 (sin
 * reconstruir la tabla) y deja la sesión como estaba; con huérfanos la valida MySQL; repetirla (también tras un fallo a
 * medias) crea solo lo que falta. Ejecuta {@code docs/multi-escuela/postcheck-v31_6.sql}.
 */
@Tag("migracion")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CargosCanceladosV31_6MigracionIntegrationTest extends MySqlIntegrationTest {

	private static final String ESQUEMA = "taemoi_v31_6_it";
	private static final String V31_6 = "db/migration/V31_6__cargos_cancelados.sql";
	private static final List<String> NUEVAS = List.of("cancelado_en", "cancelado_por_usuario_id", "motivo_cancelacion");

	DataSource dataSource;
	JdbcTemplate jdbc;
	long umbrete;
	long alumno;
	List<Map<String, Object>> cargosAntes;
	List<Map<String, Object>> cobrosAntes;
	List<String> columnasAntes;

	@BeforeAll
	void migrar() {
		dataSource = recrear(ESQUEMA);
		jdbc = new JdbcTemplate(dataSource);
		flyway(dataSource, "31.5").migrate();
		umbrete = jdbc.queryForObject("SELECT id FROM sede WHERE organizacion_id = 1", Long.class);
		jdbc.update("""
				INSERT INTO alumno (activo, apellidos, autorizacion_web, direccion, fecha_nacimiento, nombre,
				  numero_expediente, telefono, tiene_derecho_examen, organizacion_id, sede_principal_id)
				VALUES (1, 'Prueba', 1, 'Calle 1', '2015-01-01', 'Alumno V31_6', 93101, '600000000', 0, 1, ?)""",
				umbrete);
		alumno = jdbc.queryForObject("SELECT id FROM alumno WHERE numero_expediente = 93101", Long.class);
		long pagado = cargo(true);
		cargo(false);
		cargo(null);
		jdbc.update("""
				INSERT INTO cobro (organizacion_id, producto_alumno_id, sede_id, importe, fecha_cobro, origen, estado)
				VALUES (1, ?, ?, 12.50, '2026-01-06 10:00:00', 'TESORERIA', 'ACTIVO')""", pagado, umbrete);
		columnasAntes = columnas("producto_alumno");
		cargosAntes = jdbc.queryForList("SELECT * FROM producto_alumno ORDER BY id");
		cobrosAntes = jdbc.queryForList("SELECT * FROM cobro ORDER BY id");
		// Hasta V31_6: las migraciones posteriores tendrán su propio test.
		flyway(dataSource, "31.6").migrate();
	}

	@Test
	@Order(1)
	void llegaAV31_6() {
		assertEquals("31.6", jdbc.queryForObject(
				"SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1",
				String.class));
	}

	@Test
	@Order(2)
	void añadeLasTresColumnasNulasAlFinal_indiceYFkSetNull() {
		List<String> esperadas = new java.util.ArrayList<>(columnasAntes);
		esperadas.addAll(NUEVAS);
		assertEquals(esperadas, columnas("producto_alumno"));
		assertEquals("datetime(6)", tipo("cancelado_en"));
		assertEquals(tipo("fecha_pago"), tipo("cancelado_en"), "mismo tipo que fecha_pago");
		assertEquals("bigint", tipo("cancelado_por_usuario_id"));
		assertEquals("varchar(500)", tipo("motivo_cancelacion"));
		for (String columna : NUEVAS) {
			assertEquals("YES", jdbc.queryForObject("""
					SELECT IS_NULLABLE FROM information_schema.COLUMNS
					WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'producto_alumno' AND COLUMN_NAME = ?""",
					String.class, columna), columna);
		}
		assertEquals(List.of("cancelado_por_usuario_id"), jdbc.queryForList("""
				SELECT COLUMN_NAME FROM information_schema.STATISTICS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'producto_alumno'
				  AND INDEX_NAME = 'idx_producto_alumno_cancelado_por'""", String.class));
		Map<String, Object> fk = jdbc.queryForMap("""
				SELECT r.DELETE_RULE AS regla, k.COLUMN_NAME AS columna, k.REFERENCED_TABLE_NAME AS tabla
				FROM information_schema.REFERENTIAL_CONSTRAINTS r
				JOIN information_schema.KEY_COLUMN_USAGE k
				  ON k.CONSTRAINT_SCHEMA = r.CONSTRAINT_SCHEMA AND k.CONSTRAINT_NAME = r.CONSTRAINT_NAME
				WHERE r.CONSTRAINT_SCHEMA = DATABASE() AND r.CONSTRAINT_NAME = 'fk_producto_alumno_cancelado_por'""");
		assertEquals("SET NULL", fk.get("regla"));
		assertEquals("cancelado_por_usuario_id", fk.get("columna"));
		assertEquals("usuario", fk.get("tabla"));
	}

	@Test
	@Order(3)
	void noCambiaNingunaFila_lasColumnasNuevasQuedanANull() {
		List<Map<String, Object>> ahora = jdbc.queryForList("SELECT * FROM producto_alumno ORDER BY id");
		assertEquals(cargosAntes.size(), ahora.size());
		assertTrue(ahora.size() >= 3);
		for (Map<String, Object> fila : ahora) {
			for (String columna : NUEVAS) {
				assertNull(fila.get(columna), columna);
			}
		}
		assertEquals(cargosAntes, sinNuevas(ahora));
		assertEquals(cobrosAntes, jdbc.queryForList("SELECT * FROM cobro ORDER BY id"));
	}

	@Test
	@Order(4)
	void postcheck_columnasIndiceFkYVersion_sinCancelados() throws Exception {
		Map<String, Object> fila = postcheck();
		assertEquals(3L, numero(fila, "columnas_cancelacion"));
		assertEquals(3L, numero(fila, "columnas_nulables"));
		assertEquals(1L, numero(fila, "idx_cancelado_por"));
		assertEquals(1L, numero(fila, "fk_cancelado_por"));
		assertEquals(1L, numero(fila, "fk_cancelado_por_set_null"));
		assertEquals(1L, numero(fila, "flyway_31_6"));
		assertEquals(0L, numero(fila, "cargos_cancelados"));
		assertEquals(0L, numero(fila, "cancelados_pagados"));
		assertEquals(0L, numero(fila, "cancelados_con_cobro_activo"));
		assertEquals(0L, numero(fila, "cancelados_sin_motivo"));
		assertEquals(0L, numero(fila, "datos_sin_cancelar"));
	}

	@Test
	@Order(5)
	void borrarLaCuentaQueCancelo_dejaElCargoSinQuien() throws Exception {
		jdbc.update("INSERT INTO usuario (email, nombre, apellidos, contrasena) VALUES ('cancela@v31_6.test', 'C', 'P', 'x')");
		long cuenta = jdbc.queryForObject("SELECT id FROM usuario WHERE email = 'cancela@v31_6.test'", Long.class);
		long cargo = cargo(false);
		jdbc.update("""
				UPDATE producto_alumno SET cancelado_en = NOW(6), cancelado_por_usuario_id = ?, motivo_cancelacion = 'm'
				WHERE id = ?""", cuenta, cargo);
		assertEquals(1L, numero(postcheck(), "cargos_cancelados"));
		jdbc.update("DELETE FROM usuario WHERE id = ?", cuenta);
		assertNull(jdbc.queryForObject("SELECT cancelado_por_usuario_id FROM producto_alumno WHERE id = ?", Long.class,
				cargo));
		// Sin cuenta que exista, la FK rechaza el id.
		assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update(
				"UPDATE producto_alumno SET cancelado_por_usuario_id = 987654321 WHERE id = ?", cargo));
		jdbc.update("DELETE FROM producto_alumno WHERE id = ?", cargo);
	}

	/**
	 * La afirmación de coste de la migración, comprobada en el MySQL de los tests (8.0, como producción): con
	 * foreign_key_checks = 0 el ADD FOREIGN KEY admite INPLACE (sin reconstruir la tabla); con 1, MySQL lo rechaza
	 * (solo admite COPY).
	 */
	@Test
	@Order(6)
	void laFkSeAñadeInplaceSoloConForeignKeyChecksA0() {
		SingleConnectionDataSource una = new SingleConnectionDataSource(fuenteUrl(ESQUEMA), "root", MYSQL.getPassword(),
				true);
		try {
			JdbcTemplate sesion = new JdbcTemplate(una);
			sesion.execute("ALTER TABLE producto_alumno DROP FOREIGN KEY fk_producto_alumno_cancelado_por");
			String inplace = "ALTER TABLE producto_alumno ADD CONSTRAINT fk_producto_alumno_cancelado_por "
					+ "FOREIGN KEY (cancelado_por_usuario_id) REFERENCES usuario(id) ON DELETE SET NULL, ALGORITHM=INPLACE";
			sesion.execute("SET SESSION foreign_key_checks = 1");
			assertThrows(org.springframework.dao.DataAccessException.class, () -> sesion.execute(inplace),
					"con foreign_key_checks = 1 no hay INPLACE");
			sesion.execute("SET SESSION foreign_key_checks = 0");
			sesion.execute(inplace);
			sesion.execute("SET SESSION foreign_key_checks = 1");
		} finally {
			una.destroy();
		}
		assertEquals(1L, numero(postcheckSinError(), "fk_cancelado_por"));
	}

	@Test
	@Order(7)
	void repetirla_tambienTrasUnFalloAMedias_creaSoloLoQueFalta_yRestauraForeignKeyChecks() throws Exception {
		List<Map<String, Object>> antes = jdbc.queryForList("SELECT * FROM producto_alumno ORDER BY id");
		assertEquals(1L, ejecutarV31_6EnUnaSesion(), "deja foreign_key_checks = 1 en la sesión");
		assertEquals(antes, jdbc.queryForList("SELECT * FROM producto_alumno ORDER BY id"));

		// Fallo a medias: solo llegó a crear cancelado_en (ni las otras dos columnas, ni el índice, ni la FK).
		jdbc.execute("ALTER TABLE producto_alumno DROP FOREIGN KEY fk_producto_alumno_cancelado_por");
		jdbc.execute("ALTER TABLE producto_alumno DROP COLUMN motivo_cancelacion");
		jdbc.execute("ALTER TABLE producto_alumno DROP INDEX idx_producto_alumno_cancelado_por");
		jdbc.execute("ALTER TABLE producto_alumno DROP COLUMN cancelado_por_usuario_id");
		assertEquals(1L, ejecutarV31_6EnUnaSesion());
		assertEquals(1L, ejecutarV31_6EnUnaSesion());
		Map<String, Object> fila = postcheck();
		assertEquals(3L, numero(fila, "columnas_cancelacion"));
		assertEquals(1L, numero(fila, "idx_cancelado_por"));
		assertEquals(1L, numero(fila, "fk_cancelado_por_set_null"));
		assertEquals(0L, numero(fila, "cargos_cancelados"));
		List<String> esperadas = new java.util.ArrayList<>(columnasAntes);
		esperadas.addAll(NUEVAS);
		assertEquals(esperadas, columnas("producto_alumno"));
		assertEquals(cargosAntes, sinNuevas(jdbc.queryForList("SELECT * FROM producto_alumno ORDER BY id")));
	}

	/** Si alguien hubiera escrito un id de cuenta inexistente, la FK se añade validando (y la migración falla). */
	@Test
	@Order(8)
	void conHuerfanos_laFkSeValidaYFalla() {
		long cargo = cargo(false);
		jdbc.execute("ALTER TABLE producto_alumno DROP FOREIGN KEY fk_producto_alumno_cancelado_por");
		jdbc.update("UPDATE producto_alumno SET cancelado_por_usuario_id = 987654321 WHERE id = ?", cargo);
		assertThrows(Exception.class, this::ejecutarV31_6EnUnaSesion);
		assertEquals(0L, numero(postcheckSinError(), "fk_cancelado_por"));
		jdbc.update("DELETE FROM producto_alumno WHERE id = ?", cargo);
		assertEquals(1L, assertDoesNotThrowLong(this::ejecutarV31_6EnUnaSesion));
		assertEquals(1L, numero(postcheckSinError(), "fk_cancelado_por"));
	}

	// ------------------------------------------------------------------ utilidades

	private long cargo(Boolean pagado) {
		jdbc.update("""
				INSERT INTO producto_alumno (alumno_id, concepto, precio, cantidad, pagado, fecha_asignacion, fecha_pago,
				  organizacion_id, sede_id)
				VALUES (?, 'CAMISETA V31_6', 12.5, 1, ?, '2026-01-05', ?, 1, ?)""", alumno, pagado,
				Boolean.TRUE.equals(pagado) ? "2026-01-06 10:00:00" : null, umbrete);
		return jdbc.queryForObject("SELECT MAX(id) FROM producto_alumno", Long.class);
	}

	private static List<Map<String, Object>> sinNuevas(List<Map<String, Object>> filas) {
		return filas.stream().map(f -> {
			Map<String, Object> copia = new LinkedHashMap<>(f);
			NUEVAS.forEach(copia::remove);
			return copia;
		}).toList();
	}

	private List<String> columnas(String tabla) {
		return jdbc.queryForList("""
				SELECT COLUMN_NAME FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? ORDER BY ORDINAL_POSITION""", String.class, tabla);
	}

	private String tipo(String columna) {
		return jdbc.queryForObject("""
				SELECT COLUMN_TYPE FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'producto_alumno' AND COLUMN_NAME = ?""", String.class,
				columna);
	}

	private Map<String, Object> postcheck() throws Exception {
		return SedesVinculosMigracionIntegrationTest.ejecutarScriptDocs(dataSource, "postcheck-v31_6.sql").get(0)
				.get(0);
	}

	private Map<String, Object> postcheckSinError() {
		try {
			return postcheck();
		} catch (Exception e) {
			throw new AssertionError(e);
		}
	}

	/** Ejecuta V31_6 en UNA conexión y devuelve su foreign_key_checks al terminar. */
	private long ejecutarV31_6EnUnaSesion() throws Exception {
		try (Connection conexion = dataSource.getConnection()) {
			ScriptUtils.executeSqlScript(conexion,
					new EncodedResource(new ClassPathResource(V31_6), StandardCharsets.UTF_8));
			try (var st = conexion.createStatement(); var rs = st.executeQuery("SELECT @@SESSION.foreign_key_checks")) {
				rs.next();
				return rs.getLong(1);
			}
		}
	}

	private interface ConExcepcion {
		long get() throws Exception;
	}

	private static long assertDoesNotThrowLong(ConExcepcion accion) {
		try {
			return accion.get();
		} catch (Exception e) {
			throw new AssertionError(e);
		}
	}

	private static long numero(Map<String, Object> fila, String columna) {
		return ((Number) fila.get(columna)).longValue();
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

	private static String fuenteUrl(String esquema) {
		return "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306) + "/" + esquema
				+ "?useSSL=false&allowPublicKeyRetrieval=true";
	}

	private static DataSource fuente(String esquema) {
		return new DriverManagerDataSource(fuenteUrl(esquema), "root", MYSQL.getPassword());
	}
}
