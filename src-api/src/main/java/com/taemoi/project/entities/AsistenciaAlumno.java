package com.taemoi.project.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Un alumno en una lista guardada (decisión 324, tabla {@code asistencia_alumno} de V31_15): una fila por CADA alumno de
 * la lista, presente o no. Ids simples, sin relaciones, como {@link ClaseAsistencia}.
 */
@Entity
@Table(name = "asistencia_alumno")
public class AsistenciaAlumno extends EntidadOrganizacion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "clase_id", nullable = false)
	private Long claseId;

	@Column(name = "alumno_id", nullable = false)
	private Long alumnoId;

	@Column(nullable = false)
	private boolean presente;

	protected AsistenciaAlumno() {
	}

	public AsistenciaAlumno(Long claseId, Long alumnoId, boolean presente) {
		this.claseId = claseId;
		this.alumnoId = alumnoId;
		this.presente = presente;
	}

	public Long getId() {
		return id;
	}

	public Long getClaseId() {
		return claseId;
	}

	public Long getAlumnoId() {
		return alumnoId;
	}

	public boolean isPresente() {
		return presente;
	}

	public void setPresente(boolean presente) {
		this.presente = presente;
	}
}
