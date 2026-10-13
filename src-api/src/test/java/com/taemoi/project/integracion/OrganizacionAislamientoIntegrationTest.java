package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.Deporte;
import com.taemoi.project.entities.EstadoPreinscripcion;
import com.taemoi.project.entities.Grupo;
import com.taemoi.project.entities.Organizacion;
import com.taemoi.project.entities.PlantillaPreinscripcion;
import com.taemoi.project.entities.Preinscripcion;
import com.taemoi.project.entities.Producto;
import com.taemoi.project.entities.ProductoAlumno;
import com.taemoi.project.entities.Turno;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.GrupoRepository;
import com.taemoi.project.repositories.OrganizacionRepository;
import com.taemoi.project.repositories.PlantillaPreinscripcionRepository;
import com.taemoi.project.repositories.PreinscripcionRepository;
import com.taemoi.project.repositories.ProductoAlumnoRepository;
import com.taemoi.project.repositories.ProductoRepository;
import com.taemoi.project.repositories.TurnoRepository;
import com.taemoi.project.services.AlumnoService;
import com.taemoi.project.services.ContadorService;

/**
 * Aislamiento por organización con {@code @TenantId} contra MySQL real: lo creado en una
 * escuela no se ve desde otra por repositorios derivados, JPQL {@code @Query},
 * Specifications ni carga por id; y los números de expediente son por escuela.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrganizacionAislamientoIntegrationTest extends MySqlIntegrationTest {

	private static final long ORG_TAEMOI = 1L;

	@Autowired OrganizacionRepository organizaciones;
	@Autowired AlumnoRepository alumnos;
	@Autowired GrupoRepository grupos;
	@Autowired TurnoRepository turnos;
	@Autowired ProductoRepository productos;
	@Autowired ProductoAlumnoRepository productosAlumno;
	@Autowired PlantillaPreinscripcionRepository plantillas;
	@Autowired PreinscripcionRepository preinscripciones;
	@Autowired AlumnoService alumnoService;
	@Autowired ContadorService contadores;
	@Autowired TransactionTemplate tx;
	@Autowired JdbcTemplate jdbc;

	long org2;
	Datos taemoi;
	Datos otra;

	@BeforeAll
	void crearDatosEnDosOrganizaciones() {
		org2 = crearOrganizacion("it-aislamiento");
		taemoi = TenantContext.calcularCon(ORG_TAEMOI, () -> crearDatos("TAEMOI"));
		otra = TenantContext.calcularCon(org2, () -> crearDatos("OTRA"));
	}

	@AfterEach
	void sinFugaDeOrganizacion() {
		assertNull(TenantContext.get(), "El test dejó una organización fijada en el hilo");
	}

	@Test
	void cadaFilaGuardaLaOrganizacionConLaQueSeCreo() {
		assertEquals(ORG_TAEMOI, organizacionDe("alumno", taemoi.alumnoId));
		assertEquals(org2, organizacionDe("alumno", otra.alumnoId));
		assertEquals(org2, organizacionDe("grupo", otra.grupoId));
		assertEquals(org2, organizacionDe("turno", otra.turnoId));
		assertEquals(org2, organizacionDe("producto", otra.productoId));
		assertEquals(org2, organizacionDe("producto_alumno", otra.productoAlumnoId));
		assertEquals(org2, organizacionDe("plantilla_preinscripcion", otra.plantillaId));
		assertEquals(org2, organizacionDe("preinscripcion", otra.preinscripcionId));
	}

	@Test
	void taemoiNoVeNadaDeLaOtraOrganizacion() {
		TenantContext.ejecutarCon(ORG_TAEMOI, () -> noVe(otra, taemoi));
	}

	@Test
	void laOtraOrganizacionNoVeNadaDeTaemoi() {
		TenantContext.ejecutarCon(org2, () -> noVe(taemoi, otra));
	}

	@Test
	void sinOrganizacionFijadaSeUsaLaPorDefecto() {
		assertTrue(alumnos.findById(taemoi.alumnoId).isPresent());
		assertTrue(alumnos.findById(otra.alumnoId).isEmpty());
	}

	@Test
	void guardarUnaEntidadDeOtraOrganizacionNoLaModifica() {
		Alumno ajeno = TenantContext.calcularCon(org2, () -> alumnos.findById(otra.alumnoId).orElseThrow());
		ajeno.setNombre("MODIFICADO DESDE TAEMOI");

		assertThrows(RuntimeException.class,
				() -> TenantContext.ejecutarCon(ORG_TAEMOI, () -> alumnos.save(ajeno)));

		assertEquals(otra.marca, jdbc.queryForObject(
				"SELECT nombre FROM alumno WHERE id = ?", String.class, otra.alumnoId));
		assertEquals(0, jdbc.queryForObject(
				"SELECT COUNT(*) FROM alumno WHERE nombre = 'MODIFICADO DESDE TAEMOI'", Integer.class));
	}

	@Test
	void dosOrganizacionesNuevasEmpiezanAmbasEnElExpedienteUno() {
		long orgA = crearOrganizacion("it-expediente-a");
		long orgB = crearOrganizacion("it-expediente-b");

		Long alumnoA = TenantContext.calcularCon(orgA, () -> crearAlumnoConContador("EXP A"));
		Long alumnoB = TenantContext.calcularCon(orgB, () -> crearAlumnoConContador("EXP B"));

		assertEquals(1, expedienteDe(alumnoA));
		assertEquals(1, expedienteDe(alumnoB));
		assertEquals(1L, valorContador(orgA));
		assertEquals(1L, valorContador(orgB));
	}

	@Test
	void expedientesConcurrentesEnUnaOrganizacionNuevaNoSeRepiten() throws Exception {
		long org = crearOrganizacion("it-expediente-concurrente");

		comprobarConsecutivos(altasConcurrentes(org, 8), 0, 8);
		assertEquals(8L, valorContador(org));
	}

	@Test
	void expedientesConcurrentesConContadorExistenteEnTaemoiNoSeRepiten() throws Exception {
		// Como en producción: la fila del contador ya existe (V18) y hay altas previas.
		TenantContext.calcularCon(ORG_TAEMOI, () -> crearAlumnoConContador("PREVIA TAEMOI"));
		long inicio = Math.max(valorContador(ORG_TAEMOI), maximoExpediente(ORG_TAEMOI));
		int hilos = 8;

		comprobarConsecutivos(altasConcurrentes(ORG_TAEMOI, hilos), inicio, hilos);
		assertEquals(inicio + hilos, valorContador(ORG_TAEMOI));
	}

	@Test
	void contadorPorDetrasDelMaximoSeRecuperaEnLaSiguienteAlta() {
		long org = crearOrganizacion("it-expediente-atrasado");
		Long primera = TenantContext.calcularCon(org, () -> crearAlumnoConContador("PRIMERA"));
		assertEquals(1, expedienteDe(primera));
		// Alta fuera de la aplicación (importación SQL, restauración, código antiguo).
		jdbc.update("""
				INSERT INTO alumno (activo, apellidos, autorizacion_web, direccion, fecha_nacimiento, nombre,
				  numero_expediente, telefono, tiene_derecho_examen, organizacion_id)
				VALUES (1, 'IMPORTADO', 1, 'Calle 1', '2010-01-01', 'IMPORTADO', 500, 600000000, 0, ?)
				""", org);

		Long siguiente = TenantContext.calcularCon(org, () -> crearAlumnoConContador("TRAS IMPORTACION"));

		assertEquals(501, expedienteDe(siguiente));
		assertEquals(501L, valorContador(org));
	}

	private Set<Integer> altasConcurrentes(long org, int hilos) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(hilos);
		CountDownLatch salida = new CountDownLatch(1);
		try {
			List<Future<Long>> futuros = new ArrayList<>();
			for (int i = 0; i < hilos; i++) {
				String nombre = "CONCURRENTE " + i;
				futuros.add(executor.submit(() -> {
					salida.await(10, TimeUnit.SECONDS);
					return TenantContext.calcularCon(org, () -> crearAlumnoConContador(nombre));
				}));
			}
			salida.countDown();
			Set<Integer> numeros = new HashSet<>();
			for (Future<Long> futuro : futuros) {
				numeros.add(expedienteDe(futuro.get(60, TimeUnit.SECONDS)));
			}
			return numeros;
		} finally {
			executor.shutdownNow();
		}
	}

	private static void comprobarConsecutivos(Set<Integer> numeros, long inicio, int cantidad) {
		assertEquals(cantidad, numeros.size(), "Números de expediente repetidos: " + numeros);
		for (long n = inicio + 1; n <= inicio + cantidad; n++) {
			assertTrue(numeros.contains((int) n), "Falta el expediente " + n + " en " + numeros);
		}
	}

	private long maximoExpediente(long org) {
		return jdbc.queryForObject("SELECT COALESCE(MAX(numero_expediente), 0) FROM alumno WHERE organizacion_id = ?",
				Long.class, org);
	}

	// ---------------------------------------------------------------------------------

	private void noVe(Datos ajenos, Datos propios) {
		// Carga por id
		assertTrue(alumnos.findById(ajenos.alumnoId).isEmpty());
		assertTrue(grupos.findById(ajenos.grupoId).isEmpty());
		assertTrue(turnos.findById(ajenos.turnoId).isEmpty());
		assertTrue(productos.findById(ajenos.productoId).isEmpty());
		assertTrue(productosAlumno.findById(ajenos.productoAlumnoId).isEmpty());
		assertTrue(plantillas.findById(ajenos.plantillaId).isEmpty());
		assertTrue(preinscripciones.findById(ajenos.preinscripcionId).isEmpty());
		assertTrue(alumnos.findById(propios.alumnoId).isPresent());

		// findAll y consultas derivadas
		assertFalse(ids(alumnos.findAll()).contains(ajenos.alumnoId));
		assertTrue(alumnos.findByNombreContainingIgnoreCase(ajenos.marca).isEmpty());
		assertFalse(alumnos.findByNombreContainingIgnoreCase(propios.marca).isEmpty());
		assertTrue(productosAlumno.findByAlumnoId(ajenos.alumnoId).isEmpty());
		assertFalse(gruposIds().contains(ajenos.grupoId));
		assertFalse(preinscripciones.findAll().stream().anyMatch(p -> p.getId().equals(ajenos.preinscripcionId)));

		// JPQL @Query de AlumnoRepository
		assertEquals(0L, alumnos.contarAlumnosPorGrupo(ajenos.grupoNombre));
		assertEquals(1L, alumnos.contarAlumnosPorGrupo(propios.grupoNombre));
		assertFalse(ids(alumnos.findAlumnosSinGrupo()).contains(ajenos.alumnoId));
		assertTrue(alumnos.findAptosParaExamenPorDeporte(ajenos.marca, "NADA-QUE-EXCLUIR").isEmpty());
		assertTrue(alumnos.findAptoParaExamenById(ajenos.alumnoId).isEmpty());
		assertFalse(ids(alumnos.findAlumnosAptosParaExamen()).contains(ajenos.alumnoId));

		// Specification usada por AlumnoServiceImpl.obtenerAlumnosFiltrados
		assertTrue(alumnoService.obtenerAlumnosFiltrados(ajenos.marca, null, null, true).isEmpty());
		assertEquals(1, alumnoService.obtenerAlumnosFiltrados(propios.marca, null, null, true).size());

		// JPQL @Query de ProductoAlumnoRepository
		assertFalse(idsPa(productosAlumno.findAllUnpaidWithAlumno()).contains(ajenos.productoAlumnoId));
		assertTrue(idsPa(productosAlumno.findAllUnpaidWithAlumno()).contains(propios.productoAlumnoId));
		assertTrue(productosAlumno.findReservasPlazaByTemporada(ajenos.marca, false).isEmpty());
		assertEquals(1, productosAlumno.findReservasPlazaByTemporada(propios.marca, false).size());
		assertFalse(productosAlumno.findConceptosReservaPlaza().contains(ajenos.conceptoReserva));
		assertTrue(productosAlumno.findConceptosReservaPlaza().contains(propios.conceptoReserva));

		// Turnos con JOIN FETCH y bloqueo
		assertTrue(tx.execute(s -> turnos.findByIdForUpdate(ajenos.turnoId)).isEmpty());
	}

	private Datos crearDatos(String prefijo) {
		String marca = prefijo + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
		Datos d = new Datos(marca);

		Alumno alumno = alumnoNuevo(marca, contadores.siguienteNumeroExpediente());
		alumno.setAptoParaExamen(true);
		d.alumnoId = alumnos.save(alumno).getId();

		Grupo grupo = new Grupo();
		grupo.setNombre("G " + marca);
		grupo.setDeporte(Deporte.TAEKWONDO);
		grupo.getAlumnos().add(alumnos.findById(d.alumnoId).orElseThrow());
		Grupo grupoGuardado = grupos.save(grupo);
		d.grupoId = grupoGuardado.getId();
		d.grupoNombre = grupoGuardado.getNombre();
		d.turnoId = turnos.save(new Turno("Lunes", "18:00", "19:00", grupoGuardado)).getId();

		Producto producto = productos.save(new Producto("PRODUCTO " + marca, 10.0, null));
		d.productoId = producto.getId();
		ProductoAlumno pa = new ProductoAlumno();
		pa.setAlumno(alumnos.findById(d.alumnoId).orElseThrow());
		pa.setProducto(producto);
		d.conceptoReserva = "RESERVA DE PLAZA " + marca;
		pa.setConcepto(d.conceptoReserva);
		pa.setCantidad(1);
		pa.setPrecio(10.0);
		pa.setPagado(false);
		d.productoAlumnoId = productosAlumno.save(pa).getId();

		// La misma plantilla (deporte, versión) y la misma identidad en las dos escuelas:
		// los únicos ahora incluyen organizacion_id.
		PlantillaPreinscripcion plantilla = new PlantillaPreinscripcion();
		plantilla.setDeporte(Deporte.PILATES);
		plantilla.setVersion(900);
		plantilla.setActiva(false);
		plantilla.setContenido("{}");
		plantilla.setInstrucciones("-");
		d.plantillaId = plantillas.save(plantilla).getId();
		d.preinscripcionId = preinscripciones.save(preinscripcion(marca, plantilla)).getId();
		return d;
	}

	private Preinscripcion preinscripcion(String marca, PlantillaPreinscripcion plantilla) {
		Preinscripcion p = new Preinscripcion();
		p.setReferencia("IT" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
		p.setTemporada("2099-2100");
		p.setDeporte(Deporte.PILATES);
		p.setEstado(EstadoPreinscripcion.PENDIENTE);
		p.setNombre("PRE " + marca);
		p.setApellidos("PRUEBA");
		p.setIdentidadHash("a".repeat(64));
		p.setFechaNacimiento(LocalDate.of(1990, 1, 1));
		p.setDireccion("Calle 1");
		p.setTelefono("600000000");
		p.setEmail("it@example.test");
		p.setAceptacionNormas(true);
		p.setFirmanteNombre("PRUEBA");
		p.setFirma("firma".getBytes(StandardCharsets.UTF_8));
		p.setPlantilla(plantilla);
		p.setPlantillaSnapshot("{}");
		p.setPdfFirmado("pdf".getBytes(StandardCharsets.UTF_8));
		p.setTokenDocumentoHash("b".repeat(64));
		return p;
	}

	private Long crearAlumnoConContador(String nombre) {
		return tx.execute(s -> alumnos.save(alumnoNuevo(nombre, contadores.siguienteNumeroExpediente())).getId());
	}

	private Alumno alumnoNuevo(String nombre, int expediente) {
		Alumno alumno = new Alumno();
		alumno.setNombre(nombre);
		alumno.setApellidos("PRUEBA AISLAMIENTO");
		alumno.setNumeroExpediente(expediente);
		alumno.setFechaNacimiento(Date.valueOf(LocalDate.of(2010, 5, 5)));
		alumno.setDireccion("Calle Prueba 1");
		alumno.setTelefono(600000000);
		alumno.setActivo(true);
		alumno.setAutorizacionWeb(true);
		return alumno;
	}

	private long crearOrganizacion(String prefijoSlug) {
		Organizacion org = new Organizacion();
		org.setSlug(prefijoSlug + "-" + UUID.randomUUID().toString().substring(0, 8));
		org.setNombre("Escuela " + prefijoSlug);
		long id = organizaciones.save(org).getId();
		assertNotEquals(ORG_TAEMOI, id);
		return id;
	}

	private long organizacionDe(String tabla, Long id) {
		return jdbc.queryForObject("SELECT organizacion_id FROM " + tabla + " WHERE id = ?", Long.class, id);
	}

	private int expedienteDe(Long alumnoId) {
		return jdbc.queryForObject("SELECT numero_expediente FROM alumno WHERE id = ?", Integer.class, alumnoId);
	}

	private long valorContador(long org) {
		return jdbc.queryForObject(
				"SELECT valor FROM contador WHERE organizacion_id = ? AND clave = 'numero_expediente'", Long.class, org);
	}

	private Set<Long> gruposIds() {
		Set<Long> ids = new HashSet<>();
		grupos.findAll().forEach(g -> ids.add(g.getId()));
		return ids;
	}

	private static List<Long> ids(List<Alumno> lista) {
		return lista.stream().map(Alumno::getId).toList();
	}

	private static List<Long> idsPa(List<ProductoAlumno> lista) {
		return lista.stream().map(ProductoAlumno::getId).toList();
	}

	static final class Datos {
		final String marca;
		Long alumnoId;
		Long grupoId;
		String grupoNombre;
		Long turnoId;
		Long productoId;
		Long productoAlumnoId;
		String conceptoReserva;
		Long plantillaId;
		Long preinscripcionId;

		Datos(String marca) {
			this.marca = marca;
		}
	}
}
