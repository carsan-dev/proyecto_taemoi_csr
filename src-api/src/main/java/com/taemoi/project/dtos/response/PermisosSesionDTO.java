package com.taemoi.project.dtos.response;

import java.util.List;

/**
 * Respuesta de {@code GET /api/auth/permisos} (pieza E8-1, D8-14): la fuente única de menús y guards del frontend. La
 * autorización real sigue en el servidor; esto solo dice qué enseñar.
 *
 * @param roles             los {@code RolMembresia} de la sesión en la escuela activa, por el orden del enum; en la
 *                          sesión solo-profesor (también R+Pr), solo {@code PROFESOR}; en la solo-plataforma, ninguno.
 * @param esAdminPlataforma si la sesión lleva {@code ROLE_ADMIN_PLATAFORMA}.
 * @param sedes             sedes visibles: con gestión, todas las de la escuela (también inactivas); el responsable,
 *                          las suyas; el resto, ninguna. Por id.
 * @param variasSedes       la escuela tiene más de una sede activa.
 * @param tieneGrupos       la sesión tiene grupos asignados como profesor ({@code alcance.grupos()}).
 * @param capacidades       por el orden de {@link Capacidad}.
 * @param nombreVisible     pieza 8b-T: «Nombre A.» de la cuenta de la sesión (nombre e inicial del primer apellido),
 *                          para el botón de cuenta; {@code null} sin nombre. Solo el propio.
 * @param cumpleanos     pieza 8b-M2: hoy (Europe/Madrid) es el cumpleaños de la ficha de alumno PROPIA de la cuenta en
 *                          la escuela activa (29-feb → 28-feb en años no bisiestos). Solo el booleano, nunca la fecha.
 */
public record PermisosSesionDTO(List<String> roles, boolean esAdminPlataforma, List<Sede> sedes, boolean variasSedes,
		boolean tieneGrupos, List<Capacidad> capacidades, String nombreVisible,
		boolean cumpleanos) {

	public PermisosSesionDTO {
		roles = List.copyOf(roles);
		sedes = List.copyOf(sedes);
		capacidades = List.copyOf(capacidades);
	}

	public record Sede(Long id, String nombre, boolean activa) {
	}

	/** Qué puede ver la sesión (D8-14). Los nombres son el contrato con el frontend (E8-5). */
	public enum Capacidad {
		/** Miembros de la escuela: propietario y gestor. */
		MIEMBROS,
		/** Dar o quitar PROPIETARIO o ADMINISTRADOR: solo el propietario (D8). */
		DAR_GESTION,
		/** Sedes de la escuela: propietario y gestor. */
		SEDES,
		/** Asignar profesores a grupos: propietario y gestor. */
		ASIGNAR_PROFESORES,
		/** «Mis grupos»: la sesión solo-profesor siempre; con PROFESOR fuera de ella, solo con grupos (217). */
		MIS_GRUPOS,
		/** «Mi caja»: cualquier sesión con {@code ROLE_PROFESOR}. */
		MI_CAJA
	}
}
