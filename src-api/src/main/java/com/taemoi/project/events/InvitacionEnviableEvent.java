package com.taemoi.project.events;

import com.taemoi.project.entities.RolMembresia;

/**
 * Pieza E8-4 (D8-13): invitación creada o reenviada dentro de una transacción; el correo sale tras el commit
 * ({@code InvitacionCorreoListener}): una transacción deshecha no envía nada y un fallo del correo nunca la deshace.
 *
 * <p>{@code token} es el token EN CLARO (en la base solo está su hash): solo vive en memoria hasta el correo. Nunca se
 * imprime: {@link #toString()} no lo incluye (ni el email ni el nombre).
 *
 * @param usuarioId      cuenta invitada
 * @param organizacionId escuela de la invitación (marca del correo)
 * @param email          destinatario
 * @param nombre         nombre de la cuenta, para el saludo
 * @param rol            rol con el que se la invita (texto del correo)
 * @param token          token en claro del enlace
 */
public record InvitacionEnviableEvent(Long usuarioId, Long organizacionId, String email, String nombre,
		RolMembresia rol, String token) {

	@Override
	public String toString() {
		return "InvitacionEnviableEvent[usuarioId=" + usuarioId + ", organizacionId=" + organizacionId + ", rol=" + rol
				+ "]";
	}
}
