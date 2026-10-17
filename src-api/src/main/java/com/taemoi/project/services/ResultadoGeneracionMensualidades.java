package com.taemoi.project.services;

import java.util.List;

import com.taemoi.project.dtos.response.CuantiaPersonalizadaSuperiorDTO;

/**
 * Resultado de una generación masiva de mensualidades.
 *
 * @param alumnosOmitidosSedeInactiva         alumnos saltados por ser de una sede inactiva (D6-17, D6-23); 0 si todas
 *                                            están activas
 * @param alumnosCuantiaPersonalizadaSuperior mensualidades creadas con una cuantía personalizada mayor que la tarifa de
 *                                            la sede (entrega 6-4, D6-28); vacía sin tarifas por sede
 */
public record ResultadoGeneracionMensualidades(int alumnosOmitidosSedeInactiva,
		List<CuantiaPersonalizadaSuperiorDTO> alumnosCuantiaPersonalizadaSuperior) {

	public ResultadoGeneracionMensualidades {
		alumnosCuantiaPersonalizadaSuperior = alumnosCuantiaPersonalizadaSuperior == null ? List.of()
				: List.copyOf(alumnosCuantiaPersonalizadaSuperior);
	}
}
