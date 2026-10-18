package com.taemoi.project.services;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.taemoi.project.events.PreinscripcionPromocionadaEvent;

/**
 * R2-P7d: los avisos de plaza salen solo tras confirmar la promoción: una promoción deshecha (p. ej., porque una
 * solicitud posterior de la cola falla) no avisa a nadie. Salen en el orden de la cola y, como antes, por delante del
 * aviso de la operación que liberó la plaza (cancelación o cambio de turnos, publicado antes en la misma transacción):
 * por eso este listener va justo antes que los demás (que tienen el orden por defecto, el último).
 */
@Component
public class PreinscripcionPromocionEmailListener {
	private final PreinscripcionPromocionEmailService emailService;

	public PreinscripcionPromocionEmailListener(PreinscripcionPromocionEmailService emailService) {
		this.emailService = emailService;
	}

	@Order(Ordered.LOWEST_PRECEDENCE - 1)
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void alPromocionar(PreinscripcionPromocionadaEvent event) {
		emailService.enviar(event);
	}
}
