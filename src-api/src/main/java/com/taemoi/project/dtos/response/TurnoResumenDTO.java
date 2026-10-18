package com.taemoi.project.dtos.response;

/**
 * Turno en versión ligera ({@code GET /api/turnos/resumen}), para los listados y selectores del panel: día, horas y
 * grupo. Sin los alumnos que sí incrusta {@link com.taemoi.project.dtos.TurnoDTO} ({@code GET /api/turnos}, que no
 * cambia).
 *
 * <p>{@code tipoGrupo} es el tipo del turno ({@code Turno.getTipo()}), tal cual, también si es nulo o vacío; lo pinta
 * la pantalla de turnos de un alumno.
 *
 * <p>{@code grupoNombre} vale {@code "Sin grupo"} si el turno no tiene grupo, igual que en
 * {@link com.taemoi.project.dtos.TurnoDTO}.
 */
public record TurnoResumenDTO(Long id, String diaSemana, String horaInicio, String horaFin, Long grupoId,
		String grupoNombre, String tipoGrupo) {
}
