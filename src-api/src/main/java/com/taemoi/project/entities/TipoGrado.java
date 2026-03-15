package com.taemoi.project.entities;

/**
 * Enumeración que representa los diferentes tipos de grado en artes marciales.
 * Incluye información sobre el nombre del grado, productos de examen asociados,
 * y productos de recompensa.
 */
public enum TipoGrado {
	BLANCO("Blanco", null, null),
	BLANCO_AMARILLO("Blanco-Amarillo", "DERECHO A EXAMEN BLANCO/AMARILLO", "DERECHO CAMBIO A BLANCO-AMARILLO RECOMPENSA"),
	AMARILLO("Amarillo", "DERECHO A EXAMEN AMARILLO", "DERECHO DE CAMBIO A AMARILLO POR RECOMPENSA"),
	AMARILLO_NARANJA("Amarillo-Naranja", "DERECHO A EXAMEN AMARILLO/NARANJA", "DERECHO DE CAMBIO A AMARILLO-NARANJA POR RECOMPENSA"),
	NARANJA("Naranja", "DERECHO A EXAMEN NARANJA", "DERECHO DE CAMBIO A NARANJA POR RECOMPENSA"),
	NARANJA_VERDE("Naranja-Verde", "DERECHO A EXAMEN NARANJA/VERDE", "DERECHO DE CAMBIO A NARANJA-VERDE POR RECOMPENSA"),
	VERDE("Verde", "DERECHO A EXAMEN VERDE", "DERECHO DE CAMBIO A VERDE RECOMPENSA"),
	VERDE_AZUL("Verde-Azul", "DERECHO A EXAMEN VERDE/AZUL", "DERECHO DE CAMBIO A VERDE-AZUL POR RECOMPENSA"),
	AZUL("Azul", "DERECHO A EXAMEN AZUL", "DERECHO DE CAMBIO A AZUL POR RECOMPENSA"),
	AZUL_ROJO("Azul-Rojo", "DERECHO A EXAMEN AZUL/ROJO", "DERECHO DE CAMBIO A AZUL-ROJO POR RECOMPENSA"),
	ROJO("Rojo", "DERECHO A EXAMEN ROJO", "DERECHO DE CAMBIO A ROJO POR RECOMPENSA"),
	ROJO_NEGRO_1_PUM("Rojo-Negro-1-Pum", "DERECHO A EXAMEN ROJO/NEGRO 1º PUM", null),
	ROJO_NEGRO_2_PUM("Rojo-Negro-2-Pum", "DERECHO A EXAMEN ROJO/NEGRO 2º PUM", null),
	ROJO_NEGRO_3_PUM("Rojo-Negro-3-Pum", null, null),
	NEGRO_1_DAN("Negro-1-Dan", "DERECHO A EXAMEN NEGRO 1º DAN", null),
	NEGRO_2_DAN("Negro-2-Dan", "DERECHO A EXAMEN NEGRO 2º DAN", null),
	NEGRO_3_DAN("Negro-3-Dan", "DERECHO A EXAMEN NEGRO 3º DAN", null),
	NEGRO_4_DAN("Negro-4-Dan", null, null),
	NEGRO_5_DAN("Negro-5-Dan", null, null);

	private final String nombre;
	private final String productoExamen;
	private final String productoRecompensa;

	/**
	 * Constructor del enum TipoGrado.
	 *
	 * @param nombre El nombre del grado.
	 * @param productoExamen El nombre del producto asociado al derecho de examen.
	 * @param productoRecompensa El nombre del producto asociado al pase de grado por recompensa.
	 */
	private TipoGrado(String nombre, String productoExamen, String productoRecompensa) {
		this.nombre = nombre;
		this.productoExamen = productoExamen;
		this.productoRecompensa = productoRecompensa;
	}

	public String getNombre() {
		return nombre;
	}

	/**
	 * Obtiene el nombre del producto asociado a este grado.
	 *
	 * @param porRecompensa true si se requiere el producto de recompensa, false para el producto de examen normal.
	 * @return El nombre del producto correspondiente.
	 * @throws IllegalArgumentException si se solicita un producto de recompensa para un grado que no lo tiene,
	 *                                  o si se solicita un producto de examen para un grado que no lo tiene.
	 */
	public String obtenerNombreProducto(boolean porRecompensa) {
		if (porRecompensa) {
			if (productoRecompensa == null) {
				throw new IllegalArgumentException(
					"El grado " + nombre + " no tiene producto de recompensa asociado");
			}
			return productoRecompensa;
		} else {
			if (productoExamen == null) {
				throw new IllegalArgumentException(
					"El grado " + nombre + " no tiene producto de examen asociado");
			}
			return productoExamen;
		}
	}

	/**
	 * Verifica si este grado tiene un producto de examen asociado.
	 *
	 * @return true si tiene producto de examen, false en caso contrario.
	 */
	public boolean tieneProductoExamen() {
		return productoExamen != null;
	}

	/**
	 * Verifica si este grado tiene un producto de recompensa asociado.
	 *
	 * @return true si tiene producto de recompensa, false en caso contrario.
	 */
	public boolean tieneProductoRecompensa() {
		return productoRecompensa != null;
	}

	public static TipoGrado fromProductoRecompensa(String concepto) {
		if (concepto == null) {
			return null;
		}
		String conceptoUpper = concepto.toUpperCase();
		for (TipoGrado tipo : TipoGrado.values()) {
			if (tipo.tieneProductoRecompensa()
					&& conceptoUpper.contains(tipo.obtenerNombreProducto(true).toUpperCase())) {
				return tipo;
			}
		}
		return null;
	}

	/**
	 * Obtiene el TipoGrado correspondiente a un concepto de producto de examen.
	 *
	 * @param concepto El concepto del producto de examen.
	 * @return El TipoGrado correspondiente, o null si no se encuentra.
	 */
	public static TipoGrado fromProductoExamen(String concepto) {
		if (concepto == null) {
			return null;
		}
		String conceptoUpper = concepto.toUpperCase();
		for (TipoGrado tipo : TipoGrado.values()) {
			if (tipo.tieneProductoExamen()
					&& conceptoUpper.contains(tipo.obtenerNombreProducto(false).toUpperCase())) {
				return tipo;
			}
		}
		return null;
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
