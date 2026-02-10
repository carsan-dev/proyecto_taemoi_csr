package com.taemoi.project.dtos.response;

public class RetoDiarioEstadoDTO {
	private Integer racha;
	private Boolean completadoHoy;
	private String fechaCompletado;

	public RetoDiarioEstadoDTO() {
		// Constructor vacio
	}

	public RetoDiarioEstadoDTO(Integer racha, Boolean completadoHoy, String fechaCompletado) {
		this.racha = racha;
		this.completadoHoy = completadoHoy;
		this.fechaCompletado = fechaCompletado;
	}

	public Integer getRacha() {
		return racha;
	}

	public void setRacha(Integer racha) {
		this.racha = racha;
	}

	public Boolean getCompletadoHoy() {
		return completadoHoy;
	}

	public void setCompletadoHoy(Boolean completadoHoy) {
		this.completadoHoy = completadoHoy;
	}

	public String getFechaCompletado() {
		return fechaCompletado;
	}

	public void setFechaCompletado(String fechaCompletado) {
		this.fechaCompletado = fechaCompletado;
	}
}
