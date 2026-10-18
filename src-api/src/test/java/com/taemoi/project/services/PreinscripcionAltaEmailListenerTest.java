package com.taemoi.project.services;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.taemoi.project.events.PreinscripcionRecibidaEvent;

/** R2-P7d: el aviso interno del alta se intenta siempre, también si el correo a la familia falla (como antes). */
class PreinscripcionAltaEmailListenerTest {

	private final PreinscripcionRecibidaEvent evento = new PreinscripcionRecibidaEvent("PRE-1", "Plaza asignada · PRE-1",
			"<p>x</p>", "admin@example.test", "Nueva preinscripción recibida: PRE-1", "<p>y</p>");

	@Test
	void primeroLaFamiliaYDespuesElBuzonInterno() {
		PreinscripcionAltaEmailService servicio = mock(PreinscripcionAltaEmailService.class);

		new PreinscripcionAltaEmailListener(servicio).alRecibir(evento);

		InOrder orden = inOrder(servicio);
		orden.verify(servicio).enviar(evento);
		orden.verify(servicio).avisarAdmin(evento);
	}

	@Test
	void siElEnvioALaFamiliaFallaElAvisoInternoSaleIgual() {
		PreinscripcionAltaEmailService servicio = mock(PreinscripcionAltaEmailService.class);
		doThrow(new IllegalStateException("Lock wait timeout exceeded")).when(servicio).enviar(evento);

		assertThrows(IllegalStateException.class, () -> new PreinscripcionAltaEmailListener(servicio).alRecibir(evento));

		verify(servicio).avisarAdmin(evento);
	}
}
