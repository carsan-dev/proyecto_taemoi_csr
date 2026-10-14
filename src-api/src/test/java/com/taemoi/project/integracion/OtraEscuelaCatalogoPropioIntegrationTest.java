package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

import com.taemoi.project.controllers.AlumnoController;
import com.taemoi.project.dtos.AlumnoDTO;
import com.taemoi.project.dtos.ProductoAlumnoDTO;
import com.taemoi.project.dtos.response.TesoreriaMovimientoDTO;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.AlumnoDeporte;
import com.taemoi.project.entities.AplicaGrado;
import com.taemoi.project.entities.Categoria;
import com.taemoi.project.entities.Disciplina;
import com.taemoi.project.entities.Grado;
import com.taemoi.project.entities.Organizacion;
import com.taemoi.project.entities.Producto;
import com.taemoi.project.entities.SistemaGrados;
import com.taemoi.project.entities.Tarifa;
import com.taemoi.project.entities.TipoProducto;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.CategoriaRepository;
import com.taemoi.project.repositories.DisciplinaRepository;
import com.taemoi.project.repositories.GradoRepository;
import com.taemoi.project.repositories.OrganizacionRepository;
import com.taemoi.project.repositories.ProductoRepository;
import com.taemoi.project.repositories.SistemaGradosRepository;
import com.taemoi.project.repositories.TarifaRepository;
import com.taemoi.project.services.AlumnoDeporteService;
import com.taemoi.project.services.ComportamientoLegadoService;
import com.taemoi.project.services.ProductoAlumnoService;
import com.taemoi.project.services.ProductosCatalogoService;
import com.taemoi.project.services.TesoreriaService;

/**
 * V28: una escuela con su propio catálogo, sin ningún código de los enums borrados (Deporte, TipoGrado,
 * TipoTarifa, TipoCategoria): grados con códigos propios (siguiente grado y alta), tarifas y categorías
 * propias. Y el comportamiento heredado de Taemoi limitado a la organización 1: otra escuela con productos
 * que usan los códigos de V24 (COMPETIDOR_TAEKWONDO, MENSUALIDAD, EXAMEN_…) recibe el comportamiento del
 * catálogo (precio y nombre del producto, tipo en tesorería, sin variantes sin clasificar).
 */
@SpringBootTest
class OtraEscuelaCatalogoPropioIntegrationTest extends MySqlIntegrationTest {

	private static final long ORG_TAEMOI = 1L;
	private static final int ANIO = LocalDate.now().getYear();

	@Autowired OrganizacionRepository organizaciones;
	@Autowired DisciplinaRepository disciplinas;
	@Autowired SistemaGradosRepository sistemas;
	@Autowired GradoRepository grados;
	@Autowired TarifaRepository tarifas;
	@Autowired CategoriaRepository categorias;
	@Autowired ProductoRepository productos;
	@Autowired AlumnoRepository alumnos;
	@Autowired AlumnoController alumnoController;
	@Autowired AlumnoDeporteService alumnoDeporteService;
	@Autowired ProductoAlumnoService productoAlumnoService;
	@Autowired ProductosCatalogoService catalogo;
	@Autowired TesoreriaService tesoreria;
	@Autowired ComportamientoLegadoService comportamientoLegado;
	@Autowired JdbcTemplate jdbc;
	@Autowired TransactionTemplate tx;

	@AfterEach
	void sinFugas() {
		SecurityContextHolder.clearContext();
		assertNull(TenantContext.get());
	}

	// =============================================================================================
	// Grados, tarifas y categorías con códigos propios
	// =============================================================================================

	@Test
	void siguienteGrado_conCodigosPropios_usaLaReglaDeProgresion() {
		long org = escuelaDeJudo("it-judo-grados");
		TenantContext.ejecutarCon(org, () -> tx.executeWithoutResult(s -> {
			// Menor (año natural, edad adulta 14): pasa por el cinto de menores.
			assertEquals("CINTO_AMARILLO", siguiente("CINTO_BLANCO", ANIO - 8));
			assertEquals("CINTO_NARANJA", siguiente("CINTO_AMARILLO", ANIO - 8));
			assertNull(siguiente("CINTO_NARANJA", ANIO - 8), "el cinto verde es solo de adultos");
			// Adulto: salta el de menores y llega al de adultos.
			assertEquals("CINTO_NARANJA", siguiente("CINTO_BLANCO", ANIO - 30));
			assertEquals("CINTO_VERDE", siguiente("CINTO_NARANJA", ANIO - 30));
			assertNull(siguiente("CINTO_VERDE", ANIO - 30));
		}));
	}

