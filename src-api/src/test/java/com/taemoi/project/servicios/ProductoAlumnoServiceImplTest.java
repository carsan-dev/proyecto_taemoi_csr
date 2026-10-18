package com.taemoi.project.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import com.taemoi.project.dtos.ProductoAlumnoDTO;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.AlumnoDeporte;
import com.taemoi.project.legado.Deporte;
import com.taemoi.project.entities.Producto;
import com.taemoi.project.entities.ProductoAlumno;
import com.taemoi.project.repositories.AlumnoConvocatoriaRepository;
import com.taemoi.project.repositories.AlumnoDeporteRepository;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.ProductoAlumnoRepository;
import com.taemoi.project.repositories.ProductoRepository;
import com.taemoi.project.services.AlumnoDeporteService;
import com.taemoi.project.services.impl.ProductoAlumnoServiceImpl;

@ExtendWith(MockitoExtension.class)
class ProductoAlumnoServiceImplTest {

	@Mock
	private ProductoAlumnoRepository productoAlumnoRepository;

	@Mock
	private ProductoRepository productoRepository;

	@Mock
	private AlumnoRepository alumnoRepository;

	@Mock
	private AlumnoConvocatoriaRepository alumnoConvocatoriaRepository;

	@Mock
	private AlumnoDeporteRepository alumnoDeporteRepository;

	@Mock
	private AlumnoDeporteService alumnoDeporteService;

	/** Entrega 6-3: sin sesión de responsable ni sedes inactivas (los valores por defecto del simulado). */
	@Mock
	private com.taemoi.project.acceso.GestionPorSede gestionPorSede;

	/** Entrega 6-4: sin tarifas por sede, la cuantía de la inscripción tal cual (identidad). */
	@Mock
	private com.taemoi.project.services.TarifaSedeService tarifaSede;

	/**
	 * Pieza 6b-1: registro de cobros simulado. La transacción ejecuta la acción tal cual y «bloquear» el cargo es leerlo
	 * del repositorio simulado, así que el flujo del cargo es el de antes; los cobros se prueban en
	 * {@code CobrosIntegrationTest}.
	 */
	@Mock
	private com.taemoi.project.services.CobroService cobros;

	@InjectMocks
	private ProductoAlumnoServiceImpl productoAlumnoService;

	/**
	 * Desde V27 los productos se resuelven con el catálogo: el real sobre el repositorio simulado de
	 * este test y el catálogo de disciplinas de Taemoi en memoria. Los productos simulados no están
	 * clasificados (sin codigo ni tipo), así que se encuentran por el concepto de antes (respaldo).
	 */
	@org.junit.jupiter.api.BeforeEach
	void catalogoEnMemoria() {
		org.mockito.Mockito.lenient().when(tarifaSede.preparar())
				.thenReturn(com.taemoi.project.services.ResolutorCuantiaMensualidad.identidad());
		org.mockito.Mockito.lenient().when(cobros.enTransaccion(any()))
				.thenAnswer(invocacion -> ((java.util.function.Supplier<?>) invocacion.getArgument(0)).get());
		org.mockito.Mockito.lenient().when(cobros.bloquearCargo(any()))
				.thenAnswer(invocacion -> productoAlumnoRepository.findById(invocacion.getArgument(0)));
		com.taemoi.project.catalogo.CatalogoTaemoiEnMemoria memoria = new com.taemoi.project.catalogo.CatalogoTaemoiEnMemoria();
		org.springframework.test.util.ReflectionTestUtils.setField(productoAlumnoService, "catalogoDisciplinas",
				memoria.catalogo());
		org.springframework.test.util.ReflectionTestUtils.setField(productoAlumnoService, "progresionGrados",
				memoria.progresion());
		org.springframework.test.util.ReflectionTestUtils.setField(productoAlumnoService, "productosCatalogo",
				new com.taemoi.project.services.impl.ProductosCatalogoServiceImpl(productoRepository,
						org.mockito.Mockito.mock(com.taemoi.project.repositories.GradoRepository.class),
						memoria.catalogo(), memoria.progresion(), memoria.comportamientoTaemoi()));
		org.springframework.test.util.ReflectionTestUtils.setField(productoAlumnoService, "comportamientoLegado",
				memoria.comportamientoTaemoi());
	}

