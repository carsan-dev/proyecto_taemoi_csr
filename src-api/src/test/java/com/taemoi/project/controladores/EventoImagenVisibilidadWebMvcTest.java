package com.taemoi.project.controladores;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.taemoi.project.config.AuditoriaRequestFilter;
import com.taemoi.project.config.JwtAuthenticationFilter;
import com.taemoi.project.config.MethodSecurityCompatibilityConfig;
import com.taemoi.project.config.OAuth2AuthenticationSuccessHandler;
import com.taemoi.project.config.SecurityConfiguration;
import com.taemoi.project.controllers.EventoController;
import com.taemoi.project.entities.Evento;
import com.taemoi.project.entities.Imagen;
import com.taemoi.project.exceptions.evento.EventoNoEncontradoException;
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
 * {@code GET /api/eventos/{id}/imagen}: la imagen de un evento visible es pública; la de un evento oculto solo
 * la ven ADMIN o MANAGER, y para el resto responde 404 (como el detalle del evento oculto).
 */
@WebMvcTest(controllers = EventoController.class)
@Import({ SecurityConfiguration.class, MethodSecurityCompatibilityConfig.class,
		EventoImagenVisibilidadWebMvcTest.FilterTestConfig.class })
@TestPropertySource(properties = {
		"app.base.url=http://localhost:8080",
		"cors.allowed.origin=http://localhost:4200"
})
class EventoImagenVisibilidadWebMvcTest {

	private static final long VISIBLE = 30L;
	private static final long OCULTO = 31L;
	private static final long SIN_VISIBILIDAD = 32L;
	private static final long INEXISTENTE = 99L;

	@TempDir
	static Path directorio;

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private EventoService eventoService;

	@MockitoBean
	private ImagenService imagenService;

	@MockitoBean
	private DocumentoService documentoService;

	@MockitoBean
	private UsuarioService usuarioService;

	@MockitoBean
	private UsuarioRepository usuarioRepository;

	@MockitoBean
	private AlumnoRepository alumnoRepository;

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

	@BeforeEach
	void preparar() throws Exception {
		Path archivo = directorio.resolve("evento.png");
		if (!Files.exists(archivo)) {
			Files.write(archivo, new byte[] { (byte) 0x89, 'P', 'N', 'G', 1, 2, 3, 4 });
		}
		when(imagenService.resolverRutaArchivo(any(Imagen.class))).thenReturn(archivo);
		when(eventoService.obtenerEventoPorId(VISIBLE)).thenReturn(evento(VISIBLE, true));
		when(eventoService.obtenerEventoPorId(OCULTO)).thenReturn(evento(OCULTO, false));
		when(eventoService.obtenerEventoPorId(SIN_VISIBILIDAD)).thenReturn(evento(SIN_VISIBILIDAD, null));
		when(eventoService.obtenerEventoPorId(INEXISTENTE))
				.thenThrow(new EventoNoEncontradoException("El evento con ID 99 no existe."));
	}

	@Test
	void imagenDeEventoVisibleEsPublicaYCacheable() throws Exception {
		mockMvc.perform(get("/api/eventos/{id}/imagen", VISIBLE))
				.andExpect(status().isOk())
				.andExpect(content().contentType("image/png"))
				.andExpect(header().string("Cache-Control", containsString("public")));
		mockMvc.perform(get("/api/eventos/{id}/imagen", VISIBLE).with(familia()))
				.andExpect(status().isOk());
	}

	@Test
	void imagenDeEventoOcultoEs404ParaAnonimoYFamilia() throws Exception {
		mockMvc.perform(get("/api/eventos/{id}/imagen", OCULTO))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/eventos/{id}/imagen", OCULTO).with(familia()))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/eventos/{id}/imagen", SIN_VISIBILIDAD))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/eventos/{id}/imagen", OCULTO).param("w", "480"))
				.andExpect(status().isNotFound());
	}

	@Test
	void imagenDeEventoOcultoLaVenManagerYAdminSinCacheCompartida() throws Exception {
		mockMvc.perform(get("/api/eventos/{id}/imagen", OCULTO).with(manager()))
				.andExpect(status().isOk())
				.andExpect(content().contentType("image/png"))
				.andExpect(header().string("Cache-Control", containsString("private")));
		mockMvc.perform(get("/api/eventos/{id}/imagen", OCULTO).with(admin()))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/eventos/{id}/imagen", SIN_VISIBILIDAD).with(admin()))
				.andExpect(status().isOk());
	}

	@Test
	void eventoInexistenteEs404() throws Exception {
		mockMvc.perform(get("/api/eventos/{id}/imagen", INEXISTENTE))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/eventos/{id}/imagen", INEXISTENTE).with(admin()))
				.andExpect(status().isNotFound());
	}

	private static Evento evento(long id, Boolean visible) {
		Evento evento = new Evento();
		evento.setId(id);
		evento.setTitulo("Evento " + id);
		evento.setVisible(visible);
		evento.setFotoEvento(new Imagen("evento.png", "image/png", null, "evento.png"));
		return evento;
	}

	private static RequestPostProcessor familia() {
		return user("familia@example.com").authorities(() -> "ROLE_USER");
	}

	private static RequestPostProcessor manager() {
		return user("manager@example.com").authorities(() -> "ROLE_MANAGER");
	}

	private static RequestPostProcessor admin() {
		return user("admin@example.com").authorities(() -> "ROLE_ADMIN");
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
