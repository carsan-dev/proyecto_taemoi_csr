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
}
