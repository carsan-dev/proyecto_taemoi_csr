package com.taemoi.project.entities;

/**
 * Código de un movimiento de caja (pieza 6b-2, decisión 5 del propietario). El importe lleva signo: positivo entra en
 * caja, negativo sale.
 * <ul>
 * <li>{@link #DEVOLUCION}: dinero devuelto a un alumno; negativo y con alumno obligatorio.</li>
 * <li>{@link #ERROR}: corrección de un error; cualquier signo, alumno opcional.</li>
 * <li>{@link #SOBRANTE}: hay más dinero del esperado; positivo y sin alumno.</li>
 * <li>{@link #FALTANTE}: hay menos dinero del esperado; negativo y sin alumno.</li>
 * <li>{@link #OTRO}: cualquier otro; cualquier signo, alumno opcional.</li>
 * </ul>
 * Una entrega cuyo importe contado difiere del declarado genera su SOBRANTE o FALTANTE automático.
 */
public enum TipoMovimientoCaja {
	DEVOLUCION,
	ERROR,
	SOBRANTE,
	FALTANTE,
	OTRO
}
