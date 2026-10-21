package com.taemoi.project.exceptions;

/**
 * El fichero subido no pasa la validación: tipo o extensión fuera de la lista blanca, HEIC/HEIF (decisión 227),
 * contenido que no es una imagen legible o fichero vacío. Es un {@link IllegalArgumentException}, así que sigue
 * respondiendo 400 con {@code {"mensaje": …}}; el subtipo permite registrar el aviso de «archivo rechazado» solo en
 * estos casos y no en cualquier otro argumento inválido.
 */
public class ArchivoNoValidoException extends IllegalArgumentException {

	private static final long serialVersionUID = 1L;

	public ArchivoNoValidoException(String mensaje) {
		super(mensaje);
	}
}
