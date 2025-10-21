package com.taemoi.project.utils;

import java.time.DayOfWeek;
import java.util.Locale;

public class DiaSemanaUtils {
	public static DayOfWeek mapGrupoToDayOfWeek(String grupo) {
		return switch (grupo.toLowerCase(Locale.ROOT)) {
		case "lunes" -> DayOfWeek.MONDAY;
		case "martes" -> DayOfWeek.TUESDAY;
		case "miercoles", "miércoles" -> DayOfWeek.WEDNESDAY;
		case "jueves" -> DayOfWeek.THURSDAY;
		default -> throw new IllegalArgumentException("Grupo desconocido: " + grupo);
		};
	}
}
