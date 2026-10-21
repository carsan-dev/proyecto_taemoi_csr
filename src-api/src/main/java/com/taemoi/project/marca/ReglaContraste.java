package com.taemoi.project.marca;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Pieza 8b-T (fase3-e8b-plan.md §4, DESIGN.md «The Contraste Automático Rule»): de un color de escuela guardado a los
 * colores del panel ({@link TemaEscuela}). La aplica el sistema, no el superadmin. Pura, determinista y sin Spring: se
 * calcula al leer, sin columnas nuevas. El frontend repite la misma regla para la vista previa de «Logo y color»
 * ({@code regla-contraste.ts}, con los mismos vectores en sus tests).
 *
 * <ul>
 * <li>Texto sobre el primario: blanco si da ≥ 4,5:1; si no, la tinta {@value #TINTA} si da ≥ 4,5:1; si ninguno llega,
 * el primario se oscurece en OKLCH (misma tonalidad y croma, luz −{@value #PASO_LUZ} por paso, recortado a sRGB) hasta
 * que el blanco llegue.</li>
 * <li>Secundario de la cabecera: el texto mezclado con el primario (del {@value #MEZCLA_SECUNDARIO_MAXIMA} hacia 0, en
 * pasos de {@value #PASO_MEZCLA}), el primero que dé ≥ 4,5:1 sobre el primario.</li>
 * <li>Tinta: el primario oscurecido igual hasta ≥ 4,5:1 sobre blanco (si ya llega, él mismo).</li>
 * <li>Suave: el primario al {@value #MEZCLA_SUAVE} sobre blanco, rebajado si hiciera falta para que la tinta dé ≥ 4,5:1
 * encima.</li>
 * <li>Sin color guardado: {@value #POR_DEFECTO} (decisión 257).</li>
 * </ul>
 * Las razones se miden sobre el color ya redondeado a {@code #RRGGBB}, que es el que se pinta (WCAG 2.2, 1.4.3).
 */
public final class ReglaContraste {

	public static final String BLANCO = "#FFFFFF";
	public static final String TINTA = "#1A2230";
	/** Decisión 257: una escuela sin color guardado. */
	public static final String POR_DEFECTO = "#1A2230";
	public static final double MINIMO = 4.5;
	public static final String MENSAJE_FORMATO = "El color debe tener el formato #RRGGBB.";

	static final double PASO_LUZ = 0.01;
	static final double MEZCLA_SECUNDARIO_MAXIMA = 0.25;
	static final double PASO_MEZCLA = 0.05;
	static final double MEZCLA_SUAVE = 0.10;
	static final double PASO_SUAVE = 0.01;

	private static final Pattern FORMATO = Pattern.compile("^#[0-9A-Fa-f]{6}$");

	private ReglaContraste() {
	}

	/** Los colores del panel para un color guardado ({@code null}: el de por defecto). */
	public static TemaEscuela tema(String colorGuardado) {
		String original = colorGuardado == null ? POR_DEFECTO : normalizar(colorGuardado);
		String primario = original;
		String sobre;
		if (contraste(BLANCO, original) >= MINIMO) {
			sobre = BLANCO;
		} else if (contraste(TINTA, original) >= MINIMO) {
			sobre = TINTA;
		} else {
			primario = oscurecerHastaBlanco(original);
			sobre = BLANCO;
		}
		return new TemaEscuela(primario, sobre, secundario(sobre, primario), oscurecerHastaBlanco(primario),
				suave(primario), !primario.equals(original));
	}

	/** Razón de contraste de WCAG 2.2 entre dos {@code #RRGGBB} (de 1 a 21). */
	public static double contraste(String a, String b) {
		double la = luminancia(normalizar(a));
		double lb = luminancia(normalizar(b));
		return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
	}

	/** Tonalidad OKLCH en grados (para comprobar que oscurecer no cambia el color de la marca). */
	public static double tonoOklch(String color) {
		return oklch(normalizar(color))[2];
	}

	public static boolean esColorValido(String color) {
		return color != null && FORMATO.matcher(color).matches();
	}

	/**
	 * {@code #RRGGBB} en mayúsculas.
	 *
	 * @throws IllegalArgumentException si no tiene ese formato exacto (sin espacios)
	 */
	public static String normalizar(String color) {
		if (!esColorValido(color)) {
			throw new IllegalArgumentException(MENSAJE_FORMATO);
		}
		return color.toUpperCase(Locale.ROOT);
	}

	// ------------------------------------------------------------------ reglas

	/** Baja la luz OKLCH conservando tonalidad y croma hasta que el blanco llegue a 4,5:1 encima. */
	private static String oscurecerHastaBlanco(String color) {
		if (contraste(BLANCO, color) >= MINIMO) {
			return color;
		}
		double[] lch = oklch(color);
		for (int paso = 1;; paso++) {
			double luz = lch[0] - paso * PASO_LUZ;
			if (luz <= 0) {
				return "#000000";
			}
			String candidato = desdeOklch(luz, lch[1], lch[2]);
			if (contraste(BLANCO, candidato) >= MINIMO) {
				return candidato;
			}
		}
	}

	private static String secundario(String texto, String primario) {
		int pasos = (int) Math.round(MEZCLA_SECUNDARIO_MAXIMA / PASO_MEZCLA);
		for (int i = pasos; i > 0; i--) {
			String mezcla = mezclar(texto, primario, i * PASO_MEZCLA);
			if (contraste(mezcla, primario) >= MINIMO) {
				return mezcla;
			}
		}
		return texto;
	}

	private static String suave(String primario) {
		int pasos = (int) Math.round(MEZCLA_SUAVE / PASO_SUAVE);
		for (int i = pasos; i > 0; i--) {
			String mezcla = mezclar(BLANCO, primario, i * PASO_SUAVE);
			if (contraste(TINTA, mezcla) >= MINIMO) {
				return mezcla;
			}
		}
		return BLANCO;
	}

	/** {@code base} con la parte {@code t} de {@code otro}, canal a canal en sRGB. */
	private static String mezclar(String base, String otro, double t) {
		int[] a = canales(base);
		int[] b = canales(otro);
		int[] r = new int[3];
		for (int i = 0; i < 3; i++) {
			r[i] = (int) Math.round(a[i] * (1 - t) + b[i] * t);
		}
		return hex(r);
	}

	// ------------------------------------------------------------------ color

	private static double luminancia(String color) {
		int[] c = canales(color);
		return 0.2126 * lineal(c[0]) + 0.7152 * lineal(c[1]) + 0.0722 * lineal(c[2]);
	}

	private static double lineal(int canal) {
		double c = canal / 255.0;
		return c <= 0.04045 ? c / 12.92 : StrictMath.pow((c + 0.055) / 1.055, 2.4);
	}

	private static double gamma(double c) {
		double recortado = Math.min(1.0, Math.max(0.0, c));
		return recortado <= 0.0031308 ? 12.92 * recortado : 1.055 * StrictMath.pow(recortado, 1 / 2.4) - 0.055;
	}

	/** {L, C, h°} de un {@code #RRGGBB}. */
	private static double[] oklch(String color) {
		int[] c = canales(color);
		double r = lineal(c[0]);
		double g = lineal(c[1]);
		double b = lineal(c[2]);
		double l = StrictMath.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
		double m = StrictMath.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
		double s = StrictMath.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
		double luz = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s;
		double a = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s;
		double bb = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s;
		double tono = StrictMath.toDegrees(StrictMath.atan2(bb, a));
		return new double[] { luz, StrictMath.hypot(a, bb), tono < 0 ? tono + 360 : tono };
	}

	/** {@code #RRGGBB} de un OKLCH, recortado a la gama sRGB. */
	private static String desdeOklch(double luz, double croma, double tono) {
		double rad = StrictMath.toRadians(tono);
		double a = croma * StrictMath.cos(rad);
		double b = croma * StrictMath.sin(rad);
		double l = cubo(luz + 0.3963377774 * a + 0.2158037573 * b);
		double m = cubo(luz - 0.1055613458 * a - 0.0638541728 * b);
		double s = cubo(luz - 0.0894841775 * a - 1.2914855480 * b);
		double r = 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s;
		double g = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s;
		double bl = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s;
		return hex(new int[] { (int) Math.round(gamma(r) * 255), (int) Math.round(gamma(g) * 255),
				(int) Math.round(gamma(bl) * 255) });
	}

	private static double cubo(double x) {
		return x * x * x;
	}

	private static int[] canales(String color) {
		return new int[] { Integer.parseInt(color.substring(1, 3), 16), Integer.parseInt(color.substring(3, 5), 16),
				Integer.parseInt(color.substring(5, 7), 16) };
	}

	private static String hex(int[] c) {
		return String.format(Locale.ROOT, "#%02X%02X%02X", c[0], c[1], c[2]);
	}
}
