package com.taemoi.project.exceptions.alumno;

import java.io.Serial;

public class AlumnoNoEncontradoException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

	public AlumnoNoEncontradoException(String mensaje) {
		super(mensaje);
	}
}