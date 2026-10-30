package com.taemoi.project.exceptions.evento;

import java.io.Serial;

/** El evento ya tiene el máximo de fotos. */
public class TopeFotosEventoException extends RuntimeException {
	@Serial
	private static final long serialVersionUID = 1L;

	public TopeFotosEventoException(String mensaje) {
		super(mensaje);
	}
}
