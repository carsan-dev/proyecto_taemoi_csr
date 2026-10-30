package com.taemoi.project.dtos.response;

/**
 * Una foto de la galería de un evento. {@code url} es la del endpoint, que comprueba si el evento está oculto: nunca la
 * ruta del fichero ni su URL bajo {@code /imagenes/}.
 */
public record EventoFotoDTO(Long id, String url, Integer orden) {
}
