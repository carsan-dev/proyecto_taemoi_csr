package com.taemoi.project.plataforma;

import com.taemoi.project.marca.TemaEscuela;

/**
 * Respuesta de {@code PUT /api/plataforma/escuelas/{organizacionId}/marca} (pieza 8b-T): los campos de
 * {@link EscuelaPlataformaDTO} (con el color tal cual quedó guardado) más el tema que se calcula de él.
 */
public record EscuelaPlataformaConTemaDTO(Long id, String nombre, boolean tieneLogo, boolean marcaHeredada,
		String colorPrimario, TemaEscuela tema) {

	public static EscuelaPlataformaConTemaDTO de(EscuelaPlataformaDTO escuela, TemaEscuela tema) {
		return new EscuelaPlataformaConTemaDTO(escuela.id(), escuela.nombre(), escuela.tieneLogo(),
				escuela.marcaHeredada(), escuela.colorPrimario(), tema);
	}
}
