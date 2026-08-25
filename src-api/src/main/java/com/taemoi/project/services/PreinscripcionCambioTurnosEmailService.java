package com.taemoi.project.services;

import java.time.Instant;
import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.taemoi.project.entities.EstadoEmailCambioTurnos;
import com.taemoi.project.entities.Preinscripcion;
import com.taemoi.project.repositories.PreinscripcionRepository;

@Service
public class PreinscripcionCambioTurnosEmailService {
	private static final Logger log = LoggerFactory.getLogger(PreinscripcionCambioTurnosEmailService.class);

	private final PreinscripcionRepository preinscripciones;
	private final EmailService emailService;

	public PreinscripcionCambioTurnosEmailService(PreinscripcionRepository preinscripciones,
			EmailService emailService) {
		this.preinscripciones = preinscripciones;
		this.emailService = emailService;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void enviar(String referencia) {
		Preinscripcion preinscripcion = preinscripciones.findByReferenciaForUpdate(referencia)
				.orElseThrow(() -> new NoSuchElementException("Preinscripción no encontrada."));
		preinscripcion.setEmailCambioTurnosIntentos(preinscripcion.getEmailCambioTurnosIntentos() + 1);
		preinscripcion.setEstadoEmailCambioTurnos(EstadoEmailCambioTurnos.PENDIENTE);
		preinscripcion.setEmailCambioTurnosEnviadoEn(null);
		preinscripcion.setEmailCambioTurnosUltimoError(null);

		try {
			validarSnapshots(preinscripcion);
			emailService.sendEmailSync(preinscripcion.getEmail(),
					"Horarios actualizados · " + preinscripcion.getReferencia(), html(preinscripcion));
			preinscripcion.setEstadoEmailCambioTurnos(EstadoEmailCambioTurnos.ENVIADO);
			preinscripcion.setEmailCambioTurnosEnviadoEn(Instant.now());
		} catch (Exception ex) {
			preinscripcion.setEstadoEmailCambioTurnos(EstadoEmailCambioTurnos.ERROR);
			preinscripcion.setEmailCambioTurnosUltimoError(errorResumido(ex));
			log.warn("No se pudo enviar el aviso de cambio de turnos de {}: {}", referencia, ex.getMessage());
		}

		preinscripciones.save(preinscripcion);
	}

	String html(Preinscripcion p) {
		return "<div style='font-family:Arial,sans-serif;color:#172126;line-height:1.55;max-width:640px'>"
				+ "<h2>Hemos actualizado tus horarios</h2><p>Hola, " + esc(p.getNombre())
				+ ". Nuestro equipo ha revisado tu solicitud.</p>"
				+ "<p><strong>Asignación anterior:</strong> " + esc(p.getEmailCambioTurnosAnteriorSnapshot()) + "</p>"
				+ "<p style='padding:12px;border-left:4px solid #237a57;background:#edf8f3'>"
				+ "<strong>Asignación vigente:</strong> " + esc(p.getEmailCambioTurnosNuevoSnapshot()) + "</p>"
				+ bloqueObservaciones(p)
				+ "<p>Conserva la referencia <strong>" + esc(p.getReferencia())
				+ "</strong> para cualquier consulta.</p>"
				+ "<p>Un saludo,<br><strong>Moi's Kim Do</strong></p></div>";
	}

	private void validarSnapshots(Preinscripcion p) {
		if (blank(p.getEmailCambioTurnosAnteriorSnapshot()) || blank(p.getEmailCambioTurnosNuevoSnapshot())) {
			throw new IllegalStateException("La solicitud no tiene un cambio de turnos pendiente de comunicar.");
		}
	}

	private String bloqueObservaciones(Preinscripcion p) {
		return blank(p.getObservaciones()) ? ""
				: "<h3>Observación de la solicitud</h3><p style='padding:12px;background:#f4f6f6;"
						+ "border-left:4px solid #547078;white-space:pre-wrap'>" + esc(p.getObservaciones()) + "</p>";
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
