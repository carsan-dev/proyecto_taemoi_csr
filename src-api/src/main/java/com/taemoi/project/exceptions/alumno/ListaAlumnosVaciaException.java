package com.taemoi.project.exceptions.alumno;

import java.io.Serial;

public class ListaAlumnosVaciaException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

	public ListaAlumnosVaciaException(String mensaje) {
		super(mensaje);
	}
}