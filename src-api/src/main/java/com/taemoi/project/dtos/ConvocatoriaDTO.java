package com.taemoi.project.dtos;

import java.util.Date;

import com.taemoi.project.entidades.Deporte;

public class ConvocatoriaDTO {
	
	private Long id;
	
	private Date fechaConvocatoria;
	
	private Deporte deporte;
	
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
	public Deporte getDeporte() {
		return deporte;
	}
	public void setDeporte(Deporte deporte) {
		this.deporte = deporte;
	}

}
