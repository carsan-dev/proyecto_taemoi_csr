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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.entities.Roles;
import com.taemoi.project.web.EventoWebDTO;
import com.taemoi.project.web.EventosWebDTO;
import com.taemoi.project.web.LegalWebDTO;
import com.taemoi.project.web.TemasWeb;
import com.taemoi.project.web.WebPublicaDTO;
import com.taemoi.project.web.WebPublicaService;

/**
 * Web por plantilla, lectura pública (fase4-web-plan.md §5, piezas W1 y W5). Anónimo ({@code permitAll} en
 * {@code SecurityConfiguration}), de la escuela activa. 404 si no tiene web publicada (Moiskimdo, la 1, no la tiene).
 *
 * <p>{@code vistaPrevia=true} solo vale con sesión de propietario o gestor (ROLE_ADMIN o ROLE_MANAGER): ve la web de su
 * escuela aunque no esté publicada. Sin esa sesión se ignora y se ve lo publicado.
 *
 * <p>Caché: {@code ETag} del cuerpo (304 si coincide) y, solo en lo anónimo, {@code Cache-Control: public,
 * max-age=60}. Con sesión o con {@code vistaPrevia} en la URL, {@code no-store}: la escuela sale de la sesión, no de
 * la URL. Siempre {@code Vary: Cookie, Authorization}, para que ninguna caché reutilice la respuesta anónima para una
 * sesión (la vista previa de un gestor).
 */
@RestController
@RequestMapping("/api/publico/web")
public class WebPublicaController {

	static final CacheControl CACHE_PUBLICA = CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic();
	static final CacheControl CACHE_CON_SESION = CacheControl.noStore();
	/** Cabeceras de la petición que cambian la respuesta (la sesión). */
	static final String[] VARIA_CON = { "Cookie", "Authorization" };
	private static final Set<String> GESTION = Set.of(Roles.ROLE_ADMIN.name(), Roles.ROLE_MANAGER.name());

	private final WebPublicaService web;
	private final ObjectMapper json;

	public WebPublicaController(WebPublicaService web, ObjectMapper json) {
		this.web = web;
		this.json = json;
	}

	/**
	 * Escuela, tema, bloques, sedes con grupos, turnos y plazas, tarifas, licencias, próximos eventos y contacto.
	 *
	 * <p>Pieza W3: {@code tema} solo cuenta en la vista previa de la gestión (el tema elegido en «Web · Tema» y aún no
	 * publicado, que no se guarda): sale ese tema en lugar del publicado; uno que no existe, 400. En cualquier otra
	 * petición (anónima o sin rol de gestión) se ignora.
	 */
	@GetMapping
	public ResponseEntity<WebPublicaDTO> web(
			@RequestParam(name = "vistaPrevia", defaultValue = "false") boolean vistaPrevia,
			@RequestParam(name = "tema", required = false) String tema) {
		boolean deGestion = vistaPrevia && esGestion();
		String temaVistaPrevia = null;
		if (deGestion && tema != null) {
			if (!TemasWeb.esValido(tema)) {
				throw new IllegalArgumentException(TemasWeb.TEMA_NO_VALIDO);
			}
			temaVistaPrevia = tema;
		}
		return responder(web.web(deGestion, temaVistaPrevia), vistaPrevia);
	}

	/** Titular (con CIF y domicilio), normas por disciplina y fecha de la última publicación. */
	@GetMapping("/legal")
	public ResponseEntity<LegalWebDTO> legal(
			@RequestParam(name = "vistaPrevia", defaultValue = "false") boolean vistaPrevia) {
		return responder(web.legal(vistaPrevia && esGestion()), vistaPrevia);
	}

	/** Pieza W5: eventos visibles con fecha, próximos y anteriores (hasta 60 de cada). */
	@GetMapping("/eventos")
	public ResponseEntity<EventosWebDTO> eventos(
			@RequestParam(name = "vistaPrevia", defaultValue = "false") boolean vistaPrevia) {
		return responder(web.eventos(vistaPrevia && esGestion()), vistaPrevia);
	}

	/** Pieza W5: un evento visible con fecha, con sus documentos y fotos; 404 si no lo es o no es de la escuela. */
	@GetMapping("/eventos/{id}")
	public ResponseEntity<EventoWebDTO> evento(@PathVariable("id") long id,
			@RequestParam(name = "vistaPrevia", defaultValue = "false") boolean vistaPrevia) {
		return responder(web.evento(vistaPrevia && esGestion(), id), vistaPrevia);
	}

	private <T> ResponseEntity<T> responder(Optional<T> cuerpo, boolean vistaPrevia) {
		if (cuerpo.isEmpty()) {
			return ResponseEntity.notFound().varyBy(VARIA_CON).build();
		}
		return ResponseEntity.ok().eTag(etiqueta(cuerpo.get())).varyBy(VARIA_CON)
				.cacheControl(esAnonimo() && !vistaPrevia ? CACHE_PUBLICA : CACHE_CON_SESION).body(cuerpo.get());
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
