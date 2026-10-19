package com.taemoi.project.dtos.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Respuestas de la API de caja (pieza 6b-2, {@code /api/caja}). Importes con dos decimales.
 *
 * <p>Decisión 69 del propietario (ajustada): las rutas del PROFESOR devuelven filas ({@link CargoPendienteProfesor},
 * {@link CobroCaja}) y, como único agregado, SUS propios totales de lo pendiente de entregar
 * ({@link MisCobrosPendientes}); nunca totales de sede, caja, escuela ni de otros profesores, que son de la caja de
 * sede ({@link ResumenCajaSede}, {@link PendienteCobrador}).
 */
public final class CajaDTOs {

	private CajaDTOs() {
	}

	/** Un cargo pendiente (no pagado) de un alumno de los grupos del profesor. */
	public record CargoPendienteProfesor(Long cargoId, Long alumnoId, String alumnoNombre, String alumnoApellidos,
			String concepto, BigDecimal importe, Date fechaAsignacion, Long sedeId, String sedeNombre) {
	}

	/**
	 * Un cobro (fila): del profesor, o de la lista de pendientes de entrega de una sede. {@code venta} (pieza 6b-3b-1):
	 * el cobro es la venta del catálogo de quien lo cobró (apuntó y cobró el producto él mismo), la condición que hace
	 * válido deshacerlo con NO_HUBO_VENTA; no depende de que el producto siga marcado como vendible.
	 */
	public record CobroCaja(Long cobroId, Long cargoId, Long alumnoId, String alumnoNombre, String alumnoApellidos,
			String concepto, BigDecimal importe, Date fechaCobro, Long sedeId, boolean venta) {
	}

	/**
	 * Pieza 6b-3b-1: un alumno ACTIVO de los grupos del profesor de la sesión (para «Vender» en «Mi caja»).
	 * {@code grupos}: los nombres de SUS grupos (los del profesor) en los que está el alumno, por nombre. Sin datos
	 * sensibles (ni salud, ni DNI, ni contacto).
	 */
	public record AlumnoDeMisGrupos(Long alumnoId, String nombre, String apellidos, List<String> grupos) {
	}

	/** Un producto del catálogo que el profesor puede apuntar y cobrar (lista blanca de la 6b-2). */
	public record ProductoVendible(Long id, String concepto, BigDecimal precio) {
	}

	/**
	 * Pieza 6c-4 (D6c-14): un cobro del profesor de la sesión ya entregado a la sede en los últimos 30 días, para
	 * «Entregados recientes» de Mi caja. {@code venta}: admite deshacerlo como NO_HUBO_VENTA (como en {@link CobroCaja}).
	 * {@code solicitudPendienteId}: su solicitud de deshacer PENDIENTE (para retirarla), o {@code null}.
	 * {@code noSePuedePedir}: por qué ya no se puede pedir deshacerlo (devolución registrada o pendiente, otra solicitud
	 * pendiente, el cargo ya no existe o ya no está pagado), o {@code null} si se puede. Sin totales.
	 */
	public record CobroEntregadoReciente(Long cobroId, Long cargoId, Long alumnoId, String alumnoNombre,
			String alumnoApellidos, String concepto, BigDecimal importe, Date fechaCobro, Long entregaId,
			LocalDateTime fechaEntrega, Long sedeId, String sedeNombre, boolean venta, Long solicitudPendienteId,
			String noSePuedePedir) {
	}

	/** Lo que el profesor de la sesión lleva pendiente de entregar: sus cobros, cuántos y cuánto suman. */
	public record MisCobrosPendientes(int numeroCobros, BigDecimal importe, List<CobroCaja> cobros) {
	}

