package com.taemoi.project.legado;

/**
 * Oráculo: el dibujo de cinturones de {@code PDFServiceImpl} antes de V30 (colores derivados del código del
 * grado), copiado literalmente. Los tests comprueban que, con los colores que V30 guarda en Taemoi, el dibujo
 * nuevo es idéntico (salvo el ROJO de kickboxing, marrón por la decisión D4) y que un grado sin colores en el
 * catálogo se sigue dibujando igual que aquí.
 */
public final class CinturonesPdfLegado {

	private CinturonesPdfLegado() {
	}

	/** {@code generarCinturonInlineHTML(String, int, int)}. */
	public static String inline(String tipo, int width, int height) {
		if (tipo == null) {
			return "";
		}

		StringBuilder html = new StringBuilder();

		if (tipo.contains("ROJO_NEGRO")) {
			String[] parts = tipo.split("_");
			String colorInferior = obtenerColorPorNombre(parts[0]);
			String colorSuperior = obtenerColorPorNombre(parts[1]);
			int stripeCount = 0;
			try {
				stripeCount = Integer.parseInt(parts[2]);
			} catch (Exception e) {
			}

			html.append("<div style='position: relative; width: ").append(width).append("px; height: ").append(height)
					.append("px; display: inline-block; vertical-align: middle; border: 1px solid #495057;'>");
			html.append("<div style='background-color: ").append(colorSuperior)
					.append("; position: absolute; top: 0; left: 0; right: 0; height: 50%; z-index: 1;'></div>");
			html.append("<div style='background-color: ").append(colorInferior)
					.append("; position: absolute; bottom: 0; left: 0; right: 0; height: 50%; z-index: 1;'></div>");

			int stripeWidth = Math.max(2, width / 15);
			int gap = 1;
			int initialMargin = 3;
			for (int i = 0; i < stripeCount; i++) {
				int rightOffset = initialMargin + i * (stripeWidth + gap);
				html.append("<div style='position: absolute; right:").append(rightOffset).append("px; width:")
						.append(stripeWidth)
						.append("px; top: 50%; transform: translateY(-50%); height: 80%; background-color: #FFD700; z-index: 10;'></div>");
			}
			html.append("</div>");
		} else if (tipo.contains("DAN")
				|| (tipo.contains("PUM") && !tipo.contains("ROJO_NEGRO"))) {
			html.append("<div style='background-color: ").append(obtenerColorCinturon(tipo)).append("; width: ")
					.append(width).append("px; height: ").append(height)
					.append("px; position: relative; display: inline-block; vertical-align: middle; border: 1px solid #495057;'>");

			int stripeCount = 0;
			if (tipo.contains("DAN")) {
				String[] parts = tipo.split("_");
				try {
					stripeCount = Integer.parseInt(parts[1]);
				} catch (Exception e) {
				}
			}

			int stripeWidth = Math.max(2, width / 15);
			int gap = 1;
			int initialMargin = 3;
			for (int i = 0; i < stripeCount; i++) {
				int rightOffset = initialMargin + i * (stripeWidth + gap);
				html.append("<div style='position: absolute; right:").append(rightOffset).append("px; width:")
						.append(stripeWidth)
						.append("px; top: 50%; transform: translateY(-50%); height: 80%; background-color: #FFD700; z-index: 10;'></div>");
			}
			html.append("</div>");
		} else if (tipo.contains("_")) {
			String[] parts = tipo.split("_");
			String colorSuperior = obtenerColorPorNombre(parts[1]);
			String colorInferior = obtenerColorPorNombre(parts[0]);
			html.append("<div style='position: relative; width: ").append(width).append("px; height: ").append(height)
					.append("px; display: inline-block; vertical-align: middle; border: 1px solid #495057;'>");
			html.append("<div style='background-color: ").append(colorSuperior)
					.append("; position: absolute; top: 0; left: 0; right: 0; height: 50%;'></div>");
			html.append("<div style='background-color: ").append(colorInferior)
					.append("; position: absolute; bottom: 0; left: 0; right: 0; height: 50%;'></div>");
			html.append("</div>");
		} else {
			html.append("<div style='background-color: ").append(obtenerColorCinturon(tipo)).append("; width: ")
					.append(width).append("px; height: ").append(height)
					.append("px; display: inline-block; vertical-align: middle; border: 1px solid #495057;'></div>");
		}

		return html.toString();
	}

