package com.taemoi.project.dtos.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;
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
	 * local de la escuela. Pieza 6c-6: {@code movimientoDevolucionId}, la devolución que cerró el encargo caído (195);
	 * {@code devolucionEnSede}, con la devolución pendiente, quién la registra: {@code true} la sede (el dinero ya está
	 * en su caja), {@code false} el profesor (aún lo lleva encima); sin devolución pendiente, {@code null}. Pieza 6c-8:
	 * {@code estadoCobro}, solo en un encargo con cargo, cómo está ese cargo ({@link EstadoCobroEncargo}); en los demás
	 * tipos no sale en el JSON.
	 */
	public record Solicitud(Long id, TipoSolicitud tipo, EstadoSolicitud estado, Long sedeId, String sedeNombre,
			Long solicitadoPorUsuarioId, String solicitadoPorNombre, LocalDateTime solicitadoEn, String motivo,
			Long resueltoPorUsuarioId, String resueltoPorNombre, LocalDateTime resueltoEn, String respuesta,
			Long cobroId, Long entregaCajaId, Long movimientoCajaId, Long productoAlumnoId, Long productoId,
			Long alumnoId, String alumnoNombre, String alumnoApellidos, String concepto, BigDecimal importe,
			CajaRequests.MotivoDeshacer motivoDeshacer, LocalDateTime encargadoEn, LocalDateTime recibidoEn,
			LocalDateTime entregadoEn, boolean devolucionPendiente, boolean propia, Long movimientoDevolucionId,
			Boolean devolucionEnSede, @JsonInclude(JsonInclude.Include.NON_NULL) EstadoCobroEncargo estadoCobro) {

		/** Sin el estado del cobro (un tipo que no es encargo). */
		public Solicitud(Long id, TipoSolicitud tipo, EstadoSolicitud estado, Long sedeId, String sedeNombre,
				Long solicitadoPorUsuarioId, String solicitadoPorNombre, LocalDateTime solicitadoEn, String motivo,
				Long resueltoPorUsuarioId, String resueltoPorNombre, LocalDateTime resueltoEn, String respuesta,
				Long cobroId, Long entregaCajaId, Long movimientoCajaId, Long productoAlumnoId, Long productoId,
				Long alumnoId, String alumnoNombre, String alumnoApellidos, String concepto, BigDecimal importe,
				CajaRequests.MotivoDeshacer motivoDeshacer, LocalDateTime encargadoEn, LocalDateTime recibidoEn,
				LocalDateTime entregadoEn, boolean devolucionPendiente, boolean propia, Long movimientoDevolucionId,
				Boolean devolucionEnSede) {
			this(id, tipo, estado, sedeId, sedeNombre, solicitadoPorUsuarioId, solicitadoPorNombre, solicitadoEn, motivo,
					resueltoPorUsuarioId, resueltoPorNombre, resueltoEn, respuesta, cobroId, entregaCajaId,
					movimientoCajaId, productoAlumnoId, productoId, alumnoId, alumnoNombre, alumnoApellidos, concepto,
					importe, motivoDeshacer, encargadoEn, recibidoEn, entregadoEn, devolucionPendiente, propia,
					movimientoDevolucionId, devolucionEnSede, null);
		}
	}

	/**
	 * Pieza 6c-8: cómo está el cargo de un encargo. {@code DEVUELTO}: su cobro tiene una devolución activa (153);
	 * {@code CANCELADO}: el cargo se canceló (el encargo cayó sin cobrar); {@code COBRADO}: pagado; {@code PENDIENTE}: por
	 * cobrar. Se mira en ese orden.
	 */
	public enum EstadoCobroEncargo {
		PENDIENTE, COBRADO, DEVUELTO, CANCELADO
	}

	/** Lo que espera acción de la sesión ({@code GET /api/solicitudes/contador}, D6c-4). */
	public record Contador(long total) {
	}
}
