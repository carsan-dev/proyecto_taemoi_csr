package com.taemoi.project.errores.alumno;

public class ListaAlumnosVaciaException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public ListaAlumnosVaciaException(String mensaje) {
		super(mensaje);
	}
}