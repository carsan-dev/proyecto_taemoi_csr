package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
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
 * V32_10: subida de 2 € de las tarifas de taekwondo y kickboxing de la organización 1 (Moiskimdo) desde la mensualidad
 * de enero de 2027. Cambia el catálogo (INFANTIL 28→30, ADULTO 30→32, KICKBOXING 30→32, HERMANOS 26→28, ADULTO_GRUPO y
 * INFANTIL_GRUPO 20→22, PADRES_HIJOS adulto 28→30 y menor 26→28) y la cuantía de las inscripciones que tienen
 * EXACTAMENTE la cuantía de catálogo vieja de su tarifa con su rol (regla de {@code TarifasService#calcularImporte},
 * D6-27). Las personalizadas, FAMILIAR, PILATES, DEFENSA_PERSONAL_FEMENINA, las inscripciones sin tarifa y las de otras
 * escuelas no cambian; tampoco productos, cargos ni tarifas por sede. Si el catálogo de producción no tiene el valor
 * viejo exacto de una tarifa, ni ella ni sus inscripciones cambian. Repetirla no cambia nada, también tras un fallo
 * entre sus dos sentencias. Ejecuta {@code docs/multi-escuela/postcheck-subida-tarifas-2027.sql}. Datos inventados.
 *
 * <p>Se prueba sobre un esquema propio migrado hasta 32.9 (Flyway), con las inscripciones sembradas antes de aplicar
 * 32.10; después el script se relanza a mano con {@link ScriptUtils} sobre el estado restaurado.
 */
@Tag("migracion")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SubidaTarifas2027V32_10MigracionIntegrationTest extends MySqlIntegrationTest {

	private static final String ESQUEMA = "taemoi_v32_10_it";
	private static final String V32_10 = "db/migration/V32_10__subida_tarifas_2027.sql";

	/** codigo -> {importe, importe_adulto, importe_menor} de la organización 1 antes (V24) y después de V32_10. */
	private static final Map<String, List<BigDecimal>> CATALOGO_ANTES = new LinkedHashMap<>();
	private static final Map<String, List<BigDecimal>> CATALOGO_DESPUES = new LinkedHashMap<>();

	static {
		CATALOGO_ANTES.put("FAMILIAR", importes("0.00", null, null));
		CATALOGO_ANTES.put("PADRES_HIJOS", importes("0.00", "28.00", "26.00"));
		CATALOGO_ANTES.put("ADULTO_GRUPO", importes("20.00", null, null));
		CATALOGO_ANTES.put("INFANTIL_GRUPO", importes("20.00", null, null));
		CATALOGO_ANTES.put("HERMANOS", importes("26.00", null, null));
		CATALOGO_ANTES.put("INFANTIL", importes("28.00", null, null));
		CATALOGO_ANTES.put("ADULTO", importes("30.00", null, null));
		CATALOGO_ANTES.put("KICKBOXING", importes("30.00", null, null));
		CATALOGO_ANTES.put("PILATES", importes("30.00", null, null));
		CATALOGO_ANTES.put("DEFENSA_PERSONAL_FEMENINA", importes("25.00", null, null));

		CATALOGO_DESPUES.putAll(CATALOGO_ANTES);
		CATALOGO_DESPUES.put("PADRES_HIJOS", importes("0.00", "30.00", "28.00"));
		CATALOGO_DESPUES.put("ADULTO_GRUPO", importes("22.00", null, null));
		CATALOGO_DESPUES.put("INFANTIL_GRUPO", importes("22.00", null, null));
		CATALOGO_DESPUES.put("HERMANOS", importes("28.00", null, null));
		CATALOGO_DESPUES.put("INFANTIL", importes("30.00", null, null));
		CATALOGO_DESPUES.put("ADULTO", importes("32.00", null, null));
		CATALOGO_DESPUES.put("KICKBOXING", importes("32.00", null, null));
	}

	/** Inscripción de prueba de la organización 1: tarifa (null = sin tarifa), rol, activa, cuantía antes y después. */
	private record Caso(String tarifa, String rol, boolean activa, Double antes, Double despues) {
	}

	private static final List<Caso> CASOS = List.of(
			// Cuantía de catálogo: sube (activas o no, con cualquier rol si la tarifa no tiene importes por rol).
			new Caso("INFANTIL", "NINGUNO", true, 28.0, 30.0),
			new Caso("INFANTIL", null, false, 28.0, 30.0),
			new Caso("INFANTIL", "HIJO", true, 28.0, 30.0),
			new Caso("ADULTO", "NINGUNO", true, 30.0, 32.0),
			new Caso("ADULTO", "PADRE", false, 30.0, 32.0),
			new Caso("KICKBOXING", "NINGUNO", true, 30.0, 32.0),
			new Caso("HERMANOS", "NINGUNO", true, 26.0, 28.0),
			new Caso("ADULTO_GRUPO", "NINGUNO", true, 20.0, 22.0),
			new Caso("INFANTIL_GRUPO", "NINGUNO", false, 20.0, 22.0),
			new Caso("PADRES_HIJOS", "PADRE", true, 28.0, 30.0),
			new Caso("PADRES_HIJOS", "MADRE", false, 28.0, 30.0),
			new Caso("PADRES_HIJOS", "HIJO", true, 26.0, 28.0),
			new Caso("PADRES_HIJOS", "HIJA", true, 26.0, 28.0),
			// Personalizadas (distintas de la de catálogo con su rol): no cambian.
			new Caso("INFANTIL", "NINGUNO", true, 25.0, 25.0),
			new Caso("INFANTIL", "NINGUNO", true, 28.5, 28.5),
			new Caso("INFANTIL", "NINGUNO", true, 27.995, 27.995),
			new Caso("ADULTO", "NINGUNO", true, 28.0, 28.0),
			new Caso("ADULTO", "NINGUNO", true, 32.0, 32.0),
			new Caso("KICKBOXING", "NINGUNO", true, 26.0, 26.0),
			new Caso("HERMANOS", "NINGUNO", true, 28.0, 28.0),
			new Caso("PADRES_HIJOS", "HIJO", true, 28.0, 28.0),
			new Caso("PADRES_HIJOS", "PADRE", true, 26.0, 26.0),
			new Caso("PADRES_HIJOS", "NINGUNO", true, 28.0, 28.0),
			new Caso("PADRES_HIJOS", null, true, 26.0, 26.0),
			new Caso("PADRES_HIJOS", "NINGUNO", true, 0.0, 0.0),
			new Caso("INFANTIL", "NINGUNO", true, null, null),
			// Tarifas que no suben y sin tarifa: no cambian.
			new Caso("FAMILIAR", "NINGUNO", true, 0.0, 0.0),
			new Caso("PILATES", "NINGUNO", true, 30.0, 30.0),
			new Caso("DEFENSA_PERSONAL_FEMENINA", "NINGUNO", true, 25.0, 25.0),
			new Caso(null, "NINGUNO", true, 28.0, 28.0));

	private static final List<String> TABLAS_SIN_CAMBIOS = List.of("alumno", "producto", "producto_alumno", "tarifa_sede",
			"disciplina", "organizacion", "sede");
	private static final String COLUMNAS_DEL_ESQUEMA = """
			SELECT TABLE_NAME, COLUMN_NAME, ORDINAL_POSITION, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
			FROM information_schema.COLUMNS
			WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME <> 'flyway_schema_history'
			ORDER BY TABLE_NAME, ORDINAL_POSITION""";

	DataSource dataSource;
	JdbcTemplate jdbc;
	long otra;
	long disciplinaTkd;
	int expediente = 321000;
	/** id de alumno_deporte -> caso, en el orden de {@link #CASOS}. */
	final Map<Long, Caso> inscripciones = new LinkedHashMap<>();
	final List<Long> inscripcionesOtra = new ArrayList<>();
	List<Map<String, Object>> tarifasAntes;
	List<Map<String, Object>> inscripcionesAntes;
	List<Map<String, Object>> columnasAntes;
	final Map<String, List<Map<String, Object>>> filasAntes = new LinkedHashMap<>();

	@BeforeAll
	void migrar() {
		dataSource = recrear(ESQUEMA);
		jdbc = new JdbcTemplate(dataSource);
		flyway(dataSource, "32.9").migrate();
		assertEquals(CATALOGO_ANTES, catalogo(1L), "semilla de V24 en la organización 1");
		disciplinaTkd = jdbc.queryForObject("SELECT id FROM disciplina WHERE organizacion_id = 1 AND codigo = 'TAEKWONDO'",
				Long.class);

		for (Caso caso : CASOS) {
			inscripciones.put(inscripcion(1L, disciplinaTkd, caso.tarifa() == null ? null : idTarifa(1L, caso.tarifa()),
					caso.rol(), caso.activa(), caso.antes()), caso);
		}

		// Otra escuela con las mismas tarifas e importes (como una copia de la plantilla) e inscripciones de catálogo.
		jdbc.update("INSERT INTO organizacion (slug, nombre) VALUES ('otra-v32-10', 'Otra escuela V32_10')");
		otra = jdbc.queryForObject("SELECT id FROM organizacion WHERE slug = 'otra-v32-10'", Long.class);
		jdbc.update("""
				INSERT INTO disciplina (organizacion_id, codigo, nombre, sistema_grados_id, edad_adulto, orden)
				VALUES (?, 'TAEKWONDO', 'Taekwondo V32_10', NULL, 14, 1)""", otra);
		long disciplinaOtra = jdbc.queryForObject("SELECT id FROM disciplina WHERE organizacion_id = ?", Long.class, otra);
		int orden = 1;
		for (Map.Entry<String, List<BigDecimal>> t : CATALOGO_ANTES.entrySet()) {
			List<BigDecimal> v = t.getValue();
			jdbc.update("""
					INSERT INTO tarifa (organizacion_id, disciplina_id, codigo, nombre, importe, importe_adulto, importe_menor,
					  activa, orden) VALUES (?, ?, ?, ?, ?, ?, ?, TRUE, ?)""", otra, disciplinaOtra, t.getKey(), t.getKey(),
					v.get(0), v.get(1), v.get(2), orden++);
		}
		inscripcionesOtra.add(inscripcion(otra, disciplinaOtra, idTarifa(otra, "INFANTIL"), "NINGUNO", true, 28.0));
		inscripcionesOtra.add(inscripcion(otra, disciplinaOtra, idTarifa(otra, "ADULTO"), "NINGUNO", true, 30.0));
		inscripcionesOtra.add(inscripcion(otra, disciplinaOtra, idTarifa(otra, "PADRES_HIJOS"), "PADRE", true, 28.0));
		inscripcionesOtra.add(inscripcion(otra, disciplinaOtra, idTarifa(otra, "PADRES_HIJOS"), "HIJA", true, 26.0));
		// Inscripción de la otra escuela que apunta (por error) a una tarifa de la organización 1: tampoco se toca.
		inscripcionesOtra.add(inscripcion(otra, disciplinaOtra, idTarifa(1L, "INFANTIL"), "NINGUNO", true, 28.0));

		tarifasAntes = tarifas();
		inscripcionesAntes = todasLasInscripciones();
		columnasAntes = jdbc.queryForList(COLUMNAS_DEL_ESQUEMA);
		for (String tabla : TABLAS_SIN_CAMBIOS) {
			filasAntes.put(tabla, jdbc.queryForList("SELECT * FROM " + tabla + " ORDER BY id"));
		}
		flyway(dataSource, "32.10").migrate();
	}

	@Test
	@Order(1)
	void llegaAV32_10() {
		assertEquals("32.10", jdbc.queryForObject(
				"SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1",
				String.class));
	}

	@Test
	@Order(2)
	void catalogoDeLaOrganizacion1_subeDosEurosEnTaekwondoYKickboxing() {
		assertEquals(CATALOGO_DESPUES, catalogo(1L));
	}

	/** Solo cambian importe, importe_adulto e importe_menor de las siete tarifas que suben; el resto de columnas, igual. */
	@Test
	@Order(3)
	void tarifas_soloCambianLosImportesDeLasQueSuben() {
		assertEquals(esperadoTarifas(), tarifas());
	}

	@Test
	@Order(4)
	void inscripciones_subenLasDeCuantiaDeCatalogo_yLasPersonalizadasNo() {
		assertEquals(esperadoInscripciones(), cuantias());
	}

	/** De alumno_deporte solo cambia cuantia_tarifa, y solo en las filas que suben. */
	@Test
	@Order(5)
	void inscripciones_soloCambiaLaCuantia() {
		List<Map<String, Object>> esperado = new ArrayList<>();
		for (Map<String, Object> fila : inscripcionesAntes) {
			Map<String, Object> copia = new LinkedHashMap<>(fila);
			Caso caso = inscripciones.get(((Number) fila.get("id")).longValue());
			if (caso != null) {
				copia.put("cuantia_tarifa", caso.despues());
			}
			esperado.add(copia);
		}
		assertEquals(esperado, todasLasInscripciones());
	}

	@Test
	@Order(6)
	void otraEscuela_productosCargosYTarifasPorSede_sinCambios() {
		assertEquals(CATALOGO_ANTES, catalogo(otra));
		for (Long id : inscripcionesOtra) {
			assertEquals(cuantiaAntes(id), cuantia(id), "inscripción de la otra escuela " + id);
		}
		for (String tabla : TABLAS_SIN_CAMBIOS) {
			assertEquals(filasAntes.get(tabla), jdbc.queryForList("SELECT * FROM " + tabla + " ORDER BY id"), tabla);
		}
		assertEquals(columnasAntes, jdbc.queryForList(COLUMNAS_DEL_ESQUEMA), "el esquema no cambia");
	}

	/**
	 * El postcheck: tarifas de la organización 1 (las siete que suben y PILATES), inscripciones por tarifa, rol y cuantía,
	 * y la fila de Flyway. Solo lectura (lo comprueba el ejecutor).
	 */
	@Test
	@Order(7)
	void postcheck_justoTrasDesplegar() throws Exception {
		List<List<Map<String, Object>>> resultados = SedesVinculosMigracionIntegrationTest.ejecutarScriptDocs(dataSource,
				"postcheck-subida-tarifas-2027.sql");
		assertEquals(3, resultados.size(), "tres consultas");

		Map<String, List<BigDecimal>> tarifas = new LinkedHashMap<>();
		for (Map<String, Object> fila : resultados.get(0)) {
			tarifas.put((String) fila.get("codigo"), Arrays.asList((BigDecimal) fila.get("importe"),
					(BigDecimal) fila.get("importe_adulto"), (BigDecimal) fila.get("importe_menor")));
		}
		Map<String, List<BigDecimal>> esperadas = new LinkedHashMap<>(CATALOGO_DESPUES);
		esperadas.remove("FAMILIAR");
		esperadas.remove("DEFENSA_PERSONAL_FEMENINA");
		assertEquals(esperadas.keySet().stream().sorted().toList(), tarifas.keySet().stream().sorted().toList());
		esperadas.forEach((codigo, importes) -> assertEquals(importes, tarifas.get(codigo), codigo));

		// Recuentos de la organización 1 por tarifa, rol y cuantía, con su situación frente al catálogo NUEVO: una
		// personalizada que ya coincidía con el precio nuevo (PADRES_HIJOS HIJO 28, ADULTO 32) cuenta como catálogo.
		Map<String, Long> recuentos = new LinkedHashMap<>();
		for (Map<String, Object> fila : resultados.get(1)) {
			String clave = fila.get("tarifa") + "/" + fila.get("rol_familiar") + "/" + fila.get("cuantia_tarifa") + "/"
					+ fila.get("situacion");
			recuentos.merge(clave, ((Number) fila.get("inscripciones")).longValue(), Long::sum);
		}
		assertEquals(1L, recuentos.get("INFANTIL/NINGUNO/30.0/catalogo"), recuentos.toString());
		assertEquals(1L, recuentos.get("INFANTIL/null/30.0/catalogo"), recuentos.toString());
		assertEquals(1L, recuentos.get("INFANTIL/NINGUNO/28.5/personalizada"), recuentos.toString());
		assertEquals(1L, recuentos.get("INFANTIL/NINGUNO/null/sin cuantia"), recuentos.toString());
		assertEquals(2L, recuentos.get("PADRES_HIJOS/HIJO/28.0/catalogo"), recuentos.toString());
		assertEquals(2L, recuentos.get("ADULTO/NINGUNO/32.0/catalogo"), recuentos.toString());
		assertEquals(1L, recuentos.get("PADRES_HIJOS/NINGUNO/0.0/catalogo"), recuentos.toString());
		assertEquals(1L, recuentos.get("PADRES_HIJOS/NINGUNO/28.0/personalizada"), recuentos.toString());
		assertEquals(1L, recuentos.get("PADRES_HIJOS/PADRE/30.0/catalogo"), recuentos.toString());
		assertEquals(1L, recuentos.get("PADRES_HIJOS/PADRE/26.0/personalizada"), recuentos.toString());
		assertEquals(1L, recuentos.get("ADULTO/NINGUNO/28.0/personalizada"), recuentos.toString());
		assertEquals(1L, recuentos.get("PILATES/NINGUNO/30.0/catalogo"), recuentos.toString());
		assertEquals(1L, recuentos.get("FAMILIAR/NINGUNO/0.0/catalogo"), recuentos.toString());
		assertEquals(1L, recuentos.get("null/NINGUNO/28.0/sin tarifa"), recuentos.toString());
		long total = recuentos.values().stream().mapToLong(Long::longValue).sum();
		assertEquals(CASOS.size(), total, "todas las inscripciones de la organización 1 y ninguna de la otra");

		assertEquals(1, resultados.get(2).size(), "la fila de Flyway");
		Map<String, Object> flyway = resultados.get(2).get(0);
		assertEquals("32.10", flyway.get("version"));
		Object exito = flyway.get("success");
		assertTrue(Boolean.TRUE.equals(exito) || (exito instanceof Number n && n.intValue() == 1), "success: " + exito);
	}

	@Test
	@Order(8)
	void repetirla_noCambiaNada() throws Exception {
		ejecutarV32_10();
		ejecutarV32_10();
		assertEquals(esperadoTarifas(), tarifas());
		assertEquals(esperadoInscripciones(), cuantias());
	}

	/**
	 * Si en producción una tarifa no tiene exactamente el valor viejo, ni ella ni sus inscripciones cambian (las demás
	 * suben igual). PADRES_HIJOS se mira entera: con un solo importe distinto no cambia ni el adulto ni el menor.
	 */
	@Test
	@Order(9)
	void catalogoDistintoDelEsperado_esaTarifaYSusInscripcionesNoCambian() throws Exception {
		restaurarAntesDeV32_10();
		jdbc.update("UPDATE tarifa SET importe = 29.00 WHERE organizacion_id = 1 AND codigo = 'INFANTIL'");
		jdbc.update("UPDATE tarifa SET importe_menor = 25.00 WHERE organizacion_id = 1 AND codigo = 'PADRES_HIJOS'");
		jdbc.update("UPDATE tarifa SET importe_adulto = 30.00 WHERE organizacion_id = 1 AND codigo = 'HERMANOS'");
		try {
			ejecutarV32_10();
			assertEquals(importes("29.00", null, null), catalogo(1L).get("INFANTIL"));
			assertEquals(importes("0.00", "28.00", "25.00"), catalogo(1L).get("PADRES_HIJOS"));
			assertEquals(importes("26.00", "30.00", null), catalogo(1L).get("HERMANOS"));
			assertEquals(importes("32.00", null, null), catalogo(1L).get("ADULTO"), "las demás suben");
			for (Map.Entry<Long, Caso> e : inscripciones.entrySet()) {
				Caso caso = e.getValue();
				boolean quieta = "INFANTIL".equals(caso.tarifa()) || "PADRES_HIJOS".equals(caso.tarifa())
						|| "HERMANOS".equals(caso.tarifa());
				assertEquals(quieta ? caso.antes() : caso.despues(), cuantia(e.getKey()), caso.toString());
			}
		} finally {
			restaurarAntesDeV32_10();
		}
		ejecutarV32_10();
		assertEquals(esperadoTarifas(), tarifas());
		assertEquals(esperadoInscripciones(), cuantias());
	}

	/**
	 * Fallo entre las dos sentencias (sin la transacción de Flyway): las inscripciones ya subieron y el catálogo no.
	 * Volver a lanzarla completa el catálogo sin subir otra vez las inscripciones.
	 */
	@Test
	@Order(10)
	void falloAntesDelCatalogo_repetirLaCompletaSinSubirDosVeces() throws Exception {
		restaurarAntesDeV32_10();
		jdbc.execute("""
				CREATE TRIGGER it_v32_10_fallo BEFORE UPDATE ON tarifa FOR EACH ROW
				SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'fallo simulado de V32_10'""");
		try {
			assertThrows(Exception.class, this::ejecutarV32_10);
			assertEquals(tarifasAntes, tarifas(), "catálogo sin aplicar");
			assertEquals(esperadoInscripciones(), cuantias(), "inscripciones ya subidas");
		} finally {
			jdbc.execute("DROP TRIGGER IF EXISTS it_v32_10_fallo");
		}
		ejecutarV32_10();
		assertEquals(esperadoTarifas(), tarifas());
		assertEquals(esperadoInscripciones(), cuantias());
	}

	/** Dentro de una transacción (como la aplica Flyway en MySQL), un fallo no deja nada aplicado. */
	@Test
	@Order(11)
	void falloDentroDeUnaTransaccion_noAplicaNada() throws Exception {
		restaurarAntesDeV32_10();
		jdbc.execute("""
				CREATE TRIGGER it_v32_10_fallo_tx BEFORE UPDATE ON tarifa FOR EACH ROW
				SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'fallo simulado de V32_10'""");
		try (Connection conexion = dataSource.getConnection()) {
			conexion.setAutoCommit(false);
			assertThrows(Exception.class, () -> ScriptUtils.executeSqlScript(conexion,
					new EncodedResource(new ClassPathResource(V32_10), StandardCharsets.UTF_8)));
			conexion.rollback();
		} finally {
			jdbc.execute("DROP TRIGGER IF EXISTS it_v32_10_fallo_tx");
		}
		assertEquals(tarifasAntes, tarifas(), "nada aplicado en el catálogo");
		assertEquals(inscripcionesAntes, todasLasInscripciones(), "nada aplicado en las inscripciones");
		ejecutarV32_10();
		assertEquals(esperadoTarifas(), tarifas());
		assertEquals(esperadoInscripciones(), cuantias());
	}

	// ------------------------------------------------------------------ utilidades

	private static List<BigDecimal> importes(String importe, String adulto, String menor) {
		return Arrays.asList(new BigDecimal(importe), adulto == null ? null : new BigDecimal(adulto),
				menor == null ? null : new BigDecimal(menor));
	}

	private Map<String, List<BigDecimal>> catalogo(long organizacion) {
		Map<String, List<BigDecimal>> catalogo = new LinkedHashMap<>();
		for (Map<String, Object> fila : jdbc.queryForList("""
				SELECT codigo, importe, importe_adulto, importe_menor FROM tarifa
				WHERE organizacion_id = ? ORDER BY id""", organizacion)) {
			catalogo.put((String) fila.get("codigo"), Arrays.asList((BigDecimal) fila.get("importe"),
					(BigDecimal) fila.get("importe_adulto"), (BigDecimal) fila.get("importe_menor")));
		}
		return catalogo;
	}

	private List<Map<String, Object>> esperadoTarifas() {
		List<Map<String, Object>> esperado = new ArrayList<>();
		for (Map<String, Object> fila : tarifasAntes) {
			Map<String, Object> copia = new LinkedHashMap<>(fila);
			if (((Number) fila.get("organizacion_id")).longValue() == 1L) {
				List<BigDecimal> nuevos = CATALOGO_DESPUES.get((String) fila.get("codigo"));
				copia.put("importe", nuevos.get(0));
				copia.put("importe_adulto", nuevos.get(1));
				copia.put("importe_menor", nuevos.get(2));
			}
			esperado.add(copia);
		}
		return esperado;
	}

	private Map<Long, Double> esperadoInscripciones() {
		Map<Long, Double> esperado = new LinkedHashMap<>();
		inscripciones.forEach((id, caso) -> esperado.put(id, caso.despues()));
		for (Long id : inscripcionesOtra) {
			esperado.put(id, cuantiaAntes(id));
		}
		return esperado;
	}

	private Map<Long, Double> cuantias() {
		Map<Long, Double> cuantias = new LinkedHashMap<>();
		for (Long id : inscripciones.keySet()) {
			cuantias.put(id, cuantia(id));
		}
		for (Long id : inscripcionesOtra) {
			cuantias.put(id, cuantia(id));
		}
		return cuantias;
	}

	private Double cuantia(long id) {
		return jdbc.queryForObject("SELECT cuantia_tarifa FROM alumno_deporte WHERE id = ?", Double.class, id);
	}

	private Double cuantiaAntes(long id) {
		return inscripcionesAntes.stream().filter(f -> ((Number) f.get("id")).longValue() == id)
				.map(f -> (Double) f.get("cuantia_tarifa")).findFirst().orElseThrow();
	}

	private void restaurarAntesDeV32_10() {
		for (Map<String, Object> fila : tarifasAntes) {
			jdbc.update("UPDATE tarifa SET importe = ?, importe_adulto = ?, importe_menor = ? WHERE id = ?",
					fila.get("importe"), fila.get("importe_adulto"), fila.get("importe_menor"), fila.get("id"));
		}
		for (Map<String, Object> fila : inscripcionesAntes) {
			jdbc.update("UPDATE alumno_deporte SET cuantia_tarifa = ? WHERE id = ?", fila.get("cuantia_tarifa"),
					fila.get("id"));
		}
		assertEquals(tarifasAntes, tarifas());
		assertEquals(inscripcionesAntes, todasLasInscripciones());
	}

	private long inscripcion(long organizacion, long disciplina, Long tarifa, String rol, boolean activa, Double cuantia) {
		int numero = expediente++;
		jdbc.update("""
				INSERT INTO alumno (activo, apellidos, autorizacion_web, direccion, fecha_nacimiento, nombre,
				  numero_expediente, telefono, tiene_derecho_examen, organizacion_id)
				VALUES (?, 'PRUEBA', 1, 'Calle 1', '2012-01-01', 'V32_10', ?, '600000000', 0, ?)""", activa, numero,
				organizacion);
		long alumno = jdbc.queryForObject("SELECT id FROM alumno WHERE numero_expediente = ? AND organizacion_id = ?",
				Long.class, numero, organizacion);
		jdbc.update("""
				INSERT INTO alumno_deporte (activo, apto_para_examen, alumno_id, competidor, principal, tiene_licencia,
				  organizacion_id, fecha_alta, fecha_alta_inicial, disciplina_id, tarifa_id, rol_familiar, cuantia_tarifa)
				VALUES (?, FALSE, ?, 0, 1, 0, ?, '2024-09-01', '2024-09-01', ?, ?, ?, ?)""", activa, alumno, organizacion,
				disciplina, tarifa, rol, cuantia);
		return jdbc.queryForObject("SELECT id FROM alumno_deporte WHERE alumno_id = ?", Long.class, alumno);
	}

	private long idTarifa(long organizacion, String codigo) {
		return jdbc.queryForObject("SELECT id FROM tarifa WHERE organizacion_id = ? AND codigo = ?", Long.class,
				organizacion, codigo);
	}

	private List<Map<String, Object>> tarifas() {
		return jdbc.queryForList("SELECT * FROM tarifa ORDER BY id");
	}

	private List<Map<String, Object>> todasLasInscripciones() {
		return jdbc.queryForList("SELECT * FROM alumno_deporte ORDER BY id");
	}

	private void ejecutarV32_10() throws Exception {
		try (Connection conexion = dataSource.getConnection()) {
			ScriptUtils.executeSqlScript(conexion,
					new EncodedResource(new ClassPathResource(V32_10), StandardCharsets.UTF_8));
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
