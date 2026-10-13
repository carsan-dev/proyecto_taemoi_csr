package com.taemoi.project.utils;

import java.util.Locale;

public final class NifUtils {

	public static final String PLACEHOLDER_SIN_NIF = "00000000Z";

	private NifUtils() {
		// Utility class
	}

	public static String normalizeForStorage(String nif) {
		if (nif == null) {
			return null;
		}

		String normalized = nif.trim().toUpperCase(Locale.ROOT);
		if (normalized.isEmpty() || PLACEHOLDER_SIN_NIF.equals(normalized)) {
			return null;
		}
		return normalized;
	}

	/**
	 * Forma canónica para comparar NIFs: mayúsculas, sin espacios ni guiones; {@code null} si queda
	 * vacío o es el marcador "sin NIF".
	 */
	public static String normalizeForComparison(String nif) {
		String base = normalizeForStorage(nif);
		if (base == null) {
			return null;
		}
		String limpio = base.replaceAll("[\\s-]", "");
		return limpio.isEmpty() || PLACEHOLDER_SIN_NIF.equals(limpio) ? null : limpio;
	}
}
