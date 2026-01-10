package com.taemoi.project.utils;

import com.taemoi.project.entities.Deporte;
import com.taemoi.project.entities.TipoGrado;

/**
 * Utilidades para la gestión de grados en diferentes deportes.
 */
public class GradoUtils {

	private GradoUtils() {
		// Utility class
	}

	/**
	 * Obtiene el nombre a mostrar para un grado según el deporte.
	 * En Kickboxing, el grado ROJO se muestra como MARRÓN.
	 *
	 * @param tipoGrado El tipo de grado
	 * @param deporte   El deporte del alumno
	 * @return El nombre del grado a mostrar
	 */
	public static String getNombreGradoParaDeporte(TipoGrado tipoGrado, Deporte deporte) {
		if (tipoGrado == null) {
			return "N/A";
		}

		String nombreGrado = tipoGrado.getNombre();
		if (nombreGrado == null) {
			return "N/A";
		}

		// En Kickboxing, el grado ROJO se llama MARRÓN
		if (deporte == Deporte.KICKBOXING && tipoGrado == TipoGrado.ROJO) {
			return "MARRÓN";
		}

		return nombreGrado;
	}

	/**
	 * Obtiene el nombre a mostrar para un grado según el deporte (sobrecarga con String).
	 * Útil cuando solo se tiene el nombre del grado como String.
	 *
	 * @param nombreGrado El nombre del grado
	 * @param deporte     El deporte del alumno
	 * @return El nombre del grado a mostrar
	 */
	public static String getNombreGradoParaDeporte(String nombreGrado, Deporte deporte) {
		if (nombreGrado == null || nombreGrado.isEmpty()) {
			return "N/A";
		}

		// En Kickboxing, el grado ROJO se llama MARRÓN
		if (deporte == Deporte.KICKBOXING && nombreGrado.equalsIgnoreCase("ROJO")) {
			return "MARRÓN";
		}

		return nombreGrado;
	}
}
