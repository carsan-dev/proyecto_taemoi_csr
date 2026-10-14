package com.taemoi.project.exceptions;

/** Un documento no se puede borrar porque es la prueba de un consentimiento (409). */
public class DocumentoReferenciadoException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public DocumentoReferenciadoException(String mensaje) {
		super(mensaje);
	}
}
