package com.taemoi.project.exceptions.evento;

import java.io.Serial;

/** La foto no existe, es de otra escuela o no pertenece a ese evento: para quien pregunta es lo mismo. */
public class FotoEventoNoEncontradaException extends RuntimeException {
	@Serial
	private static final long serialVersionUID = 1L;

	public FotoEventoNoEncontradaException(String mensaje) {
		super(mensaje);
	}
}
