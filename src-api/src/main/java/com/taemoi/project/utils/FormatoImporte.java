package com.taemoi.project.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Importe en euros para los PDF, en formato es-ES y sin depender del idioma de la JVM: «28,00 €», «1.234,50 €». Dos
 * decimales fijos con redondeo HALF_UP (como {@link ImporteCargo#de}), punto de miles y un espacio normal (U+0020, no
 * NBSP) antes del símbolo. El importe nulo cuenta como 0, como el precio nulo de un cargo.
 */
public final class FormatoImporte {

	private static final Locale ES = Locale.of("es", "ES");

	private FormatoImporte() {
	}

	public static String eur(BigDecimal importe) {
		DecimalFormat formato = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(ES));
		formato.setRoundingMode(RoundingMode.HALF_UP);
		return formato.format(importe != null ? importe : BigDecimal.ZERO) + " €";
	}

	/** Sobre la representación decimal más corta del {@code double} ({@link BigDecimal#valueOf(double)}). */
	public static String eur(Double importe) {
		return eur(importe != null ? BigDecimal.valueOf(importe) : null);
	}
}
