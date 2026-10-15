package com.taemoi.project.controllers;

import java.util.Set;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.taemoi.project.dtos.response.CatalogoDTO;
import com.taemoi.project.dtos.response.CatalogoPublicoDTO;
import com.taemoi.project.entities.Roles;
import com.taemoi.project.services.CatalogoService;

/**
 * Catálogo de la organización de la petición (docs/multi-escuela/catalogo-frontend.md §1). La organización la
 * fija {@code OrganizacionRequestFilter} (sesión; anónimo → organización por defecto); no se recibe por
 * parámetro.
 *
 * <p>Cabeceras: la respuesta depende de la sesión (cookie {@code jwt}), así que es {@code private, no-cache}
 * (ninguna caché compartida la guarda; el navegador revalida siempre) con {@code Vary: Cookie}. El ETag lo
 * añade {@code ShallowEtagHeaderFilter} (solo en {@code /api/catalogo/**}) y responde 304 si no cambió.
 */
@RestController
@RequestMapping("/api/catalogo")
public class CatalogoController {

	private static final CacheControl CACHE = CacheControl.noCache().cachePrivate();
	private static final Set<String> GESTION = Set.of(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString());

	private final CatalogoService catalogo;

	public CatalogoController(CatalogoService catalogo) {
		this.catalogo = catalogo;
	}

	/** Usuarios autenticados. Las tarifas solo para gestión (ROLE_ADMIN, ROLE_MANAGER); el resto, vacías (D14). */
	@GetMapping
	public ResponseEntity<CatalogoDTO> obtener(Authentication autenticacion) {
		return conCabeceras(catalogo.obtener(esGestion(autenticacion)));
	}

	/** Público: disciplinas activas (código, nombre, orden). */
	@GetMapping("/publico")
	public ResponseEntity<CatalogoPublicoDTO> obtenerPublico() {
		return conCabeceras(catalogo.obtenerPublico());
	}

	private static <T> ResponseEntity<T> conCabeceras(T cuerpo) {
		return ResponseEntity.ok().cacheControl(CACHE).header(HttpHeaders.VARY, HttpHeaders.COOKIE).body(cuerpo);
	}

	private static boolean esGestion(Authentication autenticacion) {
		if (autenticacion == null || autenticacion.getAuthorities() == null) {
			return false;
		}
		for (GrantedAuthority autoridad : autenticacion.getAuthorities()) {
			if (GESTION.contains(autoridad.getAuthority())) {
				return true;
			}
		}
		return false;
	}
}
