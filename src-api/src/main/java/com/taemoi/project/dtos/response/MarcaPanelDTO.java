package com.taemoi.project.dtos.response;

import com.taemoi.project.marca.TemaEscuela;

/**
 * Respuesta de {@code GET /api/escuela/marca} (pieza 8b-T, fase3-e8b-plan.md §4): la marca de la escuela ACTIVA para
 * la cabecera del panel nuevo. Sin datos personales.
 *
 * @param nombre      nombre de la escuela.
 * @param logo        de dónde sale el logo.
 * @param logoVersion id de la imagen del logo subido (para la URL de {@code GET /api/escuela/logo} y su caché);
 *                    {@code null} sin logo subido.
 * @param tema        colores del panel derivados del color guardado ({@code #1A2230} sin color).
 */
public record MarcaPanelDTO(String nombre, Logo logo, Long logoVersion, TemaEscuela tema) {

	public enum Logo {
		/** Logo subido por el superadmin: {@code GET /api/escuela/logo}. */
		SUBIDO,
		/** Moiskimdo sin logo subido: el SVG de siempre de {@code assets}. */
		LEGADO,
		/** Sin logo: solo el nombre. */
		NINGUNO
	}
}
