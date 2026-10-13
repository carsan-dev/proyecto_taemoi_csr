package com.taemoi.project.dtos.response;

import java.time.LocalDate;
import java.util.List;

import com.taemoi.project.entities.ParentescoTutor;

/**
 * Grupo de alumnos de la escuela activa que comparten el mismo NIF normalizado, con la propuesta de
 * corrección. Nunca lleva el NIF completo: {@code nifEnmascarado} solo deja ver los 4 últimos
 * caracteres y {@code grupo} es una referencia opaca (el NIF cifrado y ligado a la escuela) que se
 * devuelve tal cual al aplicar.
 */
public record DniCompartidoDTO(String grupo, String nifEnmascarado, List<Miembro> miembros, Propuesta propuesta) {

	public record Miembro(Long id, String nombre, String apellidos, LocalDate fechaNacimiento, Integer edad,
			boolean activo, List<TutorResumen> tutores) {
	}

	public record TutorResumen(Long id, String nombre, String apellidos, String nifEnmascarado,
			ParentescoTutor parentesco, boolean responsableLegal) {
	}

	/**
	 * {@code tipo}: TITULAR_ADULTO (el DNI es del único adulto), TUTOR_NO_ALUMNO (todos menores: es de
	 * un tutor que no es alumno) o REVISION_MANUAL (sin propuesta automática).
	 */
	public record Propuesta(String tipo, String descripcion, Long titularId, String nombreTutor,
			List<Cambio> cambios) {
	}

	public record Cambio(Long alumnoId, boolean quitarNif, boolean crearTutor, boolean responsableLegal) {
	}
}
