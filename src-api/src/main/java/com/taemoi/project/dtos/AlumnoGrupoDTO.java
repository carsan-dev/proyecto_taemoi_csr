package com.taemoi.project.dtos;

import com.taemoi.project.entidades.Alumno;
import com.taemoi.project.entidades.Imagen;

public class AlumnoGrupoDTO {
    private String nombre;
    private String apellidos;
	private Imagen fotoAlumno;
	
	public AlumnoGrupoDTO(String nombre, String apellidos, Imagen fotoAlumno) {
		super();
		this.nombre = nombre;
		this.apellidos = apellidos;
		this.fotoAlumno = fotoAlumno;
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
	
	public static AlumnoGrupoDTO deAlumno(Alumno alumno) {
	    if (alumno == null) {
	        return null;
	    }

	    return new AlumnoGrupoDTO(alumno.getNombre(), alumno.getApellidos(), alumno.getFotoAlumno());
	}
}
