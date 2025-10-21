package com.taemoi.project.errores.alumno;

import java.io.Serial;

public class ListaAlumnosVaciaException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

	public ListaAlumnosVaciaException(String mensaje) {
		super(mensaje);
	}
}