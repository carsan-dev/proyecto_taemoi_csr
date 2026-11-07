package com.taemoi.project.dtos.response;

import com.taemoi.project.entities.Sede;

/**
 * Una sede de la escuela activa (pieza E8-2, D8-2). {@code aforoTurno} {@code null} = el límite de turno de la
 * escuela. Web por plantilla (W2): {@code slug}, {@code lugar}, {@code indicacionesAcceso} y las fotos (ids de imagen,
 * solo lectura); {@code null} si no se han puesto.
 */
public record SedeDTO(Long id, String nombre, String direccion, String localidad, String codigoPostal, String telefono,
		String email, String codigoClubFederacion, Integer aforoTurno, boolean activa, String slug, String lugar,
		String indicacionesAcceso, Long fotoImagenId, Long foto2ImagenId) {

	public static SedeDTO de(Sede sede) {
		return new SedeDTO(sede.getId(), sede.getNombre(), sede.getDireccion(), sede.getLocalidad(),
				sede.getCodigoPostal(), sede.getTelefono(), sede.getEmail(), sede.getCodigoClubFederacion(),
				sede.getAforoTurno(), sede.isActiva(), sede.getSlug(), sede.getLugar(), sede.getIndicacionesAcceso(),
				sede.getFotoImagenId(), sede.getFoto2ImagenId());
	}
}
