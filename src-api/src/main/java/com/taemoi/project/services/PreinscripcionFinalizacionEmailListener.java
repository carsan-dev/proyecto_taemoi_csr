package com.taemoi.project.services;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.taemoi.project.events.PreinscripcionFinalizadaEvent;

@Component
public class PreinscripcionFinalizacionEmailListener {
	private final PreinscripcionFinalizacionEmailService emailService;

	public PreinscripcionFinalizacionEmailListener(PreinscripcionFinalizacionEmailService emailService) {
		this.emailService = emailService;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void alFinalizar(PreinscripcionFinalizadaEvent event) {
		emailService.enviar(event.referencia());
	}
}
