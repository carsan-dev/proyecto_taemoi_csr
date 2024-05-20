package com.taemoi.project.dtos.response;

import com.taemoi.project.entidades.Alumno;
import com.taemoi.project.entidades.Imagen;

public class AlumnoParaGrupoDTO {
	private Long id;
    private String nombre;
    private String apellidos;
	private Imagen fotoAlumno;
	
	public AlumnoParaGrupoDTO(Long id, String nombre, String apellidos, Imagen fotoAlumno) {
		super();
		this.id = id;
		this.nombre = nombre;
		this.apellidos = apellidos;
		this.fotoAlumno = fotoAlumno;
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

	public Imagen getFotoAlumno() {
		return fotoAlumno;
	}

	public void setFotoAlumno(Imagen fotoAlumno) {
		this.fotoAlumno = fotoAlumno;
	}
	
	public static AlumnoParaGrupoDTO deAlumno(Alumno alumno) {
	    if (alumno == null) {
	        return null;
	    }

	    return new AlumnoParaGrupoDTO(alumno.getId(), alumno.getNombre(), alumno.getApellidos(), alumno.getFotoAlumno());
	}
}
