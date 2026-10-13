package com.taemoi.project.dtos.request;

import com.taemoi.project.entities.OrigenDato;

/**
 * Datos de salud completos de un alumno (PUT: sustituye lo guardado). {@code origen} nulo = {@code APP}.
 * Los booleanos admiten nulo: «no consta» (p. ej. casilla sin marcar en la ficha en papel).
 *
 * <p>Sin anotaciones de Bean Validation a propósito: los límites (4000 caracteres por texto) los
 * comprueba {@code AlumnoSaludService} con mensajes que no repiten el valor rechazado. Sin contenido
 * en {@link #toString()}.
 */
public record AlumnoSaludRequest(
		Boolean lesionEnfermedad,
		String descripcion,
		Boolean impideActividad,
		String impideDetalle,
		String alergias,
		OrigenDato origen) {

	public static final int MAX_TEXTO = 4000;

	@Override
	public String toString() {
		return "AlumnoSaludRequest[origen=" + origen + "]";
	}
}
