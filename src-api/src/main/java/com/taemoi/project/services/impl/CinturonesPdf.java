package com.taemoi.project.services.impl;

import com.taemoi.project.entities.Grado;
import com.taemoi.project.utils.ColoresGrado;
import com.taemoi.project.utils.ColoresGrado.Cinturon;

/**
 * HTML de los cinturones de los informes PDF. Los colores salen del catálogo ({@link ColoresGrado}: color_1
 * abajo, color_2 arriba); las franjas de PUM/DAN, del código. Con los colores que V30 guarda en Taemoi el
 * resultado es el mismo que antes de V30 (salvo el ROJO de kickboxing, marrón por la decisión D4); un grado
 * sin colores en el catálogo se dibuja exactamente como antes (colores derivados del código).
 */
final class CinturonesPdf {

	private CinturonesPdf() {
	}

	/** Cinturón en línea (celdas de grado, convocatorias, competidores, mensualidades); vacío sin cinturón. */
	static String inline(Cinturon c, int width, int height) {
		if (c == null) {
			return "";
		}
		StringBuilder html = new StringBuilder();
		int stripeWidth = Math.max(2, width / 15);
		if (c.doble()) {
			String zIndex = c.rayado() ? " z-index: 1;" : "";
			html.append("<div style='position: relative; width: ").append(width).append("px; height: ").append(height)
					.append("px; display: inline-block; vertical-align: middle; border: 1px solid #495057;'>");
			html.append("<div style='background-color: ").append(c.superior())
					.append("; position: absolute; top: 0; left: 0; right: 0; height: 50%;").append(zIndex)
					.append("'></div>");
			html.append("<div style='background-color: ").append(c.inferior())
					.append("; position: absolute; bottom: 0; left: 0; right: 0; height: 50%;").append(zIndex)
					.append("'></div>");
			franjasInline(html, c.franjas(), stripeWidth);
			html.append("</div>");
		} else if (c.rayado()) {
			html.append("<div style='background-color: ").append(c.inferior()).append("; width: ").append(width)
					.append("px; height: ").append(height)
					.append("px; position: relative; display: inline-block; vertical-align: middle; border: 1px solid #495057;'>");
			franjasInline(html, c.franjas(), stripeWidth);
			html.append("</div>");
		} else {
			html.append("<div style='background-color: ").append(c.inferior()).append("; width: ").append(width)
					.append("px; height: ").append(height)
					.append("px; display: inline-block; vertical-align: middle; border: 1px solid #495057;'></div>");
		}
		return html.toString();
	}

	private static void franjasInline(StringBuilder html, int franjas, int stripeWidth) {
		int gap = 1;
		int initialMargin = 3;
		for (int i = 0; i < franjas; i++) {
			int rightOffset = initialMargin + i * (stripeWidth + gap);
			html.append("<div style='position: absolute; right:").append(rightOffset).append("px; width:")
					.append(stripeWidth)
					.append("px; top: 50%; transform: translateY(-50%); height: 80%; background-color: #FFD700; z-index: 10;'></div>");
		}
	}

	/** Cinturón del encabezado de cada grado en los informes por grado (clases {@code cinturon}, {@code raya}). */
	static String encabezado(Cinturon c) {
		StringBuilder html = new StringBuilder();
		int stripeWidth = 6;
		int gap = 2;
		int initialMargin = 6;
		if (c.doble() && c.rayado()) {
			html.append("<div class='cinturon doble' style='position: relative;'>");
			html.append("<div class='superior' style='background-color: " + c.superior() + "; z-index: 1;'></div>");
			html.append("<div class='inferior' style='background-color: " + c.inferior() + "; z-index: 1;'></div>");
			for (int i = 0; i < c.franjas(); i++) {
				int rightOffset = initialMargin + i * (stripeWidth + gap);
				html.append("<div class='raya' style='right:" + rightOffset + "px; width:" + stripeWidth
						+ "px; z-index: 2;'></div>");
			}
			html.append("</div>");
		} else if (c.doble()) {
			html.append("<div class='cinturon doble'>");
			html.append("<div class='superior' style='background-color: " + c.superior() + ";'></div>");
			html.append("<div class='inferior' style='background-color: " + c.inferior() + ";'></div>");
			html.append("</div>");
		} else {
			html.append("<div class='cinturon' style='background-color: " + c.inferior() + ";'>");
			for (int i = 0; i < c.franjas(); i++) {
				int rightOffset = initialMargin + i * (stripeWidth + gap);
				html.append("<div class='raya' style='right:" + rightOffset + "px; width:" + stripeWidth
						+ "px;'></div>");
			}
			html.append("</div>");
		}
		return html.toString();
	}

