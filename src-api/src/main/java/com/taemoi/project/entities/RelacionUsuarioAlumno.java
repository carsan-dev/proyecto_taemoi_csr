package com.taemoi.project.entities;

import java.time.LocalDate;
import java.time.Period;

/**
 * Relación de una cuenta con la ficha que ve ({@code usuario_alumno.relacion}, V31): el propio alumno o su tutor.
 * Informativa en esta fase; V31 la deduce por edad (18 años, decisión D7) y se corrige a mano.
 */
public enum RelacionUsuarioAlumno {
	PROPIO,
	TUTOR;

	/**
	 * Misma regla que el relleno de V31: {@code PROPIO} si el alumno tiene 18 años cumplidos en {@code hoy}
	 * ({@code TIMESTAMPDIFF(YEAR, fecha_nacimiento, CURDATE()) >= 18}), {@code TUTOR} si no o sin fecha.
	 */
	public static RelacionUsuarioAlumno porEdad(LocalDate fechaNacimiento, LocalDate hoy) {
		return fechaNacimiento != null && Period.between(fechaNacimiento, hoy).getYears() >= 18 ? PROPIO : TUTOR;
	}
}
