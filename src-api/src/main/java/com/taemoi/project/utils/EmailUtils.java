package com.taemoi.project.utils;

import java.util.Locale;

public final class EmailUtils {
	private EmailUtils() {
	}

	public static String normalizeEmail(String email) {
		return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
	}
}
