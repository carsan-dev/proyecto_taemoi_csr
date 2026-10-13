package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.config.TarifaConfig;
import com.taemoi.project.entities.Deporte;
import com.taemoi.project.entities.Grado;
import com.taemoi.project.entities.Organizacion;
import com.taemoi.project.entities.Producto;
import com.taemoi.project.entities.RolFamiliar;
import com.taemoi.project.entities.Tarifa;
import com.taemoi.project.entities.TipoGrado;
import com.taemoi.project.entities.TipoProducto;
import com.taemoi.project.entities.TipoTarifa;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.OrganizacionRepository;
import com.taemoi.project.repositories.ProductoRepository;
import com.taemoi.project.repositories.TarifaRepository;
import com.taemoi.project.services.AlumnoDeporteService;
import com.taemoi.project.services.ProductoService;
import com.taemoi.project.services.ProductosCatalogoService;
import com.taemoi.project.services.ProgresionGradosService;
import com.taemoi.project.services.TarifasService;

/**
 * Paridad de tarifas y productos (V24) con el código que sustituyen, para Taemoi (organización
 * 1): {@link TarifaConfig}, las búsquedas {@code findFirstByConcepto} por las constantes de
 * {@code InicializadorDatos}, {@code ProductoAlumnoServiceImpl}, {@code AlumnoServiceImpl} y
 * {@link TipoGrado}, y la activación de grado al pagar ({@code fromProductoRecompensa} /
 * {@code fromProductoExamen}). Las combinaciones se generan desde los enums.
 *
 * <p>Antes de comparar se asegura que existe un producto por cada nombre conocido (en producción
 * los crea la gestión, no una semilla) y se repite V24, que es repetible.
 *
 * <p>DIVERGENCIA DELIBERADA: el grado que activa un producto se resuelve por enlace y es el
 * correcto; {@code TipoGrado.fromProductoExamen} (contains) devuelve el grado de color anterior
 * para intermedios y PUM. Ver {@link #DIVERGENCIAS_GRADO}.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TarifasProductosParidadIntegrationTest extends MySqlIntegrationTest {

	private static final long ORG_TAEMOI = 1L;
	private static final List<Deporte> CON_GRADOS = List.of(Deporte.TAEKWONDO, Deporte.KICKBOXING);
	private static final List<RolFamiliar> ROLES_Y_NULO = Arrays.asList(RolFamiliar.PADRE, RolFamiliar.MADRE,
			RolFamiliar.HIJO, RolFamiliar.HIJA, RolFamiliar.NINGUNO, null);

	/**
	 * Conceptos en los que el servicio nuevo NO da lo mismo que el código actual, con
	 * {legado, nuevo} (null = sin grado).
	 * <ul>
	 * <li>Intermedios y PUM: el legado devuelve el grado de color anterior (error conocido, ver
	 * valores-actuales.md §1); el nuevo, el grado correcto por enlace.</li>
	 * <li>ROJO BORDADO (recompensa): CAMBIO INTENCIONADO por decisión del usuario (el bordado es
	 * solo el cinturón que se elige al pasar a rojo). El legado no activa ningún grado (su concepto
	 * no contiene el de ROJO); el nuevo concede ROJO por {@code producto.grado_id}. El examen
	 * bordado ya activaba ROJO y coincide.</li>
	 * </ul>
	 */
	static final Map<String, String[]> DIVERGENCIAS_GRADO = new LinkedHashMap<>();

	static {
		DIVERGENCIAS_GRADO.put("DERECHO A EXAMEN AMARILLO/NARANJA", new String[] { "AMARILLO", "AMARILLO_NARANJA" });
		DIVERGENCIAS_GRADO.put("DERECHO A EXAMEN NARANJA/VERDE", new String[] { "NARANJA", "NARANJA_VERDE" });
		DIVERGENCIAS_GRADO.put("DERECHO A EXAMEN VERDE/AZUL", new String[] { "VERDE", "VERDE_AZUL" });
		DIVERGENCIAS_GRADO.put("DERECHO A EXAMEN AZUL/ROJO", new String[] { "AZUL", "AZUL_ROJO" });
		DIVERGENCIAS_GRADO.put("DERECHO A EXAMEN ROJO/NEGRO 1º PUM", new String[] { "ROJO", "ROJO_NEGRO_1_PUM" });
		DIVERGENCIAS_GRADO.put("DERECHO A EXAMEN ROJO/NEGRO 2º PUM", new String[] { "ROJO", "ROJO_NEGRO_2_PUM" });
		DIVERGENCIAS_GRADO.put("DERECHO DE CAMBIO A ROJO BORDADO POR RECOMPENSA", new String[] { null, "ROJO" });
	}

	@Autowired TarifaConfig tarifaConfig;
	@Autowired AlumnoDeporteService alumnoDeporteService;
	@Autowired TarifasService tarifas;
	@Autowired TarifaRepository tarifaRepository;
	@Autowired ProductosCatalogoService catalogo;
	@Autowired ProgresionGradosService progresion;
	@Autowired ProductoRepository productoRepository;
	@Autowired ProductoService productoService;
	@Autowired OrganizacionRepository organizaciones;
	@Autowired DataSource dataSource;
	@Autowired JdbcTemplate jdbc;
	@Autowired ObjectMapper objectMapper;

	final List<Long> insertados = new ArrayList<>();
	long org2;

	@BeforeAll
	void asegurarProductosYRepetirV24() throws Exception {
		for (String concepto : TarifasProductosMigracionIntegrationTest.CONOCIDOS.keySet()) {
			boolean existe = jdbc.queryForObject("""
					SELECT COUNT(*) FROM producto
					WHERE organizacion_id = 1 AND CAST(concepto AS BINARY) = CAST(? AS BINARY)""",
					Integer.class, concepto) > 0;
			if (!existe) {
				jdbc.update("INSERT INTO producto (concepto, precio, organizacion_id) VALUES (?, 15.0, 1)", concepto);
				insertados.add(jdbc.queryForObject("""
						SELECT MAX(id) FROM producto
						WHERE organizacion_id = 1 AND CAST(concepto AS BINARY) = CAST(? AS BINARY)""", Long.class, concepto));
			}
		}
		try (Connection conexion = dataSource.getConnection()) {
			ScriptUtils.executeSqlScript(conexion, new EncodedResource(
					new ClassPathResource("db/migration/V24__tarifas_y_productos.sql"), StandardCharsets.UTF_8));
		}

		Organizacion org = new Organizacion();
		org.setSlug("it-tarifas-" + UUID.randomUUID().toString().substring(0, 8));
		org.setNombre("Escuela sin tarifas");
		org2 = TenantContext.calcularCon(ORG_TAEMOI, () -> organizaciones.save(org).getId());
		assertNotEquals(ORG_TAEMOI, org2);
	}

	@AfterAll
	void borrarProductosInsertados() {
		for (Long id : insertados) {
			jdbc.update("DELETE FROM producto WHERE id = ?", id);
		}
	}

	@AfterEach
	void sinFugaDeOrganizacion() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
	}

	private static String codigo(Deporte deporte) {
		return deporte == null ? null : deporte.name();
	}

	private static Long id(Optional<Producto> producto) {
		return producto.map(Producto::getId).orElse(null);
	}

	private Long idLegado(String concepto) {
		return concepto == null ? null : id(productoRepository.findFirstByConcepto(concepto));
	}

	private static void igualImporte(double esperado, BigDecimal real, String caso) {
		assertEquals(0, BigDecimal.valueOf(esperado).compareTo(real), caso + ": " + esperado + " vs " + real);
		assertEquals(esperado, real.doubleValue(), 0.0, caso);
	}

	// ---------------- TarifaConfig ----------------

	@Test
	void importe_igualQueTarifaConfig_paraCadaTarifaYRol() {
		TenantContext.ejecutarCon(ORG_TAEMOI, () -> {
			int comparadas = 0;
			for (TipoTarifa tipo : TipoTarifa.values()) {
				igualImporte(tarifaConfig.obtenerCuantia(tipo), tarifas.obtenerImporte(tipo.name()), tipo.name());
				for (RolFamiliar rol : ROLES_Y_NULO) {
					igualImporte(tarifaConfig.obtenerCuantiaConRol(tipo, rol), tarifas.obtenerImporte(tipo.name(), rol),
							tipo + "/" + rol);
					comparadas++;
				}
			}
			assertEquals(TipoTarifa.values().length * ROLES_Y_NULO.size(), comparadas);
			// Literal: la representación de PADRES_HIJOS y HERMANOS.
			Tarifa padresHijos = tarifas.obtenerPorCodigo("PADRES_HIJOS");
			assertEquals(new BigDecimal("0.00"), padresHijos.getImporte());
			assertEquals(new BigDecimal("28.00"), padresHijos.getImporteAdulto());
			assertEquals(new BigDecimal("26.00"), padresHijos.getImporteMenor());
			Tarifa hermanos = tarifas.obtenerPorCodigo("HERMANOS");
			assertEquals(new BigDecimal("26.00"), hermanos.getImporte());
			assertNull(hermanos.getImporteAdulto());
			assertNull(hermanos.getImporteMenor());
		});
	}

	@Test
	void listado_igualQueObtenerTodasLasCuantias_mismoOrden() {
		TenantContext.ejecutarCon(ORG_TAEMOI, () -> {
			Map<TipoTarifa, Double> legado = tarifaConfig.obtenerTodasLasCuantias();
			List<Tarifa> nuevas = tarifas.listarActivas();
			assertEquals(legado.keySet().stream().map(Enum::name).toList(),
					nuevas.stream().map(Tarifa::getCodigo).toList());
			for (Tarifa t : nuevas) {
				igualImporte(legado.get(TipoTarifa.valueOf(t.getCodigo())), t.getImporte(), t.getCodigo());
				assertTrue(tarifaConfig.tieneCuantiaDefinida(TipoTarifa.valueOf(t.getCodigo())));
			}
		});
	}

	@Test
	void tarifaNulaODesconocida_lanzaIllegalArgument_comoTarifaConfig() {
		assertThrows(IllegalArgumentException.class, () -> tarifaConfig.obtenerCuantia(null));
		assertThrows(IllegalArgumentException.class, () -> tarifaConfig.obtenerCuantiaConRol(null, RolFamiliar.PADRE));
		assertThrows(IllegalArgumentException.class, () -> tarifas.calcularImporte(null, RolFamiliar.PADRE));
		TenantContext.ejecutarCon(ORG_TAEMOI, () -> {
			assertThrows(IllegalArgumentException.class, () -> tarifas.obtenerImporte(null));
			assertThrows(IllegalArgumentException.class, () -> tarifas.obtenerImporte(null, RolFamiliar.PADRE));
			assertThrows(IllegalArgumentException.class, () -> tarifas.obtenerImporte("NO_EXISTE"));
			assertTrue(tarifas.buscarPorCodigo(null).isEmpty());
		});
	}

	/** Regla de PreinscripcionService.validarDatosDeporte (copiada literalmente, privada allí). */
	private static boolean admitidaHoy(Deporte deporte, TipoTarifa tarifa) {
		return switch (deporte) {
		case TAEKWONDO -> tarifa != TipoTarifa.KICKBOXING && tarifa != TipoTarifa.PILATES
				&& tarifa != TipoTarifa.DEFENSA_PERSONAL_FEMENINA;
		case KICKBOXING -> tarifa == TipoTarifa.KICKBOXING;
		case PILATES -> tarifa == TipoTarifa.PILATES;
		case DEFENSA_PERSONAL_FEMENINA -> tarifa == TipoTarifa.DEFENSA_PERSONAL_FEMENINA;
		};
	}

	@Test
	void disciplinaAdmitida_igualQuePreinscripcion() {
		TenantContext.ejecutarCon(ORG_TAEMOI, () -> {
			for (Deporte deporte : Deporte.values()) {
				for (TipoTarifa tarifa : TipoTarifa.values()) {
					assertEquals(admitidaHoy(deporte, tarifa), tarifas.admiteDisciplina(tarifa.name(), deporte.name()),
							deporte + "/" + tarifa);
				}
			}
		});
	}

	// ---------------- Productos por nombre ----------------

	@Test
	void cadaNombreConocido_mismoProductoPorCodigoQuePorConcepto() {
		TenantContext.ejecutarCon(ORG_TAEMOI, () -> {
			for (Map.Entry<String, String[]> e : TarifasProductosMigracionIntegrationTest.CONOCIDOS.entrySet()) {
				Long legado = idLegado(e.getKey());
				assertTrue(legado != null, "Falta el producto " + e.getKey());
				Producto nuevo = catalogo.buscarPorCodigo(e.getValue()[0]).orElseThrow(
						() -> new AssertionError("Sin codigo " + e.getValue()[0]));
				assertEquals(legado, nuevo.getId(), e.getKey());
				assertEquals(e.getKey(), nuevo.getConcepto());
				assertEquals(TipoProducto.valueOf(e.getValue()[1]), nuevo.getTipo(), e.getKey());
			}
			assertEquals(40, TarifasProductosMigracionIntegrationTest.CONOCIDOS.size());
		});
	}

	@Test
	void competidorPorDisciplina_igualQueObtenerNombreProductoCompetidor() {
		TenantContext.ejecutarCon(ORG_TAEMOI, () -> {
			for (Deporte deporte : Deporte.values()) {
				String nombreHoy = switch (deporte) {
				case TAEKWONDO -> "TARIFA COMPETIDOR TAEKWONDO";
				case KICKBOXING -> "TARIFA COMPETIDOR KICKBOXING";
				default -> null;
				};
				assertEquals(idLegado(nombreHoy), id(catalogo.buscarProductoCompetidor(codigo(deporte))), deporte.name());
				assertTrue(catalogo.buscarProductoLicencia(codigo(deporte)).isEmpty(), deporte.name());
			}
			assertTrue(catalogo.buscarProductoCompetidor(null).isEmpty());
		});
	}

	@Test
	void productoDeExamenYRecompensaPorGrado_igualQueResolverConceptoProducto() {
		TenantContext.ejecutarCon(ORG_TAEMOI, () -> {
			int comparadas = 0;
			for (Deporte deporte : CON_GRADOS) {
				for (TipoGrado grado : TipoGrado.values()) {
					for (boolean porRecompensa : new boolean[] { false, true }) {
						String caso = deporte + " " + grado + " recompensa=" + porRecompensa;
						boolean tiene = porRecompensa ? grado.tieneProductoRecompensa() : grado.tieneProductoExamen();
						Long legado = tiene ? idLegado(grado.obtenerNombreProducto(porRecompensa)) : null;
						Optional<Producto> nuevo = porRecompensa
								? catalogo.buscarProductoRecompensa(codigo(deporte), grado.name())
								: catalogo.buscarProductoExamen(codigo(deporte), grado.name());
						assertEquals(legado, id(nuevo), caso);
						nuevo.ifPresent(p -> assertEquals(porRecompensa ? TipoProducto.RECOMPENSA : TipoProducto.EXAMEN,
								p.getTipo(), caso));
						comparadas++;
					}
				}
			}
			assertEquals(2 * TipoGrado.values().length * 2, comparadas);
			// Rojo bordado (AlumnoServiceImpl): por codigo.
			assertEquals(idLegado("DERECHO A EXAMEN ROJO BORDADO"), id(catalogo.buscarPorCodigo("EXAMEN_ROJO_BORDADO")));
			assertEquals(idLegado("DERECHO DE CAMBIO A ROJO BORDADO POR RECOMPENSA"),
					id(catalogo.buscarPorCodigo("RECOMPENSA_ROJO_BORDADO")));
			// Sin grados o nulos: vacío.
			assertTrue(catalogo.buscarProductoExamen("PILATES", "AMARILLO").isEmpty());
			assertTrue(catalogo.buscarProductoExamen(null, "AMARILLO").isEmpty());
			assertTrue(catalogo.buscarProductoExamen("TAEKWONDO", null).isEmpty());
		});
	}

	@Test
	void listarPorTipo_agrupaLosConocidos() {
		TenantContext.ejecutarCon(ORG_TAEMOI, () -> {
			assertEquals(Set.of("LICENCIA_TAEKWONDO_ADULTO", "LICENCIA_TAEKWONDO_INFANTIL",
					"LICENCIA_TAEKWONDO_DISCAPACIDAD", "LICENCIA_TAEKWONDO_ADULTO_PROPORCIONAL",
					"LICENCIA_TAEKWONDO_INFANTIL_PROPORCIONAL", "LICENCIA_TAEKWONDO_DISCAPACIDAD_PROPORCIONAL"),
					codigos(catalogo.listarPorTipo(TipoProducto.LICENCIA, "TAEKWONDO")));
			assertEquals(Set.of("LICENCIA_KICKBOXING_ADULTO", "LICENCIA_KICKBOXING_INFANTIL"),
					codigos(catalogo.listarPorTipo(TipoProducto.LICENCIA, "KICKBOXING")));
			assertEquals(Set.of("LICENCIA_FEDERATIVA"), codigos(catalogo.listarPorTipo(TipoProducto.LICENCIA, null)));
			assertEquals(Set.of("MENSUALIDAD"), codigos(catalogo.listarPorTipo(TipoProducto.MENSUALIDAD, null)));
			assertEquals(Set.of("RESERVA_PLAZA"), codigos(catalogo.listarPorTipo(TipoProducto.RESERVA_PLAZA, null)));
			assertEquals(Set.of("COMPETIDOR_KICKBOXING"),
					codigos(catalogo.listarPorTipo(TipoProducto.COMPETIDOR, "KICKBOXING")));
			assertEquals(16, catalogo.listarPorTipo(TipoProducto.EXAMEN, null).size());
			assertEquals(11, catalogo.listarPorTipo(TipoProducto.RECOMPENSA, null).size());
			assertEquals(List.of(), catalogo.listarPorTipo(TipoProducto.EXAMEN, "TAEKWONDO"));
			assertEquals(List.of(), catalogo.listarPorTipo(TipoProducto.MENSUALIDAD, "PILATES"));
			assertEquals(List.of(), catalogo.listarPorTipo(null, null));
		});
	}

	private static Set<String> codigos(List<Producto> lista) {
		return new HashSet<>(lista.stream().map(Producto::getCodigo).toList());
	}

	// ---------------- Grado desde el producto pagado ----------------

	/** Lo que hace hoy ProductoAlumnoServiceImpl.activarGradoPorRecompensaPagada con el concepto. */
	private static String gradoLegado(String concepto) {
		TipoGrado g = TipoGrado.fromProductoRecompensa(concepto);
		if (g == null) {
			g = TipoGrado.fromProductoExamen(concepto);
		}
		return g == null ? null : g.name();
	}

	@Test
	void gradoActivadoAlPagar_igualQueElLegado_salvoLasDivergenciasListadas() {
		TenantContext.ejecutarCon(ORG_TAEMOI, () -> {
			Map<String, String> divergenciasVistas = new TreeMap<>();
			int iguales = 0;
			for (Deporte deporte : CON_GRADOS) {
				Long sistema = progresion.buscarGrado(codigo(deporte), "BLANCO").orElseThrow().getSistemaGrados().getId();
				for (String concepto : TarifasProductosMigracionIntegrationTest.CONOCIDOS.keySet()) {
					Long productoId = idLegado(concepto);
					Optional<Grado> nuevo = catalogo.buscarGradoPorProducto(productoId, codigo(deporte));
					nuevo.ifPresent(g -> assertEquals(sistema, g.getSistemaGrados().getId(), "sistema de " + deporte));
					String nuevoCodigo = nuevo.map(Grado::getCodigo).orElse(null);
					// Concepto del producto y concepto tal como lo guarda hoy el cargo (« - DEPORTE»).
					for (String guardado : List.of(concepto, concepto + " - " + deporte.name())) {
						String legado = gradoLegado(guardado);
						String caso = deporte + " «" + guardado + "»";
						String[] divergencia = DIVERGENCIAS_GRADO.get(concepto);
						if (divergencia == null) {
							assertEquals(legado, nuevoCodigo, caso);
							iguales++;
						} else {
							assertEquals(divergencia[0], legado, "legado " + caso);
							assertEquals(divergencia[1], nuevoCodigo, "nuevo " + caso);
							divergenciasVistas.put(concepto, legado + " -> " + nuevoCodigo);
						}
					}
				}
			}
			assertEquals(DIVERGENCIAS_GRADO.keySet(), divergenciasVistas.keySet());
			assertEquals(2 * 2 * (TarifasProductosMigracionIntegrationTest.CONOCIDOS.size() - DIVERGENCIAS_GRADO.size()),
					iguales);
		});
	}

	@Test
	void gradoPorEnlace_esSiempreElGradoDelNombreDelProducto() {
		TenantContext.ejecutarCon(ORG_TAEMOI, () -> {
			for (Deporte deporte : CON_GRADOS) {
				for (TipoGrado grado : TipoGrado.values()) {
					if (grado.tieneProductoExamen()) {
						Long id = idLegado(grado.obtenerNombreProducto(false));
						assertEquals(grado.name(), catalogo.buscarGradoPorProductoExamen(id, codigo(deporte))
								.map(Grado::getCodigo).orElse(null), deporte + " examen " + grado);
						assertTrue(catalogo.buscarGradoPorProductoRecompensa(id, codigo(deporte)).isEmpty());
					}
					if (grado.tieneProductoRecompensa()) {
						Long id = idLegado(grado.obtenerNombreProducto(true));
						assertEquals(grado.name(), catalogo.buscarGradoPorProductoRecompensa(id, codigo(deporte))
								.map(Grado::getCodigo).orElse(null), deporte + " recompensa " + grado);
						assertTrue(catalogo.buscarGradoPorProductoExamen(id, codigo(deporte)).isEmpty());
					}
				}
			}
			// Rojo bordado: las dos variantes conceden ROJO, también en kickboxing (su propio sistema).
			for (Deporte deporte : CON_GRADOS) {
				Long sistema = progresion.buscarGrado(codigo(deporte), "ROJO").orElseThrow().getSistemaGrados().getId();
				Grado examen = catalogo.buscarGradoPorProductoExamen(idLegado("DERECHO A EXAMEN ROJO BORDADO"),
						codigo(deporte)).orElseThrow();
				Grado recompensa = catalogo.buscarGradoPorProductoRecompensa(
						idLegado("DERECHO DE CAMBIO A ROJO BORDADO POR RECOMPENSA"), codigo(deporte)).orElseThrow();
				assertEquals("ROJO", examen.getCodigo());
				assertEquals("ROJO", recompensa.getCodigo());
				assertEquals(sistema, examen.getSistemaGrados().getId());
				assertEquals(sistema, recompensa.getSistemaGrados().getId());
			}
			// El producto por defecto del grado ROJO sigue siendo el del cinturón normal.
			assertEquals(idLegado("DERECHO A EXAMEN ROJO"), id(catalogo.buscarProductoExamen("TAEKWONDO", "ROJO")));
			assertEquals(idLegado("DERECHO DE CAMBIO A ROJO POR RECOMPENSA"),
					id(catalogo.buscarProductoRecompensa("TAEKWONDO", "ROJO")));
			Long amarillo = idLegado("DERECHO A EXAMEN AMARILLO");
			assertTrue(catalogo.buscarGradoPorProducto(amarillo, "PILATES").isEmpty());
			assertTrue(catalogo.buscarGradoPorProducto(amarillo, null).isEmpty());
			assertTrue(catalogo.buscarGradoPorProducto(null, "TAEKWONDO").isEmpty());
			assertThrows(IllegalArgumentException.class, () -> catalogo.buscarGradoPorProducto(amarillo, "NO_EXISTE"));
		});
	}

	// ---------------- Escritores actuales ----------------

	@Test
	void jsonDeProducto_sinCambios_yLaClasificacionNoSeFijaDesdeLaPeticion() throws Exception {
		Long id = TenantContext.calcularCon(ORG_TAEMOI, () -> idLegado("MENSUALIDAD"));
		Producto actual = TenantContext.calcularCon(ORG_TAEMOI, () -> productoRepository.findById(id).orElseThrow());
		Map<?, ?> json = objectMapper.readValue(objectMapper.writeValueAsString(actual), Map.class);
		assertEquals(Set.of("id", "concepto", "precio"), json.keySet());

		// POST /api/productos con el id de un producto clasificado (merge): no pisa la clasificación.
		Producto recibido = objectMapper.readValue("{\"id\":" + id + ",\"concepto\":\"MENSUALIDAD\",\"precio\":"
				+ actual.getPrecio() + ",\"codigo\":\"OTRO_CODIGO\",\"tipo\":\"EXAMEN\"}", Producto.class);
		assertNull(recibido.getCodigo());
		assertEquals(TipoProducto.OTRO, recibido.getTipo());
		TenantContext.ejecutarCon(ORG_TAEMOI, () -> productoService.guardarProducto(recibido));
		assertEquals(Map.of("codigo", "MENSUALIDAD", "tipo", "MENSUALIDAD"),
				jdbc.queryForMap("SELECT codigo, tipo FROM producto WHERE id = ?", id));

		// Alta como la de hoy: OTRO, sin codigo.
		Producto nuevo = new Producto();
		nuevo.setConcepto("PRODUCTO IT SIN CLASIFICAR");
		nuevo.setPrecio(1.0);
		Long nuevoId = TenantContext.calcularCon(ORG_TAEMOI, () -> productoService.guardarProducto(nuevo).getId());
		insertados.add(nuevoId);
		Map<String, Object> fila = jdbc.queryForMap("SELECT codigo, tipo, disciplina_id FROM producto WHERE id = ?",
				nuevoId);
		assertNull(fila.get("codigo"));
		assertEquals("OTRO", fila.get("tipo"));
		assertNull(fila.get("disciplina_id"));
	}

	@Test
	void eliminarUnProductoEnlazadoAUnGrado_sigueFuncionando() {
		jdbc.update("INSERT INTO producto (concepto, precio, organizacion_id) VALUES ('IT TEMPORAL ENLAZADO', 1.0, 1)");
		Long temporal = jdbc.queryForObject("SELECT MAX(id) FROM producto WHERE concepto = 'IT TEMPORAL ENLAZADO'",
				Long.class);
		Long negro5 = TenantContext.calcularCon(ORG_TAEMOI,
				() -> progresion.buscarGrado("TAEKWONDO", "NEGRO_5_DAN").orElseThrow().getId());
		jdbc.update("UPDATE grado SET producto_recompensa_id = ? WHERE id = ?", temporal, negro5);

		TenantContext.ejecutarCon(ORG_TAEMOI, () -> productoService.eliminarProducto(temporal));

		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM producto WHERE id = ?", Integer.class, temporal));
		assertNull(jdbc.queryForObject("SELECT producto_recompensa_id FROM grado WHERE id = ?", Long.class, negro5));
	}

	// ---------------- Resincronización tras V24 ----------------

	@Test
	void cambiarTipoTarifaPorElServicioActual_dejaTarifaIdDesfasada_yElResyncLaCorrige() throws Exception {
		int expediente = 900000 + (int) (System.nanoTime() % 90000);
		jdbc.update("""
				INSERT INTO alumno (activo, apellidos, autorizacion_web, direccion, fecha_nacimiento, nombre,
				  numero_expediente, telefono, tiene_derecho_examen, organizacion_id)
				VALUES (1, 'Resync', 1, 'Calle 1', '2010-01-01', 'IT', ?, 600000000, 0, 1)""", expediente);
		Long alumno = jdbc.queryForObject(
				"SELECT id FROM alumno WHERE organizacion_id = 1 AND numero_expediente = ?", Long.class, expediente);
		Long adulto = TenantContext.calcularCon(ORG_TAEMOI, () -> tarifas.obtenerPorCodigo("ADULTO").getId());
		Long infantil = TenantContext.calcularCon(ORG_TAEMOI, () -> tarifas.obtenerPorCodigo("INFANTIL").getId());
		jdbc.update("""
				INSERT INTO alumno_deporte (activo, apto_para_examen, alumno_id, deporte, competidor, tipo_tarifa,
				  tarifa_id, organizacion_id)
				VALUES (1, 0, ?, 'TAEKWONDO', 0, 'ADULTO', ?, 1)""", alumno, adulto);
		try {
			// Camino real: PUT /api/alumnos/{id}/deportes/{deporte}/tipo-tarifa -> actualizarTipoTarifa.
			TenantContext.ejecutarCon(ORG_TAEMOI,
					() -> alumnoDeporteService.actualizarTipoTarifa(alumno, Deporte.TAEKWONDO, TipoTarifa.INFANTIL));
			Map<String, Object> fila = jdbc.queryForMap(
					"SELECT tipo_tarifa, tarifa_id FROM alumno_deporte WHERE alumno_id = ?", alumno);
			assertEquals("INFANTIL", fila.get("tipo_tarifa"));
			assertEquals(adulto, ((Number) fila.get("tarifa_id")).longValue(), "desfasada hasta el resync");

			ejecutar("db/resync/resync_catalogo_v24.sql");
			assertEquals(infantil, jdbc.queryForObject("SELECT tarifa_id FROM alumno_deporte WHERE alumno_id = ?",
					Long.class, alumno));
			ejecutar("db/resync/resync_catalogo_v24.sql");
			assertEquals(infantil, jdbc.queryForObject("SELECT tarifa_id FROM alumno_deporte WHERE alumno_id = ?",
					Long.class, alumno));
		} finally {
			jdbc.update("DELETE FROM alumno_deporte WHERE alumno_id = ?", alumno);
			jdbc.update("DELETE FROM alumno WHERE id = ?", alumno);
		}
	}

	private void ejecutar(String recurso) throws Exception {
		try (Connection conexion = dataSource.getConnection()) {
			ScriptUtils.executeSqlScript(conexion,
					new EncodedResource(new ClassPathResource(recurso), StandardCharsets.UTF_8));
		}
	}

	// ---------------- Aislamiento ----------------

	@Test
	void otraOrganizacion_noVeTarifasNiProductosDeTaemoi() {
		Long tarifaTaemoi = TenantContext.calcularCon(ORG_TAEMOI, () -> tarifas.obtenerPorCodigo("ADULTO").getId());
		Long mensualidad = TenantContext.calcularCon(ORG_TAEMOI, () -> idLegado("MENSUALIDAD"));
		TenantContext.ejecutarCon(org2, () -> {
			assertEquals(List.of(), tarifas.listarActivas());
			assertEquals(0, tarifaRepository.count());
			assertTrue(tarifaRepository.findById(tarifaTaemoi).isEmpty());
			assertTrue(tarifas.buscarPorCodigo("ADULTO").isEmpty());
			assertThrows(IllegalArgumentException.class, () -> tarifas.obtenerImporte("ADULTO", RolFamiliar.PADRE));
			assertTrue(catalogo.buscarPorCodigo("MENSUALIDAD").isEmpty());
			assertTrue(productoRepository.findById(mensualidad).isEmpty());
			assertTrue(productoRepository.findFirstByConcepto("MENSUALIDAD").isEmpty());
			for (TipoProducto tipo : TipoProducto.values()) {
				assertEquals(List.of(), catalogo.listarPorTipo(tipo, null), tipo.name());
			}
			assertThrows(IllegalArgumentException.class, () -> catalogo.buscarProductoCompetidor("TAEKWONDO"));
			assertThrows(IllegalArgumentException.class, () -> catalogo.buscarGradoPorProducto(mensualidad, "TAEKWONDO"));
		});
		assertFalse(TenantContext.calcularCon(ORG_TAEMOI, () -> tarifas.listarActivas().isEmpty()));
	}
}
