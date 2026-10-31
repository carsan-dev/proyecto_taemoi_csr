package com.taemoi.project.dtos.request;

/** {@code PUT /api/alumnos/{id}/sede} (pieza 9-1, decisión 369): {@code {"sedeId": 2}}, obligatorio. */
public record CambiarSedeAlumnoRequest(Long sedeId) {
}
