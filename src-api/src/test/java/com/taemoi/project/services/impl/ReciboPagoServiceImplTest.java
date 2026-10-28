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
import com.taemoi.project.entities.AlumnoDeporte;
import com.taemoi.project.entities.Cobro;
import com.taemoi.project.entities.EstadoCobro;
import com.taemoi.project.entities.EstadoMovimientoCaja;
import com.taemoi.project.entities.MetodoPago;
import com.taemoi.project.entities.Organizacion;
import com.taemoi.project.entities.ProductoAlumno;
import com.taemoi.project.entities.Sede;
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
 *
 * <p>Cargo de legado (pagado antes de V31_5, sin ninguna fila de cobro): el recibo sale del propio cargo, con la serie
 * {@code R-{año del pago}-C{id del cargo}}; cancelado, devuelto o sin fecha de pago, 409.
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
		ProductoAlumno sinPagar = new ProductoAlumno();
		sinPagar.setId(5L);
		sinPagar.setPagado(false);
		when(cargos.buscarEnEscuelaActiva(5L)).thenReturn(Optional.of(sinPagar));
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

	// ------------------------------------------------------------------ cargo de legado (sin ninguna fila de cobro)

	@Test
	void numeroDeLegado_serieConCYElIdDelCargo() {
		assertEquals("R-2025-C014192", ReciboPagoServiceImpl.numeroDeCargo(LocalDate.of(2025, 3, 10), 14_192L));
		assertEquals("R-2024-C000001", ReciboPagoServiceImpl.numeroDeCargo(LocalDate.of(2024, 12, 31), 1L));
		assertEquals("R-2025-C1234567", ReciboPagoServiceImpl.numeroDeCargo(LocalDate.of(2025, 6, 1), 1_234_567L),
				"más de 6 cifras: no se corta");
	}

	@Test
	void legadoPagado_reciboDelCargo() throws Exception {
		Alumno alumno = new Alumno();
		alumno.setNombre("Ana");
		alumno.setApellidos("Ficticia Uno");
		alumno.setNif("00000000T");
		alumno.setEmail("ana@example.test");
		alumno.setTelefono("600000000");
		ProductoAlumno cargo = legado(14_192L, alumno, 14.5, 2, "2025-03-10T19:00");
		cargo.setSedeId(3L);
		Sede sede = new Sede();
		sede.setNombre("Sede Ficticia Norte");
		when(sedes.findById(3L)).thenReturn(Optional.of(sede));

		ReciboPago recibo = servicio.generar(14_192L);
		assertEquals("R-2025-C014192", recibo.numero());
		assertEquals("recibo-R-2025-C014192.pdf", recibo.nombreFichero());
		String texto = String.join("\n", ContenidoPdf.lineas(recibo.pdf()));
		for (String esperado : new String[] { "R-2025-C014192", "Fecha del cobro 10/03/2025", "Ana Ficticia Uno",
				"CAMISETA LEGADO", "29,00 €", "Sede Ficticia Norte", "No es una factura.", "Emitido el 16/10/2026" }) {
			assertTrue(texto.contains(esperado), "falta «" + esperado + "» en:\n" + texto);
		}
		for (String prohibido : new String[] { "Forma de pago", "Sin indicar", "00000000T", "ana@example.test",
				"600000000" }) {
			assertFalse(texto.contains(prohibido), "no debe salir «" + prohibido + "»:\n" + texto);
		}
	}

	@Test
	void legadoPagado_cantidadNulaOMenorQueUno_cuentaComoUna_yNoHaySede() throws Exception {
		Alumno alumno = new Alumno();
		alumno.setNombre("Ana");
		alumno.setApellidos("Ficticia");
		legado(20L, alumno, 28.0, null, "2025-01-05T10:00");
		String texto = String.join("\n", ContenidoPdf.lineas(servicio.generar(20L).pdf()));
		assertTrue(texto.contains("28,00 €"), texto);
		assertFalse(texto.contains("Sede"), "cargo sin sede: sin fila «Sede»: " + texto);

		legado(21L, alumno, 28.0, 0, "2025-01-05T10:00");
		assertTrue(String.join("\n", ContenidoPdf.lineas(servicio.generar(21L).pdf())).contains("28,00 €"));
	}

	@Test
	void legadoPagado_alumnoPorLaInscripcion() throws Exception {
		Alumno alumno = new Alumno();
		alumno.setNombre("Luis");
		alumno.setApellidos("Ficticio Dos");
		ProductoAlumno cargo = legado(22L, null, 30.0, 1, "2025-02-01T10:00");
		AlumnoDeporte inscripcion = new AlumnoDeporte();
		inscripcion.setAlumno(alumno);
		cargo.setAlumnoDeporte(inscripcion);
		assertTrue(String.join("\n", ContenidoPdf.lineas(servicio.generar(22L).pdf())).contains("Luis Ficticio Dos"));
	}

	@Test
	void legadoNoPagado_sinCobro() {
		ProductoAlumno cargo = legado(23L, null, 28.0, 1, null);
		cargo.setPagado(false);
		assertEquals(ReciboPagoServiceImpl.SIN_COBRO,
				assertThrows(IllegalStateException.class, () -> servicio.generar(23L)).getMessage());
		cargo.setPagado(null);
		assertEquals(ReciboPagoServiceImpl.SIN_COBRO,
				assertThrows(IllegalStateException.class, () -> servicio.generar(23L)).getMessage());
	}

	@Test
	void legadoCancelado_conflicto() {
		ProductoAlumno cargo = legado(24L, null, 28.0, 1, "2025-02-01T10:00");
		cargo.setCanceladoEn(LocalDateTime.parse("2025-02-02T10:00"));
		assertEquals(ReciboPagoServiceImpl.CARGO_CANCELADO,
				assertThrows(IllegalStateException.class, () -> servicio.generar(24L)).getMessage());
	}

	@Test
	void legadoDevuelto_conflicto() {
		legado(25L, null, 28.0, 1, "2025-02-01T10:00");
		when(movimientosCaja.existsByProductoAlumnoIdAndTipoAndEstado(25L, TipoMovimientoCaja.DEVOLUCION,
				EstadoMovimientoCaja.ACTIVO)).thenReturn(true);
		assertEquals(ReciboPagoServiceImpl.CARGO_DEVUELTO,
				assertThrows(IllegalStateException.class, () -> servicio.generar(25L)).getMessage());
	}

	@Test
	void legadoSinFechaDePago_conflicto() {
		legado(26L, null, 28.0, 1, null);
		assertEquals(ReciboPagoServiceImpl.SIN_FECHA_PAGO,
				assertThrows(IllegalStateException.class, () -> servicio.generar(26L)).getMessage());
	}

	@Test
	void conCobroAnulado_noSaleDelCargo_aunqueEstePagado() {
		legado(27L, null, 28.0, 1, "2025-02-01T10:00");
		when(cobros.findByProductoAlumnoIdOrderByIdAsc(27L)).thenReturn(List.of(cobro(42L, null, EstadoCobro.ANULADO)));
		assertEquals(ReciboPagoServiceImpl.COBRO_ANULADO,
				assertThrows(IllegalStateException.class, () -> servicio.generar(27L)).getMessage());
	}

	/** Cargo pagado de legado de la escuela activa: sin cobro ACTIVO y sin ninguna fila de cobro. */
	private ProductoAlumno legado(long id, Alumno alumno, Double precio, Integer cantidad, String fechaPago) {
		ProductoAlumno cargo = new ProductoAlumno();
		cargo.setId(id);
		cargo.setAlumno(alumno);
		cargo.setConcepto("CAMISETA LEGADO");
		cargo.setPrecio(precio);
		cargo.setCantidad(cantidad);
		cargo.setPagado(true);
		cargo.setFechaPago(fechaPago == null ? null
				: Date.from(LocalDateTime.parse(fechaPago).atZone(java.time.ZoneId.systemDefault()).toInstant()));
		when(cargos.existsById(id)).thenReturn(true);
		when(cobros.findFirstByProductoAlumnoIdAndEstado(id, EstadoCobro.ACTIVO)).thenReturn(Optional.empty());
		when(cobros.findByProductoAlumnoIdOrderByIdAsc(id)).thenReturn(List.of());
		when(cargos.buscarEnEscuelaActiva(id)).thenReturn(Optional.of(cargo));
		return cargo;
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
