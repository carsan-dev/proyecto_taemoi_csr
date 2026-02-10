package com.taemoi.project.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

import com.taemoi.project.entities.Roles;
import com.taemoi.project.services.UsuarioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuración de seguridad para la aplicación.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

	private static final Logger logger = LoggerFactory.getLogger(SecurityConfiguration.class);

	/**
	 * Inyección del filtro de JWT.
	 */
	@Autowired
	private JwtAuthenticationFilter jwtAuthenticationFilter;

	/**
	 * Inyección del servicio de usuario.
	 */
	@Autowired
	private UsuarioService usuarioService;

	/**
	 * Inyección del manejador de éxito de OAuth2.
	 */
	@Autowired
	private OAuth2AuthenticationSuccessHandler oauth2SuccessHandler;

	/**
	 * Inyección del codificador de contraseñas.
	 */
	@Autowired
	private PasswordEncoder passwordEncoder;

	/**
	 * Configures which paths should be completely ignored by Spring Security.
	 * No paths are completely ignored anymore - all go through SecurityFilterChain.
	 * This allows granular control over alumno images vs public images.
	 */
	@Bean
	org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer webSecurityCustomizer() {
		logger.info("========================================");
		logger.info("WebSecurityCustomizer: No paths ignored");
		logger.info("All image and document access controlled by SecurityFilterChain");
		logger.info("========================================");
		return (web) -> {};
	}

	/**
	 * Configuración del filtro de seguridad para las solicitudes HTTP.
	 *
	 * @param http El objeto HttpSecurity que se configura.
	 * @return Un objeto SecurityFilterChain configurado.
	 * @throws Exception Si hay un error durante la configuración.
	 */
	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		logger.info("========================================");
		logger.info("Configuring SecurityFilterChain");
		logger.info("CSRF ignoring: /api/**, /login/oauth2/**, /oauth2/**");
		logger.info("Protected resources: /imagenes/alumnos/**, /documentos/** (ADMIN/MANAGER only)");
		logger.info("Public images: /imagenes/** (except alumnos subdirectory)");
		logger.info("========================================");

		http.csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
				.ignoringRequestMatchers("/api/**", "/login/oauth2/**", "/oauth2/**", "/imagenes/**"))
				.authorizeHttpRequests(request -> request
						// OAuth2 endpoints
						.requestMatchers("/login/oauth2/**").permitAll()
						.requestMatchers("/oauth2/**").permitAll()
						// Static resources that browsers request automatically
						.requestMatchers("/favicon.ico", "/error").permitAll()
						// Protected images - alumno photos require ADMIN or MANAGER role
						.requestMatchers("/imagenes/alumnos/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						// Public images - eventos, etc. (must come after specific rules)
						.requestMatchers("/imagenes/**").permitAll()
						// Public event documents
						.requestMatchers(HttpMethod.GET, "/documentos/Documentos_Eventos/**").permitAll()
						// Document access - requires ADMIN or MANAGER role
						.requestMatchers("/documentos/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						// API endpoints
						.requestMatchers(HttpMethod.OPTIONS, "/api/**").permitAll()
						.requestMatchers("/api/auth/**").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/alumnos/{alumnoId}/grupos")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString(),
								Roles.ROLE_USER.toString())
						.requestMatchers(HttpMethod.GET, "/api/alumnos/{alumnoId}/deportes")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString(),
								Roles.ROLE_USER.toString())
						.requestMatchers(HttpMethod.GET, "/api/alumnos/{alumnoId}/turnos")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString(),
								Roles.ROLE_USER.toString())
						.requestMatchers(HttpMethod.GET, "/api/alumnos/{alumnoId}/documentos")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString(),
								Roles.ROLE_USER.toString())
						.requestMatchers(HttpMethod.GET, "/api/alumnos/{alumnoId}/documentos/{documentoId}/descargar")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString(),
								Roles.ROLE_USER.toString())
						.requestMatchers(HttpMethod.GET, "/api/alumnos/{alumnoId}/reto-diario")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString(),
								Roles.ROLE_USER.toString())
						.requestMatchers(HttpMethod.PUT, "/api/alumnos/{alumnoId}/reto-diario/completar")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString(),
								Roles.ROLE_USER.toString())
						.requestMatchers(HttpMethod.POST, "/api/alumnos/aptos/{id}/actualizar-grado")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.GET, "/api/alumnos/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.POST, "/api/alumnos/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.POST, "/api/alumnos/{alumnoId}/turnos/{turnoId}")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.PUT, "/api/alumnos/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.DELETE, "/api/alumnos/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.DELETE, "/api/alumnos/{alumnoId}/turnos/{turnoId}")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.GET, "/api/grupos/{grupoId}/turnos")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.GET, "/api/grupos/{grupoId}/alumnos/{alumnoId}/turnos")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString(),
								Roles.ROLE_USER.toString())
						.requestMatchers(HttpMethod.GET, "/api/grupos/conteo-alumnos")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.GET, "/api/grupos/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.POST, "/api/grupos/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.PUT, "/api/grupos/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.DELETE, "/api/grupos/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.GET, "/api/turnos/dto").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/turnos/limite").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/turnos/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.POST, "/api/turnos/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.PUT, "/api/turnos/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.DELETE, "/api/turnos/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.GET, "/api/eventos").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/eventos/{eventoId}").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/eventos/{eventoId}/imagen").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/eventos/{eventoId}/documentos/{documentoId}/descargar")
						.permitAll()
						.requestMatchers(HttpMethod.HEAD, "/api/eventos/{eventoId}/imagen").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/eventos/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.POST, "/api/eventos/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.PUT, "/api/eventos/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.DELETE, "/api/eventos/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.GET, "/api/pagos/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.POST, "/api/pagos/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.PUT, "/api/pagos/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.DELETE, "/api/pagos/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.GET, "/api/productos/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.POST, "/api/productos/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.PUT, "/api/productos/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.DELETE, "/api/productos/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.GET, "/api/productos-alumno/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.POST, "/api/productos-alumno/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.PUT, "/api/productos-alumno/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.DELETE, "/api/productos-alumno/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.GET, "/api/convocatorias/alumnos/{alumnoId}")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString(),
								Roles.ROLE_USER.toString())
						.requestMatchers(HttpMethod.GET, "/api/convocatorias/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.POST, "/api/convocatorias/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.PUT, "/api/convocatorias/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.DELETE, "/api/convocatorias/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.GET, "/api/informes/asistencia").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/informes/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.POST, "/api/informes/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.PUT, "/api/informes/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.DELETE, "/api/informes/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.GET, "/api/admin/**").hasAnyAuthority(Roles.ROLE_ADMIN.toString())
						.requestMatchers(HttpMethod.POST, "/api/mail/**").permitAll().anyRequest().authenticated())
				.sessionManagement(manager -> manager.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authenticationProvider(authenticationProvider())
				.oauth2Login(oauth2 -> oauth2
						.successHandler(oauth2SuccessHandler)
						.permitAll())
				.exceptionHandling(exceptions -> exceptions
						.defaultAuthenticationEntryPointFor(
								new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
								request -> {
									String uri = request.getRequestURI();
									String contextPath = request.getContextPath();
									String path = contextPath != null && !contextPath.isEmpty()
											? uri.substring(contextPath.length())
											: uri;
									return "/api".equals(path) || path.startsWith("/api/");
								})
						.authenticationEntryPoint((request, response, authException) -> {
							// Redirect unauthenticated requests to OAuth2 login
							String requestURI = request.getRequestURI();
							logger.warn("========================================");
							logger.warn("AUTHENTICATION ENTRY POINT TRIGGERED!");
							logger.warn("Request URI: {}", requestURI);
							logger.warn("Request Method: {}", request.getMethod());
							logger.warn("Auth Exception: {}", authException.getMessage());
							logger.warn("This should NOT happen for /imagenes/** (documents now require auth)");
							logger.warn("========================================");
							response.sendRedirect("/oauth2/authorization/google");
						}));
		http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}

	/**
	 * Crea un proveedor de autenticación.
	 *
	 * @return Un objeto AuthenticationProvider.
	 */
	@Bean
	@SuppressWarnings("deprecation")
	AuthenticationProvider authenticationProvider() {
		DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
		authProvider.setUserDetailsService(usuarioService);
		authProvider.setPasswordEncoder(passwordEncoder);
		return authProvider;
	}

	/**
	 * Obtiene el AuthenticationManager.
	 *
	 * @param config La configuración de autenticación.
	 * @return Un objeto AuthenticationManager.
	 * @throws Exception Si hay un error al obtener el AuthenticationManager.
	 */
	@Bean
	AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
		return config.getAuthenticationManager();
	}
}
