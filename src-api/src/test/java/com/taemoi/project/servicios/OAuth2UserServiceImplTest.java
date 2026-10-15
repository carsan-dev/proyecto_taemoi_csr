package com.taemoi.project.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

import com.taemoi.project.config.OAuth2AuthenticationSuccessHandler;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.AuthProvider;
import com.taemoi.project.entities.RolMembresia;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.JwtService;
import com.taemoi.project.services.MembresiaService;
import com.taemoi.project.services.VinculoFamiliaService;
import com.taemoi.project.services.impl.OAuth2UserServiceImpl;

/**
 * Login con Google: nunca autentica una cuenta cuyo email no sea el de Google, y exige
 * {@code email_verified}.
 */
class OAuth2UserServiceImplTest {

	private static final String EMAIL_X = "atacante@gmail.test";
	private static final String EMAIL_Y = "gestor@escuela.test";
	private static final String MENSAJE_SIN_ALUMNO = "No se encontró ningun alumno activo registrado con el email: "
			+ EMAIL_X + ". Por favor, contacte con el administrador para registrarse.";

	private final AlumnoRepository alumnos = mock(AlumnoRepository.class);
	private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
	private final MembresiaService membresias = mock(MembresiaService.class);
	private final VinculoFamiliaService vinculos = mock(VinculoFamiliaService.class);
	private OAuth2UserServiceImpl servicio;

	@BeforeEach
	void setup() {
		servicio = new OAuth2UserServiceImpl(alumnos, usuarios, NoOpPasswordEncoder.getInstance(), membresias,
				vinculos);
		when(usuarios.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());
		when(usuarios.save(any(Usuario.class))).thenAnswer(call -> {
			Usuario u = call.getArgument(0);
			if (u.getId() == null) {
				u.setId(99L);
			}
			return u;
		});
	}

	private static OAuth2User google(String email, Object emailVerified) {
		Map<String, Object> atributos = new HashMap<>();
		atributos.put("email", email);
		if (emailVerified != null) {
			atributos.put("email_verified", emailVerified);
		}
		OAuth2User google = mock(OAuth2User.class);
		when(google.getAttributes()).thenReturn(atributos);
		when(google.getAttribute(any())).thenAnswer(call -> atributos.get(call.<String>getArgument(0)));
		return google;
	}

	private static Alumno alumno(long id, String email) {
		Alumno a = new Alumno();
		a.setId(id);
		a.setEmail(email);
		a.setActivo(true);
		return a;
	}

	private static Usuario cuentaY(Alumno vinculado) {
		Usuario y = new Usuario();
		y.setId(7L);
		y.setEmail(EMAIL_Y);
		y.setAlumno(vinculado);
		vinculado.setUsuario(y);
		return y;
	}

	private void verificarSinEscrituras() {
		verify(usuarios, never()).save(any(Usuario.class));
		verify(alumnos, never()).save(any(Alumno.class));
		verify(membresias, never()).asegurarMembresia(anyLong(), any(), any());
		verify(vinculos, never()).vincularAlta(any(), any());
	}

	@Test
	void alumnoConEmailXVinculadoAOtraCuentaNoAutenticaEsaCuenta() {
		Alumno alumnoX = alumno(1L, EMAIL_X);
		Usuario y = cuentaY(alumnoX);
		when(alumnos.findAllByEmailIgnoreCaseAndActivoTrue(EMAIL_X)).thenReturn(List.of(alumnoX));

		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
				() -> servicio.processOAuth2Login(google(EMAIL_X, true), "google"));

