package com.taemoi.project.dtos.response;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * Respuestas de pasar lista (decisión 324, fase3-pasar-lista.md), iguales por {@code /api/profesor/grupos/{grupoId}} y
 * {@code /api/grupos/{grupoId}}. <b>Nunca</b> salud, contacto, NIF, email ni fecha de nacimiento: un campo nuevo aquí se
 * revisa contra esa lista. Las fechas salen como {@code "yyyy-MM-dd"} (no como el array por defecto de la aplicación).
 */
public final class AsistenciaDTOs {

	private AsistenciaDTOs() {
	}

	/** Estado de un día del alumno: hay fila y estuvo, hay fila y faltó, o no hay fila suya. */
	public enum EstadoDia {
		PRESENTE, FALTA, SIN_LISTA
	}

	/**
	 * Una clase: un turno y una fecha. {@code pasada}: ya terminó. {@code presentes} y {@code total} (filas de la lista
	 * guardada), {@code null} si no hay lista. {@code alumnos}: tamaño de la lista actual del turno; 0, turno sin alumnos.
	 */
	public record Clase(Long turnoId, @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate fecha,
			String horaInicio, String horaFin, boolean pasada, Long presentes, Long total, long alumnos) {
	}

	/** La primera clase del grupo que aún no ha empezado. */
	public record Proxima(@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate fecha,
			String horaInicio) {
	}

	/** {@code GET /clases}. */
	public record Clases(List<Clase> hoy, List<Clase> recientes, Proxima proxima) {
	}

	/** Un alumno de la lista de una clase, con la misma forma de {@code deportes} que el listado del grupo. */
	public record AlumnoDeLista(Long id, String nombre, String apellidos, boolean presente,
			List<ProfesorDTOs.DeporteEnLista> deportes) {
	}

	/** {@code GET /clases/{turnoId}/{fecha}}. */
	public record ListaDeClase(Clase clase, List<AlumnoDeLista> alumnos) {
	}

	/** {@code GET /asistencia/resumen}: un alumno con alguna fila ese mes. */
	public record ResumenAlumno(Long alumnoId, long presentes, long clases) {
	}

	public record Dia(@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate fecha,
			String horaInicio, EstadoDia estado) {
	}

	/** {@code GET /alumnos/{alumnoId}/asistencia}. */
	public record AsistenciaDeAlumno(long presentes, long clases, List<Dia> dias) {
	}
}
