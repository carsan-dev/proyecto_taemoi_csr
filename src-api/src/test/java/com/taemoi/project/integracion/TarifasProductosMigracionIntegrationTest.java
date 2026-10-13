package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import com.taemoi.project.entities.TipoGrado;
import com.taemoi.project.entities.TipoTarifa;

/**
 * V24 sobre datos con la forma de producción: esquema propio migrado hasta V23, productos con
 * ids propios (no consecutivos), conceptos repetidos, variantes no exactas (mayúsculas, espacios),
 * productos desconocidos, inscripciones con cada {@link TipoTarifa} y una segunda organización.
 * Se migra a la última versión y se repite V24. V24 solo amplía.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TarifasProductosMigracionIntegrationTest extends MySqlIntegrationTest {

	private static final String ESQUEMA = "taemoi_tarifas_it";
	private static final long ORG2 = 2L;

	/** concepto -> {codigo, tipo, disciplina}: los 40 nombres que el código busca hoy. */
	static final Map<String, String[]> CONOCIDOS = new LinkedHashMap<>();

	static {
		CONOCIDOS.put("MENSUALIDAD", new String[] { "MENSUALIDAD", "MENSUALIDAD", null });
		CONOCIDOS.put("RESERVA DE PLAZA", new String[] { "RESERVA_PLAZA", "RESERVA_PLAZA", null });
		CONOCIDOS.put("LICENCIA FEDERATIVA", new String[] { "LICENCIA_FEDERATIVA", "LICENCIA", null });
		CONOCIDOS.put("LICENCIA FEDERATIVA TAEKWONDO ADULTO", new String[] { "LICENCIA_TAEKWONDO_ADULTO", "LICENCIA", "TAEKWONDO" });
		CONOCIDOS.put("LICENCIA FEDERATIVA TAEKWONDO INFANTIL", new String[] { "LICENCIA_TAEKWONDO_INFANTIL", "LICENCIA", "TAEKWONDO" });
		CONOCIDOS.put("LICENCIA FEDERATIVA TAEKWONDO DISCAPACIDAD", new String[] { "LICENCIA_TAEKWONDO_DISCAPACIDAD", "LICENCIA", "TAEKWONDO" });
		CONOCIDOS.put("PARTE PROPORCIONAL LICENCIA FEDERATIVA TAEKWONDO ADULTO", new String[] { "LICENCIA_TAEKWONDO_ADULTO_PROPORCIONAL", "LICENCIA", "TAEKWONDO" });
		CONOCIDOS.put("PARTE PROPORCIONAL LICENCIA FEDERATIVA TAEKWONDO INFANTIL", new String[] { "LICENCIA_TAEKWONDO_INFANTIL_PROPORCIONAL", "LICENCIA", "TAEKWONDO" });
		CONOCIDOS.put("PARTE PROPORCIONAL LICENCIA FEDERATIVA TAEKWONDO DISCAPACIDAD", new String[] { "LICENCIA_TAEKWONDO_DISCAPACIDAD_PROPORCIONAL", "LICENCIA", "TAEKWONDO" });
		CONOCIDOS.put("LICENCIA FEDERATIVA KICKBOXING ADULTO", new String[] { "LICENCIA_KICKBOXING_ADULTO", "LICENCIA", "KICKBOXING" });
		CONOCIDOS.put("LICENCIA FEDERATIVA KICKBOXING INFANTIL", new String[] { "LICENCIA_KICKBOXING_INFANTIL", "LICENCIA", "KICKBOXING" });
		CONOCIDOS.put("TARIFA COMPETIDOR TAEKWONDO", new String[] { "COMPETIDOR_TAEKWONDO", "COMPETIDOR", "TAEKWONDO" });
		CONOCIDOS.put("TARIFA COMPETIDOR KICKBOXING", new String[] { "COMPETIDOR_KICKBOXING", "COMPETIDOR", "KICKBOXING" });
		for (TipoGrado g : TipoGrado.values()) {
			if (g.tieneProductoExamen()) {
				CONOCIDOS.put(g.obtenerNombreProducto(false), new String[] { "EXAMEN_" + g.name(), "EXAMEN", null });
			}
		}
		CONOCIDOS.put("DERECHO A EXAMEN ROJO BORDADO", new String[] { "EXAMEN_ROJO_BORDADO", "EXAMEN", null });
		for (TipoGrado g : TipoGrado.values()) {
			if (g.tieneProductoRecompensa()) {
				CONOCIDOS.put(g.obtenerNombreProducto(true), new String[] { "RECOMPENSA_" + g.name(), "RECOMPENSA", null });
			}
		}
		CONOCIDOS.put("DERECHO DE CAMBIO A ROJO BORDADO POR RECOMPENSA",
				new String[] { "RECOMPENSA_ROJO_BORDADO", "RECOMPENSA", null });
	}

	/** Cuantías de TarifaConfig (fuente: valores-actuales.md §6). */
	private static final Map<String, String> IMPORTES = Map.of("ADULTO", "30.00", "ADULTO_GRUPO", "20.00",
			"FAMILIAR", "0.00", "INFANTIL", "28.00", "INFANTIL_GRUPO", "20.00", "HERMANOS", "26.00", "PADRES_HIJOS",
			"0.00", "KICKBOXING", "30.00", "PILATES", "30.00", "DEFENSA_PERSONAL_FEMENINA", "25.00");

	DataSource dataSource;
	JdbcTemplate jdbc;

	/** concepto conocido -> id del producto que debe quedar clasificado. */
	final Map<String, Long> canonico = new LinkedHashMap<>();
	/** Productos que deben quedar OTRO / codigo NULL. */
	final List<Long> sinClasificar = new ArrayList<>();
	long duplicadoMensualidadMayor;
	long duplicadoAmarilloMayor;
	long varianteMinusculas;
	long varianteEspacioFinal;
	long varianteDobleEspacio;
	long productoOrg2;
	long alumnoSinTarifa;
	long alumnoOrg2;

	@BeforeAll
	void migrarDesdeV23ConDatos() {
		JdbcTemplate root = new JdbcTemplate(fuente(""));
		root.execute("DROP DATABASE IF EXISTS " + ESQUEMA);
		root.execute("CREATE DATABASE " + ESQUEMA + " CHARACTER SET utf8mb4");
		dataSource = fuente(ESQUEMA);
		jdbc = new JdbcTemplate(dataSource);

		flyway("23").migrate();
		insertarDatosV23();
		flyway(null).migrate();
	}

	@Test
	void llegaAV24() {
		assertEquals(1, jdbc.queryForObject(
				"SELECT COUNT(*) FROM flyway_schema_history WHERE version = '24' AND success = 1", Integer.class));
	}

	// ---------------- tarifa ----------------

	@Test
	void tarifasDeTaemoi_igualesQueTarifaConfig_enSuOrden() {
		List<Map<String, Object>> filas = jdbc.queryForList("""
				SELECT t.*, d.codigo AS disciplina FROM tarifa t LEFT JOIN disciplina d ON d.id = t.disciplina_id
				WHERE t.organizacion_id = 1 ORDER BY t.id""");
		assertEquals(List.of("FAMILIAR", "PADRES_HIJOS", "ADULTO_GRUPO", "INFANTIL_GRUPO", "HERMANOS", "INFANTIL",
				"ADULTO", "KICKBOXING", "PILATES", "DEFENSA_PERSONAL_FEMENINA"),
				filas.stream().map(f -> f.get("codigo")).toList());
		Set<String> enum_ = new HashSet<>();
		for (TipoTarifa t : TipoTarifa.values()) {
			enum_.add(t.name());
		}
		assertEquals(enum_, new HashSet<>(filas.stream().map(f -> (String) f.get("codigo")).toList()));
		for (Map<String, Object> f : filas) {
			String codigo = (String) f.get("codigo");
			assertEquals(new BigDecimal(IMPORTES.get(codigo)), f.get("importe"), codigo);
			if (codigo.equals("PADRES_HIJOS")) {
				assertEquals(new BigDecimal("28.00"), f.get("importe_adulto"));
				assertEquals(new BigDecimal("26.00"), f.get("importe_menor"));
			} else {
				assertNull(f.get("importe_adulto"), codigo);
				assertNull(f.get("importe_menor"), codigo);
			}
			String disciplina = switch (codigo) {
			case "KICKBOXING", "PILATES", "DEFENSA_PERSONAL_FEMENINA" -> codigo;
			default -> "TAEKWONDO";
			};
			assertEquals(disciplina, f.get("disciplina"), codigo);
			assertEquals(Boolean.TRUE, f.get("activa"), codigo);
		}
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM tarifa WHERE organizacion_id <> 1", Integer.class));
		assertTrue(existeUnico("tarifa", "uk_tarifa_organizacion_codigo", "organizacion_id,codigo"));
		assertTrue(existeFk("tarifa", "fk_tarifa_disciplina", "disciplina"));
		assertTrue(existeFk("tarifa", "fk_tarifa_organizacion", "organizacion"));
	}

	@Test
	void alumnoDeporteTarifaId_rellenadaPorCodigo_yTipoTarifaSeConserva() {
		List<Map<String, Object>> filas = jdbc.queryForList("""
				SELECT ad.id, ad.organizacion_id, ad.tipo_tarifa, ad.tarifa_id, t.codigo, t.organizacion_id AS org_tarifa
				FROM alumno_deporte ad LEFT JOIN tarifa t ON t.id = ad.tarifa_id""");
		int conTarifa = 0;
		for (Map<String, Object> f : filas) {
			long org = ((Number) f.get("organizacion_id")).longValue();
			if (f.get("tipo_tarifa") == null || org != 1L) {
				assertNull(f.get("tarifa_id"), "sin tipo_tarifa u otra organización: " + f);
			} else {
				assertEquals(f.get("tipo_tarifa"), f.get("codigo"), f.toString());
				assertEquals(1L, ((Number) f.get("org_tarifa")).longValue());
				conTarifa++;
			}
		}
		assertEquals(TipoTarifa.values().length, conTarifa);
		assertNull(jdbc.queryForObject("SELECT tipo_tarifa FROM alumno_deporte WHERE alumno_id = ?", String.class,
				alumnoSinTarifa));
		assertEquals("ADULTO", jdbc.queryForObject("SELECT tipo_tarifa FROM alumno_deporte WHERE alumno_id = ?",
				String.class, alumnoOrg2));
		assertTrue(existeIndice("alumno_deporte", "idx_alumno_deporte_tarifa"));
		assertTrue(existeFk("alumno_deporte", "fk_alumno_deporte_tarifa", "tarifa"));
		assertEquals("YES", nullable("alumno_deporte", "tarifa_id"));
	}

	// ---------------- producto ----------------

	@Test
	void productosConocidos_clasificadosPorConceptoExacto_elDeMenorId() {
		for (Map.Entry<String, String[]> e : CONOCIDOS.entrySet()) {
			long id = canonico.get(e.getKey());
			Map<String, Object> p = jdbc.queryForMap("""
					SELECT p.concepto, p.codigo, p.tipo, d.codigo AS disciplina, p.organizacion_id
					FROM producto p LEFT JOIN disciplina d ON d.id = p.disciplina_id WHERE p.id = ?""", id);
			assertEquals(e.getKey(), p.get("concepto"));
			assertEquals(e.getValue()[0], p.get("codigo"), e.getKey());
			assertEquals(e.getValue()[1], p.get("tipo"), e.getKey());
			assertEquals(e.getValue()[2], p.get("disciplina"), e.getKey());
		}
		assertEquals(CONOCIDOS.size(), jdbc.queryForObject(
				"SELECT COUNT(*) FROM producto WHERE organizacion_id = 1 AND codigo IS NOT NULL", Integer.class));
		assertEquals(40, CONOCIDOS.size());
	}

	@Test
	void repetidosVariantesYDesconocidos_quedanOtroSinCodigo() {
		assertTrue(sinClasificar.size() >= 7);
		for (long id : sinClasificar) {
			Map<String, Object> p = jdbc.queryForMap("SELECT * FROM producto WHERE id = ?", id);
			assertNull(p.get("codigo"), p.toString());
			assertEquals("OTRO", p.get("tipo"), p.toString());
			assertNull(p.get("disciplina_id"), p.toString());
		}
		// Repetido con id menor que el original: gana el de menor id.
		assertTrue(canonico.get("DERECHO A EXAMEN AMARILLO") < duplicadoAmarilloMayor);
		assertTrue(canonico.get("MENSUALIDAD") < duplicadoMensualidadMayor);
		// Otra organización: nada clasificado.
		assertEquals(0, jdbc.queryForObject(
				"SELECT COUNT(*) FROM producto WHERE organizacion_id = ? AND (codigo IS NOT NULL OR tipo <> 'OTRO')",
				Integer.class, ORG2));
	}

	@Test
	void columnasDeProducto_tipoPorDefectoOtro_yEscritoresActualesSiguenInsertando() {
		assertEquals("OTRO", jdbc.queryForObject("""
				SELECT COLUMN_DEFAULT FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'producto' AND COLUMN_NAME = 'tipo'""", String.class));
		assertEquals("NO", nullable("producto", "tipo"));
		assertEquals("YES", nullable("producto", "codigo"));
		assertEquals("YES", nullable("producto", "disciplina_id"));
		assertTrue(existeUnico("producto", "uk_producto_organizacion_codigo", "organizacion_id,codigo"));
		assertTrue(existeFk("producto", "fk_producto_disciplina", "disciplina"));
		// INSERT como el de hoy (sin tipo ni codigo), varias veces: OTRO, codigo NULL repetible.
		for (int i = 0; i < 2; i++) {
			jdbc.update("INSERT INTO producto (concepto, precio, organizacion_id) VALUES ('NUEVO SIN TIPO', 1.0, 1)");
		}
		assertEquals(List.of("OTRO", "OTRO"), jdbc.queryForList(
				"SELECT tipo FROM producto WHERE concepto = 'NUEVO SIN TIPO' AND codigo IS NULL", String.class));
		jdbc.update("DELETE FROM producto WHERE concepto = 'NUEVO SIN TIPO'");
	}

	// ---------------- grado y disciplina ----------------

	@Test
	void gradosEnlazadosASuProducto_enTaekwondoYEnLaCopiaDeKickboxing() {
		for (String plantilla : List.of("TAEKWONDO", "KICKBOXING")) {
			long sistema = sistema(plantilla);
			for (TipoGrado g : TipoGrado.values()) {
				Map<String, Object> fila = jdbc.queryForMap(
						"SELECT producto_examen_id, producto_recompensa_id FROM grado WHERE sistema_grados_id = ? AND codigo = ?",
						sistema, g.name());
				Long examen = g.tieneProductoExamen() ? canonico.get(g.obtenerNombreProducto(false)) : null;
				Long recompensa = g.tieneProductoRecompensa() ? canonico.get(g.obtenerNombreProducto(true)) : null;
				assertEquals(examen, numero(fila.get("producto_examen_id")), plantilla + " " + g);
				assertEquals(recompensa, numero(fila.get("producto_recompensa_id")), plantilla + " " + g);
			}
		}
		// Las variantes «bordado» no se enlazan a ningún grado.
		for (String bordado : List.of("DERECHO A EXAMEN ROJO BORDADO", "DERECHO DE CAMBIO A ROJO BORDADO POR RECOMPENSA")) {
			assertEquals(0, jdbc.queryForObject(
					"SELECT COUNT(*) FROM grado WHERE producto_examen_id = ? OR producto_recompensa_id = ?",
					Integer.class, canonico.get(bordado), canonico.get(bordado)));
		}
		assertTrue(existeFk("grado", "fk_grado_producto_examen", "producto"));
		assertTrue(existeFk("grado", "fk_grado_producto_recompensa", "producto"));
		assertTrue(existeIndice("grado", "idx_grado_producto_examen"));
		assertTrue(existeIndice("grado", "idx_grado_producto_recompensa"));
	}

	@Test
	void disciplinas_competidorEnlazado_licenciaSinEnlace() {
		List<Map<String, Object>> filas = jdbc.queryForList(
				"SELECT codigo, producto_competidor_id, producto_licencia_id FROM disciplina WHERE organizacion_id = 1 ORDER BY orden");
		assertEquals(4, filas.size());
		for (Map<String, Object> f : filas) {
			String codigo = (String) f.get("codigo");
			Long esperado = switch (codigo) {
			case "TAEKWONDO" -> canonico.get("TARIFA COMPETIDOR TAEKWONDO");
			case "KICKBOXING" -> canonico.get("TARIFA COMPETIDOR KICKBOXING");
			default -> null;
			};
			assertEquals(esperado, numero(f.get("producto_competidor_id")), codigo);
			assertNull(f.get("producto_licencia_id"), codigo);
		}
		assertTrue(existeFk("disciplina", "fk_disciplina_producto_competidor", "producto"));
		assertTrue(existeFk("disciplina", "fk_disciplina_producto_licencia", "producto"));
	}

	@Test
	void borrarUnProductoEnlazado_sigueFuncionando_yDejaElEnlaceANulo() {
		jdbc.update("INSERT INTO producto (concepto, precio, organizacion_id) VALUES ('TEMPORAL ENLAZADO', 1.0, 1)");
		long temporal = jdbc.queryForObject("SELECT id FROM producto WHERE concepto = 'TEMPORAL ENLAZADO'", Long.class);
		long negro5 = jdbc.queryForObject("SELECT id FROM grado WHERE sistema_grados_id = ? AND codigo = 'NEGRO_5_DAN'",
				Long.class, sistema("TAEKWONDO"));
		jdbc.update("UPDATE grado SET producto_recompensa_id = ? WHERE id = ?", temporal, negro5);
		jdbc.update("UPDATE disciplina SET producto_licencia_id = ? WHERE organizacion_id = 1 AND codigo = 'PILATES'",
				temporal);

		jdbc.update("DELETE FROM producto WHERE id = ?", temporal);

		assertNull(jdbc.queryForObject("SELECT producto_recompensa_id FROM grado WHERE id = ?", Long.class, negro5));
		assertNull(jdbc.queryForObject(
				"SELECT producto_licencia_id FROM disciplina WHERE organizacion_id = 1 AND codigo = 'PILATES'", Long.class));
	}

	// ---------------- postcheck y repetición ----------------

	@Test
	void postcheck_listaRepetidosVariantesYNadaMas() throws Exception {
		String sql = Files.readString(postcheck(), StandardCharsets.UTF_8).trim();
		if (sql.endsWith(";")) {
			sql = sql.substring(0, sql.length() - 1);
		}
		List<Map<String, Object>> filas = jdbc.queryForList(sql);
		Map<Long, String> variantes = new LinkedHashMap<>();
		for (Map<String, Object> f : filas) {
			assertEquals("VARIANTE_O_DUPLICADO", f.get("motivo"), "Fila inesperada: " + f);
			variantes.put(numero(f.get("id")), (String) f.get("codigo"));
		}
		assertEquals(Map.of(duplicadoMensualidadMayor, "MENSUALIDAD", duplicadoAmarilloMayor, "EXAMEN_AMARILLO",
				varianteMinusculas, "MENSUALIDAD", varianteEspacioFinal, "RESERVA_PLAZA", varianteDobleEspacio,
				"COMPETIDOR_TAEKWONDO"), variantes);
		// La variante en minúsculas tiene id menor: findFirstByConcepto la podría devolver hoy.
		assertEquals(1, jdbc.queryForList(sql).stream()
				.filter(f -> numero(f.get("id")) == varianteMinusculas)
				.mapToInt(f -> ((Number) f.get("id_menor")).intValue()).findFirst().orElseThrow());
	}

	@Test
	void repetirV24NoDuplicaNiCambiaNada() throws Exception {
		Map<String, List<Map<String, Object>>> antes = instantanea();

		try (Connection conexion = dataSource.getConnection()) {
			ScriptUtils.executeSqlScript(conexion, new EncodedResource(
					new ClassPathResource("db/migration/V24__tarifas_y_productos.sql"), StandardCharsets.UTF_8));
		}

		assertEquals(antes, instantanea());
	}

	// ---------------------------------------------------------------------------------

	private Map<String, List<Map<String, Object>>> instantanea() {
		Map<String, List<Map<String, Object>>> m = new LinkedHashMap<>();
		m.put("tarifa", jdbc.queryForList("SELECT * FROM tarifa ORDER BY id"));
		m.put("producto", jdbc.queryForList("SELECT * FROM producto ORDER BY id"));
		m.put("grado", jdbc.queryForList("SELECT * FROM grado ORDER BY id"));
		m.put("disciplina", jdbc.queryForList("SELECT * FROM disciplina ORDER BY id"));
		m.put("alumno_deporte", jdbc.queryForList("SELECT * FROM alumno_deporte ORDER BY id"));
		return m;
	}

	private void insertarDatosV23() {
		jdbc.update("INSERT INTO organizacion (id, slug, nombre) VALUES (?, 'otra-it', 'Otra escuela')", ORG2);

		// Ruido antes de los conocidos: variante en minúsculas con id MENOR que el original.
		varianteMinusculas = producto(10, "Mensualidad", 1);
		sinClasificar.add(varianteMinusculas);
		// Repetido con id menor que el «original»: será el canónico.
		long amarilloMenor = producto(20, "DERECHO A EXAMEN AMARILLO", 1);

		long id = 1500;
		for (String concepto : CONOCIDOS.keySet()) {
			id -= 7; // ids decrecientes y no consecutivos
			long creado = producto(id, concepto, 1);
			canonico.put(concepto, creado);
		}
		duplicadoAmarilloMayor = canonico.get("DERECHO A EXAMEN AMARILLO");
		canonico.put("DERECHO A EXAMEN AMARILLO", amarilloMenor);
		sinClasificar.add(duplicadoAmarilloMayor);

		duplicadoMensualidadMayor = producto(3000, "MENSUALIDAD", 1);
		varianteEspacioFinal = producto(3001, "RESERVA DE PLAZA ", 1);
		varianteDobleEspacio = producto(3002, "TARIFA  COMPETIDOR TAEKWONDO", 1);
		sinClasificar.addAll(List.of(duplicadoMensualidadMayor, varianteEspacioFinal, varianteDobleEspacio,
				producto(3003, "CAMISETA TAEMOI", 1), producto(3004, "MATRÍCULA 2025-2026", 1),
				producto(3005, null, 1), producto(3006, "DERECHO A EXAMEN VERDE MARZO 2026", 1)));
		productoOrg2 = producto(3100, "MENSUALIDAD", ORG2);

		int expediente = 1;
		for (TipoTarifa tarifa : TipoTarifa.values()) {
			long alumno = alumno(expediente++, 1);
			inscripcion(alumno, "TAEKWONDO", tarifa.name(), 1);
		}
		alumnoSinTarifa = alumno(expediente++, 1);
		inscripcion(alumnoSinTarifa, "PILATES", null, 1);
		alumnoOrg2 = alumno(expediente, ORG2);
		inscripcion(alumnoOrg2, "TAEKWONDO", "ADULTO", ORG2);
	}

	private long producto(long id, String concepto, long organizacion) {
		jdbc.update("INSERT INTO producto (id, concepto, precio, organizacion_id) VALUES (?, ?, 10.0, ?)", id, concepto,
				organizacion);
		return id;
	}

	private long alumno(int expediente, long organizacion) {
		jdbc.update("""
				INSERT INTO alumno (activo, apellidos, autorizacion_web, direccion, fecha_nacimiento, nombre,
				  numero_expediente, telefono, tiene_derecho_examen, organizacion_id)
				VALUES (1, 'Prueba', 1, 'Calle 1', '2010-01-01', ?, ?, 600000000, 0, ?)
				""", "Alumno " + expediente, expediente, organizacion);
		return jdbc.queryForObject("SELECT id FROM alumno WHERE numero_expediente = ? AND organizacion_id = ?",
				Long.class, expediente, organizacion);
	}

	private void inscripcion(long alumnoId, String deporte, String tipoTarifa, long organizacion) {
		jdbc.update("""
				INSERT INTO alumno_deporte (activo, apto_para_examen, alumno_id, deporte, competidor, tipo_tarifa, organizacion_id)
				VALUES (1, 0, ?, ?, 0, ?, ?)
				""", alumnoId, deporte, tipoTarifa, organizacion);
	}

	private static Long numero(Object valor) {
		return valor == null ? null : ((Number) valor).longValue();
	}

	private long sistema(String plantilla) {
		return jdbc.queryForObject(
				"SELECT id FROM sistema_grados WHERE organizacion_id = 1 AND plantilla_origen = ?", Long.class, plantilla);
	}

	private static Path postcheck() {
		Path desdeModulo = Path.of("..", "docs", "multi-escuela", "postcheck-v24.sql");
		Path desdeRaiz = Path.of("docs", "multi-escuela", "postcheck-v24.sql");
		Path ruta = Files.exists(desdeModulo) ? desdeModulo : desdeRaiz;
		assertNotNull(ruta);
		assertTrue(Files.exists(ruta), "No se encuentra postcheck-v24.sql");
		return ruta;
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

	private String nullable(String tabla, String columna) {
		return jdbc.queryForObject("""
				SELECT IS_NULLABLE FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?
				""", String.class, tabla, columna);
	}

	private boolean existeIndice(String tabla, String nombre) {
		return jdbc.queryForObject("""
				SELECT COUNT(*) FROM information_schema.STATISTICS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?
				""", Integer.class, tabla, nombre) > 0;
	}

	private boolean existeFk(String tabla, String nombre, String referenciada) {
		return jdbc.queryForObject("""
				SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS
				WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = ? AND CONSTRAINT_NAME = ?
				  AND REFERENCED_TABLE_NAME = ?
				""", Integer.class, tabla, nombre, referenciada) == 1;
	}

	private boolean existeUnico(String tabla, String nombre, String columnas) {
		String reales = jdbc.queryForObject("""
				SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',')
				FROM information_schema.STATISTICS
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ? AND NON_UNIQUE = 0
				""", String.class, tabla, nombre);
		return columnas.equals(reales);
	}
}
