package com.taemoi.project.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

import com.taemoi.project.entities.Roles;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.HostDeLaPeticion;
import com.taemoi.project.services.JwtService;
import com.taemoi.project.services.MembresiaService;
import com.taemoi.project.services.OAuth2UserService;

/**
 * Fase 4, pieza 3: vuelta de Google por Host con la configuración de producción ({@code app.base.url} y
 * {@code cors.allowed.origin} en {@code https://moiskimdo.es}, perfil {@code production}). En moiskimdo.es las cabeceras
 * {@code Set-Cookie}, el destino y la escuela salen como antes de la pieza (las cadenas se comprobaron con el código
 * anterior); en el Host X de la tabla, cookie solo del host, destino en su origen y la escuela X si puede entrar; un Host
 * fuera de la tabla nunca es destino (redirección abierta).
 */
class OAuth2AuthenticationSuccessHandlerPorHostTest {

	private static final String MOISKIMDO = "https://moiskimdo.es";
	private static final long X = 7L;
	private static final String EXPIRA = "Expires=Thu, 1 Jan 1970 00:00:00 GMT";
	private static final String BORRAR_JWT = "jwt=; Path=/; Max-Age=0; " + EXPIRA + "; Secure; HttpOnly; SameSite=Lax";
	private static final String BORRAR_REMEMBER = "rememberMe=; Path=/; Max-Age=0; " + EXPIRA + "; Secure; SameSite=Lax";