	private void prepararAsignacion(String concepto) {
		Alumno alumno = new Alumno();
		alumno.setId(10L);
		Producto producto = new Producto();
		producto.setId(20L);
		producto.setConcepto(concepto);
		producto.setPrecio(25.0);
		when(alumnoRepository.findById(10L)).thenReturn(Optional.of(alumno));
		when(productoRepository.findById(20L)).thenReturn(Optional.of(producto));
	}

	@Test
	void matricula_genericaRechazaAcentosYMayusculasSinGuardar() {
		for (String concepto : List.of("MATRICULA", "Matrícula", "matrícula anual")) {
			prepararAsignacion(concepto);
			ResponseStatusException ex = assertThrows(ResponseStatusException.class,
					() -> productoAlumnoService.asignarProductoAAlumno(10L, 20L, new ProductoAlumnoDTO()));
			assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
		}
		verify(productoAlumnoRepository, never()).save(any());
	}

	@Test
	void matricula_deporteAusenteInvalidoONoInscritoNoGuarda() {
		prepararAsignacion("Matrícula");
		for (String deporte : new String[] { null, "", "TODOS", "PILATES" }) {
			assertThrows(IllegalArgumentException.class,
					() -> productoAlumnoService.asignarProductoAAlumnoDeporte(10L, 20L, deporte, new ProductoAlumnoDTO()));
		}
		verify(productoAlumnoRepository, never()).save(any());
	}

