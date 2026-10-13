package com.taemoi.project.dtos.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Corrección de un grupo de DNI compartido. {@code grupo} es la referencia opaca del informe (el NIF
 * cifrado: así el DNI completo no viaja ni queda en la auditoría de la petición) y {@code alumnoIds}
 * los miembros que se mostraron (si el grupo cambió desde entonces: 409).
 * <ul>
 * <li>{@code PROPUESTA}: aplica la propuesta automática (400 si el grupo es de revisión manual).</li>
 * <li>{@code TITULAR}: el DNI es de {@code titularId}; el resto queda sin NIF y con ese tutor.</li>
 * <li>{@code SIN_TITULAR}: el DNI es de un tutor que no es alumno; todos quedan sin NIF.</li>
 * </ul>
 * {@code nombreTutor} (opcional) sustituye al nombre propuesto del tutor cuando no hay titular.
 */
public record AplicarDniCompartidoRequest(
		@NotBlank @Size(max = 512) String grupo,
		@NotEmpty @Size(max = 200) List<@NotNull Long> alumnoIds,
		@NotNull Accion accion,
		Long titularId,
		@Size(max = 180) String nombreTutor) {

	public enum Accion {
		PROPUESTA, TITULAR, SIN_TITULAR
	}
}
