package com.taemoi.project.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;

import com.taemoi.project.dtos.response.TesoreriaMovimientoDTO;
import com.taemoi.project.dtos.response.TesoreriaResumenDTO;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.ProductoAlumno;
import com.taemoi.project.repositories.ProductoAlumnoRepository;
import com.taemoi.project.services.impl.TesoreriaServiceImpl;

@ExtendWith(MockitoExtension.class)
class TesoreriaServiceImplTest {

	@Mock
	private ProductoAlumnoRepository productoAlumnoRepository;

	@InjectMocks
	private TesoreriaServiceImpl tesoreriaService;

	@Test
	void obtenerMovimientos_mensualidadUsaMesDelConceptoAntesQueFecha() {
		ProductoAlumno mensualidadFebrero = crearMovimiento(
				1L,
				"MENSUALIDAD FEBRERO 2026 - TAEKWONDO",
				fecha(2026, 1, 15),
				false,
				35.0);
		ProductoAlumno mensualidadEnero = crearMovimiento(
				2L,
				"MENSUALIDAD ENERO 2026 - TAEKWONDO",
				fecha(2026, 1, 10),
				false,
				35.0);
		ProductoAlumnoRepository.TesoreriaPeriodoBaseProjection baseFebrero = crearMovimientoBase(
				1L,
				"MENSUALIDAD FEBRERO 2026 - TAEKWONDO",
				fecha(2026, 1, 15));
		ProductoAlumnoRepository.TesoreriaPeriodoBaseProjection baseEnero = crearMovimientoBase(
				2L,
				"MENSUALIDAD ENERO 2026 - TAEKWONDO",
				fecha(2026, 1, 10));

		when(productoAlumnoRepository.findMovimientosTesoreriaPeriodoBase(
				any(),
				any(),
				any(),
				any(),
				any(),
				any(),
				any(),
				any(),
				any()))
				.thenReturn(List.of(baseFebrero, baseEnero));
		when(productoAlumnoRepository.findMovimientosTesoreriaByIds(any()))
				.thenReturn(List.of(mensualidadFebrero, mensualidadEnero));

		Page<TesoreriaMovimientoDTO> febrero = tesoreriaService.obtenerMovimientos(2, 2026, "TODOS", null, null, null, 1, 25);
		assertEquals(1, febrero.getTotalElements());
		assertTrue(febrero.getContent().get(0).getConcepto().contains("FEBRERO"));

		Page<TesoreriaMovimientoDTO> enero = tesoreriaService.obtenerMovimientos(1, 2026, "TODOS", null, null, null, 1, 25);
		assertEquals(1, enero.getTotalElements());
		assertTrue(enero.getContent().get(0).getConcepto().contains("ENERO"));
	}

	@Test
	void obtenerMovimientos_categoriaNoMensualidadPriorizaFecha() {
		ProductoAlumno licencia = crearMovimiento(
				3L,
				"LICENCIA FEBRERO 2026",
				fecha(2026, 1, 20),
				false,
				40.0);
		ProductoAlumnoRepository.TesoreriaPeriodoBaseProjection baseLicencia = crearMovimientoBase(
				3L,
				"LICENCIA FEBRERO 2026",
				fecha(2026, 1, 20));

		when(productoAlumnoRepository.findMovimientosTesoreriaPeriodoBase(
				any(),
				any(),
				any(),
				any(),
				any(),
				any(),
				any(),
				any(),
				any()))
				.thenReturn(List.of(baseLicencia));
		when(productoAlumnoRepository.findMovimientosTesoreriaByIds(any()))
				.thenReturn(List.of(licencia));

		Page<TesoreriaMovimientoDTO> febrero = tesoreriaService.obtenerMovimientos(2, 2026, "TODOS", null, null, null, 1, 25);
		assertEquals(0, febrero.getTotalElements());

		Page<TesoreriaMovimientoDTO> enero = tesoreriaService.obtenerMovimientos(1, 2026, "TODOS", null, null, null, 1, 25);
		assertEquals(1, enero.getTotalElements());
	}

	@Test
	void obtenerAniosDisponibles_incluyeAniosDesdeConceptoConFechaExistente() {
		when(productoAlumnoRepository.findAniosDistintosTesoreriaConFecha()).thenReturn(List.of(2025));
		when(productoAlumnoRepository.findConceptosTesoreriaConPeriodoPotencial())
				.thenReturn(List.of("MENSUALIDAD FEBRERO 2026 - TAEKWONDO"));
		when(productoAlumnoRepository.findConceptosTesoreriaSinFecha()).thenReturn(List.of());

		List<Integer> anios = tesoreriaService.obtenerAniosDisponibles();
		assertEquals(List.of(2025, 2026), anios);
	}

