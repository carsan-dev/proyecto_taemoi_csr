package com.taemoi.project.exceptions.usuario;

import java.io.Serial;

public class UsuarioNoEncontradoException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

	public UsuarioNoEncontradoException(String message) {
		super(message);
	}
}
