package com.taemoi.project.exceptions.evento;

/**
 * Web por plantilla (W2): la hora, el lugar o la sede de un evento no son válidos. {@code EventoController} responde
 * 400 con {@code {"mensaje"}}.
 */
public class DatosEventoNoValidosException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public DatosEventoNoValidosException(String mensaje) {
		super(mensaje);
	}
}
