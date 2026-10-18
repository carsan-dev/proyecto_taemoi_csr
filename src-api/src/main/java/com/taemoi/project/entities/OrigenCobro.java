package com.taemoi.project.entities;

/** Puerta por la que entró un cobro (pieza 6b-1). */
public enum OrigenCobro {
	/** PUT del cargo y cargos que nacen pagados (asignación, reservas, licencias). */
	TESORERIA,
	/** PUT de la inscripción a una convocatoria, que copia el pago a su cargo. */
	CONVOCATORIA,
	/**
	 * Cobro en efectivo del profesor (pieza 6b-2, {@code POST /api/caja/profesor/cargos/{id}/cobrar}). El único que
	 * queda pendiente de entrega hasta que se registra en una entrega de caja de su sede.
	 */
	PROFESOR
}
