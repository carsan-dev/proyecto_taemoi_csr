package com.taemoi.project.entities;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Entity
public class Grupo extends EntidadOrganizacion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotBlank(message = "El nombre no puede estar en blanco")
	@Size(max = 50, message = "El nombre no puede tener más de 50 caracteres")
	private String nombre;

	private String tipo;

	// NUEVO: Campo para identificar explícitamente el deporte del grupo
	@Enumerated(EnumType.STRING)
	@NotNull(message = "El deporte del grupo no puede ser nulo")
	private Deporte deporte;

	/**
	 * Disciplina (V26, NOT NULL). Se escribe junto a {@code deporte}, que se conserva hasta la
	 * contracción para poder volver al código anterior.
	 */
	@JsonIgnore
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "disciplina_id", nullable = false)
	private Disciplina disciplina;

	// Rango de edad personalizado (opcional)
	@Column(name = "rango_edad_min")
	private Integer rangoEdadMin;

	@Column(name = "rango_edad_max")
	private Integer rangoEdadMax;

	@ManyToMany(fetch = FetchType.EAGER)
	@JoinTable(name = "alumno_grupo", joinColumns = @JoinColumn(name = "grupo_id"), inverseJoinColumns = @JoinColumn(name = "alumno_id"))
	@JsonBackReference
	private Set<Alumno> alumnos = new LinkedHashSet<>();

	@OneToMany(mappedBy = "grupo", fetch = FetchType.LAZY)
	@JsonBackReference
	private List<Turno> turnos;

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

	public Deporte getDeporte() {
		return deporte;
	}

	public void setDeporte(Deporte deporte) {
		this.deporte = deporte;
	}

	public Disciplina getDisciplina() {
		return disciplina;
	}

	public void setDisciplina(Disciplina disciplina) {
		this.disciplina = disciplina;
	}

	public Integer getRangoEdadMin() {
		return rangoEdadMin;
	}

	public void setRangoEdadMin(Integer rangoEdadMin) {
		this.rangoEdadMin = rangoEdadMin;
	}

	public Integer getRangoEdadMax() {
		return rangoEdadMax;
	}

	public void setRangoEdadMax(Integer rangoEdadMax) {
		this.rangoEdadMax = rangoEdadMax;
	}

	public Set<Alumno> getAlumnos() {
		return alumnos;
	}

	public void setAlumnos(Collection<Alumno> alumnos) {
		this.alumnos = alumnos == null ? new LinkedHashSet<>() : new LinkedHashSet<>(alumnos);
	}

	public List<Turno> getTurnos() {
		return turnos;
	}

	public void setTurnos(List<Turno> turnos) {
		this.turnos = turnos;
	}

	public void addAlumno(Alumno alumno) {
		if (alumno != null) {
			if (alumnos.stream().noneMatch(actual -> mismaEntidad(actual, alumno))) {
				alumnos.add(alumno);
			}
			if (alumno.getGrupos().stream().noneMatch(actual -> mismaEntidad(actual, this))) {
				alumno.getGrupos().add(this);
			}
		}
	}

	public void removeAlumno(Alumno alumno) {
		if (alumno != null) {
			alumnos.removeIf(actual -> mismaEntidad(actual, alumno));
			alumno.getGrupos().removeIf(actual -> mismaEntidad(actual, this));
		}
	}

	private boolean mismaEntidad(Alumno actual, Alumno objetivo) {
		return actual == objetivo || actual.getId() != null && actual.getId().equals(objetivo.getId());
	}

	private boolean mismaEntidad(Grupo actual, Grupo objetivo) {
		return actual == objetivo || actual.getId() != null && actual.getId().equals(objetivo.getId());
	}
}
