package com.taemoi.project.dtos.response;

import java.util.List;

/**
 * Grupo en versión ligera ({@code GET /api/grupos/resumen}), para los listados y selectores del panel: los datos del
 * grupo, el número de alumnos y sus turnos. Sin los alumnos ni sus datos personales, que sí incrusta
 * {@link GrupoConAlumnosDTO} ({@code GET /api/grupos}, que no cambia).
 *
 * <p>{@code numAlumnos} cuenta lo mismo que la lista {@code alumnos} de {@link GrupoConAlumnosDTO}: alumnos activos
 * del grupo y, si tienen deportes, con el deporte del grupo activo.
 */
public record GrupoResumenDTO(Long id, String nombre, String deporte, Integer rangoEdadMin, Integer rangoEdadMax,
		long numAlumnos, List<TurnoDeGrupoResumenDTO> turnos) {
}
