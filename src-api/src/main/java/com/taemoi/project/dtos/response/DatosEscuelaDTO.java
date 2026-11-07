package com.taemoi.project.dtos.response;

import com.taemoi.project.entities.Organizacion;

/**
 * Pieza 394: datos de la escuela activa para «Escuela · Datos de la escuela» (solo el propietario y el administrador de
 * plataforma). Los mismos campos que {@code DatosEscuelaRequest}; un dato sin rellenar va a {@code null}.
 * {@code diaLimiteMensualidad}: último día del mes para pagar la mensualidad (5 por defecto).
 */
public record DatosEscuelaDTO(
		String nombreLegal,
		String cif,
		String direccion,
		String emailContacto,
		String telefono,
		String firmanteNombre,
		String firmanteCargo,
		String firmanteNif,
		int diaLimiteMensualidad) {

	public static DatosEscuelaDTO de(Organizacion escuela) {
		Integer dia = escuela.getDiaLimiteMensualidad();
		return new DatosEscuelaDTO(escuela.getNombreLegal(), escuela.getCif(), escuela.getDireccion(),
				escuela.getEmailContacto(), escuela.getTelefono(), escuela.getFirmanteNombre(), escuela.getFirmanteCargo(),
				escuela.getFirmanteNif(), dia != null ? dia : Organizacion.DIA_LIMITE_MENSUALIDAD_POR_DEFECTO);
	}
}
