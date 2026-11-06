package com.taemoi.project.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Un alumno en una lista guardada (decisión 324, tabla {@code asistencia_alumno} de V31_15): una fila por CADA alumno de
 * la lista, presente o no. Ids simples, sin relaciones, como {@link ClaseAsistencia}. {@code motivoFalta} (V32_2,
 * decisiones 455-463): solo en una falta; {@code null} si estuvo o si la falta no tiene motivo.
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

	@Enumerated(EnumType.STRING)
	@Column(name = "motivo_falta", length = 20)
	private MotivoFalta motivoFalta;

	protected AsistenciaAlumno() {
	}

	public AsistenciaAlumno(Long claseId, Long alumnoId, boolean presente) {
		this(claseId, alumnoId, presente, null);
	}

	public AsistenciaAlumno(Long claseId, Long alumnoId, boolean presente, MotivoFalta motivoFalta) {
		this.claseId = claseId;
		this.alumnoId = alumnoId;
		marcar(presente, motivoFalta);
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

	public MotivoFalta getMotivoFalta() {
		return motivoFalta;
	}

	/** Estado completo de la fila: presente (sin motivo) o falta, con o sin motivo. */
	public void marcar(boolean presente, MotivoFalta motivoFalta) {
		this.presente = presente;
		this.motivoFalta = presente ? null : motivoFalta;
	}
}
