package com.taemoi.project.entities;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Index;

@Entity
@Table(name = "alumno_reto_diario_log", uniqueConstraints = {
		@UniqueConstraint(name = "uk_alumno_reto_diario_log_alumno_fecha", columnNames = { "alumno_id",
				"fecha_completado" }) }, indexes = {
						@Index(name = "idx_alumno_reto_diario_log_semana", columnList = "anio_iso, semana_iso"),
						@Index(name = "idx_alumno_reto_diario_log_alumno_semana", columnList = "alumno_id, anio_iso, semana_iso") })
public class AlumnoRetoDiarioLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "alumno_id", nullable = false)
	private Alumno alumno;

	@Column(name = "fecha_completado", nullable = false)
	private LocalDate fechaCompletado;

	@Column(name = "anio_iso", nullable = false)
	private Integer anioIso;

	@Column(name = "semana_iso", nullable = false)
	private Integer semanaIso;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@PrePersist
	void prePersist() {
		if (createdAt == null) {
			createdAt = LocalDateTime.now();
		}
	}

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

	public LocalDate getFechaCompletado() {
		return fechaCompletado;
	}

	public void setFechaCompletado(LocalDate fechaCompletado) {
		this.fechaCompletado = fechaCompletado;
	}

	public Integer getAnioIso() {
		return anioIso;
	}

	public void setAnioIso(Integer anioIso) {
		this.anioIso = anioIso;
	}

	public Integer getSemanaIso() {
		return semanaIso;
	}

	public void setSemanaIso(Integer semanaIso) {
		this.semanaIso = semanaIso;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}
}
