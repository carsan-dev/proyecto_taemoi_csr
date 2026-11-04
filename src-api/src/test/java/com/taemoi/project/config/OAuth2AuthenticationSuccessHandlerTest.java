package com.taemoi.project.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

import com.taemoi.project.entities.Roles;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.services.JwtService;
import com.taemoi.project.services.MembresiaService;
import com.taemoi.project.services.OAuth2UserService;
import com.taemoi.project.services.impl.AuthenticationServiceImpl;

/**
 * Inicio de sesión con Google: el motivo del rechazo cuando ninguna escuela abre sesión y, si entra, a dónde va según
 * las autoridades de su sesión (decisión 384: el mismo destino que con contraseña).
 */
class OAuth2AuthenticationSuccessHandlerTest {

	private static final String FRONTEND = "http://localhost:4200";

	private final OAuth2UserService oauth2UserService = mock(OAuth2UserService.class);
	private final JwtService jwtService = mock(JwtService.class);
	private final MembresiaService membresiaService = mock(MembresiaService.class);
	private final OAuth2AuthenticationSuccessHandler handler = new OAuth2AuthenticationSuccessHandler(
			oauth2UserService, jwtService, membresiaService);
	private final OAuth2User google = mock(OAuth2User.class);
	private Usuario usuario;

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(handler, "frontendUrl", FRONTEND);
		ReflectionTestUtils.setField(handler, "activeProfile", "test");
		ReflectionTestUtils.setField(handler, "appBaseUrl", "");
		usuario = new Usuario();
		usuario.setId(10L);
		usuario.setEmail("persona@example.test");
		when(oauth2UserService.processOAuth2Login(google, "google")).thenReturn(usuario);
		when(membresiaService.elegirOrganizacionParaLogin(usuario)).thenReturn(Optional.empty());
	}

	@Test
	void soloRolesSinAccesoRedirigeConElMensajeNuevo() throws Exception {
		when(membresiaService.organizacionesConMembresiaActiva(10L)).thenReturn(List.of(2L));
		when(membresiaService.soloTieneRolesSinAcceso(10L)).thenReturn(true);

		assertEquals(FRONTEND + "/login?error=no_alumno_found&message="
				+ URLEncoder.encode(AuthenticationServiceImpl.ROL_SIN_ACCESO, StandardCharsets.UTF_8), redirigir());
		verify(jwtService, never()).generateToken(any(), anyLong(), any());
	}

	@Test
	void losDemasMotivosConservanSuMensaje() throws Exception {
		when(membresiaService.organizacionesConMembresiaActiva(10L)).thenReturn(List.of());
		assertEquals(FRONTEND + "/login?error=no_alumno_found&message=" + URLEncoder.encode(
				"El usuario no tiene acceso a ninguna escuela. Contacte con el administrador.", StandardCharsets.UTF_8),
				redirigir());

		when(membresiaService.organizacionesConMembresiaActiva(10L)).thenReturn(List.of(2L));
		when(membresiaService.soloTieneRolesSinAcceso(10L)).thenReturn(false);
		assertEquals(FRONTEND + "/login?error=no_alumno_found&message=" + URLEncoder.encode(
				"No hay alumnos activos asociados a este email. Contacte con el administrador.", StandardCharsets.UTF_8),
				redirigir());
	}

	/**
	 * Decisión 384. Solo familia y solo gestión tienen un único destino posible, el mismo que con contraseña, y van
	 * directos como hasta ahora. Las demás combinaciones (dos vistas, también profesor + familia por la 439, responsable
	 * de sede, solo profesor, plataforma sin
	 * roles de escuela) dependen de la vista preferida o de las capacidades: van a la entrada del frontend que aplica la
	 * regla del login con contraseña.
	 */
	static Stream<Arguments> destinoSegunAutoridades() {
		return Stream.of(
				Arguments.of(EnumSet.of(Roles.ROLE_USER), "/userpage"),
				// Decisión 439: profesor + familia elige vista (con grupos) y va a la entrada, como gestión + familia.
				Arguments.of(EnumSet.of(Roles.ROLE_USER, Roles.ROLE_PROFESOR), OAuth2AuthenticationSuccessHandler.RUTA_ENTRADA),
				Arguments.of(EnumSet.of(Roles.ROLE_ADMIN), "/adminpage"),
				Arguments.of(EnumSet.of(Roles.ROLE_MANAGER), "/adminpage"),
				Arguments.of(EnumSet.of(Roles.ROLE_ADMIN, Roles.ROLE_PROFESOR), "/adminpage"),
				Arguments.of(EnumSet.of(Roles.ROLE_ADMIN, Roles.ROLE_USER), OAuth2AuthenticationSuccessHandler.RUTA_ENTRADA),
				Arguments.of(EnumSet.of(Roles.ROLE_MANAGER, Roles.ROLE_USER), OAuth2AuthenticationSuccessHandler.RUTA_ENTRADA),
				Arguments.of(EnumSet.of(Roles.ROLE_RESPONSABLE_SEDE, Roles.ROLE_USER),
						OAuth2AuthenticationSuccessHandler.RUTA_ENTRADA),
				Arguments.of(EnumSet.of(Roles.ROLE_RESPONSABLE_SEDE), OAuth2AuthenticationSuccessHandler.RUTA_ENTRADA),
				Arguments.of(EnumSet.of(Roles.ROLE_RESPONSABLE_SEDE, Roles.ROLE_PROFESOR),
						OAuth2AuthenticationSuccessHandler.RUTA_ENTRADA),
				Arguments.of(EnumSet.of(Roles.ROLE_ADMIN, Roles.ROLE_RESPONSABLE_SEDE),
						OAuth2AuthenticationSuccessHandler.RUTA_ENTRADA),
				Arguments.of(EnumSet.of(Roles.ROLE_PROFESOR), OAuth2AuthenticationSuccessHandler.RUTA_ENTRADA),
				Arguments.of(EnumSet.noneOf(Roles.class), OAuth2AuthenticationSuccessHandler.RUTA_ENTRADA));
	}

	@ParameterizedTest(name = "{0} -> {1}")
	@MethodSource("destinoSegunAutoridades")
	void entraConSuCookieYVaAlDestinoDelLoginConContrasena(Set<Roles> autoridades, String destino) throws Exception {
		when(membresiaService.elegirOrganizacionParaLogin(usuario)).thenReturn(Optional.of(2L));
		when(membresiaService.autoridadesEn(10L, 2L)).thenReturn(autoridades);
		when(jwtService.generateToken(usuario, 1000L * 60 * 60 * 10, 2L)).thenReturn("token-sesion");
		MockHttpServletResponse respuesta = new MockHttpServletResponse();

		handler.onAuthenticationSuccess(new MockHttpServletRequest("GET", "/login/oauth2/code/google"), respuesta,
				new TestingAuthenticationToken(google, null));

		// Sin token ni datos en la URL: la sesión va solo en la cookie, como antes.
		assertEquals(FRONTEND + destino, respuesta.getRedirectedUrl());
		assertTrue(respuesta.getHeaders("Set-Cookie").stream().anyMatch(c -> c.startsWith("jwt=token-sesion;")),
				respuesta.getHeaders("Set-Cookie").toString());
		verify(membresiaService).registrarUltimaOrganizacion(usuario, 2L);
	}

	private String redirigir() throws Exception {
		MockHttpServletResponse respuesta = new MockHttpServletResponse();
		handler.onAuthenticationSuccess(new MockHttpServletRequest("GET", "/login/oauth2/code/google"), respuesta,
				new TestingAuthenticationToken(google, null));
		return respuesta.getRedirectedUrl();
	}
}
