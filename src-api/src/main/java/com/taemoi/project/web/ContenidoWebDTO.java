package com.taemoi.project.web;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.taemoi.project.archivos.UrlArchivoSerializer;

/**
 * Respuesta de {@code GET/PUT /api/escuela/web/contenido} (pieza W12a): el contenido de la web de la escuela activa tal
 * como lo edita el propietario, con las secciones ocultas y la constancia del permiso de cada profesor. Solo para el
 * editor: la web pública no sale de aquí.
 *
 * @param version versión del contenido (0 si la escuela aún no ha guardado); el {@code PUT} la devuelve (d522).
 * @param bloques todas las filas de {@code web_bloque}, por {@code orden} y clave.
 */
public record ContenidoWebDTO(long version, List<Bloque> bloques) {

	/**
	 * @param imagenUrl URL de la imagen (absoluta con {@code app.base.url}); {@code null} sin imagen.
	 * @param permiso   en {@code profesor.<n>.nombre}: la constancia del permiso de publicación; {@code null} en las
	 *                  demás o si no la tiene.
	 */
	public record Bloque(String clave, String texto, Long imagenId,
			@JsonSerialize(using = UrlArchivoSerializer.class) String imagenUrl, int orden, boolean oculto,
			Permiso permiso) {
	}

	/**
	 * Constancia del permiso de un profesor (d521, d526).
	 *
	 * @param origen {@code PANEL} (lo marcó {@code por} en el editor) o {@code ALTA} («Permiso dado en el alta»).
	 * @param por    nombre de quien lo marcó; {@code null} en el alta o si la cuenta ya no existe.
	 * @param en     cuándo.
	 */
	public record Permiso(String origen, String por,
			@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime en) {
	}
}
