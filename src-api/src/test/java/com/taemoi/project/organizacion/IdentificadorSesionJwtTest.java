package com.taemoi.project.organizacion;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import com.taemoi.project.entities.Roles;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.JwtService;
import com.taemoi.project.services.MembresiaService;
import com.taemoi.project.services.TokenJwt;

import jakarta.servlet.http.Cookie;

/**
 * Comprobación de alumno activo al validar el JWT de una familia: un email de cuenta en blanco nunca
 * cuenta como coincidencia (aunque haya alumnos activos con el email vacío).
 */
class IdentificadorSesionJwtTest {

	private final JwtService jwtService = mock(JwtService.class);
	private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
	private final MembresiaService membresias = mock(MembresiaService.class);
	private final AlumnoRepository alumnos = mock(AlumnoRepository.class);
	private final IdentificadorSesionJwt identificador = new IdentificadorSesionJwt(jwtService, usuarios,
			membresias, alumnos);

	@Test
	void familiaConEmailDeCuentaEnBlancoSeRechazaAunqueHayaAlumnosConEmailVacio() {
		// Datos inconsistentes: el token trae un email, pero la cuenta encontrada lo tiene vacío.
		when(jwtService.verificar("tok")).thenReturn(new TokenJwt("familia@example.test", 1L, new Date(),
				new Date(System.currentTimeMillis() + 60_000)));
		Usuario cuenta = new Usuario();
		cuenta.setId(5L);
		cuenta.setEmail("");
		when(usuarios.findByEmailIgnoreCase("familia@example.test")).thenReturn(Optional.of(cuenta));
		when(membresias.autoridadesEn(5L, 1L)).thenReturn(Set.of(Roles.ROLE_USER));
		when(alumnos.contarActivosPorEmailEnOrganizacion(any(), anyLong())).thenReturn(1L);

		MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/api/auth/user/alumnos");
		peticion.setCookies(new Cookie("jwt", "tok"));

		assertTrue(identificador.identificar(peticion).isEmpty());
		verify(alumnos, never()).contarActivosPorEmailEnOrganizacion(any(), anyLong());
	}

	@Test
	void familiaConAlumnoActivoDeSuEmailSigueEntrando() {
		when(jwtService.verificar("tok")).thenReturn(new TokenJwt("familia@example.test", 1L, new Date(),
				new Date(System.currentTimeMillis() + 60_000)));
		Usuario cuenta = new Usuario();
		cuenta.setId(5L);
		cuenta.setEmail("familia@example.test");
		when(usuarios.findByEmailIgnoreCase("familia@example.test")).thenReturn(Optional.of(cuenta));
		when(membresias.autoridadesEn(5L, 1L)).thenReturn(Set.of(Roles.ROLE_USER));
		when(alumnos.contarActivosPorEmailEnOrganizacion("familia@example.test", 1L)).thenReturn(1L);

		MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/api/auth/user/alumnos");
		peticion.setCookies(new Cookie("jwt", "tok"));

		assertTrue(identificador.identificar(peticion).isPresent());
	}
}
