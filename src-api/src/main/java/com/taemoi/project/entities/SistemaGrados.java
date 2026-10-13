package com.taemoi.project.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Conjunto ordenado de grados de una organización (p. ej. taekwondo, kickboxing). Una
 * {@link Disciplina} sin sistema no tiene grados.
 */
@Entity
@Table(name = "sistema_grados")
public class SistemaGrados extends EntidadOrganizacion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100)
	private String nombre;

	/** Plantilla de la que se copió (p. ej. {@code TAEKWONDO}); informativo. */
	@Column(name = "plantilla_origen", length = 40)
	private String plantillaOrigen;

	public Long getId() {
		return id;
	}

	public String getNombre() {
		return nombre;
	}

	public void setNombre(String nombre) {
		this.nombre = nombre;
	}

	public String getPlantillaOrigen() {
		return plantillaOrigen;
	}

	public void setPlantillaOrigen(String plantillaOrigen) {
		this.plantillaOrigen = plantillaOrigen;
	}
}
