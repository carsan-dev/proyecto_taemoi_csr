package com.taemoi.project.errores.alumno;

public class FechaNacimientoInvalidaException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public FechaNacimientoInvalidaException(String mensaje) {
		super(mensaje);
	}
}