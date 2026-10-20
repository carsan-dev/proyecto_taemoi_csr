package com.taemoi.project.dtos.response;

import com.taemoi.project.entities.RolAsignacionProfesor;

/**
 * Respuestas de {@code /api/grupos/{grupoId}/profesores} (pieza E7-4, decisión 208, D7-8): los profesores de un grupo
 * para la gestión (propietario y gestor).
 */
public final class GrupoProfesoresDTOs {

	private GrupoProfesoresDTOs() {
	}

	/** Un profesor asignado al grupo: la cuenta (id, nombre, apellidos y email) y su rol en el grupo. */
	public record Profesor(Long usuarioId, String nombre, String apellidos, String email, RolAsignacionProfesor rol) {
	}
}
