package com.taemoi.project.dtos.request;

/**
 * Pieza 6b-2c: cuerpo de {@code POST /api/productos-alumno/{id}/cancelar}. Motivo obligatorio (hasta 500 caracteres,
 * sin espacios a los lados); lo valida el servicio (400 con {@code {"mensaje"}}).
 */
public record CancelarCargoRequest(String motivo) {
}
