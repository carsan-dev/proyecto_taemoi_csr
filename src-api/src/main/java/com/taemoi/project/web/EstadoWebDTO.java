package com.taemoi.project.web;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * Estado de la web por plantilla de una escuela para el panel «Web · Tema» (pieza W3). Sin el autor de la publicación.
 *
 * @param tema             tema publicado; {@code null} si la escuela aún no tiene web.
 * @param publicada        si la web está publicada.
 * @param publicadaEn      fecha y hora de la última publicación ({@code yyyy-MM-ddTHH:mm:ss}, hora del servidor);
 *                         {@code null} si nunca se publicó.
 * @param temasDisponibles los ids de {@link TemasWeb#TODOS} (el panel enseña solo los construidos).
 */
public record EstadoWebDTO(String tema, boolean publicada,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime publicadaEn,
		List<String> temasDisponibles) {
}
