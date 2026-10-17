package com.taemoi.project.dtos.response;

/**
 * Entrega 6-4 (D6-28): mensualidad generada con una cuantía personalizada MAYOR que la tarifa de la sede del alumno para
 * ese mes. Se cobra la personalizada; la respuesta de la generación la lista para que la gestión la revise.
 *
 * @param alumnoId    alumno del cargo
 * @param nombre      nombre y apellidos del alumno
 * @param deporte     código de la disciplina de la inscripción
 * @param cuantia     cuantía de la inscripción (la que se ha cobrado)
 * @param cuantiaSede importe de la tarifa de la sede para ese mes y rol
 */
public record CuantiaPersonalizadaSuperiorDTO(Long alumnoId, String nombre, String deporte, Double cuantia,
		Double cuantiaSede) {
}
