package com.taemoi.project.dtos.response;

import com.taemoi.project.entidades.Alumno;

public class AlumnoParaUsuarioDTO {
	private Long id;
    private String nombre;
    private String apellidos;
	
	public AlumnoParaUsuarioDTO(Long id, String nombre, String apellidos) {
		super();
		this.id = id;
		this.nombre = nombre;
		this.apellidos = apellidos;
	}
	
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

	public String getApellidos() {
		return apellidos;
	}

	public void setApellidos(String apellidos) {
		this.apellidos = apellidos;
	}
	
	public static AlumnoParaUsuarioDTO deAlumno(Alumno alumno) {
	    if (alumno == null) {
	        return null;
	    }

	    return new AlumnoParaUsuarioDTO(alumno.getId(), alumno.getNombre(), alumno.getApellidos());
	}
}
