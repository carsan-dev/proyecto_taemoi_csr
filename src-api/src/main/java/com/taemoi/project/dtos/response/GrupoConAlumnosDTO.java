package com.taemoi.project.dtos.response;

import java.util.List;

public class GrupoConAlumnosDTO {
    private Long id;
    private String nombre;
    private List<AlumnoParaGrupoDTO> alumnos;
    
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
	public List<AlumnoParaGrupoDTO> getAlumnos() {
		return alumnos;
	}
	public void setAlumnos(List<AlumnoParaGrupoDTO> alumnos) {
		this.alumnos = alumnos;
	}

}
