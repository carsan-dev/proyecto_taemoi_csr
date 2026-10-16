package com.taemoi.project.controladores;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.taemoi.project.config.AuditoriaRequestFilter;
import com.taemoi.project.config.JwtAuthenticationFilter;
import com.taemoi.project.config.MethodSecurityCompatibilityConfig;
import com.taemoi.project.config.OAuth2AuthenticationSuccessHandler;
import com.taemoi.project.config.SecurityConfiguration;
import com.taemoi.project.controllers.PDFController;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.services.AuditoriaPayloadSanitizer;
import com.taemoi.project.services.AuditoriaService;
import com.taemoi.project.services.CatalogoDisciplinasService;
import com.taemoi.project.services.JwtService;
import com.taemoi.project.services.PDFService;
import com.taemoi.project.services.UsuarioService;

/**
 * El listado de asistencia imprime nombre y apellidos de cada alumno: solo ADMIN y MANAGER pueden
 * descargarlo (antes estaba en permitAll y cualquier anónimo obtenía el de la escuela del host).
 */
@WebMvcTest(controllers = PDFController.class)
@Import({ SecurityConfiguration.class, MethodSecurityCompatibilityConfig.class,
		PDFControllerSecurityWebMvcTest.FilterTestConfig.class })
@TestPropertySource(properties = {
		"app.base.url=http://localhost:8080",
		"cors.allowed.origin=http://localhost:4200"
})
class PDFControllerSecurityWebMvcTest {
	private static final String ASISTENCIA = "/api/informes/asistencia";
	private static final byte[] PDF = "%PDF-asistencia".getBytes();

	@Autowired MockMvc mockMvc;

	@MockitoBean PDFService pdfService;
	@MockitoBean CatalogoDisciplinasService catalogoDisciplinas;
	@MockitoBean UsuarioService usuarios;
	@MockitoBean AlumnoRepository alumnos;
	@MockitoBean JwtService jwtService;
	@MockitoBean AuditoriaService auditoriaService;
	@MockitoBean AuditoriaPayloadSanitizer auditoriaPayloadSanitizer;
	@MockitoBean PasswordEncoder passwordEncoder;
	@MockitoBean OAuth2AuthenticationSuccessHandler oauth2SuccessHandler;

	@Test
	void anonimoNoPuedeDescargarAsistencia() throws Exception {
		mockMvc.perform(get(ASISTENCIA).param("year", "2026").param("month", "9").param("grupo", "lunes"))
				.andExpect(status().isUnauthorized());

		verify(pdfService, never()).generarListadoAsistencia(anyInt(), anyInt(), anyList(), any());
	}

	@Test
	void familiaNoPuedeDescargarAsistencia() throws Exception {
		mockMvc.perform(get(ASISTENCIA).param("year", "2026").param("month", "9").param("grupo", "lunes")
				.with(user("familia@example.com").authorities(() -> "ROLE_USER")))
				.andExpect(status().isForbidden());

		verify(pdfService, never()).generarListadoAsistencia(anyInt(), anyInt(), anyList(), any());
	}

	@Test
	void managerPuedeDescargarAsistencia() throws Exception {
		when(pdfService.generarListadoAsistencia(2026, 9, List.of("lunes"), null)).thenReturn(PDF);

		mockMvc.perform(get(ASISTENCIA).param("year", "2026").param("month", "9").param("grupo", "lunes")
				.with(user("manager@example.com").authorities(() -> "ROLE_MANAGER")))
				.andExpect(status().isOk())
				.andExpect(content().bytes(PDF));
	}

	@Test
	void adminPuedeDescargarAsistencia() throws Exception {
		when(pdfService.generarListadoAsistencia(2026, 9, List.of("lunes", "martes"), null)).thenReturn(PDF);

		mockMvc.perform(get(ASISTENCIA).param("year", "2026").param("month", "9")
				.param("grupos", "lunes").param("grupos", "martes")
				.with(user("admin@example.com").authorities(() -> "ROLE_ADMIN")))
				.andExpect(status().isOk())
				.andExpect(content().bytes(PDF));
	}

	/** Segunda barrera: aunque se reabriera la ruta en la cadena de filtros, el método sigue protegido. */
	@Test
	void handlerDeAsistenciaExigeLosMismosRolesQueElRestoDeInformes() throws Exception {
		PreAuthorize asistencia = PDFController.class.getMethod("generarAsistencia", int.class, int.class,
				String.class, List.class, String.class, jakarta.servlet.http.HttpServletResponse.class)
				.getAnnotation(PreAuthorize.class);
		PreAuthorize hermano = PDFController.class.getMethod("generarInformeAlumnosPorGrado", boolean.class)
				.getAnnotation(PreAuthorize.class);

		assertThat(asistencia).isNotNull();
		assertThat(asistencia.value()).isEqualTo(hermano.value());
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
