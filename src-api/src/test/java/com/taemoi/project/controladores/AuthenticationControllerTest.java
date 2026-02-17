package com.taemoi.project.controladores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
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
import com.taemoi.project.services.AuthenticationService;
import com.taemoi.project.services.PasswordResetService;
import com.taemoi.project.services.RegistroService;
import com.taemoi.project.services.UsuarioService;

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
	private PasswordResetService passwordResetService;

	@Mock
	private RegistroService registroService;

	@InjectMocks
	private AuthenticationController authenticationController;

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void obtenerUsuarioAutenticado_roleUserConAlumnoAsociadoActivo_loMantiene() {
		String email = "familia@example.com";
		SecurityContextHolder.getContext()
				.setAuthentication(new UsernamePasswordAuthenticationToken(email, "token"));

		Alumno asociadoActivo = crearAlumno(7L, "Ana", "Activa", true, email);
		Alumno otroActivo = crearAlumno(8L, "Beto", "Activo", true, email);
		Usuario usuario = crearUsuario(99L, email, Set.of(Roles.ROLE_USER), asociadoActivo);

		when(usuarioService.encontrarPorEmail(email)).thenReturn(Optional.of(usuario));
		when(alumnoRepository.findAllByEmailIgnoreCaseAndActivoTrue(email))
				.thenReturn(List.of(otroActivo, asociadoActivo));

		UsuarioConAlumnoAsociadoDTO response = authenticationController.obtenerUsuarioAutenticado();

		assertNotNull(response);
		assertNotNull(response.getAlumnoDTO());
		assertEquals(7L, response.getAlumnoDTO().getId());
	}

	@Test
	void obtenerUsuarioAutenticado_roleUserConAsociadoInactivo_devuelveAlumnoActivoPorEmail() {
		String email = "familia@example.com";
		SecurityContextHolder.getContext()
				.setAuthentication(new UsernamePasswordAuthenticationToken(email, "token"));

		Alumno asociadoInactivo = crearAlumno(10L, "Clara", "Inactiva", false, email);
		Alumno activo = crearAlumno(11L, "Diego", "Activo", true, email);
		Usuario usuario = crearUsuario(100L, email, Set.of(Roles.ROLE_USER), asociadoInactivo);

		when(usuarioService.encontrarPorEmail(email)).thenReturn(Optional.of(usuario));
		when(alumnoRepository.findAllByEmailIgnoreCaseAndActivoTrue(email))
				.thenReturn(List.of(activo));

		UsuarioConAlumnoAsociadoDTO response = authenticationController.obtenerUsuarioAutenticado();

		assertNotNull(response);
		assertNotNull(response.getAlumnoDTO());
		assertEquals(11L, response.getAlumnoDTO().getId());
	}

	@Test
	void obtenerRecordatorioRachaEmail_devuelvePreferenciaActual() {
		String email = "familia@example.com";
		SecurityContextHolder.getContext()
				.setAuthentication(new UsernamePasswordAuthenticationToken(email, "token"));

		Usuario usuario = crearUsuario(101L, email, Set.of(Roles.ROLE_USER), null);
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
				.setAuthentication(new UsernamePasswordAuthenticationToken(email, "token"));

		Usuario usuario = crearUsuario(102L, email, Set.of(Roles.ROLE_USER), null);
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

	private Usuario crearUsuario(Long id, String email, Set<Roles> roles, Alumno alumno) {
		Usuario usuario = new Usuario();
		usuario.setId(id);
		usuario.setEmail(email);
		usuario.setRoles(roles);
		usuario.setAlumno(alumno);
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
