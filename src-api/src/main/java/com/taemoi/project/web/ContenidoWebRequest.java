package com.taemoi.project.web;

import java.util.List;

/**
 * Cuerpo de {@code PUT /api/escuela/web/contenido} (pieza W12a): el contenido ENTERO de la web (lo que no llega se
 * quita) y la versión que se leyó.
 *
 * @param version la de {@code GET}; si otra persona guardó después, 409 y no se escribe nada (d522).
 * @param bloques las filas, con las claves de {@link ClavesWeb}.
 */
public record ContenidoWebRequest(Long version, List<Bloque> bloques) {

	/**
	 * @param texto    texto; en las imágenes, su descripción; en las cabeceras, nada.
	 * @param imagenId solo en las claves de imagen: una foto subida por {@code POST /api/escuela/web/imagenes}.
	 * @param orden    puesto de la sección (cabeceras) o de la foto en la galería; 0 si no llega.
	 * @param oculto   solo en las cabeceras de las secciones movibles.
	 * @param permiso  solo en {@code profesor.<n>.nombre}: {@code true} si se acaba de marcar la casilla «Tengo su
	 *                 permiso…». Obligatorio para un profesor sin constancia guardada; con constancia, se conserva la
	 *                 que había.
	 */
	public record Bloque(String clave, String texto, Long imagenId, Integer orden, Boolean oculto, Boolean permiso) {
	}
}
