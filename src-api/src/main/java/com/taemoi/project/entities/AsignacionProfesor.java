package com.taemoi.project.entities;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * Grupo que imparte un profesor (V31), como titular o ayudante.
 *
 * <p>NO es una {@link EntidadOrganizacion}: como {@link Membresia}, se consultará al verificar la sesión, antes
 * de fijar la escuela de la petición (alcance del profesor, fase 3). Por eso TODA consulta debe filtrar
 * explícitamente por {@code organizacion_id} (ver {@code AsignacionProfesorRepository}), y quien la crea fija
 * la organización del grupo.
 */
@Entity
@Table(name = "asignacion_profesor")
@IdClass(AsignacionProfesor.Clave.class)
public class AsignacionProfesor {

	@Id
	@Column(name = "usuario_id", nullable = false, updatable = false)
	private Long usuarioId;

	@Id
	@Column(name = "grupo_id", nullable = false, updatable = false)
	private Long grupoId;

	@Column(name = "organizacion_id", nullable = false, updatable = false)
	private Long organizacionId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, columnDefinition = "ENUM('TITULAR','AYUDANTE')")
	private RolAsignacionProfesor rol;

	protected AsignacionProfesor() {
	}

	public AsignacionProfesor(Long organizacionId, Long usuarioId, Long grupoId, RolAsignacionProfesor rol) {
		this.organizacionId = organizacionId;
		this.usuarioId = usuarioId;
		this.grupoId = grupoId;
		this.rol = rol;
	}

	public Long getUsuarioId() {
		return usuarioId;
	}

	public Long getGrupoId() {
		return grupoId;
	}

	public Long getOrganizacionId() {
		return organizacionId;
	}

	public RolAsignacionProfesor getRol() {
		return rol;
	}

	public void setRol(RolAsignacionProfesor rol) {
		this.rol = rol;
	}

	/** Clave primaria compuesta (usuario_id, grupo_id). */
	public static class Clave implements Serializable {

		private static final long serialVersionUID = 1L;

		private Long usuarioId;
		private Long grupoId;

		public Clave() {
		}

		public Clave(Long usuarioId, Long grupoId) {
			this.usuarioId = usuarioId;
			this.grupoId = grupoId;
		}

		@Override
		public boolean equals(Object otro) {
			return otro instanceof Clave c && Objects.equals(usuarioId, c.usuarioId) && Objects.equals(grupoId, c.grupoId);
		}

		@Override
		public int hashCode() {
			return Objects.hash(usuarioId, grupoId);
		}
	}
}