		assertEquals(MENSAJE_SIN_ALUMNO, e.getMessage());
		assertSame(y, alumnoX.getUsuario());
		verificarSinEscrituras();
	}

	@Test
	void primerAlumnoVinculadoAOtraCuentaYSegundoLibreCreaLaCuentaXConElSegundo() {
		Alumno vinculado = alumno(1L, EMAIL_X);
		Usuario y = cuentaY(vinculado);
		Alumno libre = alumno(2L, EMAIL_X);
		when(alumnos.findAllByEmailIgnoreCaseAndActivoTrue(EMAIL_X)).thenReturn(List.of(vinculado, libre));

		Usuario resultado = servicio.processOAuth2Login(google(EMAIL_X, true), "google");

		assertNotSame(y, resultado);
		assertEquals(EMAIL_X, resultado.getEmail());
		assertSame(libre, resultado.getAlumno());
		assertSame(resultado, libre.getUsuario());
		assertSame(y, vinculado.getUsuario());
		verify(alumnos).save(libre);
		verify(alumnos, never()).save(vinculado);
	}

	@Test
	void alumnoConEmailXSinCuentaCreaYVinculaLaCuentaX() {
		Alumno alumnoX = alumno(1L, EMAIL_X);
		when(alumnos.findAllByEmailIgnoreCaseAndActivoTrue(EMAIL_X)).thenReturn(List.of(alumnoX));

		Usuario resultado = servicio.processOAuth2Login(google(EMAIL_X, true), "google");

		assertNotNull(resultado);
		assertEquals(EMAIL_X, resultado.getEmail());
		assertEquals(AuthProvider.GOOGLE, resultado.getAuthProvider());
		assertSame(alumnoX, resultado.getAlumno());
		assertSame(resultado, alumnoX.getUsuario());
		verify(usuarios).save(resultado);
		verify(alumnos).save(alumnoX);
		verify(membresias).asegurarMembresia(eq(99L), any(), eq(RolMembresia.FAMILIA));
	}

	@Test
	void sinAlumnosConEmailXSeRechazaConElMensajeDeSiempre() {
		when(alumnos.findAllByEmailIgnoreCaseAndActivoTrue(EMAIL_X)).thenReturn(List.of());

		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
				() -> servicio.processOAuth2Login(google(EMAIL_X, true), "google"));

		assertEquals(MENSAJE_SIN_ALUMNO, e.getMessage());
		verificarSinEscrituras();
	}

	@Test
	void cuentaExistenteConEmailXEntra() {
		Usuario x = new Usuario();
		x.setId(3L);
		x.setEmail(EMAIL_X);
		x.setAuthProvider(AuthProvider.GOOGLE);
		when(usuarios.findByEmailIgnoreCase(EMAIL_X)).thenReturn(Optional.of(x));
		when(membresias.elegirOrganizacionParaLogin(x)).thenReturn(Optional.of(1L));

		assertSame(x, servicio.processOAuth2Login(google(EMAIL_X, true), "google"));
		verify(alumnos, never()).findAllByEmailIgnoreCaseAndActivoTrue(any());
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = { "false", "" })
	void emailNoVerificadoSeRechazaSinCrearNada(String emailVerified) {
		when(alumnos.findAllByEmailIgnoreCaseAndActivoTrue(EMAIL_X)).thenReturn(List.of(alumno(1L, EMAIL_X)));

		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
				() -> servicio.processOAuth2Login(google(EMAIL_X, emailVerified), "google"));

		assertEquals(MENSAJE_SIN_ALUMNO, e.getMessage());
		verificarSinEscrituras();
	}

	@Test
	void emailVerifiedFalseBooleanoSeRechazaAunqueExistaLaCuentaX() {
		Usuario x = new Usuario();
		x.setId(3L);
		x.setEmail(EMAIL_X);
		when(usuarios.findByEmailIgnoreCase(EMAIL_X)).thenReturn(Optional.of(x));
		when(membresias.elegirOrganizacionParaLogin(x)).thenReturn(Optional.of(1L));

		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
				() -> servicio.processOAuth2Login(google(EMAIL_X, Boolean.FALSE), "google"));

		assertEquals(MENSAJE_SIN_ALUMNO, e.getMessage());
		verificarSinEscrituras();
	}

	// ------------------------------------------------------------------ entrega 2b: usuario_alumno

	@Test
	void cuentaNuevaVinculaEnUsuarioAlumnoTodosLosAlumnosLibresDelEmail() {
		Alumno primero = alumno(1L, EMAIL_X);
		Alumno hermano = alumno(2L, EMAIL_X);
		when(alumnos.findAllByEmailIgnoreCaseAndActivoTrue(EMAIL_X)).thenReturn(List.of(primero, hermano));

		Usuario resultado = servicio.processOAuth2Login(google(EMAIL_X, true), "google");

		assertSame(primero, resultado.getAlumno(), "usuario.alumno_id sigue siendo el primer alumno libre");
		verify(vinculos).vincularAlta(resultado, primero);
		verify(vinculos).vincularAlta(resultado, hermano);
		assertEquals(null, hermano.getUsuario(), "solo un alumno ocupa usuario.alumno_id (UNIQUE)");
		verify(alumnos, never()).save(hermano);
	}

	@Test
	void alumnoDelEmailConOtraCuentaNoSeVinculaEnUsuarioAlumno() {
		// Opción conservadora (decisión pendiente del propietario): el alumno con email X que ya tiene la cuenta
		// Y no se vincula a la cuenta nueva X.
		Alumno vinculado = alumno(1L, EMAIL_X);
		cuentaY(vinculado);
		Alumno libre = alumno(2L, EMAIL_X);
		when(alumnos.findAllByEmailIgnoreCaseAndActivoTrue(EMAIL_X)).thenReturn(List.of(vinculado, libre));

		Usuario resultado = servicio.processOAuth2Login(google(EMAIL_X, true), "google");

		verify(vinculos).vincularAlta(resultado, libre);
		verify(vinculos, never()).vincularAlta(any(), eq(vinculado));
	}

	@Test
	void elVinculoSeEscribeDespuesDeLaMembresiaDeFamilia() {
		Alumno alumnoX = alumno(1L, EMAIL_X);
		when(alumnos.findAllByEmailIgnoreCaseAndActivoTrue(EMAIL_X)).thenReturn(List.of(alumnoX));

		Usuario resultado = servicio.processOAuth2Login(google(EMAIL_X, true), "google");

		InOrder orden = inOrder(usuarios, membresias, vinculos);
		orden.verify(usuarios).save(resultado);
		orden.verify(membresias).asegurarMembresia(eq(99L), any(), eq(RolMembresia.FAMILIA));
		orden.verify(vinculos).vincularAlta(resultado, alumnoX);
	}

	@Test
	void tambienSeVinculanLosAlumnosInactivosSinCuentaPeroNuncaOcupanAlumnoId() {
		Alumno activo = alumno(1L, EMAIL_X);
		Alumno inactivoLibre = alumno(2L, EMAIL_X);
		inactivoLibre.setActivo(false);
		Alumno inactivoDeY = alumno(3L, EMAIL_X);
		inactivoDeY.setActivo(false);
		cuentaY(inactivoDeY);
		when(alumnos.findAllByEmailIgnoreCaseAndActivoTrue(EMAIL_X)).thenReturn(List.of(activo));
		when(alumnos.findAllByEmailIgnoreCase(EMAIL_X)).thenReturn(List.of(inactivoLibre, activo, inactivoDeY));

		Usuario resultado = servicio.processOAuth2Login(google(EMAIL_X, true), "google");

		assertSame(activo, resultado.getAlumno());
		verify(vinculos).vincularAlta(resultado, activo);
		verify(vinculos).vincularAlta(resultado, inactivoLibre);
		verify(vinculos, never()).vincularAlta(any(), eq(inactivoDeY));
		verify(alumnos, never()).save(inactivoLibre);
	}

	@Test
	void soloAlumnosInactivosLibresSigueRechazandose() {
		Alumno inactivoLibre = alumno(2L, EMAIL_X);
		inactivoLibre.setActivo(false);
		when(alumnos.findAllByEmailIgnoreCaseAndActivoTrue(EMAIL_X)).thenReturn(List.of());
		when(alumnos.findAllByEmailIgnoreCase(EMAIL_X)).thenReturn(List.of(inactivoLibre));

		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
				() -> servicio.processOAuth2Login(google(EMAIL_X, true), "google"));

		assertEquals(MENSAJE_SIN_ALUMNO, e.getMessage());
		verificarSinEscrituras();
	}

	@Test
	void cuentaExistenteNoEscribeVinculos() {
		Usuario x = new Usuario();
		x.setId(3L);
		x.setEmail(EMAIL_X);
		x.setAuthProvider(AuthProvider.GOOGLE);
		when(usuarios.findByEmailIgnoreCase(EMAIL_X)).thenReturn(Optional.of(x));
		when(membresias.elegirOrganizacionParaLogin(x)).thenReturn(Optional.of(1L));

		servicio.processOAuth2Login(google(EMAIL_X, true), "google");

		verify(vinculos, never()).vincularAlta(any(), any());
	}

	@Test
	void successHandlerNuncaEmiteElJwtDeLaCuentaAjena() throws Exception {
		Alumno alumnoX = alumno(1L, EMAIL_X);
		Usuario y = cuentaY(alumnoX);
		when(alumnos.findAllByEmailIgnoreCaseAndActivoTrue(EMAIL_X)).thenReturn(List.of(alumnoX));
		when(membresias.elegirOrganizacionParaLogin(any())).thenReturn(Optional.of(1L));
		JwtService jwt = mock(JwtService.class);
		when(jwt.generateToken(any(UserDetails.class), anyLong(), any())).thenReturn("token");
		OAuth2AuthenticationSuccessHandler handler = new OAuth2AuthenticationSuccessHandler(servicio, jwt, membresias);
		ReflectionTestUtils.setField(handler, "frontendUrl", "https://front.test");
		Authentication auth = mock(Authentication.class);
		OAuth2User google = google(EMAIL_X, true);
		when(auth.getPrincipal()).thenReturn(google);
		MockHttpServletResponse response = new MockHttpServletResponse();

		handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, auth);

		ArgumentCaptor<UserDetails> emitido = ArgumentCaptor.forClass(UserDetails.class);
		verify(jwt, never()).generateToken(emitido.capture(), anyLong(), any());
		verify(jwt, never()).generateToken(any(UserDetails.class));
		verify(jwt, never()).generateToken(any(UserDetails.class), anyLong());
		assertTrue(emitido.getAllValues().stream().noneMatch(u -> u == y));
		assertTrue(response.getRedirectedUrl().startsWith("https://front.test/login?error=no_alumno_found"),
				response.getRedirectedUrl());
		assertTrue(response.getHeaders("Set-Cookie").stream().noneMatch(c -> c.startsWith("jwt=token")));
	}
}
