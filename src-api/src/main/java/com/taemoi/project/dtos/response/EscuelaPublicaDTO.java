package com.taemoi.project.dtos.response;

import com.taemoi.project.marca.TemaEscuela;

/**
 * Respuesta de {@code GET /api/publico/escuela} (fase 4, pieza 4): la marca pública de la escuela del Host. Solo estos
 * cuatro campos; nunca datos privados de la escuela.
 *
 * @param nombre      nombre de la escuela.
 * @param logo        de dónde sale el logo ({@code SUBIDO}: {@code GET /api/publico/escuela/logo}).
 * @param logoVersion id de la imagen del logo subido (para la caché); {@code null} sin logo subido.
 * @param tema        colores derivados del color guardado.
 */
public record EscuelaPublicaDTO(String nombre, MarcaPanelDTO.Logo logo, Long logoVersion, TemaEscuela tema) {

	public static EscuelaPublicaDTO de(MarcaPanelDTO marca) {
		return new EscuelaPublicaDTO(marca.nombre(), marca.logo(), marca.logoVersion(), marca.tema());
	}
}
