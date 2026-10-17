package com.taemoi.project.controladores;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import org.springframework.http.MediaType;
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
import com.taemoi.project.entities.EstadoEmailCancelacion;
import com.taemoi.project.entities.EstadoPreinscripcion;
import com.taemoi.project.entities.Preinscripcion;
import com.taemoi.project.services.AuditoriaPayloadSanitizer;
import com.taemoi.project.services.AuditoriaService;
import com.taemoi.project.services.JwtService;
import com.taemoi.project.services.PreinscripcionService;
import com.taemoi.project.services.TemporadaService;
import com.taemoi.project.services.UsuarioService;

@WebMvcTest(controllers = PreinscripcionController.class)
@Import({ SecurityConfiguration.class, MethodSecurityCompatibilityConfig.class,
		PreinscripcionControllerSecurityWebMvcTest.FilterTestConfig.class,
		// Entrega 6-2: los @PreAuthorize consultan @acceso.preinscripcionEnSede (bean real, puerto simulado).
		com.taemoi.project.acceso.AccesoRecursos.class, com.taemoi.project.organizacion.AlcanceActual.class })
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
	@MockitoBean com.taemoi.project.acceso.ConsultaAccesoRecursos consultaAcceso;

	/** Entrega 6-2: la gestión no consulta el alcance (alcance total, sin consulta extra). */
	@Test
	void gestionNoConsultaElAlcanceDeLaPreinscripcion() throws Exception {
		mockMvc.perform(post("/api/preinscripciones/PRE-1/reenviar-cambio-turnos")
				.with(user("manager@example.com").authorities(() -> "ROLE_MANAGER")))
				.andExpect(status().isOk());

		verifyNoInteractions(consultaAcceso);
	}

	/** Entrega 6-2: un responsable sin sesión de escuela no tiene sedes: 403 sin tocar el servicio. */
	@Test
	void responsableSinSedesNoPuedeCancelar() throws Exception {
		mockMvc.perform(post("/api/preinscripciones/PRE-1/cancelar")
				.with(user("resp@example.com").authorities(() -> "ROLE_RESPONSABLE_SEDE")))
				.andExpect(status().isForbidden());

		verifyNoInteractions(preinscripciones);
	}

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

	@Test
	void anonimoNoPuedeCancelar() throws Exception {
		mockMvc.perform(post("/api/preinscripciones/PRE-1/cancelar"))
				.andExpect(status().isUnauthorized());

		verifyNoInteractions(preinscripciones);
	}

	@Test
	void usuarioNoPuedeCancelar() throws Exception {
		mockMvc.perform(post("/api/preinscripciones/PRE-1/cancelar")
				.with(user("user@example.com").authorities(() -> "ROLE_USER"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"x\"}"))
				.andExpect(status().isForbidden());

		verifyNoInteractions(preinscripciones);
	}

	@Test
	void managerCancelaSinCuerpoConMotivoNulo() throws Exception {
		mockMvc.perform(post("/api/preinscripciones/PRE-1/cancelar")
				.with(user("manager@example.com").authorities(() -> "ROLE_MANAGER")))
				.andExpect(status().isOk());

		verify(preinscripciones).cancelar("PRE-1", null);
	}

	@Test
	void adminCancelaConCuerpoVacioOSinMotivo() throws Exception {
		mockMvc.perform(post("/api/preinscripciones/PRE-1/cancelar")
				.with(user("admin@example.com").authorities(() -> "ROLE_ADMIN"))
				.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isOk());

		verify(preinscripciones).cancelar("PRE-1", null);
	}

	@Test
	void adminCancelaConMotivo() throws Exception {
		mockMvc.perform(post("/api/preinscripciones/PRE-1/cancelar")
				.with(user("admin@example.com").authorities(() -> "ROLE_ADMIN"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Grupo cerrado\"}"))
				.andExpect(status().isOk());

		verify(preinscripciones).cancelar("PRE-1", "Grupo cerrado");
	}

	@Test
	void motivoDeMasDe500CaracteresDevuelve400SinCancelar() throws Exception {
		mockMvc.perform(post("/api/preinscripciones/PRE-1/cancelar")
				.with(user("admin@example.com").authorities(() -> "ROLE_ADMIN"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"" + "x".repeat(501) + "\"}"))
				.andExpect(status().isBadRequest());

		verify(preinscripciones, never()).cancelar(anyString(), any());
	}

	@Test
	void motivoDeExactamente500CaracteresSeAcepta() throws Exception {
		String motivo = "x".repeat(500);
		mockMvc.perform(post("/api/preinscripciones/PRE-1/cancelar")
				.with(user("admin@example.com").authorities(() -> "ROLE_ADMIN"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"" + motivo + "\"}"))
				.andExpect(status().isOk());

		verify(preinscripciones).cancelar("PRE-1", motivo);
	}

	@Test
	void detalleIncluyeMotivoYSeguimientoDelAvisoDeCancelacion() throws Exception {
		Preinscripcion p = new Preinscripcion();
		p.setReferencia("PRE-1");
		p.setEstado(EstadoPreinscripcion.CANCELADA);
		p.setMotivoCancelacion("Grupo cerrado");
		p.setEstadoEmailCancelacion(EstadoEmailCancelacion.ERROR);
		p.setEmailCancelacionIntentos(2);
		p.setEmailCancelacionUltimoError("SMTP no disponible");
		when(preinscripciones.buscar("PRE-1")).thenReturn(p);
		when(preinscripciones.alumnosCoincidentes(p)).thenReturn(List.of());

		mockMvc.perform(get("/api/preinscripciones/PRE-1")
				.with(user("manager@example.com").authorities(() -> "ROLE_MANAGER")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.motivoCancelacion").value("Grupo cerrado"))
				.andExpect(jsonPath("$.estadoEmailCancelacion").value("ERROR"))
				.andExpect(jsonPath("$.emailCancelacionIntentos").value(2))
				.andExpect(jsonPath("$.emailCancelacionUltimoError").value("SMTP no disponible"));
	}

	/**
	 * Contrato real de fechas (no lo cambia esta rama): con {@code @EnableWebMvc} en WebConfig, Spring MVC no usa
	 * el ObjectMapper de Boot y los {@code Instant} salen como número en SEGUNDOS con decimales, las LocalDate como
	 * [año, mes, día] y los java.util.Date como milisegundos. El frontend los normaliza (utils/fecha-api.ts).
	 */
	@Test
	void detalleSerializaLosInstantComoSegundosYLasLocalDateComoArray() throws Exception {
		Preinscripcion p = new Preinscripcion();
		p.setReferencia("PRE-1");
		p.setFechaNacimiento(java.time.LocalDate.of(2014, 4, 12));
		p.setEmailFinalizacionEnviadoEn(java.time.Instant.ofEpochSecond(1_760_000_000L, 500_000_000));
		p.setTurnosModificadosEn(java.time.Instant.ofEpochSecond(1_760_000_000L));
		when(preinscripciones.buscar("PRE-1")).thenReturn(p);
		when(preinscripciones.alumnosCoincidentes(p)).thenReturn(List.of(
				Map.of("fechaNacimiento", new java.util.Date(1_397_260_800_000L))));

		mockMvc.perform(get("/api/preinscripciones/PRE-1")
				.with(user("manager@example.com").authorities(() -> "ROLE_MANAGER")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.emailFinalizacionEnviadoEn").value(1_760_000_000.5))
				.andExpect(jsonPath("$.turnosModificadosEn").value(1_760_000_000.0))
				.andExpect(jsonPath("$.fechaNacimiento[0]").value(2014))
				.andExpect(jsonPath("$.fechaNacimiento[1]").value(4))
				.andExpect(jsonPath("$.fechaNacimiento[2]").value(12))
				.andExpect(jsonPath("$.alumnosCoincidentes[0].fechaNacimiento").value(1_397_260_800_000L));
	}

	@TestConfiguration
	static class FilterTestConfig {
		@Bean
		JwtAuthenticationFilter jwtAuthenticationFilter() {
			return new JwtAuthenticationFilter();
		}

		@Bean
		AuditoriaRequestFilter auditoriaRequestFilter(AuditoriaService auditoriaService,
				AuditoriaPayloadSanitizer auditoriaPayloadSanitizer) {
			return new AuditoriaRequestFilter(auditoriaService, auditoriaPayloadSanitizer);
		}
	}
}
