package com.taemoi.project.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Contador por organización (p. ej. {@code numero_expediente}). {@code valor} es el último
 * número entregado; se incrementa bajo bloqueo pesimista.
 */
@Entity
@Table(name = "contador",
		uniqueConstraints = @UniqueConstraint(name = "uk_contador_clave", columnNames = { "organizacion_id", "clave" }))
public class Contador extends EntidadOrganizacion {

	public static final String NUMERO_EXPEDIENTE = "numero_expediente";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 40)
	private String clave;

	@Column(nullable = false)
	private Long valor;

	public Contador() {
	}

	public Contador(String clave, Long valor) {
		this.clave = clave;
		this.valor = valor;
	}

	public Long getId() {
		return id;
	}

	public String getClave() {
		return clave;
	}

	public Long getValor() {
		return valor;
	}

	public void setValor(Long valor) {
		this.valor = valor;
	}
}
