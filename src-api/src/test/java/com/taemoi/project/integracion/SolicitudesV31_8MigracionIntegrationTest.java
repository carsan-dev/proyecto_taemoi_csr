package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * V31_8 (pieza 6c-2, docs/multi-escuela/fase3-6c-solicitudes.md §6): tabla {@code solicitud}, vacía, con sus cuatro
 * columnas VIRTUAL de «una pendiente por recurso», y cuatro columnas al final de {@code movimiento_caja}
 * ({@code cobro_id}, {@code producto_alumno_id}, {@code metodo} y la VIRTUAL {@code cobro_devuelto_activo_id}), sobre una
 * base en V31_7 con cobros, entregas y movimientos de dos sedes y dos escuelas. Solo amplía: ninguna fila cambia salvo
 * {@code metodo} = EFECTIVO en todos los movimientos, y el saldo de cada sede es el mismo (también contando solo los
 * EFECTIVO, D6c-13). Repetirla, también tras un fallo a medias, no cambia nada. Ejecuta
 * {@code docs/multi-escuela/postcheck-v31_8.sql}. Datos inventados.
 */
@Tag("migracion")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SolicitudesV31_8MigracionIntegrationTest extends MySqlIntegrationTest {

	private static final String ESQUEMA = "taemoi_v31_8_it";
	private static final String V31_8 = "db/migration/V31_8__solicitudes_y_devoluciones.sql";
	private static final List<String> NUEVAS_MOVIMIENTO = List.of("cobro_id", "producto_alumno_id", "metodo",
			"cobro_devuelto_activo_id");
	private static final List<String> COLUMNAS_SOLICITUD = List.of("id", "organizacion_id", "sede_id", "tipo",
			"estado", "solicitado_por_usuario_id", "solicitado_en", "motivo", "resuelto_por_usuario_id", "resuelto_en",
			"respuesta", "cobro_id", "entrega_caja_id", "movimiento_caja_id", "producto_alumno_id", "producto_id",
			"alumno_id", "concepto", "importe", "motivo_deshacer", "encargado_en", "encargado_por_usuario_id",
			"recibido_en", "recibido_por_usuario_id", "entregado_en", "devolucion_pendiente",
			"movimiento_devolucion_id", "creado_en", "version", "cobro_pendiente_id", "entrega_pendiente_id",
			"movimiento_pendiente_id", "cargo_pendiente_id");
	private static final String SALDO_POR_SEDE = """
			SELECT s.id AS sede,
			  (SELECT COALESCE(SUM(c.importe), 0) FROM cobro c WHERE c.sede_id = s.id AND c.estado = 'ACTIVO'
			     AND c.metodo = 'EFECTIVO' AND (c.origen <> 'PROFESOR' OR c.entrega_caja_id IS NOT NULL))
			  + (SELECT COALESCE(SUM(m.importe), 0) FROM movimiento_caja m WHERE m.sede_id = s.id AND m.estado = 'ACTIVO'
			     %s) AS saldo
			FROM sede s ORDER BY s.id""";

	DataSource dataSource;
	JdbcTemplate jdbc;
	long umbrete, sedeB, otraSede, otra;
	long alumno, cargoPagado, cargoSinCobro, cobroTesoreria, cobroEntregado, entrega, movimientoSuelto, producto;
	List<String> columnasMovimientoAntes;
	List<Map<String, Object>> movimientosAntes;
	List<Map<String, Object>> cobrosAntes;
	List<Map<String, Object>> entregasAntes;
	List<Map<String, Object>> cargosAntes;
	List<Map<String, Object>> saldoAntes;

	@BeforeAll
	void migrar() {
		dataSource = recrear(ESQUEMA);
		jdbc = new JdbcTemplate(dataSource);
		flyway(dataSource, "31.7").migrate();
		umbrete = jdbc.queryForObject("SELECT MIN(id) FROM sede WHERE organizacion_id = 1", Long.class);
		jdbc.update("INSERT INTO sede (organizacion_id, nombre, activa) VALUES (1, 'Sede B V31_8', TRUE)");
		sedeB = jdbc.queryForObject("SELECT id FROM sede WHERE nombre = 'Sede B V31_8'", Long.class);
		jdbc.update("INSERT INTO organizacion (slug, nombre) VALUES ('otra-v31-8', 'Otra escuela V31_8')");
		otra = jdbc.queryForObject("SELECT id FROM organizacion WHERE slug = 'otra-v31-8'", Long.class);
		jdbc.update("INSERT INTO sede (organizacion_id, nombre, activa) VALUES (?, 'Sede otra V31_8', TRUE)", otra);
		otraSede = jdbc.queryForObject("SELECT id FROM sede WHERE nombre = 'Sede otra V31_8'", Long.class);

		jdbc.update("""
				INSERT INTO alumno (activo, apellidos, autorizacion_web, direccion, fecha_nacimiento, nombre,
				  numero_expediente, telefono, tiene_derecho_examen, organizacion_id, sede_principal_id)
				VALUES (1, 'Prueba', 1, 'Calle 1', '2015-01-01', 'Alumno V31_8', 93801, '600000000', 0, 1, ?)""",
				umbrete);
		alumno = jdbc.queryForObject("SELECT id FROM alumno WHERE numero_expediente = 93801", Long.class);
		jdbc.update("INSERT INTO producto (concepto, precio, organizacion_id) VALUES ('CAMISETA V31_8', 12.5, 1)");
		producto = jdbc.queryForObject("SELECT MAX(id) FROM producto", Long.class);
		cargoPagado = cargo(umbrete, true);
		cargoSinCobro = cargo(umbrete, true);
		long cargoProfesor = cargo(umbrete, true);
		long cargoB = cargo(sedeB, true);
		cobroTesoreria = cobro(1, cargoPagado, umbrete, "12.50", "EFECTIVO", "TESORERIA", null);
		jdbc.update("""
				INSERT INTO entrega_caja (organizacion_id, sede_id, importe_declarado, importe_contado, fecha)
				VALUES (1, ?, 12.50, 13.00, '2026-02-01 10:00:00')""", umbrete);
		entrega = jdbc.queryForObject("SELECT MAX(id) FROM entrega_caja", Long.class);
		cobroEntregado = cobro(1, cargoProfesor, umbrete, "12.50", "EFECTIVO", "PROFESOR", entrega);
		cobro(1, cargoB, sedeB, "12.50", "BIZUM", "TESORERIA", null);
		movimiento(1, umbrete, "SOBRANTE", "0.50", entrega, "ACTIVO");
		movimientoSuelto = movimiento(1, umbrete, "OTRO", "-2.00", null, "ACTIVO");
		movimiento(1, umbrete, "ERROR", "5.00", null, "ANULADO");
		movimiento(1, sedeB, "OTRO", "3.00", null, "ACTIVO");
		movimiento(otra, otraSede, "OTRO", "7.25", null, "ACTIVO");

		columnasMovimientoAntes = columnas("movimiento_caja");
		movimientosAntes = jdbc.queryForList("SELECT * FROM movimiento_caja ORDER BY id");
		cobrosAntes = jdbc.queryForList("SELECT * FROM cobro ORDER BY id");
		entregasAntes = jdbc.queryForList("SELECT * FROM entrega_caja ORDER BY id");
		cargosAntes = jdbc.queryForList("SELECT * FROM producto_alumno ORDER BY id");
		saldoAntes = jdbc.queryForList(SALDO_POR_SEDE.formatted(""));
		assertTrue(saldoAntes.stream().anyMatch(f -> ((BigDecimal) f.get("saldo")).signum() != 0));
		// Hasta V31_8: las migraciones posteriores tendrán su propio test.
		flyway(dataSource, "31.8").migrate();
	}

	@Test
	@Order(1)
	void llegaAV31_8() {
		assertEquals("31.8", jdbc.queryForObject(
				"SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1",
				String.class));
	}

	@Test
	@Order(2)
	void creaSolicitudVacia_conClavesVirtualesYFk() {
		assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM solicitud", Long.class));
		assertEquals(COLUMNAS_SOLICITUD, columnas("solicitud"));
		assertEquals(List.of("cargo_pendiente_id", "cobro_pendiente_id", "entrega_pendiente_id",
				"movimiento_pendiente_id"), jdbc.queryForList("""
						SELECT COLUMN_NAME FROM information_schema.COLUMNS
						WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'solicitud' AND EXTRA = 'VIRTUAL GENERATED'
						ORDER BY COLUMN_NAME""", String.class));
		assertEquals(List.of("uk_solicitud_cargo_pendiente:cargo_pendiente_id",
				"uk_solicitud_cobro_pendiente:cobro_pendiente_id",
				"uk_solicitud_entrega_pendiente:entrega_pendiente_id",
				"uk_solicitud_movimiento_pendiente:movimiento_pendiente_id"), unicas("solicitud"));
		assertEquals("decimal(10,2)", tipo("solicitud", "importe"));
		assertEquals("datetime(6)", tipo("solicitud", "solicitado_en"));
		assertEquals("tinyint(1)", tipo("solicitud", "devolucion_pendiente"));
		assertEquals("utf8mb4_0900_ai_ci", jdbc.queryForObject("""
				SELECT TABLE_COLLATION FROM information_schema.TABLES
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'solicitud'""", String.class));
		Map<String, String> fks = new LinkedHashMap<>();
		jdbc.queryForList("""
				SELECT k.COLUMN_NAME AS columna, k.REFERENCED_TABLE_NAME AS tabla, r.DELETE_RULE AS regla
				FROM information_schema.KEY_COLUMN_USAGE k
				JOIN information_schema.REFERENTIAL_CONSTRAINTS r
				  ON r.CONSTRAINT_SCHEMA = k.CONSTRAINT_SCHEMA AND r.CONSTRAINT_NAME = k.CONSTRAINT_NAME
				WHERE k.TABLE_SCHEMA = DATABASE() AND k.TABLE_NAME = 'solicitud' AND k.REFERENCED_TABLE_NAME IS NOT NULL
				ORDER BY k.COLUMN_NAME""").forEach(f -> fks.put((String) f.get("columna"), f.get("tabla") + " " + f.get("regla")));
		Map<String, String> esperadas = new LinkedHashMap<>();
		esperadas.put("alumno_id", "alumno SET NULL");
		esperadas.put("cobro_id", "cobro NO ACTION");
		esperadas.put("encargado_por_usuario_id", "usuario SET NULL");
		esperadas.put("entrega_caja_id", "entrega_caja NO ACTION");
		esperadas.put("movimiento_caja_id", "movimiento_caja NO ACTION");
		esperadas.put("movimiento_devolucion_id", "movimiento_caja NO ACTION");
		esperadas.put("organizacion_id", "organizacion NO ACTION");
		esperadas.put("producto_alumno_id", "producto_alumno SET NULL");
		esperadas.put("producto_id", "producto SET NULL");
		esperadas.put("recibido_por_usuario_id", "usuario SET NULL");
		esperadas.put("resuelto_por_usuario_id", "usuario SET NULL");
		esperadas.put("sede_id", "sede SET NULL");
		esperadas.put("solicitado_por_usuario_id", "usuario SET NULL");
		assertEquals(esperadas, fks);
	}

	@Test
	@Order(3)
	void movimientoCaja_cuatroColumnasAlFinal_metodoEfectivo_yNadaMasCambia() {
		List<String> esperadas = new ArrayList<>(columnasMovimientoAntes);
		esperadas.addAll(NUEVAS_MOVIMIENTO);
		assertEquals(esperadas, columnas("movimiento_caja"));
		assertEquals("varchar(20)", tipo("movimiento_caja", "metodo"));
		assertEquals("EFECTIVO", jdbc.queryForObject("""
				SELECT COLUMN_DEFAULT FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'movimiento_caja' AND COLUMN_NAME = 'metodo'
				  AND IS_NULLABLE = 'NO'""", String.class));
		assertEquals("VIRTUAL GENERATED", jdbc.queryForObject("""
				SELECT EXTRA FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
				  AND TABLE_NAME = 'movimiento_caja' AND COLUMN_NAME = 'cobro_devuelto_activo_id'""", String.class));
		assertEquals(List.of("uk_movimiento_caja_cobro_devuelto_activo:cobro_devuelto_activo_id"),
				unicas("movimiento_caja"));
		List<Map<String, Object>> ahora = jdbc.queryForList("SELECT * FROM movimiento_caja ORDER BY id");
		for (Map<String, Object> fila : ahora) {
			assertEquals("EFECTIVO", fila.get("metodo"), "movimiento " + fila.get("id"));
			assertNull(fila.get("cobro_id"));
			assertNull(fila.get("producto_alumno_id"));
			assertNull(fila.get("cobro_devuelto_activo_id"));
		}
		assertEquals(movimientosAntes, sinNuevas(ahora));
		assertEquals(cobrosAntes, jdbc.queryForList("SELECT * FROM cobro ORDER BY id"));
		assertEquals(entregasAntes, jdbc.queryForList("SELECT * FROM entrega_caja ORDER BY id"));
		assertEquals(cargosAntes, jdbc.queryForList("SELECT * FROM producto_alumno ORDER BY id"));
		assertEquals(List.of("cobro_id cobro NO ACTION", "producto_alumno_id producto_alumno SET NULL"),
				jdbc.queryForList("""
						SELECT CONCAT(k.COLUMN_NAME, ' ', k.REFERENCED_TABLE_NAME, ' ', r.DELETE_RULE)
						FROM information_schema.KEY_COLUMN_USAGE k
						JOIN information_schema.REFERENTIAL_CONSTRAINTS r
						  ON r.CONSTRAINT_SCHEMA = k.CONSTRAINT_SCHEMA AND r.CONSTRAINT_NAME = k.CONSTRAINT_NAME
						WHERE k.TABLE_SCHEMA = DATABASE() AND k.TABLE_NAME = 'movimiento_caja'
						  AND k.COLUMN_NAME IN ('cobro_id', 'producto_alumno_id') ORDER BY k.COLUMN_NAME""",
						String.class));
	}

	@Test
	@Order(4)
	void saldoPorSede_igualAntesYDespues() {
		assertEquals(saldoAntes, jdbc.queryForList(SALDO_POR_SEDE.formatted("")));
		assertEquals(saldoAntes, jdbc.queryForList(SALDO_POR_SEDE.formatted("AND m.metodo = 'EFECTIVO'")),
				"D6c-13: contando solo los EFECTIVO, el saldo de hoy no cambia");
	}

	@Test
	@Order(5)
	void postcheck_tablaVaciaColumnasYVersion() throws Exception {
		Map<String, Object> fila = postcheck();
		assertEquals(1L, numero(fila, "tabla_solicitud"));
		assertEquals(0L, numero(fila, "filas_solicitud"));
		assertEquals(4L, numero(fila, "columnas_virtuales_solicitud"));
		assertEquals(4L, numero(fila, "uk_solicitud"));
		assertEquals(13L, numero(fila, "fk_solicitud"));
		assertEquals(8L, numero(fila, "fk_solicitud_set_null"));
		assertEquals(5L, numero(fila, "fk_solicitud_sin_accion"));
		assertEquals(4L, numero(fila, "columnas_movimiento_caja"));
		assertEquals(1L, numero(fila, "metodo_no_nulo_efectivo"));
		assertEquals(1L, numero(fila, "uk_cobro_devuelto_activo"));
		assertEquals(8L, numero(fila, "fk_movimiento_caja"));
		assertEquals(4L, numero(fila, "fk_movimiento_caja_set_null"));
		assertEquals(4L, numero(fila, "fk_movimiento_caja_sin_accion"));
		assertEquals(1L, numero(fila, "flyway_31_8"));
		assertEquals(0L, numero(fila, "movimientos_no_efectivo"));
		assertEquals(0L, numero(fila, "sedes_con_saldo_distinto_solo_efectivo"));
		assertEquals(0L, numero(fila, "movimientos_con_cobro"));
		assertEquals(0L, numero(fila, "solicitudes_pendientes"));
	}

	@Test
	@Order(6)
	void clavesUnicas_unaPendientePorRecurso_ylasResueltasNoCuentan() {
		long primera = solicitud("REVERTIR_COBRO", "PENDIENTE", cobroTesoreria, null, null, cargoPagado);
		assertThrows(DuplicateKeyException.class,
				() -> solicitud("REVERTIR_COBRO", "PENDIENTE", cobroTesoreria, null, null, cargoPagado));
		assertThrows(DuplicateKeyException.class,
				() -> solicitud("DESHACER_COBRO_ENTREGADO", "PENDIENTE", cobroTesoreria, null, null, null),
				"una pendiente por cobro, sea del tipo que sea");
		jdbc.update("UPDATE solicitud SET estado = 'RETIRADA' WHERE id = ?", primera);
		solicitud("REVERTIR_COBRO", "PENDIENTE", cobroTesoreria, null, null, cargoPagado);

		// 191: una pendiente por cargo, también sin cobro.
		solicitud("REVERTIR_COBRO", "PENDIENTE", null, null, null, cargoSinCobro);
		assertThrows(DuplicateKeyException.class,
				() -> solicitud("REVERTIR_COBRO", "PENDIENTE", null, null, null, cargoSinCobro));
		solicitud("REVERTIR_COBRO", "DENEGADA", null, null, null, cargoSinCobro);

		solicitud("ANULAR_ENTREGA", "PENDIENTE", null, entrega, null, null);
		assertThrows(DuplicateKeyException.class, () -> solicitud("ANULAR_ENTREGA", "PENDIENTE", null, entrega, null,
				null));
		solicitud("ANULAR_MOVIMIENTO", "PENDIENTE", null, null, movimientoSuelto, null);
		assertThrows(DuplicateKeyException.class,
				() -> solicitud("ANULAR_MOVIMIENTO", "PENDIENTE", null, null, movimientoSuelto, null));

		// Un encargo no tiene clave: dos camisetas del mismo producto para el mismo alumno.
		jdbc.update("""
				INSERT INTO solicitud (organizacion_id, sede_id, tipo, estado, solicitado_en, motivo, producto_id)
				VALUES (1, ?, 'ENCARGO', 'PEDIDO', NOW(6), 'talla M', ?), (1, ?, 'ENCARGO', 'PEDIDO', NOW(6), 'talla M', ?)""",
				umbrete, producto, umbrete, producto);

		// D6c-11: una devolución ACTIVA por cobro.
		long devolucion = movimiento(1, umbrete, "DEVOLUCION", "-12.50", null, "ACTIVO");
		jdbc.update("UPDATE movimiento_caja SET cobro_id = ? WHERE id = ?", cobroTesoreria, devolucion);
		long segunda = movimiento(1, umbrete, "DEVOLUCION", "-12.50", null, "ACTIVO");
		assertThrows(DuplicateKeyException.class,
				() -> jdbc.update("UPDATE movimiento_caja SET cobro_id = ? WHERE id = ?", cobroTesoreria, segunda));
		jdbc.update("UPDATE movimiento_caja SET estado = 'ANULADO' WHERE id = ?", devolucion);
		jdbc.update("UPDATE movimiento_caja SET cobro_id = ? WHERE id = ?", cobroTesoreria, segunda);

		jdbc.update("DELETE FROM movimiento_caja WHERE id IN (?, ?)", devolucion, segunda);
		jdbc.update("DELETE FROM solicitud");
		assertEquals(0L, numero(postcheckSinError(), "solicitudes_pendientes"));
	}

	/** El código anterior inserta movimientos sin las columnas nuevas: quedan EFECTIVO y sin enlace. */
	@Test
	@Order(7)
	void insertarMovimientoSinLasColumnas_quedaEfectivo() {
		long id = movimiento(1, umbrete, "OTRO", "1.00", null, "ACTIVO");
		Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM movimiento_caja WHERE id = ?", id);
		assertEquals("EFECTIVO", fila.get("metodo"));
		assertNull(fila.get("cobro_id"));
		assertNull(fila.get("producto_alumno_id"));
		jdbc.update("DELETE FROM movimiento_caja WHERE id = ?", id);
	}

	@Test
	@Order(8)
	void borrarCargoOCuenta_dejaLasReferenciasANull() {
		jdbc.update("INSERT INTO usuario (email, nombre, apellidos, contrasena) VALUES "
				+ "('solicita@v31_8.test', 'Solicita', 'Prueba', 'x')");
		long cuenta = jdbc.queryForObject("SELECT id FROM usuario WHERE email = 'solicita@v31_8.test'", Long.class);
		long cargo = cargo(umbrete, true);
		jdbc.update("""
				INSERT INTO solicitud (organizacion_id, sede_id, tipo, estado, solicitado_por_usuario_id, solicitado_en,
				  motivo, producto_alumno_id, alumno_id)
				VALUES (1, ?, 'REVERTIR_COBRO', 'PENDIENTE', ?, NOW(6), 'motivo', ?, ?)""", umbrete, cuenta, cargo,
				alumno);
		long solicitud = jdbc.queryForObject("SELECT MAX(id) FROM solicitud", Long.class);
		long mov = movimiento(1, umbrete, "OTRO", "1.00", null, "ACTIVO");
		jdbc.update("UPDATE movimiento_caja SET producto_alumno_id = ? WHERE id = ?", cargo, mov);

		jdbc.update("DELETE FROM producto_alumno WHERE id = ?", cargo);
		jdbc.update("DELETE FROM usuario WHERE id = ?", cuenta);
		Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM solicitud WHERE id = ?", solicitud);
		assertNull(fila.get("producto_alumno_id"));
		assertNull(fila.get("cargo_pendiente_id"));
		assertNull(fila.get("solicitado_por_usuario_id"));
		assertNull(jdbc.queryForObject("SELECT producto_alumno_id FROM movimiento_caja WHERE id = ?", Long.class, mov));
		jdbc.update("DELETE FROM solicitud");
		jdbc.update("DELETE FROM movimiento_caja WHERE id = ?", mov);
	}

	@Test
	@Order(9)
	void repetirla_tambienTrasUnFalloAMedias_noCambiaNada() throws Exception {
		List<Map<String, Object>> movimientos = jdbc.queryForList("SELECT * FROM movimiento_caja ORDER BY id");
		List<String> esquemaSolicitud = columnas("solicitud");
		ejecutarV31_8();
		ejecutarV31_8();
		assertEquals(movimientos, jdbc.queryForList("SELECT * FROM movimiento_caja ORDER BY id"));
		assertEquals(esquemaSolicitud, columnas("solicitud"));
		assertEquals(13L, numero(postcheck(), "fk_solicitud"));
		assertEquals(8L, numero(postcheck(), "fk_movimiento_caja"));

		// Fallo a medias: ni solicitud ni nada nuevo en movimiento_caja. Las columnas se vuelven a añadir a mano con
		// ALGORITHM=INSTANT (la afirmación de coste de V31_8, en el MySQL 8.0 de los tests) y V31_8 completa el resto.
		jdbc.execute("DROP TABLE solicitud");
		jdbc.execute("ALTER TABLE movimiento_caja DROP FOREIGN KEY fk_movimiento_caja_cobro");
		jdbc.execute("ALTER TABLE movimiento_caja DROP FOREIGN KEY fk_movimiento_caja_producto_alumno");
		jdbc.execute("ALTER TABLE movimiento_caja DROP INDEX uk_movimiento_caja_cobro_devuelto_activo");
		jdbc.execute("ALTER TABLE movimiento_caja DROP INDEX idx_movimiento_caja_cobro");
		jdbc.execute("ALTER TABLE movimiento_caja DROP INDEX idx_movimiento_caja_producto_alumno");
		jdbc.execute("ALTER TABLE movimiento_caja DROP COLUMN cobro_devuelto_activo_id, DROP COLUMN metodo, "
				+ "DROP COLUMN producto_alumno_id, DROP COLUMN cobro_id");
		assertEquals(columnasMovimientoAntes, columnas("movimiento_caja"));
		jdbc.execute("ALTER TABLE movimiento_caja ADD COLUMN cobro_id BIGINT NULL, ALGORITHM=INSTANT");
		jdbc.execute("ALTER TABLE movimiento_caja ADD COLUMN producto_alumno_id BIGINT NULL, ALGORITHM=INSTANT");
		jdbc.execute("ALTER TABLE movimiento_caja ADD COLUMN metodo VARCHAR(20) NOT NULL DEFAULT 'EFECTIVO', "
				+ "ALGORITHM=INSTANT");
		jdbc.execute("ALTER TABLE movimiento_caja ADD COLUMN cobro_devuelto_activo_id BIGINT GENERATED ALWAYS AS "
				+ "(IF(estado = 'ACTIVO' AND tipo = 'DEVOLUCION', cobro_id, NULL)) VIRTUAL, ALGORITHM=INSTANT");
		ejecutarV31_8();
		ejecutarV31_8();
		Map<String, Object> fila = postcheck();
		assertEquals(1L, numero(fila, "tabla_solicitud"));
		assertEquals(4L, numero(fila, "uk_solicitud"));
		assertEquals(13L, numero(fila, "fk_solicitud"));
		assertEquals(4L, numero(fila, "columnas_movimiento_caja"));
		assertEquals(1L, numero(fila, "uk_cobro_devuelto_activo"));
		assertEquals(8L, numero(fila, "fk_movimiento_caja"));
		assertEquals(8L, numero(fila, "fk_solicitud_set_null"));
		assertEquals(5L, numero(fila, "fk_solicitud_sin_accion"));
		assertEquals(4L, numero(fila, "fk_movimiento_caja_set_null"));
		assertEquals(4L, numero(fila, "fk_movimiento_caja_sin_accion"));
		assertEquals(0L, numero(fila, "movimientos_no_efectivo"));
		assertEquals(0L, numero(fila, "sedes_con_saldo_distinto_solo_efectivo"));
		assertEquals(COLUMNAS_SOLICITUD, columnas("solicitud"));
		List<String> esperadas = new ArrayList<>(columnasMovimientoAntes);
		esperadas.addAll(NUEVAS_MOVIMIENTO);
		assertEquals(esperadas, columnas("movimiento_caja"));
		assertEquals(movimientosAntes, sinNuevas(jdbc.queryForList("SELECT * FROM movimiento_caja ORDER BY id")));
		assertEquals(saldoAntes, jdbc.queryForList(SALDO_POR_SEDE.formatted("")));
	}

	// ------------------------------------------------------------------ utilidades

	private long cargo(long sede, boolean pagado) {
		jdbc.update("""
				INSERT INTO producto_alumno (alumno_id, concepto, precio, cantidad, pagado, fecha_asignacion, fecha_pago,
				  organizacion_id, sede_id)
				VALUES (?, 'CAMISETA V31_8', 12.5, 1, ?, '2026-01-05', ?, 1, ?)""", alumno, pagado,
				pagado ? "2026-01-06 10:00:00" : null, sede);
		return jdbc.queryForObject("SELECT MAX(id) FROM producto_alumno", Long.class);
	}

	private long cobro(long org, long cargo, long sede, String importe, String metodo, String origen, Long entregaId) {
		jdbc.update("""
				INSERT INTO cobro (organizacion_id, producto_alumno_id, sede_id, importe, metodo, fecha_cobro, origen,
				  estado, entrega_caja_id)
				VALUES (?, ?, ?, ?, ?, '2026-01-06 10:00:00', ?, 'ACTIVO', ?)""", org, cargo, sede,
				new BigDecimal(importe), metodo, origen, entregaId);
		return jdbc.queryForObject("SELECT MAX(id) FROM cobro", Long.class);
	}

	private long movimiento(long org, long sede, String tipo, String importe, Long entregaId, String estado) {
		jdbc.update("""
				INSERT INTO movimiento_caja (organizacion_id, sede_id, tipo, importe, entrega_caja_id, nota, fecha, estado)
				VALUES (?, ?, ?, ?, ?, 'nota', '2026-02-01 10:00:00', ?)""", org, sede, tipo, new BigDecimal(importe),
				entregaId, estado);
		return jdbc.queryForObject("SELECT MAX(id) FROM movimiento_caja", Long.class);
	}

	private long solicitud(String tipo, String estado, Long cobro, Long entregaId, Long movimiento, Long cargo) {
		jdbc.update("""
				INSERT INTO solicitud (organizacion_id, sede_id, tipo, estado, solicitado_en, motivo, cobro_id,
				  entrega_caja_id, movimiento_caja_id, producto_alumno_id)
				VALUES (1, ?, ?, ?, NOW(6), 'motivo', ?, ?, ?, ?)""", umbrete, tipo, estado, cobro, entregaId,
				movimiento, cargo);
		return jdbc.queryForObject("SELECT MAX(id) FROM solicitud", Long.class);
	}

	private static List<Map<String, Object>> sinNuevas(List<Map<String, Object>> filas) {
		return filas.stream().map(f -> {
			Map<String, Object> copia = new LinkedHashMap<>(f);
			NUEVAS_MOVIMIENTO.forEach(copia::remove);
			return copia;
		}).toList();
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

	/** Claves únicas (salvo la primaria) como {@code nombre:columnas}. */
	private List<String> unicas(String tabla) {
		return jdbc.queryForList("""
				SELECT CONCAT(INDEX_NAME, ':', GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX))
				FROM information_schema.STATISTICS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND NON_UNIQUE = 0 AND INDEX_NAME <> 'PRIMARY'
				GROUP BY INDEX_NAME ORDER BY INDEX_NAME""", String.class, tabla);
	}

	private void ejecutarV31_8() throws Exception {
		try (Connection conexion = dataSource.getConnection()) {
			ScriptUtils.executeSqlScript(conexion,
					new EncodedResource(new ClassPathResource(V31_8), StandardCharsets.UTF_8));
		}
	}

	private Map<String, Object> postcheck() throws Exception {
		return SedesVinculosMigracionIntegrationTest.ejecutarScriptDocs(dataSource, "postcheck-v31_8.sql").get(0)
				.get(0);
	}

	private Map<String, Object> postcheckSinError() {
		try {
			return postcheck();
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

	private static DataSource fuente(String esquema) {
		return new DriverManagerDataSource("jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306) + "/"
				+ esquema + "?useSSL=false&allowPublicKeyRetrieval=true", "root", MYSQL.getPassword());
	}
}
