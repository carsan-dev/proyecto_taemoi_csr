package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.taemoi.project.controllers.AlumnoController;
import com.taemoi.project.dtos.AlumnoDTO;
import com.taemoi.project.dtos.AlumnoDeporteDTO;
import com.taemoi.project.dtos.ProductoAlumnoDTO;
import com.taemoi.project.dtos.response.TesoreriaMovimientoDTO;
import com.taemoi.project.dtos.response.TesoreriaResumenDTO;
import com.taemoi.project.legado.Deporte;
import com.taemoi.project.entities.Disciplina;
import com.taemoi.project.entities.Organizacion;
import com.taemoi.project.entities.Producto;
import com.taemoi.project.entities.Tarifa;
import com.taemoi.project.legado.TipoGrado;
import com.taemoi.project.entities.TipoProducto;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.DisciplinaRepository;
import com.taemoi.project.repositories.GradoRepository;
import com.taemoi.project.repositories.OrganizacionRepository;
import com.taemoi.project.repositories.ProductoRepository;
import com.taemoi.project.repositories.SistemaGradosRepository;
import com.taemoi.project.repositories.TarifaRepository;
import com.taemoi.project.services.AlumnoDeporteService;
import com.taemoi.project.services.ProductoAlumnoService;
import com.taemoi.project.services.ProductosCatalogoService;
import com.taemoi.project.services.TesoreriaService;

/**
 * Una escuela con códigos de tarifa y de producto propios (que no son nombres de los enums ni los
 * conceptos de Taemoi): alta, mensualidad con tarifa de competidor, cobro, cambio de tarifa y
 * tesorería funcionan sin errores y con los datos de la escuela. También los respaldos por concepto
 * (WARN) y la creación del producto de competidor cuando falta.
 */
@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
class OtraEscuelaTarifasProductosIntegrationTest extends MySqlIntegrationTest {

	private static final long ORG_TAEMOI = 1L;

	@Autowired OrganizacionRepository organizaciones;
	@Autowired DisciplinaRepository disciplinas;
	@Autowired SistemaGradosRepository sistemas;
	@Autowired GradoRepository grados;
	@Autowired TarifaRepository tarifas;
	@Autowired ProductoRepository productos;
	@Autowired AlumnoController alumnoController;
	@Autowired AlumnoDeporteService alumnoDeporteService;
	@Autowired ProductoAlumnoService productoAlumnoService;
	@Autowired ProductosCatalogoService catalogo;
	@Autowired TesoreriaService tesoreria;
	@Autowired com.taemoi.project.controllers.ProductoController productoController;
	@Autowired JdbcTemplate jdbc;
	@Autowired org.springframework.transaction.support.TransactionTemplate tx;

	@AfterEach
	void sinFugas() {
		SecurityContextHolder.clearContext();
		assertNull(TenantContext.get());
	}

