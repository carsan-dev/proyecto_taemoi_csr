package com.taemoi.project.dtos.request;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public class CertificadosCobrosRequest {

	@NotEmpty(message = "Debes seleccionar al menos un alumno.")
	private List<Long> alumnoIds;

	@NotNull(message = "Debes indicar un año.")
	@Min(value = 1900, message = "El año no es válido.")
	@Max(value = 2200, message = "El año no es válido.")
	private Integer ano;

	public List<Long> getAlumnoIds() {
		return alumnoIds;
	}

	public void setAlumnoIds(List<Long> alumnoIds) {
		this.alumnoIds = alumnoIds;
	}

	public Integer getAno() {
		return ano;
	}

	public void setAno(Integer ano) {
		this.ano = ano;
	}
}
