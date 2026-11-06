package com.taemoi.project.dtos.response;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * Respuestas de pasar lista (decisión 324, fase3-pasar-lista.md), iguales por {@code /api/profesor/grupos/{grupoId}} y
 * {@code /api/grupos/{grupoId}}. <b>Nunca</b> salud, contacto, NIF, email ni fecha de nacimiento: un campo nuevo aquí se
 * revisa contra esa lista. Única excepción, aceptada por el propietario (decisión 455): el motivo de una falta puede ser
 * {@code MEDICA}, sin ningún detalle más. Las fechas salen como {@code "yyyy-MM-dd"} (no como el array por defecto de la
 * aplicación).
 */
public final class AsistenciaDTOs {

	private AsistenciaDTOs() {
	}

	/**
	 * Estado de un día del alumno: hay fila y estuvo, hay fila y faltó, no hay fila suya, o (decisiones 455-463) el día es
	 * festivo en la sede del grupo y la clase no tiene lista guardada. {@code FESTIVO} no cuenta en {@code clases}.
	 */
	public enum EstadoDia {
		PRESENTE, FALTA, SIN_LISTA, FESTIVO
	}

	/**
	 * Festivo que cae en una clase sin lista guardada (decisiones 455-463). {@code tipo}: {@code DIA}, {@code RANGO} o
	 * {@code ANUAL}; {@code fechaDesde} y {@code fechaHasta}, las del festivo (ANUAL: la fecha de esta clase en las dos);
	 * {@code sedes}: cuántas sedes cubre; {@code deUnDiaYUnaSede}: es de un solo día y una sola sede.
	 */
	public record FestivoDeClase(Long id, String nombre, String tipo,
			@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate fechaDesde,
			@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate fechaHasta, int sedes,
			boolean deUnDiaYUnaSede) {
	}

	/**
	 * Una clase: un turno y una fecha. {@code pasada}: ya terminó. {@code presentes} y {@code total} (filas de la lista
	 * guardada), {@code null} si no hay lista. {@code alumnos}: tamaño de la lista actual del turno; 0, turno sin alumnos.
	 * {@code festivo}: solo si el día es festivo en la sede del grupo y la clase no tiene lista guardada (con lista, manda
	 * la lista); si no, {@code null}.
	 */
	public record Clase(Long turnoId, @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate fecha,
			String horaInicio, String horaFin, boolean pasada, Long presentes, Long total, long alumnos,
			FestivoDeClase festivo) {
	}

	/** La primera clase del grupo que aún no ha empezado. */
	public record Proxima(@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate fecha,
			String horaInicio) {
	}

	/** {@code GET /clases}. */
	public record Clases(List<Clase> hoy, List<Clase> recientes, Proxima proxima) {
	}

	/**
	 * Un alumno de la lista de una clase, con la misma forma de {@code deportes} que el listado del grupo. {@code motivo}:
	 * el de su falta guardada ({@code INJUSTIFICADA}, {@code MEDICA}, {@code FAMILIAR} u {@code OTRA_JUSTIFICADA});
	 * {@code null} si estuvo, si la falta no tiene motivo o si no hay fila.
	 */
	public record AlumnoDeLista(Long id, String nombre, String apellidos, boolean presente,
			List<ProfesorDTOs.DeporteEnLista> deportes, String motivo) {
	}

	/** La sede del grupo de una clase. */
	public record SedeDeLista(Long id, String nombre) {
	}

	/** {@code GET /clases/{turnoId}/{fecha}}. {@code sede}: la del grupo; {@code null} si no tuviera. */
	public record ListaDeClase(Clase clase, SedeDeLista sede, List<AlumnoDeLista> alumnos) {
	}

	/** {@code GET /asistencia/resumen}: un alumno con alguna fila ese mes. */
	public record ResumenAlumno(Long alumnoId, long presentes, long clases) {
	}

	/** {@code motivo}: el de la falta guardada, como en {@link AlumnoDeLista}; {@code null} en los demás estados. */
	public record Dia(@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate fecha,
			String horaInicio, EstadoDia estado, String motivo) {
	}

	/** {@code GET /alumnos/{alumnoId}/asistencia}. */
	public record AsistenciaDeAlumno(long presentes, long clases, List<Dia> dias) {
	}
}
