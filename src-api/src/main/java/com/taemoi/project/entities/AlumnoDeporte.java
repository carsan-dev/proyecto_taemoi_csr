package com.taemoi.project.entities;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "alumno_deporte",
	uniqueConstraints = @UniqueConstraint(columnNames = {"alumno_id", "deporte"}),
	indexes = {
		@Index(name = "idx_alumno_deporte_alumno_id", columnList = "alumno_id"),
		@Index(name = "idx_alumno_deporte_deporte", columnList = "deporte"),
		@Index(name = "idx_alumno_deporte_apto", columnList = "deporte, apto_para_examen")
	})
public class AlumnoDeporte {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "alumno_id", nullable = false)
	@JsonIgnore
	private Alumno alumno;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	@NotNull(message = "El deporte no puede ser nulo")
	private Deporte deporte;

	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "grado_id")
	@JsonManagedReference
	private Grado grado;

	@Temporal(TemporalType.DATE)
	private Date fechaGrado;

	@Column(name = "apto_para_examen", nullable = false)
	@NotNull(message = "El campo aptoParaExamen no puede ser nulo")
	private Boolean aptoParaExamen = false;

	@Column(nullable = false)
	@NotNull(message = "El campo activo no puede ser nulo")
	private Boolean activo = true;

	@Temporal(TemporalType.DATE)
	private Date fechaAlta;

	@Temporal(TemporalType.DATE)
	private Date fechaBaja;

	@OneToMany(mappedBy = "alumnoDeporte", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<AlumnoConvocatoria> convocatorias = new ArrayList<>();

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

	public Deporte getDeporte() {
		return deporte;
	}

	public void setDeporte(Deporte deporte) {
		this.deporte = deporte;
	}

	public Grado getGrado() {
		return grado;
	}

	public void setGrado(Grado grado) {
		this.grado = grado;
	}

	public Date getFechaGrado() {
		return fechaGrado;
	}

	public void setFechaGrado(Date fechaGrado) {
		this.fechaGrado = fechaGrado;
	}

	public Boolean getAptoParaExamen() {
		return aptoParaExamen;
	}

	public void setAptoParaExamen(Boolean aptoParaExamen) {
		this.aptoParaExamen = aptoParaExamen;
	}

	public Boolean getActivo() {
		return activo;
	}

	public void setActivo(Boolean activo) {
		this.activo = activo;
	}

	public Date getFechaAlta() {
		return fechaAlta;
	}

	public void setFechaAlta(Date fechaAlta) {
		this.fechaAlta = fechaAlta;
	}

	public Date getFechaBaja() {
		return fechaBaja;
	}

	public void setFechaBaja(Date fechaBaja) {
		this.fechaBaja = fechaBaja;
	}

	public List<AlumnoConvocatoria> getConvocatorias() {
		return convocatorias;
	}

	public void setConvocatorias(List<AlumnoConvocatoria> convocatorias) {
		this.convocatorias = convocatorias;
	}
}
