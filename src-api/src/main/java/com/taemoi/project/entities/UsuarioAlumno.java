package com.taemoi.project.entities;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * Ficha de alumno que ve una cuenta (V31). Sustituirá a {@code usuario.alumno_id} y a la comprobación por email
 * del acceso de familia (fase 3, entrega 2). Desde la entrega 2a la lee el acceso de familia
 * ({@code AlumnoRepository.ACCESO_FAMILIA} vía {@code AccesoFamiliaService}); la rellena V31 y, desde la entrega
 * 2b, la escriben el registro y el primer login con Google ({@code VinculoFamiliaService}, origen REGISTRO).
 *
 * <p>{@code organizacion_id} es la del alumno (desnormalizada). NO es una {@link EntidadOrganizacion}: como
 * {@link Membresia}, el inicio de sesión la consultará antes de fijar la escuela. Por eso TODA consulta debe
 * filtrar explícitamente por organización (ver {@code UsuarioAlumnoRepository}).
 */
@Entity
@Table(name = "usuario_alumno")
@IdClass(UsuarioAlumno.Clave.class)
public class UsuarioAlumno {

	@Id
	@Column(name = "usuario_id", nullable = false, updatable = false)
	private Long usuarioId;

	@Id
	@Column(name = "alumno_id", nullable = false, updatable = false)
	private Long alumnoId;

	@Column(name = "organizacion_id", nullable = false, updatable = false)
	private Long organizacionId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, columnDefinition = "ENUM('PROPIO','TUTOR')")
	private RelacionUsuarioAlumno relacion;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, columnDefinition = "ENUM('MIGRACION','REGISTRO','MANUAL')")
	private OrigenUsuarioAlumno origen;

	@Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
	private LocalDateTime creadoEn;

	protected UsuarioAlumno() {
	}

	public UsuarioAlumno(Long usuarioId, Long alumnoId, Long organizacionId, RelacionUsuarioAlumno relacion,
			OrigenUsuarioAlumno origen) {
		this.usuarioId = usuarioId;
		this.alumnoId = alumnoId;
		this.organizacionId = organizacionId;
		this.relacion = relacion;
		this.origen = origen;
	}

	public Long getUsuarioId() {
		return usuarioId;
	}

	public Long getAlumnoId() {
		return alumnoId;
	}

	public Long getOrganizacionId() {
		return organizacionId;
	}

	public RelacionUsuarioAlumno getRelacion() {
		return relacion;
	}

	public void setRelacion(RelacionUsuarioAlumno relacion) {
		this.relacion = relacion;
	}

	public OrigenUsuarioAlumno getOrigen() {
		return origen;
	}

	public LocalDateTime getCreadoEn() {
		return creadoEn;
	}

	/** Clave primaria compuesta (usuario_id, alumno_id). */
	public static class Clave implements Serializable {

		private static final long serialVersionUID = 1L;

		private Long usuarioId;
		private Long alumnoId;

		public Clave() {
		}

		public Clave(Long usuarioId, Long alumnoId) {
			this.usuarioId = usuarioId;
			this.alumnoId = alumnoId;
		}

		@Override
		public boolean equals(Object otro) {
			return otro instanceof Clave c && Objects.equals(usuarioId, c.usuarioId) && Objects.equals(alumnoId, c.alumnoId);
		}

		@Override
		public int hashCode() {
			return Objects.hash(usuarioId, alumnoId);
		}
	}
}
