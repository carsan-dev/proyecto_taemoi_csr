package com.taemoi.project.errores.usuario;

import java.io.Serial;

public class ListaUsuariosVaciaException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

	public ListaUsuariosVaciaException(String message) {
		super(message);
	}
}