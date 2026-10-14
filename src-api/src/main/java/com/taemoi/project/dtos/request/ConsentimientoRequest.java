package com.taemoi.project.dtos.request;

import java.time.LocalDate;

import com.taemoi.project.entities.OrigenDato;
import com.taemoi.project.entities.RelacionFirmante;
import com.taemoi.project.entities.TipoConsentimiento;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

/**
 * Registro de un consentimiento (o de su retirada, con {@code otorgado = false}). {@code origen} nulo =
 * {@code APP}; {@code PREINSCRIPCION} solo lo usa el sistema al finalizar una preinscripción.
 * {@code documentoId}: documento del mismo alumno (p. ej. la ficha escaneada).
 */
public record ConsentimientoRequest(
		@NotNull(message = "El tipo de consentimiento es obligatorio") TipoConsentimiento tipo,
		@NotNull(message = "Debe indicarse si se otorga o se retira") Boolean otorgado,
		@PastOrPresent(message = "La fecha no puede ser futura") LocalDate fecha,
		Boolean fechaAproximada,
		@Size(max = 180) String firmanteNombre,
		RelacionFirmante firmanteRelacion,
		OrigenDato origen,
		Long documentoId) {
}
