package com.taemoi.project.services;

import java.util.List;

import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.Usuario;

/**
 * Punto ÚNICO del acceso de familia a sus alumnos (fase3-sedes-permisos.md §5 y D6; fase 3, entrega 2a). La
 * regla vive en una sola consulta ({@code AlumnoRepository.ACCESO_FAMILIA}): vínculo explícito en
 * {@code usuario_alumno} y, solo en las escuelas con {@code comportamiento_legado_taemoi}, el email de antes.
 *
 * <p>No decide nada de los roles de gestión: quien llama sigue dando acceso total a {@code ROLE_ADMIN} y
 * {@code ROLE_MANAGER} antes de preguntar aquí.
 */
public interface AccesoFamiliaService {

	/** Ids (ascendentes) de los alumnos que la cuenta ve como familia en la organización. */
	List<Long> idsAccesibles(Usuario usuario, Long organizacionId);

	/** Si la cuenta ve como familia ese alumno de la organización. */
	boolean puedeAcceder(Usuario usuario, Long organizacionId, Long alumnoId);

	/** Si la cuenta ve como familia algún alumno de la organización (puerta de entrada de una cuenta solo familia). */
	boolean tieneAlgunoAccesible(Usuario usuario, Long organizacionId);

	/**
	 * Alumnos (entidades, en orden de id) que la cuenta ve como familia en la organización ACTIVA
	 * ({@code TenantContext}); vacío si no hay ninguna fijada.
	 */
	List<Alumno> alumnosAccesiblesEnOrganizacionActiva(Usuario usuario);
}
