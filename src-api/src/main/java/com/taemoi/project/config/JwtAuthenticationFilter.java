package com.taemoi.project.config;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.taemoi.project.entities.Roles;
import com.taemoi.project.repositories.AlumnoRepository;
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
	private final AlumnoRepository alumnoRepository;

	public JwtAuthenticationFilter(JwtService jwtService, UsuarioService usuarioService, AlumnoRepository alumnoRepository) {
		this.jwtService = jwtService;
		this.usuarioService = usuarioService;
		this.alumnoRepository = alumnoRepository;
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
			List<String> jwtCandidates = new ArrayList<>();
			if (cookies != null) {
				for (Cookie cookie : cookies) {
					if ("jwt".equals(cookie.getName())) {
						String value = cookie.getValue();
						if (value != null && !value.isBlank()) {
							jwtCandidates.add(value.trim());
						}
					}
				}
			}

			if (jwtCandidates.isEmpty()) {
				filterChain.doFilter(request, response);
				return;
			}

			if (SecurityContextHolder.getContext().getAuthentication() == null) {
				boolean autenticado = false;
				for (String jwt : jwtCandidates) {
					try {
						final String userEmail = jwtService.extractUserName(jwt);
						if (userEmail == null || userEmail.isBlank()) {
							continue;
						}

						UserDetails userDetails = usuarioService.loadUserByUsername(userEmail);

						boolean roleUser = userDetails.getAuthorities().stream()
								.anyMatch(authority -> Roles.ROLE_USER.toString().equals(authority.getAuthority()));
						boolean roleAdminOrManager = userDetails.getAuthorities().stream()
								.anyMatch(authority -> Roles.ROLE_ADMIN.toString().equals(authority.getAuthority())
										|| Roles.ROLE_MANAGER.toString().equals(authority.getAuthority()));
						boolean requiereAlumnoActivo = roleUser && !roleAdminOrManager;
						boolean hasActiveAlumno = !requiereAlumnoActivo
								|| alumnoRepository.existsByEmailIgnoreCaseAndActivoTrue(userEmail);

						if (jwtService.isTokenValid(jwt, userDetails) && userDetails.isEnabled() && hasActiveAlumno) {
							UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
									userDetails,
									null,
									userDetails.getAuthorities());
							SecurityContextHolder.getContext().setAuthentication(authToken);
							autenticado = true;
							break;
						} else if (!hasActiveAlumno) {
							logger.info(">>> JWT rechazado para usuario sin alumnos activos: {}", userEmail);
						}
					} catch (Exception tokenEx) {
						logger.warn(">>> JWT candidate inválido para {}: {}", requestPath, tokenEx.getMessage());
					}
				}

				if (!autenticado) {
					SecurityContextHolder.clearContext();
				}
			}
		} catch (Exception e) {
			// Token inválido o expirado - no autenticar al usuario
			// En lugar de devolver error aquí (que no tendría headers CORS),
			// dejamos que la petición continúe sin autenticación.
			// Spring Security devolverá un 401 con los headers CORS correctos.
			logger.warn(">>> Token JWT inválido o expirado para {}: {}", requestPath, e.getMessage());
			SecurityContextHolder.clearContext();
		}

		logger.info(">>> JwtAuthenticationFilter completed for: {}", requestPath);
		filterChain.doFilter(request, response);
	}
}