	@Test
	void alta_conGradoTarifaYCategoriaPropios() {
		long org = escuelaDeJudo("it-judo-alta");
		AlumnoDTO respuesta = crear(org, """
				{"nombre":"JUDO","apellidos":"ALTA","fechaNacimiento":"%d-03-04","direccion":"Calle 1",
				 "telefono":600000004,"email":"judo-%s@example.test","rolFamiliar":"NINGUNO","grupoFamiliar":"",
				 "deportesInicial":[{"deporte":"JUDO","grado":"CINTO_AMARILLO","fechaGrado":"2040-09-01",
				   "fechaAlta":"2040-09-01","codigoTarifa":"CUOTA_JUDO","cuantiaTarifa":33.0,
				   "competidor":true,"tieneLicencia":true,"fechaLicencia":"2040-09-01"}]}""".formatted(ANIO - 12, sufijo()));

		// La respuesta mantiene la forma de siempre: códigos de la escuela en los mismos campos.
		assertEquals("JUDO", respuesta.getDeporte());
		assertEquals("CINTO_AMARILLO", respuesta.getGrado());
		assertEquals("CUOTA_JUDO", respuesta.getTipoTarifa());
		Map<String, Object> ad = jdbc.queryForMap("""
				SELECT d.codigo AS disciplina, g.codigo AS grado, sg.id AS sistema_grado, d.sistema_grados_id AS sistema,
				       t.codigo AS tarifa, c.nombre AS categoria
				FROM alumno_deporte ad JOIN disciplina d ON d.id = ad.disciplina_id
				JOIN grado g ON g.id = ad.grado_id JOIN sistema_grados sg ON sg.id = g.sistema_grados_id
				LEFT JOIN tarifa t ON t.id = ad.tarifa_id LEFT JOIN categoria c ON c.id = ad.categoria_id
				WHERE ad.alumno_id = ?""", respuesta.getId());
		assertEquals("JUDO", ad.get("disciplina"));
		assertEquals("CINTO_AMARILLO", ad.get("grado"));
		assertEquals(ad.get("sistema"), ad.get("sistema_grado"), "grado del sistema de su disciplina");
		assertEquals("CUOTA_JUDO", ad.get("tarifa"));
		assertEquals("Alevín", ad.get("categoria"), "12 años: categoría propia por edad");

		// Un código de grado que la escuela no tiene es un 400 (los de Taemoi, con el mensaje de «no configurado»).
		assertEquals("Grado no válido para JUDO: CINTO_ROSA", altaRechazada(org, "CINTO_ROSA"));
		assertEquals("El grado AMARILLO no está configurado en la escuela.", altaRechazada(org, "AMARILLO"));

		// Cambio de grado y de tarifa con los códigos de la escuela; un código de tarifa de Taemoi no vale.
		TenantContext.ejecutarCon(org, () -> {
			assertEquals("CINTO_NARANJA", alumnoDeporteService
					.actualizarGradoPorDeporte(respuesta.getId(), "JUDO", "CINTO_NARANJA").getGrado().getCodigo());
			assertEquals("CUOTA_JUDO_REDUCIDA", alumnoDeporteService
					.actualizarTarifa(respuesta.getId(), "JUDO", "CUOTA_JUDO_REDUCIDA").getTipoTarifa());
		});
		IllegalArgumentException ajena = assertThrows(IllegalArgumentException.class, () -> TenantContext.ejecutarCon(org,
				() -> alumnoDeporteService.actualizarTarifa(respuesta.getId(), "JUDO", "ADULTO")));
		assertEquals("No enum constant com.taemoi.project.entities.TipoTarifa.ADULTO", ajena.getMessage());
	}

