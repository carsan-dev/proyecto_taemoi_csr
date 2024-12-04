package com.taemoi.project.dtos.response;

import com.taemoi.project.entidades.TipoGrado;

public class AlumnoConvocatoriaDTO {
	private Long alumnoId;

	private String nombre;

	private String apellidos;

	private Double cuantiaExamen;

	private TipoGrado gradoActual;

	private TipoGrado gradoSiguiente;

	private Boolean pagado;

	public AlumnoConvocatoriaDTO(Long alumnoId, String nombre, String apellidos, Double cuantiaExamen,
			TipoGrado gradoActual, TipoGrado gradoSiguiente, Boolean pagado) {
		this.alumnoId = alumnoId;
		this.nombre = nombre;
		this.apellidos = apellidos;
		this.cuantiaExamen = cuantiaExamen;
		this.gradoActual = gradoActual;
		this.gradoSiguiente = gradoSiguiente;
		this.pagado = pagado;
	}

	public AlumnoConvocatoriaDTO() {
		// TODO Auto-generated constructor stub
	}

	public Long getAlumnoId() {
		return alumnoId;
	}

	public void setAlumnoId(Long alumnoId) {
		this.alumnoId = alumnoId;
	}

	public String getNombre() {
		return nombre;
	}

	public void setNombre(String nombre) {
		this.nombre = nombre;
	}

	public String getApellidos() {
		return apellidos;
	}

	public void setApellidos(String apellidos) {
		this.apellidos = apellidos;
	}

	public Double getCuantiaExamen() {
		return cuantiaExamen;
	}

	public void setCuantiaExamen(Double cuantiaExamen) {
		this.cuantiaExamen = cuantiaExamen;
	}

	public TipoGrado getGradoActual() {
		return gradoActual;
	}

	public void setGradoActual(TipoGrado gradoActual) {
		this.gradoActual = gradoActual;
	}

	public TipoGrado getGradoSiguiente() {
		return gradoSiguiente;
	}

	public void setGradoSiguiente(TipoGrado gradoSiguiente) {
		this.gradoSiguiente = gradoSiguiente;
	}

	public Boolean getPagado() {
		return pagado;
	}

	public void setPagado(Boolean pagado) {
		this.pagado = pagado;
	}

}
