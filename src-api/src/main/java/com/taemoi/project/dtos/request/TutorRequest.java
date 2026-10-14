package com.taemoi.project.dtos.request;

import com.taemoi.project.entities.ParentescoTutor;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Alta o modificación de un tutor. {@code parentesco} nulo = {@code OTRO}; {@code orden} nulo = al
 * final (alta) o sin cambios (modificación); {@code responsableLegal} nulo = {@code false} (alta) o
 * sin cambios (modificación).
 */
public record TutorRequest(
		@NotBlank(message = "El nombre del tutor es obligatorio") @Size(max = 180) String nombre,
		@Size(max = 160) String apellidos,
		@Size(max = 16) String nif,
		ParentescoTutor parentesco,
		@Size(max = 30) String telefono,
		@Email(message = "El correo del tutor no es válido") @Size(max = 180) String email,
		Boolean responsableLegal,
		@PositiveOrZero Integer orden) {
}
