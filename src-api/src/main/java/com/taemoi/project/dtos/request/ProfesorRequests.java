package com.taemoi.project.dtos.request;

/**
 * Peticiones de las rutas propias del profesor (entrega 7, pieza E7-3; {@code /api/profesor/alumnos/{alumnoId}/deportes/
 * {deporte}/...}). Los nombres de los campos son los de las rutas de gestión equivalentes.
 */
public final class ProfesorRequests {

	private ProfesorRequests() {
	}

	/** {@code PUT .../apto}: {@code {"aptoParaExamen": true|false}} (obligatorio). */
	public record Apto(Boolean aptoParaExamen) {
	}

	/** {@code PUT .../fecha-grado}: {@code {"fechaGrado": "yyyy-MM-dd"}} (obligatorio). */
	public record FechaGrado(String fechaGrado) {
	}

	/**
	 * {@code POST .../pase-examen} y {@code .../pase-recompensa}, cuerpo opcional: {@code rojoBordado} elige «Rojo
	 * bordado» cuando el siguiente grado es Rojo y la ficha lo pide ({@code pideRojoBordado}); si no, se ignora, como en
	 * gestión.
	 */
	public record Pase(Boolean rojoBordado) {
	}
}
