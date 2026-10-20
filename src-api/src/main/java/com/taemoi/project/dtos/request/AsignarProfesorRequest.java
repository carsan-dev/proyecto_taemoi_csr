package com.taemoi.project.dtos.request;

/**
 * {@code POST /api/grupos/{grupoId}/profesores} (pieza E7-4, decisión 208): {@code {"email": ..., "rol": "TITULAR" |
 * "AYUDANTE"}}. Los dos son obligatorios; el rol llega como texto para responder 400 con un mensaje propio si no es
 * válido.
 */
public record AsignarProfesorRequest(String email, String rol) {
}
