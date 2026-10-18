package com.taemoi.project.dtos.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

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

	/** Un cobro (fila): del profesor, o de la lista de pendientes de entrega de una sede. */
	public record CobroCaja(Long cobroId, Long cargoId, Long alumnoId, String alumnoNombre, String alumnoApellidos,
			String concepto, BigDecimal importe, Date fechaCobro, Long sedeId) {
	}

	/** Un producto del catálogo que el profesor puede apuntar y cobrar (lista blanca de la 6b-2). */
	public record ProductoVendible(Long id, String concepto, BigDecimal precio) {
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
	 * profesor; permitido por decisión del propietario).
	 */
	public record EntregaCaja(Long id, Long sedeId, Long entregadoPorUsuarioId, String entregadoPorNombre,
			Long recibidoPorUsuarioId, String recibidoPorNombre, BigDecimal importeDeclarado, BigDecimal importeContado,
			BigDecimal diferencia, LocalDateTime fecha, String nota, String estado, List<Long> cobroIds,
			Long movimientoId, Long anuladaPorUsuarioId, LocalDateTime anuladaEn, String motivoAnulacion,
			boolean autoentrega) {
	}

	/** Un movimiento de caja (importe con signo). {@code entregaCajaId}: si es el automático de una entrega. */
	public record MovimientoCaja(Long id, Long sedeId, String tipo, BigDecimal importe, Long alumnoId,
			Long entregaCajaId, String nota, Long creadoPorUsuarioId, LocalDateTime fecha, String estado,
			Long anuladoPorUsuarioId, LocalDateTime anuladoEn, String motivoAnulacion) {
	}
}
