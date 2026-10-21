package com.taemoi.project.dtos.response;

import java.util.List;

/**
 * {@code GET /api/panel/diseno-nuevo} (pieza 8b-I, fase3-e8b-plan.md §3): el valor por defecto de la escuela y el
 * interruptor de cada miembro de gestión. Solo ids y booleanos: la pantalla cruza los ids con {@code GET /api/miembros}
 * para los nombres.
 *
 * @param porDefecto la escuela tiene el diseño nuevo para todo el equipo ({@code panel.diseno_por_defecto = NUEVO}).
 * @param usuarios   la gestión activa de la escuela (PROPIETARIO o ADMINISTRADOR), por id.
 */
public record DisenoPanelDTO(boolean porDefecto, List<Usuario> usuarios) {

	public DisenoPanelDTO {
		usuarios = List.copyOf(usuarios);
	}

	/**
	 * @param permitido el propietario le dio permiso.
	 * @param activo    el diseño que ve, por la regla efectiva (con permiso, su elección; si no, el por defecto).
	 */
	public record Usuario(Long usuarioId, boolean permitido, boolean activo) {
	}

	/** Respuesta de {@code PUT /api/panel/diseno-nuevo/por-defecto}. */
	public record PorDefecto(boolean porDefecto) {
	}

	/**
	 * Respuesta de {@code PUT /api/panel/mi-diseno} y {@code disenoNuevo} de {@code GET /api/auth/permisos}.
	 *
	 * @param activo    la sesión ve el diseño nuevo.
	 * @param permitido la sesión puede encenderlo o apagarlo para sí (con permiso o con el por defecto NUEVO).
	 */
	public record Propio(boolean activo, boolean permitido) {

		public static final Propio APAGADO = new Propio(false, false);
	}
}
