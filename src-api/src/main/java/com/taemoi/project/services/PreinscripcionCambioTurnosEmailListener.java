package com.taemoi.project.services;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.taemoi.project.events.PreinscripcionTurnosModificadosEvent;

@Component
public class PreinscripcionCambioTurnosEmailListener {
	private final PreinscripcionCambioTurnosEmailService emailService;

	public PreinscripcionCambioTurnosEmailListener(PreinscripcionCambioTurnosEmailService emailService) {
		this.emailService = emailService;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void alModificarTurnos(PreinscripcionTurnosModificadosEvent event) {
		emailService.enviar(event.referencia());
	}
}
