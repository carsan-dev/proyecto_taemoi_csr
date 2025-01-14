package com.taemoi.project.entidades;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.validation.constraints.NotNull;

@Entity
public class Convocatoria {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotNull(message = "La fecha de la convocatoria no puede ser nula")
	@Temporal(TemporalType.DATE)
	private Date fechaConvocatoria;

	@Enumerated(EnumType.STRING)
	@NotNull(message = "El deporte de la convocatoria no puede ser nulo")
	private Deporte deporte;

	@OneToMany(mappedBy = "convocatoria", cascade = CascadeType.ALL)
	private List<AlumnoConvocatoria> alumnosConvocatoria = new ArrayList<>();

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Date getFechaConvocatoria() {
		return fechaConvocatoria;
	}

	public void setFechaConvocatoria(Date fechaConvocatoria) {
		this.fechaConvocatoria = fechaConvocatoria;
	}

	public Deporte getDeporte() {
		return deporte;
	}

	public void setDeporte(Deporte deporte) {
		this.deporte = deporte;
	}

	public List<AlumnoConvocatoria> getAlumnosConvocatoria() {
		return alumnosConvocatoria;
	}

	public void setAlumnosConvocatoria(List<AlumnoConvocatoria> alumnosConvocatoria) {
		this.alumnosConvocatoria = alumnosConvocatoria;
	}
}