	/**
	 * Celda del cinturón en el listado de asistencia. Con colores en el catálogo usa esos colores (número de
	 * DAN/PUM sobre el cinturón); sin ellos, la paleta y las clases propias de este listado, como antes de V30.
	 */
	static String celdaAsistencia(Grado grado) {
		if (grado == null) {
			return "<td class='cinturon-blanco'></td>";
		}
		if (grado.getColor1() == null) {
			return celdaAsistenciaDesdeCodigo(grado.getCodigo());
		}
		Cinturon c = ColoresGrado.de(grado);
		if (c.doble()) {
			if (c.franjas() > 0) {
				return "<td><div class='cinturon-split' style='position: relative;'>"
						+ "<div class='cinturon-half-superior' style='background-color: " + c.superior() + ";'></div>"
						+ "<div class='cinturon-half-inferior' style='background-color: " + c.inferior() + ";'></div>"
						+ "<div style='position: absolute; top: 0; left: 0; width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; z-index: 10;'>"
						+ "<span style='color: #ffffff; font-weight: 700; font-size: 7pt;'>" + c.franjas() + "º</span>"
						+ "</div></div></td>";
			}
			return "<td><div class='cinturon-split'>" + "<div class='cinturon-half-superior' style='background-color: "
					+ c.superior() + ";'></div>" + "<div class='cinturon-half-inferior' style='background-color: "
					+ c.inferior() + ";'></div></div></td>";
		}
		if (c.franjas() > 0) {
			return "<td class='cinturon-negro' style='background: " + c.inferior() + ";'>" + c.franjas() + "º</td>";
		}
		return "<td style='background: " + c.inferior() + ";'></td>";
	}

	/** Celda del listado de asistencia derivada del código (antes de V30, con la paleta de este listado). */
	static String celdaAsistenciaDesdeCodigo(String tipoGrado) {
		if (tipoGrado == null) {
			return "<td class='cinturon-blanco'></td>";
		}

		String enumName = tipoGrado;

		// Handle black belt grades (NEGRO_X_DAN) - show dan number
		if (enumName.startsWith("NEGRO_")) {
			String[] parts = enumName.split("_");
			if (parts.length >= 2) {
				String danNumber = parts[1]; // e.g., "1" from NEGRO_1_DAN
				return "<td class='cinturon-negro'>" + danNumber + "º</td>";
			}
			return "<td class='cinturon-negro'></td>";
		}

		// Handle ROJO_NEGRO grades (pre-black belt) - show split colors with PUM number
		if (enumName.startsWith("ROJO_NEGRO_")) {
			String[] parts = enumName.split("_");
			String pumNumber = parts.length >= 3 ? parts[2] : "";
			return "<td><div class='cinturon-split' style='position: relative;'>"
					+ "<div class='cinturon-half-superior' style='background-color: #212529;'></div>"
					+ "<div class='cinturon-half-inferior' style='background-color: #f44336;'></div>"
					+ "<div style='position: absolute; top: 0; left: 0; width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; z-index: 10;'>"
					+ "<span style='color: #ffffff; font-weight: 700; font-size: 7pt;'>" + pumNumber + "º</span>"
					+ "</div></div></td>";
		}

		// Check if it's a half-color belt (e.g., BLANCO_AMARILLO, AMARILLO_NARANJA)
		String[] parts = enumName.split("_");
		if (parts.length == 2 && !enumName.contains("DAN") && !enumName.contains("PUM")) {
			String color1 = colorAsistencia(parts[1]);
			String color2 = colorAsistencia(parts[0]);
			// El borde viene de la celda <td>, no necesitamos añadir uno extra
			return "<td><div class='cinturon-split'>" + "<div class='cinturon-half-superior' style='background-color: "
					+ color1 + ";'></div>" + "<div class='cinturon-half-inferior' style='background-color: " + color2
					+ ";'></div></div></td>";
		}

		// Single color belt
		String color = parts[0].toLowerCase();
		return "<td class='cinturon-" + color + "'></td>";
	}

	/** Paleta propia del listado de asistencia (antes {@code getBeltColorHex}); solo para grados sin colores. */
	private static String colorAsistencia(String colorName) {
		switch (colorName) {
		case "BLANCO":
			return "#ffffff";
		case "AMARILLO":
			return "#ffeb3b";
		case "NARANJA":
			return "#ff9800";
		case "VERDE":
			return "#4caf50";
		case "AZUL":
			return "#2196f3";
		case "ROJO":
			return "#f44336";
		case "NEGRO":
			return "#212529";
		default:
			return "#cccccc";
		}
	}
}
