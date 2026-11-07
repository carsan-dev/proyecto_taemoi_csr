package com.taemoi.project.exceptions.evento;

import java.io.Serial;

/** El documento no existe, es de otra escuela o no pertenece a ese evento: para quien pregunta es lo mismo. */
public class DocumentoEventoNoEncontradoException extends RuntimeException {
	@Serial
	private static final long serialVersionUID = 1L;

	public DocumentoEventoNoEncontradoException(String mensaje) {
		super(mensaje);
	}
}
