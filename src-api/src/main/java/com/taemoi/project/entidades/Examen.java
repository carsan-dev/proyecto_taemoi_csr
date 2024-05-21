package com.taemoi.project.entidades;

import java.util.Date;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.validation.constraints.NotNull;

@Entity
public class Examen {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne
	@JoinColumn(name = "alumno_id")
	private Alumno alumno;

	@ManyToOne
	@JoinColumn(name = "grado_id")
	private Grado grado;

	@Temporal(TemporalType.DATE)
	@NotNull(message = "La fecha del examen no puede ser nula")
	private Date fecha;

	@ManyToOne
	@JoinColumn(name = "examen_grado_id")
	private Grado examenGrado;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Alumno getAlumno() {
		return alumno;
	}

	public void setAlumno(Alumno alumno) {
		this.alumno = alumno;
	}

	public Grado getGrado() {
		return grado;
	}

	public void setGrado(Grado grado) {
		this.grado = grado;
	}

	public Date getFecha() {
		return fecha;
	}

	public void setFecha(Date fecha) {
		this.fecha = fecha;
	}

	public Grado getExamenGrado() {
		return examenGrado;
	}

	public void setExamenGrado(Grado examenGrado) {
		this.examenGrado = examenGrado;
	}

}