package com.taemoi.project.errores.turno;

public class TurnoNoEncontradoException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public TurnoNoEncontradoException(String mensaje) {
		super(mensaje);
	}
}