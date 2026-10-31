package com.taemoi.project.dtos.response;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

/**
 * Respuesta de {@code PUT /api/sedes/{id}} (pieza 9-7): los campos de {@link SedeDTO} y {@code turnosSobreAforo}, que
 * dice si el aforo que se acaba de cambiar queda por debajo de la ocupación de algún turno de la sede.
 */
public record SedeEditadaDTO(@JsonUnwrapped SedeDTO sede, boolean turnosSobreAforo) {
}
