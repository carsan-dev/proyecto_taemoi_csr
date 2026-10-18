package com.taemoi.project.exceptions.pago;

/**
 * Pieza 6b-1: el cobro de un cargo ya va en una entrega de caja, así que no se puede revertir el pago ni borrar el
 * cargo. Es un {@link IllegalStateException}: {@code GlobalExceptionHandler} responde 409 con {@code {"mensaje": ...}}.
 */
public class CobroEntregadoException extends IllegalStateException {

	private static final long serialVersionUID = 1L;

	public static final String MENSAJE = "El cobro de este cargo ya está incluido en una entrega de caja: "
			+ "no se puede revertir el pago ni borrar el cargo.";

	public CobroEntregadoException() {
		super(MENSAJE);
	}
}
