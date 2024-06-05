package com.taemoi.project.dtos.response;

public class UsuarioConAlumnoAsociadoDTO {
	private Long id;
	private String email;
	private AlumnoParaUsuarioDTO alumnoDTO;
	
	public Long getId() {
		return id;
	}
	public void setId(Long id) {
		this.id = id;
	}
	public String getEmail() {
		return email;
	}
	public void setEmail(String email) {
		this.email = email;
	}
	public AlumnoParaUsuarioDTO getAlumnoDTO() {
		return alumnoDTO;
	}
	public void setAlumnoDTO(AlumnoParaUsuarioDTO alumnoDTO) {
		this.alumnoDTO = alumnoDTO;
	}
	
}
