package com.taemoi.project.configuracion;

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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

import com.taemoi.project.entidades.Roles;
import com.taemoi.project.servicios.UsuarioService;

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
	 * Configuración del filtro de seguridad para las solicitudes HTTP.
	 *
	 * @param http El objeto HttpSecurity que se configura.
	 * @return Un objeto SecurityFilterChain configurado.
	 * @throws Exception Si hay un error durante la configuración.
	 */
	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
				.ignoringRequestMatchers("/api/**")).authorizeHttpRequests(request -> request
						.requestMatchers(HttpMethod.OPTIONS, "/api/**").permitAll().requestMatchers("/api/auth/**")
						.permitAll().requestMatchers(HttpMethod.GET, "/imagenes/**").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/alumnos/{alumnoId}/grupos")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString(),
								Roles.ROLE_USER.toString())
						.requestMatchers(HttpMethod.GET, "/api/alumnos/{alumnoId}/turnos")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString(),
								Roles.ROLE_USER.toString())
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
						.requestMatchers(HttpMethod.GET, "/api/admin/**").hasAnyAuthority(Roles.ROLE_ADMIN.toString())
						.requestMatchers(HttpMethod.POST, "/api/mail/**").permitAll().anyRequest().authenticated())
				.sessionManagement(manager -> manager.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authenticationProvider(authenticationProvider());
		http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}

	/**
	 * Crea un codificador de contraseñas.
	 *
	 * @return Un objeto PasswordEncoder.
	 */
	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	/**
	 * Crea un proveedor de autenticación.
	 *
	 * @return Un objeto AuthenticationProvider.
	 */
	@Bean
	AuthenticationProvider authenticationProvider() {
		DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
		authProvider.setUserDetailsService(usuarioService); // Ahora es válido
		authProvider.setPasswordEncoder(passwordEncoder());
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