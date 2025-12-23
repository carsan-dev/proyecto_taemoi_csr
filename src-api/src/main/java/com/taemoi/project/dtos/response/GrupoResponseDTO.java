package com.taemoi.project.dtos.response;

public class GrupoResponseDTO {
	private Long id;
	private String nombre;
	private String deporte; // Deporte del grupo (TAEKWONDO, KICKBOXING, PILATES, etc.)

	public GrupoResponseDTO() {
		// TODO Auto-generated constructor stub
	}

	public GrupoResponseDTO(Long id, String nombre) {
		this.id = id;
		this.nombre = nombre;
	}

	public GrupoResponseDTO(Long id, String nombre, String deporte) {
		this.id = id;
		this.nombre = nombre;
		this.deporte = deporte;
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

	public String getDeporte() {
		return deporte;
	}

	public void setDeporte(String deporte) {
		this.deporte = deporte;
	}

}
