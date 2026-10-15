package com.taemoi.project.dtos.response;

import java.util.List;

/**
 * Catálogo público ({@code GET /api/catalogo/publico}): solo las disciplinas activas de la organización de la
 * petición, sin precios ni grados.
 */
public record CatalogoPublicoDTO(List<Disciplina> disciplinas) {

	public record Disciplina(String codigo, String nombre, Integer orden) {
	}
}
