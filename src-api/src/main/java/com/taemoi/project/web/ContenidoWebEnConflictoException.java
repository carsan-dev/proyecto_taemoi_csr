package com.taemoi.project.web;

/**
 * 409 del guardado del contenido de la web (d522): otra persona guardó después de que se leyera. No se escribe nada y
 * la respuesta no dice quién ni cuándo (d530).
 */
public class ContenidoWebEnConflictoException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public ContenidoWebEnConflictoException() {
		super(ContenidoWebService.CONFLICTO);
	}
}
