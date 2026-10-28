package com.taemoi.project.services.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.taemoi.project.config.ValidacionRelojConfig;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.Cobro;
import com.taemoi.project.entities.EstadoCobro;
import com.taemoi.project.entities.EstadoMovimientoCaja;
import com.taemoi.project.entities.MetodoPago;
import com.taemoi.project.entities.Organizacion;
import com.taemoi.project.entities.TipoMovimientoCaja;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.pdf.ContenidoPdf;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.CobroRepository;
import com.taemoi.project.repositories.MovimientoCajaRepository;
import com.taemoi.project.repositories.OrganizacionRepository;
import com.taemoi.project.repositories.ProductoAlumnoRepository;
import com.taemoi.project.repositories.SedeRepository;
import com.taemoi.project.services.MarcaEscuelaPruebas;
import com.taemoi.project.services.ReciboPagoService.ReciboPago;

/**
 * Pieza 279: número y formato del recibo de pago, y las reglas del servicio con repositorios simulados (sin cobro,
 * anulado, cargo ajeno, alumno sin nombre, datos que no salen, escape de HTML). Datos ficticios.
 */
class ReciboPagoServiceImplTest {

	private final ProductoAlumnoRepository cargos = mock(ProductoAlumnoRepository.class);
	private final CobroRepository cobros = mock(CobroRepository.class);
	private final MovimientoCajaRepository movimientosCaja = mock(MovimientoCajaRepository.class);
	private final AlumnoRepository alumnos = mock(AlumnoRepository.class);
	private final SedeRepository sedes = mock(SedeRepository.class);
	private final OrganizacionRepository organizaciones = mock(OrganizacionRepository.class);
	private final Clock reloj = Clock.fixed(
			LocalDateTime.parse("2026-10-16T00:30").atZone(ValidacionRelojConfig.ZONA).toInstant(),
			java.time.ZoneOffset.UTC);
	private ReciboPagoServiceImpl servicio;
	private Locale localeAnterior;

	@BeforeEach
	void montar() {
		localeAnterior = Locale.getDefault();
		Organizacion escuela = MarcaEscuelaPruebas.escuela(2L, "Escuela Ficticia & Sierra");
		escuela.setNombreLegal("Club Deportivo Ficticio");
		escuela.setCif("G00000000");
		servicio = new ReciboPagoServiceImpl(cargos, cobros, movimientosCaja, alumnos, sedes, organizaciones,
				MarcaEscuelaPruebas.real(organizaciones, mock(com.taemoi.project.repositories.ImagenRepository.class),
						mock(com.taemoi.project.services.ImagenService.class), escuela),
				reloj);
		TenantContext.set(2L);
	}

	@AfterEach
	void limpiar() {
		TenantContext.clear();
		Locale.setDefault(localeAnterior);
	}

	// ------------------------------------------------------------------ número, importe y forma de pago

	@Test
	void numero_rellenaA6CifrasConElAnoDelCobro() {
		assertEquals("R-2026-000123", ReciboPagoServiceImpl.numero(LocalDate.of(2026, 1, 1), 123L));
		assertEquals("R-2025-000001", ReciboPagoServiceImpl.numero(LocalDate.of(2025, 12, 31), 1L));
		assertEquals("R-2026-999999", ReciboPagoServiceImpl.numero(LocalDate.of(2026, 6, 1), 999_999L));
		assertEquals("R-2026-1234567", ReciboPagoServiceImpl.numero(LocalDate.of(2026, 6, 1), 1_234_567L),
				"más de 6 cifras: no se corta");
	}

	@Test
	void importe_formatoEspanolSinDependerDelLocaleDeLaJvm() {
		for (Locale locale : List.of(Locale.ROOT, Locale.US, Locale.GERMANY, Locale.of("es", "ES"))) {
			Locale.setDefault(locale);
			assertEquals("28,00 €", ReciboPagoServiceImpl.importe(new BigDecimal("28")), locale.toString());
			assertEquals("0,50 €", ReciboPagoServiceImpl.importe(new BigDecimal("0.5")));
			assertEquals("1.234,56 €", ReciboPagoServiceImpl.importe(new BigDecimal("1234.56")));
			assertEquals("26,01 €", ReciboPagoServiceImpl.importe(new BigDecimal("26.005")), "redondeo a la mitad");
		}
	}

	@Test
	void formaDePago_etiquetaLegible() {
		assertEquals("Efectivo", ReciboPagoServiceImpl.formaDePago(MetodoPago.EFECTIVO));
		assertEquals("Transferencia", ReciboPagoServiceImpl.formaDePago(MetodoPago.TRANSFERENCIA));
		assertEquals("Tarjeta", ReciboPagoServiceImpl.formaDePago(MetodoPago.TARJETA));
		assertEquals("Bizum", ReciboPagoServiceImpl.formaDePago(MetodoPago.BIZUM));
		assertEquals("Sin indicar", ReciboPagoServiceImpl.formaDePago(MetodoPago.SIN_INDICAR));
		assertEquals("Sin indicar", ReciboPagoServiceImpl.formaDePago(null));
	}

