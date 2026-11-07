package com.taemoi.project.web;

import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Temas de la web por plantilla (fase4-web-plan.md §1, decisiones 450 y 469): los cuatro aprobados, en el orden de los
 * bocetos. Son los valores de {@code web_escuela.tema}. El panel enseña solo los ya construidos en el frontend (§9,
 * n.º 12): la API devuelve los cuatro y el panel filtra.
 */
public final class TemasWeb {

	public static final List<String> TODOS = List.of("linea-comarcal", "chandal", "sede-por-pantalla", "estandar");

	public static final String FALTA_TEMA = "Falta el tema de la web.";
	public static final String TEMA_NO_VALIDO = "Tema no válido. Valores admitidos: " + String.join(", ", TODOS) + ".";

	private TemasWeb() {
	}

	public static boolean esValido(String tema) {
		return tema != null && TODOS.contains(tema);
	}

	/**
	 * El tema de un cuerpo {@code {"tema": "…"}}: texto y uno de {@link #TODOS} (tal cual, sin cambiar mayúsculas).
	 *
	 * @throws IllegalArgumentException (400) sin cuerpo, sin la clave, con {@code null} o con un valor que no vale
	 */
	public static String temaDe(JsonNode cuerpo) {
		if (cuerpo == null || !cuerpo.isObject() || !cuerpo.hasNonNull("tema")) {
			throw new IllegalArgumentException(FALTA_TEMA);
		}
		JsonNode valor = cuerpo.get("tema");
		if (!valor.isTextual() || !esValido(valor.asText())) {
			throw new IllegalArgumentException(TEMA_NO_VALIDO);
		}
		return valor.asText();
	}

	/** Valor para el registro de acciones: {@code sede-por-pantalla} → {@code SEDE_POR_PANTALLA} (nunca texto libre). */
	static String paraRegistro(String tema) {
		return tema.replace('-', '_').toUpperCase(Locale.ROOT);
	}
}
