package com.taemoi.project.exceptions.preinscripcion;

public class PreinscripcionDuplicadaException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public PreinscripcionDuplicadaException(String mensaje) {
		super(mensaje);
	}
}
