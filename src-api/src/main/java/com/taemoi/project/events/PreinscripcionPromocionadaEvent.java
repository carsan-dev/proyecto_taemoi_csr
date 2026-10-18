package com.taemoi.project.events;

/**
 * R2-P7d: solicitud de la lista de espera promocionada a PENDIENTE. Los avisos (a la familia y al buzón interno) se
 * preparan dentro de la transacción de la promoción, con los textos de siempre, y salen tras el commit
 * ({@code PreinscripcionPromocionEmailListener}), en el orden de la cola. {@code adminEmail} es null si no hay buzón
 * interno configurado.
 */
public record PreinscripcionPromocionadaEvent(String referencia, String email, String asunto, String html,
		String adminEmail, String asuntoAdmin, String htmlAdmin) {
}
