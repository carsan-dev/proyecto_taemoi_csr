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
import com.taemoi.project.entities.Deporte;
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

	@InjectMocks
	private ProductoAlumnoServiceImpl productoAlumnoService;

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
			ad.setDeporte(deporte);
			when(alumnoDeporteRepository.findByAlumnoIdAndDeporte(10L, deporte)).thenReturn(Optional.of(ad));
			ProductoAlumnoDTO resultado = productoAlumnoService.asignarProductoAAlumnoDeporte(
					10L, 20L, deporte.name().toLowerCase(), new ProductoAlumnoDTO());
			assertEquals("Matrícula anual - " + deporte.name(), resultado.getConcepto());
		}
		ArgumentCaptor<ProductoAlumno> captor = ArgumentCaptor.forClass(ProductoAlumno.class);
		verify(productoAlumnoRepository, times(2)).save(captor.capture());
		assertEquals(Deporte.TAEKWONDO, captor.getAllValues().get(0).getAlumnoDeporte().getDeporte());
		assertEquals(Deporte.KICKBOXING, captor.getAllValues().get(1).getAlumnoDeporte().getDeporte());
	}

	@Test
	void matricula_conceptoConDeporteNoLoDuplica() {
		prepararAsignacion("Matrícula - taekwondo");
		AlumnoDeporte ad = new AlumnoDeporte();
		ad.setDeporte(Deporte.TAEKWONDO);
		when(alumnoDeporteRepository.findByAlumnoIdAndDeporte(10L, Deporte.TAEKWONDO)).thenReturn(Optional.of(ad));
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
		when(alumnoRepository.save(any(Alumno.class))).thenAnswer(invocation -> invocation.getArgument(0));
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
				"admin@test.com",
				null,
				java.util.List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

		ProductoAlumnoDTO resultado = productoAlumnoService.actualizarProductoAlumno(1L, cambios);

		assertFalse(resultado.getPagado());
		assertNull(resultado.getFechaPago());
		verify(productoAlumnoRepository).save(any(ProductoAlumno.class));
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
		alumno.setCuantiaTarifa(35.0);
		alumno.setProductosAlumno(new ArrayList<>());

		AlumnoDeporte alumnoDeporte = new AlumnoDeporte();
		alumnoDeporte.setAlumno(alumno);
		alumnoDeporte.setDeporte(Deporte.TAEKWONDO);
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
		when(alumnoDeporteRepository.findByAlumnoIdAndDeporte(alumnoId, Deporte.TAEKWONDO))
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
	void resolverFechaAsignacion_conNullDevuelveFechaActual() throws Exception {
		Method method = ProductoAlumnoServiceImpl.class.getDeclaredMethod("resolverFechaAsignacion", LocalDate.class);
		method.setAccessible(true);
		Date resultado = (Date) method.invoke(productoAlumnoService, new Object[] { null });
		assertNotNull(resultado);
	}
}