	// ------------------------------------------------------------------ reglas del servicio

	@Test
	void cargoQueNoEstaEnLaEscuela_noEncontrado() {
		when(cargos.existsById(9L)).thenReturn(false);
		assertThrows(NoSuchElementException.class, () -> servicio.generar(9L));
		assertThrows(NoSuchElementException.class, () -> servicio.generar(null));
	}

	@Test
	void cargoSinCobro_conflicto() {
		when(cargos.existsById(5L)).thenReturn(true);
		when(cobros.findFirstByProductoAlumnoIdAndEstado(5L, EstadoCobro.ACTIVO)).thenReturn(Optional.empty());
		when(cobros.findByProductoAlumnoIdOrderByIdAsc(5L)).thenReturn(List.of());
		IllegalStateException error = assertThrows(IllegalStateException.class, () -> servicio.generar(5L));
		assertEquals(ReciboPagoServiceImpl.SIN_COBRO, error.getMessage());
	}

	@Test
	void cobroAnulado_conflicto() {
		when(cargos.existsById(5L)).thenReturn(true);
		when(cobros.findFirstByProductoAlumnoIdAndEstado(5L, EstadoCobro.ACTIVO)).thenReturn(Optional.empty());
		Cobro anulado = cobro(40L, null, EstadoCobro.ANULADO);
		when(cobros.findByProductoAlumnoIdOrderByIdAsc(5L)).thenReturn(List.of(anulado));
		IllegalStateException error = assertThrows(IllegalStateException.class, () -> servicio.generar(5L));
		assertEquals(ReciboPagoServiceImpl.COBRO_ANULADO, error.getMessage());
	}

	@Test
	void cobroDevuelto_conflicto() {
		activo(cobro(41L, 7L, EstadoCobro.ACTIVO));
		when(movimientosCaja.existsByCobroIdAndTipoAndEstado(41L, TipoMovimientoCaja.DEVOLUCION,
				EstadoMovimientoCaja.ACTIVO)).thenReturn(true);
		IllegalStateException error = assertThrows(IllegalStateException.class, () -> servicio.generar(5L));
		assertEquals(ReciboPagoServiceImpl.COBRO_DEVUELTO, error.getMessage());
	}

	@Test
	void recibo_soloNombreDelAlumno_yEscapaElHtml() throws Exception {
		Alumno alumno = new Alumno();
		alumno.setNombre("Ana");
		alumno.setApellidos("Ficticia <Uno>");
		alumno.setNif("00000000T");
		alumno.setEmail("ana@example.test");
		when(alumnos.findById(7L)).thenReturn(Optional.of(alumno));
		activo(cobro(123L, 7L, EstadoCobro.ACTIVO));

		ReciboPago recibo = servicio.generar(5L);
		assertEquals("R-2026-000123", recibo.numero());
		assertEquals("recibo-R-2026-000123.pdf", recibo.nombreFichero());
		String texto = String.join("\n", ContenidoPdf.lineas(recibo.pdf()));
		assertTrue(texto.contains("Ana Ficticia <Uno>"), texto);
		assertTrue(texto.contains("Escuela Ficticia & Sierra"), texto);
		assertTrue(texto.contains("CAMISETA & PANTALÓN"), texto);
		assertTrue(texto.contains("Emitido el 16/10/2026"), "la fecha de Madrid, no la UTC del reloj: " + texto);
		assertFalse(texto.contains("00000000T"), texto);
		assertFalse(texto.contains("ana@example.test"), texto);
	}

	@Test
	void alumnoBorrado_saleAlumnoSinNombre() throws Exception {
		activo(cobro(124L, null, EstadoCobro.ACTIVO));
		assertTrue(ContenidoPdf.lineas(servicio.generar(5L).pdf()).contains("Alumno"), "«Alumno» sin nombre");

		when(alumnos.findById(8L)).thenReturn(Optional.empty());
		activo(cobro(125L, 8L, EstadoCobro.ACTIVO));
		assertTrue(ContenidoPdf.lineas(servicio.generar(5L).pdf()).contains("Alumno"), "alumno que ya no existe");
	}

	private void activo(Cobro cobro) {
		when(cargos.existsById(5L)).thenReturn(true);
		when(cobros.findFirstByProductoAlumnoIdAndEstado(5L, EstadoCobro.ACTIVO)).thenReturn(Optional.of(cobro));
	}

	private static Cobro cobro(long id, Long alumnoId, EstadoCobro estado) {
		Cobro cobro = new Cobro();
		ReflectionTestUtils.setField(cobro, "id", id);
		cobro.setProductoAlumnoId(5L);
		cobro.setAlumnoId(alumnoId);
		cobro.setConcepto("CAMISETA & PANTALÓN");
		cobro.setImporte(new BigDecimal("28.00"));
		cobro.setMetodo(MetodoPago.EFECTIVO);
		cobro.setFechaCobro(Date.from(LocalDateTime.parse("2026-03-10T12:00")
				.atZone(java.time.ZoneId.systemDefault()).toInstant()));
		cobro.setEstado(estado);
		return cobro;
	}
}
