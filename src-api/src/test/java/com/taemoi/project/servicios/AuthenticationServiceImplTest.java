package com.taemoi.project.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.taemoi.project.dtos.request.LoginRequest;
import com.taemoi.project.dtos.request.RegistroRequest;
import com.taemoi.project.dtos.response.JwtAuthenticationResponse;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.ResolutorOrganizacion;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.JwtService;
import com.taemoi.project.services.LoginAttemptService;
import com.taemoi.project.services.MembresiaService;
import com.taemoi.project.services.impl.AuthenticationServiceImpl;

/**
 * Inicio de sesión: la escuela y los permisos salen de las membresías ({@link MembresiaService}); la
 * regla «familia sin alumnos activos no entra» vive en {@code elegirOrganizacionParaLogin} (ver
 * {@code MembresiaServiceImplTest} y los tests de integración de sesión por organización).
 */
@ExtendWith(MockitoExtension.class)
class AuthenticationServiceImplTest {

	@Mock
	private UsuarioRepository usuarioRepository;

	@Mock
	private LoginAttemptService loginAttemptService;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private JwtService jwtService;

	@Mock
	private AuthenticationManager authenticationManager;

	@Mock
	private MembresiaService membresiaService;

	@Mock
	private ResolutorOrganizacion resolutorOrganizacion;

	@InjectMocks
	private AuthenticationServiceImpl authenticationService;

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void signup_emailYaExiste_lanzaExcepcion() {
		RegistroRequest request = new RegistroRequest("John", "Doe", "john@example.com", "password");
		when(usuarioRepository.existsByEmailIgnoreCase("john@example.com")).thenReturn(true);

		assertThrows(IllegalArgumentException.class, () -> authenticationService.signup(request));
		verify(usuarioRepository, never()).save(any());
	}

	@Test
	void signin_sinEscuelaValida_lanzaDisabledYNoEmiteToken() {
		LoginRequest request = new LoginRequest("john@example.com", "password");
		Usuario usuario = usuario(1L, "john@example.com");

		when(loginAttemptService.isBlocked("john@example.com")).thenReturn(false);
		when(authenticationManager.authenticate(any()))
				.thenReturn(new UsernamePasswordAuthenticationToken(usuario, null, usuario.getAuthorities()));
		when(membresiaService.elegirOrganizacionParaLogin(usuario)).thenReturn(Optional.empty());

		assertThrows(DisabledException.class, () -> authenticationService.signin(request));
		verify(jwtService, never()).generateToken(any(), anyLong(), any());
		verify(loginAttemptService, never()).loginSucceeded(any());
	}

	@Test
	void signin_conEscuela_emiteTokenConEsaOrganizacionYLaRecuerda() {
		LoginRequest request = new LoginRequest("manager@example.com", "password");
		Usuario usuario = usuario(2L, "manager@example.com");

		when(loginAttemptService.isBlocked("manager@example.com")).thenReturn(false);
		when(authenticationManager.authenticate(any()))
				.thenReturn(new UsernamePasswordAuthenticationToken(usuario, null, usuario.getAuthorities()));
		when(membresiaService.elegirOrganizacionParaLogin(usuario)).thenReturn(Optional.of(7L));
		when(jwtService.generateToken(eq(usuario), eq(AuthenticationServiceImpl.DURACION_SESION_MILLIS), eq(7L)))
				.thenReturn("jwt-token");

		JwtAuthenticationResponse response = authenticationService.signin(request);

		assertEquals("jwt-token", response.getToken());
		verify(membresiaService).registrarUltimaOrganizacion(usuario, 7L);
	}

	@Test
	void signin_recordarme_usaLaDuracionLarga() {
		LoginRequest request = new LoginRequest("admin@example.com", "password");
		request.setRememberMe(true);
		Usuario usuario = usuario(3L, "admin@example.com");

		when(loginAttemptService.isBlocked("admin@example.com")).thenReturn(false);
		when(authenticationManager.authenticate(any()))
				.thenReturn(new UsernamePasswordAuthenticationToken(usuario, null, usuario.getAuthorities()));
		when(membresiaService.elegirOrganizacionParaLogin(usuario)).thenReturn(Optional.of(1L));
		when(jwtService.generateToken(eq(usuario), eq(AuthenticationServiceImpl.DURACION_RECORDARME_MILLIS), eq(1L)))
				.thenReturn("jwt-token-admin");

		assertEquals("jwt-token-admin", authenticationService.signin(request).getToken());
	}

	@Test
	void cambiarOrganizacion_sinAcceso_noEmiteToken() {
		Usuario usuario = usuario(4L, "familia@example.com");
		when(membresiaService.puedeEntrarEn(usuario, 2L)).thenReturn(false);

		assertTrue(authenticationService.cambiarOrganizacion(usuario, 2L, new Date(System.currentTimeMillis() + 60_000))
				.isEmpty());
		verify(jwtService, never()).generateToken(any(), anyLong(), any());
		verify(membresiaService, never()).registrarUltimaOrganizacion(any(), any());
	}

	@Test
	void cambiarOrganizacion_conAcceso_mantieneLaCaducidadActual() {
		Usuario usuario = usuario(5L, "profe@example.com");
		when(membresiaService.puedeEntrarEn(usuario, 2L)).thenReturn(true);
		when(jwtService.generateToken(eq(usuario), anyLong(), eq(2L))).thenReturn("jwt-org-2");

		Optional<JwtAuthenticationResponse> respuesta = authenticationService.cambiarOrganizacion(usuario, 2L,
				new Date(System.currentTimeMillis() + 60_000));

		assertEquals("jwt-org-2", respuesta.orElseThrow().getToken());
		verify(jwtService).generateToken(eq(usuario),
				org.mockito.ArgumentMatchers.longThat(ms -> ms > 0 && ms <= 60_000), eq(2L));
		verify(membresiaService).registrarUltimaOrganizacion(usuario, 2L);
	}

	private static Usuario usuario(Long id, String email) {
		Usuario usuario = new Usuario();
		usuario.setId(id);
		usuario.setEmail(email);
		usuario.setContrasena("encoded");
		return usuario;
	}
}
