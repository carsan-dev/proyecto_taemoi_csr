package com.taemoi.project.servicios;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import com.taemoi.project.entities.CustomUserDetails;
import com.taemoi.project.services.impl.JwtServiceImpl;


public class JwtServiceImplTest {

	private JwtServiceImpl jwtService;

	@BeforeEach
	void setUp() {
		jwtService = new JwtServiceImpl();
		String secret = Base64.getEncoder()
				.encodeToString("01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8));
		ReflectionTestUtils.setField(jwtService, "jwtSigningKey", secret);
	}

	@Test
	public void testGenerarToken() {
		UserDetails userDetails = new CustomUserDetails("John", "password");
		String token = jwtService.generateToken(userDetails);
		assertNotNull(token);
	}

	@Test
	public void testEsValidoToken_TokenValido() {
		UserDetails userDetails = new CustomUserDetails("John", "password");
		String token = jwtService.generateToken(userDetails);

		boolean isValid = jwtService.isTokenValid(token, userDetails);
		assertTrue(isValid);
	}

	@Test
	public void testIsTokenValid_UsuarioIncorrecto() {
		UserDetails userDetails = new CustomUserDetails("John", "password");
		String token = jwtService.generateToken(userDetails);

		UserDetails invalidUserDetails = new CustomUserDetails("Alice", "password");

		boolean isValid = jwtService.isTokenValid(token, invalidUserDetails);
		assertFalse(isValid);
	}
}
