package com.taemoi.project.entities;

/**
 * Método con el que se cobró un cargo (pieza 6b-1). {@link #SIN_INDICAR} cuando la petición no lo dice (y en los cargos
 * que nacen pagados).
 */
public enum MetodoPago {
	EFECTIVO,
	TRANSFERENCIA,
	TARJETA,
	BIZUM,
	SIN_INDICAR
}
