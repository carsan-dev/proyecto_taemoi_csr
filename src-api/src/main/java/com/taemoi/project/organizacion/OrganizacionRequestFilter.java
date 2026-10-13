package com.taemoi.project.organizacion;

import java.io.IOException;
import java.util.Optional;

import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Fija la organización activa durante cada petición y la limpia siempre al terminar.
 * Se registra antes de la cadena de Spring Security ({@link OrganizacionWebConfig}).
 *
 * <p>Es también el ÚNICO sitio donde se verifica el JWT de la petición ({@link IdentificadorSesion}):
 * el resultado se deja en el atributo {@link SesionVerificada#ATRIBUTO} y
 * {@code JwtAuthenticationFilter} lo usa para autenticar, sin volver a leer el token.
 */
public class OrganizacionRequestFilter extends OncePerRequestFilter {

	private final ResolutorOrganizacion resolutor;
	private final IdentificadorSesion identificador;

	public OrganizacionRequestFilter(ResolutorOrganizacion resolutor, IdentificadorSesion identificador) {
		this.resolutor = resolutor;
		this.identificador = identificador;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		// Se restaura lo que hubiera (no se limpia): un despacho ERROR anidado dentro de la petición
		// no debe dejar sin organización al resto de la petición original.
		Long anterior = TenantContext.get();
		TenantContext.set(resolverOrganizacion(request));
		try {
			chain.doFilter(request, response);
		} finally {
			TenantContext.restaurar(anterior);
		}
	}

	/**
	 * Despacho ERROR dentro del mismo despacho (p. ej. {@code sendError} reenviado por el contenedor
	 * mientras el filtro sigue activo): también necesita organización, porque open-in-view abre una
	 * sesión de Hibernate para {@code /error}. Reutiliza la sesión ya verificada (no relee el token).
	 */
	@Override
	protected void doFilterNestedErrorDispatch(HttpServletRequest request, HttpServletResponse response,
			FilterChain chain) throws ServletException, IOException {
		doFilterInternal(request, response, chain);
	}

	/**
	 * PUNTO ÚNICO de resolución de la organización de una petición:
	 * <ul>
	 * <li>JWT válido con membresía activa → la organización de la sesión (claim {@code org}, o la única
	 * del usuario si el token es heredado);</li>
	 * <li>sin JWT o con un JWT rechazado → petición anónima en la organización por defecto
	 * (endpoints públicos: preinscripción, eventos, login, registro...).</li>
	 * </ul>
	 * La identificación solo lee tablas globales; se hace con la organización por defecto fijada
	 * porque Hibernate exige una al abrir cualquier sesión. En los despachos ASYNC y ERROR se reutiliza
	 * el resultado (atributo de la petición): el token se verifica una sola vez por petición.
	 */
	protected Long resolverOrganizacion(HttpServletRequest request) {
		Long porDefecto = resolutor.getOrganizacionPorDefecto();
		if (request.getAttribute(SesionVerificada.ATRIBUTO_RESUELTA) == null) {
			Optional<SesionVerificada> sesion = TenantContext.calcularCon(porDefecto,
					() -> identificador.identificar(request));
			request.setAttribute(SesionVerificada.ATRIBUTO_RESUELTA, Boolean.TRUE);
			sesion.ifPresent(s -> request.setAttribute(SesionVerificada.ATRIBUTO, s));
		}
		Object sesion = request.getAttribute(SesionVerificada.ATRIBUTO);
		return sesion instanceof SesionVerificada verificada ? verificada.organizacionId() : porDefecto;
	}

	@Override
	protected boolean shouldNotFilterAsyncDispatch() {
		return false;
	}

	/** El despacho ERROR ({@code /error}) también abre sesión de Hibernate con open-in-view. */
	@Override
	protected boolean shouldNotFilterErrorDispatch() {
		return false;
	}
}
