package com.taemoi.project.dtos.request;

import jakarta.validation.constraints.NotNull;

public class RecordatorioRachaEmailRequest {

	@NotNull(message = "El estado del recordatorio es obligatorio")
	private Boolean habilitado;

	public Boolean getHabilitado() {
		return habilitado;
	}

	public void setHabilitado(Boolean habilitado) {
		this.habilitado = habilitado;
	}
}
