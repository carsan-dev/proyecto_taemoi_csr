package com.taemoi.project.entities;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.validation.constraints.NotNull;

@Entity
public class Convocatoria extends EntidadOrganizacion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotNull(message = "La fecha de la convocatoria no puede ser nula")
	@Temporal(TemporalType.DATE)
	private Date fechaConvocatoria;

	/**
	 * Disciplina (V26, NOT NULL). Desde V28 es la única fuente del deporte de la convocatoria (la columna
	 * heredada {@code deporte} se borró); el JSON expone su código como {@code deporte}.
	 */
	@JsonIgnore
	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "disciplina_id", nullable = false)
	@NotNull(message = "El deporte de la convocatoria no puede ser nulo")
	private Disciplina disciplina;

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

	/** Código de la disciplina: el valor de la antigua columna {@code deporte}, que la API sigue devolviendo. */
	@JsonProperty("deporte")
	public String getDeporte() {
		return disciplina == null ? null : disciplina.getCodigo();
	}

	public Disciplina getDisciplina() {
		return disciplina;
	}

	public void setDisciplina(Disciplina disciplina) {
		this.disciplina = disciplina;
	}

	public List<AlumnoConvocatoria> getAlumnosConvocatoria() {
		return alumnosConvocatoria;
	}

	public void setAlumnosConvocatoria(List<AlumnoConvocatoria> alumnosConvocatoria) {
		this.alumnosConvocatoria = alumnosConvocatoria;
	}
}
