package com.taemoi.project.controladores;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
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

import com.taemoi.project.acceso.AccesoRecursos;
import com.taemoi.project.config.AuditoriaRequestFilter;
import com.taemoi.project.config.FileUtils;
import com.taemoi.project.config.JwtAuthenticationFilter;
import com.taemoi.project.config.MethodSecurityCompatibilityConfig;
import com.taemoi.project.config.OAuth2AuthenticationSuccessHandler;
import com.taemoi.project.config.SecurityConfiguration;
import com.taemoi.project.controllers.AlumnoController;
import com.taemoi.project.dtos.AlumnoDTO;
import com.taemoi.project.entities.Documento;
import com.taemoi.project.exceptions.ArchivoNoValidoException;
import com.taemoi.project.exceptions.alumno.AlumnoNoEncontradoException;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.AlumnoDeporteService;
import com.taemoi.project.services.AlumnoService;
import com.taemoi.project.services.AuditoriaPayloadSanitizer;
import com.taemoi.project.services.AuditoriaService;
import com.taemoi.project.services.CatalogoDisciplinasService;
import com.taemoi.project.services.DocumentoService;
import com.taemoi.project.services.GrupoService;
import com.taemoi.project.services.ImagenService;
import com.taemoi.project.services.JwtService;
import com.taemoi.project.services.MaterialExamenService;
import com.taemoi.project.services.MembresiaService;
import com.taemoi.project.services.PreinscripcionService;
import com.taemoi.project.services.ProgresionGradosService;
import com.taemoi.project.services.UsuarioService;
import com.taemoi.project.utils.DocumentoSecurityUtils;

/**
 * Subidas de alumnos (documentos y foto): un fichero que no pasa la validación responde 400 con {@code mensaje}
 * (HEIC con el de convertir a JPG, decisión 227) y deja un aviso en el log sin el nombre del fichero; el resto de
 * fallos siguen siendo 500. Permisos y rutas, sin cambios: {@code @acceso} se simula y concede.
 */
@WebMvcTest(controllers = AlumnoController.class)
@Import({ SecurityConfiguration.class, MethodSecurityCompatibilityConfig.class,
		AlumnoSubidasWebMvcTest.FilterTestConfig.class })
@TestPropertySource(properties = {
		"app.base.url=http://localhost:8080",
		"cors.allowed.origin=http://localhost:4200"
})
@ExtendWith(OutputCaptureExtension.class)
class AlumnoSubidasWebMvcTest {

	private static final long ALUMNO = 812L;

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean(name = "acceso")
	private AccesoRecursos acceso;

	@MockitoBean
	private AlumnoService alumnoService;

	@MockitoBean
	private AlumnoRepository alumnoRepository;

	@MockitoBean
	private GrupoService grupoService;

	@MockitoBean
	private ImagenService imagenService;

	@MockitoBean
	private DocumentoService documentoService;

	@MockitoBean
	private MaterialExamenService materialExamenService;

	@MockitoBean
	private AlumnoDeporteService alumnoDeporteService;

	@MockitoBean
	private PreinscripcionService preinscripcionService;

	@MockitoBean
	private CatalogoDisciplinasService catalogoDisciplinas;

	@MockitoBean
	private ProgresionGradosService progresionGrados;

	@MockitoBean
	private com.taemoi.project.services.ResumenCargosAlumnoService resumenCargosAlumno;

	@MockitoBean
	private UsuarioService usuarioService;

	@MockitoBean
	private UsuarioRepository usuarioRepository;

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
	void preparar() {
		when(acceso.alumnoEnSede(anyLong(), anyString())).thenReturn(true);
		// El servicio simulado valida como DocumentoServiceImpl: nombre limpio + lista blanca.
		when(alumnoService.agregarDocumentoAAlumno(eq(ALUMNO), any(MultipartFile.class))).thenAnswer(invocacion -> {
			MultipartFile archivo = invocacion.getArgument(1);
			String nombre = FileUtils.limpiarNombreArchivo(archivo.getOriginalFilename());
			String tipo = DocumentoSecurityUtils.detectarMimePermitido(nombre, archivo.getContentType());
			Documento documento = new Documento();
			documento.setId(9L);
			documento.setNombre(nombre);
			documento.setTipo(tipo);
			return documento;
		});
	}

