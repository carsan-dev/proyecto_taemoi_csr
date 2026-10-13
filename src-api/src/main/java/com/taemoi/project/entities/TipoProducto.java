package com.taemoi.project.entities;

/**
 * Tipo de un {@link Producto} (columna {@code producto.tipo}). Sustituirá a las búsquedas por
 * nombre ({@code "MENSUALIDAD"}, {@code "LICENCIA FEDERATIVA …"}, {@code TipoGrado.fromProductoExamen}).
 *
 * <p>{@code OTRO} es el valor por defecto: un producto solo entra en una búsqueda por tipo cuando
 * se ha clasificado expresamente (V24 o, más adelante, la gestión de productos).
 */
public enum TipoProducto {
	MENSUALIDAD, RESERVA_PLAZA, LICENCIA, COMPETIDOR, EXAMEN, RECOMPENSA, OTRO
}
