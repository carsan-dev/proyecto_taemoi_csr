package com.taemoi.project.dtos.request;

/**
 * {@code POST .../alumnos/{alumnoId}/rallas} (decisiones 510-513): {@code {"paso": n}} con {@code n} en medias rallas:
 * −2 («− Una»), −1 («− Media»), +1 («+ Media») o +2 («+ Una»). Otro valor o sin él, 400.
 */
public record PasoRallasRequest(Integer paso) {
}
