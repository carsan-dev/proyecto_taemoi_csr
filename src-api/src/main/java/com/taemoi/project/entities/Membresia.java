package com.taemoi.project.entities;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Pertenencia de un usuario (global) a una organización con un rol.
 *
 * <p>NO es una {@link EntidadOrganizacion}: no lleva {@code @TenantId} porque hay que poder
 * consultar las membresías de un usuario en todas sus escuelas (inicio de sesión, cambio de
 * escuela). Por eso TODA consulta sobre esta entidad debe filtrar explícitamente por usuario
 * y/o por organización (ver {@code MembresiaRepository}).
 */
@Entity
@Table(name = "membresia")
public class Membresia {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "usuario_id", nullable = false, updatable = false)
	private Long usuarioId;

	@Column(name = "organizacion_id", nullable = false, updatable = false)
	private Long organizacionId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false,
			columnDefinition = "ENUM('PROPIETARIO','ADMINISTRADOR','RESPONSABLE_SEDE','PROFESOR','FAMILIA')")
	private RolMembresia rol;

	@Column(length = 100)
	private String cargo;

	@Column(nullable = false)
	private Boolean activa = Boolean.TRUE;

	@Column(name = "creada_en", nullable = false, insertable = false, updatable = false)
	private LocalDateTime creadaEn;

	protected Membresia() {
	}

	public Membresia(Long usuarioId, Long organizacionId, RolMembresia rol) {
		this.usuarioId = usuarioId;
		this.organizacionId = organizacionId;
		this.rol = rol;
	}

	public Long getId() {
		return id;
	}

	public Long getUsuarioId() {
		return usuarioId;
	}

	public Long getOrganizacionId() {
		return organizacionId;
	}

	public RolMembresia getRol() {
		return rol;
	}

	public String getCargo() {
		return cargo;
	}

	public void setCargo(String cargo) {
		this.cargo = cargo;
	}

	public boolean isActiva() {
		return Boolean.TRUE.equals(activa);
	}

	public void setActiva(boolean activa) {
		this.activa = activa;
	}

	public LocalDateTime getCreadaEn() {
		return creadaEn;
	}
}
