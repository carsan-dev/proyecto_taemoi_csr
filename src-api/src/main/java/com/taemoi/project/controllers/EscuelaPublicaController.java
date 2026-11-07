package com.taemoi.project.controllers;

import java.time.Duration;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.taemoi.project.dtos.response.EscuelaPublicaDTO;
import com.taemoi.project.marca.MarcaPanelService;

/**
 * Fase 4, escuela por dominio, pieza 4 ({@code docs/multi-escuela/fase4-dominio-plan.md} §4.2): la marca PÚBLICA de la
 * escuela del Host de la petición (la resuelve {@code OrganizacionRequestFilter}; Host desconocido o el de Moiskimdo,
 * la escuela por defecto). Anónimo ({@code permitAll}). Solo lo que ya ve cualquier visitante de su web: nombre, origen
 * del logo, versión y tema; nunca datos privados (CIF, buzón, firmante, correos, teléfono).
 */
@RestController
@RequestMapping("/api/publico/escuela")
public class EscuelaPublicaController {

	static final CacheControl CACHE_LOGO = CacheControl.maxAge(Duration.ofSeconds(300)).cachePublic();

	private final MarcaPanelService marca;

	public EscuelaPublicaController(MarcaPanelService marca) {
		this.marca = marca;
	}

	/** Nombre, origen del logo ({@code SUBIDO}, {@code LEGADO} o {@code NINGUNO}), su versión y el tema. */
	@GetMapping
	public ResponseEntity<EscuelaPublicaDTO> escuela() {
		return ResponseEntity.ok(EscuelaPublicaDTO.de(marca.marcaActiva()));
	}

	/**
	 * PNG del logo subido de la escuela del Host, con {@code Cache-Control: public, max-age=300} y {@code ETag} con el
	 * id de la imagen (304 si coincide, antes de leer el fichero). 404 sin logo subido (Moiskimdo usa su SVG de
	 * siempre).
	 */
	@GetMapping("/logo")
	public ResponseEntity<byte[]> logo(@RequestHeader(value = "If-None-Match", required = false) String siNoCoincide) {
		if (siNoCoincide != null && !siNoCoincide.isBlank()) {
			String vigente = EscuelaMarcaController.etiqueta(marca.versionLogoActiva());
			if (EscuelaMarcaController.coincide(siNoCoincide, vigente)) {
				return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(vigente).cacheControl(CACHE_LOGO).build();
			}
		}
		MarcaPanelService.LogoPanel logo = marca.logoActivo();
		return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).eTag(EscuelaMarcaController.etiqueta(logo.imagenId()))
				.cacheControl(CACHE_LOGO).body(logo.png());
	}
}
