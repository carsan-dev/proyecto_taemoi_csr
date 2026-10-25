package com.taemoi.project.repositories;

import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.taemoi.project.entities.Alumno;

/** Fragmento de {@link AlumnoRepository}: ids con un predicado, sin cargar las fichas (entrega 6-1c). */
public interface AlumnoIdsRepository {

	/**
	 * Ids de los alumnos de la escuela activa que cumplen {@code predicado} (solo activos salvo
	 * {@code incluirInactivos}), en orden ascendente de id. A diferencia de
	 * {@link AlumnoRepository#buscarIdsEnOrganizacionActiva}, NO excluye a los eliminados anonimizando (decisión 287):
	 * el alcance del responsable de sede ({@code GestionPorSede#idsAlumnosEnAlcance}) los necesita para sus
	 * inscripciones y su tesorería. Los listados que no deben mostrarlos lo añaden al predicado.
	 */
	List<Long> buscarIds(Specification<Alumno> predicado, boolean incluirInactivos);
}
