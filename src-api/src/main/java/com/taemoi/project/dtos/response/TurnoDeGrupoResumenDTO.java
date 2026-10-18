package com.taemoi.project.dtos.response;

/**
 * Turno dentro de {@link GrupoResumenDTO}: solo lo que pinta la pantalla de grupos y turnos (día y horas), sin los
 * alumnos que sí traen {@link TurnoCortoDTO} y {@link com.taemoi.project.dtos.TurnoDTO}.
 */
public record TurnoDeGrupoResumenDTO(Long id, String diaSemana, String horaInicio, String horaFin) {
}
