package com.taemoi.project.errores.alumno;

import java.io.Serial;

public class AlumnoDuplicadoException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

	public AlumnoDuplicadoException(String mensaje) {
		super(mensaje);
	}
}