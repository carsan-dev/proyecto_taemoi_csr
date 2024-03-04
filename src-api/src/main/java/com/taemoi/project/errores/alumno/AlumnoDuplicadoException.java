package com.taemoi.project.errores.alumno;

public class AlumnoDuplicadoException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public AlumnoDuplicadoException(String mensaje) {
		super(mensaje);
	}
}