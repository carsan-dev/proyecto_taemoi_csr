package com.taemoi.project.controladores;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.taemoi.project.config.AuditoriaRequestFilter;
import com.taemoi.project.config.JwtAuthenticationFilter;
import com.taemoi.project.config.MethodSecurityCompatibilityConfig;
import com.taemoi.project.config.OAuth2AuthenticationSuccessHandler;
import com.taemoi.project.config.SecurityConfiguration;
import com.taemoi.project.controllers.AuthenticationController;
import com.taemoi.project.controllers.EventoController;
import com.taemoi.project.entities.Evento;
import com.taemoi.project.entities.Roles;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.AuditoriaPayloadSanitizer;
import com.taemoi.project.services.AuditoriaService;
import com.taemoi.project.services.AuthenticationService;
import com.taemoi.project.services.DocumentoService;
import com.taemoi.project.services.EventoService;
import com.taemoi.project.services.ImagenService;
import com.taemoi.project.services.JwtService;
import com.taemoi.project.services.PasswordResetService;
import com.taemoi.project.services.RegistroService;
import com.taemoi.project.services.UsuarioService;

@WebMvcTest(controllers = { AuthenticationController.class, EventoController.class })
@Import({ SecurityConfiguration.class, MethodSecurityCompatibilityConfig.class,
		SecurityAuthorizationWebMvcTest.FilterTestConfig.class })
@TestPropertySource(properties = {
		"app.base.url=http://localhost:8080",
		"cors.allowed.origin=http://localhost:4200"
})
class SecurityAuthorizationWebMvcTest {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private AuthenticationService authenticationService;

	@MockBean
	private UsuarioService usuarioService;

	@MockBean
	private UsuarioRepository usuarioRepository;

	@MockBean
	private AlumnoRepository alumnoRepository;

	@MockBean
	private PasswordResetService passwordResetService;

	@MockBean
	private RegistroService registroService;

	@MockBean
	private EventoService eventoService;

	@MockBean
	private ImagenService imagenService;

	@MockBean
	private DocumentoService documentoService;

	@MockBean
	private JwtService jwtService;

	@MockBean
	private AuditoriaService auditoriaService;

	@MockBean
	private AuditoriaPayloadSanitizer auditoriaPayloadSanitizer;

	@MockBean
	private PasswordEncoder passwordEncoder;

	@MockBean
	private OAuth2AuthenticationSuccessHandler oauth2SuccessHandler;

	@Test
	void anonymousCannotReadAuthRoles() throws Exception {
		mockMvc.perform(get("/api/auth/roles"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void anonymousCannotReadAuthUser() throws Exception {
		mockMvc.perform(get("/api/auth/user"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void authenticatedUserCanReadAuthRoles() throws Exception {
		Usuario usuario = new Usuario();
		usuario.setEmail("user@example.com");
		usuario.setRoles(Set.of(Roles.ROLE_USER));
		when(usuarioService.encontrarPorEmail("user@example.com")).thenReturn(Optional.of(usuario));

		mockMvc.perform(get("/api/auth/roles")
				.with(user("user@example.com").authorities(() -> "ROLE_USER")))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("ROLE_USER")));
	}

	@Test
	void authenticatedUserCanReadAuthUser() throws Exception {
		Usuario usuario = new Usuario();
		usuario.setId(12L);
		usuario.setEmail("user@example.com");
		usuario.setRoles(Set.of(Roles.ROLE_USER));
		when(usuarioService.encontrarPorEmail("user@example.com")).thenReturn(Optional.of(usuario));

		mockMvc.perform(get("/api/auth/user")
				.with(user("user@example.com").authorities(() -> "ROLE_USER")))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("user@example.com")));
	}

	@Test
	void anonymousCanReadVisibleEventDetail() throws Exception {
		Evento evento = new Evento();
		evento.setId(10L);
		evento.setTitulo("Evento visible");
		evento.setVisible(true);
		when(eventoService.obtenerEventoPorId(10L)).thenReturn(evento);

		mockMvc.perform(get("/api/eventos/10"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Evento visible")));
	}

	@Test
	void anonymousCannotReadHiddenEventDetail() throws Exception {
		Evento evento = new Evento();
		evento.setId(20L);
		evento.setTitulo("Evento oculto");
		evento.setVisible(false);
		when(eventoService.obtenerEventoPorId(20L)).thenReturn(evento);

		mockMvc.perform(get("/api/eventos/20"))
				.andExpect(status().isNotFound());
	}

	@Test
	void managerCanReadHiddenEventDetail() throws Exception {
		Evento evento = new Evento();
		evento.setId(21L);
		evento.setTitulo("Evento interno");
		evento.setVisible(false);
		when(eventoService.obtenerEventoPorId(21L)).thenReturn(evento);

		mockMvc.perform(get("/api/eventos/21")
				.with(user("manager@example.com").authorities(() -> "ROLE_MANAGER")))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Evento interno")));
	}

	@TestConfiguration
	static class FilterTestConfig {

		@Bean
		JwtAuthenticationFilter jwtAuthenticationFilter(
				JwtService jwtService,
				UsuarioService usuarioService,
				AlumnoRepository alumnoRepository) {
			return new JwtAuthenticationFilter(jwtService, usuarioService, alumnoRepository);
		}

		@Bean
		AuditoriaRequestFilter auditoriaRequestFilter(
				AuditoriaService auditoriaService,
				AuditoriaPayloadSanitizer auditoriaPayloadSanitizer) {
			return new AuditoriaRequestFilter(auditoriaService, auditoriaPayloadSanitizer);
		}
	}
}
