package com.taemoi.project.dtos.response;

import com.taemoi.project.entities.ParentescoTutor;
import com.taemoi.project.entities.Tutor;

public record TutorDTO(Long id, String nombre, String apellidos, String nif, ParentescoTutor parentesco,
		String telefono, String email, boolean responsableLegal, Integer orden) {

	public static TutorDTO de(Tutor t) {
		return new TutorDTO(t.getId(), t.getNombre(), t.getApellidos(), t.getNif(), t.getParentesco(),
				t.getTelefono(), t.getEmail(), t.isResponsableLegal(), t.getOrden());
	}
}
