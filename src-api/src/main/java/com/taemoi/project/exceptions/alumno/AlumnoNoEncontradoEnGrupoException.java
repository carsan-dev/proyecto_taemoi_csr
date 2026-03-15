package com.taemoi.project.exceptions.alumno;

import java.io.Serial;

public class AlumnoNoEncontradoEnGrupoException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

	public AlumnoNoEncontradoEnGrupoException(String mensaje) {
		super(mensaje);
	}
}
