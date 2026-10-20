package com.taemoi.project.events;

/**
 * R2-P7d: alta pública guardada. El asunto y los cuerpos se preparan dentro de la transacción del alta (los mismos de
 * siempre, con la posición en la lista de espera de ese momento); los correos salen tras el commit
 * ({@code PreinscripcionAltaEmailListener}). {@code adminEmail} es null si no hay buzón interno configurado.
 */
public record PreinscripcionRecibidaEvent(String referencia, String asunto, String html, String adminEmail,
		String asuntoAdmin, String htmlAdmin, String remitente) {

	/** Sin remitente propio (Moiskimdo): el nombre de siempre. {@code remitente}: pieza E7-7a. */
	public PreinscripcionRecibidaEvent(String referencia, String asunto, String html, String adminEmail,
			String asuntoAdmin, String htmlAdmin) {
		this(referencia, asunto, html, adminEmail, asuntoAdmin, htmlAdmin, null);
	}
}
