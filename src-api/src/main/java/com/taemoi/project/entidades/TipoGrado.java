package com.taemoi.project.entidades;

public enum TipoGrado {
	BLANCO("Blanco"), BLANCO_AMARILLO("Blanco-Amarillo"), AMARILLO("Amarillo"), AMARILLO_NARANJA("Amarillo-Naranja"),
	NARANJA("Naranja"), NARANJA_VERDE("Naranja-Verde"), VERDE("Verde"), VERDE_AZUL("Verde-Azul"), AZUL("Azul"),
	AZUL_ROJO("Azul-Rojo"), ROJO("Rojo"), ROJO_NEGRO_1_PUM("Rojo-Negro-1-Pum"),
	ROJO_NEGRO_2_PUM("Rojo-Negro-2-Pum"), ROJO_NEGRO_3_PUM("Rojo-Negro-3-Pum"),
	NEGRO_1_DAN("Negro-1-Dan"), NEGRO_2_DAN("Negro-2-Dan"), NEGRO_3_DAN("Negro-3-Dan"),
	NEGRO_4_DAN("Negro-4-Dan"), NEGRO_5_DAN("Negro-5-Dan"),;

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
	
    /**
     * Obtiene el siguiente TipoGrado en el orden definido por la enumeración.
     *
     * @return El siguiente TipoGrado, o null si es el último grado.
     */
    public TipoGrado siguiente() {
        int ordinal = this.ordinal();
        TipoGrado[] values = TipoGrado.values();
        if (ordinal < values.length - 1) {
            return values[ordinal + 1];
        } else {
            return null; // O lanza una excepción si no hay siguiente grado
        }
    }
}
