package com.taemoi.project.services;

import org.springframework.lang.NonNull;

public interface EmailService {

	void sendEmail(@NonNull String to, @NonNull String subject, @NonNull String htmlContent);

	void sendEmailSync(@NonNull String to, @NonNull String subject, @NonNull String htmlContent);

	void sendEmailConAdjunto(@NonNull String to, @NonNull String subject, @NonNull String htmlContent,
			@NonNull String nombreAdjunto, @NonNull byte[] adjunto);

	/**
	 * Pieza E7-7a (decisión 211): como {@link #sendEmail(String, String, String)} pero con el nombre visible del
	 * remitente de la escuela; vacío o {@code null}, el de siempre ({@code app.mail.from-name}). La dirección no cambia.
	 */
	void sendEmail(@NonNull String to, @NonNull String subject, @NonNull String htmlContent, String nombreRemitente);

	/** Como {@link #sendEmailSync(String, String, String)} con el nombre visible del remitente (E7-7a). */
	void sendEmailSync(@NonNull String to, @NonNull String subject, @NonNull String htmlContent, String nombreRemitente);

	/** Como {@link #sendEmailConAdjunto(String, String, String, String, byte[])} con el nombre del remitente (E7-7a). */
	void sendEmailConAdjunto(@NonNull String to, @NonNull String subject, @NonNull String htmlContent,
			@NonNull String nombreAdjunto, @NonNull byte[] adjunto, String nombreRemitente);

	// Pieza E7-7a: sin remitente propio (Moiskimdo, {@code MarcaEscuela#remitente()} nulo) se llama al método de
	// siempre; con él, a la sobrecarga. Así los correos de Moiskimdo salen por el mismo camino que antes.

	static void enviar(EmailService servicio, String to, String subject, String htmlContent, String nombreRemitente) {
		if (nombreRemitente == null) {
			servicio.sendEmail(to, subject, htmlContent);
		} else {
			servicio.sendEmail(to, subject, htmlContent, nombreRemitente);
		}
	}

	static void enviarSync(EmailService servicio, String to, String subject, String htmlContent,
			String nombreRemitente) {
		if (nombreRemitente == null) {
			servicio.sendEmailSync(to, subject, htmlContent);
		} else {
			servicio.sendEmailSync(to, subject, htmlContent, nombreRemitente);
		}
	}

	static void enviarConAdjunto(EmailService servicio, String to, String subject, String htmlContent,
			String nombreAdjunto, byte[] adjunto, String nombreRemitente) {
		if (nombreRemitente == null) {
			servicio.sendEmailConAdjunto(to, subject, htmlContent, nombreAdjunto, adjunto);
		} else {
			servicio.sendEmailConAdjunto(to, subject, htmlContent, nombreAdjunto, adjunto, nombreRemitente);
		}
	}
}
