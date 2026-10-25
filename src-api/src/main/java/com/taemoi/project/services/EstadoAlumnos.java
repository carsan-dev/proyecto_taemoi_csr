package com.taemoi.project.services;

import java.util.Locale;

/**
 * Qué alumnos pide el listado según su ficha ({@code activo}): solo activos, solo inactivos o todos. Los anonimizados
 * no salen en ningún caso.
 */
public enum EstadoAlumnos {
	ACTIVOS, INACTIVOS, TODOS;

	/** El equivalente del parámetro de siempre {@code incluirInactivos}: true, todos; false, solo activos. */
	public static EstadoAlumnos desdeIncluirInactivos(boolean incluirInactivos) {
		return incluirInactivos ? TODOS : ACTIVOS;
	}

	/**
	 * El parámetro {@code estado} del listado ({@code activos}, {@code inactivos} o {@code todos}, sin distinguir
	 * mayúsculas).
	 *
	 * @throws IllegalArgumentException si el valor no es ninguno de los tres.
	 */
	public static EstadoAlumnos desdeParametro(String valor) {
		if (valor != null) {
			for (EstadoAlumnos estado : values()) {
				if (estado.name().equals(valor.trim().toUpperCase(Locale.ROOT))) {
					return estado;
				}
			}
		}
		throw new IllegalArgumentException("Estado no válido: usa activos, inactivos o todos.");
	}
}
