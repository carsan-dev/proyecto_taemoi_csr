package com.taemoi.project.entities;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * Sedes a las que se limita una membresía {@code RESPONSABLE_SEDE} o {@code PROFESOR} (V31). Sin filas = todas
 * las sedes de la escuela.
 *
 * <p>NO es una {@link EntidadOrganizacion} (la tabla no lleva {@code organizacion_id}): como {@link Membresia},
 * se consulta siempre por membresía (ver {@code MembresiaSedeRepository}).
 */
@Entity
@Table(name = "membresia_sede")
@IdClass(MembresiaSede.Clave.class)
public class MembresiaSede {

	@Id
	@Column(name = "membresia_id", nullable = false, updatable = false)
	private Long membresiaId;

	@Id
	@Column(name = "sede_id", nullable = false, updatable = false)
	private Long sedeId;

	protected MembresiaSede() {
	}

	public MembresiaSede(Long membresiaId, Long sedeId) {
		this.membresiaId = membresiaId;
		this.sedeId = sedeId;
	}

	public Long getMembresiaId() {
		return membresiaId;
	}

	public Long getSedeId() {
		return sedeId;
	}

	/** Clave primaria compuesta (membresia_id, sede_id). */
	public static class Clave implements Serializable {

		private static final long serialVersionUID = 1L;

		private Long membresiaId;
		private Long sedeId;

		public Clave() {
		}

		public Clave(Long membresiaId, Long sedeId) {
			this.membresiaId = membresiaId;
			this.sedeId = sedeId;
		}

		@Override
		public boolean equals(Object otro) {
			return otro instanceof Clave c && Objects.equals(membresiaId, c.membresiaId) && Objects.equals(sedeId, c.sedeId);
		}

		@Override
		public int hashCode() {
			return Objects.hash(membresiaId, sedeId);
		}
	}
}
