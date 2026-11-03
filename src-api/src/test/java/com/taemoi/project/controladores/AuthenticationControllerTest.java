package com.taemoi.project.controladores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.taemoi.project.controllers.AuthenticationController;
import com.taemoi.project.dtos.request.RecordatorioRachaEmailRequest;
import com.taemoi.project.dtos.response.RecordatorioRachaEmailResponse;
import com.taemoi.project.dtos.response.UsuarioConAlumnoAsociadoDTO;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.Roles;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.AccesoFamiliaService;
import com.taemoi.project.services.AuthenticationService;
import com.taemoi.project.services.PasswordResetService;
import com.taemoi.project.services.RegistroService;
import com.taemoi.project.services.UsuarioService;
import com.taemoi.project.services.VinculoFamiliaService;

@ExtendWith(MockitoExtension.class)
class AuthenticationControllerTest {

	@Mock
	private AuthenticationService authenticationService;

	@Mock
	private UsuarioService usuarioService;

	@Mock
	private UsuarioRepository usuarioRepository;

	@Mock
	private AlumnoRepository alumnoRepository;

	@Mock
	private AccesoFamiliaService accesoFamilia;

	@Mock
	private VinculoFamiliaService vinculoFamilia;

	@Mock
	private PasswordResetService passwordResetService;

	@Mock
	private RegistroService registroService;

	@InjectMocks
	private AuthenticationController authenticationController;

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	// Decisión 431 (pieza E10-2): la ficha de alumnoDTO sale de usuario_alumno (fichas PROPIO), no de usuario.alumno_id.

	@Test
	void obtenerUsuarioAutenticado_familiaConFichaPropiaYTutorada_devuelveLaPropia() {
		String email = "familia@example.com";
		sesion(email, Roles.ROLE_USER);
		Alumno tutorada = crearAlumno(5L, "Ana", "Hija", true, email);
		Alumno propia = crearAlumno(9L, "Eva", "Madre", true, email);
		Usuario usuario = crearUsuario(99L, email);
		when(usuarioService.encontrarPorEmail(email)).thenReturn(Optional.of(usuario));
		when(vinculoFamilia.fichasPropiasDeLaCuenta(eq(99L), any())).thenReturn(List.of(9L));
		when(accesoFamilia.alumnosAccesiblesEnOrganizacionActiva(usuario)).thenReturn(List.of(tutorada, propia));

		UsuarioConAlumnoAsociadoDTO response = authenticationController.obtenerUsuarioAutenticado();

		assertNotNull(response.getAlumnoDTO());
		assertEquals(9L, response.getAlumnoDTO().getId());
	}

	@Test
	void obtenerUsuarioAutenticado_familiaSoloTutora_devuelveLaDeMenorId() {
		String email = "familia@example.com";
		sesion(email, Roles.ROLE_USER);
		Alumno primera = crearAlumno(5L, "Ana", "Hija", true, email);
		Alumno segunda = crearAlumno(8L, "Beto", "Hijo", true, email);
		Usuario usuario = crearUsuario(99L, email);
		when(usuarioService.encontrarPorEmail(email)).thenReturn(Optional.of(usuario));
		when(vinculoFamilia.fichasPropiasDeLaCuenta(eq(99L), any())).thenReturn(List.of());
		when(accesoFamilia.alumnosAccesiblesEnOrganizacionActiva(usuario)).thenReturn(List.of(primera, segunda));

		UsuarioConAlumnoAsociadoDTO response = authenticationController.obtenerUsuarioAutenticado();

		assertNotNull(response.getAlumnoDTO());
		assertEquals(5L, response.getAlumnoDTO().getId());
	}

	@Test
	void obtenerUsuarioAutenticado_familiaConFichaPropiaQueNoVe_devuelveLaQueVe() {
		String email = "familia@example.com";
		sesion(email, Roles.ROLE_USER);
		Alumno activo = crearAlumno(11L, "Diego", "Activo", true, email);
		Usuario usuario = crearUsuario(100L, email);
		when(usuarioService.encontrarPorEmail(email)).thenReturn(Optional.of(usuario));
		// La 10 es PROPIO pero inactiva: el acceso de familia no la da.
		when(vinculoFamilia.fichasPropiasDeLaCuenta(eq(100L), any())).thenReturn(List.of(10L));
		when(accesoFamilia.alumnosAccesiblesEnOrganizacionActiva(usuario)).thenReturn(List.of(activo));

		UsuarioConAlumnoAsociadoDTO response = authenticationController.obtenerUsuarioAutenticado();

		assertNotNull(response.getAlumnoDTO());
		assertEquals(11L, response.getAlumnoDTO().getId());
	}

	@Test
	void obtenerUsuarioAutenticado_familiaSinFichasVisibles_sinAlumno() {
		String email = "familia@example.com";
		sesion(email, Roles.ROLE_USER);
		Usuario usuario = crearUsuario(100L, email);
		when(usuarioService.encontrarPorEmail(email)).thenReturn(Optional.of(usuario));
		when(vinculoFamilia.fichasPropiasDeLaCuenta(eq(100L), any())).thenReturn(List.of(10L));
		when(accesoFamilia.alumnosAccesiblesEnOrganizacionActiva(usuario)).thenReturn(List.of());

		assertNull(authenticationController.obtenerUsuarioAutenticado().getAlumnoDTO());
	}

