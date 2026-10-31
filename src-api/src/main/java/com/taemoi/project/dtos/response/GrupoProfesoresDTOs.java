package com.taemoi.project.dtos.response;

import com.taemoi.project.entities.RolAsignacionProfesor;

/**
 * Respuestas de {@code /api/grupos/{grupoId}/profesores} (pieza E7-4, decisión 208, D7-8): los profesores de un grupo
 * para la gestión (propietario y gestor).
 */
public final class GrupoProfesoresDTOs {

	private GrupoProfesoresDTOs() {
	}

	/**
	 * Un profesor asignado al grupo: la cuenta (id, nombre, apellidos y email), su rol en el grupo y (pieza E8-4) si su
	 * invitación sigue pendiente (cuenta LOCAL sin contraseña, con algún rol activo en esta escuela), para el chip
	 * «Invitación pendiente».
	 */
	public record Profesor(Long usuarioId, String nombre, String apellidos, String email, RolAsignacionProfesor rol,
			boolean invitacionPendiente) {
	}
}
