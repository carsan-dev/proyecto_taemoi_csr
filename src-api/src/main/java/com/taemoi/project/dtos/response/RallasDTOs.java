package com.taemoi.project.dtos.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * Respuestas de las rallas hacia el examen (decisiones 510-513). {@code mediasRallas}: de 0 a 10 (dos medias son una
 * ralla; 10 son las 5 rallas del examen).
 */
public final class RallasDTOs {

	private RallasDTOs() {
	}

	/**
	 * Cómo queda la inscripción del alumno en la disciplina del grupo tras un paso o tras marcarlo apto desde la hoja.
	 * {@code deporte}: código de la disciplina; {@code aptoParaExamen}: el apto de esa inscripción. Al bajar desde las
	 * 10 medias, los pases borrados y las convocatorias de las que salió; si no, listas vacías.
	 */
	public record Estado(Long alumnoId, String deporte, int mediasRallas, boolean aptoParaExamen,
			List<PasePendiente> pasesBorrados, List<ConvocatoriaPendiente> convocatoriasSalidas) {

		/** Sin pases ni convocatorias afectados. */
		public Estado(Long alumnoId, String deporte, int mediasRallas, boolean aptoParaExamen) {
			this(alumnoId, deporte, mediasRallas, aptoParaExamen, List.of(), List.of());
		}
	}

	/** Un cargo de pase sin cobrar de esa inscripción: id, concepto e importe (precio × cantidad). */
	public record PasePendiente(Long id, String concepto, BigDecimal importe) {
	}

	/**
	 * Una convocatoria pendiente de la misma disciplina a la que está apuntado: su id, la fecha y el cargo del examen
	 * (id, concepto e importe), o los tres nulos si la inscripción no tiene cargo.
	 */
	public record ConvocatoriaPendiente(Long convocatoriaId,
			@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate fecha, Long cargoId,
			String concepto, BigDecimal importe) {
	}

	/**
	 * {@code GET .../rallas/al-quitar}: qué pasaría al quitar rallas ahora. Con 10 medias, sus pases sin cobrar y sus
	 * convocatorias pendientes (se borran y se sale de ellas al confirmar); con menos, listas vacías.
	 */
	public record AlQuitar(Long alumnoId, String deporte, int mediasRallas, boolean aptoParaExamen,
			List<PasePendiente> pases, List<ConvocatoriaPendiente> convocatorias) {
	}

	/**
	 * Interruptor de una disciplina en Configuración: {@code deporte} (código), {@code nombre} y {@code activas} (la
	 * escuela usa las rallas en ella). Solo las disciplinas activas con sistema de grados.
	 */
	public record Disciplina(String deporte, String nombre, boolean activas) {
	}
}
