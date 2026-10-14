package com.taemoi.project.exceptions.usuario;

import java.io.Serial;

/**
 * Cambio sobre la cuenta GLOBAL de un usuario que también pertenece a otra escuela (409).
 */
public class UsuarioCompartidoException extends RuntimeException {

	@Serial
	private static final long serialVersionUID = 1L;

	public UsuarioCompartidoException(String message) {
		super(message);
	}
}
