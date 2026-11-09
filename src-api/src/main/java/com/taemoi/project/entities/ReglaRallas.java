package com.taemoi.project.entities;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Regla única de las rallas hacia el examen (decisiones 510-513): cuándo se usan y su rango. Una inscripción
 * ({@link AlumnoDeporte}) lleva rallas solo si su disciplina las tiene activas ({@link Disciplina#getRallasActivas()}),
 * tiene grado y ese grado no es PUM ni DAN. El blanco SÍ lleva.
 *
 * <p>PUM y DAN se reconocen por el CÓDIGO del grado, nunca por su nombre visible: la convención del catálogo (V30) es
 * {@code <colores>_<n>_PUM} y {@code <colores>_<n>_DAN} ({@code ROJO_NEGRO_1_PUM}, {@code NEGRO_3_DAN}), la misma que
 * dibuja sus franjas. Se admite también el sufijo sin ordinal ({@code NEGRO_DAN}) o el código solo ({@code DAN}).
 */
public final class ReglaRallas {

	/** Sin rallas. */
	public static final int MINIMO = 0;
	/** Las 5 rallas del examen, en medias. */
	public static final int MAXIMO = 10;

	private static final Pattern PUM_O_DAN = Pattern.compile("^(.*_)?(PUM|DAN)$");

	private ReglaRallas() {
	}

	/** El grado con ese código lleva rallas: tiene código y no es PUM ni DAN. */
	public static boolean gradoLlevaRallas(String codigoGrado) {
		if (codigoGrado == null || codigoGrado.isBlank()) {
			return false;
		}
		return !PUM_O_DAN.matcher(codigoGrado.strip().toUpperCase(Locale.ROOT)).matches();
	}

	/** Se usan: disciplina con rallas activas y grado que las lleva ({@link #gradoLlevaRallas}). */
	public static boolean aplica(Boolean disciplinaConRallas, String codigoGrado) {
		return Boolean.TRUE.equals(disciplinaConRallas) && gradoLlevaRallas(codigoGrado);
	}
}
