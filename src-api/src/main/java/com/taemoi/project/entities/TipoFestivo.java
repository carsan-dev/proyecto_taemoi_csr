package com.taemoi.project.entities;

/**
 * Forma de un {@link Festivo} (V32_2): un día ({@code fechaDesde = fechaHasta}), un periodo de una fecha a otra, ambas
 * incluidas, o el mismo día y mes cada año (fechas {@code null}).
 */
public enum TipoFestivo {
	DIA, RANGO, ANUAL;

	/** El tipo con ese nombre exacto; {@code null} si no hay ninguno con ese nombre. */
	public static TipoFestivo deNombre(String nombre) {
		if (nombre != null) {
			for (TipoFestivo tipo : values()) {
				if (tipo.name().equals(nombre)) {
					return tipo;
				}
			}
		}
		return null;
	}
}
