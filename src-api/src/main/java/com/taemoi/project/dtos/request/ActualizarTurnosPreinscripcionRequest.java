package com.taemoi.project.dtos.request;

import java.util.List;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ActualizarTurnosPreinscripcionRequest(
		@NotEmpty List<@NotNull @Positive Long> turnoIds
) {
	@AssertTrue(message = "No se puede seleccionar el mismo turno más de una vez")
	public boolean hasTurnosUnicos() {
		return turnoIds == null || turnoIds.size() == turnoIds.stream().distinct().count();
	}
}
