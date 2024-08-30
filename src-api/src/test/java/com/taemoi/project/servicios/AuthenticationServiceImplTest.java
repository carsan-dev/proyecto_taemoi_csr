package com.taemoi.project.servicios;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.taemoi.project.dtos.request.LoginRequest;
import com.taemoi.project.dtos.request.RegistroRequest;
import com.taemoi.project.repositorios.UsuarioRepository;
import com.taemoi.project.servicios.impl.AuthenticationServiceImpl;

@SpringBootTest
public class AuthenticationServiceImplTest {

	@Mock
	private UsuarioRepository usuarioRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private JwtService jwtService;

	@Mock
	private AuthenticationManager authenticationManager;

	@InjectMocks
	private AuthenticationServiceImpl authenticationService;

    @BeforeEach
    public void setUp() {
		MockitoAnnotations.openMocks(this);
	}

	@Test
	public void testSignup_EmailYaExiste() {
		RegistroRequest request = new RegistroRequest("John", "Doe", "john@example.com", "password");

		when(usuarioRepository.existsByEmail(request.getEmail())).thenReturn(true);

		assertThrows(IllegalArgumentException.class, () -> authenticationService.signup(request));

		verify(usuarioRepository, never()).save(any());
	}

	@Test
	public void testSignin_CredencialesNoValidas() {
		LoginRequest request = new LoginRequest("john@example.com", "password");

		when(authenticationManager.authenticate(any())).thenThrow(new IllegalArgumentException());
		when(usuarioRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());

		assertThrows(IllegalArgumentException.class, () -> authenticationService.signin(request));
	}
}