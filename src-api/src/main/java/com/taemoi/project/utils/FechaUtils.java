package com.taemoi.project.utils;

import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.Date;

import com.taemoi.project.entities.Deporte;

public class FechaUtils {

	/**
	 * Edad limite para ser considerado adulto en Taekwondo.
	 * Un alumno es adulto si cumple 14 anos en el ano actual.
	 */
	public static final int EDAD_ADULTO_TAEKWONDO = 14;

	/**
	 * Edad limite para ser considerado adulto en Kickboxing.
	 * Un alumno es adulto si cumple 15 anos en el ano actual.
	 */
	public static final int EDAD_ADULTO_KICKBOXING = 15;

	/**
	 * Determina si un alumno es considerado "menor" segun las reglas del deporte especificado.
	 *
	 * Para Taekwondo: menor si edad < 13 o (edad == 13 y no cumple 14 este ano)
	 * Para Kickboxing: menor si edad < 14 o (edad == 14 y no cumple 15 este ano)
	 * Para otros deportes: se usa la regla de Taekwondo por defecto.
	 *
	 * @param fechaNacimiento La fecha de nacimiento del alumno.
	 * @param deporte El deporte para aplicar la regla correcta.
	 * @return true si el alumno es considerado menor, false si es adulto.
	 */
	public static boolean esMenor(Date fechaNacimiento, Deporte deporte) {
		if (fechaNacimiento == null) {
			return true; // Por defecto, si no hay fecha, consideramos menor
		}

		int edad = calcularEdad(fechaNacimiento);
		int edadAdulto = obtenerEdadAdulto(deporte);
		int edadLimiteMenor = edadAdulto - 1; // 13 para taekwondo, 14 para kickboxing

		if (edad < edadLimiteMenor) {
			return true;
		}

		if (edad == edadLimiteMenor) {
			// Solo es adulto si cumple edadAdulto este ano
			return !cumpleEdadEsteAnio(fechaNacimiento, edadAdulto);
		}

		return false; // edad >= edadAdulto
	}

	/**
	 * Determina si un alumno es considerado "menor" para un ano de referencia.
	 * Se considera adulto si cumple la edad adulta durante ese ano.
	 *
	 * @param fechaNacimiento La fecha de nacimiento del alumno.
	 * @param deporte El deporte para aplicar la regla correcta.
	 * @param anioReferencia El ano que se usa para decidir si cumple la edad adulta ese ano.
	 * @return true si el alumno es considerado menor, false si es adulto.
	 */
	public static boolean esMenor(Date fechaNacimiento, Deporte deporte, int anioReferencia) {
		if (fechaNacimiento == null) {
			return true; // Por defecto, si no hay fecha, consideramos menor
		}

		int edadAdulto = obtenerEdadAdulto(deporte);
		LocalDate fechaNac = convertirALocalDate(fechaNacimiento);
		int anioCumpleAdulto = fechaNac.plusYears(edadAdulto).getYear();

		// Es menor si NO cumple la edad adulta en el ano de referencia (ni antes).
		return anioCumpleAdulto > anioReferencia;
	}

	/**
	 * Obtiene la edad a partir de la cual un alumno es considerado adulto para un deporte.
	 *
	 * @param deporte El deporte.
	 * @return La edad limite para ser adulto.
	 */
	public static int obtenerEdadAdulto(Deporte deporte) {
		if (deporte == Deporte.KICKBOXING) {
			return EDAD_ADULTO_KICKBOXING;
		}
		// Taekwondo y otros deportes usan la regla de Taekwondo
		return EDAD_ADULTO_TAEKWONDO;
	}

	/**
	 * Verifica si el alumno cumple una edad especifica en el ano actual.
	 *
	 * @param fechaNacimiento La fecha de nacimiento.
	 * @param edad La edad a verificar.
	 * @return true si cumple esa edad este ano.
	 */
	public static boolean cumpleEdadEsteAnio(Date fechaNacimiento, int edad) {
		if (fechaNacimiento == null) {
			return false;
		}

		LocalDate fechaNac = convertirALocalDate(fechaNacimiento);
		LocalDate fechaCumple = fechaNac.plusYears(edad);
		int anioActual = LocalDate.now().getYear();
		return fechaCumple.getYear() == anioActual;
	}

	private static LocalDate convertirALocalDate(Date fecha) {
		if (fecha instanceof java.sql.Date date) {
			return date.toLocalDate();
		}
		return fecha.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
	}

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
	 * Calcula la antiguedad desde una fecha hasta hoy, retornando un string con formato "X anos y Y meses".
	 * Si la fecha es null, retorna null.
	 * Usa correctamente singular/plural en espanol (1 ano, 2 anos, 1 mes, 2 meses).
	 *
	 * @param fechaInicial La fecha inicial desde la que se calcula la antiguedad.
	 * @return String con formato "X anos y Y meses", "X ano", "Y meses", etc., o null si fechaInicial es null.
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
