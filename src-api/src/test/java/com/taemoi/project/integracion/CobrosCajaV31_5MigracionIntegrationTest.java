package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * V31_5 (pieza 6b-1): tablas {@code cobro}, {@code entrega_caja} y {@code movimiento_caja}, vacías, sobre una base en
 * V31_4 con datos. Solo añade: no escribe filas ni toca las demás tablas; la clave única deja como mucho un cobro ACTIVO
 * por cargo; borrar el cargo deja el cobro sin cargo; repetirla (también tras un fallo a medias) crea solo lo que falta.
 * Ejecuta {@code docs/multi-escuela/postcheck-v31_5.sql}.
 */
@Tag("migracion")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CobrosCajaV31_5MigracionIntegrationTest extends MySqlIntegrationTest {

	private static final String ESQUEMA = "taemoi_v31_5_it";
	private static final String V31_5 = "db/migration/V31_5__cobros_y_caja.sql";

	DataSource dataSource;
	JdbcTemplate jdbc;
	long umbrete;
	long cargoPagado;
	long cargoPendiente;
	List<Map<String, Object>> cargosAntes;
	List<Map<String, Object>> alumnosAntes;
	List<Map<String, Object>> convocatoriasAntes;

	@BeforeAll
	void migrar() {
		dataSource = recrear(ESQUEMA);
		jdbc = new JdbcTemplate(dataSource);
		flyway(dataSource, "31.4").migrate();
		umbrete = jdbc.queryForObject("SELECT id FROM sede WHERE organizacion_id = 1", Long.class);
		jdbc.update("""
				INSERT INTO alumno (activo, apellidos, autorizacion_web, direccion, fecha_nacimiento, nombre,
				  numero_expediente, telefono, tiene_derecho_examen, organizacion_id, sede_principal_id)
				VALUES (1, 'Prueba', 1, 'Calle 1', '2015-01-01', 'Alumno V31_5', 93001, '600000000', 0, 1, ?)""",
				umbrete);
		long alumno = jdbc.queryForObject("SELECT id FROM alumno WHERE numero_expediente = 93001", Long.class);
		cargoPagado = cargo(alumno, true);
		cargoPendiente = cargo(alumno, false);
		cargosAntes = jdbc.queryForList("SELECT * FROM producto_alumno ORDER BY id");
		alumnosAntes = jdbc.queryForList("SELECT * FROM alumno ORDER BY id");
		convocatoriasAntes = jdbc.queryForList("SELECT * FROM alumno_convocatoria ORDER BY id");
		// Hasta V31_5: las migraciones posteriores tendrán su propio test.
		flyway(dataSource, "31.5").migrate();
	}

	@Test
	@Order(1)
	void llegaAV31_5() {
		assertEquals("31.5", jdbc.queryForObject(
				"SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1",
				String.class));
	}

	@Test
	@Order(2)
	void creaLasTresTablasVaciasSinRelleno() {
		assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM cobro", Long.class),
				"sin relleno: el cargo ya pagado no tiene cobro");
		assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM entrega_caja", Long.class));
		assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM movimiento_caja", Long.class));
		assertEquals(List.of("id", "organizacion_id", "producto_alumno_id", "sede_id", "alumno_id", "concepto",
				"importe", "metodo", "fecha_cobro", "cobrado_por_usuario_id", "origen", "estado",
				"anulado_por_usuario_id", "anulado_en", "motivo_anulacion", "entrega_caja_id", "creado_en", "version",
				"producto_alumno_activo_id"), columnas("cobro"));
		assertEquals("datetime(6)", tipo("cobro", "fecha_cobro"));
		assertEquals(tipo("producto_alumno", "fecha_pago"), tipo("cobro", "fecha_cobro"),
				"fecha_cobro tiene el tipo de fecha_pago");
		assertEquals("decimal(10,2)", tipo("cobro", "importe"));
		assertEquals("VIRTUAL GENERATED", jdbc.queryForObject("""
				SELECT EXTRA FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cobro'
				  AND COLUMN_NAME = 'producto_alumno_activo_id'""", String.class));
		assertEquals("utf8mb4_0900_ai_ci", jdbc.queryForObject("""
				SELECT TABLE_COLLATION FROM information_schema.TABLES
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cobro'""", String.class));
		assertEquals(List.of("fk_cobro_alumno", "fk_cobro_anulado_por", "fk_cobro_cobrado_por",
				"fk_cobro_entrega_caja", "fk_cobro_organizacion", "fk_cobro_producto_alumno", "fk_cobro_sede"),
				jdbc.queryForList("""
						SELECT CONSTRAINT_NAME FROM information_schema.REFERENTIAL_CONSTRAINTS
						WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'cobro' ORDER BY CONSTRAINT_NAME""",
						String.class));
		assertEquals("SET NULL", jdbc.queryForObject("""
				SELECT DELETE_RULE FROM information_schema.REFERENTIAL_CONSTRAINTS
				WHERE CONSTRAINT_SCHEMA = DATABASE() AND CONSTRAINT_NAME = 'fk_cobro_producto_alumno'""",
				String.class));
	}

	@Test
	@Order(3)
	void noTocaCargosAlumnosNiConvocatorias() {
		assertEquals(cargosAntes, jdbc.queryForList("SELECT * FROM producto_alumno ORDER BY id"));
		assertEquals(alumnosAntes, jdbc.queryForList("SELECT * FROM alumno ORDER BY id"));
		assertEquals(convocatoriasAntes, jdbc.queryForList("SELECT * FROM alumno_convocatoria ORDER BY id"));
	}

	@Test
	@Order(4)
	void postcheck_tablasVaciasYVersionAplicada() throws Exception {
		Map<String, Object> fila = SedesVinculosMigracionIntegrationTest
				.ejecutarScriptDocs(dataSource, "postcheck-v31_5.sql").get(0).get(0);
		assertEquals(3L, numero(fila, "tablas"));
		assertEquals(0L, numero(fila, "filas_cobro"));
		assertEquals(0L, numero(fila, "filas_entrega_caja"));
		assertEquals(0L, numero(fila, "filas_movimiento_caja"));
		assertEquals(7L, numero(fila, "columnas_clave_cobro"));
		assertEquals(1L, numero(fila, "uk_cobro_activo_por_cargo"));
		assertEquals(3L, numero(fila, "indices_cobro"));
		assertEquals(7L, numero(fila, "fk_cobro"));
		assertEquals(5L, numero(fila, "fk_entrega_caja"));
		assertEquals(6L, numero(fila, "fk_movimiento_caja"));
		assertEquals(1L, numero(fila, "flyway_31_5"));
		assertEquals(0L, numero(fila, "cargos_con_varios_activos"));
		assertEquals(0L, numero(fila, "cobros_activos_de_cargo_no_pagado"));
		assertEquals(0L, numero(fila, "cobros_con_sede_distinta"));
		assertEquals(0L, numero(fila, "cobros_de_otra_escuela"));
	}

	@Test
	@Order(5)
	void comoMuchoUnCobroActivoPorCargo() {
		long primero = cobro(cargoPagado, "ACTIVO");
		assertThrows(DuplicateKeyException.class, () -> cobro(cargoPagado, "ACTIVO"));
		// Los anulados no cuentan: varios anulados y uno activo conviven.
		cobro(cargoPagado, "ANULADO");
		cobro(cargoPagado, "ANULADO");
		jdbc.update("UPDATE cobro SET estado = 'ANULADO' WHERE id = ?", primero);
		cobro(cargoPagado, "ACTIVO");
		assertEquals(1L, jdbc.queryForObject(
				"SELECT COUNT(*) FROM cobro WHERE producto_alumno_id = ? AND estado = 'ACTIVO'", Long.class,
				cargoPagado));
		// Cargos distintos, un activo cada uno.
		cobro(cargoPendiente, "ACTIVO");
		jdbc.update("DELETE FROM cobro");
	}

	@Test
	@Order(6)
	void borrarElCargo_dejaElCobroSinCargo() {
		long alumno = jdbc.queryForObject("SELECT id FROM alumno WHERE numero_expediente = 93001", Long.class);
		long cargo = cargo(alumno, true);
		long cobro = cobro(cargo, "ACTIVO");
		jdbc.update("DELETE FROM producto_alumno WHERE id = ?", cargo);
		assertNull(jdbc.queryForObject("SELECT producto_alumno_id FROM cobro WHERE id = ?", Long.class, cobro));
		assertNull(jdbc.queryForObject("SELECT producto_alumno_activo_id FROM cobro WHERE id = ?", Long.class,
				cobro));
		jdbc.update("DELETE FROM cobro");
	}

	@Test
	@Order(7)
	void lasFkAUsuario_admitenNullYSonSetNull_borrarUnaCuentaConCajaNoFalla() {
		List<Map<String, Object>> fks = jdbc.queryForList("""
				SELECT k.TABLE_NAME AS tabla, k.COLUMN_NAME AS columna, r.DELETE_RULE AS regla, c.IS_NULLABLE AS nula
				FROM information_schema.KEY_COLUMN_USAGE k
				JOIN information_schema.REFERENTIAL_CONSTRAINTS r
				  ON r.CONSTRAINT_SCHEMA = k.CONSTRAINT_SCHEMA AND r.CONSTRAINT_NAME = k.CONSTRAINT_NAME
				JOIN information_schema.COLUMNS c
				  ON c.TABLE_SCHEMA = k.TABLE_SCHEMA AND c.TABLE_NAME = k.TABLE_NAME AND c.COLUMN_NAME = k.COLUMN_NAME
				WHERE k.TABLE_SCHEMA = DATABASE() AND k.REFERENCED_TABLE_NAME = 'usuario'
				  AND k.TABLE_NAME IN ('cobro', 'entrega_caja', 'movimiento_caja')
				ORDER BY k.TABLE_NAME, k.COLUMN_NAME""");
		assertEquals(List.of("cobro.anulado_por_usuario_id", "cobro.cobrado_por_usuario_id",
				"entrega_caja.anulada_por_usuario_id", "entrega_caja.entregado_por_usuario_id",
				"entrega_caja.recibido_por_usuario_id", "movimiento_caja.anulado_por_usuario_id",
				"movimiento_caja.creado_por_usuario_id"),
				fks.stream().map(f -> f.get("tabla") + "." + f.get("columna")).toList());
		fks.forEach(f -> {
			assertEquals("SET NULL", f.get("regla"), f.toString());
			assertEquals("YES", f.get("nula"), f.toString());
		});

		jdbc.update("INSERT INTO usuario (email, nombre, apellidos, contrasena) VALUES ('caja@v31_5.test', 'Caja', 'Prueba', 'x')");
		long cuenta = jdbc.queryForObject("SELECT id FROM usuario WHERE email = 'caja@v31_5.test'", Long.class);
		jdbc.update("""
				INSERT INTO entrega_caja (organizacion_id, sede_id, entregado_por_usuario_id, recibido_por_usuario_id,
				  importe_declarado, importe_contado, fecha) VALUES (1, ?, ?, ?, 5.00, 5.00, NOW(6))""",
				umbrete, cuenta, cuenta);
		long entrega = jdbc.queryForObject("SELECT MAX(id) FROM entrega_caja", Long.class);
		jdbc.update("""
				INSERT INTO movimiento_caja (organizacion_id, sede_id, tipo, importe, nota, creado_por_usuario_id, fecha)
				VALUES (1, ?, 'SOBRANTE', 1.00, 'nota', ?, NOW(6))""", umbrete, cuenta);
		long movimiento = jdbc.queryForObject("SELECT MAX(id) FROM movimiento_caja", Long.class);
		jdbc.update("DELETE FROM usuario WHERE id = ?", cuenta);
		Map<String, Object> filaEntrega = jdbc.queryForMap("SELECT * FROM entrega_caja WHERE id = ?", entrega);
		assertNull(filaEntrega.get("entregado_por_usuario_id"));
		assertNull(filaEntrega.get("recibido_por_usuario_id"));
		assertNull(jdbc.queryForObject("SELECT creado_por_usuario_id FROM movimiento_caja WHERE id = ?", Long.class,
				movimiento));
		jdbc.update("DELETE FROM movimiento_caja");
		jdbc.update("DELETE FROM entrega_caja");
	}

	@Test
	@Order(8)
	void repetirla_tambienTrasUnFalloAMedias_noCambiaNada() throws Exception {
		long cobro = cobro(cargoPagado, "ACTIVO");
		List<Map<String, Object>> antes = jdbc.queryForList("SELECT * FROM cobro ORDER BY id");
		ejecutarV31_5();
		assertEquals(antes, jdbc.queryForList("SELECT * FROM cobro ORDER BY id"));

		// Fallo a medias: solo llegó a crear entrega_caja (cada CREATE TABLE es atómico en MySQL 8).
		jdbc.update("DELETE FROM cobro WHERE id = ?", cobro);
		jdbc.execute("DROP TABLE movimiento_caja");
		jdbc.execute("DROP TABLE cobro");
		ejecutarV31_5();
		ejecutarV31_5();
		Map<String, Object> fila = SedesVinculosMigracionIntegrationTest
				.ejecutarScriptDocs(dataSource, "postcheck-v31_5.sql").get(0).get(0);
		assertEquals(3L, numero(fila, "tablas"));
		assertEquals(7L, numero(fila, "fk_cobro"));
		assertEquals(6L, numero(fila, "fk_movimiento_caja"));
		assertEquals(1L, numero(fila, "uk_cobro_activo_por_cargo"));
		assertEquals(cargosAntes, jdbc.queryForList("SELECT * FROM producto_alumno ORDER BY id"));
	}

	// ------------------------------------------------------------------ utilidades

	private long cargo(long alumno, boolean pagado) {
		jdbc.update("""
				INSERT INTO producto_alumno (alumno_id, concepto, precio, cantidad, pagado, fecha_asignacion, fecha_pago,
				  organizacion_id, sede_id)
				VALUES (?, 'CAMISETA V31_5', 12.5, 1, ?, '2026-01-05', ?, 1, ?)""", alumno, pagado,
				pagado ? "2026-01-06 10:00:00" : null, umbrete);
		return jdbc.queryForObject("SELECT MAX(id) FROM producto_alumno", Long.class);
	}

	private long cobro(long cargo, String estado) {
		jdbc.update("""
				INSERT INTO cobro (organizacion_id, producto_alumno_id, sede_id, importe, fecha_cobro, origen, estado)
				VALUES (1, ?, ?, 12.50, '2026-01-06 10:00:00', 'TESORERIA', ?)""", cargo, umbrete, estado);
		return jdbc.queryForObject("SELECT MAX(id) FROM cobro", Long.class);
	}

	private List<String> columnas(String tabla) {
		return jdbc.queryForList("""
				SELECT COLUMN_NAME FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? ORDER BY ORDINAL_POSITION""", String.class, tabla);
	}

	private String tipo(String tabla, String columna) {
		return jdbc.queryForObject("""
				SELECT COLUMN_TYPE FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?""", String.class, tabla, columna);
	}

	private void ejecutarV31_5() throws Exception {
		try (Connection conexion = dataSource.getConnection()) {
			ScriptUtils.executeSqlScript(conexion,
					new EncodedResource(new ClassPathResource(V31_5), StandardCharsets.UTF_8));
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

	private static DataSource fuente(String esquema) {
		String url = "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306) + "/" + esquema
				+ "?useSSL=false&allowPublicKeyRetrieval=true";
		return new DriverManagerDataSource(url, "root", MYSQL.getPassword());
	}
}