	@Test
	void obtenerUsuarioAutenticado_gestorConFichaPropiaActiva_laDevuelve() {
		String email = "gestor@example.com";
		sesion(email, Roles.ROLE_MANAGER);
		Usuario usuario = crearUsuario(200L, email);
		when(usuarioService.encontrarPorEmail(email)).thenReturn(Optional.of(usuario));
		when(vinculoFamilia.fichasPropiasDeLaCuenta(eq(200L), any())).thenReturn(List.of(30L));
		when(alumnoRepository.buscarPorIdEnOrganizacionActiva(30L))
				.thenReturn(Optional.of(crearAlumno(30L, "Gema", "Gestora", true, email)));

		UsuarioConAlumnoAsociadoDTO response = authenticationController.obtenerUsuarioAutenticado();

		assertNotNull(response.getAlumnoDTO());
		assertEquals(30L, response.getAlumnoDTO().getId());
		verifyNoInteractions(accesoFamilia);
	}

	@Test
	void obtenerUsuarioAutenticado_gestorConFichaPropiaInactiva_sinAlumno() {
		String email = "gestor@example.com";
		sesion(email, Roles.ROLE_ADMIN);
		Usuario usuario = crearUsuario(200L, email);
		when(usuarioService.encontrarPorEmail(email)).thenReturn(Optional.of(usuario));
		when(vinculoFamilia.fichasPropiasDeLaCuenta(eq(200L), any())).thenReturn(List.of(30L));
		when(alumnoRepository.buscarPorIdEnOrganizacionActiva(30L))
				.thenReturn(Optional.of(crearAlumno(30L, "Gema", "Gestora", false, email)));

		assertNull(authenticationController.obtenerUsuarioAutenticado().getAlumnoDTO());
	}

	@Test
	void obtenerUsuarioAutenticado_gestorConFichaPropiaDeOtraEscuela_sinAlumno() {
		String email = "gestor@example.com";
		sesion(email, Roles.ROLE_MANAGER);
		Usuario usuario = crearUsuario(200L, email);
		when(usuarioService.encontrarPorEmail(email)).thenReturn(Optional.of(usuario));
		when(vinculoFamilia.fichasPropiasDeLaCuenta(eq(200L), any())).thenReturn(List.of(30L));
		when(alumnoRepository.buscarPorIdEnOrganizacionActiva(30L)).thenReturn(Optional.empty());

		assertNull(authenticationController.obtenerUsuarioAutenticado().getAlumnoDTO());
	}

	@Test
	void obtenerUsuarioAutenticado_gestorSinFicha_sinAlumno() {
		String email = "gestor@example.com";
		sesion(email, Roles.ROLE_MANAGER);
		Usuario usuario = crearUsuario(200L, email);
		when(usuarioService.encontrarPorEmail(email)).thenReturn(Optional.of(usuario));
		when(vinculoFamilia.fichasPropiasDeLaCuenta(eq(200L), any())).thenReturn(List.of());

		assertNull(authenticationController.obtenerUsuarioAutenticado().getAlumnoDTO());
		verifyNoInteractions(alumnoRepository, accesoFamilia);
	}

	@Test
	void obtenerUsuarioAutenticado_profesor_sinAlumnoNiConsultas() {
		String email = "profe@example.com";
		sesion(email, Roles.ROLE_PROFESOR);
		Usuario usuario = crearUsuario(300L, email);
		when(usuarioService.encontrarPorEmail(email)).thenReturn(Optional.of(usuario));

		assertNull(authenticationController.obtenerUsuarioAutenticado().getAlumnoDTO());
		verifyNoInteractions(vinculoFamilia, alumnoRepository, accesoFamilia);
	}

	private static void sesion(String email, Roles rol) {
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(email, "token",
				List.of(new SimpleGrantedAuthority(rol.name()))));
	}

	@Test
	void obtenerRecordatorioRachaEmail_devuelvePreferenciaActual() {
		String email = "familia@example.com";
		SecurityContextHolder.getContext()
				.setAuthentication(new UsernamePasswordAuthenticationToken(email, "token",
						List.of(new SimpleGrantedAuthority(Roles.ROLE_USER.name()))));

		Usuario usuario = crearUsuario(101L, email);
		usuario.setRecordatorioRachaEmailHabilitado(true);
		when(usuarioService.encontrarPorEmail(email)).thenReturn(Optional.of(usuario));

		ResponseEntity<RecordatorioRachaEmailResponse> response = authenticationController.obtenerRecordatorioRachaEmail();

		assertNotNull(response.getBody());
		assertEquals(true, response.getBody().isHabilitado());
	}

	@Test
	void actualizarRecordatorioRachaEmail_actualizaPreferencia() {
		String email = "familia@example.com";
		SecurityContextHolder.getContext()
				.setAuthentication(new UsernamePasswordAuthenticationToken(email, "token",
						List.of(new SimpleGrantedAuthority(Roles.ROLE_USER.name()))));

		Usuario usuario = crearUsuario(102L, email);
		usuario.setRecordatorioRachaEmailHabilitado(false);
		when(usuarioService.encontrarPorEmail(email)).thenReturn(Optional.of(usuario));
		when(usuarioService.actualizarUsuario(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

		RecordatorioRachaEmailRequest request = new RecordatorioRachaEmailRequest();
		request.setHabilitado(true);

		ResponseEntity<RecordatorioRachaEmailResponse> response = authenticationController
				.actualizarRecordatorioRachaEmail(request);

		assertNotNull(response.getBody());
		assertEquals(true, response.getBody().isHabilitado());
		assertEquals(true, usuario.getRecordatorioRachaEmailHabilitado());
	}

	private Usuario crearUsuario(Long id, String email) {
		Usuario usuario = new Usuario();
		usuario.setId(id);
		usuario.setEmail(email);
		return usuario;
	}

	private Alumno crearAlumno(Long id, String nombre, String apellidos, boolean activo, String email) {
		Alumno alumno = new Alumno();
		alumno.setId(id);
		alumno.setNombre(nombre);
		alumno.setApellidos(apellidos);
		alumno.setActivo(activo);
		alumno.setEmail(email);
		return alumno;
	}
}
