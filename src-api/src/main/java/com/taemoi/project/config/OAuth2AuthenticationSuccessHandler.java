package com.taemoi.project.config;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
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

	@Value("${spring.profiles.active:default}")
	private String activeProfile;

	@Value("${app.base.url:}")
	private String appBaseUrl;

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

			boolean rememberMe = false;
			jakarta.servlet.http.Cookie[] cookies = request.getCookies();
			if (cookies != null) {
				for (jakarta.servlet.http.Cookie cookie : cookies) {
					if ("rememberMe".equals(cookie.getName())) {
						String value = cookie.getValue();
						rememberMe = "true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value);
						break;
					}
				}
			}

			// Generar JWT token
			long expirationMillis = rememberMe ? 1000L * 60 * 60 * 24 * 30 : 1000L * 60 * 60 * 10;
			String jwt = jwtService.generateToken(usuario, expirationMillis);

			// Determinar si usar Secure basado en el perfil activo
			boolean isProduction = "production".equals(activeProfile) || "docker".equals(activeProfile);

			response.addHeader(HttpHeaders.SET_COOKIE,
					construirCookieJwt(jwt, isProduction, rememberMe, false, null).toString());
			resolverDominioCookie().ifPresent(domain -> response.addHeader(
					HttpHeaders.SET_COOKIE,
					construirCookieJwt(jwt, isProduction, rememberMe, false, domain).toString()));

			response.addHeader(HttpHeaders.SET_COOKIE,
					construirCookieRememberMe(isProduction, null).toString());
			resolverDominioCookie().ifPresent(domain -> response.addHeader(
					HttpHeaders.SET_COOKIE,
					construirCookieRememberMe(isProduction, domain).toString()));

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

	private Optional<String> resolverDominioCookie() {
		String baseUrl = (appBaseUrl != null && !appBaseUrl.isBlank()) ? appBaseUrl : frontendUrl;
		if (baseUrl == null || baseUrl.isBlank()) {
			return Optional.empty();
		}

		try {
			String host = URI.create(baseUrl.trim()).getHost();
			if (host == null || host.isBlank()) {
				return Optional.empty();
			}
			host = host.toLowerCase(Locale.ROOT);
			if ("localhost".equals(host) || host.matches("^\\d+\\.\\d+\\.\\d+\\.\\d+$")) {
				return Optional.empty();
			}
			if (host.startsWith("www.")) {
				host = host.substring(4);
			}
			return host.isBlank() ? Optional.empty() : Optional.of(host);
		} catch (Exception e) {
			return Optional.empty();
		}
	}

	private ResponseCookie construirCookieJwt(
			String valor,
			boolean secure,
			boolean rememberMe,
			boolean eliminar,
			String domain) {
		ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from("jwt", valor)
				.httpOnly(true)
				.secure(secure)
				.path("/")
				.sameSite("Lax");

		if (eliminar) {
			builder.maxAge(Duration.ZERO);
		} else if (rememberMe) {
			builder.maxAge(Duration.ofDays(30));
		}

		if (domain != null && !domain.isBlank()) {
			builder.domain(domain);
		}
		return builder.build();
	}

	private ResponseCookie construirCookieRememberMe(boolean secure, String domain) {
		ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from("rememberMe", "")
				.secure(secure)
				.path("/")
				.maxAge(Duration.ZERO)
				.sameSite("Lax");
		if (domain != null && !domain.isBlank()) {
			builder.domain(domain);
		}
		return builder.build();
	}
}
