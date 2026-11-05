package com.taemoi.project.plataforma;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Fase 4, pieza F4-2 (decisión 444): el slug de una escuela nueva sale de su nombre: minúsculas, ASCII sin tildes, guiones
 * entre palabras y como mucho {@value #MAXIMO} caracteres (la columna {@code organizacion.slug}). Si ya existe, se le
 * añade {@code -2}, {@code -3}… recortando la base para no pasar del máximo.
 */
final class SlugEscuela {

	static final int MAXIMO = 60;
	/** Para un nombre sin ninguna letra ni cifra ASCII. */
	static final String POR_DEFECTO = "escuela";

	private SlugEscuela() {
	}

	static String base(String nombre) {
		String sinTildes = Normalizer.normalize(nombre == null ? "" : nombre, Normalizer.Form.NFD)
				.replaceAll("\\p{M}+", "");
		String slug = sinTildes.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
		slug = recortar(slug, MAXIMO);
		return slug.isEmpty() ? POR_DEFECTO : slug;
	}

	/** El primero libre de {@code base}, {@code base-2}, {@code base-3}… según {@code ocupado}. */
	static String libre(String nombre, Predicate<String> ocupado) {
		String base = base(nombre);
		if (!ocupado.test(base)) {
			return base;
		}
		for (int n = 2;; n++) {
			String sufijo = "-" + n;
			String candidato = recortar(base, MAXIMO - sufijo.length()) + sufijo;
			if (!ocupado.test(candidato)) {
				return candidato;
			}
		}
	}

	private static String recortar(String slug, int maximo) {
		String recortado = slug.length() > maximo ? slug.substring(0, maximo) : slug;
		return recortado.replaceAll("-+$", "");
	}
}
