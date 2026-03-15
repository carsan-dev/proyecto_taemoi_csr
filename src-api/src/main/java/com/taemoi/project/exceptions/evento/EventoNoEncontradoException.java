package com.taemoi.project.exceptions.evento;

import java.io.Serial;

public class EventoNoEncontradoException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

	public EventoNoEncontradoException(String mensaje) {
		super(mensaje);
	}
}