package com.taemoi.project.errores.alumno;

public class DatosAlumnoInvalidosException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public DatosAlumnoInvalidosException(String mensaje) {
		super(mensaje);
	}
}