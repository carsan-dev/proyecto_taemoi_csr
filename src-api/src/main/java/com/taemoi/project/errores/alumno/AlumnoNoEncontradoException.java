package com.taemoi.project.errores.alumno;

public class AlumnoNoEncontradoException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public AlumnoNoEncontradoException(String mensaje) {
		super(mensaje);
	}
}