	/** Cinturón del encabezado de cada grado en los informes por grado (bloque de {@code generarInformePorGrado}). */
	public static String encabezado(String tipo) {
		StringBuilder html = new StringBuilder();
		if (tipo.contains("ROJO_NEGRO")) {
			String[] parts = tipo.split("_");
			String colorInferior = obtenerColorPorNombre(parts[0]);
			String colorSuperior = obtenerColorPorNombre(parts[1]);
			int stripeCount = 0;
			try {
				stripeCount = Integer.parseInt(parts[2]);
			} catch (Exception e) {
			}
			html.append("<div class='cinturon doble' style='position: relative;'>");
			html.append(
					"<div class='superior' style='background-color: " + colorSuperior + "; z-index: 1;'></div>");
			html.append(
					"<div class='inferior' style='background-color: " + colorInferior + "; z-index: 1;'></div>");
			int stripeWidth = 6;
			int gap = 2;
			int initialMargin = 6;
			for (int i = 0; i < stripeCount; i++) {
				int rightOffset = initialMargin + i * (stripeWidth + gap);
				html.append("<div class='raya' style='right:" + rightOffset + "px; width:" + stripeWidth
						+ "px; z-index: 2;'></div>");
			}
			html.append("</div>");
		} else if (tipo.contains("DAN")
				|| (tipo.contains("PUM") && !tipo.contains("ROJO_NEGRO"))) {
			html.append("<div class='cinturon' style='background-color: " + obtenerColorCinturon(tipo) + ";'>");
			try {
				int stripeCount = 0;
				if (tipo.contains("DAN")) {
					String[] parts = tipo.split("_");
					stripeCount = Integer.parseInt(parts[1]);
				}
				int stripeWidth = 6;
				int gap = 2;
				int initialMargin = 6;
				for (int i = 0; i < stripeCount; i++) {
					int rightOffset = initialMargin + i * (stripeWidth + gap);
					html.append("<div class='raya' style='right:" + rightOffset + "px; width:" + stripeWidth
							+ "px;'></div>");
				}
			} catch (Exception e) {
			}
			html.append("</div>");
		} else if (tipo.contains("_")) {
			String[] parts = tipo.split("_");
			String colorSuperior = obtenerColorPorNombre(parts[1]);
			String colorInferior = obtenerColorPorNombre(parts[0]);
			html.append("<div class='cinturon doble'>");
			html.append("<div class='superior' style='background-color: " + colorSuperior + ";'></div>");
			html.append("<div class='inferior' style='background-color: " + colorInferior + ";'></div>");
			html.append("</div>");
		} else {
			String cinturonStyle = "background-color: " + obtenerColorCinturon(tipo) + ";";
			html.append("<div class='cinturon' style='" + cinturonStyle + "'></div>");
		}
		return html.toString();
	}

	/** {@code generateBeltCellHtml(String)}: celda del listado de asistencia (con su propia paleta). */
	public static String celdaAsistencia(String tipoGrado) {
		if (tipoGrado == null) {
			return "<td class='cinturon-blanco'></td>";
		}

		String enumName = tipoGrado;

		if (enumName.startsWith("NEGRO_")) {
			String[] parts = enumName.split("_");
			if (parts.length >= 2) {
				String danNumber = parts[1];
				return "<td class='cinturon-negro'>" + danNumber + "º</td>";
			}
			return "<td class='cinturon-negro'></td>";
		}

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

		String[] parts = enumName.split("_");
		if (parts.length == 2 && !enumName.contains("DAN") && !enumName.contains("PUM")) {
			String color1 = getBeltColorHex(parts[1]);
			String color2 = getBeltColorHex(parts[0]);
			return "<td><div class='cinturon-split'>" + "<div class='cinturon-half-superior' style='background-color: "
					+ color1 + ";'></div>" + "<div class='cinturon-half-inferior' style='background-color: " + color2
					+ ";'></div></div></td>";
		}

		String color = parts[0].toLowerCase();
		return "<td class='cinturon-" + color + "'></td>";
	}

	private static String getBeltColorHex(String colorName) {
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

	private static String obtenerColorCinturon(String tipo) {
		if (tipo == null)
			return "#CCCCCC";
		switch (tipo) {
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
		default:
			return "#CCCCCC";
		}
	}

	private static String obtenerColorPorNombre(String nombre) {
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
