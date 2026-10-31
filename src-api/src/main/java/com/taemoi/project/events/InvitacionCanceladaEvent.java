package com.taemoi.project.events;

/**
 * Decisión 389: invitación cancelada dentro de una transacción; el aviso sale tras el commit
 * ({@code InvitacionCorreoListener}): una cancelación deshecha no avisa a nadie y un fallo del correo nunca la deshace.
 *
 * <p>Lleva el email y el nombre copiados porque, al confirmar, la cuenta puede ya no existir. Nunca se imprimen:
 * {@link #toString()} no los incluye.
 *
 * @param usuarioId      cuenta cancelada (puede estar ya borrada)
 * @param organizacionId escuela que cancela (marca del correo)
 * @param email          destinatario
 * @param nombre         nombre de la cuenta, para el saludo
 */
public record InvitacionCanceladaEvent(Long usuarioId, Long organizacionId, String email, String nombre) {

	@Override
	public String toString() {
		return "InvitacionCanceladaEvent[usuarioId=" + usuarioId + ", organizacionId=" + organizacionId + "]";
	}
}
