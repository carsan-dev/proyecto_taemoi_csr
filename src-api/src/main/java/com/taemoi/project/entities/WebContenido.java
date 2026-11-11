package com.taemoi.project.entities;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Versión del contenido de la web de una escuela (V32_9, pieza W12a, d522): el editor la lee y la devuelve al guardar;
 * si otra persona guardó antes, no se escribe nada (409). Sin fila, la versión es 0 (la escuela aún no ha guardado). Una
 * fila por escuela, con la organización como clave, como {@link WebEscuela}; aparte de ella porque el contenido se
 * guarda también antes de publicar ({@code web_escuela.tema} es obligatorio).
 *
 * <p>No lleva {@code @TenantId}: la clave ES la organización y se lee siempre por la escuela activa.
 */
@Entity
@Table(name = "web_contenido")
public class WebContenido {

	@Id
	@Column(name = "organizacion_id")
	private Long organizacionId;

	@Column(nullable = false)
	private Long version = 0L;

	@Column(name = "guardado_en")
	private LocalDateTime guardadoEn;

	/** Id del usuario que guardó; sin clave ajena, como {@code web_escuela.publicada_por}. */
	@Column(name = "guardado_por")
	private Long guardadoPor;

	public Long getOrganizacionId() {
		return organizacionId;
	}

	public void setOrganizacionId(Long organizacionId) {
		this.organizacionId = organizacionId;
	}

	public Long getVersion() {
		return version;
	}

	public void setVersion(Long version) {
		this.version = version;
	}

	public LocalDateTime getGuardadoEn() {
		return guardadoEn;
	}

	public void setGuardadoEn(LocalDateTime guardadoEn) {
		this.guardadoEn = guardadoEn;
	}

	public Long getGuardadoPor() {
		return guardadoPor;
	}

	public void setGuardadoPor(Long guardadoPor) {
		this.guardadoPor = guardadoPor;
	}
}
