package com.taemoi.project.controladores;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.multipart.MultipartFile;

import com.taemoi.project.config.AuditoriaRequestFilter;
import com.taemoi.project.config.FileUtils;
import com.taemoi.project.config.JwtAuthenticationFilter;
import com.taemoi.project.config.MethodSecurityCompatibilityConfig;
import com.taemoi.project.config.OAuth2AuthenticationSuccessHandler;
import com.taemoi.project.config.SecurityConfiguration;
import com.taemoi.project.controllers.EventoController;
import com.taemoi.project.entities.Documento;
import com.taemoi.project.entities.Evento;
import com.taemoi.project.exceptions.ArchivoNoValidoException;
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
import com.taemoi.project.utils.DocumentoSecurityUtils;

/**
 * Subidas de eventos (documentos e imagen): un fichero que no pasa la validación responde 400 con {@code mensaje}
 * (HEIC con el de convertir a JPG, decisión 227); el resto de fallos siguen siendo 500. Permisos y rutas, sin cambios.
 */
@WebMvcTest(controllers = EventoController.class)
@Import({ SecurityConfiguration.class, MethodSecurityCompatibilityConfig.class,
		EventoSubidasWebMvcTest.FilterTestConfig.class })
@TestPropertySource(properties = {
		"app.base.url=http://localhost:8080",
		"cors.allowed.origin=http://localhost:4200"
})
@ExtendWith(OutputCaptureExtension.class)
class EventoSubidasWebMvcTest {

	private static final long EVENTO = 70L;

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

	/** El servicio simulado valida como {@code DocumentoServiceImpl}: nombre limpio + lista blanca. */
	private void documentoConValidacionReal() throws IOException {
		when(eventoService.agregarDocumentoAEvento(eq(EVENTO), any(MultipartFile.class))).thenAnswer(invocacion -> {
			MultipartFile archivo = invocacion.getArgument(1);
			String nombre = FileUtils.limpiarNombreArchivo(archivo.getOriginalFilename());
			String tipo = DocumentoSecurityUtils.detectarMimePermitido(nombre, archivo.getContentType());
			Documento documento = new Documento();
			documento.setId(5L);
			documento.setNombre(nombre);
			documento.setTipo(tipo);
			return documento;
		});
	}

	@Test
	void documentoHeicResponde400ConElMensajeDeConvertir(CapturedOutput salida) throws Exception {
		documentoConValidacionReal();
		mockMvc.perform(multipart("/api/eventos/{id}/documentos", EVENTO)
				.file(new MockMultipartFile("archivo", "Cartel de Lucía.heic", "image/heic", new byte[] { 1, 2, 3 }))
				.with(manager()).with(csrf()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value(DocumentoSecurityUtils.MENSAJE_HEIC));

		org.assertj.core.api.Assertions.assertThat(salida.getOut())
				.contains("eventoId=" + EVENTO).contains("ext=heic").contains("contentType=image/heic")
				.doesNotContain("Cartel").doesNotContain("Luc");
	}

	@Test
	void documentoExeResponde400TipoNoPermitido() throws Exception {
		documentoConValidacionReal();
		mockMvc.perform(multipart("/api/eventos/{id}/documentos", EVENTO)
				.file(new MockMultipartFile("archivo", "programa.exe", "application/octet-stream", new byte[] { 'M', 'Z' }))
				.with(admin()).with(csrf()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value("Tipo de documento no permitido."));
	}

	@Test
	void documentoValidoSigueRespondiendo201() throws Exception {
		documentoConValidacionReal();
		mockMvc.perform(multipart("/api/eventos/{id}/documentos", EVENTO)
				.file(new MockMultipartFile("archivo", "bases.pdf", "application/pdf", new byte[] { '%', 'P', 'D', 'F' }))
				.with(manager()).with(csrf()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.tipo").value("application/pdf"));
	}

	@Test
	void errorDeDiscoAlGuardarDocumentoSigueSiendo500SinDetalleInterno(CapturedOutput salida) throws Exception {
		when(eventoService.agregarDocumentoAEvento(eq(EVENTO), any(MultipartFile.class)))
				.thenThrow(new IOException("/srv/documentos/org-1/eventos/70/bases.pdf: disco lleno"));
		mockMvc.perform(multipart("/api/eventos/{id}/documentos", EVENTO)
				.file(new MockMultipartFile("archivo", "bases.pdf", "application/pdf", new byte[] { 1 }))
				.with(manager()).with(csrf()))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.error").value(not(containsString("/srv"))));

		org.assertj.core.api.Assertions.assertThat(salida.getOut())
				.contains("eventoId=" + EVENTO).contains("IOException").doesNotContain("bases.pdf");
	}

	@Test
	void documentoDeFamiliaSigueDenegado() throws Exception {
		mockMvc.perform(multipart("/api/eventos/{id}/documentos", EVENTO)
				.file(new MockMultipartFile("archivo", "bases.pdf", "application/pdf", new byte[] { 1 }))
				.with(familia()).with(csrf()))
				.andExpect(status().isForbidden());
	}

	@Test
	void crearEventoConImagenHeicResponde400() throws Exception {
		when(eventoService.guardarEvento(any(Evento.class), any(MultipartFile.class)))
				.thenThrow(new ArchivoNoValidoException(DocumentoSecurityUtils.MENSAJE_HEIC));
		mockMvc.perform(multipart("/api/eventos/crear")
				.file(new MockMultipartFile("file", "cartel.heic", "image/heic", new byte[] { 1, 2 }))
				.param("nuevo", "{\"titulo\":\"Torneo\"}")
				.with(manager()).with(csrf()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value(DocumentoSecurityUtils.MENSAJE_HEIC));
	}

	@Test
	void actualizarEventoConImagenHeicResponde400() throws Exception {
		when(imagenService.guardarImagenEvento(any(MultipartFile.class)))
				.thenThrow(new ArchivoNoValidoException(DocumentoSecurityUtils.MENSAJE_HEIC));
		mockMvc.perform(multipart(HttpMethod.PUT, "/api/eventos/{id}", EVENTO)
				.file(new MockMultipartFile("file", "cartel.jpg", "image/heif", new byte[] { 1, 2 }))
				.param("eventoEditado", "{\"titulo\":\"Torneo\"}")
				.with(admin()).with(csrf()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value(DocumentoSecurityUtils.MENSAJE_HEIC));
	}

	@Test
	void actualizarEventoConImagenIlegibleResponde400() throws Exception {
		when(imagenService.guardarImagenEvento(any(MultipartFile.class)))
				.thenThrow(new ArchivoNoValidoException("No se pudo leer la imagen del evento."));
		mockMvc.perform(multipart(HttpMethod.PUT, "/api/eventos/{id}", EVENTO)
				.file(new MockMultipartFile("file", "cartel.png", "image/png", new byte[] { 1, 2 }))
				.param("eventoEditado", "{\"titulo\":\"Torneo\"}")
				.with(admin()).with(csrf()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value("No se pudo leer la imagen del evento."));
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