	@Test
	void categoriasPropias_porEdad_soloEnLaDisciplinaQueLasTiene() {
		long org = escuelaDeJudo("it-judo-categorias");
		long adulto = alumno(org, ANIO - 30);
		long infantil = alumno(org, ANIO - 9);
		TenantContext.ejecutarCon(org, () -> tx.executeWithoutResult(s -> {
			inscribir(adulto, "JUDO", true);
			inscribir(infantil, "JUDO", true);
			inscribir(adulto, "TAEKWONDO", true);
		}));
		TenantContext.ejecutarCon(org, () -> {
			assertEquals("Absoluto", alumnoDeporteService.actualizarCompetidor(adulto, "JUDO", true).getCategoria().getNombre());
			assertEquals("Benjamín", alumnoDeporteService.actualizarCompetidor(infantil, "JUDO", true).getCategoria().getNombre());
			// Taekwondo en esta escuela no tiene categorías: no se asigna ninguna (ni las de Taemoi).
			assertNull(alumnoDeporteService.actualizarCompetidor(adulto, "TAEKWONDO", true).getCategoria());
			// Dejar de competir quita la categoría, como antes.
			assertNull(alumnoDeporteService.actualizarCompetidor(adulto, "JUDO", false).getCategoria());
		});
		// La categoría por nombre (la que elige el usuario) también es de la escuela.
		assertThrows(IllegalArgumentException.class, () -> TenantContext.ejecutarCon(org,
				() -> alumnoDeporteService.actualizarCategoria(adulto, "JUDO", "Senior")));
	}

	// =============================================================================================
	// Comportamiento heredado de Taemoi limitado a la organización 1
	// =============================================================================================

	@Test
	void marcaDeComportamientoHeredado_soloTaemoi() {
		long org = escuela("it-sin-legado");
		assertTrue(TenantContext.calcularCon(ORG_TAEMOI, comportamientoLegado::activo));
		assertFalse(TenantContext.calcularCon(org, comportamientoLegado::activo));
	}

	/**
	 * Otra escuela con un producto codificado COMPETIDOR_TAEKWONDO (un código de V24): se cobra
	 * {@code producto.precio} (no los 20 € de Taemoi) y el concepto lleva el nombre del producto (no el literal
	 * «TARIFA COMPETIDOR TAEKWONDO»). Lo mismo para la mensualidad (código MENSUALIDAD). La tesorería clasifica
	 * por el tipo y el producto de examen con código de V24 da derecho a examen por su tipo.
	 */
	@Test
	void codigosDeV24_enOtraEscuela_usanElCatalogo() {
		long org = escuela("it-codigos-v24");
		TenantContext.ejecutarCon(org, () -> {
			Disciplina tkd = disciplinas.findByCodigo("TAEKWONDO").orElseThrow();
			tarifa("CUOTA", "Cuota", "40.00", tkd);
			// Variante SIN clasificar con el concepto de Taemoi, anterior (id menor) a la clasificada: en Taemoi
			// se cobraría esta; aquí no.
			productos.save(new Producto("MENSUALIDAD", 99.0, null));
			producto("MENSUALIDAD", TipoProducto.MENSUALIDAD, "Cuota del mes", 40.0, null);
			Producto competidor = producto("COMPETIDOR_TAEKWONDO", TipoProducto.COMPETIDOR, "Cuota de competición", 17.0, tkd);
			tkd.setProductoCompetidor(competidor);
			disciplinas.save(tkd);
			producto("EXAMEN_AMARILLO", TipoProducto.EXAMEN, "Examen de cinto amarillo", 25.0, null);
		});
		assertEquals("Cuota del mes", TenantContext.calcularCon(org,
				() -> catalogo.resolverProductoMensualidad("TAEKWONDO").orElseThrow().getConcepto()));

		long alumno = alumno(org, 1990);
		TenantContext.ejecutarCon(org, () -> tx.executeWithoutResult(s -> {
			AlumnoDeporte ad = inscribir(alumno, "TAEKWONDO", true);
			ad.setTarifa(tarifas.findByCodigo("CUOTA").orElseThrow());
			ad.setCuantiaTarifa(40.0);
			ad.setPrincipal(true);
			ad.setCompetidor(true);
			ad.setFechaAltaCompeticion(Date.valueOf("2040-09-01"));
		}));
		TenantContext.ejecutarCon(org, () -> tx.executeWithoutResult(s -> productoAlumnoService
				.cargarMensualidadIndividualPorDeporte(alumno, "TAEKWONDO", "2041-05", false, LocalDate.of(2041, 5, 1))));
		Map<String, Double> cargos = jdbc.queryForList(
				"SELECT concepto, precio FROM producto_alumno WHERE alumno_id = ?", alumno).stream()
				.collect(Collectors.toMap(f -> (String) f.get("concepto"), f -> ((Number) f.get("precio")).doubleValue()));
		assertEquals(Map.of("Cuota del mes MAYO 2041 - TAEKWONDO", 40.0, "Cuota de competición MAYO 2041", 17.0), cargos);

		// Tesorería: el cargo de competidor (concepto que no empieza por «TARIFA COMPETIDOR») por su tipo.
		Map<String, String> categoriasTesoreria = TenantContext.calcularCon(org,
				() -> tesoreria.obtenerMovimientos(5, 2041, "TODOS", null, null, false, 0, 10).stream()
						.collect(Collectors.toMap(TesoreriaMovimientoDTO::getConcepto, TesoreriaMovimientoDTO::getCategoria)));
		assertEquals(Map.of("Cuota del mes MAYO 2041 - TAEKWONDO", "MENSUALIDAD",
				"Cuota de competición MAYO 2041", "TARIFA_COMPETIDOR"), categoriasTesoreria);

		// Derecho a examen por el tipo del producto (en Taemoi, los códigos de V24 solo por concepto).
		Long examen = TenantContext.calcularCon(org, () -> productos.findByCodigo("EXAMEN_AMARILLO").orElseThrow().getId());
		ProductoAlumnoDTO pagado = new ProductoAlumnoDTO();
		pagado.setPagado(true);
		TenantContext.ejecutarCon(org, () -> productoAlumnoService.asignarProductoAAlumno(alumno, examen, pagado));
		assertTrue(jdbc.queryForObject("SELECT tiene_derecho_examen FROM alumno WHERE id = ?", Boolean.class, alumno));

		// Taemoi no ve nada de esto.
		assertNotEquals("Cuota del mes", TenantContext.calcularCon(ORG_TAEMOI,
				() -> catalogo.resolverProductoMensualidad("TAEKWONDO").map(Producto::getConcepto).orElse(null)));
	}

