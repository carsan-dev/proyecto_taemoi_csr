package com.taemoi.project.exceptions;

import org.springframework.http.HttpStatus;

/** Error de una operación de material de examen: su estado HTTP y un mensaje en español apto para la pantalla. */
public class MaterialExamenException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final HttpStatus estado;

	public MaterialExamenException(HttpStatus estado, String mensaje) {
		super(mensaje);
		this.estado = estado;
	}

	public HttpStatus getEstado() {
		return estado;
	}
}
