package com.taemoi.project.web;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

/**
 * Respuesta de {@code GET /api/publico/web/eventos/{id}} (fase4-web-plan.md §5, pieza W5): la página de un evento
 * visible con fecha de la escuela activa. Los ficheros se sirven por los endpoints públicos de eventos, que también
 * filtran por escuela y dan 404 si el evento está oculto: el cartel por {@code GET /api/eventos/{id}/imagen} (si
 * {@code conImagen}), cada foto por {@code GET /api/eventos/{id}/fotos/{fotoId}} ({@code ?w=640}, la miniatura) y cada
 * documento por {@code GET /api/eventos/{id}/documentos/{documentoId}/descargar}. Nunca la ruta ni la URL del fichero.
 *
 * @param evento      los campos del evento de {@code GET /api/publico/web}, al mismo nivel.
 * @param descripcion texto del evento.
 * @param documentos  documentos del evento, por id.
 * @param fotos       fotos de la galería, por {@code orden}.
 */
public record EventoWebDTO(@JsonUnwrapped WebPublicaDTO.Evento evento, String descripcion, List<Documento> documentos,
		List<Foto> fotos) {

	/** @param tipo tipo MIME ({@code application/pdf}…). */
	public record Documento(Long id, String nombre, String tipo) {
	}

	public record Foto(Long id) {
	}
}
