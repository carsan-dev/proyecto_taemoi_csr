package com.taemoi.project.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;

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
import com.taemoi.project.entities.Roles;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.JwtService;
import com.taemoi.project.services.LoginAttemptService;
import com.taemoi.project.services.impl.AuthenticationServiceImpl;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceImplTest {

	@Mock
	private UsuarioRepository usuarioRepository;

	@Mock
	private AlumnoRepository alumnoRepository;

	@Mock
	private LoginAttemptService loginAttemptService;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private JwtService jwtService;

	@Mock
	private AuthenticationManager authenticationManager;

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
	void signin_roleUserSinAlumnosActivos_lanzaDisabled() {
		LoginRequest request = new LoginRequest("john@example.com", "password");
		Usuario usuario = new Usuario();
		usuario.setEmail("john@example.com");
		usuario.setContrasena("encoded");
		usuario.setRoles(Set.of(Roles.ROLE_USER));

		when(loginAttemptService.isBlocked("john@example.com")).thenReturn(false);
		when(authenticationManager.authenticate(any()))
				.thenReturn(new UsernamePasswordAuthenticationToken(usuario, null, usuario.getAuthorities()));
		when(alumnoRepository.existsByEmailIgnoreCaseAndActivoTrue("john@example.com")).thenReturn(false);

		assertThrows(DisabledException.class, () -> authenticationService.signin(request));
		verify(jwtService, never()).generateToken(any(), any(Long.class));
	}

	@Test
	void signin_roleManagerSinAlumnoActivo_permiteLogin() {
		LoginRequest request = new LoginRequest("manager@example.com", "password");
		Usuario usuario = new Usuario();
		usuario.setEmail("manager@example.com");
		usuario.setContrasena("encoded");
		usuario.setRoles(Set.of(Roles.ROLE_MANAGER));

		when(loginAttemptService.isBlocked("manager@example.com")).thenReturn(false);
		when(authenticationManager.authenticate(any()))
				.thenReturn(new UsernamePasswordAuthenticationToken(usuario, null, usuario.getAuthorities()));
		when(jwtService.generateToken(eq(usuario), any(Long.class))).thenReturn("jwt-token");

		JwtAuthenticationResponse response = authenticationService.signin(request);

		assertEquals("jwt-token", response.getToken());
		verify(alumnoRepository, never()).existsByEmailIgnoreCaseAndActivoTrue(any());
	}
}

