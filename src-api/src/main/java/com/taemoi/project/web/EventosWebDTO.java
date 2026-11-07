package com.taemoi.project.web;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

/**
 * Respuesta de {@code GET /api/publico/web/eventos} (fase4-web-plan.md §5, pieza W5): los eventos visibles con fecha de
 * la escuela activa. Anónimo: SOLO datos públicos (nunca {@code visible}, {@code orden}, rutas de ficheros ni la sede
 * de otra escuela).
 *
 * @param proximos   fecha de hoy o posterior (zona de la escuela), del más cercano al más lejano; como mucho
 *                   {@value WebPublicaService#EVENTOS_MAXIMO}.
 * @param anteriores fecha anterior a hoy, del más reciente al más antiguo; como mucho
 *                   {@value WebPublicaService#EVENTOS_MAXIMO} (los más recientes).
 */
public record EventosWebDTO(List<Evento> proximos, List<Evento> anteriores) {

	/**
	 * Los campos del evento de {@code GET /api/publico/web} ({@link WebPublicaDTO.Evento}, al mismo nivel) y la
	 * descripción, que pinta la lista.
	 */
	public record Evento(@JsonUnwrapped WebPublicaDTO.Evento evento, String descripcion) {
	}
}
