package com.taemoi.project.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

import com.taemoi.project.entities.Usuario;
import com.taemoi.project.services.JwtService;
import com.taemoi.project.services.MembresiaService;
import com.taemoi.project.services.OAuth2UserService;
import com.taemoi.project.services.impl.AuthenticationServiceImpl;

/** Motivo del rechazo del inicio de sesión con Google cuando ninguna escuela abre sesión. */
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

	private String redirigir() throws Exception {
		MockHttpServletResponse respuesta = new MockHttpServletResponse();
		handler.onAuthenticationSuccess(new MockHttpServletRequest("GET", "/login/oauth2/code/google"), respuesta,
				new TestingAuthenticationToken(google, null));
		return respuesta.getRedirectedUrl();
	}
}
