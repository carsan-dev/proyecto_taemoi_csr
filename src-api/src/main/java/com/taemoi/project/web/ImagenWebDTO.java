package com.taemoi.project.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.taemoi.project.archivos.UrlArchivoSerializer;

/** Respuesta de {@code POST /api/escuela/web/imagenes} (pieza W12a): la foto subida, aún sin bloque. */
public record ImagenWebDTO(Long imagenId, @JsonSerialize(using = UrlArchivoSerializer.class) String url) {
}
