package com.taemoi.project.plataforma.importacion;

import java.time.LocalDate;

/**
 * El valor de una celda ya normalizado.
 *
 * @param texto el contenido como texto, sin espacios en los extremos; vacío ({@code ""}) si la celda está en blanco
 * @param fecha la fecha si la celda es una fecha de Excel (número con formato de fecha); si no, {@code null}
 * @param error la celda guarda un error de fórmula ({@code #N/A}, {@code #DIV/0!}…)
 */
record Valor(String texto, LocalDate fecha, boolean error) {

	static final Valor VACIO = new Valor("", null, false);
	static final Valor ERROR = new Valor("", null, true);

	Valor {
		texto = texto == null ? "" : texto;
	}

	static Valor deTexto(String texto) {
		return texto == null || texto.isEmpty() ? VACIO : new Valor(texto, null, false);
	}

	static Valor deFecha(LocalDate fecha) {
		return new Valor(fecha.toString(), fecha, false);
	}

	boolean vacio() {
		return !error && fecha == null && texto.isEmpty();
	}
}
