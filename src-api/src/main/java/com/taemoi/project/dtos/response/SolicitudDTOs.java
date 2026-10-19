package com.taemoi.project.dtos.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.taemoi.project.dtos.request.CajaRequests;
import com.taemoi.project.entities.EstadoSolicitud;
import com.taemoi.project.entities.TipoSolicitud;

/** Respuestas de la API de solicitudes (pieza 6c-2, {@code /api/solicitudes}). Importes con dos decimales. */
public final class SolicitudDTOs {

	private SolicitudDTOs() {
	}

	/**
	 * Una solicitud o un encargo, con sus instantáneas (sede, alumno, concepto e importe al pedir) y los nombres para la
	 * bandeja. {@code propia}: la pidió la cuenta de la sesión (puede retirarla mientras esté pendiente). Fechas en hora
	 * local de la escuela.
	 */
	public record Solicitud(Long id, TipoSolicitud tipo, EstadoSolicitud estado, Long sedeId, String sedeNombre,
			Long solicitadoPorUsuarioId, String solicitadoPorNombre, LocalDateTime solicitadoEn, String motivo,
			Long resueltoPorUsuarioId, String resueltoPorNombre, LocalDateTime resueltoEn, String respuesta,
			Long cobroId, Long entregaCajaId, Long movimientoCajaId, Long productoAlumnoId, Long productoId,
			Long alumnoId, String alumnoNombre, String alumnoApellidos, String concepto, BigDecimal importe,
			CajaRequests.MotivoDeshacer motivoDeshacer, LocalDateTime encargadoEn, LocalDateTime recibidoEn,
			LocalDateTime entregadoEn, boolean devolucionPendiente, boolean propia) {
	}

	/** Lo que espera acción de la sesión ({@code GET /api/solicitudes/contador}, D6c-4). */
	public record Contador(long total) {
	}
}