	// ---------------------------------------------------------------------------------

	private String siguiente(String codigoGrado, int anioNacimiento) {
		Alumno alumno = new Alumno();
		alumno.setFechaNacimiento(Date.valueOf(LocalDate.of(anioNacimiento, 6, 1)));
		Disciplina judo = disciplinas.findByCodigo("JUDO").orElseThrow();
		AlumnoDeporte ad = new AlumnoDeporte();
		ad.setAlumno(alumno);
		ad.setDisciplina(judo);
		ad.setGrado(grados.findBySistemaGradosIdAndCodigo(judo.getSistemaGrados().getId(), codigoGrado).orElseThrow());
		return alumnoDeporteService.calcularSiguienteGrado(ad);
	}

	/** Escuela con TAEKWONDO (con un grado) y JUDO con cintos, tarifas y categorías propios. */
	private long escuelaDeJudo(String prefijo) {
		long org = escuela(prefijo);
		TenantContext.ejecutarCon(org, () -> {
			SistemaGrados sistema = new SistemaGrados();
			sistema.setNombre("Judo");
			sistema.setPlantillaOrigen("JUDO");
			sistema = sistemas.save(sistema);
			grado(sistema, "CINTO_BLANCO", "Cinto blanco", 1, AplicaGrado.TODOS);
			grado(sistema, "CINTO_AMARILLO", "Cinto amarillo", 2, AplicaGrado.MENORES);
			grado(sistema, "CINTO_NARANJA", "Cinto naranja", 3, AplicaGrado.TODOS);
			grado(sistema, "CINTO_VERDE", "Cinto verde", 4, AplicaGrado.ADULTOS);
			Disciplina judo = new Disciplina();
			judo.setCodigo("JUDO");
			judo.setNombre("Judo");
			judo.setSistemaGrados(sistema);
			judo.setEdadAdulto(14);
			judo.setPermiteCompeticion(true);
			judo.setRequiereLicencia(true);
			judo.setOrden(2);
			judo = disciplinas.save(judo);
			tarifa("CUOTA_JUDO", "Cuota de judo", "33.00", judo);
			tarifa("CUOTA_JUDO_REDUCIDA", "Cuota de judo reducida", "28.00", judo);
			categoria("Benjamín", judo, null, 10, 1);
			categoria("Alevín", judo, 11, 13, 2);
			categoria("Absoluto", judo, 14, null, 3);
		});
		return org;
	}

