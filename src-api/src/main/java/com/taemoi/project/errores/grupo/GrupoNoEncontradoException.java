package com.taemoi.project.errores.grupo;

import java.io.Serial;

public class GrupoNoEncontradoException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

	public GrupoNoEncontradoException(String mensaje) {
		super(mensaje);
	}
}
