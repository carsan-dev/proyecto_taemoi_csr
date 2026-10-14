package com.taemoi.project.dtos;

import java.util.Date;


public class ConvocatoriaDTO {

	private Long id;

	private Date fechaConvocatoria;

	private String deporte;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Date getFechaConvocatoria() {
		return fechaConvocatoria;
	}

	public void setFechaConvocatoria(Date fechaConvocatoria) {
		this.fechaConvocatoria = fechaConvocatoria;
	}

	public String getDeporte() {
		return deporte;
	}

	public void setDeporte(String deporte) {
		this.deporte = deporte;
	}

}
