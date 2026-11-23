package com.taemoi.project.dtos.request;

import java.util.List;

/**
 * {@code POST .../alumnos/{alumnoId}/rallas} (decisiones 510-513): {@code {"paso": n}} con {@code n} en medias rallas:
 * −2 («− Una»), −1 («− Media»), +1 («+ Media») o +2 («+ Una»). Otro valor o sin él, 400.
 *
 * <p>Bajar desde las 10 medias quita el apto de esa disciplina y borra sus pendientes (pases sin cobrar y
 * convocatorias pendientes): entonces hace falta {@code confirmarQuitarApto = true} y los ids EXACTOS que dio
 * {@code GET .../rallas/al-quitar} ({@code pasesABorrar}: cargos; {@code convocatoriasASalir}: convocatorias). En el
 * resto de pasos se ignoran. Ausentes: {@code false} y listas vacías.
 */
public record PasoRallasRequest(Integer paso, Boolean confirmarQuitarApto, List<Long> pasesABorrar,
		List<Long> convocatoriasASalir) {
}
