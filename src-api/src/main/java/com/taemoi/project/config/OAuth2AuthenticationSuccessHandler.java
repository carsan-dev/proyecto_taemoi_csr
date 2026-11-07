package com.taemoi.project.config;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.taemoi.project.entities.Roles;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.HostDeLaPeticion;
import com.taemoi.project.services.JwtService;
import com.taemoi.project.services.MembresiaService;
import com.taemoi.project.services.OAuth2UserService;
import com.taemoi.project.services.impl.AuthenticationServiceImpl;

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

	/**
	 * Entrada del frontend tras Google (ruta {@code login/google}): resuelve el destino con la misma regla que el login
	 * con contraseña (vista preferida y capacidades de la sesión). Solo lleva la sesión en la cookie, nada en la URL.
	 */
	public static final String RUTA_ENTRADA = "/login/google";

	private final OAuth2UserService oauth2UserService;
	private final JwtService jwtService;
	private final MembresiaService membresiaService;

	@Value("${cors.allowed.origin}")
	private String frontendUrl;

	@Value("${spring.profiles.active:default}")
	private String activeProfile;

	@Value("${app.base.url:}")
	private String appBaseUrl;

	public OAuth2AuthenticationSuccessHandler(OAuth2UserService oauth2UserService,
											  JwtService jwtService,
											  MembresiaService membresiaService) {
		this.oauth2UserService = oauth2UserService;
		this.jwtService = jwtService;
		this.membresiaService = membresiaService;
	}

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request,
										HttpServletResponse response,
										Authentication authentication) throws IOException, ServletException {
		OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
		// Fase 4 (pieza 3): destino en el origen del propio Host si está en organizacion_dominio (lista blanca); si no
		// (moiskimdo.es, un Host desconocido), cors.allowed.origin tal cual, como siempre. Nunca un Host ajeno.
		String origen = HostDeLaPeticion.origen(request, frontendUrl);

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

			// Escuela de la sesión: misma regla que el login con contraseña (en el Host de otra escuela, esa si puede).
			Long organizacionId = AuthenticationServiceImpl.elegirOrganizacionParaLogin(membresiaService, usuario,
					HostDeLaPeticion.escuelaPreferidaParaLogin(request))
					.orElseThrow(() -> new IllegalArgumentException(motivoRechazo(usuario)));
			membresiaService.registrarUltimaOrganizacion(usuario, organizacionId);
			Set<Roles> autoridades = membresiaService.autoridadesEn(usuario.getId(), organizacionId);

			// Generar JWT token
			long expirationMillis = rememberMe ? 1000L * 60 * 60 * 24 * 30 : 1000L * 60 * 60 * 10;
			String jwt = jwtService.generateToken(usuario, expirationMillis, organizacionId);

			// Determinar si usar Secure basado en el perfil activo
			boolean isProduction = "production".equals(activeProfile) || "docker".equals(activeProfile);
			Optional<String> dominioCookie = HostDeLaPeticion.dominioCookie(request, resolverDominioCookie());

			// Limpiar posibles cookies heredadas (host-only y domain) antes de fijar la canónica
			response.addHeader(HttpHeaders.SET_COOKIE,
					construirCookieJwt("", isProduction, false, true, null).toString());
			if (dominioCookie.isPresent()) {
				String domain = dominioCookie.get();
				response.addHeader(
						HttpHeaders.SET_COOKIE,
						construirCookieJwt("", isProduction, false, true, domain).toString());
			}
			String cookieDomain = dominioCookie.orElse(null);
			response.addHeader(HttpHeaders.SET_COOKIE,
					construirCookieJwt(jwt, isProduction, rememberMe, false, cookieDomain).toString());

			response.addHeader(HttpHeaders.SET_COOKIE,
					construirCookieRememberMe(isProduction, null).toString());
			if (dominioCookie.isPresent()) {
				String domain = dominioCookie.get();
				response.addHeader(
						HttpHeaders.SET_COOKIE,
						construirCookieRememberMe(isProduction, domain).toString());
			}

			String redirectUrl = origen + destino(autoridades);
			getRedirectStrategy().sendRedirect(request, response, redirectUrl);

		} catch (IllegalArgumentException e) {
			// No se encontró alumno con ese email
			String errorUrl = origen + "/login?error=no_alumno_found&message=" +
							  java.net.URLEncoder.encode(e.getMessage(), "UTF-8");
			getRedirectStrategy().sendRedirect(request, response, errorUrl);
		} catch (Exception e) {
			// Error general
			String errorUrl = origen + "/login?error=oauth_error&message=" +
							  java.net.URLEncoder.encode("Error en la autenticación OAuth2", "UTF-8");
			getRedirectStrategy().sendRedirect(request, response, errorUrl);
		}
	}

	/**
	 * Destino tras entrar con Google, el mismo que con contraseña (decisión 384; la regla es
	 * {@code AuthenticationService.resolverRutaInicioPorRoles} del frontend). Solo familia y solo gestión tienen un único
	 * destino posible y van directos, como antes. Las demás sesiones dependen de la vista preferida del navegador
	 * (gestión + familia) o de las capacidades ({@code ELEGIR_VISTA} de responsable o de profesor con familia, decisiones
	 * 371 y 439; {@code PANEL_SEDE}), que el servidor no decide aquí: van a {@link #RUTA_ENTRADA}, que aplica esa regla.
	 */
	private static String destino(Set<Roles> autoridades) {
		boolean gestion = autoridades.contains(Roles.ROLE_ADMIN) || autoridades.contains(Roles.ROLE_MANAGER);
		boolean familia = autoridades.contains(Roles.ROLE_USER);
		if (autoridades.contains(Roles.ROLE_RESPONSABLE_SEDE)) {
			return RUTA_ENTRADA;
		}
		if (gestion && !familia) {
			return "/adminpage";
		}
		if (familia && !gestion && !autoridades.contains(Roles.ROLE_PROFESOR)) {
			return "/userpage";
		}
		return RUTA_ENTRADA;
	}

	/** Mismos motivos que el login con contraseña; los dos de siempre conservan su texto. */
	private String motivoRechazo(Usuario usuario) {
		if (membresiaService.organizacionesConMembresiaActiva(usuario.getId()).isEmpty()) {
			return "El usuario no tiene acceso a ninguna escuela. Contacte con el administrador.";
		}
		if (membresiaService.soloTieneRolesSinAcceso(usuario.getId())) {
			return AuthenticationServiceImpl.ROL_SIN_ACCESO;
		}
		return "No hay alumnos activos asociados a este email. Contacte con el administrador.";
	}

	/** El dominio de siempre (de {@code app.base.url}); {@link HostDeLaPeticion#dominioCookie} lo quita en otro Host. */
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
