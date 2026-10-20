package com.taemoi.project.dtos.request;

/** {@code PUT /api/sedes/{id}/activa} (pieza E8-2, D8-2): {@code {"activa": true | false}}, obligatorio. */
public record SedeActivaRequest(Boolean activa) {
}
