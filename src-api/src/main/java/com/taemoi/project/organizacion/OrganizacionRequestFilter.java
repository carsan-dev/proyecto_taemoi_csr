package com.taemoi.project.organizacion;

import java.io.IOException;

import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Fija la organización activa durante cada petición y la limpia siempre al terminar.
 * Se registra antes de la cadena de Spring Security ({@link OrganizacionWebConfig}).
 */
public class OrganizacionRequestFilter extends OncePerRequestFilter {

	private final ResolutorOrganizacion resolutor;

	public OrganizacionRequestFilter(ResolutorOrganizacion resolutor) {
		this.resolutor = resolutor;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		TenantContext.set(resolverOrganizacion(request));
		try {
			chain.doFilter(request, response);
		} finally {
			TenantContext.clear();
		}
	}

	/**
	 * PUNTO ÚNICO de resolución de la organización de una petición.
	 * Hoy: siempre la organización por defecto (Taemoi). La siguiente tarea la obtendrá
	 * del claim {@code org} del JWT (validando la membresía) y solo debe cambiar este método.
	 */
	protected Long resolverOrganizacion(HttpServletRequest request) {
		return resolutor.getOrganizacionPorDefecto();
	}

	@Override
	protected boolean shouldNotFilterAsyncDispatch() {
		return false;
	}
}
