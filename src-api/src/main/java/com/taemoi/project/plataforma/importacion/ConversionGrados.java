package com.taemoi.project.plataforma.importacion;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decisión d558.3: el grado de la hoja maestra al código del sistema de grados (los de V23, {@code CatalogoLegadoTaemoi}).
 * <ul>
 * <li>«SIN GRADO» o vacío → {@code BLANCO}, con aviso.</li>
 * <li>Colores, con barra entre dos: «BLANCO/AMARILLO» → {@code BLANCO_AMARILLO}.</li>
 * <li>«n PUM» (1 a 3) → {@code ROJO_NEGRO_n_PUM}.</li>
 * <li>«n DAN» (1 a 5) → {@code NEGRO_n_DAN}; un menor (menos de 18 años el día de la carga) entra con el PUM del mismo
 * número, con aviso, y con 4 o 5 DAN es un error (no hay PUM equivalente).</li>
 * </ul>
 * Sin aproximaciones: cualquier otro texto es un error. Mayúsculas y espacios sobrantes no cuentan.
 */
final class ConversionGrados {

	static final String BLANCO = "BLANCO";
	static final String AVISO_SIN_GRADO = "Sin grado: entrará como BLANCO.";

	private static final Set<String> COLORES = Set.of("BLANCO", "BLANCO/AMARILLO", "AMARILLO", "AMARILLO/NARANJA",
			"NARANJA", "NARANJA/VERDE", "VERDE", "VERDE/AZUL", "AZUL", "AZUL/ROJO", "ROJO");
	private static final Pattern NUMERADO = Pattern.compile("([0-9])\\s*(PUM|DAN)");
	private static final int LARGO_MAXIMO_CITADO = 40;

	/** Código del grado (o {@code null} si hay error), con su aviso o su error. */
	record Resultado(String codigo, String aviso, String error) {
	}

	private ConversionGrados() {
	}

	static Resultado convertir(String texto, boolean menor) {
		String original = texto == null ? "" : texto.strip();
		String limpio = original.toUpperCase(Locale.ROOT).replaceAll("\\s+", " ").replaceAll("\\s*/\\s*", "/");
		if (limpio.isEmpty() || "SIN GRADO".equals(limpio)) {
			return new Resultado(BLANCO, AVISO_SIN_GRADO, null);
		}
		if (COLORES.contains(limpio)) {
			return new Resultado(limpio.replace('/', '_'), null, null);
		}
		Matcher numerado = NUMERADO.matcher(limpio);
		if (numerado.matches()) {
			int n = Integer.parseInt(numerado.group(1));
			if ("PUM".equals(numerado.group(2)) && n >= 1 && n <= 3) {
				return new Resultado("ROJO_NEGRO_" + n + "_PUM", null, null);
			}
			if ("DAN".equals(numerado.group(2)) && n >= 1 && n <= 5) {
				if (!menor) {
					return new Resultado("NEGRO_" + n + "_DAN", null, null);
				}
				if (n <= 3) {
					return new Resultado("ROJO_NEGRO_" + n + "_PUM",
							"Menor con " + n + " DAN: entrará como " + n + " PUM.", null);
				}
				return new Resultado(null, null, "Menor con " + n
						+ " DAN: no hay PUM equivalente; revisa el grado o la fecha de nacimiento.");
			}
		}
		return new Resultado(null, null, "«" + citado(original) + "» no es un grado válido.");
	}

	/** Un valor de la hoja citado en un motivo, acortado. */
	static String citado(String valor) {
		String limpio = valor == null ? "" : valor.strip().replaceAll("\\s+", " ");
		return limpio.length() <= LARGO_MAXIMO_CITADO ? limpio : limpio.substring(0, LARGO_MAXIMO_CITADO) + "…";
	}
}
