package com.taemoi.project.organizacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.AccesoFamiliaService;
import com.taemoi.project.services.JwtService;
import com.taemoi.project.services.MembresiaService;
import com.taemoi.project.services.TokenJwt;

import jakarta.servlet.http.Cookie;

/**
 * Comprobación de alumno accesible al validar el JWT de una cuenta solo familia, en CADA petición: la decide
 * {@link AccesoFamiliaService} (regla única; su email normalizado y el guard de email en blanco se prueban en
 * {@code AccesoFamiliaServiceImplTest} y contra MySQL en {@code AccesoFamiliaEquivalenciaIntegrationTest}). Los
 * roles de gestión no necesitan alumno; RESPONSABLE_SEDE y PROFESOR solos no abren sesión, y con FAMILIA entran
 * como familia (entrega 5b, D5-1 y D5-2).
 *
 * <p>La cuenta se busca por el email del token, así que por HTTP su email nunca llega en blanco ni con
 * espacios a esta comprobación: se simulan datos inconsistentes escribiendo el campo directamente.
 */
class IdentificadorSesionJwtTest {

	private static final String EMAIL_TOKEN = "familia@example.test";

	private final JwtService jwtService = mock(JwtService.class);
	private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
	private final MembresiaService membresias = mock(MembresiaService.class);
	private final AccesoFamiliaService acceso = mock(AccesoFamiliaService.class);
	private final IdentificadorSesionJwt identificador = new IdentificadorSesionJwt(jwtService, usuarios,
			membresias, acceso, new ResolutorOrganizacion(1L));

	@Test
	void familiaSinAlumnoAccesibleSeRechaza() {
		Usuario cuenta = cuentaCon(EMAIL_TOKEN, Roles.ROLE_USER);
		when(acceso.tieneAlgunoAccesible(cuenta, 1L)).thenReturn(false);

		assertTrue(identificador.identificar(peticion()).isEmpty());
		verify(acceso).tieneAlgunoAccesible(cuenta, 1L);
	}

	@Test
	void familiaConAlumnoAccesibleSigueEntrando() {
		Usuario cuenta = cuentaCon(EMAIL_TOKEN, Roles.ROLE_USER);
		when(acceso.tieneAlgunoAccesible(cuenta, 1L)).thenReturn(true);

		assertTrue(identificador.identificar(peticion()).isPresent());
	}

	@Test
	void familiaConEmailGuardadoDistintoSePreguntaConLaCuentaYLaEscuelaDelToken() {
		// Email guardado con espacios y mayúsculas: la normalización es del servicio central.
		Usuario cuenta = cuentaCon(" Familia@Example.test ", Roles.ROLE_USER);
		when(acceso.tieneAlgunoAccesible(cuenta, 1L)).thenReturn(true);

		assertTrue(identificador.identificar(peticion()).isPresent());
	}

	@Test
	void gestionConEmailDeCuentaEnBlancoSigueEntrandoSinPreguntarPorAlumnos() {
		for (Roles rol : new Roles[] { Roles.ROLE_ADMIN, Roles.ROLE_MANAGER }) {
			cuentaCon("", rol);
			assertTrue(identificador.identificar(peticion()).isPresent(), rol.name());
		}
		verify(acceso, never()).tieneAlgunoAccesible(any(), anyLong());
	}

	// ------------------------------------------------------------------ roles sin acceso todavía (entrega 5b)

	@Test
	void soloProfesorOResponsableNoAbreSesion() {
		cuentaConAutoridades(Set.of(Roles.ROLE_PROFESOR));
		assertTrue(identificador.identificar(peticion()).isEmpty(), "solo PROFESOR");

		cuentaConAutoridades(Set.of(Roles.ROLE_RESPONSABLE_SEDE, Roles.ROLE_PROFESOR));
		assertTrue(identificador.identificar(peticion()).isEmpty(), "RESPONSABLE_SEDE + PROFESOR");
		verify(acceso, never()).tieneAlgunoAccesible(any(), anyLong());
	}

	@Test
	void profesorYFamiliaExigeAlumnoAccesible() {
		Usuario cuenta = cuentaConAutoridades(Set.of(Roles.ROLE_PROFESOR, Roles.ROLE_USER));

		when(acceso.tieneAlgunoAccesible(cuenta, 1L)).thenReturn(false);
		assertTrue(identificador.identificar(peticion()).isEmpty());

		when(acceso.tieneAlgunoAccesible(cuenta, 1L)).thenReturn(true);
		assertEquals(Set.of(Roles.ROLE_PROFESOR, Roles.ROLE_USER),
				identificador.identificar(peticion()).orElseThrow().autoridades());
	}

	@Test
	void profesorYAdministradorConservaSusDosAutoridades() {
		cuentaConAutoridades(Set.of(Roles.ROLE_MANAGER, Roles.ROLE_PROFESOR));

		SesionVerificada sesion = identificador.identificar(peticion()).orElseThrow();

		assertEquals(Set.of(Roles.ROLE_MANAGER, Roles.ROLE_PROFESOR), sesion.autoridades());
		verify(acceso, never()).tieneAlgunoAccesible(any(), anyLong());
	}

