package com.taemoi.project.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Par clave/valor de configuración de una organización (sustituye a configuracion_sistema).
 */
@Entity
@Table(name = "configuracion_organizacion",
		uniqueConstraints = @UniqueConstraint(name = "uk_configuracion_organizacion_clave",
				columnNames = { "organizacion_id", "clave" }))
public class ConfiguracionOrganizacion extends EntidadOrganizacion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 80)
	private String clave;

	@Column(nullable = false, length = 255)
	private String valor;

	public ConfiguracionOrganizacion() {
	}

	public ConfiguracionOrganizacion(String clave, String valor) {
		this.clave = clave;
		this.valor = valor;
	}

	public Long getId() {
		return id;
	}

	public String getClave() {
		return clave;
	}

	public void setClave(String clave) {
		this.clave = clave;
	}

	public String getValor() {
		return valor;
	}

	public void setValor(String valor) {
		this.valor = valor;
	}
}
