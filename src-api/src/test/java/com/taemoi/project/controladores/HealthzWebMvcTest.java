package com.taemoi.project.controladores;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

import com.taemoi.project.config.AuditoriaRequestFilter;
import com.taemoi.project.config.JwtAuthenticationFilter;
import com.taemoi.project.config.MethodSecurityCompatibilityConfig;
import com.taemoi.project.config.OAuth2AuthenticationSuccessHandler;
import com.taemoi.project.config.SecurityConfiguration;
import com.taemoi.project.controllers.EventoController;
import com.taemoi.project.controllers.HealthzController;
import com.taemoi.project.entities.AuditoriaAccion;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.AuditoriaPayloadSanitizer;
import com.taemoi.project.services.AuditoriaService;
import com.taemoi.project.services.DocumentoService;
import com.taemoi.project.services.EventoService;
import com.taemoi.project.services.ImagenService;
import com.taemoi.project.services.JwtService;
import com.taemoi.project.services.MembresiaService;
import com.taemoi.project.services.UsuarioService;

/**
 * {@code GET /healthz} (healthcheck de Docker): responde 200 sin sesión, no se audita y solo se abre esa
 * ruta con GET. Las rutas reales de {@code /api} se siguen auditando igual.
 */
@WebMvcTest(controllers = { HealthzController.class, EventoController.class })
@Import({ SecurityConfiguration.class, MethodSecurityCompatibilityConfig.class,
		HealthzWebMvcTest.FilterTestConfig.class })
@TestPropertySource(properties = {
		"app.base.url=http://localhost:8080",
		"cors.allowed.origin=http://localhost:4200"
})
class HealthzWebMvcTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private UsuarioService usuarioService;

	@MockitoBean
	private UsuarioRepository usuarioRepository;

	@MockitoBean
	private AlumnoRepository alumnoRepository;

	@MockitoBean
	private EventoService eventoService;

	@MockitoBean
	private ImagenService imagenService;

	@MockitoBean
	private DocumentoService documentoService;

	@MockitoBean
	private JwtService jwtService;

	@MockitoBean
	private AuditoriaService auditoriaService;

	@MockitoBean
	private AuditoriaPayloadSanitizer auditoriaPayloadSanitizer;

	@MockitoBean
	private PasswordEncoder passwordEncoder;

	@MockitoBean
	private OAuth2AuthenticationSuccessHandler oauth2SuccessHandler;

	@MockitoBean
	private MembresiaService membresiaService;

	@Test
	void healthzResponde200SinAutenticarYNoSeAudita() throws Exception {
		mockMvc.perform(get("/healthz"))
				.andExpect(status().isOk())
				.andExpect(content().json("{\"status\":\"UP\"}", true));

		verifyNoInteractions(auditoriaService);
		// Sin acceso a datos: la respuesta no depende de ningún servicio.
		verifyNoInteractions(eventoService, usuarioService, usuarioRepository, alumnoRepository);
	}

	@Test
	void healthzSoloAbreGetEnEsaRuta() throws Exception {
		mockMvc.perform(post("/healthz"))
				.andExpect(status().is4xxClientError());
		mockMvc.perform(get("/healthz/detalle"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/healthzx"))
				.andExpect(status().isUnauthorized());

		verifyNoInteractions(auditoriaService);
	}

	@Test
	void apiEventosSeSigueAuditando() throws Exception {
		mockMvc.perform(get("/api/eventos"))
				.andExpect(status().isOk());

		verify(auditoriaService, times(1)).registrarEvento(
				eq(AuditoriaAccion.READ),
				eq("GET"),
				eq("/api/eventos"),
				any(),
				any(),
				anyBoolean(),
				eq(200),
				any(),
				any());
	}

	@TestConfiguration
	static class FilterTestConfig {

		@Bean
		JwtAuthenticationFilter jwtAuthenticationFilter() {
			return new JwtAuthenticationFilter();
		}

		@Bean
		AuditoriaRequestFilter auditoriaRequestFilter(
				AuditoriaService auditoriaService,
				AuditoriaPayloadSanitizer auditoriaPayloadSanitizer) {
			return new AuditoriaRequestFilter(auditoriaService, auditoriaPayloadSanitizer);
		}
	}
}