	@Test
	void plataformaSoloProfesorEnLaEscuelaPorDefectoQuedaSoloConLaAutoridadDePlataforma() {
		marcarPlataforma(cuentaConAutoridades(Set.of(Roles.ROLE_PROFESOR)));

		SesionVerificada sesion = identificador.identificar(peticion()).orElseThrow();

		assertEquals(Set.of(), sesion.autoridades());
		assertTrue(sesion.adminPlataforma());
	}

	// ------------------------------------------------------------------ administrador de plataforma

	@Test
	void cuentaSinMarcaDePlataformaNoRecibeLaAutoridad() {
		cuentaCon(EMAIL_TOKEN, Roles.ROLE_ADMIN);

		SesionVerificada sesion = identificador.identificar(peticion()).orElseThrow();

		assertEquals(Set.of(Roles.ROLE_ADMIN), sesion.autoridades());
		assertFalse(sesion.adminPlataforma());
	}

	@Test
	void plataformaConMembresiaSumaLaAutoridadALasDeSuEscuela() {
		Usuario cuenta = cuentaCon(EMAIL_TOKEN, Roles.ROLE_ADMIN);
		marcarPlataforma(cuenta);

		SesionVerificada sesion = identificador.identificar(peticion()).orElseThrow();

		assertEquals(1L, sesion.organizacionId());
		assertEquals(Set.of(Roles.ROLE_ADMIN), sesion.autoridades());
		assertTrue(sesion.adminPlataforma());
	}

	@Test
	void plataformaSinMembresiaEntraSoloEnLaEscuelaPorDefectoYSinAutoridadesDeEscuela() {
		Usuario cuenta = cuentaSin(1L);
		marcarPlataforma(cuenta);

		SesionVerificada sesion = identificador.identificar(peticion()).orElseThrow();

		assertEquals(1L, sesion.organizacionId());
		assertEquals(Set.of(), sesion.autoridades());
		assertTrue(sesion.adminPlataforma());
	}

	@Test
	void plataformaSinMembresiaEnOtraEscuelaSeRechaza() {
		Usuario cuenta = cuentaSin(2L);
		marcarPlataforma(cuenta);

		assertTrue(identificador.identificar(peticion()).isEmpty());
	}

	@Test
	void plataformaSoloFamiliaSinAlumnoAccesibleQuedaSoloConLaAutoridadDePlataforma() {
		Usuario cuenta = cuentaCon(EMAIL_TOKEN, Roles.ROLE_USER);
		marcarPlataforma(cuenta);
		when(acceso.tieneAlgunoAccesible(cuenta, 1L)).thenReturn(false);

		SesionVerificada sesion = identificador.identificar(peticion()).orElseThrow();

		assertEquals(Set.of(), sesion.autoridades());
		assertTrue(sesion.adminPlataforma());
	}

	@Test
	void sinMarcaYSinMembresiaSeRechazaComoAntes() {
		cuentaSin(1L);

		assertTrue(identificador.identificar(peticion()).isEmpty());
	}

	private static void marcarPlataforma(Usuario cuenta) {
		ReflectionTestUtils.setField(cuenta, "esAdminPlataforma", Boolean.TRUE);
	}

	private Usuario cuentaSin(long organizacionToken) {
		when(jwtService.verificar("tok")).thenReturn(new TokenJwt(EMAIL_TOKEN, organizacionToken, new Date(),
				new Date(System.currentTimeMillis() + 60_000)));
		Usuario cuenta = new Usuario();
		cuenta.setId(5L);
		ReflectionTestUtils.setField(cuenta, "email", EMAIL_TOKEN);
		when(usuarios.findByEmailIgnoreCase(EMAIL_TOKEN)).thenReturn(Optional.of(cuenta));
		when(membresias.autoridadesEn(5L, organizacionToken)).thenReturn(Set.of());
		return cuenta;
	}

	private Usuario cuentaCon(String emailGuardado, Roles rol) {
		return cuentaCon(emailGuardado, Set.of(rol));
	}

	private Usuario cuentaConAutoridades(Set<Roles> autoridades) {
		return cuentaCon(EMAIL_TOKEN, autoridades);
	}

	private Usuario cuentaCon(String emailGuardado, Set<Roles> autoridades) {
		when(jwtService.verificar("tok")).thenReturn(new TokenJwt(EMAIL_TOKEN, 1L, new Date(),
				new Date(System.currentTimeMillis() + 60_000)));
		Usuario cuenta = new Usuario();
		cuenta.setId(5L);
		ReflectionTestUtils.setField(cuenta, "email", emailGuardado);
		when(usuarios.findByEmailIgnoreCase(EMAIL_TOKEN)).thenReturn(Optional.of(cuenta));
		when(membresias.autoridadesEn(5L, 1L)).thenReturn(autoridades);
		return cuenta;
	}

	private static MockHttpServletRequest peticion() {
		MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/api/auth/user/alumnos");
		peticion.setCookies(new Cookie("jwt", "tok"));
		return peticion;
	}
}
