package com.taemoi.project.services;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import com.taemoi.project.entities.EstadoSolicitud;
import com.taemoi.project.entities.Solicitud;
import com.taemoi.project.entities.TipoSolicitud;

/**
 * Pieza 6c-2: la tabla de transiciones de las solicitudes (fase3-6c-solicitudes.md §6.2) en UN solo sitio. Quién puede
 * hacer cada una y sus efectos los comprueba el servicio; aquí solo si el cambio de estado existe. Se llama con la fila
 * de la solicitud bloqueada ({@code SolicitudRepository#bloquearPorId}) y releída: así, de dos clics a la vez, uno gana
 * y el otro recibe 409 {@value #NO_PENDIENTE}.
 *
 * <ul>
 * <li>Tipos 1 a 3: PENDIENTE → RETIRADA, DENEGADA, ACEPTADA o NO_APLICABLE.</li>
 * <li>Encargo: PEDIDO → RETIRADA, ENCARGADO o RECHAZADO; ENCARGADO → RECIBIDO o RECHAZADO; RECIBIDO → ENTREGADO o
 * RECHAZADO. La devolución de un encargo caído (195) no cambia el estado: no pasa por aquí.</li>
 * </ul>
 */
public final class SolicitudTransiciones {

	/** 409 de cualquier transición que ya no es posible (otra persona la resolvió antes, o no existe). */
	public static final String NO_PENDIENTE = "La solicitud ya no está pendiente";

	private static final Map<EstadoSolicitud, Set<EstadoSolicitud>> TIPOS_1_A_3 = new EnumMap<>(EstadoSolicitud.class);
	private static final Map<EstadoSolicitud, Set<EstadoSolicitud>> ENCARGO = new EnumMap<>(EstadoSolicitud.class);

	static {
		TIPOS_1_A_3.put(EstadoSolicitud.PENDIENTE, EnumSet.of(EstadoSolicitud.RETIRADA, EstadoSolicitud.DENEGADA,
				EstadoSolicitud.ACEPTADA, EstadoSolicitud.NO_APLICABLE));
		ENCARGO.put(EstadoSolicitud.PEDIDO,
				EnumSet.of(EstadoSolicitud.RETIRADA, EstadoSolicitud.ENCARGADO, EstadoSolicitud.RECHAZADO));
		ENCARGO.put(EstadoSolicitud.ENCARGADO, EnumSet.of(EstadoSolicitud.RECIBIDO, EstadoSolicitud.RECHAZADO));
		ENCARGO.put(EstadoSolicitud.RECIBIDO, EnumSet.of(EstadoSolicitud.ENTREGADO, EstadoSolicitud.RECHAZADO));
	}

	private SolicitudTransiciones() {
	}

	/** Estado con el que nace una solicitud del tipo dado. */
	public static EstadoSolicitud estadoInicial(TipoSolicitud tipo) {
		return tipo == TipoSolicitud.ENCARGO ? EstadoSolicitud.PEDIDO : EstadoSolicitud.PENDIENTE;
	}

	/** Si la tabla admite pasar de {@code desde} a {@code destino} en ese tipo. */
	public static boolean permitida(TipoSolicitud tipo, EstadoSolicitud desde, EstadoSolicitud destino) {
		if (tipo == null || desde == null || destino == null) {
			return false;
		}
		Map<EstadoSolicitud, Set<EstadoSolicitud>> tabla = tipo == TipoSolicitud.ENCARGO ? ENCARGO : TIPOS_1_A_3;
		return tabla.getOrDefault(desde, Set.of()).contains(destino);
	}

	/**
	 * Exige que la solicitud (bloqueada y releída) pueda pasar a {@code destino}; si no, 409 {@value #NO_PENDIENTE}. No
	 * cambia el estado: lo hace quien llama, junto con los demás campos de la resolución.
	 */
	public static void exigir(Solicitud solicitud, EstadoSolicitud destino) {
		if (solicitud == null || !permitida(solicitud.getTipo(), solicitud.getEstado(), destino)) {
			throw new IllegalStateException(NO_PENDIENTE);
		}
	}
}
