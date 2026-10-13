package com.taemoi.project.dtos.response;

import java.util.List;

/**
 * Resultado de aplicar una corrección; {@code aplicado = false} si ya estaba aplicada (sin cambios).
 * {@code tutoresCompletados}: tutores existentes sin NIF a los que se les puso el DNI.
 */
public record DniCompartidoResultadoDTO(String nifEnmascarado, boolean aplicado, Long titularId,
		List<Long> alumnosSinNif, int tutoresCreados, int tutoresCompletados) {
}
