package com.taemoi.project.events;

/**
 * R2-P7d: solicitud de la lista de espera promocionada a PENDIENTE. Los avisos (a la familia y al buzón interno) se
 * preparan dentro de la transacción de la promoción, con los textos de siempre, y salen tras el commit
 * ({@code PreinscripcionPromocionEmailListener}), en el orden de la cola. {@code adminEmail} es null si no hay buzón
 * interno configurado.
 */
public record PreinscripcionPromocionadaEvent(String referencia, String email, String asunto, String html,
		String adminEmail, String asuntoAdmin, String htmlAdmin, String remitente) {

	/** Sin remitente propio (Moiskimdo): el nombre de siempre. {@code remitente}: pieza E7-7a. */
	public PreinscripcionPromocionadaEvent(String referencia, String email, String asunto, String html,
			String adminEmail, String asuntoAdmin, String htmlAdmin) {
		this(referencia, email, asunto, html, adminEmail, asuntoAdmin, htmlAdmin, null);
	}
}