	@Test
	void matricula_dosDeportesCreanSoloDosCargosConConceptoYAsociacion() {
		prepararAsignacion("Matrícula anual");
		when(productoAlumnoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		for (Deporte deporte : List.of(Deporte.TAEKWONDO, Deporte.KICKBOXING)) {
			AlumnoDeporte ad = new AlumnoDeporte();
			ad.setDisciplina(com.taemoi.project.catalogo.CatalogoTaemoiEnMemoria.disciplinaTaemoi(deporte.name()));
			when(alumnoDeporteRepository.findByAlumnoIdAndDeporte(10L, deporte.name())).thenReturn(Optional.of(ad));
			ProductoAlumnoDTO resultado = productoAlumnoService.asignarProductoAAlumnoDeporte(
					10L, 20L, deporte.name().toLowerCase(), new ProductoAlumnoDTO());
			assertEquals("Matrícula anual - " + deporte.name(), resultado.getConcepto());
		}
		ArgumentCaptor<ProductoAlumno> captor = ArgumentCaptor.forClass(ProductoAlumno.class);
		verify(productoAlumnoRepository, times(2)).save(captor.capture());
		assertEquals(Deporte.TAEKWONDO.name(), captor.getAllValues().get(0).getAlumnoDeporte().getDeporte());
		assertEquals(Deporte.KICKBOXING.name(), captor.getAllValues().get(1).getAlumnoDeporte().getDeporte());
	}

	@Test
	void matricula_conceptoConDeporteNoLoDuplica() {
		prepararAsignacion("Matrícula - taekwondo");
		AlumnoDeporte ad = new AlumnoDeporte();
		ad.setDisciplina(com.taemoi.project.catalogo.CatalogoTaemoiEnMemoria.disciplinaTaemoi(Deporte.TAEKWONDO.name()));
		when(alumnoDeporteRepository.findByAlumnoIdAndDeporte(10L, Deporte.TAEKWONDO.name())).thenReturn(Optional.of(ad));
		when(productoAlumnoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		assertEquals("Matrícula - taekwondo", productoAlumnoService.asignarProductoAAlumnoDeporte(
				10L, 20L, "TAEKWONDO", new ProductoAlumnoDTO()).getConcepto());
	}

	@Test
	void productoNoMatricula_conservaAsignacionGenerica() {
		prepararAsignacion("DOBOK");
		when(productoAlumnoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		assertEquals("DOBOK", productoAlumnoService.asignarProductoAAlumno(10L, 20L, new ProductoAlumnoDTO()).getConcepto());
		ArgumentCaptor<ProductoAlumno> captor = ArgumentCaptor.forClass(ProductoAlumno.class);
		verify(productoAlumnoRepository).save(captor.capture());
		assertNull(captor.getValue().getAlumnoDeporte());
	}

	/** D6-17 y D6-23 (entrega 6-3): la masiva salta a los alumnos de sedes inactivas y dice cuántos. */
	@Test
	void mensualidadesGenerales_saltaAlumnosDeSedesInactivasYLosCuenta() {
		Producto mensualidad = new Producto();
		mensualidad.setId(30L);
		mensualidad.setConcepto("MENSUALIDAD");
		when(productoRepository.findFirstByConcepto("MENSUALIDAD")).thenReturn(Optional.of(mensualidad));
		when(productoAlumnoRepository.findAll()).thenReturn(List.of());
		AlumnoDeporte activa = inscripcionEnSede(1L, 1L);
		AlumnoDeporte inactiva = inscripcionEnSede(2L, 9L);
		when(alumnoDeporteRepository.findAll()).thenReturn(List.of(activa, inactiva));

		// Todas las sedes activas (Moiskimdo): se generan las dos y no se omite nadie.
		when(gestionPorSede.sedesInactivas()).thenReturn(java.util.Set.of());
		assertEquals(0, productoAlumnoService.cargarMensualidadesGenerales("2026-05", null).alumnosOmitidosSedeInactiva());
		verify(productoAlumnoRepository, times(2)).save(any());

		// Sede 9 inactiva: solo la de la sede activa; un alumno omitido.
		org.mockito.Mockito.clearInvocations(productoAlumnoRepository);
		when(productoAlumnoRepository.findAll()).thenReturn(List.of());
		when(gestionPorSede.sedesInactivas()).thenReturn(java.util.Set.of(9L));
		assertEquals(1, productoAlumnoService.cargarMensualidadesGenerales("2026-06", null).alumnosOmitidosSedeInactiva());
		ArgumentCaptor<ProductoAlumno> captor = ArgumentCaptor.forClass(ProductoAlumno.class);
		verify(productoAlumnoRepository).save(captor.capture());
		assertEquals(1L, captor.getValue().getAlumno().getId());
	}

	private static AlumnoDeporte inscripcionEnSede(long alumnoId, long sede) {
		Alumno alumno = new Alumno();
		alumno.setId(alumnoId);
		alumno.setSedePrincipalId(sede);
		AlumnoDeporte ad = new AlumnoDeporte();
		ad.setAlumno(alumno);
		ad.setActivo(true);
		ad.setCuantiaTarifa(30.0);
		ad.setDisciplina(com.taemoi.project.catalogo.CatalogoTaemoiEnMemoria.disciplinaTaemoi(Deporte.TAEKWONDO.name()));
		return ad;
	}

	@AfterEach
	void limpiarContexto() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void actualizarProductoAlumno_managerNoPuedeRevertirPagado() {
		ProductoAlumno productoAlumno = crearProductoPagado();
		ProductoAlumnoDTO cambios = new ProductoAlumnoDTO();
		cambios.setPagado(false);
		cambios.setMotivoCambio("error");

		when(productoAlumnoRepository.findById(1L)).thenReturn(Optional.of(productoAlumno));
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
				"manager@test.com",
				null,
				java.util.List.of(new SimpleGrantedAuthority("ROLE_MANAGER"))));

		ResponseStatusException ex = assertThrows(
				ResponseStatusException.class,
				() -> productoAlumnoService.actualizarProductoAlumno(1L, cambios));

		assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
		verify(productoAlumnoRepository, never()).save(any(ProductoAlumno.class));
	}

	@Test
	void actualizarProductoAlumno_adminRevertirLimpiaFechaPago() {
		ProductoAlumno productoAlumno = crearProductoPagado();
		ProductoAlumnoDTO cambios = new ProductoAlumnoDTO();
		cambios.setPagado(false);
		cambios.setMotivoCambio("reversion");

		when(productoAlumnoRepository.findById(1L)).thenReturn(Optional.of(productoAlumno));
		when(productoAlumnoRepository.save(any(ProductoAlumno.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(alumnoConvocatoriaRepository.findByProductoAlumnoId(1L)).thenReturn(Optional.empty());
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
				"admin@test.com",
				null,
				java.util.List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

		ProductoAlumnoDTO resultado = productoAlumnoService.actualizarProductoAlumno(1L, cambios);

		assertFalse(resultado.getPagado());
		assertNull(resultado.getFechaPago());
		verify(productoAlumnoRepository).save(any(ProductoAlumno.class));
		// Pieza 6b-2 (decisión del propietario): un cargo que no es de examen no toca el derecho a examen del alumno.
		verify(alumnoRepository, never()).save(any(Alumno.class));
	}

	@Test
	void actualizarProductoAlumno_fechaAsignacionModificadaSeGuarda() {
		Date nuevaFecha = Date.from(java.time.Instant.parse("2026-02-15T23:30:12.123Z"));
		comprobarActualizacionFecha(nuevaFecha, nuevaFecha);
	}

	@Test
	void actualizarProductoAlumno_fechaAsignacionNullUOmitidaConservaOriginal() {
		comprobarActualizacionFecha(null, Date.from(java.time.Instant.parse("2026-01-20T23:30:12.123Z")));
	}

	private void comprobarActualizacionFecha(Date solicitada, Date esperada) {
		ProductoAlumno producto = crearProductoPagado();
		producto.setFechaAsignacion(Date.from(java.time.Instant.parse("2026-01-20T23:30:12.123Z")));
		Date fechaPagoOriginal = producto.getFechaPago();
		ProductoAlumnoDTO cambios = new ProductoAlumnoDTO();
		cambios.setFechaAsignacion(solicitada);
		when(productoAlumnoRepository.findById(1L)).thenReturn(Optional.of(producto));
		when(productoAlumnoRepository.save(any(ProductoAlumno.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(alumnoConvocatoriaRepository.findByProductoAlumnoId(1L)).thenReturn(Optional.empty());

		ProductoAlumnoDTO resultado = productoAlumnoService.actualizarProductoAlumno(1L, cambios);

		ArgumentCaptor<ProductoAlumno> captor = ArgumentCaptor.forClass(ProductoAlumno.class);
		verify(productoAlumnoRepository).save(captor.capture());
		assertEquals(esperada, captor.getValue().getFechaAsignacion());
		assertEquals(esperada, resultado.getFechaAsignacion());
		assertEquals(fechaPagoOriginal, resultado.getFechaPago());
		assertEquals(35.0, resultado.getPrecio());
		assertEquals(1, resultado.getCantidad());
	}

	private ProductoAlumno crearProductoPagado() {
		Alumno alumno = new Alumno();
		alumno.setId(10L);
		alumno.setNombre("Alumno");
		alumno.setApellidos("Test");
		alumno.setTieneDerechoExamen(true);

		ProductoAlumno productoAlumno = new ProductoAlumno();
		productoAlumno.setId(1L);
		productoAlumno.setAlumno(alumno);
		productoAlumno.setConcepto("MENSUALIDAD ENERO 2026");
		productoAlumno.setPagado(true);
		productoAlumno.setFechaPago(new Date());
		productoAlumno.setPrecio(35.0);
		productoAlumno.setCantidad(1);
		return productoAlumno;
	}

	@Test
	void cargarMensualidadIndividualPorDeporte_aplicaMismaFechaManualAMensualidadYTarifa() {
		Long alumnoId = 1L;
		LocalDate fechaManual = LocalDate.of(2026, 1, 15);
		Date fechaEsperada = Date.from(fechaManual.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant());

		Alumno alumno = new Alumno();
		alumno.setId(alumnoId);
		alumno.setNombre("Alumno");
		alumno.setApellidos("Test");
		alumno.setProductosAlumno(new ArrayList<>());

		AlumnoDeporte alumnoDeporte = new AlumnoDeporte();
		alumnoDeporte.setAlumno(alumno);
		alumnoDeporte.setDisciplina(com.taemoi.project.catalogo.CatalogoTaemoiEnMemoria.disciplinaTaemoi(Deporte.TAEKWONDO.name()));
		alumnoDeporte.setActivo(true);
		alumnoDeporte.setCompetidor(true);
		alumnoDeporte.setCuantiaTarifa(35.0);

		Producto productoMensualidad = new Producto();
		productoMensualidad.setId(100L);
		productoMensualidad.setConcepto("MENSUALIDAD");
		productoMensualidad.setPrecio(30.0);

		Producto productoTarifa = new Producto();
		productoTarifa.setId(200L);
		productoTarifa.setConcepto("TARIFA COMPETIDOR TAEKWONDO");
		productoTarifa.setPrecio(20.0);

		when(alumnoRepository.findById(alumnoId)).thenReturn(Optional.of(alumno));
		when(alumnoDeporteRepository.findByAlumnoIdAndDeporte(alumnoId, Deporte.TAEKWONDO.name()))
				.thenReturn(Optional.of(alumnoDeporte));
		when(productoRepository.findFirstByConcepto("MENSUALIDAD")).thenReturn(Optional.of(productoMensualidad));
		when(productoRepository.findFirstByConcepto("TARIFA COMPETIDOR TAEKWONDO"))
				.thenReturn(Optional.of(productoTarifa));
		when(productoAlumnoRepository.findByAlumnoId(alumnoId)).thenReturn(List.of());
		when(productoAlumnoRepository.save(any(ProductoAlumno.class))).thenAnswer(invocation -> invocation.getArgument(0));

		productoAlumnoService.cargarMensualidadIndividualPorDeporte(
				alumnoId,
				"TAEKWONDO",
				"2026-02",
				false,
				fechaManual);

		ArgumentCaptor<ProductoAlumno> captor = ArgumentCaptor.forClass(ProductoAlumno.class);
		verify(productoAlumnoRepository, times(2)).save(captor.capture());
		List<ProductoAlumno> guardados = captor.getAllValues();
		assertEquals(2, guardados.size());
		assertEquals(fechaEsperada, guardados.get(0).getFechaAsignacion());
		assertEquals(fechaEsperada, guardados.get(1).getFechaAsignacion());
	}

	@Test
	void cargarMensualidadIndividualPorDeporte_usaSoloLaTarifaDelDeporte() {
		Long alumnoId = 2L;
		Alumno alumno = new Alumno();
		alumno.setId(alumnoId);
		alumno.setNombre("Alumno");
		alumno.setApellidos("Tarifa");
		alumno.setProductosAlumno(new ArrayList<>());

		AlumnoDeporte kickboxing = new AlumnoDeporte();
		kickboxing.setAlumno(alumno);
		kickboxing.setDisciplina(com.taemoi.project.catalogo.CatalogoTaemoiEnMemoria.disciplinaTaemoi(Deporte.KICKBOXING.name()));
		kickboxing.setActivo(true);
		kickboxing.setCompetidor(false);
		kickboxing.setCuantiaTarifa(42.0);

		Producto productoMensualidad = new Producto();
		productoMensualidad.setId(100L);
		productoMensualidad.setConcepto("MENSUALIDAD");
		productoMensualidad.setPrecio(30.0);

		when(alumnoRepository.findById(alumnoId)).thenReturn(Optional.of(alumno));
		when(alumnoDeporteRepository.findByAlumnoIdAndDeporte(alumnoId, Deporte.KICKBOXING.name()))
				.thenReturn(Optional.of(kickboxing));
		when(productoRepository.findFirstByConcepto("MENSUALIDAD")).thenReturn(Optional.of(productoMensualidad));
		when(productoAlumnoRepository.save(any(ProductoAlumno.class))).thenAnswer(invocation -> invocation.getArgument(0));

		productoAlumnoService.cargarMensualidadIndividualPorDeporte(alumnoId, "KICKBOXING", "2026-02", false,
				LocalDate.of(2026, 2, 1));

		ArgumentCaptor<ProductoAlumno> captor = ArgumentCaptor.forClass(ProductoAlumno.class);
		verify(productoAlumnoRepository).save(captor.capture());
		assertEquals(42.0, captor.getValue().getPrecio());
		assertEquals(kickboxing, captor.getValue().getAlumnoDeporte());
	}

	@Test
	void resolverFechaAsignacion_conNullDevuelveFechaActual() throws Exception {
		Method method = ProductoAlumnoServiceImpl.class.getDeclaredMethod("resolverFechaAsignacion", LocalDate.class);
		method.setAccessible(true);
		Date resultado = (Date) method.invoke(productoAlumnoService, new Object[] { null });
		assertNotNull(resultado);
	}
}
