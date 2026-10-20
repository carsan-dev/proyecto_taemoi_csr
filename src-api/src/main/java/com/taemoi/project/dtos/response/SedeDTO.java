package com.taemoi.project.dtos.response;

import com.taemoi.project.entities.Sede;

/**
 * Una sede de la escuela activa (pieza E8-2, D8-2). {@code aforoTurno} {@code null} = el límite de turno de la
 * escuela.
 */
public record SedeDTO(Long id, String nombre, String direccion, String localidad, String codigoPostal, String telefono,
		String email, String codigoClubFederacion, Integer aforoTurno, boolean activa) {

	public static SedeDTO de(Sede sede) {
		return new SedeDTO(sede.getId(), sede.getNombre(), sede.getDireccion(), sede.getLocalidad(),
				sede.getCodigoPostal(), sede.getTelefono(), sede.getEmail(), sede.getCodigoClubFederacion(),
				sede.getAforoTurno(), sede.isActiva());
	}
}
