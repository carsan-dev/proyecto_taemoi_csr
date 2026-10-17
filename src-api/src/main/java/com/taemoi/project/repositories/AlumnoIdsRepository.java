package com.taemoi.project.repositories;

import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.taemoi.project.entities.Alumno;

/** Fragmento de {@link AlumnoRepository}: ids con un predicado, sin cargar las fichas (entrega 6-1c). */
public interface AlumnoIdsRepository {

	/**
	 * Ids de los alumnos de la escuela activa que cumplen {@code predicado} (solo activos salvo
	 * {@code incluirInactivos}), en orden ascendente de id. Mismo resultado que
	 * {@link AlumnoRepository#buscarIdsEnOrganizacionActiva} con un predicado sin restricción.
	 */
	List<Long> buscarIds(Specification<Alumno> predicado, boolean incluirInactivos);
}
