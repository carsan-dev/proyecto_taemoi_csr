package com.taemoi.project.utils;

import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.Date;

public class FechaUtils {

	public static int calcularEdad(Date fechaNacimiento) {
		LocalDate fechaNacimientoLocal;

		if (fechaNacimiento instanceof java.sql.Date date) {
			fechaNacimientoLocal = date.toLocalDate();
		} else {
			fechaNacimientoLocal = fechaNacimiento.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
		}

		LocalDate fechaActual = LocalDate.now();
		return Period.between(fechaNacimientoLocal, fechaActual).getYears();
	}

	/**
	 * Calcula la antigüedad desde una fecha hasta hoy, retornando un string con formato "X años y Y meses".
	 * Si la fecha es null, retorna null.
	 * Usa correctamente singular/plural en español (1 año, 2 años, 1 mes, 2 meses).
	 *
	 * @param fechaInicial La fecha inicial desde la que se calcula la antigüedad.
	 * @return String con formato "X años y Y meses", "X año", "Y meses", etc., o null si fechaInicial es null.
	 */
	public static String calcularAntiguedad(Date fechaInicial) {
		if (fechaInicial == null) {
			return null;
		}

		LocalDate fechaInicialLocal;
		if (fechaInicial instanceof java.sql.Date date) {
			fechaInicialLocal = date.toLocalDate();
		} else {
			fechaInicialLocal = fechaInicial.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
		}

		LocalDate fechaActual = LocalDate.now();
		Period periodo = Period.between(fechaInicialLocal, fechaActual);

		int anios = periodo.getYears();
		int meses = periodo.getMonths();

		String aniosStr = anios == 1 ? "1 año" : anios + " años";
		String mesesStr = meses == 1 ? "1 mes" : meses + " meses";

		if (anios > 0 && meses > 0) {
			return aniosStr + " y " + mesesStr;
		} else if (anios > 0) {
			return aniosStr;
		} else if (meses > 0) {
			return mesesStr;
		} else {
			return "0 meses";
		}
	}
}
