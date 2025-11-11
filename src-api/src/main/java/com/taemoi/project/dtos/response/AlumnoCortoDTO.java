package com.taemoi.project.dtos.response;

import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.Imagen;

public class AlumnoCortoDTO {
	private Long id;
	private String nombre;
	private String apellidos;
	private Imagen fotoAlumno;
	private Integer numeroExpediente;
	private String grado;
	private Boolean activo;

	public AlumnoCortoDTO(Long id, String nombre, String apellidos, Imagen fotoAlumno, Integer numeroExpediente, String grado, Boolean activo) {
		super();
		this.id = id;
		this.nombre = nombre;
		this.apellidos = apellidos;
		this.fotoAlumno = fotoAlumno;
		this.numeroExpediente = numeroExpediente;
		this.grado = grado;
		this.activo = activo;
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

	public Integer getNumeroExpediente() {
		return numeroExpediente;
	}

	public void setNumeroExpediente(Integer numeroExpediente) {
		this.numeroExpediente = numeroExpediente;
	}

	public String getGrado() {
		return grado;
	}

	public void setGrado(String grado) {
		this.grado = grado;
	}

	public Boolean getActivo() {
		return activo;
	}

	public void setActivo(Boolean activo) {
		this.activo = activo;
	}

	public static AlumnoCortoDTO deAlumno(Alumno alumno) {
		if (alumno == null) {
			return null;
		}

		String grado = alumno.getGrado() != null ? alumno.getGrado().getTipoGrado().getNombre() : null;
		return new AlumnoCortoDTO(
			alumno.getId(),
			alumno.getNombre(),
			alumno.getApellidos(),
			alumno.getFotoAlumno(),
			alumno.getNumeroExpediente(),
			grado,
			alumno.getActivo()
		);
	}
}
