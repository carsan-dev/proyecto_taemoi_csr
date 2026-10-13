package com.taemoi.project.cifrado;

/**
 * Un dato cifrado no se puede descifrar (clave, fila o columna incorrecta, o dato alterado). El
 * mensaje nunca incluye contenido; la API responde un error genérico (GlobalExceptionHandler).
 */
public class DatoCifradoIlegibleException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public DatoCifradoIlegibleException(String mensaje) {
		super(mensaje);
	}
}
