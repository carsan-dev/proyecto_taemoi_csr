package com.taemoi.project.entities;

import com.fasterxml.jackson.annotation.JsonBackReference;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Entity
public class Documento {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotBlank(message = "El nombre del documento no puede estar en blanco")
	@Size(max = 200, message = "El nombre no puede tener más de 200 caracteres")
	private String nombre;

	@NotBlank(message = "El tipo (MIME) no puede estar en blanco")
	@Size(max = 50, message = "El tipo no puede tener más de 50 caracteres")
	private String tipo; // MIME type (application/pdf, application/msword, etc.)

	private String url;

	@NotBlank(message = "La ruta no puede estar en blanco")
	@Size(max = 500, message = "La ruta no puede tener más de 500 caracteres")
	private String ruta;

	@ManyToOne
	@JoinColumn(name = "alumno_id")
	@JsonBackReference("alumno-documentos")
	private Alumno alumno;

	@ManyToOne
	@JoinColumn(name = "evento_id")
	@JsonBackReference("evento-documentos")
	private Evento evento;

	public Documento() {
	}

	public Documento(String nombre, String tipo, String url, String ruta) {
		this.nombre = nombre;
		this.tipo = tipo;
		this.url = url;
		this.ruta = ruta;
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

	public String getTipo() {
		return tipo;
	}

	public void setTipo(String tipo) {
		this.tipo = tipo;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getRuta() {
		return ruta;
	}

	public void setRuta(String ruta) {
		this.ruta = ruta;
	}

	public Alumno getAlumno() {
		return alumno;
	}

	public void setAlumno(Alumno alumno) {
		this.alumno = alumno;
	}

	public Evento getEvento() {
		return evento;
	}

	public void setEvento(Evento evento) {
		this.evento = evento;
	}

}