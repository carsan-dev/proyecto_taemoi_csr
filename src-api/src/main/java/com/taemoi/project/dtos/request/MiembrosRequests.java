package com.taemoi.project.dtos.request;

import java.util.List;

/**
 * Cuerpos de la API de miembros (pieza E8-3; D8-6, D8-7, D8-10, D8-11). Los valores se validan en
 * {@code GestionMiembrosService}. {@code cargo}: ausente o {@code null}, se conserva; en blanco, se borra.
 */
public final class MiembrosRequests {

	private MiembrosRequests() {
	}

	/** Un grupo y el rol en él ({@code TITULAR} o {@code AYUDANTE}). */
	public record Asignacion(Long grupoId, String rol) {
	}

	/**
	 * {@code POST /api/miembros}: cuenta existente por email; sin cuenta, con {@code nombre} y {@code apellidos}, la
	 * invitación (pieza E8-4). Con cuenta, {@code nombre} y {@code apellidos} se ignoran.
	 */
	public record Alta(String email, String rol, String cargo, List<Long> sedeIds, List<Asignacion> grupos,
			Boolean reactivar, String nombre, String apellidos) {
	}

	/**
	 * {@code PUT /api/miembros/{usuarioId}/roles/{rol}} (cuerpo opcional; {@code sedeIds} solo para el responsable;
	 * FAMILIA no lleva cargo).
	 */
	public record Rol(String cargo, List<Long> sedeIds) {
	}

	/** {@code PUT /api/miembros/{usuarioId}/sedes}. */
	public record Sedes(List<Long> sedeIds) {
	}

	/** {@code PUT /api/miembros/{usuarioId}/grupos}: el conjunto completo. */
	public record Grupos(List<Asignacion> asignaciones) {
	}
}
