package com.taemoi.project.archivos;

import java.io.InputStream;

/**
 * Cuerpo de una petición que llega sin multipart: el flujo tal cual, su {@code Content-Length} ({@code -1} si no lo trae)
 * y su {@code Content-Type}. Nunca se carga en memoria.
 */
public record CuerpoCrudo(InputStream contenido, long longitud, String tipo) {
}
