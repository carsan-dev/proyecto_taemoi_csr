package com.taemoi.project.services;

import java.util.Date;

/**
 * Contenido de un JWT cuya firma y caducidad ya se han verificado.
 *
 * @param email          sujeto del token.
 * @param organizacionId claim {@code org}; {@code null} en tokens emitidos antes de la multi-escuela.
 * @param emitidoEn      claim {@code iat}.
 * @param expiraEn       claim {@code exp}.
 */
public record TokenJwt(String email, Long organizacionId, Date emitidoEn, Date expiraEn) {

	public boolean esHeredado() {
		return organizacionId == null;
	}
}
