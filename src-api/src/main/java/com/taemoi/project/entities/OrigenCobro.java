package com.taemoi.project.entities;

/** Puerta por la que entró un cobro (pieza 6b-1). */
public enum OrigenCobro {
	/** PUT del cargo y cargos que nacen pagados (asignación, reservas, licencias). */
	TESORERIA,
	/** PUT de la inscripción a una convocatoria, que copia el pago a su cargo. */
	CONVOCATORIA,
	/** Cobro en efectivo del profesor (se usará en la 6b-2). */
	PROFESOR
}
