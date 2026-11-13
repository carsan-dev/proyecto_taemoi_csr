package com.taemoi.project.plataforma.importacion;

import java.time.LocalDate;

/**
 * El valor de una celda ya normalizado.
 *
 * @param texto           el contenido como texto, sin espacios en los extremos; vacío ({@code ""}) si la celda está en
 *                        blanco
 * @param fecha           la fecha si la celda es una fecha de Excel (número con formato de fecha); si no, {@code null}
 * @param error           la celda no tiene un dato que se pueda usar: guarda un error de fórmula ({@code #N/A},
 *                        {@code #DIV/0!}…) o es una fórmula sin valor guardado
 * @param formulaSinValor la celda es una fórmula sin valor guardado (nadie la calculó): {@code error} también es cierto
 */
record Valor(String texto, LocalDate fecha, boolean error, boolean formulaSinValor) {

	static final Valor VACIO = new Valor("", null, false);
	static final Valor ERROR = new Valor("", null, true);
	static final Valor FORMULA_SIN_VALOR = new Valor("", null, true, true);

	Valor {
		texto = texto == null ? "" : texto;
	}

	Valor(String texto, LocalDate fecha, boolean error) {
		this(texto, fecha, error, false);
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
