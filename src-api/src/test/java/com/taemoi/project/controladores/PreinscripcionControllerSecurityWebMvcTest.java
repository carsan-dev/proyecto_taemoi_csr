package com.taemoi.project.controladores;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import com.taemoi.project.config.AuditoriaRequestFilter;
import com.taemoi.project.config.JwtAuthenticationFilter;
import com.taemoi.project.config.MethodSecurityCompatibilityConfig;
import com.taemoi.project.config.OAuth2AuthenticationSuccessHandler;
import com.taemoi.project.config.SecurityConfiguration;
import com.taemoi.project.controllers.PreinscripcionController;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.entities.Preinscripcion;
import com.taemoi.project.services.AuditoriaPayloadSanitizer;
import com.taemoi.project.services.AuditoriaService;
import com.taemoi.project.services.JwtService;
import com.taemoi.project.services.PreinscripcionService;
import com.taemoi.project.services.TemporadaService;
import com.taemoi.project.services.UsuarioService;

@WebMvcTest(controllers = PreinscripcionController.class)
@Import({ SecurityConfiguration.class, MethodSecurityCompatibilityConfig.class,
		PreinscripcionControllerSecurityWebMvcTest.FilterTestConfig.class })
@TestPropertySource(properties = {
		"app.base.url=http://localhost:8080",
		"cors.allowed.origin=http://localhost:4200"
})
class PreinscripcionControllerSecurityWebMvcTest {
	@Autowired MockMvc mockMvc;

	@MockitoBean PreinscripcionService preinscripciones;
	@MockitoBean TemporadaService temporadas;
	@MockitoBean UsuarioService usuarios;
	@MockitoBean AlumnoRepository alumnos;
	@MockitoBean JwtService jwtService;
	@MockitoBean AuditoriaService auditoriaService;
	@MockitoBean AuditoriaPayloadSanitizer auditoriaPayloadSanitizer;
	@MockitoBean PasswordEncoder passwordEncoder;
	@MockitoBean OAuth2AuthenticationSuccessHandler oauth2SuccessHandler;

	@Test
	void anonimoNoPuedeReenviarAvisoDeCambio() throws Exception {
		mockMvc.perform(post("/api/preinscripciones/PRE-1/reenviar-cambio-turnos"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void usuarioNoPuedeReenviarAvisoDeCambio() throws Exception {
		mockMvc.perform(post("/api/preinscripciones/PRE-1/reenviar-cambio-turnos")
				.with(user("user@example.com").authorities(() -> "ROLE_USER")))
				.andExpect(status().isForbidden());
	}

	@Test
	void managerPuedeReenviarAvisoDeCambio() throws Exception {
		mockMvc.perform(post("/api/preinscripciones/PRE-1/reenviar-cambio-turnos")
				.with(user("manager@example.com").authorities(() -> "ROLE_MANAGER")))
				.andExpect(status().isOk());

		verify(preinscripciones).reenviarCambioTurnos("PRE-1");
	}

	@Test
	void adminPuedeReenviarAvisoDeCambio() throws Exception {
		mockMvc.perform(post("/api/preinscripciones/PRE-1/reenviar-cambio-turnos")
				.with(user("admin@example.com").authorities(() -> "ROLE_ADMIN")))
				.andExpect(status().isOk());

		verify(preinscripciones).reenviarCambioTurnos("PRE-1");
	}

	@Test
	void detalleDiferenciaHorarioOriginalYAsignacionVigente() throws Exception {
		Preinscripcion p = new Preinscripcion();
		p.setReferencia("PRE-1");
		when(preinscripciones.buscar("PRE-1")).thenReturn(p);
		when(preinscripciones.grupoOriginal(p)).thenReturn("Horario original firmado");
		when(preinscripciones.grupoVigente(p)).thenReturn("Horario vigente");
		when(preinscripciones.turnosSolicitados(p)).thenReturn(List.of(Map.of("id", 21L)));
		when(preinscripciones.alumnosCoincidentes(p)).thenReturn(List.of());

		mockMvc.perform(get("/api/preinscripciones/PRE-1")
				.with(user("manager@example.com").authorities(() -> "ROLE_MANAGER")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.grupoOriginal").value("Horario original firmado"))
				.andExpect(jsonPath("$.grupo").value("Horario vigente"))
				.andExpect(jsonPath("$.turnosSolicitados[0].id").value(21));
	}

	@TestConfiguration
	static class FilterTestConfig {
		@Bean
		JwtAuthenticationFilter jwtAuthenticationFilter(JwtService jwtService, UsuarioService usuarioService,
				AlumnoRepository alumnoRepository) {
			return new JwtAuthenticationFilter(jwtService, usuarioService, alumnoRepository);
		}

		@Bean
		AuditoriaRequestFilter auditoriaRequestFilter(AuditoriaService auditoriaService,
				AuditoriaPayloadSanitizer auditoriaPayloadSanitizer) {
			return new AuditoriaRequestFilter(auditoriaService, auditoriaPayloadSanitizer);
		}
	}
}
