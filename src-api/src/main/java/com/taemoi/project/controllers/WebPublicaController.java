package com.taemoi.project.controllers;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.entities.Roles;
import com.taemoi.project.web.LegalWebDTO;
import com.taemoi.project.web.WebPublicaDTO;
import com.taemoi.project.web.WebPublicaService;

/**
 * Web por plantilla, lectura pública (fase4-web-plan.md §5, pieza W1). Anónimo ({@code permitAll} en
 * {@code SecurityConfiguration}), de la escuela activa. 404 si no tiene web publicada (Moiskimdo, la 1, no la tiene).
 *
 * <p>{@code vistaPrevia=true} solo vale con sesión de propietario o gestor (ROLE_ADMIN o ROLE_MANAGER): ve la web de su
 * escuela aunque no esté publicada. Sin esa sesión se ignora y se ve lo publicado.
 *
 * <p>Caché: {@code ETag} del cuerpo (304 si coincide) y, solo en lo anónimo, {@code Cache-Control: public,
 * max-age=60}. Con sesión (también la vista previa) {@code no-store}: la escuela sale de la sesión, no de la URL.
 */
@RestController
@RequestMapping("/api/publico/web")
public class WebPublicaController {

	static final CacheControl CACHE_PUBLICA = CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic();
	static final CacheControl CACHE_CON_SESION = CacheControl.noStore();
	private static final Set<String> GESTION = Set.of(Roles.ROLE_ADMIN.name(), Roles.ROLE_MANAGER.name());

	private final WebPublicaService web;
	private final ObjectMapper json;

	public WebPublicaController(WebPublicaService web, ObjectMapper json) {
		this.web = web;
		this.json = json;
	}

	/** Escuela, tema, bloques, sedes con grupos, turnos y plazas, tarifas, licencias, próximos eventos y contacto. */
	@GetMapping
	public ResponseEntity<WebPublicaDTO> web(
			@RequestParam(name = "vistaPrevia", defaultValue = "false") boolean vistaPrevia) {
		return responder(web.web(vistaPrevia && esGestion()));
	}

	/** Titular (con CIF y domicilio), normas por disciplina y fecha de la última publicación. */
	@GetMapping("/legal")
	public ResponseEntity<LegalWebDTO> legal(
			@RequestParam(name = "vistaPrevia", defaultValue = "false") boolean vistaPrevia) {
		return responder(web.legal(vistaPrevia && esGestion()));
	}

	private <T> ResponseEntity<T> responder(Optional<T> cuerpo) {
		if (cuerpo.isEmpty()) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok().eTag(etiqueta(cuerpo.get()))
				.cacheControl(esAnonimo() ? CACHE_PUBLICA : CACHE_CON_SESION).body(cuerpo.get());
	}

	/** Huella del JSON: Spring responde 304 si {@code If-None-Match} coincide. */
	private String etiqueta(Object cuerpo) {
		try {
			return "\"web-" + DigestUtils.md5DigestAsHex(json.writeValueAsBytes(cuerpo)) + "\"";
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("No se pudo serializar la web pública.", e);
		}
	}

	private static boolean esGestion() {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		return autenticacion != null && !(autenticacion instanceof AnonymousAuthenticationToken)
				&& autenticacion.isAuthenticated() && autenticacion.getAuthorities().stream()
						.map(GrantedAuthority::getAuthority).anyMatch(GESTION::contains);
	}

	private static boolean esAnonimo() {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		return autenticacion == null || autenticacion instanceof AnonymousAuthenticationToken
				|| !autenticacion.isAuthenticated();
	}
}
