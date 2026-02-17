package com.taemoi.project.dtos.response;

public class RecordatorioRachaEmailResponse {

	private boolean habilitado;

	public RecordatorioRachaEmailResponse() {
	}

	public RecordatorioRachaEmailResponse(boolean habilitado) {
		this.habilitado = habilitado;
	}

	public boolean isHabilitado() {
		return habilitado;
	}

	public void setHabilitado(boolean habilitado) {
		this.habilitado = habilitado;
	}
}
