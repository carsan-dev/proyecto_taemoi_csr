package com.taemoi.project.errores.grupo;

public class GrupoNoEncontradoException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public GrupoNoEncontradoException(String mensaje) {
		super(mensaje);
	}
}
