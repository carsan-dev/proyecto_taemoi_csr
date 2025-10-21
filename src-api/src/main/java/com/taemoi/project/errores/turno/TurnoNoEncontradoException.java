package com.taemoi.project.errores.turno;

import java.io.Serial;

public class TurnoNoEncontradoException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

	public TurnoNoEncontradoException(String mensaje) {
		super(mensaje);
	}
}