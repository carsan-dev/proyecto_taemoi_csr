package com.taemoi.project.dtos.response;

public class GrupoResponseDTO {
    private Long id;
    private String nombre;

	public GrupoResponseDTO() {
		// TODO Auto-generated constructor stub
	}

	public GrupoResponseDTO(Long id, String nombre) {
		this.id = id;
		this.nombre = nombre;
	}

	public String getNombre() {
		return nombre;
	}

	public void setNombre(String nombre) {
		this.nombre = nombre;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

}
