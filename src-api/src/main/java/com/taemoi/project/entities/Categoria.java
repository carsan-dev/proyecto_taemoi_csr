package com.taemoi.project.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotBlank;

@Entity
public class Categoria {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	private TipoCategoria tipoCategoria;

	@NotBlank(message = "El nombre de la categoría no puede estar en blanco")
	private String nombre;

	// DEPRECATED: Categoria is now per-sport (mapped in AlumnoDeporte, not Alumno)
	// @OneToMany(mappedBy = "categoria")
	// @JsonBackReference
	// private List<Alumno> alumnos;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public TipoCategoria getTipoCategoria() {
		return tipoCategoria;
	}

	public void setTipoCategoria(TipoCategoria tipoCategoria) {
		this.tipoCategoria = tipoCategoria;
	}

	public String getNombre() {
		return nombre;
	}

	public void setNombre(String nombre) {
		this.nombre = nombre;
	}

	// DEPRECATED: Categoria is now per-sport (mapped in AlumnoDeporte, not Alumno)
	// public List<Alumno> getAlumnos() {
	// 	return alumnos;
	// }

	// public void setAlumnos(List<Alumno> alumnos) {
	// 	this.alumnos = alumnos;
	// }
}