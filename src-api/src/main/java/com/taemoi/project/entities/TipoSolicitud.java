package com.taemoi.project.entities;

/**
 * Tipo de una {@link Solicitud} (decisión 188, fase3-6c-solicitudes.md §6.1). Los tipos 1 a 3 los resuelve el
 * propietario; el encargo (tipo 4) lo atiende quien gestiona la sede.
 */
public enum TipoSolicitud {
	/** Tipo 1: revertir un cobro (o, sin cobro, desmarcar un cargo pagado antes de la 6b-1: decisión 191). */
	REVERTIR_COBRO,
	/** Tipo 2: deshacer un cobro del profesor ya entregado a la sede (decisión 193). */
	DESHACER_COBRO_ENTREGADO,
	/** Tipo 3: anular una entrega de caja. */
	ANULAR_ENTREGA,
	/** Tipo 3: anular un movimiento de caja suelto. */
	ANULAR_MOVIMIENTO,
	/** Tipo 4: encargo de un producto del catálogo para un alumno (decisión 183). */
	ENCARGO
}
