package com.taemoi.project.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.taemoi.project.entities.Organizacion;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.organizacion.TransaccionEnEscuela;
import com.taemoi.project.repositories.ImagenRepository;
import com.taemoi.project.repositories.OrganizacionRepository;

/**
 * Decisión 391: el buzón de cada escuela ({@code organizacion.email_contacto}) para el formulario de contacto y los
 * avisos internos de preinscripción. La escuela es la de la entidad (o la activa si no la tiene), como la marca; nunca
 * se cae en la escuela por defecto ni en otra escuela.
 */
class MarcaEscuelaBuzonTest {

	private static final String BUZON_1 = "buzon-uno@example.test";
	private static final String BUZON_2 = "buzon-dos@example.test";

	private final OrganizacionRepository organizaciones = mock(OrganizacionRepository.class);
	private final Organizacion uno = MarcaEscuelaPruebas.moiskimdo();
	private final Organizacion dos = MarcaEscuelaPruebas.escuela(2L, "Escuela Ficticia Dos");
	private final MarcaEscuelaService marca = MarcaEscuelaPruebas.real(organizaciones, mock(ImagenRepository.class),
			mock(ImagenService.class), uno, dos);

	@AfterEach
	void sinEscuelaEnElHilo() {
		assertNull(TenantContext.get(), "El test dejó una escuela fijada en el hilo");
	}

	@Test
	void buzonDeLaEscuelaDeLaEntidadAunqueElHiloTengaOtra() {
		uno.setEmailContacto(BUZON_1);
		dos.setEmailContacto(BUZON_2);
		TransaccionEnEscuela enEscuela = mock(TransaccionEnEscuela.class);
		MarcaEscuelaService directo = new MarcaEscuelaService(organizaciones, mock(ImagenRepository.class),
				mock(ImagenService.class), enEscuela, new AtomicLong()::get);

		assertEquals(BUZON_2, TenantContext.calcularCon(1L, () -> directo.buzonDeEntidadOActiva(2L)));
		assertEquals(BUZON_2, directo.buzonDeEntidadOActiva(2L), "hilo sin escuela (tras el commit)");
		assertEquals(BUZON_1, TenantContext.calcularCon(2L, () -> directo.buzonDeEntidadOActiva(1L)));
		verify(organizaciones, times(2)).findById(2L);
		verifyNoInteractions(enEscuela);
	}

	@Test
	void sinEntidadElDeLaEscuelaActiva() {
		uno.setEmailContacto(BUZON_1);
		dos.setEmailContacto(BUZON_2);

		assertEquals(BUZON_2, TenantContext.calcularCon(2L, () -> marca.buzonDeEntidadOActiva(null)));
		assertEquals(BUZON_2, TenantContext.calcularCon(2L, marca::buzonDeActiva));
		assertEquals(BUZON_1, TenantContext.calcularCon(1L, marca::buzonDeActiva));
	}

	@Test
	void sinEscuelaActivaFallaCerrado() {
		assertThrows(IllegalStateException.class, marca::buzonDeActiva);
		assertThrows(IllegalStateException.class, () -> marca.buzonDeEntidadOActiva(null));
	}

	@Test
	void escuelaSinBuzonOEnBlancoDaNullSinCaerEnOtra() {
		uno.setEmailContacto(BUZON_1);
		dos.setEmailContacto(null);
		assertNull(TenantContext.calcularCon(1L, () -> marca.buzonDeEntidadOActiva(2L)));

		dos.setEmailContacto("   ");
		assertNull(TenantContext.calcularCon(2L, marca::buzonDeActiva));
	}

	@Test
	void sinCacheYSinEspacios() {
		dos.setEmailContacto("  " + BUZON_2 + " ");
		assertEquals(BUZON_2, marca.buzonDeEntidadOActiva(2L));

		dos.setEmailContacto("nuevo-buzon@example.test");
		assertEquals("nuevo-buzon@example.test", marca.buzonDeEntidadOActiva(2L), "el cambio se ve al momento");
	}

	@Test
	void escuelaInexistenteFallaCerrado() {
		assertThrows(IllegalStateException.class, () -> marca.buzonDeEntidadOActiva(99L));
	}
}
