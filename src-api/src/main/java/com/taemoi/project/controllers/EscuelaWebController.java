package com.taemoi.project.controllers;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.web.EstadoWebDTO;
import com.taemoi.project.web.PublicacionWebService;
import com.taemoi.project.web.TemasWeb;

/**
 * Pieza W3 de la web por plantilla (fase4-web-plan.md §5): «Web · Tema» del panel, de la escuela ACTIVA. Solo el
 * propietario (ROLE_ADMIN; §9, n.º 3): la regla de URL de {@code /api/escuela/**} deja pasar a la gestión y
 * {@code @PreAuthorize} la estrecha. El superadmin publica por {@code /api/plataforma/escuelas/{id}/web/publicacion}.
 * Moiskimdo (web propia) responde 409.
 */
@RestController
@RequestMapping("/api/escuela/web")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class EscuelaWebController {

	static final String RUTA_PUBLICAR = "/api/escuela/web/publicacion";
	static final String CUERPO_NO_VALIDO = "El cuerpo de la petición no es válido.";

	private final PublicacionWebService publicacion;

	public EscuelaWebController(PublicacionWebService publicacion) {
		this.publicacion = publicacion;
	}

	/** Tema publicado, si está publicada, cuándo y los temas disponibles. */
	@GetMapping
	public ResponseEntity<EstadoWebDTO> estado() {
		return ResponseEntity.ok(publicacion.estado(TenantContext.get()));
	}

	/**
	 * Publica la web con el tema del cuerpo ({@code {"tema": "chandal"}}). 200 con el estado; 400 tema que no vale;
	 * 409 Moiskimdo o publicación a la vez. Idempotente con el mismo tema.
	 */
	@PutMapping("/publicacion")
	public ResponseEntity<EstadoWebDTO> publicar(@RequestBody(required = false) JsonNode cuerpo) {
		return ResponseEntity.ok(publicacion.publicar(TenantContext.get(), TemasWeb.temaDe(cuerpo), RUTA_PUBLICAR));
	}

	/** Cuerpo que no se puede leer: 400 con {@code {"mensaje"}}, como {@code PlataformaController}. */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<Map<String, String>> cuerpoNoLegible() {
		return ResponseEntity.badRequest().body(Map.of("mensaje", CUERPO_NO_VALIDO));
	}
}
