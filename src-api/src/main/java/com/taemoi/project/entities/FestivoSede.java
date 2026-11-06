package com.taemoi.project.entities;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * Una sede de un {@link Festivo} (tabla {@code festivo_sede} de V32_2). Pertenece a la escuela activa ({@code @TenantId}),
 * como el festivo. Ids simples; borrar el festivo o la sede se lleva la fila (ON DELETE CASCADE).
 */
@Entity
@Table(name = "festivo_sede")
@IdClass(FestivoSede.Clave.class)
public class FestivoSede extends EntidadOrganizacion {

	@Id
	@Column(name = "festivo_id", nullable = false, updatable = false)
	private Long festivoId;

	@Id
	@Column(name = "sede_id", nullable = false, updatable = false)
	private Long sedeId;

	protected FestivoSede() {
	}

	public FestivoSede(Long festivoId, Long sedeId) {
		this.festivoId = festivoId;
		this.sedeId = sedeId;
	}

	public Long getFestivoId() {
		return festivoId;
	}

	public Long getSedeId() {
		return sedeId;
	}

	/** Clave primaria compuesta (festivo_id, sede_id). */
	public static class Clave implements Serializable {

		private static final long serialVersionUID = 1L;

		private Long festivoId;
		private Long sedeId;

		public Clave() {
		}

		public Clave(Long festivoId, Long sedeId) {
			this.festivoId = festivoId;
			this.sedeId = sedeId;
		}

		@Override
		public boolean equals(Object otro) {
			return otro instanceof Clave clave && Objects.equals(festivoId, clave.festivoId)
					&& Objects.equals(sedeId, clave.sedeId);
		}

		@Override
		public int hashCode() {
			return Objects.hash(festivoId, sedeId);
		}
	}
}
