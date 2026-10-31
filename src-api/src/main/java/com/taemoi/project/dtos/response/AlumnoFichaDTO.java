package com.taemoi.project.dtos.response;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.taemoi.project.dtos.AlumnoDTO;

/**
 * Pieza 9-1: la ficha del panel ({@code GET /api/alumnos/{id}}): el {@link AlumnoDTO} de siempre más la sede principal,
 * {@code null} si no tiene. Aparte de {@link AlumnoDTO} porque ese DTO también sale en los listados, que no cambian. Esta
 * ruta es de la gestión y del responsable de sede: la familia no la lee.
 */
public record AlumnoFichaDTO(@JsonUnwrapped AlumnoDTO alumno, Long sedePrincipalId, String sedePrincipalNombre) {
}
