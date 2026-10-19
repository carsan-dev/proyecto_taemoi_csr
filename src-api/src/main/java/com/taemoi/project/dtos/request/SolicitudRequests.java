package com.taemoi.project.dtos.request;

import com.taemoi.project.entities.TipoSolicitud;

/** Peticiones de la API de solicitudes (pieza 6c-2, {@code /api/solicitudes}). */
public final class SolicitudRequests {

	private SolicitudRequests() {
	}

	/**
	 * Pedir al propietario: {@code POST /api/solicitudes}. Un solo recurso, el de su tipo:
	 * <ul>
	 * <li>{@code REVERTIR_COBRO}: {@code cobroId} o {@code productoAlumnoId}. Con el cargo, si tiene cobro ACTIVO se
	 * enlaza ese cobro; si no, es un cargo pagado antes de la 6b-1 (decisión 191).</li>
	 * <li>{@code ANULAR_ENTREGA}: {@code entregaId}.</li>
	 * <li>{@code ANULAR_MOVIMIENTO}: {@code movimientoId}.</li>
	 * </ul>
	 * El tipo 2 y los encargos se piden por las rutas del profesor (6c-4 y 6c-5). Motivo obligatorio, máximo 500.
	 */
	public record Crear(TipoSolicitud tipo, Long cobroId, Long productoAlumnoId, Long entregaId, Long movimientoId,
			String motivo) {
	}

	/** Denegar (solo el propietario): {@code POST /api/solicitudes/{id}/denegar}; respuesta obligatoria (D6c-1). */
	public record Denegar(String respuesta) {
	}
}
