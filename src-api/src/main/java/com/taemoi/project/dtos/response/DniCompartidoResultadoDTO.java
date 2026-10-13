package com.taemoi.project.dtos.response;

import java.util.List;

/** Resultado de aplicar una corrección; {@code aplicado = false} si ya estaba aplicada (sin cambios). */
public record DniCompartidoResultadoDTO(String nifEnmascarado, boolean aplicado, Long titularId,
		List<Long> alumnosSinNif, int tutoresCreados) {
}
