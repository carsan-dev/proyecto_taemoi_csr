package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

import com.taemoi.project.controllers.AlumnoController;
import com.taemoi.project.dtos.AlumnoDTO;
import com.taemoi.project.dtos.ProductoAlumnoDTO;
import com.taemoi.project.dtos.response.TesoreriaMovimientoDTO;
import com.taemoi.project.dtos.response.TesoreriaResumenDTO;
import com.taemoi.project.entities.Producto;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.services.ProductoAlumnoService;
import com.taemoi.project.services.ProductoService;
import com.taemoi.project.services.ProductosCatalogoService;
import com.taemoi.project.services.TesoreriaService;

/**
 * Paridad del cambio de llamadores a tarifas y productos (V27) para Taemoi (organización 1), con los
 * valores LITERALES de docs/multi-escuela/valores-actuales.md (§6 tarifas, §7 productos y tesorería):
 * la cuantía de la mensualidad de cada tarifa, el concepto y el importe de cada licencia, el producto
 * de competidor de cada disciplina y los totales de tesorería de un conjunto de cobros fijo.
 *
 * <p>Los cobros de este test van en 2041 (y los de tesorería en 2042) para no mezclarse con otros.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CambioTarifasProductosIntegrationTest extends MySqlIntegrationTest {

	private static final long ORG_TAEMOI = 1L;

	/**
	 * Precio de cada licencia en el conjunto de prueba: los importes de valores-actuales.md §7
	 * (los de taekwondo; kickboxing no tiene importe en el código, se usan 41 y 31 para distinguirlos).
	 */
	private static final Map<String, Double> PRECIO_LICENCIA = new LinkedHashMap<>();

	static {
		PRECIO_LICENCIA.put("LICENCIA FEDERATIVA TAEKWONDO ADULTO", 46.0);
		PRECIO_LICENCIA.put("LICENCIA FEDERATIVA TAEKWONDO INFANTIL", 35.0);
		PRECIO_LICENCIA.put("LICENCIA FEDERATIVA TAEKWONDO DISCAPACIDAD", 30.0);
		PRECIO_LICENCIA.put("PARTE PROPORCIONAL LICENCIA FEDERATIVA TAEKWONDO ADULTO", 35.0);
		PRECIO_LICENCIA.put("PARTE PROPORCIONAL LICENCIA FEDERATIVA TAEKWONDO INFANTIL", 20.0);
		PRECIO_LICENCIA.put("PARTE PROPORCIONAL LICENCIA FEDERATIVA TAEKWONDO DISCAPACIDAD", 20.0);
		PRECIO_LICENCIA.put("LICENCIA FEDERATIVA KICKBOXING ADULTO", 41.0);
		PRECIO_LICENCIA.put("LICENCIA FEDERATIVA KICKBOXING INFANTIL", 31.0);
	}

	@Autowired AlumnoController alumnoController;
	@Autowired AlumnoRepository alumnos;
	@Autowired ProductoAlumnoService productoAlumnoService;
	@Autowired ProductosCatalogoService catalogo;
	@Autowired ProductoService productoService;
	@Autowired TesoreriaService tesoreria;
	@Autowired com.taemoi.project.controllers.ProductoController productoController;
	@Autowired com.taemoi.project.repositories.ProductoRepository productoRepository;
	@Autowired TransactionTemplate tx;
	@Autowired DataSource dataSource;
	@Autowired JdbcTemplate jdbc;

	final List<Long> productosInsertados = new ArrayList<>();
	final Map<Long, Double> preciosOriginales = new HashMap<>();
	final List<Long> alumnosCreados = new ArrayList<>();

	@BeforeAll
	void productosDeTaemoiClasificados() throws Exception {
		// En producción los crea la gestión; aquí se asegura uno por cada nombre conocido y se repite V27
		// (resync de V24, repetible) para que queden clasificados como en producción tras V27.
		for (String concepto : TarifasProductosMigracionIntegrationTest.CONOCIDOS.keySet()) {
			if (idPorConcepto(concepto) == null) {
				jdbc.update("INSERT INTO producto (concepto, precio, organizacion_id) VALUES (?, 15.0, 1)", concepto);
				productosInsertados.add(idPorConcepto(concepto));
			}
		}
		// Resync de V24 que repite V27 (productos y grados; desde V28 sin las sentencias de tipo_tarifa, que ya
		// no existe).
		ResyncProductosDePrueba.clasificarProductos(dataSource);
		for (Map.Entry<String, Double> licencia : PRECIO_LICENCIA.entrySet()) {
			fijarPrecio(idPorConcepto(licencia.getKey()), licencia.getValue());
		}
		fijarPrecio(idPorConcepto("TARIFA COMPETIDOR TAEKWONDO"), 20.0);
		fijarPrecio(idPorConcepto("TARIFA COMPETIDOR KICKBOXING"), 20.0);
		fijarPrecio(idPorConcepto("RESERVA DE PLAZA"), 10.0);
	}

	@AfterAll
	void restaurar() {
		for (Long alumno : alumnosCreados) {
			jdbc.update("DELETE FROM producto_alumno WHERE alumno_id = ?", alumno);
			jdbc.update("DELETE FROM alumno_deporte WHERE alumno_id = ?", alumno);
		}
		preciosOriginales.forEach((id, precio) -> jdbc.update("UPDATE producto SET precio = ? WHERE id = ?", precio, id));
		for (Long id : productosInsertados) {
			jdbc.update("UPDATE producto_alumno SET producto_id = NULL WHERE producto_id = ?", id);
			jdbc.update("UPDATE disciplina SET producto_competidor_id = NULL WHERE producto_competidor_id = ?", id);
			jdbc.update("UPDATE grado SET producto_examen_id = NULL WHERE producto_examen_id = ?", id);
			jdbc.update("UPDATE grado SET producto_recompensa_id = NULL WHERE producto_recompensa_id = ?", id);
			jdbc.update("DELETE FROM producto WHERE id = ?", id);
		}
	}

	@AfterEach
	void sinFugas() {
		SecurityContextHolder.clearContext();
		assertNull(TenantContext.get());
	}

	// ---------------- §6 Tarifas: cuantía de la mensualidad ----------------

	/**
	 * Alta con los campos de deporte único (sin cuantía: la calcula el servidor desde la tarifa) y
	 * mensualidad del deporte: el cargo cobra la cuantía de valores-actuales.md §6 y la inscripción
	 * guarda tarifa_id.
	 */
	@Test
	void mensualidad_cobraLaCuantiaDeCadaTarifaHeredada() {
		Object[][] casos = {
				{ "ADULTO", "TAEKWONDO", "NINGUNO", 30.0 },
				{ "ADULTO_GRUPO", "TAEKWONDO", "NINGUNO", 20.0 },
				{ "FAMILIAR", "TAEKWONDO", "NINGUNO", 0.0 },
				{ "INFANTIL", "TAEKWONDO", "NINGUNO", 28.0 },
				{ "INFANTIL_GRUPO", "TAEKWONDO", "NINGUNO", 20.0 },
				{ "HERMANOS", "TAEKWONDO", "NINGUNO", 26.0 },
				{ "PADRES_HIJOS", "TAEKWONDO", "PADRE", 28.0 },
				{ "PADRES_HIJOS", "TAEKWONDO", "MADRE", 28.0 },
				{ "PADRES_HIJOS", "TAEKWONDO", "HIJO", 26.0 },
				{ "PADRES_HIJOS", "TAEKWONDO", "HIJA", 26.0 },
				{ "PADRES_HIJOS", "TAEKWONDO", "NINGUNO", 0.0 },
				{ "KICKBOXING", "KICKBOXING", "NINGUNO", 30.0 },
				{ "PILATES", "PILATES", "NINGUNO", 30.0 },
				{ "DEFENSA_PERSONAL_FEMENINA", "DEFENSA_PERSONAL_FEMENINA", "NINGUNO", 25.0 } };
		Map<String, Double> esperado = new LinkedHashMap<>();
		Map<String, Double> real = new LinkedHashMap<>();
		for (Object[] caso : casos) {
			String tarifa = (String) caso[0];
			String deporte = (String) caso[1];
			String rol = (String) caso[2];
			long alumno = altaDeporteUnico(deporte, tarifa, rol);
			TenantContext.ejecutarCon(ORG_TAEMOI, () -> tx.executeWithoutResult(s -> productoAlumnoService
					.cargarMensualidadIndividualPorDeporte(alumno, deporte, "2041-03", false, LocalDate.of(2041, 3, 1))));

			Map<String, Object> inscripcion = jdbc.queryForMap("""
					SELECT t.codigo AS tarifa FROM alumno_deporte ad
					LEFT JOIN tarifa t ON t.id = ad.tarifa_id WHERE ad.alumno_id = ?""", alumno);
			assertEquals(tarifa, inscripcion.get("tarifa"), tarifa + ": tarifa_id escrita en el alta");

			Map<String, Object> cargo = jdbc.queryForMap(
					"SELECT pa.precio, p.codigo FROM producto_alumno pa JOIN producto p ON p.id = pa.producto_id"
							+ " WHERE pa.alumno_id = ? AND pa.concepto = ?",
					alumno, "MENSUALIDAD MARZO 2041 - " + deporte);
			assertEquals("MENSUALIDAD", cargo.get("codigo"));
			esperado.put(tarifa + "/" + rol, (Double) caso[3]);
			real.put(tarifa + "/" + rol, ((Number) cargo.get("precio")).doubleValue());
		}
		assertEquals(esperado, real);
	}

	// ---------------- §7 Licencias ----------------

	/** Producto de cada variante: los conceptos literales de valores-actuales.md §7. */
	@Test
	void licencia_conceptoDeCadaVariante() {
		Map<String, String> esperado = new LinkedHashMap<>();
		esperado.put("TAEKWONDO adulto", "LICENCIA FEDERATIVA TAEKWONDO ADULTO");
		esperado.put("TAEKWONDO infantil", "LICENCIA FEDERATIVA TAEKWONDO INFANTIL");
		esperado.put("TAEKWONDO discapacidad", "LICENCIA FEDERATIVA TAEKWONDO DISCAPACIDAD");
		esperado.put("TAEKWONDO discapacidad infantil", "LICENCIA FEDERATIVA TAEKWONDO DISCAPACIDAD");
		esperado.put("TAEKWONDO adulto proporcional", "PARTE PROPORCIONAL LICENCIA FEDERATIVA TAEKWONDO ADULTO");
		esperado.put("TAEKWONDO infantil proporcional", "PARTE PROPORCIONAL LICENCIA FEDERATIVA TAEKWONDO INFANTIL");
		esperado.put("TAEKWONDO discapacidad proporcional", "PARTE PROPORCIONAL LICENCIA FEDERATIVA TAEKWONDO DISCAPACIDAD");
		esperado.put("KICKBOXING adulto", "LICENCIA FEDERATIVA KICKBOXING ADULTO");
		esperado.put("KICKBOXING infantil", "LICENCIA FEDERATIVA KICKBOXING INFANTIL");
		esperado.put("KICKBOXING adulto proporcional", "LICENCIA FEDERATIVA KICKBOXING ADULTO");
		esperado.put("KICKBOXING infantil proporcional", "LICENCIA FEDERATIVA KICKBOXING INFANTIL");
		esperado.put("KICKBOXING discapacidad", "LICENCIA FEDERATIVA KICKBOXING ADULTO");
		esperado.put("KICKBOXING discapacidad infantil proporcional", "LICENCIA FEDERATIVA KICKBOXING INFANTIL");
		// Disciplinas sin licencias propias: los nombres de taekwondo (como el código anterior).
		esperado.put("PILATES adulto", "LICENCIA FEDERATIVA TAEKWONDO ADULTO");
		esperado.put("null infantil proporcional", "PARTE PROPORCIONAL LICENCIA FEDERATIVA TAEKWONDO INFANTIL");

		Map<String, String> real = new LinkedHashMap<>();
		TenantContext.ejecutarCon(ORG_TAEMOI, () -> {
			for (String caso : esperado.keySet()) {
				String[] partes = caso.split(" ");
				String disciplina = "null".equals(partes[0]) ? null : partes[0];
				boolean discapacidad = caso.contains("discapacidad");
				boolean menor = caso.contains("infantil");
				boolean proporcional = caso.contains("proporcional");
				real.put(caso, catalogo.resolverProductoLicencia(disciplina, discapacidad, menor, proporcional)
						.map(Producto::getConcepto).orElse(null));
			}
		});
		assertEquals(esperado, real);
	}

	/** Importe de cada variante: el precio del producto de esa variante (valores-actuales.md §7). */
	@Test
	void licencia_importeDeCadaVariante() {
		Map<String, Double> esperado = new LinkedHashMap<>();
		esperado.put("TAEKWONDO adulto", 46.0);
		esperado.put("TAEKWONDO infantil", 35.0);
		esperado.put("TAEKWONDO discapacidad", 30.0);
		esperado.put("TAEKWONDO adulto proporcional", 35.0);
		esperado.put("TAEKWONDO infantil proporcional", 20.0);
		esperado.put("TAEKWONDO discapacidad proporcional", 20.0);
		esperado.put("KICKBOXING adulto", 41.0);
		esperado.put("KICKBOXING infantil proporcional", 31.0);
		Map<String, Double> real = new LinkedHashMap<>();
		TenantContext.ejecutarCon(ORG_TAEMOI, () -> {
			for (String caso : esperado.keySet()) {
				real.put(caso, catalogo.resolverProductoLicencia(caso.split(" ")[0], caso.contains("discapacidad"),
						caso.contains("infantil"), caso.contains("proporcional")).map(Producto::getPrecio).orElse(null));
			}
		});
		assertEquals(esperado, real);
	}

	/**
	 * Carga individual de licencias (periodo actual: parte proporcional desde septiembre): el cargo es
	 * «<producto> DEL dd/MM/aaaa AL 31/12/aaaa» al precio del producto de la variante.
	 */
	@Test
	void licencia_cargaIndividual_conceptoEImporteDelPeriodoActual() {
		LocalDate hoy = LocalDate.now();
		int anio = hoy.getYear();
		boolean proporcional = hoy.getMonthValue() >= 9;
		String inicio = proporcional ? "01/09/" + anio : "01/01/" + anio;
		Object[][] casos = {
				// deporte, nacimiento, discapacidad, producto del periodo actual
				{ "TAEKWONDO", (anio - 30) + "-05-05", false, proporcional
						? "PARTE PROPORCIONAL LICENCIA FEDERATIVA TAEKWONDO ADULTO" : "LICENCIA FEDERATIVA TAEKWONDO ADULTO" },
				{ "TAEKWONDO", (anio - 8) + "-05-05", false, proporcional
						? "PARTE PROPORCIONAL LICENCIA FEDERATIVA TAEKWONDO INFANTIL" : "LICENCIA FEDERATIVA TAEKWONDO INFANTIL" },
				{ "TAEKWONDO", (anio - 30) + "-05-05", true, proporcional
						? "PARTE PROPORCIONAL LICENCIA FEDERATIVA TAEKWONDO DISCAPACIDAD" : "LICENCIA FEDERATIVA TAEKWONDO DISCAPACIDAD" },
				// Kickboxing: sin parte proporcional ni discapacidad; adulto si cumple 15 en el año.
				{ "KICKBOXING", (anio - 15) + "-12-31", true, "LICENCIA FEDERATIVA KICKBOXING ADULTO" },
				{ "KICKBOXING", (anio - 14) + "-01-01", false, "LICENCIA FEDERATIVA KICKBOXING INFANTIL" } };
		for (Object[] caso : casos) {
			long alumno = alumnoConLicencia((String) caso[0], (String) caso[1], (Boolean) caso[2], anio + "-01-10");
			TenantContext.ejecutarCon(ORG_TAEMOI, () -> tx.executeWithoutResult(s -> productoAlumnoService
					.cargarLicenciaIndividual(alumno, anio, (String) caso[0], false, LocalDate.of(anio, 1, 15))));
			Map<String, Object> cargo = jdbc.queryForMap("""
					SELECT pa.concepto, pa.precio, p.concepto AS producto FROM producto_alumno pa
					JOIN producto p ON p.id = pa.producto_id WHERE pa.alumno_id = ?""", alumno);
			String descripcion = caso[0] + " " + caso[1] + " " + caso[2];
			assertEquals(caso[3], cargo.get("producto"), descripcion);
			assertEquals(caso[3] + " DEL " + inicio + " AL 31/12/" + anio, cargo.get("concepto"), descripcion);
			assertEquals(PRECIO_LICENCIA.get((String) caso[3]), ((Number) cargo.get("precio")).doubleValue(), descripcion);
		}
	}

	/**
	 * Renovación con productos de licencia sin precio: importe de respaldo de valores-actuales.md §7
	 * (adulto 46/35, menor 35/20, discapacidad 30/20 antes/desde septiembre).
	 */
	@Test
	void licencia_renovacionSinPrecio_usaElImporteDeRespaldo() {
		LocalDate hoy = LocalDate.now();
		int anio = hoy.getYear();
		boolean proporcional = hoy.getMonthValue() >= 9;
		Object[][] casos = {
				{ (anio - 30) + "-05-05", false, proporcional ? 35.0 : 46.0 },
				{ (anio - 8) + "-05-05", false, proporcional ? 20.0 : 35.0 },
				{ (anio - 30) + "-05-05", true, proporcional ? 20.0 : 30.0 } };
		List<Long> licencias = PRECIO_LICENCIA.keySet().stream().filter(c -> c.contains("TAEKWONDO"))
				.map(this::idPorConcepto).toList();
		licencias.forEach(id -> jdbc.update("UPDATE producto SET precio = NULL WHERE id = ?", id));
		try {
			for (Object[] caso : casos) {
				long alumno = alumnoConLicencia("TAEKWONDO", (String) caso[0], (Boolean) caso[1], anio + "-01-10");
				TenantContext.ejecutarCon(ORG_TAEMOI,
						() -> tx.executeWithoutResult(s -> productoAlumnoService.renovarLicencia(alumno)));
				assertEquals((Double) caso[2], jdbc.queryForObject(
						"SELECT precio FROM producto_alumno WHERE alumno_id = ?", Double.class, alumno),
						caso[0] + " " + caso[1]);
			}
		} finally {
			for (Map.Entry<String, Double> licencia : PRECIO_LICENCIA.entrySet()) {
				jdbc.update("UPDATE producto SET precio = ? WHERE id = ?", licencia.getValue(),
						idPorConcepto(licencia.getKey()));
			}
		}
	}

	// ---------------- §7 Competidor ----------------

	@Test
	void competidor_productoDeCadaDisciplina_yCargoDelMes() {
		Map<String, String> productos = new LinkedHashMap<>();
		TenantContext.ejecutarCon(ORG_TAEMOI, () -> {
			for (String disciplina : List.of("TAEKWONDO", "KICKBOXING", "PILATES", "DEFENSA_PERSONAL_FEMENINA")) {
				productos.put(disciplina,
						catalogo.resolverProductoCompetidor(disciplina).map(Producto::getConcepto).orElse(null));
				if (!disciplina.equals("TAEKWONDO") && !disciplina.equals("KICKBOXING")) {
					assertTrue(catalogo.asegurarProductoCompetidor(disciplina).isEmpty(), disciplina);
				}
			}
		});
		Map<String, String> esperado = new LinkedHashMap<>();
		esperado.put("TAEKWONDO", "TARIFA COMPETIDOR TAEKWONDO");
		esperado.put("KICKBOXING", "TARIFA COMPETIDOR KICKBOXING");
		esperado.put("PILATES", null);
		esperado.put("DEFENSA_PERSONAL_FEMENINA", null);
		assertEquals(esperado, productos);
		// Es el enlace de la disciplina (disciplina.producto_competidor_id).
		for (String disciplina : List.of("TAEKWONDO", "KICKBOXING")) {
			assertEquals(idPorConcepto("TARIFA COMPETIDOR " + disciplina), jdbc.queryForObject(
					"SELECT producto_competidor_id FROM disciplina WHERE organizacion_id = 1 AND codigo = ?",
					Long.class, disciplina));
		}

		// Competidor de taekwondo y de kickboxing: mensualidad + tarifa de competidor del mes (20 €).
		for (String disciplina : List.of("TAEKWONDO", "KICKBOXING")) {
			long alumno = alumnoConLicencia(disciplina, "2000-05-05", false, "2041-01-10");
			jdbc.update("UPDATE alumno_deporte SET competidor = 1, cuantia_tarifa = 30 WHERE alumno_id = ?", alumno);
			TenantContext.ejecutarCon(ORG_TAEMOI, () -> tx.executeWithoutResult(s -> productoAlumnoService
					.cargarMensualidadIndividualPorDeporte(alumno, disciplina, "2041-03", false, LocalDate.of(2041, 3, 1))));
			Map<String, Double> cargos = jdbc.queryForList(
					"SELECT concepto, precio FROM producto_alumno WHERE alumno_id = ?", alumno).stream()
					.collect(Collectors.toMap(f -> (String) f.get("concepto"), f -> ((Number) f.get("precio")).doubleValue()));
			assertEquals(Map.of("MENSUALIDAD MARZO 2041 - " + disciplina, 30.0,
					"TARIFA COMPETIDOR " + disciplina + " MARZO 2041", 20.0), cargos, disciplina);
		}
	}

	// ---------------- §7 Tesorería ----------------

	/**
	 * Totales y categorías de tesorería de un conjunto fijo de cobros de 2042 (año solo de este test) (§7: categoría por
	 * concepto, periodo del concepto para mensualidades y tarifas de competidor). Incluye un cargo cuyo
	 * producto se borró (producto_id NULL): se clasifica por su concepto, como antes.
	 */
	@Test
	void tesoreria_totalesYCategorias_deUnConjuntoFijo() {
		long a = alumnoConLicencia("TAEKWONDO", "2000-05-05", false, "2042-01-10");
		long b = alumnoConLicencia("KICKBOXING", "2000-05-05", false, "2042-01-10");
		long adB = jdbc.queryForObject("SELECT id FROM alumno_deporte WHERE alumno_id = ?", Long.class, b);
		Long borrado = TenantContext.calcularCon(ORG_TAEMOI, () -> productoService.guardarProducto(
				new Producto("PRODUCTO TEMPORAL IT 2042", 30.0, null)).getId());
		Long matricula = TenantContext.calcularCon(ORG_TAEMOI, () -> productoService.guardarProducto(
				new Producto("MATRICULA 2042 IT", 40.0, null)).getId());
		Long camiseta = TenantContext.calcularCon(ORG_TAEMOI, () -> productoService.guardarProducto(
				new Producto("CAMISETA IT 2042", 12.5, null)).getId());
		try {
			cargo(a, null, "MENSUALIDAD", "MENSUALIDAD MARZO 2042 - TAEKWONDO", 30.0, true, "2042-03-01");
			cargo(b, adB, "MENSUALIDAD", "MENSUALIDAD MARZO 2042 - KICKBOXING", 30.0, false, "2042-02-27");
			cargo(a, null, "TARIFA COMPETIDOR TAEKWONDO", "TARIFA COMPETIDOR TAEKWONDO MARZO 2042", 20.0, false, "2042-03-02");
			cargo(a, null, "LICENCIA FEDERATIVA TAEKWONDO ADULTO",
					"LICENCIA FEDERATIVA TAEKWONDO ADULTO DEL 01/01/2042 AL 31/12/2042", 46.0, true, "2042-03-10");
			cargo(a, null, "RESERVA DE PLAZA", "RESERVA DE PLAZA 2042/2043 - TAEKWONDO", 10.0, true, "2042-03-15");
			cargo(a, null, "DERECHO A EXAMEN AZUL", "DERECHO A EXAMEN AZUL - TAEKWONDO", 25.0, false, "2042-03-20");
			cargoDe(a, matricula, "MATRICULA 2042 - TAEKWONDO", 40.0, true, "2042-03-05");
			cargoDe(a, camiseta, "CAMISETA IT 2042", 12.5, false, "2042-03-07");
			cargoDe(b, borrado, "MENSUALIDAD MARZO 2042 - PILATES", 30.0, false, "2042-04-02");
			cargo(a, null, "MENSUALIDAD", "MENSUALIDAD ABRIL 2042 - TAEKWONDO", 30.0, false, "2042-03-31");
			TenantContext.ejecutarCon(ORG_TAEMOI, () -> productoService.eliminarProducto(borrado));
			assertNull(jdbc.queryForObject("SELECT producto_id FROM producto_alumno WHERE alumno_id = ? AND concepto = ?",
					Long.class, b, "MENSUALIDAD MARZO 2042 - PILATES"));

			TesoreriaResumenDTO marzo = TenantContext.calcularCon(ORG_TAEMOI,
					() -> tesoreria.obtenerResumen(3, 2042, "TODOS", false));
			assertEquals(List.of(9L, 4L, 5L, 243.5, 126.0, 117.5, 2L), resumen(marzo));

			TesoreriaResumenDTO anio = TenantContext.calcularCon(ORG_TAEMOI,
					() -> tesoreria.obtenerResumen(null, 2042, "TODOS", false));
			assertEquals(List.of(10L, 4L, 6L, 273.5, 126.0, 147.5, 2L), resumen(anio));

			TesoreriaResumenDTO kickboxing = TenantContext.calcularCon(ORG_TAEMOI,
					() -> tesoreria.obtenerResumen(3, 2042, "KICKBOXING", false));
			assertEquals(List.of(1L, 0L, 1L, 30.0, 0.0, 30.0, 1L), resumen(kickboxing));

			Map<String, String> categorias = new LinkedHashMap<>();
			Map<String, String> deportes = new LinkedHashMap<>();
			TenantContext.ejecutarCon(ORG_TAEMOI, () -> {
				for (TesoreriaMovimientoDTO m : tesoreria.obtenerMovimientos(3, 2042, "TODOS", null, null, false, 0, 50)) {
					categorias.put(m.getConcepto(), m.getCategoria());
					deportes.put(m.getConcepto(), m.getDeporte());
				}
			});
			Map<String, String> esperado = new HashMap<>();
			esperado.put("MENSUALIDAD MARZO 2042 - TAEKWONDO", "MENSUALIDAD");
			esperado.put("MENSUALIDAD MARZO 2042 - KICKBOXING", "MENSUALIDAD");
			esperado.put("TARIFA COMPETIDOR TAEKWONDO MARZO 2042", "TARIFA_COMPETIDOR");
			esperado.put("LICENCIA FEDERATIVA TAEKWONDO ADULTO DEL 01/01/2042 AL 31/12/2042", "LICENCIA");
			esperado.put("RESERVA DE PLAZA 2042/2043 - TAEKWONDO", "RESERVA_PLAZA");
			esperado.put("DERECHO A EXAMEN AZUL - TAEKWONDO", "EXAMEN");
			esperado.put("MATRICULA 2042 - TAEKWONDO", "MATRICULA");
			esperado.put("CAMISETA IT 2042", "OTRO");
			esperado.put("MENSUALIDAD MARZO 2042 - PILATES", "MENSUALIDAD");
			assertEquals(esperado, categorias);
			assertEquals("KICKBOXING", deportes.get("MENSUALIDAD MARZO 2042 - KICKBOXING"));
			assertEquals("PILATES", deportes.get("MENSUALIDAD MARZO 2042 - PILATES"));
			assertEquals("GENERAL", deportes.get("CAMISETA IT 2042"));
		} finally {
			jdbc.update("DELETE FROM producto_alumno WHERE alumno_id IN (?, ?)", a, b);
			jdbc.update("DELETE FROM producto WHERE id IN (?, ?)", matricula, camiseta);
		}
	}

	// ---------------- Comportamiento heredado que se conserva (códigos de V24) ----------------

	/**
	 * Renombrar los productos de Taemoi (incluido un cambio solo de acento) no cambia el concepto de los
	 * cargos (literal heredado de su código) ni duplica nada al volver a generar. Antes de V27 la generación
	 * fallaba con el producto renombrado; ahora sigue funcionando con el concepto de siempre.
	 */
	@Test
	void renombrarProductosHeredados_noDuplicaCargos_yConservaElConcepto() {
		int anio = LocalDate.now().getYear();
		long alumno = alumnoConLicencia("TAEKWONDO", (anio - 30) + "-05-05", false, anio + "-01-10");
		jdbc.update("UPDATE alumno_deporte SET competidor = 1, cuantia_tarifa = 30 WHERE alumno_id = ?", alumno);
		String licencia = LocalDate.now().getMonthValue() >= 9
				? "PARTE PROPORCIONAL LICENCIA FEDERATIVA TAEKWONDO ADULTO" : "LICENCIA FEDERATIVA TAEKWONDO ADULTO";
		Map<Long, String> nombres = new LinkedHashMap<>();
		for (String concepto : List.of("MENSUALIDAD", "TARIFA COMPETIDOR TAEKWONDO", licencia)) {
			nombres.put(idPorConcepto(concepto), concepto);
		}
		Runnable generar = () -> TenantContext.ejecutarCon(ORG_TAEMOI, () -> tx.executeWithoutResult(s -> {
			productoAlumnoService.cargarMensualidadIndividualPorDeporte(alumno, "TAEKWONDO", "2041-06", true,
					LocalDate.of(2041, 6, 1));
		}));
		Runnable licenciar = () -> TenantContext.ejecutarCon(ORG_TAEMOI, () -> tx.executeWithoutResult(s -> productoAlumnoService
				.cargarLicenciaIndividual(alumno, anio, "TAEKWONDO", false, LocalDate.of(anio, 1, 15))));
		generar.run();
		licenciar.run();
		List<String> iniciales = conceptosDe(alumno);
		assertEquals(3, iniciales.size(), iniciales.toString());
		assertTrue(iniciales.contains("MENSUALIDAD JUNIO 2041 - TAEKWONDO"));
		assertTrue(iniciales.contains("TARIFA COMPETIDOR TAEKWONDO JUNIO 2041"));
		assertTrue(iniciales.stream().anyMatch(c -> c.startsWith(licencia + " DEL ")));
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
				"admin-it", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
		try {
			for (String sufijoNombre : List.of("acento", "otro nombre")) {
				for (Map.Entry<Long, String> producto : nombres.entrySet()) {
					String nuevo = sufijoNombre.equals("acento")
							? producto.getValue().replace("MENSUALIDAD", "MENSUALIDÁD").replace("COMPETIDOR", "COMPETIDÓR")
									.replace("FEDERATIVA", "FEDERATÍVA")
							: "Renombrado IT " + producto.getKey();
					Producto detalles = new Producto(nuevo, precioDe(producto.getKey()), null);
					ResponseEntity<?> respuesta = TenantContext.calcularCon(ORG_TAEMOI,
							() -> productoController.actualizarProducto(producto.getKey(), detalles));
					assertEquals(HttpStatus.OK, respuesta.getStatusCode(), "un código heredado se puede renombrar");
				}
				generar.run();
				assertThrows(IllegalStateException.class, licenciar::run,
						() -> "la licencia ya está asignada (mismo concepto): " + sufijoNombre + " " + conceptosDe(alumno));
				assertEquals(iniciales, conceptosDe(alumno), sufijoNombre);
			}
		} finally {
			nombres.forEach((id, concepto) -> jdbc.update("UPDATE producto SET concepto = ? WHERE id = ?", concepto, id));
		}
	}

	/** Producto de competidor con código heredado: 20 € fijos aunque el producto cueste otra cosa (como antes). */
	@Test
	void competidorHeredado_cobra20AunqueElProductoTengaOtroPrecio() {
		long alumno = alumnoConLicencia("TAEKWONDO", "2000-05-05", false, "2041-01-10");
		jdbc.update("UPDATE alumno_deporte SET competidor = 1, cuantia_tarifa = 30 WHERE alumno_id = ?", alumno);
		Long competidor = idPorConcepto("TARIFA COMPETIDOR TAEKWONDO");
		jdbc.update("UPDATE producto SET precio = 25 WHERE id = ?", competidor);
		try {
			TenantContext.ejecutarCon(ORG_TAEMOI, () -> tx.executeWithoutResult(s -> productoAlumnoService
					.cargarMensualidadIndividualPorDeporte(alumno, "TAEKWONDO", "2041-07", false, LocalDate.of(2041, 7, 1))));
			assertEquals(20.0, jdbc.queryForObject("SELECT precio FROM producto_alumno WHERE alumno_id = ? AND concepto = ?",
					Double.class, alumno, "TARIFA COMPETIDOR TAEKWONDO JULIO 2041"));
		} finally {
			jdbc.update("UPDATE producto SET precio = 20 WHERE id = ?", competidor);
		}
	}

	/**
	 * Un producto SIN clasificar que el código anterior encontraba por el concepto heredado (variante con
	 * otras mayúsculas) se sigue cobrando antes que el clasificado: reserva de plaza y licencia.
	 */
	@Test
	void varianteSinClasificar_seSigueUsandoComoAntes() {
		Long reserva = idPorConcepto("RESERVA DE PLAZA");
		Long licenciaKb = idPorConcepto("LICENCIA FEDERATIVA KICKBOXING ADULTO");
		// El clasificado deja de coincidir con el concepto heredado; la variante sin clasificar sí coincide
		// (sin distinguir mayúsculas, como findFirstByConcepto).
		jdbc.update("UPDATE producto SET concepto = 'RESERVA IT CLASIFICADA' WHERE id = ?", reserva);
		jdbc.update("UPDATE producto SET concepto = 'LICENCIA KB IT CLASIFICADA' WHERE id = ?", licenciaKb);
		jdbc.update("INSERT INTO producto (concepto, precio, organizacion_id) VALUES ('Reserva de Plaza', 12.0, 1)");
		Long varianteReserva = jdbc.queryForObject("SELECT MAX(id) FROM producto", Long.class);
		jdbc.update("INSERT INTO producto (concepto, precio, organizacion_id) VALUES ('Licencia Federativa Kickboxing Adulto', 44.0, 1)");
		Long varianteLicencia = jdbc.queryForObject("SELECT MAX(id) FROM producto", Long.class);
		try {
			// Lo que elegía el código anterior (findFirstByConcepto del literal): la variante sin clasificar.
			Producto antesReserva = TenantContext.calcularCon(ORG_TAEMOI,
					() -> productoRepository.findFirstByConcepto("RESERVA DE PLAZA").orElseThrow());
			Producto antesLicencia = TenantContext.calcularCon(ORG_TAEMOI,
					() -> productoRepository.findFirstByConcepto("LICENCIA FEDERATIVA KICKBOXING ADULTO").orElseThrow());
			assertNull(antesReserva.getCodigo());
			assertNull(antesLicencia.getCodigo());
			TenantContext.ejecutarCon(ORG_TAEMOI, () -> {
				assertEquals(antesReserva.getId(), catalogo.resolverProductoReservaPlaza("TAEKWONDO").orElseThrow().getId());
				assertEquals(antesLicencia.getId(),
						catalogo.resolverProductoLicencia("KICKBOXING", false, false, false).orElseThrow().getId());
			});
			long alumno = alumnoConLicencia("TAEKWONDO", "2000-05-05", false, "2041-01-10");
			TenantContext.ejecutarCon(ORG_TAEMOI, () -> tx.executeWithoutResult(s -> productoAlumnoService
					.reservarPlazaPorDeporte(alumno, "TAEKWONDO", "RESERVA DE PLAZA IT - TAEKWONDO", false, false)));
			Map<String, Object> cargo = jdbc.queryForMap(
					"SELECT producto_id, precio FROM producto_alumno WHERE alumno_id = ?", alumno);
			assertEquals(antesReserva.getId(), ((Number) cargo.get("producto_id")).longValue());
			assertEquals(antesReserva.getPrecio(), ((Number) cargo.get("precio")).doubleValue());
		} finally {
			jdbc.update("DELETE FROM producto_alumno WHERE producto_id IN (?, ?)", varianteReserva, varianteLicencia);
			jdbc.update("DELETE FROM producto WHERE id IN (?, ?)", varianteReserva, varianteLicencia);
			jdbc.update("UPDATE producto SET concepto = 'RESERVA DE PLAZA' WHERE id = ?", reserva);
			jdbc.update("UPDATE producto SET concepto = 'LICENCIA FEDERATIVA KICKBOXING ADULTO' WHERE id = ?", licenciaKb);
		}
	}

	/**
	 * Cargos de productos con código heredado y concepto editado a mano: tesorería y el derecho a examen
	 * miran SOLO el concepto, como antes (el tipo del catálogo no decide).
	 */
	@Test
	void cargoHeredadoConConceptoEditado_seClasificaPorConcepto() {
		long alumno = alumnoConLicencia("TAEKWONDO", "2000-05-05", false, "2043-01-10");
		cargo(alumno, null, "MENSUALIDAD", "CUOTA ESPECIAL MAYO 2043", 30.0, false, "2043-04-30");
		cargo(alumno, null, "DERECHO A EXAMEN AZUL", "PAGO PENDIENTE AZUL", 25.0, false, "2043-04-15");
		Map<String, String> categorias = new LinkedHashMap<>();
		TenantContext.ejecutarCon(ORG_TAEMOI, () -> tesoreria.obtenerMovimientos(null, 2043, "TODOS", null, null, false, 0, 10)
				.forEach(m -> categorias.put(m.getConcepto(), m.getCategoria())));
		assertEquals(Map.of("CUOTA ESPECIAL MAYO 2043", "OTRO", "PAGO PENDIENTE AZUL", "OTRO"), categorias);
		// Periodo por la fecha (abril), no por el concepto (mayo): no es una mensualidad por concepto.
		assertEquals(0L, TenantContext.calcularCon(ORG_TAEMOI, () -> tesoreria.obtenerResumen(5, 2043, "TODOS", false))
				.getTotalMovimientos());

		Long examen = jdbc.queryForObject("SELECT id FROM producto_alumno WHERE alumno_id = ? AND concepto = 'PAGO PENDIENTE AZUL'",
				Long.class, alumno);
		ProductoAlumnoDTO pago = new ProductoAlumnoDTO();
		pago.setPagado(true);
		TenantContext.ejecutarCon(ORG_TAEMOI, () -> productoAlumnoService.actualizarProductoAlumno(examen, pago));
		assertFalse(jdbc.queryForObject("SELECT tiene_derecho_examen FROM alumno WHERE id = ?", Boolean.class, alumno));
	}

	private List<String> conceptosDe(long alumno) {
		return jdbc.queryForList("SELECT concepto FROM producto_alumno WHERE alumno_id = ? ORDER BY concepto",
				String.class, alumno);
	}

	private double precioDe(long producto) {
		Double precio = jdbc.queryForObject("SELECT precio FROM producto WHERE id = ?", Double.class, producto);
		return precio == null ? 0.0 : precio;
	}

	// ---------------- Respaldo: cargo de un producto borrado ----------------

	/** Pagar un cargo cuyo producto se borró funciona (sin catálogo que resolver) y no promueve. */
	@Test
	void pagarCargoDeProductoBorrado_noFalla() {
		long alumno = alumnoConLicencia("TAEKWONDO", "2000-05-05", false, "2041-01-10");
		long ad = jdbc.queryForObject("SELECT id FROM alumno_deporte WHERE alumno_id = ?", Long.class, alumno);
		Long temporal = TenantContext.calcularCon(ORG_TAEMOI, () -> productoService.guardarProducto(
				new Producto("PRODUCTO BORRADO IT", 9.0, null)).getId());
		cargoDe(alumno, temporal, "PRODUCTO BORRADO IT", 9.0, false, "2041-03-01");
		jdbc.update("UPDATE producto_alumno SET alumno_deporte_id = ? WHERE alumno_id = ?", ad, alumno);
		TenantContext.ejecutarCon(ORG_TAEMOI, () -> productoService.eliminarProducto(temporal));
		Long cargo = jdbc.queryForObject("SELECT id FROM producto_alumno WHERE alumno_id = ?", Long.class, alumno);

		ProductoAlumnoDTO pago = new ProductoAlumnoDTO();
		pago.setPagado(true);
		ProductoAlumnoDTO pagado = TenantContext.calcularCon(ORG_TAEMOI,
				() -> productoAlumnoService.actualizarProductoAlumno(cargo, pago));
		assertTrue(pagado.getPagado());
		assertNull(pagado.getProductoId());
		assertFalse(jdbc.queryForObject("SELECT tiene_derecho_examen FROM alumno WHERE id = ?", Boolean.class, alumno));
	}

	// ---------------------------------------------------------------------------------

	private List<Object> resumen(TesoreriaResumenDTO r) {
		return List.of(r.getTotalMovimientos(), r.getTotalPagados(), r.getTotalPendientes(), r.getImporteTotal(),
				r.getImportePagado(), r.getImportePendiente(), r.getAlumnosConPendientes());
	}

	private void cargo(long alumno, Long alumnoDeporte, String producto, String concepto, double precio,
			boolean pagado, String fecha) {
		Long productoId = idPorConcepto(producto);
		assertNotNull(productoId, producto);
		cargoDe(alumno, productoId, concepto, precio, pagado, fecha);
		if (alumnoDeporte != null) {
			jdbc.update("UPDATE producto_alumno SET alumno_deporte_id = ? WHERE alumno_id = ? AND concepto = ?",
					alumnoDeporte, alumno, concepto);
		}
	}

	private void cargoDe(long alumno, Long productoId, String concepto, double precio, boolean pagado, String fecha) {
		jdbc.update("""
				INSERT INTO producto_alumno (alumno_id, producto_id, concepto, precio, cantidad, pagado,
				  fecha_asignacion, fecha_pago, organizacion_id)
				VALUES (?, ?, ?, ?, 1, ?, ?, ?, 1)""", alumno, productoId, concepto, precio, pagado,
				java.sql.Date.valueOf(fecha), pagado ? java.sql.Date.valueOf(fecha) : null);
	}

	/** Alta por el controlador con los campos de deporte único (sin cuantía). */
	private long altaDeporteUnico(String deporte, String tarifa, String rol) {
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
				"admin-it", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
		String json = """
				{"nombre":"TARIFA","apellidos":"%s","fechaNacimiento":"1990-03-04","direccion":"Calle 1",
				 "telefono":600000001,"email":"tarifa-%s@example.test","deporte":"%s","tipoTarifa":"%s",
				 "rolFamiliar":"%s","grupoFamiliar":"G-IT"}
				""".formatted(tarifa, sufijo(), deporte, tarifa, rol);
		ResponseEntity<?> respuesta = TenantContext.calcularCon(ORG_TAEMOI, () -> alumnoController.crearAlumno(json, null));
		assertEquals(HttpStatus.CREATED, respuesta.getStatusCode(), String.valueOf(respuesta.getBody()));
		long id = ((AlumnoDTO) respuesta.getBody()).getId();
		alumnosCreados.add(id);
		return id;
	}

	/** Alumno de Taemoi con una inscripción con licencia (fecha de licencia dada). */
	private long alumnoConLicencia(String deporte, String nacimiento, boolean discapacidad, String fechaLicencia) {
		int expediente = 800000 + (int) (Math.abs(UUID.randomUUID().getMostSignificantBits()) % 190000);
		jdbc.update("""
				INSERT INTO alumno (activo, apellidos, autorizacion_web, direccion, fecha_nacimiento, nombre,
				  numero_expediente, telefono, tiene_derecho_examen, tiene_discapacidad, organizacion_id)
				VALUES (1, 'Licencia', 1, 'Calle 1', ?, 'IT', ?, '600000000', 0, ?, 1)""",
				java.sql.Date.valueOf(nacimiento), expediente, discapacidad);
		long id = jdbc.queryForObject("SELECT id FROM alumno WHERE organizacion_id = 1 AND numero_expediente = ?",
				Long.class, expediente);
		jdbc.update("""
				INSERT INTO alumno_deporte (activo, apto_para_examen, alumno_id, competidor, principal,
				  cuantia_tarifa, tiene_licencia, fecha_licencia, organizacion_id, fecha_alta,
				  fecha_alta_inicial, disciplina_id)
				VALUES (1, 0, ?, 0, 1, NULL, 1, ?, 1, '2040-09-01', '2040-09-01',
				  (SELECT d.id FROM disciplina d WHERE d.organizacion_id = 1 AND d.codigo = ?))""",
				id, java.sql.Date.valueOf(fechaLicencia), deporte);
		alumnosCreados.add(id);
		return id;
	}

	private Long idPorConcepto(String concepto) {
		List<Long> ids = jdbc.queryForList("""
				SELECT id FROM producto WHERE organizacion_id = 1 AND CAST(concepto AS BINARY) = CAST(? AS BINARY)
				ORDER BY codigo IS NULL, id""", Long.class, concepto);
		return ids.isEmpty() ? null : ids.get(0);
	}

	private void fijarPrecio(Long productoId, double precio) {
		assertNotNull(productoId);
		preciosOriginales.putIfAbsent(productoId,
				jdbc.queryForObject("SELECT precio FROM producto WHERE id = ?", Double.class, productoId));
		jdbc.update("UPDATE producto SET precio = ? WHERE id = ?", precio, productoId);
	}

	private static String sufijo() {
		return UUID.randomUUID().toString().substring(0, 8);
	}
}
