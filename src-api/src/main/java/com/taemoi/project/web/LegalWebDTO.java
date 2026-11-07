package com.taemoi.project.web;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Respuesta de {@code GET /api/publico/web/legal} (fase4-web-plan.md §5, pieza W1): los datos de la página de Legales
 * (aviso legal, privacidad, cookies y normas). Es el ÚNICO sitio público con el CIF y el domicilio del titular (art. 10
 * LSSI). Nunca firmante, buzones ni datos de personas.
 *
 * @param titular        titular de la web.
 * @param normas         normas de la actividad por disciplina activa: la plantilla de preinscripción activa (la misma
 *                       que ya publica {@code GET /api/preinscripciones/public/configuracion/{deporte}}).
 * @param actualizadaEn  «Última actualización»: fecha de la última publicación de la web (§9, n.º 14);
 *                       {@code null} en una vista previa sin publicar.
 */
public record LegalWebDTO(Titular titular, List<Normas> normas,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate actualizadaEn) {

	/**
	 * @param nombre      nombre comercial de la escuela.
	 * @param nombreLegal razón social.
	 */
	public record Titular(String nombre, String nombreLegal, String cif, String direccion, String localidad,
			String provincia, String codigoPostal, String email, String telefono) {
	}

	/** @param contenido el JSON de la plantilla, como en {@code /public/configuracion/{deporte}}. */
	public record Normas(String disciplina, String disciplinaNombre, Integer version, JsonNode contenido,
			String instrucciones) {
	}
}
