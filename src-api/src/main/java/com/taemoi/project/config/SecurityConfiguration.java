package com.taemoi.project.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

import com.taemoi.project.entities.Roles;
import com.taemoi.project.services.UsuarioService;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Configuración de seguridad para la aplicación.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

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
	 * Configuración del filtro de seguridad para las solicitudes HTTP.
	 *
	 * @param http El objeto HttpSecurity que se configura.
	 * @return Un objeto SecurityFilterChain configurado.
	 * @throws Exception Si hay un error durante la configuración.
	 */
	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
				.ignoringRequestMatchers("/api/**", "/login/oauth2/**", "/oauth2/**", "/imagenes/**", "/documentos/**"))
				.authorizeHttpRequests(request -> request
						// Static resources - MUST be first to avoid OAuth2 redirect
						.requestMatchers("/imagenes/**").permitAll()
						.requestMatchers("/documentos/**").permitAll()
						// OAuth2 endpoints
						.requestMatchers("/login/oauth2/**").permitAll()
						.requestMatchers("/oauth2/**").permitAll()
						// API endpoints
						.requestMatchers(HttpMethod.OPTIONS, "/api/**").permitAll()
						.requestMatchers("/api/auth/**").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/alumnos/{alumnoId}/grupos")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString(),
								Roles.ROLE_USER.toString())
						.requestMatchers(HttpMethod.GET, "/api/alumnos/{alumnoId}/turnos")
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
						.requestMatchers(HttpMethod.GET, "/api/turnos/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.GET, "/api/turnos/dto").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/turnos/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.PUT, "/api/turnos/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.DELETE, "/api/turnos/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.GET, "/api/eventos").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/eventos/{eventoId}").permitAll()
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
						.authorizationEndpoint(authorization -> authorization
								.baseUri("/oauth2/authorize"))
						// Explicitly prevent OAuth2 login for static resources
						.permitAll())
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint((request, response, authException) -> {
							// Don't redirect static resources to OAuth2 login
							String requestPath = request.getRequestURI();
							if (requestPath.startsWith("/imagenes/") || requestPath.startsWith("/documentos/")) {
								// Return 404 for non-existent static resources without OAuth redirect
								response.sendError(HttpServletResponse.SC_NOT_FOUND);
							} else {
								// For other paths, let OAuth2 handle the redirect
								response.sendRedirect("/oauth2/authorize/google");
							}
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