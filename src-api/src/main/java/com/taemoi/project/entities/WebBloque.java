package com.taemoi.project.entities;

import java.time.LocalDateTime;

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
 *
 * <p>W12a (V32_9): {@code oculto} lo lleva la fila de cabecera de una sección movible; la constancia del permiso de
 * publicación de un profesor ({@code permiso*}, d521 y d526) va en su fila {@code profesor.<n>.nombre} y solo la lee
 * el editor: la API pública se construye campo a campo y nunca la incluye.
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

	@Column(nullable = false)
	private Boolean oculto = Boolean.FALSE;

	/** {@code PANEL} (lo marcó {@link #permisoPor} en el editor) o {@code ALTA} (dado en el alta); {@code null} sin él. */
	@Column(name = "permiso_origen", length = 10)
	private String permisoOrigen;

	/** Id del usuario que marcó el permiso; sin clave ajena, como {@code web_escuela.publicada_por}. */
	@Column(name = "permiso_por")
	private Long permisoPor;

	@Column(name = "permiso_en")
	private LocalDateTime permisoEn;

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

	public boolean isOculto() {
		return Boolean.TRUE.equals(oculto);
	}

	public void setOculto(boolean oculto) {
		this.oculto = oculto;
	}

	public String getPermisoOrigen() {
		return permisoOrigen;
	}

	public void setPermisoOrigen(String permisoOrigen) {
		this.permisoOrigen = permisoOrigen;
	}

	public Long getPermisoPor() {
		return permisoPor;
	}

	public void setPermisoPor(Long permisoPor) {
		this.permisoPor = permisoPor;
	}

	public LocalDateTime getPermisoEn() {
		return permisoEn;
	}

	public void setPermisoEn(LocalDateTime permisoEn) {
		this.permisoEn = permisoEn;
	}
}
