package com.taemoi.project.dtos.request;

import java.util.List;

import com.taemoi.project.entities.OrigenDato;
import com.taemoi.project.entities.TipoDiscapacidad;

/**
 * Datos de salud completos de un alumno (PUT: sustituye lo guardado). {@code origen} nulo = {@code APP}.
 * Los booleanos admiten nulo: «no consta» (p. ej. casilla sin marcar en la ficha en papel).
 *
 * <p>Discapacidad: {@code tieneDiscapacidad} nulo = no se cambia ni el booleano del alumno ni los tipos (quien no conoce
 * estos campos no borra nada); las reglas están en {@code AlumnoSaludService#guardar}.
 *
 * <p>Sin anotaciones de Bean Validation a propósito: los límites (4000 caracteres por texto) los
 * comprueba {@code AlumnoSaludService} con mensajes que no repiten el valor rechazado. Sin contenido
 * en {@link #toString()}: tampoco los campos de discapacidad, ni el booleano.
 */
public record AlumnoSaludRequest(
		Boolean lesionEnfermedad,
		String descripcion,
		Boolean impideActividad,
		String impideDetalle,
		String alergias,
		OrigenDato origen,
		Boolean tieneDiscapacidad,
		List<TipoDiscapacidad> discapacidadTipos,
		String discapacidadOtra) {

	public static final int MAX_TEXTO = 4000;

	@Override
	public String toString() {
		return "AlumnoSaludRequest[origen=" + origen + "]";
	}
}
