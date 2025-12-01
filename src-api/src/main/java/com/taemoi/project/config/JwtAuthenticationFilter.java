package com.taemoi.project.config;

import java.io.IOException;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.taemoi.project.services.JwtService;
import com.taemoi.project.services.UsuarioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Filtro para autenticación basada en tokens JWT.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

	private final JwtService jwtService;
	private final UsuarioService usuarioService;

	public JwtAuthenticationFilter(JwtService jwtService, UsuarioService usuarioService) {
		this.jwtService = jwtService;
		this.usuarioService = usuarioService;
	}

	/**
	 * Método que realiza la lógica de filtrado para la autenticación basada en
	 * tokens JWT.
	 *
	 * @param request     El objeto HttpServletRequest.
	 * @param response    El objeto HttpServletResponse.
	 * @param filterChain El objeto FilterChain.
	 * @throws ServletException Si ocurre un error durante el filtrado.
	 * @throws IOException      Si ocurre un error de I/O durante el filtrado.
	 */
	@Override
	protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
			@NonNull FilterChain filterChain) throws ServletException, IOException {
		String requestPath = request.getRequestURI();
		String method = request.getMethod();

		logger.info(">>> JwtAuthenticationFilter processing: {} {}", method, requestPath);

		// Skip JWT authentication only for public images (not alumno images)
		// Alumno images and documents require authentication (ADMIN or MANAGER)
		if (requestPath.startsWith("/imagenes/") && !requestPath.startsWith("/imagenes/alumnos/")) {
			logger.info(">>> SKIPPING JWT filter for public images: {}", requestPath);
			filterChain.doFilter(request, response);
			return;
		}

		try {
			Cookie[] cookies = request.getCookies();
			String jwt = null;
			if (cookies != null) {
				for (Cookie cookie : cookies) {
					if ("jwt".equals(cookie.getName())) {
						jwt = cookie.getValue();
						break;
					}
				}
			}

			if (jwt == null) {
				filterChain.doFilter(request, response);
				return;
			}

			// Extraer el nombre de usuario del token JWT
			final String userEmail = jwtService.extractUserName(jwt);

			// Verificar que el usuario no esté autenticado ya
			if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
				// Cargar los detalles del usuario
				UserDetails userDetails = usuarioService.loadUserByUsername(userEmail);

				if (jwtService.isTokenValid(jwt, userDetails)) {
					UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(userDetails,
							null, userDetails.getAuthorities());
					SecurityContextHolder.getContext().setAuthentication(authToken);
				}

			}
		} catch (Exception e) {
			logger.error(">>> ERROR en la validación del token JWT para {}: {}", requestPath, e.getMessage());
			response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token inválido");
			return;
		}

		logger.info(">>> JwtAuthenticationFilter completed for: {}", requestPath);
		filterChain.doFilter(request, response);
	}
}