package com.taemoi.project.entities;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Interruptor del diseño nuevo del panel de una cuenta en la escuela activa (V31_10, pieza 8b-I; fase3-e8b-plan.md §3).
 * {@code @TenantId}: Hibernate filtra por la escuela activa, así que la misma cuenta tiene una fila por escuela y nunca
 * se lee la de otra. Regla efectiva en {@code DisenoPanelService#activoPara}: con fila y ({@code permitido} o el valor
 * por defecto NUEVO), su {@code activo}; si no, el valor por defecto de la escuela.
 */
@Entity
@Table(name = "diseno_panel_usuario",
		uniqueConstraints = @UniqueConstraint(name = "uk_diseno_panel_usuario_organizacion_usuario",
				columnNames = { "organizacion_id", "usuario_id" }))
public class DisenoPanelUsuario extends EntidadOrganizacion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "usuario_id", nullable = false, updatable = false)
	private Long usuarioId;

	/** El propietario le dio permiso (decisión 256). Solo lo cambia el propietario; {@code mi-diseno}, nunca. */
	@Column(nullable = false)
	private boolean permitido;

	/** Su elección; cuenta con {@code permitido} o con el valor por defecto NUEVO. */
	@Column(nullable = false)
	private boolean activo;

	@Column(name = "cambiado_por_usuario_id")
	private Long cambiadoPorUsuarioId;

	@Column(name = "cambiado_en", nullable = false)
	private LocalDateTime cambiadoEn;

	protected DisenoPanelUsuario() {
	}

	public DisenoPanelUsuario(Long usuarioId) {
		this.usuarioId = usuarioId;
	}

	public Long getId() {
		return id;
	}

	public Long getUsuarioId() {
		return usuarioId;
	}

	public boolean isPermitido() {
		return permitido;
	}

	public void setPermitido(boolean permitido) {
		this.permitido = permitido;
	}

	public boolean isActivo() {
		return activo;
	}

	public void setActivo(boolean activo) {
		this.activo = activo;
	}

	public Long getCambiadoPorUsuarioId() {
		return cambiadoPorUsuarioId;
	}

	public LocalDateTime getCambiadoEn() {
		return cambiadoEn;
	}

	/** Quién y cuándo lo cambió por última vez. */
	public void marcarCambio(Long usuario, LocalDateTime cuando) {
		this.cambiadoPorUsuarioId = usuario;
		this.cambiadoEn = cuando;
	}
}
