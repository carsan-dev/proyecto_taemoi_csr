package com.taemoi.project.dtos.response;

import java.util.List;

/**
 * Respuesta de {@code GET /api/auth/permisos} (pieza E8-1, D8-14): la fuente única de menús y guards del frontend. La
 * autorización real sigue en el servidor; esto solo dice qué enseñar.
 *
 * @param roles             los {@code RolMembresia} de la sesión en la escuela activa, por el orden del enum; en la
 *                          solo-plataforma, ninguno.
 * @param esAdminPlataforma si la sesión lleva {@code ROLE_ADMIN_PLATAFORMA}.
 * @param sedes             sedes visibles: con gestión, todas las de la escuela (también inactivas); el responsable,
 *                          las suyas; el resto, ninguna. Por id.
 * @param variasSedes       la escuela tiene más de una sede activa.
 * @param tieneGrupos       la sesión tiene grupos asignados como profesor ({@code alcance.grupos()}).
 * @param capacidades       por el orden de {@link Capacidad}.
 * @param nombreVisible     pieza 8b-T: «Nombre A.» de la cuenta de la sesión (nombre e inicial del primer apellido),
 *                          para el botón de cuenta; con nombre preferido (decisión 322), ese tal cual; {@code null} sin
 *                          nombre. Solo el propio.
 * @param nombreSaludo      decisión 322: el nombre preferido entero; si no hay, la primera palabra del nombre;
 *                          {@code null} sin nombre. Solo el propio.
 * @param cumpleanos     pieza 8b-M2: hoy (Europe/Madrid) es el cumpleaños de la cuenta (29-feb → 28-feb en años no
 *                          bisiestos): el de su ficha de alumno PROPIA en la escuela activa, o el guardado en la cuenta
 *                          si no hay ficha válida (decisión 322). Solo el booleano, nunca la fecha.
 */
public record PermisosSesionDTO(List<String> roles, boolean esAdminPlataforma, List<Sede> sedes, boolean variasSedes,
		boolean tieneGrupos, List<Capacidad> capacidades, String nombreVisible, String nombreSaludo,
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
		/**
		 * «Mis grupos»: la sesión solo-profesor y responsable + profesor sin gestión (371), siempre; con PROFESOR fuera
		 * de esas, solo con grupos (217).
		 */
		MIS_GRUPOS,
		/** «Mi caja»: cualquier sesión con {@code ROLE_PROFESOR}. */
		MI_CAJA,
		/**
		 * Panel limitado a sus sedes (decisión 370, las de {@code sedes}): {@code ROLE_RESPONSABLE_SEDE} sin gestión,
		 * solo o con PROFESOR o FAMILIA. Con gestión no sale: esa sesión es de gestión, con alcance total.
		 */
		PANEL_SEDE,
		/** Elige entre su panel de sede y la vista de familia (decisión 371): responsable + familia sin gestión. */
		ELEGIR_VISTA
	}
}
