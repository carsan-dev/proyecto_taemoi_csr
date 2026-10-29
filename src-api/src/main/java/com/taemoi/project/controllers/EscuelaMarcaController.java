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
 * Pieza 8b-T (fase3-e8b-plan.md §4): la marca de la escuela ACTIVA para el panel nuevo. Gestión (propietario y
 * gestor) y, desde la pieza 8b-3o (decisión 326), la sesión solo-profesor, que solo lee ({@link #LEER}); el responsable
 * de sede, la familia (también la que además es profesor) y el superadmin sin membresía, 403. Protegido dos veces: la
 * regla de URL de {@code SecurityConfiguration} (y la lista blanca de la sesión solo-profesor) y {@code @PreAuthorize}
 * aquí. Una ruta nueva bajo esta clase queda solo para gestión.
 */
@RestController
@RequestMapping("/api/escuela")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGER')")
public class EscuelaMarcaController {

	/**
	 * Lectura de la marca y el logo: gestión o la sesión solo-profesor (PROFESOR sin ADMIN, MANAGER ni USER; también
	 * con RESPONSABLE_SEDE), como {@code SesionSoloProfesorAuthorizationManager.gestionOSesionSoloProfesor}.
	 */
	static final String LEER = "hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGER') "
			+ "or (hasAuthority('ROLE_PROFESOR') and !hasAuthority('ROLE_USER'))";

	static final CacheControl CACHE_LOGO = CacheControl.maxAge(Duration.ofSeconds(300)).cachePrivate();

	private final MarcaPanelService marca;

	public EscuelaMarcaController(MarcaPanelService marca) {
		this.marca = marca;
	}

	/** Nombre, origen del logo ({@code SUBIDO}, {@code LEGADO} o {@code NINGUNO}), su versión y el tema calculado. */
	@GetMapping("/marca")
	@PreAuthorize(LEER)
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
	@PreAuthorize(LEER)
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
