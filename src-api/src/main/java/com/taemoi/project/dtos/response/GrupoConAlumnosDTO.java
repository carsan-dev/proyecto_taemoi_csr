package com.taemoi.project.dtos.response;

import java.util.List;

public class GrupoConAlumnosDTO {
	private Long id;
	private String nombre;
	private String deporte; // Deporte del grupo (TAEKWONDO, KICKBOXING, PILATES, etc.)
	private Integer rangoEdadMin;
	private Integer rangoEdadMax;
	private List<AlumnoCortoDTO> alumnos;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getNombre() {
		return nombre;
	}

	public void setNombre(String nombre) {
		this.nombre = nombre;
	}

	public String getDeporte() {
		return deporte;
	}

	public void setDeporte(String deporte) {
		this.deporte = deporte;
	}

	public Integer getRangoEdadMin() {
		return rangoEdadMin;
	}

	public void setRangoEdadMin(Integer rangoEdadMin) {
		this.rangoEdadMin = rangoEdadMin;
	}

	public Integer getRangoEdadMax() {
		return rangoEdadMax;
	}

	public void setRangoEdadMax(Integer rangoEdadMax) {
		this.rangoEdadMax = rangoEdadMax;
	}

	public List<AlumnoCortoDTO> getAlumnos() {
		return alumnos;
	}

	public void setAlumnos(List<AlumnoCortoDTO> alumnos) {
		this.alumnos = alumnos;
	}

}
