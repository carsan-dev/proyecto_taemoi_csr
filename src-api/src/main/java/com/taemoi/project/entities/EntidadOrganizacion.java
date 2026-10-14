package com.taemoi.project.entities;

import org.hibernate.annotations.TenantId;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

/**
 * Superclase de toda entidad que pertenece a una organización (escuela).
 *
 * <p>Hibernate rellena {@code organizacion_id} al insertar con la organización activa
 * de la sesión y añade el filtro por organización a toda consulta HQL/JPQL, Criteria
 * y carga por id (ver {@code com.taemoi.project.organizacion.ResolutorOrganizacion}).
 * Las consultas SQL nativas NO se filtran: no deben usarse sobre estas tablas.
 *
 * <p>No se expone en JSON para no cambiar las respuestas de la API.
 */
@MappedSuperclass
public abstract class EntidadOrganizacion {

	@TenantId
	@JsonIgnore
	@Column(name = "organizacion_id", nullable = false, updatable = false)
	private Long organizacionId;

	@JsonIgnore
	public Long getOrganizacionId() {
		return organizacionId;
	}
}