	@Test
	void obtenerMovimientos_conFiltroPeriodoPasaAnoYMesAlRepositorio() {
		ProductoAlumno mensualidadFebrero = crearMovimiento(
				4L,
				"MENSUALIDAD FEBRERO 2026 - TAEKWONDO",
				fecha(2026, 1, 15),
				false,
				35.0);
		ProductoAlumnoRepository.TesoreriaPeriodoBaseProjection baseFebrero = crearMovimientoBase(
				4L,
				"MENSUALIDAD FEBRERO 2026 - TAEKWONDO",
				fecha(2026, 1, 15));

		when(productoAlumnoRepository.findMovimientosTesoreriaPeriodoBase(
				any(),
				any(),
				any(),
				any(),
				eq(true),
				eq(2026),
				eq("2026"),
				eq(2),
				eq("FEBRERO")))
				.thenReturn(List.of(baseFebrero));
		when(productoAlumnoRepository.findMovimientosTesoreriaByIds(any()))
				.thenReturn(List.of(mensualidadFebrero));

		Page<TesoreriaMovimientoDTO> febrero = tesoreriaService.obtenerMovimientos(2, 2026, "TODOS", null, null, null, 1, 25);
		assertEquals(1, febrero.getTotalElements());
	}

	@Test
	void obtenerResumen_sinFiltroPeriodoUsaConsultasAgregadas() {
		when(productoAlumnoRepository.contarMovimientosTesoreria(any(), any(), isNull(), eq(true), isNull(), isNull(), isNull(), isNull()))
				.thenReturn(10L);
		when(productoAlumnoRepository.contarMovimientosTesoreria(any(), any(), eq(true), eq(true), isNull(), isNull(), isNull(), isNull()))
				.thenReturn(6L);
		when(productoAlumnoRepository.contarMovimientosTesoreria(any(), any(), eq(false), eq(true), isNull(), isNull(), isNull(), isNull()))
				.thenReturn(4L);
		when(productoAlumnoRepository.sumarImporteMovimientosTesoreria(any(), any(), isNull(), eq(true), isNull(), isNull(), isNull(), isNull()))
				.thenReturn(350.0);
		when(productoAlumnoRepository.sumarImporteMovimientosTesoreria(any(), any(), eq(true), eq(true), isNull(), isNull(), isNull(), isNull()))
				.thenReturn(240.0);
		when(productoAlumnoRepository.sumarImporteMovimientosTesoreria(any(), any(), eq(false), eq(true), isNull(), isNull(), isNull(), isNull()))
				.thenReturn(110.0);
		when(productoAlumnoRepository.contarAlumnosConPendientesTesoreria(any(), any(), eq(true), isNull(), isNull(), isNull(), isNull()))
				.thenReturn(3L);

		TesoreriaResumenDTO resumen = tesoreriaService.obtenerResumen(null, null, "TODOS", null);

		assertEquals(10L, resumen.getTotalMovimientos());
		assertEquals(6L, resumen.getTotalPagados());
		assertEquals(4L, resumen.getTotalPendientes());
		assertEquals(350.0, resumen.getImporteTotal());
		assertEquals(240.0, resumen.getImportePagado());
		assertEquals(110.0, resumen.getImportePendiente());
		assertEquals(3L, resumen.getAlumnosConPendientes());
		verify(productoAlumnoRepository, never()).findMovimientosTesoreriaFiltrados(
				any(),
				any(),
				any(),
				any(),
				any(),
				any(),
				any(),
				any(),
				any());
	}

	private ProductoAlumnoRepository.TesoreriaPeriodoBaseProjection crearMovimientoBase(
			Long id,
			String concepto,
			Date fechaAsignacion) {
		ProductoAlumnoRepository.TesoreriaPeriodoBaseProjection base =
				mock(ProductoAlumnoRepository.TesoreriaPeriodoBaseProjection.class);
		when(base.getId()).thenReturn(id);
		when(base.getConcepto()).thenReturn(concepto);
		when(base.getFechaAsignacion()).thenReturn(fechaAsignacion);
		when(base.getFechaPago()).thenReturn(null);
		return base;
	}

	private ProductoAlumno crearMovimiento(
			Long id,
			String concepto,
			Date fechaAsignacion,
			boolean pagado,
			double precio) {
		Alumno alumno = new Alumno();
		alumno.setId(id);
		alumno.setNombre("Alumno");
		alumno.setApellidos("Test");

		ProductoAlumno movimiento = new ProductoAlumno();
		movimiento.setId(id);
		movimiento.setAlumno(alumno);
		movimiento.setConcepto(concepto);
		movimiento.setFechaAsignacion(fechaAsignacion);
		movimiento.setPagado(pagado);
		movimiento.setPrecio(precio);
		movimiento.setCantidad(1);
		return movimiento;
	}

	private Date fecha(int year, int month, int day) {
		return Date.from(LocalDate.of(year, month, day).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant());
	}
}
