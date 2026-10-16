package com.taemoi.project.servicios;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.AccesoFamiliaService;
import com.taemoi.project.services.impl.AlumnoAccessControlServiceImpl;

/**
 * Gestión ve todo sin consultar; la familia pregunta a la regla única ({@link AccesoFamiliaService}) en la
 * escuela de la sesión ({@code TenantContext}).
 */
@ExtendWith(MockitoExtension.class)
class AlumnoAccessControlServiceImplTest {

	@Mock
	private AccesoFamiliaService accesoFamilia;

	@Mock
	private UsuarioRepository usuarios;

	@InjectMocks
	private AlumnoAccessControlServiceImpl alumnoAccessControlService;

	@BeforeEach
	void escuelaDeLaSesion() {
		TenantContext.set(1L);
	}

	@AfterEach
	void limpiar() {
		TenantContext.clear();
	}

	@Test
	void canAccessAlumno_adminAlwaysAllowed() {
		Authentication authentication = buildAuthentication("admin@taemoi.com", "ROLE_ADMIN");

		assertTrue(alumnoAccessControlService.canAccessAlumno(99L, authentication));
		verifyNoInteractions(accesoFamilia, usuarios);
	}

	@Test
	void canAccessAlumno_managerAlwaysAllowed() {
		Authentication authentication = buildAuthentication("manager@taemoi.com", "ROLE_MANAGER");

		assertTrue(alumnoAccessControlService.canAccessAlumno(99L, authentication));
		verifyNoInteractions(accesoFamilia, usuarios);
	}

	@Test
	void canAccessAlumno_familiaDeLaSesion_preguntaALaReglaUnica() {
		Usuario cuenta = cuenta(5L, "familia@taemoi.com");
		Authentication authentication = new UsernamePasswordAuthenticationToken(cuenta, null,
				List.of(new SimpleGrantedAuthority("ROLE_USER")));
		when(accesoFamilia.puedeAcceder(cuenta, 1L, 7L)).thenReturn(true);
		when(accesoFamilia.puedeAcceder(cuenta, 1L, 8L)).thenReturn(false);

		assertTrue(alumnoAccessControlService.canAccessAlumno(7L, authentication));
		assertFalse(alumnoAccessControlService.canAccessAlumno(8L, authentication));
		verifyNoInteractions(usuarios);
	}

	@Test
	void canAccessAlumno_familiaConOtraAutenticacion_seBuscaLaCuentaPorSuEmail() {
		Usuario cuenta = cuenta(6L, "familia@taemoi.com");
		when(usuarios.findByEmailIgnoreCase("familia@taemoi.com")).thenReturn(Optional.of(cuenta));
		when(accesoFamilia.puedeAcceder(cuenta, 1L, 7L)).thenReturn(true);

		assertTrue(alumnoAccessControlService.canAccessAlumno(7L,
				buildAuthentication(" Familia@Taemoi.com ", "ROLE_USER")));
	}

	@Test
	void canAccessAlumno_familiaSinCuentaOSinEscuelaActiva_denegado() {
		when(usuarios.findByEmailIgnoreCase("nadie@taemoi.com")).thenReturn(Optional.empty());
		assertFalse(alumnoAccessControlService.canAccessAlumno(7L, buildAuthentication("nadie@taemoi.com", "ROLE_USER")));
		assertFalse(alumnoAccessControlService.canAccessAlumno(7L, buildAuthentication("   ", "ROLE_USER")));

		TenantContext.clear();
		assertFalse(alumnoAccessControlService.canAccessAlumno(7L, buildAuthentication("familia@taemoi.com", "ROLE_USER")));
		verifyNoInteractions(accesoFamilia);
	}

	@Test
	void canAccessAlumno_sinRolDeFamiliaNiGestion_denegado() {
		assertFalse(alumnoAccessControlService.canAccessAlumno(7L, buildAuthentication("x@taemoi.com", "ROLE_OTRO")));
		verifyNoInteractions(accesoFamilia, usuarios);
	}

	@Test
	void canAccessAlumno_deniedForAnonymousAuthentication() {
		Authentication anonymousAuthentication = new AnonymousAuthenticationToken(
				"anonymous",
				"anonymousUser",
				List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));

		assertFalse(alumnoAccessControlService.canAccessAlumno(7L, anonymousAuthentication));
		verifyNoInteractions(accesoFamilia, usuarios);
	}

	private static Usuario cuenta(Long id, String email) {
		Usuario usuario = new Usuario();
		usuario.setId(id);
		usuario.setEmail(email);
		return usuario;
	}

	private Authentication buildAuthentication(String email, String... roles) {
		return new UsernamePasswordAuthenticationToken(
				email,
				"password",
				Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList());
	}
}
