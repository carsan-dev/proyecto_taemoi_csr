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
import org.springframework.test.util.ReflectionTestUtils;

import com.taemoi.project.entities.Roles;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.JwtService;
import com.taemoi.project.services.MembresiaService;
import com.taemoi.project.services.TokenJwt;

import jakarta.servlet.http.Cookie;

/**
 * Comprobación de alumno activo al validar el JWT de una familia: se compara con el email normalizado de
 * la cuenta, y un email en blanco nunca cuenta como coincidencia (aunque haya alumnos activos con el email
 * vacío). Los roles de gestión no necesitan alumno.
 *
 * <p>La cuenta se busca por el email del token, así que por HTTP su email nunca llega en blanco ni con
 * espacios a esta comprobación: se simulan datos inconsistentes escribiendo el campo directamente.
 */
class IdentificadorSesionJwtTest {

	private static final String EMAIL_TOKEN = "familia@example.test";

	private final JwtService jwtService = mock(JwtService.class);
	private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
	private final MembresiaService membresias = mock(MembresiaService.class);
	private final AlumnoRepository alumnos = mock(AlumnoRepository.class);
	private final IdentificadorSesionJwt identificador = new IdentificadorSesionJwt(jwtService, usuarios,
			membresias, alumnos);

	@Test
	void familiaConEmailDeCuentaEnBlancoSeRechazaAunqueHayaAlumnosConEmailVacio() {
		cuentaCon("", Roles.ROLE_USER);
		when(alumnos.contarActivosPorEmailEnOrganizacion(any(), anyLong())).thenReturn(1L);

		assertTrue(identificador.identificar(peticion()).isEmpty());
		verify(alumnos, never()).contarActivosPorEmailEnOrganizacion(any(), anyLong());
	}

	@Test
	void familiaConAlumnoActivoDeSuEmailSigueEntrando() {
		cuentaCon(EMAIL_TOKEN, Roles.ROLE_USER);
		when(alumnos.contarActivosPorEmailEnOrganizacion(EMAIL_TOKEN, 1L)).thenReturn(1L);

		assertTrue(identificador.identificar(peticion()).isPresent());
	}

	@Test
	void familiaCoincideConSusAlumnosPorElEmailNormalizado() {
		cuentaCon(" Familia@Example.test ", Roles.ROLE_USER);
		when(alumnos.contarActivosPorEmailEnOrganizacion(EMAIL_TOKEN, 1L)).thenReturn(1L);

		assertTrue(identificador.identificar(peticion()).isPresent());
	}

	@Test
	void gestionConEmailDeCuentaEnBlancoSigueEntrando() {
		for (Roles rol : new Roles[] { Roles.ROLE_ADMIN, Roles.ROLE_MANAGER }) {
			cuentaCon("", rol);
			assertTrue(identificador.identificar(peticion()).isPresent(), rol.name());
		}
		verify(alumnos, never()).contarActivosPorEmailEnOrganizacion(any(), anyLong());
	}

	private void cuentaCon(String emailGuardado, Roles rol) {
		when(jwtService.verificar("tok")).thenReturn(new TokenJwt(EMAIL_TOKEN, 1L, new Date(),
				new Date(System.currentTimeMillis() + 60_000)));
		Usuario cuenta = new Usuario();
		cuenta.setId(5L);
		ReflectionTestUtils.setField(cuenta, "email", emailGuardado);
		when(usuarios.findByEmailIgnoreCase(EMAIL_TOKEN)).thenReturn(Optional.of(cuenta));
		when(membresias.autoridadesEn(5L, 1L)).thenReturn(Set.of(rol));
	}

	private static MockHttpServletRequest peticion() {
		MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/api/auth/user/alumnos");
		peticion.setCookies(new Cookie("jwt", "tok"));
		return peticion;
	}
}
