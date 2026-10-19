package com.taemoi.project.dtos.request;

import java.util.List;

/**
 * Pieza 6b-3c (decisión 185): marcar o desmarcar «Lo puede vender el profesor» ({@code producto.vendible_profesor})
 * desde el listado de productos, sin reenviar la ficha. Lo valida el servicio (400 con {@code {"mensaje"}}).
 */
public final class VendibleProfesorRequests {

	private VendibleProfesorRequests() {
	}

	/** {@code PATCH /api/productos/{id}/vendible-profesor}: marca obligatoria. */
	public record Uno(Boolean vendibleProfesor) {
	}

	/**
	 * {@code PATCH /api/productos/vendible-profesor}: ids obligatorios (sin nulos, se deduplican, hasta
	 * {@code ProductoService#MAX_IDS_VENDIBLE_PROFESOR} distintos) y marca obligatoria.
	 */
	public record Bloque(List<Long> ids, Boolean vendibleProfesor) {
	}
}
