package com.taemoi.project.entidades;

public enum TipoGrado {
	BLANCO("Blanco"), BLANCO_AMARILLO("Blanco-Amarillo"), AMARILLO("Amarillo"), AMARILLO_NARANJA("Amarillo-Naranja"),
	NARANJA("Naranja"), NARANJA_VERDE("Naranja-Verde"), VERDE("Verde"), VERDE_AZUL("Verde-Azul"), AZUL("Azul"),
	AZUL_ROJO("Azul-Rojo"), ROJO("Rojo"), ROJO_NEGRO_1º_PUM("Rojo-Negro-1º-Pum"),
	ROJO_NEGRO_2º_PUM("Rojo-Negro-2º-Pum"), ROJO_NEGRO_3º_PUM("Rojo-Negro-3º-Pum"),
	NEGRO_1º_DAN("Negro-1º-Dan"), NEGRO_2º_DAN("Negro-2º-Dan"), NEGRO_3º_DAN("Negro-3º-Dan"),
	NEGRO_4º_DAN("Negro-4º-Dan"), NEGRO_5º_DAN("Negro-5º-Dan"),;

	private final String nombre;

	private TipoGrado(String nombre) {
		this.nombre = nombre;
	}

	public String getNombre() {
		return nombre;
	}

	/**
	 * Convierte un nombre de tipo de grado en el correspondiente valor de la
	 * enumeración TipoGrado.
	 *
	 * @param nombre El nombre del tipo de grado a convertir.
	 * @return El valor de TipoGrado correspondiente al nombre proporcionado.
	 * @throws IllegalArgumentException si el nombre no coincide con ningún tipo de
	 *                                  grado válido.
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
