package com.taemoi.project.utils;

import java.util.Locale;

public final class EmailUtils {
	private EmailUtils() {
	}

	public static String normalizeEmail(String email) {
		return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
	}

	/**
	 * Indica si el email de una cuenta puede vincularla con alumnos (acceso de familia por email).
	 * Un email en blanco (null, vacío o solo espacios) nunca coincide con ningún alumno, aunque haya
	 * alumnos con el email vacío: mismo criterio que {@code AlumnoAccessControlServiceImpl}.
	 */
	public static boolean puedeVincularAlumnos(String email) {
		return email != null && !email.isBlank();
	}
}
