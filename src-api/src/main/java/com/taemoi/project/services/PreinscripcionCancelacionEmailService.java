package com.taemoi.project.services;

import java.time.Instant;
import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.taemoi.project.entities.EstadoEmailCancelacion;
import com.taemoi.project.entities.Preinscripcion;
import com.taemoi.project.repositories.PreinscripcionRepository;

/**
 * Aviso a la familia de que su preinscripción se ha cancelado. Mismo mecanismo que el aviso de cambio de turnos:
 * transacción propia tras el commit de la cancelación, envío síncrono y seguimiento en la fila (estado, intentos,
 * fecha y último error). El motivo es texto libre del personal: se escapa como el resto de datos.
 */
@Service
public class PreinscripcionCancelacionEmailService {
	private static final Logger log = LoggerFactory.getLogger(PreinscripcionCancelacionEmailService.class);

	private final PreinscripcionRepository preinscripciones;
	private final EmailService emailService;

	public PreinscripcionCancelacionEmailService(PreinscripcionRepository preinscripciones,
			EmailService emailService) {
		this.preinscripciones = preinscripciones;
		this.emailService = emailService;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void enviar(String referencia) {
		Preinscripcion preinscripcion = preinscripciones.findByReferenciaForUpdate(referencia)
				.orElseThrow(() -> new NoSuchElementException("Preinscripción no encontrada."));
		preinscripcion.setEmailCancelacionIntentos(preinscripcion.getEmailCancelacionIntentos() + 1);
		preinscripcion.setEstadoEmailCancelacion(EstadoEmailCancelacion.PENDIENTE);
		preinscripcion.setEmailCancelacionEnviadoEn(null);
		preinscripcion.setEmailCancelacionUltimoError(null);

		try {
			if (blank(preinscripcion.getEmail())) {
				throw new IllegalStateException("La solicitud no tiene correo electrónico.");
			}
			emailService.sendEmailSync(preinscripcion.getEmail().trim(),
					"Tu preinscripción " + preinscripcion.getReferencia() + " ha sido cancelada", html(preinscripcion));
			preinscripcion.setEstadoEmailCancelacion(EstadoEmailCancelacion.ENVIADO);
			preinscripcion.setEmailCancelacionEnviadoEn(Instant.now());
		} catch (Exception ex) {
			preinscripcion.setEstadoEmailCancelacion(EstadoEmailCancelacion.ERROR);
			preinscripcion.setEmailCancelacionUltimoError(errorResumido(ex));
			log.warn("No se pudo enviar el aviso de cancelación de {}: {}", referencia, ex.getMessage());
		}

		preinscripciones.save(preinscripcion);
	}

	String html(Preinscripcion p) {
		String motivo = blank(p.getMotivoCancelacion()) ? PreinscripcionService.MOTIVO_CANCELACION_POR_DEFECTO
				: p.getMotivoCancelacion();
		return "<div style='font-family:Arial,sans-serif;color:#172126;line-height:1.55;max-width:640px'>"
				+ "<h2>Preinscripción cancelada</h2>"
				+ "<p>Hola,</p>"
				+ "<p>Te informamos de que la preinscripción de <strong>" + esc(nombreCompleto(p)) + "</strong> en <strong>"
				+ esc(nombreDeporte(p)) + "</strong> (referencia <strong>" + esc(p.getReferencia())
				+ "</strong>) ha sido cancelada.</p>"
				+ "<p style='padding:12px;background:#f4f6f6;border-left:4px solid #547078;white-space:pre-wrap'>"
				+ esc(motivo) + "</p>"
				+ "<p>Si tienes cualquier duda, puedes responder a este correo o ponerte en contacto con la escuela.</p>"
				+ "<p>Un saludo,<br><strong>Moi's Kim Do</strong></p></div>";
	}

	private String nombreCompleto(Preinscripcion p) {
		return ((p.getNombre() == null ? "" : p.getNombre()) + " " + (p.getApellidos() == null ? "" : p.getApellidos()))
				.trim();
	}

	/** Nombre público de la disciplina (en Taemoi: «Taekwondo», «Defensa Personal Femenina» desde V30…). */
	private String nombreDeporte(Preinscripcion p) {
		return p.getDisciplina() == null ? "" : p.getDisciplina().getNombre();
	}

	private String errorResumido(Exception ex) {
		String mensaje = ex.getMessage();
		if ((mensaje == null || mensaje.isBlank()) && ex.getCause() != null) mensaje = ex.getCause().getMessage();
		if (mensaje == null || mensaje.isBlank()) mensaje = ex.getClass().getSimpleName();
		mensaje = mensaje.replaceAll("[\\r\\n]+", " ").trim();
		return mensaje.length() <= 500 ? mensaje : mensaje.substring(0, 500);
	}

	private boolean blank(String value) {
		return value == null || value.isBlank();
	}

	private String esc(String value) {
		return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;")
				.replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
	}
}