	private long escuela(String prefijo) {
		Organizacion o = new Organizacion();
		o.setSlug(prefijo + "-" + UUID.randomUUID().toString().substring(0, 8));
		o.setNombre("Escuela " + prefijo);
		long org = TenantContext.calcularCon(ORG_TAEMOI, () -> organizaciones.save(o).getId());
		assertNotEquals(ORG_TAEMOI, org);
		TenantContext.ejecutarCon(org, () -> DisciplinasDePrueba.conGrados(disciplinas, sistemas, grados, "TAEKWONDO", 14,
				com.taemoi.project.legado.TipoGrado.BLANCO));
		return org;
	}

	private void grado(SistemaGrados sistema, String codigo, String nombre, int orden, AplicaGrado aplica) {
		Grado g = new Grado();
		g.setCodigo(codigo);
		g.setNombre(nombre);
		g.setOrden(orden);
		g.setAplicaA(aplica);
		g.setSistemaGrados(sistema);
		g.setMesesMinimosMenor(2);
		g.setMesesMinimosAdulto(3);
		grados.save(g);
	}

	private void tarifa(String codigo, String nombre, String importe, Disciplina disciplina) {
		Tarifa t = new Tarifa();
		t.setCodigo(codigo);
		t.setNombre(nombre);
		t.setImporte(new BigDecimal(importe));
		t.setDisciplina(disciplina);
		tarifas.save(t);
	}

	private void categoria(String nombre, Disciplina disciplina, Integer edadMin, Integer edadMax, int orden) {
		Categoria c = new Categoria();
		c.setNombre(nombre);
		c.setDisciplina(disciplina);
		c.setEdadMin(edadMin);
		c.setEdadMax(edadMax);
		c.setOrden(orden);
		categorias.save(c);
	}

	private Producto producto(String codigo, TipoProducto tipo, String concepto, double precio, Disciplina disciplina) {
		Producto p = new Producto(concepto, precio, null);
		p.setCodigo(codigo);
		p.setTipo(tipo);
		p.setDisciplina(disciplina);
		return productos.save(p);
	}

	private long alumno(long org, int anioNacimiento) {
		return TenantContext.calcularCon(org, () -> {
			Alumno a = new Alumno();
			a.setNombre("IT");
			a.setApellidos("CATALOGO " + sufijo());
			a.setNumeroExpediente((int) (Math.abs(UUID.randomUUID().getMostSignificantBits()) % 900000) + 1000);
			a.setFechaNacimiento(Date.valueOf(LocalDate.of(anioNacimiento, 6, 1)));
			a.setDireccion("Calle 0");
			a.setTelefono("600000000");
			return alumnos.save(a).getId();
		});
	}

	/** Inscripción activa en la disciplina (con licencia, para poder ser competidor). */
	private AlumnoDeporte inscribir(long alumnoId, String disciplina, boolean licencia) {
		AlumnoDeporte ad = alumnoDeporteService.agregarDeporteAAlumno(alumnoId, disciplina, null,
				Date.valueOf("2040-09-01"), Date.valueOf("2040-09-01"), null);
		ad.setTieneLicencia(licencia);
		return ad;
	}

	private String altaRechazada(long org, String grado) {
		ResponseEntity<?> respuesta = crearRespuesta(org, """
				{"nombre":"JUDO","apellidos":"AJENO","fechaNacimiento":"1990-03-04","direccion":"Calle 1",
				 "telefono":600000005,"email":"judo-ajeno-%s@example.test","rolFamiliar":"NINGUNO","grupoFamiliar":"",
				 "deportesInicial":[{"deporte":"JUDO","grado":"%s","fechaAlta":"2040-09-01",
				   "codigoTarifa":"CUOTA_JUDO","cuantiaTarifa":33.0}]}""".formatted(sufijo(), grado));
		assertEquals(HttpStatus.BAD_REQUEST, respuesta.getStatusCode());
		return String.valueOf(respuesta.getBody());
	}

	private AlumnoDTO crear(long org, String json) {
		ResponseEntity<?> respuesta = crearRespuesta(org, json);
		assertEquals(HttpStatus.CREATED, respuesta.getStatusCode(), String.valueOf(respuesta.getBody()));
		return (AlumnoDTO) respuesta.getBody();
	}

	private ResponseEntity<?> crearRespuesta(long org, String json) {
		SecurityContextHolder.getContext().setAuthentication(admin());
		return TenantContext.calcularCon(org, () -> alumnoController.crearAlumno(json, null));
	}

	private static UsernamePasswordAuthenticationToken admin() {
		return new UsernamePasswordAuthenticationToken("admin-it", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
	}

	private static String sufijo() {
		return UUID.randomUUID().toString().substring(0, 8);
	}
}
