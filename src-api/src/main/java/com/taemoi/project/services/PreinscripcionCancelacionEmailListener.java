package com.taemoi.project.services;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.taemoi.project.events.PreinscripcionCanceladaEvent;

/** El aviso sale solo tras confirmar la cancelación: un fallo del SMTP nunca la deshace. */
@Component
public class PreinscripcionCancelacionEmailListener {
	private final PreinscripcionCancelacionEmailService emailService;

	public PreinscripcionCancelacionEmailListener(PreinscripcionCancelacionEmailService emailService) {
		this.emailService = emailService;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void alCancelar(PreinscripcionCanceladaEvent event) {
		emailService.enviar(event.referencia());
	}
}
