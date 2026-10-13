package com.taemoi.project.dtos.response;

import java.time.LocalDate;
import java.util.List;

import com.taemoi.project.entities.ParentescoTutor;

/**
 * Grupo de alumnos de la escuela activa que comparten el mismo NIF normalizado, con la propuesta de
 * corrección. {@code nif} es el NIF normalizado completo (solo para administradores, como en la ficha;
 * la respuesta va con {@code Cache-Control: no-store}). {@code grupo} es una referencia opaca (el NIF
 * cifrado y ligado a la escuela) que se devuelve tal cual al aplicar, para que el DNI no viaje en el
 * cuerpo de la petición ni quede en su auditoría.
 */
public record DniCompartidoDTO(String grupo, String nif, List<Miembro> miembros, Propuesta propuesta) {

	public record Miembro(Long id, String nombre, String apellidos, LocalDate fechaNacimiento, Integer edad,
			boolean activo, List<TutorResumen> tutores) {
	}

	public record TutorResumen(Long id, String nombre, String apellidos, String nif,
			ParentescoTutor parentesco, boolean responsableLegal) {
	}

	/**
	 * {@code tipo}: TITULAR_ADULTO (el DNI es del único adulto), TUTOR_NO_ALUMNO (todos menores: es de
	 * un tutor que no es alumno) o REVISION_MANUAL (sin propuesta automática).
	 */
	public record Propuesta(String tipo, String descripcion, Long titularId, String nombreTutor,
			List<Cambio> cambios) {
	}

	/**
	 * Cambio previsto en un miembro. {@code tutorExistenteId}: tutor sin NIF con el mismo nombre al que
	 * se le pondrá el NIF en lugar de crear otro. {@code responsableLegal}: el tutor creado o completado
	 * pasará a ser responsable legal (menor sin responsable).
	 */
	public record Cambio(Long alumnoId, boolean quitarNif, boolean crearTutor, Long tutorExistenteId,
			boolean responsableLegal) {
	}
}