	/**
	 * Resumen de la caja de una sede para el período [{@code desde}, {@code hasta}] (fechas locales de Europe/Madrid,
	 * ambas incluidas).
	 *
	 * @param pendienteDeEntregar  Σ cobros ACTIVOS de origen PROFESOR sin entrega (estado actual, sin período)
	 * @param recibidoEnPeriodo    Σ importe contado de las entregas ACTIVAS con fecha en el período
	 * @param diferenciasEnPeriodo Σ movimientos SOBRANTE y FALTANTE ACTIVOS con fecha en el período
	 * @param saldoCaja            Σ cobros ACTIVOS en EFECTIVO de la sede que no están pendientes de entrega (origen
	 *                             distinto de PROFESOR, o ya en una entrega) + Σ movimientos ACTIVOS (todos los tipos);
	 *                             estado actual, sin período
	 */
	public record ResumenCajaSede(Long sedeId, LocalDate desde, LocalDate hasta, BigDecimal pendienteDeEntregar,
			BigDecimal recibidoEnPeriodo, BigDecimal diferenciasEnPeriodo, BigDecimal saldoCaja) {
	}

	/** Lo pendiente de entregar de un cobrador (profesor) en una sede. */
	public record PendienteCobrador(Long usuarioId, String nombre, int numeroCobros, BigDecimal importe,
			Date cobroMasAntiguo, List<CobroCaja> cobros) {
	}

	/**
	 * Una entrega de caja. {@code diferencia} = contado − declarado. {@code cobroIds}: los cobros que la componen
	 * (vacía si está ANULADA: sus cobros volvieron a pendientes). {@code movimientoId}: su SOBRANTE o FALTANTE
	 * automático, si lo tiene. {@code autoentrega}: quien entrega es quien recibe (responsable de la sede que además es
	 * profesor; permitido por decisión del propietario). Pieza 6c-6: {@code movimientosDeCobro}, los demás movimientos
	 * de la entrega enlazados a un cobro (el ERROR +importe de un cobro deshecho ya entregado, decisión 193, y la
	 * DEVOLUCION de la devolución del profesor, D6c-10), en cualquier estado, para que «declarado» cuadre a la vista.
	 * Solo sale en el JSON si hay alguno: la respuesta de una entrega de siempre no cambia.
	 */
	public record EntregaCaja(Long id, Long sedeId, Long entregadoPorUsuarioId, String entregadoPorNombre,
			Long recibidoPorUsuarioId, String recibidoPorNombre, BigDecimal importeDeclarado, BigDecimal importeContado,
			BigDecimal diferencia, LocalDateTime fecha, String nota, String estado, List<Long> cobroIds,
			Long movimientoId, Long anuladaPorUsuarioId, LocalDateTime anuladaEn, String motivoAnulacion,
			boolean autoentrega,
			@JsonInclude(JsonInclude.Include.NON_EMPTY) List<MovimientoDeEntrega> movimientosDeCobro) {
	}

	/** Pieza 6c-6: un movimiento de una entrega enlazado a un cobro (ERROR de la 193 o DEVOLUCION de la D6c-10). */
	public record MovimientoDeEntrega(Long id, String tipo, BigDecimal importe, Long cobroId, String estado) {
	}

	/**
	 * Un movimiento de caja (importe con signo). {@code entregaCajaId}: si es el automático de una entrega. Pieza 6c-6:
	 * {@code cobroId} y {@code productoAlumnoId}, el cobro y el cargo de una devolución enlazada (153) o del ERROR de la
	 * 193; {@code metodo}, el del cobro (D6c-13: el saldo suma solo los EFECTIVO). Los tres solo salen en el JSON en un
	 * movimiento enlazado a un cobro: la respuesta de un movimiento de siempre no cambia.
	 */
	public record MovimientoCaja(Long id, Long sedeId, String tipo, BigDecimal importe, Long alumnoId,
			Long entregaCajaId, String nota, Long creadoPorUsuarioId, LocalDateTime fecha, String estado,
			Long anuladoPorUsuarioId, LocalDateTime anuladoEn, String motivoAnulacion,
			@JsonInclude(JsonInclude.Include.NON_NULL) Long cobroId,
			@JsonInclude(JsonInclude.Include.NON_NULL) Long productoAlumnoId,
			@JsonInclude(JsonInclude.Include.NON_NULL) String metodo) {
	}
}
