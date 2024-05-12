package com.taemoi.project.dtos;

import java.util.List;

public class GrupoDTO {
    private Long id;
    private String nombre;
    private List<AlumnoGrupoDTO> alumnos;
    
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
	public List<AlumnoGrupoDTO> getAlumnos() {
		return alumnos;
	}
	public void setAlumnos(List<AlumnoGrupoDTO> alumnos) {
		this.alumnos = alumnos;
	}

}
