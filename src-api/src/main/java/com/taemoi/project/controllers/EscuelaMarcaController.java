package com.taemoi.project.controllers;

import java.time.Duration;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.taemoi.project.dtos.response.MarcaPanelDTO;
import com.taemoi.project.marca.MarcaPanelService;

/**
 * Pieza 8b-T (fase3-e8b-plan.md §4): la marca de la escuela ACTIVA para el panel nuevo. Solo gestión (propietario y
 * gestor); el responsable de sede, la familia, la sesión solo-profesor y el superadmin sin membresía, 403. Protegido dos
 * veces: la regla de {@code /api/escuela/**} de {@code SecurityConfiguration} y {@code @PreAuthorize} aquí.
 */
@RestController
@RequestMapping("/api/escuela")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGER')")
public class EscuelaMarcaController {

	static final CacheControl CACHE_LOGO = CacheControl.maxAge(Duration.ofSeconds(300)).cachePrivate();

	private final MarcaPanelService marca;

	public EscuelaMarcaController(MarcaPanelService marca) {
		this.marca = marca;
	}

	/** Nombre, origen del logo ({@code SUBIDO}, {@code LEGADO} o {@code NINGUNO}), su versión y el tema calculado. */
	@GetMapping("/marca")
	public ResponseEntity<MarcaPanelDTO> marca() {
		return ResponseEntity.ok(marca.marcaActiva());
	}

	/**
	 * PNG del logo subido de la escuela activa, con {@code Cache-Control: private, max-age=300} y {@code ETag} con el id
	 * de la imagen (304 si coincide: se compara ANTES de leer el fichero). 404 (JSON con {@code mensaje}) sin logo
	 * subido: Moiskimdo usa el SVG de siempre.
	 * Sin {@code produces}: así el error sale como JSON aunque la petición acepte solo imágenes.
	 */
	@GetMapping("/logo")
	public ResponseEntity<byte[]> logo(@RequestHeader(value = "If-None-Match", required = false) String siNoCoincide) {
		if (siNoCoincide != null && !siNoCoincide.isBlank()) {
			String vigente = etiqueta(marca.versionLogoActiva());
			if (coincide(siNoCoincide, vigente)) {
				return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(vigente).cacheControl(CACHE_LOGO).build();
			}
		}
		MarcaPanelService.LogoPanel logo = marca.logoActivo();
		return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).eTag(etiqueta(logo.imagenId()))
				.cacheControl(CACHE_LOGO).body(logo.png());
	}

	private static String etiqueta(Long imagenId) {
		return "\"logo-" + imagenId + "\"";
	}

	/** {@code If-None-Match} con la misma etiqueta (también débil, {@code W/}, o en una lista), o {@code *}. */
	static boolean coincide(String siNoCoincide, String etag) {
		if (siNoCoincide == null || siNoCoincide.isBlank()) {
			return false;
		}
		for (String candidata : siNoCoincide.split(",")) {
			String limpia = candidata.trim();
			if (limpia.startsWith("W/")) {
				limpia = limpia.substring(2);
			}
			if ("*".equals(limpia) || etag.equals(limpia)) {
				return true;
			}
		}
		return false;
	}
}
