package com.taemoi.project.exceptions;

/**
 * Decisión 297: el precio de un cargo o de un producto, o la cuantía de una inscripción, no es válido (negativo, no
 * numérico o con más de 2 decimales; {@code ImporteCargo#exigirPrecioValido}). Es un {@link IllegalArgumentException},
 * así que sigue respondiendo 400 con {@code {"mensaje": …}}; el subtipo permite que los controladores que responden
 * otros argumentos inválidos de otra forma (texto plano, 500) devuelvan este con su mensaje.
 */
public class PrecioNoValidoException extends IllegalArgumentException {

	private static final long serialVersionUID = 1L;

	public PrecioNoValidoException(String mensaje) {
		super(mensaje);
	}
}
