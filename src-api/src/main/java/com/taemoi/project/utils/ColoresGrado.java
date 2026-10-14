package com.taemoi.project.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.taemoi.project.entities.Grado;

/**
 * Colores con los que se dibuja el cinturón de un grado.
 *
 * <p>Convención del catálogo (V30, la del frontend en {@code grado-colors.ts}): {@code color_1} es el color
 * base = mitad INFERIOR del cinturón (el primer color del código: BLANCO en BLANCO_AMARILLO, ROJO en
 * ROJO_NEGRO_1_PUM); {@code color_2} es la mitad SUPERIOR; {@code color_2} NULL = un solo color. Las franjas
 * de PUM/DAN no se guardan: se derivan del código ({@code _<n>_PUM}, {@code _<n>_DAN}).
 *
 * <p>Un grado sin {@code color_1} (una escuela que aún no ha rellenado sus colores) se dibuja como antes de
 * V30, a partir del código ({@link #desdeCodigo(String)}): gris {@code #CCCCCC} para los códigos que no son
 * nombres de color.
 */
public final class ColoresGrado {

	private static final Pattern FRANJAS = Pattern.compile("_(\\d+)_(PUM|DAN)$");

	/**
	 * @param inferior color de la mitad inferior (o de todo el cinturón si no es doble)
	 * @param superior color de la mitad superior (igual a {@code inferior} si no es doble)
	 * @param doble    dos colores
	 * @param franjas  franjas de PUM/DAN (0 si no tiene)
	 * @param rayado   se dibuja con el contenedor de las franjas (PUM/DAN). Con colores del catálogo, si tiene
	 *                 franjas; sin ellos, como antes de V30 (todo código con ROJO_NEGRO, DAN o PUM, aunque no
	 *                 lleve número)
	 */
	public record Cinturon(String inferior, String superior, boolean doble, int franjas, boolean rayado) {

		public Cinturon(String inferior, String superior, boolean doble, int franjas) {
			this(inferior, superior, doble, franjas, franjas > 0);
		}
	}

	private ColoresGrado() {
	}

	/** Colores del grado: los del catálogo o, sin {@code color_1}, los derivados del código. Nulo sin grado. */
	public static Cinturon de(Grado grado) {
		if (grado == null) {
			return null;
		}
		return de(grado.getCodigo(), grado.getColor1(), grado.getColor2());
	}

	/** Igual que {@link #de(Grado)} con los valores sueltos. Nulo si no hay código ni colores. */
	public static Cinturon de(String codigo, String color1, String color2) {
		if (color1 == null) {
			return codigo == null ? null : desdeCodigo(codigo);
		}
		boolean doble = color2 != null;
		return new Cinturon(color1, doble ? color2 : color1, doble, franjas(codigo));
	}

	/** Franjas de PUM/DAN según el código ({@code ROJO_NEGRO_2_PUM} = 2, {@code NEGRO_3_DAN} = 3); 0 si no. */
	public static int franjas(String codigo) {
		if (codigo == null) {
			return 0;
		}
		Matcher m = FRANJAS.matcher(codigo);
		if (!m.find()) {
			return 0;
		}
		try {
			return Integer.parseInt(m.group(1));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	/**
	 * Colores derivados del código, exactamente como los dibujaba {@code PDFServiceImpl} antes de V30 (nombres
	 * de color separados por «_»; PUM/DAN con franjas; códigos desconocidos en gris).
	 */
	public static Cinturon desdeCodigo(String tipo) {
		if (tipo.contains("ROJO_NEGRO")) {
			String[] parts = tipo.split("_");
			int franjas = 0;
			try {
				franjas = Integer.parseInt(parts[2]);
			} catch (Exception e) {
				// Sin número: sin franjas, como antes.
			}
			return new Cinturon(colorPorNombre(parts[0]), colorPorNombre(parts[1]), true, franjas, true);
		}
		if (tipo.contains("DAN") || tipo.contains("PUM")) {
			int franjas = 0;
			if (tipo.contains("DAN")) {
				try {
					franjas = Integer.parseInt(tipo.split("_")[1]);
				} catch (Exception e) {
					// Sin número: sin franjas, como antes.
				}
			}
			String color = colorCinturon(tipo);
			return new Cinturon(color, color, false, franjas, true);
		}
		if (tipo.contains("_")) {
			String[] parts = tipo.split("_");
			return new Cinturon(colorPorNombre(parts[0]), colorPorNombre(parts[1]), true, 0);
		}
		String color = colorCinturon(tipo);
		return new Cinturon(color, color, false, 0);
	}

	/** Color de un cinturón de un solo color por su código (antes {@code PDFServiceImpl.obtenerColorCinturon}). */
	private static String colorCinturon(String tipo) {
		switch (tipo) {
		case "ROJO_NEGRO_1_PUM":
		case "ROJO_NEGRO_2_PUM":
		case "ROJO_NEGRO_3_PUM":
			return "#800000";
		case "NEGRO_1_DAN":
		case "NEGRO_2_DAN":
		case "NEGRO_3_DAN":
		case "NEGRO_4_DAN":
		case "NEGRO_5_DAN":
			return "#000000";
		case "NEGRO":
			return "#CCCCCC"; // sin caso propio antes de V30: gris
		default:
			return colorPorNombre(tipo);
		}
	}

	/** Color por nombre (antes {@code PDFServiceImpl.obtenerColorPorNombre}). */
	private static String colorPorNombre(String nombre) {
		switch (nombre) {
		case "BLANCO":
			return "#FFFFFF";
		case "BLANCO_AMARILLO":
			return "#FFFACD";
		case "AMARILLO":
			return "#FFFF00";
		case "AMARILLO_NARANJA":
			return "#FFD700";
		case "NARANJA":
			return "#FFA500";
		case "NARANJA_VERDE":
			return "#ADFF2F";
		case "VERDE":
			return "#008000";
		case "VERDE_AZUL":
			return "#20B2AA";
		case "AZUL":
			return "#0000FF";
		case "AZUL_ROJO":
			return "#8A2BE2";
		case "ROJO":
			return "#FF0000";
		case "NEGRO":
			return "#000000";
		default:
			return "#CCCCCC";
		}
	}
}