	private final OAuth2UserService oauth2UserService = mock(OAuth2UserService.class);
	private final JwtService jwtService = mock(JwtService.class);
	private final MembresiaService membresiaService = mock(MembresiaService.class);
	private final OAuth2AuthenticationSuccessHandler handler = new OAuth2AuthenticationSuccessHandler(
			oauth2UserService, jwtService, membresiaService);
	private final OAuth2User google = mock(OAuth2User.class);
	private Usuario usuario;

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(handler, "frontendUrl", MOISKIMDO);
		ReflectionTestUtils.setField(handler, "activeProfile", "production");
		ReflectionTestUtils.setField(handler, "appBaseUrl", MOISKIMDO);
		usuario = new Usuario();
		usuario.setId(10L);
		usuario.setEmail("persona@example.test");
		when(oauth2UserService.processOAuth2Login(google, "google")).thenReturn(usuario);
		when(membresiaService.elegirOrganizacionParaLogin(usuario)).thenReturn(Optional.of(1L));
		when(membresiaService.autoridadesEn(10L, 1L)).thenReturn(EnumSet.of(Roles.ROLE_USER));
		when(membresiaService.autoridadesEn(10L, X)).thenReturn(EnumSet.of(Roles.ROLE_USER));
		when(jwtService.generateToken(usuario, 1000L * 60 * 60 * 10, 1L)).thenReturn("token-uno");
		when(jwtService.generateToken(usuario, 1000L * 60 * 60 * 10, X)).thenReturn("token-x");
	}

	@Test
	void enMoiskimdo_cookiesYDestinoLiteralesDeHoy() throws Exception {
		MockHttpServletResponse respuesta = volver(peticion("moiskimdo.es", 1L, true));

		assertEquals(List.of(BORRAR_JWT,
				"jwt=; Path=/; Domain=moiskimdo.es; Max-Age=0; " + EXPIRA + "; Secure; HttpOnly; SameSite=Lax",
				"jwt=token-uno; Path=/; Domain=moiskimdo.es; Secure; HttpOnly; SameSite=Lax",
				BORRAR_REMEMBER,
				"rememberMe=; Path=/; Domain=moiskimdo.es; Max-Age=0; " + EXPIRA + "; Secure; SameSite=Lax"),
				respuesta.getHeaders("Set-Cookie"));
		assertEquals(MOISKIMDO + "/userpage", respuesta.getRedirectedUrl());
	}

	@Test
	void enMoiskimdo_laReglaDeSiempre_sinMirarElHost() throws Exception {
		volver(peticion("moiskimdo.es", 1L, true));

		verify(membresiaService).elegirOrganizacionParaLogin(usuario);
		verify(membresiaService, never()).puedeEntrarEn(any(), any());
		verify(membresiaService).registrarUltimaOrganizacion(usuario, 1L);
	}

	@Test
	void enHostX_cookieSoloDelHostYDestinoEnSuOrigen() throws Exception {
		when(membresiaService.puedeEntrarEn(usuario, X)).thenReturn(true);

		MockHttpServletResponse respuesta = volver(peticion("x.example", X, false));

		assertEquals(List.of(BORRAR_JWT, "jwt=token-x; Path=/; Secure; HttpOnly; SameSite=Lax", BORRAR_REMEMBER),
				respuesta.getHeaders("Set-Cookie"));
		assertEquals("https://x.example/userpage", respuesta.getRedirectedUrl());
	}

	@Test
	void enHostX_variasEscuelas_entraEnX() throws Exception {
		when(membresiaService.puedeEntrarEn(usuario, X)).thenReturn(true);

		volver(peticion("x.example", X, false));

		verify(jwtService).generateToken(usuario, 1000L * 60 * 60 * 10, X);
		verify(membresiaService).registrarUltimaOrganizacion(usuario, X);
	}

	@Test
	void enHostX_sinAccesoAX_laReglaDeSiempre() throws Exception {
		when(membresiaService.puedeEntrarEn(usuario, X)).thenReturn(false);

		MockHttpServletResponse respuesta = volver(peticion("x.example", X, false));

		verify(membresiaService).registrarUltimaOrganizacion(usuario, 1L);
		assertTrue(respuesta.getHeaders("Set-Cookie").contains("jwt=token-uno; Path=/; Secure; HttpOnly; SameSite=Lax"),
				respuesta.getHeaders("Set-Cookie").toString());
		assertEquals("https://x.example/userpage", respuesta.getRedirectedUrl());
	}

	@Test
	void hostFueraDeLaTabla_destinoYErroresAlOrigenPorDefecto() throws Exception {
		MockHttpServletRequest ajena = new MockHttpServletRequest("GET", "/login/oauth2/code/google");
		ajena.setScheme("https");
		ajena.setServerName("evil.example");
		ajena.setServerPort(443);
		assertEquals(MOISKIMDO + "/userpage", volver(ajena).getRedirectedUrl());

		when(membresiaService.elegirOrganizacionParaLogin(usuario)).thenReturn(Optional.empty());
		when(membresiaService.organizacionesConMembresiaActiva(10L)).thenReturn(List.of());
		String error = volver(ajena).getRedirectedUrl();
		assertTrue(error.startsWith(MOISKIMDO + "/login?error=no_alumno_found&message="), error);
	}

	@Test
	void enHostX_erroresEnSuOrigen() throws Exception {
		when(membresiaService.elegirOrganizacionParaLogin(usuario)).thenReturn(Optional.empty());
		when(membresiaService.organizacionesConMembresiaActiva(10L)).thenReturn(List.of());

		String error = volver(peticion("x.example", X, false)).getRedirectedUrl();
		assertTrue(error.startsWith("https://x.example/login?error=no_alumno_found&message="), error);

		when(oauth2UserService.processOAuth2Login(google, "google")).thenThrow(new IllegalStateException("caída"));
		error = volver(peticion("x.example", X, false)).getRedirectedUrl();
		assertTrue(error.startsWith("https://x.example/login?error=oauth_error&message="), error);
	}

	private MockHttpServletResponse volver(MockHttpServletRequest peticion) throws Exception {
		MockHttpServletResponse respuesta = new MockHttpServletResponse();
		handler.onAuthenticationSuccess(peticion, respuesta, new TestingAuthenticationToken(google, null));
		return respuesta;
	}

	/** Como la deja el filtro de organización detrás de nginx: https, puerto 443 y el Host anotado. */
	private static MockHttpServletRequest peticion(String host, Long escuela, boolean porDefecto) {
		MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/login/oauth2/code/google");
		peticion.setScheme("https");
		peticion.setServerName(host);
		peticion.setServerPort(443);
		peticion.setAttribute(HostDeLaPeticion.ATRIBUTO, new HostDeLaPeticion.Registrado(host, escuela, porDefecto));
		return peticion;
	}
}
