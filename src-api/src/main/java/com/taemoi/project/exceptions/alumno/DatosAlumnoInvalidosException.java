package com.taemoi.project.exceptions.alumno;

import java.io.Serial;

public class DatosAlumnoInvalidosException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

	public DatosAlumnoInvalidosException(String mensaje) {
		super(mensaje);
	}
}