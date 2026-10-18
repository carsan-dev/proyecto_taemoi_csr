package com.taemoi.project.services;

import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.taemoi.project.entities.Preinscripcion;
import com.taemoi.project.events.PreinscripcionRecibidaEvent;
import com.taemoi.project.repositories.PreinscripcionDocumentosRepository;
import com.taemoi.project.repositories.PreinscripcionRepository;

/**
 * R2-P7d: correo inicial a la familia (con el PDF firmado adjunto) y aviso interno del alta pública. Mismo mecanismo que
 * los avisos de cancelación, cambio de turnos y finalización: transacción propia tras el commit del alta, envío síncrono
 * y seguimiento en la fila, con los campos de siempre de este correo ({@code emailEnviado}, intentos y último error,
 * los mismos valores que dejaba el envío dentro del alta). El PDF se lee aparte ({@link Preinscripcion} no lo carga).
 */
@Service
public class PreinscripcionAltaEmailService {
	private static final Logger log = LoggerFactory.getLogger(PreinscripcionAltaEmailService.class);

	private final PreinscripcionRepository preinscripciones;
	private final PreinscripcionDocumentosRepository documentos;
	private final EmailService emailService;

	public PreinscripcionAltaEmailService(PreinscripcionRepository preinscripciones,
			PreinscripcionDocumentosRepository documentos, EmailService emailService) {
		this.preinscripciones = preinscripciones;
		this.documentos = documentos;
		this.emailService = emailService;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void enviar(PreinscripcionRecibidaEvent event) {
		Preinscripcion preinscripcion = preinscripciones.findByReferenciaForUpdate(event.referencia())
				.orElseThrow(() -> new NoSuchElementException("Preinscripción no encontrada."));
		/* Antes de tocar la solicitud: leerlo después forzaría un UPDATE adicional por auto-flush (como el reenvío). */
		byte[] pdf = documentos.findPdfFirmadoById(preinscripcion.getId());
		preinscripcion.setEmailIntentos(preinscripcion.getEmailIntentos() + 1);
		try {
			emailService.sendEmailConAdjunto(preinscripcion.getEmail(), event.asunto(), event.html(),
					"preinscripcion-" + preinscripcion.getReferencia() + ".pdf", pdf);
			preinscripcion.setEmailEnviado(true);
			preinscripcion.setEmailUltimoError(null);
		} catch (Exception ex) {
			preinscripcion.setEmailEnviado(false);
			preinscripcion.setEmailUltimoError(clean(ex.getMessage()));
		}
		preinscripciones.save(preinscripcion);
	}

	/** Aviso interno, sin seguimiento en la fila (como siempre): un fallo solo queda en el log. */
	public void avisarAdmin(PreinscripcionRecibidaEvent event) {
		if (event.adminEmail() == null) return;
		try {
			emailService.sendEmail(event.adminEmail(), event.asuntoAdmin(), event.htmlAdmin());
		} catch (Exception e) {
			log.warn("No se pudo enviar aviso interno de preinscripción {}: {}", event.referencia(), e.getMessage());
		}
	}

	/** El mismo saneado que aplicaba el alta al último error. */
	private static String clean(String s) {
		return s == null ? null : s.replaceAll("[<>]", "").trim();
	}
}
