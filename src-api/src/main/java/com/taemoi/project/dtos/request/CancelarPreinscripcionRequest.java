package com.taemoi.project.dtos.request;

import jakarta.validation.constraints.Size;

/** Cuerpo opcional de la cancelación: sin motivo (o en blanco) el servicio aplica el motivo por defecto. */
public record CancelarPreinscripcionRequest(
		@Size(max = CancelarPreinscripcionRequest.MOTIVO_MAX, message = "El motivo no puede superar 500 caracteres.")
		String motivo
) {
	public static final int MOTIVO_MAX = 500;
}
