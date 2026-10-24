package com.taemoi.project.dtos.response;

import java.math.BigDecimal;

/**
 * Pieza 8b-3l (P-3, decisión 285): los cargos sin cancelar de un alumno que se borrarían con él (los suyos y los de sus
 * inscripciones): cuántos, cuántos pagados y pendientes, y sus importes.
 */
public record ResumenCargosAlumnoDTO(Long alumnoId, long cargos, long pagados, long pendientes,
		BigDecimal importePagado, BigDecimal importePendiente) {
}
