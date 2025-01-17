package com.taemoi.project.utils;

import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.Date;

public class FechaUtils {

	public static int calcularEdad(Date fechaNacimiento) {
		LocalDate fechaNacimientoLocal;

		if (fechaNacimiento instanceof java.sql.Date) {
			fechaNacimientoLocal = ((java.sql.Date) fechaNacimiento).toLocalDate();
		} else {
			fechaNacimientoLocal = fechaNacimiento.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
		}

		LocalDate fechaActual = LocalDate.now();
		return Period.between(fechaNacimientoLocal, fechaActual).getYears();
	}
}
