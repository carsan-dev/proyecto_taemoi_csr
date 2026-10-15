package com.taemoi.project.services.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.taemoi.project.entities.Organizacion;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.OrganizacionRepository;

/**
 * Caché por organización de {@code organizacion.comportamiento_legado_taemoi}: lee el valor de cada escuela,
 * lo conserva {@code VIGENCIA_MS} y lo vuelve a leer después. El reloj es controlable (sin esperas reales).
 */
class ComportamientoLegadoServiceImplTest {

	private static final long ORG_A = 1L;
	private static final long ORG_B = 2L;

	private OrganizacionRepository repositorio;
	private AtomicLong reloj;
	private ComportamientoLegadoServiceImpl servicio;

	@BeforeEach
	void preparar() {
		repositorio = mock(OrganizacionRepository.class);
		reloj = new AtomicLong(1_000_000L);
		servicio = new ComportamientoLegadoServiceImpl(repositorio, reloj::get);
	}

	@AfterEach
	void limpiarTenant() {
		TenantContext.clear();
	}

	private static Organizacion organizacion(Boolean legado) {
		Organizacion o = new Organizacion();
		o.setComportamientoLegadoTaemoi(legado);
		return o;
	}

	private boolean activoEn(long organizacion) {
		return TenantContext.calcularCon(organizacion, servicio::activo);
	}

	@Test
	void leeElValorDeLaOrganizacion() {
		when(repositorio.findById(ORG_A)).thenReturn(Optional.of(organizacion(true)));
		when(repositorio.findById(ORG_B)).thenReturn(Optional.of(organizacion(false)));

		assertTrue(activoEn(ORG_A));
		assertFalse(activoEn(ORG_B));
	}

	@Test
	void organizacionInexistenteONulaCuentaComoNoLegado() {
		when(repositorio.findById(ORG_A)).thenReturn(Optional.empty());
		when(repositorio.findById(ORG_B)).thenReturn(Optional.of(organizacion(null)));

		assertFalse(activoEn(ORG_A));
		assertFalse(activoEn(ORG_B));
	}

	@Test
	void dentroDeLaVigencia_sirveElValorGuardado_aunqueCambieEnBaseDeDatos() {
		when(repositorio.findById(ORG_A)).thenReturn(Optional.of(organizacion(true)));
		assertTrue(activoEn(ORG_A));

		when(repositorio.findById(ORG_A)).thenReturn(Optional.of(organizacion(false)));
		reloj.addAndGet(ComportamientoLegadoServiceImpl.VIGENCIA_MS); // justo en el límite: aún vigente

		assertTrue(activoEn(ORG_A));
		verify(repositorio, times(1)).findById(ORG_A);
	}

	@Test
	void pasadaLaVigencia_vuelveALeerYVeElCambio() {
		when(repositorio.findById(ORG_A)).thenReturn(Optional.of(organizacion(true)));
		assertTrue(activoEn(ORG_A));

		when(repositorio.findById(ORG_A)).thenReturn(Optional.of(organizacion(false)));
		reloj.addAndGet(ComportamientoLegadoServiceImpl.VIGENCIA_MS + 1);

		assertFalse(activoEn(ORG_A));
		verify(repositorio, times(2)).findById(ORG_A);

		// La nueva lectura empieza su propia vigencia.
		when(repositorio.findById(ORG_A)).thenReturn(Optional.of(organizacion(true)));
		reloj.addAndGet(ComportamientoLegadoServiceImpl.VIGENCIA_MS);
		assertFalse(activoEn(ORG_A));
		verify(repositorio, times(2)).findById(ORG_A);
	}

	@Test
	void lasOrganizacionesNoSeMezclan_niSeCaducanEntreSi() {
		when(repositorio.findById(ORG_A)).thenReturn(Optional.of(organizacion(true)));
		when(repositorio.findById(ORG_B)).thenReturn(Optional.of(organizacion(false)));
		assertTrue(activoEn(ORG_A));

		// B se lee más tarde: su vigencia es independiente de la de A.
		reloj.addAndGet(20_000L);
		assertFalse(activoEn(ORG_B));

		// A caduca (30,001 s desde su lectura); B (10 s desde la suya) no.
		reloj.addAndGet(10_001L);
		when(repositorio.findById(ORG_A)).thenReturn(Optional.of(organizacion(false)));
		when(repositorio.findById(ORG_B)).thenReturn(Optional.of(organizacion(true)));

		assertFalse(activoEn(ORG_A));
		assertFalse(activoEn(ORG_B));
		verify(repositorio, times(2)).findById(ORG_A);
		verify(repositorio, times(1)).findById(ORG_B);
	}

	@Test
	void sinOrganizacionActiva_lanzaIllegalStateException_sinConsultarLaBaseDeDatos() {
		IllegalStateException e = assertThrows(IllegalStateException.class, servicio::activo);

		assertTrue(e.getMessage().contains("No hay organización activa"));
		verify(repositorio, never()).findById(org.mockito.ArgumentMatchers.anyLong());
	}
}
