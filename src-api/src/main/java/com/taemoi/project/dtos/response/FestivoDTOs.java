package com.taemoi.project.dtos.response;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * Respuestas de {@code /api/festivos} (decisiones 455-463, fase3-pasar-lista.md, «Motivos y festivos»). Sin datos de
 * alumnos. Fechas {@code "yyyy-MM-dd"}.
 */
public final class FestivoDTOs {

	private FestivoDTOs() {
	}

	/** Una sede de un festivo. */
	public record SedeDeFestivo(Long id, String nombre) {
	}

	/**
	 * Un festivo. {@code proximaFecha}: DIA y RANGO, {@code fechaDesde}; ANUAL, su próxima aparición desde hoy, incluido.
	 * {@code todasLasSedes}: sus sedes cubren todas las sedes activas de la escuela. {@code editable}: quien pregunta
	 * puede cambiarlo o quitarlo (gestión, siempre; el responsable de sede, si todas sus sedes son suyas).
	 */
	public record Festivo(Long id, String tipo,
			@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate fechaDesde,
			@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate fechaHasta, Integer mes,
			Integer dia, @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate proximaFecha,
			String nombre, List<SedeDeFestivo> sedes, boolean todasLasSedes, boolean editable) {
	}

	/** Una clase con lista guardada en las fechas del festivo (sigue normal: manda la lista). */
	public record ClaseConLista(Long sedeId, String sede,
			@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate fecha, String horaInicio,
			String grupo) {
	}

	/**
	 * Una clase afectada por el festivo. {@code conLista}: ya tiene lista guardada (sigue normal); {@code presentes} y
	 * {@code total}, las cifras de esa lista, o {@code null} sin ella.
	 */
	public record ClaseAfectada(Long sedeId, String sede,
			@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate fecha, String horaInicio,
			String horaFin, String grupo, boolean conLista, Long presentes, Long total) {
	}

	/**
	 * {@code POST /api/festivos/impacto}: clases (turno × fecha) de los grupos de esas sedes en esas fechas que aún no
	 * son festivo por otro; {@code conLista}, las que ya tienen lista guardada; {@code conListaDetalle}, hasta
	 * {@value com.taemoi.project.services.FestivoService#MAX_DETALLE_IMPACTO} de ellas; {@code clasesDetalle}, hasta otras
	 * tantas de todas las afectadas, por fecha y hora.
	 */
	public record Impacto(long clases, long conLista, List<ClaseConLista> conListaDetalle,
			List<ClaseAfectada> clasesDetalle) {
	}
}
