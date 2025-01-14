package com.taemoi.project.errores.producto;

public class ProductoNoEncontradoException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public ProductoNoEncontradoException(String mensaje) {
		super(mensaje);
	}
}
