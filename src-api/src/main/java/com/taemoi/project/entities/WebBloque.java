package com.taemoi.project.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Bloque editable de la web por plantilla (V32_5, fase4-web-plan.md §4.1): un texto y/o una imagen por clave
 * ({@code portada.titular}, {@code presentacion.texto}, {@code profesor.<n>.nombre}, {@code galeria.<n>}, ...).
 * Pertenece a la escuela activa ({@code @TenantId}). Los profesores de la web salen de aquí, no de las asignaciones
 * (decisión del §9, n.º 5): nada personal sale solo.
 */
@Entity
@Table(name = "web_bloque")
public class WebBloque extends EntidadOrganizacion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 60)
	private String clave;

	@Column(columnDefinition = "TEXT")
	private String texto;

	/** Solo el id: la imagen se lee aparte, filtrada por la escuela activa. */
	@Column(name = "imagen_id")
	private Long imagenId;

	@Column(nullable = false)
	private Integer orden = 0;

	public Long getId() {
		return id;
	}

	public String getClave() {
		return clave;
	}

	public void setClave(String clave) {
		this.clave = clave;
	}

	public String getTexto() {
		return texto;
	}

	public void setTexto(String texto) {
		this.texto = texto;
	}

	public Long getImagenId() {
		return imagenId;
	}

	public void setImagenId(Long imagenId) {
		this.imagenId = imagenId;
	}

	public Integer getOrden() {
		return orden;
	}

	public void setOrden(Integer orden) {
		this.orden = orden;
	}
}
