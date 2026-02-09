package com.taemoi.project.servicios;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.services.impl.AlumnoAccessControlServiceImpl;

@ExtendWith(MockitoExtension.class)
class AlumnoAccessControlServiceImplTest {

	@Mock
	private AlumnoRepository alumnoRepository;

	@InjectMocks
	private AlumnoAccessControlServiceImpl alumnoAccessControlService;

	@Test
	void canAccessAlumno_adminAlwaysAllowed() {
		Authentication authentication = buildAuthentication("admin@taemoi.com", "ROLE_ADMIN");

		boolean allowed = alumnoAccessControlService.canAccessAlumno(99L, authentication);

		assertTrue(allowed);
		verifyNoInteractions(alumnoRepository);
	}

	@Test
	void canAccessAlumno_managerAlwaysAllowed() {
		Authentication authentication = buildAuthentication("manager@taemoi.com", "ROLE_MANAGER");

		boolean allowed = alumnoAccessControlService.canAccessAlumno(99L, authentication);

		assertTrue(allowed);
		verifyNoInteractions(alumnoRepository);
	}

	@Test
	void canAccessAlumno_userAllowedWhenAlumnoBelongsToEmail() {
		Authentication authentication = buildAuthentication("familia@taemoi.com", "ROLE_USER");
		when(alumnoRepository.existsByIdAndEmailIgnoreCase(7L, "familia@taemoi.com")).thenReturn(true);

		boolean allowed = alumnoAccessControlService.canAccessAlumno(7L, authentication);

		assertTrue(allowed);
	}

	@Test
	void canAccessAlumno_userDeniedWhenAlumnoDoesNotBelongToEmail() {
		Authentication authentication = buildAuthentication("familia@taemoi.com", "ROLE_USER");
		when(alumnoRepository.existsByIdAndEmailIgnoreCase(7L, "familia@taemoi.com")).thenReturn(false);

		boolean allowed = alumnoAccessControlService.canAccessAlumno(7L, authentication);

		assertFalse(allowed);
	}

	@Test
	void canAccessAlumno_deniedForAnonymousAuthentication() {
		Authentication anonymousAuthentication = new AnonymousAuthenticationToken(
				"anonymous",
				"anonymousUser",
				List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));

		boolean allowed = alumnoAccessControlService.canAccessAlumno(7L, anonymousAuthentication);

		assertFalse(allowed);
		verifyNoInteractions(alumnoRepository);
	}

	private Authentication buildAuthentication(String email, String... roles) {
		return new UsernamePasswordAuthenticationToken(
				email,
				"password",
				Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList());
	}
}