	@Test
	void escuelaConCodigosPropios_altaMensualidadCobroYTesoreria() {
		long org = escuela("it-codigos-propios");
		TenantContext.ejecutarCon(org, () -> {
			Disciplina tkd = disciplinas.findByCodigo("TAEKWONDO").orElseThrow();
			tarifa("CUOTA_GENERAL", "Cuota general", "45.00", tkd);
			tarifa("CUOTA_REDUCIDA", "Cuota reducida", "35.00", tkd);
			producto("CUOTA_MES", TipoProducto.MENSUALIDAD, "Cuota mensual", 45.0, null);
			Producto ficha = producto("FICHA_COMPETICION", TipoProducto.COMPETIDOR, "Ficha de competición", 15.0, tkd);
			tkd.setProductoCompetidor(ficha);
			disciplinas.save(tkd);
		});

		// Alta con el código de tarifa propio (deportesInicial.codigoTarifa) y cuantía.
		long alumno = crear(org, """
				{"nombre":"OTRA","apellidos":"ESCUELA","fechaNacimiento":"1990-03-04","direccion":"Calle 1",
				 "telefono":600000003,"email":"otra-%s@example.test","rolFamiliar":"NINGUNO","grupoFamiliar":"",
				 "deportesInicial":[{"deporte":"TAEKWONDO","grado":"BLANCO","fechaAlta":"2040-09-01",
				   "codigoTarifa":"CUOTA_GENERAL","cuantiaTarifa":45.0,"competidor":true,"tieneLicencia":true,
				   "fechaLicencia":"2040-09-01"}]}""".formatted(sufijo()));
		Map<String, Object> inscripcion = jdbc.queryForMap("""
				SELECT t.codigo AS tarifa, ad.cuantia_tarifa FROM alumno_deporte ad
				LEFT JOIN tarifa t ON t.id = ad.tarifa_id WHERE ad.alumno_id = ?""", alumno);
		// Hasta V27 tipo_tarifa quedaba NULL para los códigos propios; V28 borra la columna.
		assertEquals(0, jdbc.queryForObject("""
				SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
				  AND TABLE_NAME = 'alumno_deporte' AND COLUMN_NAME = 'tipo_tarifa'""", Integer.class));
		assertEquals("CUOTA_GENERAL", inscripcion.get("tarifa"));

		// Mensualidad del mes: concepto y precio del producto de la escuela; competidor por su enlace.
		TenantContext.ejecutarCon(org, () -> tx.executeWithoutResult(s -> productoAlumnoService
				.cargarMensualidadIndividualPorDeporte(alumno, "TAEKWONDO", "2041-05", false, LocalDate.of(2041, 5, 1))));
		Map<String, Double> cargos = jdbc.queryForList(
				"SELECT concepto, precio FROM producto_alumno WHERE alumno_id = ?", alumno).stream()
				.collect(Collectors.toMap(f -> (String) f.get("concepto"), f -> ((Number) f.get("precio")).doubleValue()));
		assertEquals(Map.of("Cuota mensual MAYO 2041 - TAEKWONDO", 45.0, "Ficha de competición MAYO 2041", 15.0), cargos);
		// La carga general del mes no duplica (existencia por concepto exacto).
		TenantContext.ejecutarCon(org, () -> tx.executeWithoutResult(s -> productoAlumnoService
				.cargarMensualidadesGenerales("2041-05", LocalDate.of(2041, 5, 1))));
		assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM producto_alumno WHERE alumno_id = ?", Integer.class,
				alumno));

		// Cobro de la mensualidad: sin errores.
		Long cuota = jdbc.queryForObject("SELECT id FROM producto_alumno WHERE alumno_id = ? AND concepto LIKE 'Cuota%'",
				Long.class, alumno);
		ProductoAlumnoDTO pago = new ProductoAlumnoDTO();
		pago.setPagado(true);
		assertTrue(TenantContext.calcularCon(org, () -> productoAlumnoService.actualizarProductoAlumno(cuota, pago))
				.getPagado());

		// Tesorería: categorías por el tipo del producto (los conceptos no son los de Taemoi).
		TesoreriaResumenDTO resumen = TenantContext.calcularCon(org, () -> tesoreria.obtenerResumen(5, 2041, "TODOS", false));
		assertEquals(2L, resumen.getTotalMovimientos());
		assertEquals(60.0, resumen.getImporteTotal());
		assertEquals(45.0, resumen.getImportePagado());
		Map<String, String> categorias = TenantContext.calcularCon(org,
				() -> tesoreria.obtenerMovimientos(5, 2041, "TODOS", null, null, false, 0, 10).stream()
						.collect(Collectors.toMap(TesoreriaMovimientoDTO::getConcepto, TesoreriaMovimientoDTO::getCategoria)));
		assertEquals(Map.of("Cuota mensual MAYO 2041 - TAEKWONDO", "MENSUALIDAD",
				"Ficha de competición MAYO 2041", "TARIFA_COMPETIDOR"), categorias);

		// Código propio: el tipo del catálogo decide (derecho a examen de un producto EXAMEN propio).
		Long paso = TenantContext.calcularCon(org, () -> producto("PASO_CINTURON", TipoProducto.EXAMEN,
				"Paso de cinturón", 20.0, null).getId());
		ProductoAlumnoDTO pagadoYa = new ProductoAlumnoDTO();
		pagadoYa.setPagado(true);
		TenantContext.ejecutarCon(org, () -> productoAlumnoService.asignarProductoAAlumno(alumno, paso, pagadoYa));
		assertTrue(jdbc.queryForObject("SELECT tiene_derecho_examen FROM alumno WHERE id = ?", Boolean.class, alumno));

		// Renombrar un producto propio con cargos duplicaría los del periodo: 400. Sin cargos, o solo el
		// precio, se permite.
		SecurityContextHolder.getContext().setAuthentication(admin());
		Long cuotaMes = TenantContext.calcularCon(org, () -> productos.findByCodigo("CUOTA_MES").orElseThrow().getId());
		ResponseEntity<?> renombrar = TenantContext.calcularCon(org,
				() -> productoController.actualizarProducto(cuotaMes, new Producto("Cuota mensual nueva", 45.0, null)));
		assertEquals(HttpStatus.BAD_REQUEST, renombrar.getStatusCode());
		assertTrue(String.valueOf(renombrar.getBody()).contains("ya tiene cargos"), String.valueOf(renombrar.getBody()));
		assertEquals("Cuota mensual", jdbc.queryForObject("SELECT concepto FROM producto WHERE id = ?", String.class, cuotaMes));
		assertEquals(HttpStatus.OK, TenantContext.calcularCon(org,
				() -> productoController.actualizarProducto(cuotaMes, new Producto("Cuota mensual", 47.0, null))).getStatusCode());
		Long sinCargos = TenantContext.calcularCon(org, () -> producto("CUOTA_TRIMESTRE", TipoProducto.MENSUALIDAD,
				"Cuota trimestral", 120.0, null).getId());
		assertEquals(HttpStatus.OK, TenantContext.calcularCon(org,
				() -> productoController.actualizarProducto(sinCargos, new Producto("Cuota del trimestre", 120.0, null)))
				.getStatusCode());

		// Cambio de tarifa por código propio; un código que no es de la escuela ni del enum es un 400.
		AlumnoDeporteDTO cambiado = TenantContext.calcularCon(org, () -> AlumnoDeporteDTO.deAlumnoDeporte(
				alumnoDeporteService.actualizarTarifa(alumno, "TAEKWONDO", "CUOTA_REDUCIDA")));
		assertEquals("CUOTA_REDUCIDA", cambiado.getTipoTarifa());
		assertEquals("CUOTA_REDUCIDA", jdbc.queryForObject(
				"SELECT t.codigo FROM alumno_deporte ad JOIN tarifa t ON t.id = ad.tarifa_id WHERE ad.alumno_id = ?",
				String.class, alumno));
		assertThrows(IllegalArgumentException.class, () -> TenantContext.ejecutarCon(org,
				() -> alumnoDeporteService.actualizarTarifa(alumno, "TAEKWONDO", "NO_EXISTE")));

		// Alta con una tarifa del enum que la escuela no tiene: 400 (no 500).
		SecurityContextHolder.getContext().setAuthentication(admin());
		ResponseEntity<?> sinTarifa = TenantContext.calcularCon(org, () -> alumnoController.crearAlumno("""
				{"nombre":"SIN","apellidos":"TARIFA","fechaNacimiento":"1990-03-04","direccion":"Calle 1",
				 "telefono":600000004,"email":"sin-%s@example.test","deporte":"TAEKWONDO","tipoTarifa":"ADULTO"}
				""".formatted(sufijo()), null));
		assertEquals(HttpStatus.BAD_REQUEST, sinTarifa.getStatusCode());
	}

	/**
	 * Escuela con productos SIN clasificar (nombres de Taemoi, sin codigo ni tipo): se encuentran por
	 * el concepto de antes con un WARN; sin producto de competidor, se crea clasificado y enlazado.
	 */
	@Test
	void sinCatalogo_respaldoPorConceptoConWarn_yCompetidorSeCrea(CapturedOutput salida) {
		long org = escuela("it-respaldo");
		TenantContext.ejecutarCon(org, () -> {
			productos.save(new Producto("MENSUALIDAD", 31.0, null));
			productos.save(new Producto("LICENCIA FEDERATIVA KICKBOXING ADULTO", 44.0, null));
			DisciplinasDePrueba.conGrados(disciplinas, sistemas, grados, "KICKBOXING", 15, TipoGrado.BLANCO);
		});
		TenantContext.ejecutarCon(org, () -> {
			assertEquals("MENSUALIDAD", catalogo.resolverProductoMensualidad("TAEKWONDO").orElseThrow().getConcepto());
			assertEquals("LICENCIA FEDERATIVA KICKBOXING ADULTO",
					catalogo.resolverProductoLicencia("KICKBOXING", false, false, true).orElseThrow().getConcepto());
			assertTrue(catalogo.resolverProductoCompetidor("KICKBOXING").isEmpty());
			assertTrue(catalogo.resolverProductoReservaPlaza(null).isEmpty());
		});
		assertTrue(salida.getOut().contains("Producto de mensualidad (TAEKWONDO)"), "WARN del respaldo");

		Producto creado = TenantContext.calcularCon(org, () -> catalogo.asegurarProductoCompetidor("KICKBOXING").orElseThrow());
		assertEquals("TARIFA COMPETIDOR KICKBOXING", creado.getConcepto());
		assertEquals(20.0, creado.getPrecio());
		Map<String, Object> fila = jdbc.queryForMap("""
				SELECT p.codigo, p.tipo, p.organizacion_id, d.codigo AS disciplina,
				  (SELECT dd.producto_competidor_id FROM disciplina dd WHERE dd.id = d.id) AS enlace
				FROM producto p JOIN disciplina d ON d.id = p.disciplina_id WHERE p.id = ?""", creado.getId());
		assertEquals("COMPETIDOR_KICKBOXING", fila.get("codigo"));
		assertEquals("COMPETIDOR", fila.get("tipo"));
		assertEquals(org, ((Number) fila.get("organizacion_id")).longValue());
		assertEquals("KICKBOXING", fila.get("disciplina"));
		assertEquals(creado.getId(), ((Number) fila.get("enlace")).longValue());
		// La segunda vez se resuelve por el enlace (no crea otro).
		assertEquals(creado.getId(), TenantContext.calcularCon(org,
				() -> catalogo.asegurarProductoCompetidor("KICKBOXING").orElseThrow().getId()));
		// Taemoi no ve el producto de la otra escuela.
		assertNotEquals(creado.getId(), TenantContext.calcularCon(ORG_TAEMOI,
				() -> catalogo.resolverProductoCompetidor("KICKBOXING").map(Producto::getId).orElse(null)));
	}

	// ---------------------------------------------------------------------------------

	private long escuela(String prefijo) {
		Organizacion o = new Organizacion();
		o.setSlug(prefijo + "-" + UUID.randomUUID().toString().substring(0, 8));
		o.setNombre("Escuela " + prefijo);
		long org = TenantContext.calcularCon(ORG_TAEMOI, () -> organizaciones.save(o).getId());
		assertNotEquals(ORG_TAEMOI, org);
		TenantContext.ejecutarCon(org,
				() -> DisciplinasDePrueba.conGrados(disciplinas, sistemas, grados, "TAEKWONDO", 14, TipoGrado.BLANCO));
		return org;
	}

	private void tarifa(String codigo, String nombre, String importe, Disciplina disciplina) {
		Tarifa t = new Tarifa();
		t.setCodigo(codigo);
		t.setNombre(nombre);
		t.setImporte(new BigDecimal(importe));
		t.setDisciplina(disciplina);
		tarifas.save(t);
	}

	private Producto producto(String codigo, TipoProducto tipo, String concepto, double precio, Disciplina disciplina) {
		Producto p = new Producto(concepto, precio, null);
		p.setCodigo(codigo);
		p.setTipo(tipo);
		p.setDisciplina(disciplina);
		return productos.save(p);
	}

	private long crear(long org, String json) {
		SecurityContextHolder.getContext().setAuthentication(admin());
		ResponseEntity<?> respuesta = TenantContext.calcularCon(org, () -> alumnoController.crearAlumno(json, null));
		assertEquals(HttpStatus.CREATED, respuesta.getStatusCode(), String.valueOf(respuesta.getBody()));
		return ((AlumnoDTO) respuesta.getBody()).getId();
	}

	private static UsernamePasswordAuthenticationToken admin() {
		return new UsernamePasswordAuthenticationToken("admin-it", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
	}

	private static String sufijo() {
		return UUID.randomUUID().toString().substring(0, 8);
	}
}
