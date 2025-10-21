package com.taemoi.project.exceptions.alumno;

import java.io.Serial;

public class FechaNacimientoInvalidaException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

	public FechaNacimientoInvalidaException(String mensaje) {
		super(mensaje);
	}
}