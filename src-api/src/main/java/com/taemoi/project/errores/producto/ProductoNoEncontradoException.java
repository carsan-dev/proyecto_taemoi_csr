package com.taemoi.project.errores.producto;

import java.io.Serial;

public class ProductoNoEncontradoException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

	public ProductoNoEncontradoException(String mensaje) {
		super(mensaje);
	}
}
