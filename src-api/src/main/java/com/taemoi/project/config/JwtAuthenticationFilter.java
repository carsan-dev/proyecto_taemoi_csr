package com.taemoi.project.config;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.taemoi.project.organizacion.SesionVerificada;
import com.taemoi.project.organizacion.TenantContext;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Autentica la petición con la sesión que ya verificó {@code OrganizacionRequestFilter}
 * (firma y caducidad del JWT, usuario, membresía activa en la organización): no vuelve a leer el
 * token. Las autoridades son las de las membresías del usuario en la organización activa, no
 * {@code Usuario.getAuthorities()}.
 *
 * <p>Si no hay sesión verificada (sin JWT, JWT rechazado o filtro de organización ausente), la
 * petición sigue como anónima y Spring Security devuelve 401 donde haga falta, con cabeceras CORS.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

	@Override
	protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
			@NonNull FilterChain filterChain) throws ServletException, IOException {
		if (SecurityContextHolder.getContext().getAuthentication() == null
				&& request.getAttribute(SesionVerificada.ATRIBUTO) instanceof SesionVerificada sesion) {
			if (sesion.organizacionId().equals(TenantContext.get())) {
				List<SimpleGrantedAuthority> autoridades = sesion.autoridades().stream()
						.map(rol -> new SimpleGrantedAuthority(rol.name()))
						.toList();
				SecurityContextHolder.getContext().setAuthentication(
						new UsernamePasswordAuthenticationToken(sesion.usuario(), null, autoridades));
			} else {
				// No debería ocurrir: la organización activa la fija el mismo filtro que verifica la sesión.
				logger.warn(">>> Sesión de la organización {} en una petición de la organización {}: no se autentica",
						sesion.organizacionId(), TenantContext.get());
			}
		}
		filterChain.doFilter(request, response);
	}
}
