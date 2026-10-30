package com.taemoi.project.exceptions.evento;

import java.io.Serial;

/** No hay hueco para procesar una foto de la galería: se responde 503 y el panel puede reintentar. */
public class ServidorOcupadoException extends RuntimeException {
	@Serial
	private static final long serialVersionUID = 1L;

	public ServidorOcupadoException(String mensaje) {
		super(mensaje);
	}
}
