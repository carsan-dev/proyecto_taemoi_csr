package com.taemoi.project.utils;

import java.util.regex.Pattern;

/**
 * Teléfonos de alumnos y tutores, guardados como texto (VARCHAR(20)) desde V25.
 *
 * <p>Se guardan solo los dígitos (con un {@code +} inicial si es internacional). Un número español
 * con prefijo {@code +34}/{@code 0034} se guarda con sus 9 dígitos, como los que ya había (INT).
 */
public final class TelefonoUtils {

	private static final Pattern SEPARADORES = Pattern.compile("[\\s.\\-()/]");
	private static final Pattern VALIDO = Pattern.compile("\\+?\\d{9,15}");

	private TelefonoUtils() {
	}

	/** Normaliza un teléfono; {@code null} si viene vacío. No valida: ver {@link #esValido}. */
	public static String normalizar(String telefono) {
		if (telefono == null) {
			return null;
		}
		String limpio = SEPARADORES.matcher(telefono.trim()).replaceAll("");
		if (limpio.isEmpty()) {
			return null;
		}
		if (limpio.startsWith("+34") && limpio.length() == 12) {
			return limpio.substring(3);
		}
		if (limpio.startsWith("0034") && limpio.length() == 13) {
			return limpio.substring(4);
		}
		return limpio;
	}

	/** {@code true} si el teléfono normalizado tiene entre 9 y 15 dígitos (con {@code +} opcional). */
	public static boolean esValido(String telefono) {
		String normalizado = normalizar(telefono);
		return normalizado != null && normalizado.length() <= 20 && VALIDO.matcher(normalizado).matches();
	}
}
