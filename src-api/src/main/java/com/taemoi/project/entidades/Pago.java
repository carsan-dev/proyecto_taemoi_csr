package com.taemoi.project.entidades;

import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Entity
public class Pago {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne
	@JoinColumn(name = "alumno_id")
	private Alumno alumno;

	@NotBlank(message = "El número de expediente del alumno no puede estar en blanco")
	@Column(name = "numero_expediente_alumno")
	private String numeroExpedienteAlumno;

	@NotBlank(message = "El concepto no puede estar en blanco")
	private String concepto;

	@NotNull(message = "La fecha del pago no puede ser nula")
	@Temporal(TemporalType.DATE)
	private Date fecha;

	@NotNull(message = "La cuantía del pago no puede ser nula")
	private Double cuantia;

	@NotNull(message = "El estado del pago no puede ser nulo")
	@Enumerated(EnumType.STRING)
	private EstadoPago estado;

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

	public String getNumeroExpedienteAlumno() {
		return numeroExpedienteAlumno;
	}

	public void setNumeroExpedienteAlumno(String numeroExpedienteAlumno) {
		this.numeroExpedienteAlumno = numeroExpedienteAlumno;
	}

	public String getConcepto() {
		return concepto;
	}

	public void setConcepto(String concepto) {
		this.concepto = concepto;
	}

	public Date getFecha() {
		return fecha;
	}

	public void setFecha(Date fecha) {
		this.fecha = fecha;
	}

	public Double getCuantia() {
		return cuantia;
	}

	public void setCuantia(Double cuantia) {
		this.cuantia = cuantia;
	}

	public EstadoPago getEstado() {
		return estado;
	}

	public void setEstado(EstadoPago estado) {
		this.estado = estado;
	}

}