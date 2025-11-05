package com.taemoi.project.config;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.taemoi.project.entities.Usuario;
import com.taemoi.project.services.JwtService;
import com.taemoi.project.services.OAuth2UserService;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Manejador de éxito de autenticación OAuth2.
 * Se ejecuta cuando un usuario se autentica exitosamente mediante Google OAuth2.
 * Crea automáticamente un usuario si existe un alumno con el email proporcionado.
 */
@Component
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

	private final OAuth2UserService oauth2UserService;
	private final JwtService jwtService;

	@Value("${cors.allowed.origin}")
	private String frontendUrl;

	public OAuth2AuthenticationSuccessHandler(OAuth2UserService oauth2UserService,
											  JwtService jwtService) {
		this.oauth2UserService = oauth2UserService;
		this.jwtService = jwtService;
	}

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request,
										HttpServletResponse response,
										Authentication authentication) throws IOException, ServletException {
		OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();

		try {
			// Procesar el login OAuth2 y crear/obtener el usuario
			Usuario usuario = oauth2UserService.processOAuth2Login(oauth2User, "google");

			// Generar JWT token
			String jwt = jwtService.generateToken(usuario);

			// Crear cookie HTTP-Only con el JWT (usando Cookie estándar como en AuthenticationController)
			jakarta.servlet.http.Cookie jwtCookie = new jakarta.servlet.http.Cookie("jwt", jwt);
			jwtCookie.setHttpOnly(true);
			jwtCookie.setSecure(false); // Cambiar a true en producción con HTTPS
			jwtCookie.setPath("/");
			jwtCookie.setMaxAge(60 * 60 * 10); // 10 horas

			// Agregar la cookie a la respuesta HTTP
			response.addCookie(jwtCookie);

			// Redirigir al frontend basado en el rol del usuario
			String redirectPath;
			if (usuario.getRoles().contains(com.taemoi.project.entities.Roles.ROLE_ADMIN) ||
				usuario.getRoles().contains(com.taemoi.project.entities.Roles.ROLE_MANAGER)) {
				redirectPath = "/adminpage";
			} else {
				redirectPath = "/userpage";
			}
			String redirectUrl = frontendUrl + redirectPath;
			getRedirectStrategy().sendRedirect(request, response, redirectUrl);

		} catch (IllegalArgumentException e) {
			// No se encontró alumno con ese email
			String errorUrl = frontendUrl + "/login?error=no_alumno_found&message=" +
							  java.net.URLEncoder.encode(e.getMessage(), "UTF-8");
			getRedirectStrategy().sendRedirect(request, response, errorUrl);
		} catch (Exception e) {
			// Error general
			String errorUrl = frontendUrl + "/login?error=oauth_error&message=" +
							  java.net.URLEncoder.encode("Error en la autenticación OAuth2", "UTF-8");
			getRedirectStrategy().sendRedirect(request, response, errorUrl);
		}
	}
}
