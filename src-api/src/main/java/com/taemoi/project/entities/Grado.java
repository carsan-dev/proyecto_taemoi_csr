package com.taemoi.project.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotNull;

@Entity
public class Grado extends EntidadOrganizacion {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotNull(message = "El tipo de grado no puede ser nulo")
	@Enumerated(EnumType.STRING)
	private TipoGrado tipoGrado;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public TipoGrado getTipoGrado() {
		return tipoGrado;
	}

	public void setTipoGrado(TipoGrado tipoGrado) {
		this.tipoGrado = tipoGrado;
	}
}