	@Test
	void documentoHeicResponde400ConElMensajeDeConvertir(CapturedOutput salida) throws Exception {
		mockMvc.perform(multipart("/api/alumnos/{id}/documentos", ALUMNO)
				.file(new MockMultipartFile("archivo", "DNI Marta Ruiz.heic", "image/heic", new byte[] { 1, 2, 3 }))
				.with(admin()).with(csrf()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value(DocumentoSecurityUtils.MENSAJE_HEIC));

		assertThat(salida.getOut()).contains("alumnoId=" + ALUMNO).contains("ext=heic")
				.contains("contentType=image/heic").doesNotContain("Marta").doesNotContain("Ruiz");
	}

	@Test
	void documentoExeResponde400TipoNoPermitido() throws Exception {
		mockMvc.perform(multipart("/api/alumnos/{id}/documentos", ALUMNO)
				.file(new MockMultipartFile("archivo", "programa.exe", "application/octet-stream", new byte[] { 'M', 'Z' }))
				.with(manager()).with(csrf()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value("Tipo de documento no permitido."));
	}

	@Test
	void documentoSinExtensionResponde400() throws Exception {
		mockMvc.perform(multipart("/api/alumnos/{id}/documentos", ALUMNO)
				.file(new MockMultipartFile("archivo", "justificante", "application/pdf", new byte[] { 1 }))
				.with(manager()).with(csrf()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value("El documento debe incluir una extension permitida."));
	}

	@Test
	void documentoValidoSigueRespondiendo200() throws Exception {
		mockMvc.perform(multipart("/api/alumnos/{id}/documentos", ALUMNO)
				.file(new MockMultipartFile("archivo", "foto.jpg", "application/octet-stream", new byte[] { 1 }))
				.with(manager()).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tipo").value("image/jpeg"));
	}

	@Test
	void errorInesperadoAlSubirDocumentoSigueSiendo500SinDetalleInterno(CapturedOutput salida) throws Exception {
		when(alumnoService.agregarDocumentoAAlumno(eq(ALUMNO), any(MultipartFile.class)))
				.thenThrow(new RuntimeException("Error al guardar el documento: /srv/documentos/org-1/alumnos/812/dni.pdf"));
		mockMvc.perform(multipart("/api/alumnos/{id}/documentos", ALUMNO)
				.file(new MockMultipartFile("archivo", "dni.pdf", "application/pdf", new byte[] { 1 }))
				.with(manager()).with(csrf()))
				.andExpect(status().isInternalServerError())
				.andExpect(content().string(Matchers.not(Matchers.containsString("/srv"))));

		assertThat(salida.getOut()).contains("alumnoId=" + ALUMNO).contains("RuntimeException")
				.doesNotContain("dni.pdf");
	}

	@Test
	void documentoDeFamiliaSigueDenegado() throws Exception {
		mockMvc.perform(multipart("/api/alumnos/{id}/documentos", ALUMNO)
				.file(new MockMultipartFile("archivo", "foto.jpg", "image/jpeg", new byte[] { 1 }))
				.with(familia()).with(csrf()))
				.andExpect(status().isForbidden());
	}

	@Test
	void crearAlumnoConFotoHeicResponde400() throws Exception {
		when(imagenService.guardarImagen(any(MultipartFile.class)))
				.thenThrow(new ArchivoNoValidoException(DocumentoSecurityUtils.MENSAJE_HEIC));
		mockMvc.perform(multipart("/api/alumnos/crear")
				.file(new MockMultipartFile("file", "IMG_0001.HEIC", "image/heic", new byte[] { 1, 2 }))
				.param("nuevo", "{\"nombre\":\"Prueba\"}")
				.with(admin()).with(csrf()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value(DocumentoSecurityUtils.MENSAJE_HEIC));
	}

	@Test
	void crearAlumnoConFotoIlegibleResponde400() throws Exception {
		when(imagenService.guardarImagen(any(MultipartFile.class)))
				.thenThrow(new ArchivoNoValidoException("No se pudo leer la imagen del alumno."));
		mockMvc.perform(multipart("/api/alumnos/crear")
				.file(new MockMultipartFile("file", "foto.jpg", "image/jpeg", new byte[] { 1, 2 }))
				.param("nuevo", "{\"nombre\":\"Prueba\"}")
				.with(admin()).with(csrf()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value("No se pudo leer la imagen del alumno."));
	}

	@Test
	void actualizarAlumnoConFotoHeicResponde400(CapturedOutput salida) throws Exception {
		when(alumnoService.fechaNacimientoValida(any())).thenReturn(true);
		when(alumnoService.datosAlumnoValidos(any(AlumnoDTO.class), anyBoolean())).thenReturn(true);
		when(alumnoService.actualizarAlumno(eq(ALUMNO), any(AlumnoDTO.class), any(), any(MultipartFile.class)))
				.thenThrow(new ArchivoNoValidoException(DocumentoSecurityUtils.MENSAJE_HEIC));
		mockMvc.perform(multipart(HttpMethod.PUT, "/api/alumnos/{id}", ALUMNO)
				.file(new MockMultipartFile("file", "foto.jpg", "image/heic", new byte[] { 1, 2 }))
				.param("alumnoEditado", "{\"nombre\":\"Prueba\"}")
				.with(admin()).with(csrf()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value(DocumentoSecurityUtils.MENSAJE_HEIC));

		assertThat(salida.getOut()).contains("alumnoId=" + ALUMNO).contains("contentType=image/heic");
	}

	@Test
	void actualizarAlumnoConFotoValidaYEmailEnUsoResponde400SinAvisoDeArchivo(CapturedOutput salida)
			throws Exception {
		when(alumnoService.fechaNacimientoValida(any())).thenReturn(true);
		when(alumnoService.datosAlumnoValidos(any(AlumnoDTO.class), anyBoolean())).thenReturn(true);
		when(alumnoService.actualizarAlumno(eq(ALUMNO), any(AlumnoDTO.class), any(), any(MultipartFile.class)))
				.thenThrow(new IllegalArgumentException("El correo electronico ya esta asociado a otro usuario."));
		mockMvc.perform(multipart(HttpMethod.PUT, "/api/alumnos/{id}", ALUMNO)
				.file(new MockMultipartFile("file", "foto.jpg", "image/jpeg", new byte[] { 1, 2 }))
				.param("alumnoEditado", "{\"nombre\":\"Prueba\"}")
				.with(admin()).with(csrf()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value("El correo electronico ya esta asociado a otro usuario."));

		assertThat(salida.getOut()).doesNotContain("rechazad");
	}

	@Test
	void documentoConArgumentoInvalidoQueNoEsDelArchivoResponde400SinAviso(CapturedOutput salida) throws Exception {
		when(alumnoService.agregarDocumentoAAlumno(eq(ALUMNO), any(MultipartFile.class)))
				.thenThrow(new IllegalArgumentException("El alumno debe existir antes de adjuntarle documentos."));
		mockMvc.perform(multipart("/api/alumnos/{id}/documentos", ALUMNO)
				.file(new MockMultipartFile("archivo", "dni.pdf", "application/pdf", new byte[] { 1 }))
				.with(manager()).with(csrf()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value("El alumno debe existir antes de adjuntarle documentos."));

		assertThat(salida.getOut()).doesNotContain("rechazad");
	}

	@Test
	void documentoDeAlumnoInexistenteResponde404() throws Exception {
		when(alumnoService.agregarDocumentoAAlumno(eq(ALUMNO), any(MultipartFile.class)))
				.thenThrow(new AlumnoNoEncontradoException("Alumno no encontrado con ID: " + ALUMNO));
		mockMvc.perform(multipart("/api/alumnos/{id}/documentos", ALUMNO)
				.file(new MockMultipartFile("archivo", "dni.pdf", "application/pdf", new byte[] { 1 }))
				.with(manager()).with(csrf()))
				.andExpect(status().isNotFound());
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
