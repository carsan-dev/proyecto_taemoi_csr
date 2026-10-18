package com.taemoi.project.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.taemoi.project.entities.Cobro;
import com.taemoi.project.entities.EstadoCobro;
import com.taemoi.project.entities.MetodoPago;
import com.taemoi.project.entities.OrigenCobro;
import com.taemoi.project.entities.ProductoAlumno;
import com.taemoi.project.exceptions.pago.CobroEntregadoException;
import com.taemoi.project.repositories.CobroRepository;
import com.taemoi.project.repositories.ProductoAlumnoRepository;
import com.taemoi.project.services.CobroService;

/**
 * Pieza 6b-1: reglas de {@link CobroService} sin base de datos. La red de seguridad de la clave única (repetir una vez
 * en lugar de dar un 500), la idempotencia de {@code registrar}, el importe como la tesorería y el 409 con entrega.
 * El comportamiento contra MySQL (bloqueo, dos puertas, concurrencia) está en {@code CobrosIntegrationTest}.
 */
class CobroServiceTest {

	private CobroRepository cobros;
	private CobroService servicio;

	@BeforeEach
	void preparar() {
		cobros = mock(CobroRepository.class);
		PlatformTransactionManager transacciones = mock(PlatformTransactionManager.class);
		when(transacciones.getTransaction(any())).thenAnswer(i -> new SimpleTransactionStatus());
		servicio = new CobroService(cobros, mock(ProductoAlumnoRepository.class), transacciones);
	}

	@AfterEach
	void limpiar() {
		TransactionSynchronizationManager.setActualTransactionActive(false);
	}

	@Test
	void claveUnicaDeCobroActivo_repiteUnaVezYDevuelveElResultado() {
		AtomicInteger intentos = new AtomicInteger();
		String resultado = servicio.enTransaccion(() -> {
			if (intentos.incrementAndGet() == 1) {
				throw duplicado();
			}
			return "ok";
		});
		assertEquals("ok", resultado);
		assertEquals(2, intentos.get());
	}

	@Test
	void claveUnicaDosVeces_409NoUn500() {
		AtomicInteger intentos = new AtomicInteger();
		assertThrows(IllegalStateException.class, () -> servicio.enTransaccion(() -> {
			intentos.incrementAndGet();
			throw duplicado();
		}));
		assertEquals(2, intentos.get());
	}

	@Test
	void otraViolacionDeIntegridad_noSeRepite() {
		AtomicInteger intentos = new AtomicInteger();
		assertThrows(DataIntegrityViolationException.class, () -> servicio.enTransaccion(() -> {
			intentos.incrementAndGet();
			throw new DataIntegrityViolationException("Cannot add or update a child row: fk_cobro_sede");
		}));
		assertEquals(1, intentos.get());
	}

	@Test
	void dentroDeUnaTransaccionAjena_noSeRepite() {
		TransactionSynchronizationManager.setActualTransactionActive(true);
		AtomicInteger intentos = new AtomicInteger();
		assertThrows(DataIntegrityViolationException.class, () -> servicio.enTransaccion(() -> {
			intentos.incrementAndGet();
			throw duplicado();
		}));
		assertEquals(1, intentos.get());
	}

	@Test
	void registrar_conUnCobroActivo_noCreaOtro() {
		Cobro existente = new Cobro();
		when(cobros.findFirstByProductoAlumnoIdAndEstado(7L, EstadoCobro.ACTIVO)).thenReturn(Optional.of(existente));
		assertSame(existente, servicio.registrar(cargo(7L, 12.0), MetodoPago.EFECTIVO, null, OrigenCobro.TESORERIA));
		verify(cobros, never()).save(any());
	}

	@Test
	void registrar_importeComoLaTesoreria_yMetodoPorDefecto() {
		when(cobros.findFirstByProductoAlumnoIdAndEstado(any(), any())).thenReturn(Optional.empty());
		when(cobros.save(any())).thenAnswer(i -> i.getArgument(0));
		ProductoAlumno sinPrecio = cargo(8L, null);
		Cobro cobro = servicio.registrar(sinPrecio, null, null, OrigenCobro.CONVOCATORIA);
		assertEquals(0, BigDecimal.ZERO.compareTo(cobro.getImporte()), "COALESCE(precio, 0)");
		assertEquals(MetodoPago.SIN_INDICAR, cobro.getMetodo());
		assertEquals(EstadoCobro.ACTIVO, cobro.getEstado());
		assertEquals(OrigenCobro.CONVOCATORIA, cobro.getOrigen());
		assertEquals(8L, cobro.getProductoAlumnoId());
		assertEquals(new BigDecimal("33.34"), servicio.registrar(cargo(9L, 33.335), null, null, OrigenCobro.TESORERIA)
				.getImporte());
	}

	@Test
	void anular_conEntrega_409SinCambios() {
		Cobro entregado = new Cobro();
		entregado.setEntregaCajaId(3L);
		when(cobros.findFirstByProductoAlumnoIdAndEstado(5L, EstadoCobro.ACTIVO)).thenReturn(Optional.of(entregado));
		assertThrows(CobroEntregadoException.class, () -> servicio.anular(cargo(5L, 1.0), 1L, "x"));
		assertEquals(EstadoCobro.ACTIVO, entregado.getEstado());
		verify(cobros, never()).save(any());
	}

	private static ProductoAlumno cargo(Long id, Double precio) {
		ProductoAlumno cargo = new ProductoAlumno();
		cargo.setId(id);
		cargo.setPrecio(precio);
		cargo.setPagado(true);
		return cargo;
	}

	private static DataIntegrityViolationException duplicado() {
		return new DataIntegrityViolationException("could not execute statement",
				new RuntimeException("Duplicate entry '7' for key 'cobro.uk_cobro_activo_por_cargo'"));
	}
}
