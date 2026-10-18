package com.taemoi.project.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.taemoi.project.events.PreinscripcionPromocionadaEvent;

/**
 * R2-P7d: avisos de plaza de una solicitud promocionada desde la lista de espera, tras el commit de la promoción. Los
 * mismos de siempre (familia y después buzón interno, en segundo plano con {@link EmailService#sendEmail}), sin
 * seguimiento en la fila: un fallo solo queda en el log.
 */
@Service
public class PreinscripcionPromocionEmailService {
	private static final Logger log = LoggerFactory.getLogger(PreinscripcionPromocionEmailService.class);

	private final EmailService emailService;

	public PreinscripcionPromocionEmailService(EmailService emailService) {
		this.emailService = emailService;
	}

	public void enviar(PreinscripcionPromocionadaEvent event) {
		try {
			emailService.sendEmail(event.email(), event.asunto(), event.html());
		} catch (Exception e) {
			log.warn("No se pudo avisar de promoción {}: {}", event.referencia(), e.getMessage());
		}
		if (event.adminEmail() == null) return;
		try {
			emailService.sendEmail(event.adminEmail(), event.asuntoAdmin(), event.htmlAdmin());
		} catch (Exception e) {
			log.warn("No se pudo avisar al admin de promoción {}: {}", event.referencia(), e.getMessage());
		}
	}
}
