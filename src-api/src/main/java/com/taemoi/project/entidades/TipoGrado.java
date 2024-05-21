package com.taemoi.project.entidades;

public enum TipoGrado {
	BLANCO("Blanco"), BLANCO_AMARILLO("Blanco-Amarillo"), AMARILLO("Amarillo"), AMARILLO_NARANJA("Amarillo-Naranja"),
	NARANJA("Naranja"), NARANJA_VERDE("Naranja-Verde"), VERDE("Verde"), VERDE_AZUL("Verde-Azul"), AZUL("Azul"),
	AZUL_ROJO("Azul-Rojo"), ROJO("Rojo"), ROJO_NEGRO("Rojo-Negro"), NEGRO("Negro");

	private final String nombre;

	private TipoGrado(String nombre) {
		this.nombre = nombre;
	}

	public String getNombre() {
		return nombre;
	}

	/**
	 * Convierte un nombre de tipo de grado en el correspondiente valor de la enumeración TipoGrado.
	 *
	 * @param nombre El nombre del tipo de grado a convertir.
	 * @return El valor de TipoGrado correspondiente al nombre proporcionado.
	 * @throws IllegalArgumentException si el nombre no coincide con ningún tipo de grado válido.
	 */
	public static TipoGrado fromNombre(String nombre) {
		for (TipoGrado tipo : TipoGrado.values()) {
			if (tipo.nombre.equalsIgnoreCase(nombre)) {
				return tipo;
			}
		}
		throw new IllegalArgumentException("Tipo de grado no válido: " + nombre);
	}
}
