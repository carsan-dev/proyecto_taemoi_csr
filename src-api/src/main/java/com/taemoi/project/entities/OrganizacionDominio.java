package com.taemoi.project.entities;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Host de una escuela (fase 4, escuela por dominio; V32_4). Tabla GLOBAL, como {@link Organizacion}: sin
 * {@code @TenantId}, porque el filtro la lee para saber la escuela de una petición anónima. El host se guarda
 * normalizado (minúsculas, sin puerto ni punto final). Una escuela puede tener varios; el principal da los enlaces de
 * correo (pieza posterior). Por ahora no hay camino de escritura: las filas llegan por migración.
 */
@Entity
@Table(name = "organizacion_dominio")
public class OrganizacionDominio {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "organizacion_id", nullable = false, updatable = false)
	private Long organizacionId;

	@Column(nullable = false, unique = true, length = 253)
	private String host;

	@Column(nullable = false)
	private Boolean principal = Boolean.FALSE;

	@Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
	private LocalDateTime creadoEn;

	protected OrganizacionDominio() {
	}

	public OrganizacionDominio(Long organizacionId, String host, boolean principal) {
		this.organizacionId = organizacionId;
		this.host = host;
		this.principal = principal;
	}

	public Long getId() {
		return id;
	}

	public Long getOrganizacionId() {
		return organizacionId;
	}

	public String getHost() {
		return host;
	}

	public Boolean getPrincipal() {
		return principal;
	}

	public LocalDateTime getCreadoEn() {
		return creadoEn;
	}
}
