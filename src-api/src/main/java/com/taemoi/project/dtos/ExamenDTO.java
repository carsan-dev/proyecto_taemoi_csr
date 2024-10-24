package com.taemoi.project.dtos;

import com.taemoi.project.entidades.Examen;

public class ExamenDTO {
	private Long id;
	private String nombreAlumno;
	private String gradoActual;
	private String gradoProximo;
	private String estadoPago;
	private Double cuantiaPago;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getNombreAlumno() {
		return nombreAlumno;
	}

	public void setNombreAlumno(String nombreAlumno) {
		this.nombreAlumno = nombreAlumno;
	}

	public String getGradoActual() {
		return gradoActual;
	}

	public void setGradoActual(String gradoActual) {
		this.gradoActual = gradoActual;
	}

	public String getGradoProximo() {
		return gradoProximo;
	}

	public void setGradoProximo(String gradoProximo) {
		this.gradoProximo = gradoProximo;
	}

	public String getEstadoPago() {
		return estadoPago;
	}

	public void setEstadoPago(String estadoPago) {
		this.estadoPago = estadoPago;
	}

	public Double getCuantiaPago() {
		return cuantiaPago;
	}

	public void setCuantiaPago(Double cuantiaPago) {
		this.cuantiaPago = cuantiaPago;
	}
    public static ExamenDTO deExamen(Examen examen) {
        ExamenDTO dto = new ExamenDTO();
        dto.setId(examen.getId());
        dto.setNombreAlumno(examen.getAlumno().getNombre() + " " + examen.getAlumno().getApellidos());
        dto.setGradoActual(examen.getGradoActual().getTipoGrado().name());
        dto.setGradoProximo(examen.getGradoProximo().getTipoGrado().name());
        dto.setEstadoPago(examen.getPago().getEstado().name());
        dto.setCuantiaPago(examen.getPago().getCuantia());
        return dto;
    }
}
