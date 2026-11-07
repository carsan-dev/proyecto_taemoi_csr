package com.taemoi.project.organizacion;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

	private static final Logger log = LoggerFactory.getLogger(OrganizacionRequestFilter.class);

	private final ResolutorOrganizacion resolutor;
	private final IdentificadorSesion identificador;
	private final ResolutorEscuelaPorHost porHost;

	/** Sin escuela por Host: toda petición anónima va a la por defecto (como antes de la fase 4). */
	public OrganizacionRequestFilter(ResolutorOrganizacion resolutor, IdentificadorSesion identificador) {
		this(resolutor, identificador, ResolutorEscuelaPorHost.sinDominios(resolutor.getOrganizacionPorDefecto()));
	}

	public OrganizacionRequestFilter(ResolutorOrganizacion resolutor, IdentificadorSesion identificador,
			ResolutorEscuelaPorHost porHost) {
		this.resolutor = resolutor;
		this.identificador = identificador;
		this.porHost = Objects.requireNonNull(porHost, "porHost");
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
	 * del usuario si el token es heredado), aunque el Host sea de otra escuela (manda la sesión);</li>
	 * <li>sin JWT o con un JWT rechazado → petición anónima en la escuela de su Host
	 * ({@link ResolutorEscuelaPorHost}): la por defecto si el Host no está en {@code organizacion_dominio}
	 * (endpoints públicos: preinscripción, eventos, login, registro...).</li>
	 * </ul>
	 * La escuela del Host queda en el atributo {@link ResolutorEscuelaPorHost#ATRIBUTO} (también con sesión).
	 * La identificación solo lee tablas globales; se hace con la organización por defecto fijada
	 * porque Hibernate exige una al abrir cualquier sesión. En los despachos ASYNC y ERROR se reutiliza
	 * el resultado (atributos de la petición): el token se verifica y el Host se mira una sola vez por petición.
	 */
	protected Long resolverOrganizacion(HttpServletRequest request) {
		Long porDefecto = resolutor.getOrganizacionPorDefecto();
		if (request.getAttribute(SesionVerificada.ATRIBUTO_RESUELTA) == null) {
			Long delHost = porHost.escuelaDe(request);
			request.setAttribute(ResolutorEscuelaPorHost.ATRIBUTO, delHost);
			Optional<SesionVerificada> sesion = TenantContext.calcularCon(porDefecto,
					() -> identificador.identificar(request));
			request.setAttribute(SesionVerificada.ATRIBUTO_RESUELTA, Boolean.TRUE);
			sesion.ifPresent(s -> {
				request.setAttribute(SesionVerificada.ATRIBUTO, s);
				avisarSiElHostEsDeOtraEscuela(s, delHost, porDefecto, request);
			});
		}
		Object sesion = request.getAttribute(SesionVerificada.ATRIBUTO);
		if (sesion instanceof SesionVerificada verificada) {
			return verificada.organizacionId();
		}
		return request.getAttribute(ResolutorEscuelaPorHost.ATRIBUTO) instanceof Long delHost ? delHost : porDefecto;
	}

	/**
	 * Sesión de una escuela en el Host de OTRA (solo si alguien lleva la cookie a otro dominio): manda la sesión y queda
	 * un aviso. En el host de la escuela por defecto (moiskimdo.es, desconocido o IP) no se avisa: allí trabajar con la
	 * sesión de otra escuela es lo normal (multimembresía), como hasta ahora. El host del aviso está en la tabla.
	 */
	private void avisarSiElHostEsDeOtraEscuela(SesionVerificada sesion, Long delHost, Long porDefecto,
			HttpServletRequest request) {
		if (!delHost.equals(porDefecto) && !delHost.equals(sesion.organizacionId())) {
			log.warn("{}: sesión en la escuela {}, host {} de la escuela {}; manda la sesión",
					ResolutorEscuelaPorHost.AVISO_SESION_Y_HOST, sesion.organizacionId(),
					ResolutorEscuelaPorHost.normalizar(request.getServerName()), delHost);
		}
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
