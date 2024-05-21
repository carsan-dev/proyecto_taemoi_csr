package com.taemoi.project.errores.alumno;

public class AlumnoNoEncontradoEnGrupoException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public AlumnoNoEncontradoEnGrupoException(String mensaje) {
		super(mensaje);
	}
}
