package com.taemoi.project.utils;

import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.Date;

import com.taemoi.project.entities.Deporte;

public final class DeporteEdadUtils {
	private static final int EDAD_ADULTO_TAEKWONDO = 14;
	private static final int EDAD_ADULTO_KICKBOXING = 15;

	private DeporteEdadUtils() {
	}

	public static boolean esMenorParaDeporte(Deporte deporte, Date fechaNacimiento) {
		if (fechaNacimiento == null) {
			return false;
		}
		return esMenorParaDeporte(deporte, toLocalDate(fechaNacimiento));
	}

	public static boolean esMenorParaDeporte(Deporte deporte, LocalDate fechaNacimiento) {
		if (fechaNacimiento == null) {
			return false;
		}
		int edadAdulto = obtenerEdadAdulto(deporte);
		int edad = Period.between(fechaNacimiento, LocalDate.now()).getYears();
		int edadMenor = Math.max(0, edadAdulto - 1);
		boolean cumpleEdadAdultoEsteAnio = cumpleEdadEsteAnio(fechaNacimiento, edadAdulto);
		return edad < edadMenor || (edad == edadMenor && !cumpleEdadAdultoEsteAnio);
	}

	private static int obtenerEdadAdulto(Deporte deporte) {
		if (deporte == Deporte.KICKBOXING) {
			return EDAD_ADULTO_KICKBOXING;
		}
		return EDAD_ADULTO_TAEKWONDO;
	}

	private static boolean cumpleEdadEsteAnio(LocalDate fechaNacimiento, int edadObjetivo) {
		LocalDate fechaCumple = fechaNacimiento.plusYears(edadObjetivo);
		int anioActual = LocalDate.now().getYear();
		return fechaCumple.getYear() == anioActual;
	}

	private static LocalDate toLocalDate(Date fechaNacimiento) {
		if (fechaNacimiento instanceof java.sql.Date date) {
			return date.toLocalDate();
		}
		return fechaNacimiento.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
	}
}
