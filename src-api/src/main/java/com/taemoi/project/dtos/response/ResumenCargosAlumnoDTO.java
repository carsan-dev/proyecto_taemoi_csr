package com.taemoi.project.dtos.response;

import java.math.BigDecimal;

/**
 * Pieza 8b-3l (P-3, decisión 285): los cargos sin cancelar de un alumno que se borrarían con él (los suyos y los de sus
 * inscripciones): cuántos, cuántos pagados y pendientes, y sus importes. {@code pagos} (8b-3l bis, decisión 287):
 * cuántos pagos tiene, en cualquier estado; con alguno no se elimina definitivamente, se elimina anonimizando.
 */
public record ResumenCargosAlumnoDTO(Long alumnoId, long cargos, long pagados, long pendientes,
		BigDecimal importePagado, BigDecimal importePendiente, long pagos) {
}
