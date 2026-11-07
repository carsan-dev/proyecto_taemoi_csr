package com.taemoi.project.entities;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Web por plantilla de una escuela (V32_5, fase4-web-plan.md §4.1): el tema publicado y su estado. Una fila por
 * escuela, con la organización como clave; sin fila, la escuela no tiene web de plantilla (Moiskimdo, la 1, no la
 * tiene). Tabla aparte de {@code organizacion} (que es global y escribe solo el superadmin): el tema lo publica el
 * propietario.
 *
 * <p>No lleva {@code @TenantId}: la clave ES la organización y se lee siempre por la escuela activa
 * ({@code TenantContext}), como {@link Organizacion}.
 */
@Entity
@Table(name = "web_escuela")
public class WebEscuela {

	@Id
	@Column(name = "organizacion_id")
	private Long organizacionId;

	/** {@code linea-comarcal}, {@code chandal}, {@code sede-por-pantalla} o {@code estandar}. */
	@Column(nullable = false, length = 30)
	private String tema;

	@Column(nullable = false)
	private Boolean publicada = Boolean.FALSE;

	@Column(name = "publicada_en")
	private LocalDateTime publicadaEn;

	/** Id del usuario que publicó por última vez (sin clave ajena, como en el plan: solo informativo). */
	@Column(name = "publicada_por")
	private Long publicadaPor;

	@Column(name = "actualizada_en", nullable = false, insertable = false, updatable = false)
	private LocalDateTime actualizadaEn;

	public Long getOrganizacionId() {
		return organizacionId;
	}

	public void setOrganizacionId(Long organizacionId) {
		this.organizacionId = organizacionId;
	}

	public String getTema() {
		return tema;
	}

	public void setTema(String tema) {
		this.tema = tema;
	}

	public boolean isPublicada() {
		return Boolean.TRUE.equals(publicada);
	}

	public void setPublicada(boolean publicada) {
		this.publicada = publicada;
	}

	public LocalDateTime getPublicadaEn() {
		return publicadaEn;
	}

	public void setPublicadaEn(LocalDateTime publicadaEn) {
		this.publicadaEn = publicadaEn;
	}

	public Long getPublicadaPor() {
		return publicadaPor;
	}

	public void setPublicadaPor(Long publicadaPor) {
		this.publicadaPor = publicadaPor;
	}

	public LocalDateTime getActualizadaEn() {
		return actualizadaEn;
	}
}
