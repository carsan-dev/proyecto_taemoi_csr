package com.taemoi.project.errores.evento;

public class EventoNoEncontradoException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public EventoNoEncontradoException(String mensaje) {
		super(mensaje);
	}
}