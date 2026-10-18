package com.taemoi.project.services;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.taemoi.project.events.PreinscripcionRecibidaEvent;

/**
 * R2-P7d: los correos del alta pública salen solo tras confirmarla, con los turnos ya liberados: un alta deshecha no
 * avisa a nadie y un fallo del SMTP nunca la deshace. Primero la familia y después el buzón interno, como antes.
 */
@Component
public class PreinscripcionAltaEmailListener {
	private final PreinscripcionAltaEmailService emailService;

	public PreinscripcionAltaEmailListener(PreinscripcionAltaEmailService emailService) {
		this.emailService = emailService;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void alRecibir(PreinscripcionRecibidaEvent event) {
		try {
			emailService.enviar(event);
		} finally {
			/* Independiente del correo a la familia, como antes: sale aunque aquel falle (p. ej., espera de bloqueo agotada). */
			emailService.avisarAdmin(event);
		}
	}
}
