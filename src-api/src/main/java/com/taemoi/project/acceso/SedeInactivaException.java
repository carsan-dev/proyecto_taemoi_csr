package com.taemoi.project.acceso;

/**
 * D6-17: en una sede inactiva no se crean grupos, turnos ni cargos. Es un {@link IllegalArgumentException} (400 en
 * {@code GlobalExceptionHandler} y en los controladores que ya capturan ese tipo); los que capturan cualquier
 * excepción como 500 la capturan antes por su tipo (entrega 6-3).
 */
public class SedeInactivaException extends IllegalArgumentException {

	private static final long serialVersionUID = 1L;

	public SedeInactivaException() {
		super("La sede está inactiva.");
	}
}
