package com.taemoi.project.entities;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * Sedes de una membresía {@code RESPONSABLE_SEDE} (V31). Solo las membresías RESPONSABLE_SEDE activas aportan
 * sedes al alcance ({@code AlcanceRepository}); sin filas, ninguna sede (falla cerrado). {@code PROFESOR} no usa
 * esta tabla: su alcance va por {@link AsignacionProfesor}. El comentario de V31 dice lo contrario; manda este
 * (decisión del propietario del 16/10/2026).
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
