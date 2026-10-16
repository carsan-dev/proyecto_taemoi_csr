package com.taemoi.project.controladores;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.config.AuditoriaRequestFilter;
import com.taemoi.project.config.JwtAuthenticationFilter;
import com.taemoi.project.config.MethodSecurityCompatibilityConfig;
import com.taemoi.project.config.OAuth2AuthenticationSuccessHandler;
import com.taemoi.project.config.SecurityConfiguration;
import com.taemoi.project.controllers.EmailController;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.AuditoriaPayloadSanitizer;
import com.taemoi.project.services.AuditoriaService;
import com.taemoi.project.services.EmailService;
import com.taemoi.project.services.JwtService;
import com.taemoi.project.services.MembresiaService;
import com.taemoi.project.services.UsuarioService;

/**
 * Formulario de contacto público ({@code POST /api/mail/enviar}): el texto del visitante llega escapado al
 * HTML del correo, sin saltos de línea en el asunto, y los límites de tamaño devuelven 400.
 */
@WebMvcTest(controllers = EmailController.class)
@Import({ SecurityConfiguration.class, MethodSecurityCompatibilityConfig.class,
		EmailControllerWebMvcTest.FilterTestConfig.class })
@TestPropertySource(properties = {
		"app.base.url=http://localhost:8080",
		"cors.allowed.origin=http://localhost:4200"
})
class EmailControllerWebMvcTest {

	private static final String DESTINO = "moiskimdotaekwondo@gmail.com";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private EmailService emailService;

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

	@Test
	void mensajeNormalLlegaIgualConSaltosDeLineaComoBr() throws Exception {
		enviar(payload("Lucía", "Pérez Gómez", "lucia@example.com", "Información clases",
				"Hola, quería información.\nDeportes de interes: Taekwondo"))
				.andExpect(status().isOk());

		ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
		verify(emailService).sendEmail(eq(DESTINO), eq("Nuevo mensaje de Lucía Pérez Gómez"), html.capture());
		assertThat(html.getValue())
				.contains("Nuevo mensaje de Lucía Pérez Gómez")
				.contains("<div>Lucía</div>")
				.contains("<div>Pérez Gómez</div>")
				.contains("<div>lucia@example.com</div>")
				.contains("<div>Información clases</div>")
				.contains("<div>Hola, quería información.<br>Deportes de interes: Taekwondo</div>");
	}

	@Test
	void htmlDelVisitanteLlegaEscapado() throws Exception {
		enviar(payload("<b>Ana</b>", "<script>alert(1)</script>", "ana@example.com",
				"<img src=x onerror=alert(1)>",
				"Pulsa <a href=\"https://malo.example\">aquí</a> & 'ya'\r\n<script>robar()</script>"))
				.andExpect(status().isOk());

		ArgumentCaptor<String> asunto = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
		verify(emailService).sendEmail(eq(DESTINO), asunto.capture(), html.capture());

		assertThat(html.getValue())
				.doesNotContain("<a href")
				.doesNotContain("<script>")
				.doesNotContain("<img src=x")
				.doesNotContain("<b>Ana</b>")
				.contains("&lt;b&gt;Ana&lt;/b&gt;")
				.contains("&lt;script&gt;alert(1)&lt;/script&gt;")
				.contains("&lt;img src=x onerror=alert(1)&gt;")
				.contains("Pulsa &lt;a href=&quot;https://malo.example&quot;&gt;aquí&lt;/a&gt; &amp; &#39;ya&#39;<br>"
						+ "&lt;script&gt;robar()&lt;/script&gt;");
		// El asunto es texto plano (cabecera): sin escapar, pero sin saltos de línea.
		assertThat(asunto.getValue()).isEqualTo("Nuevo mensaje de <b>Ana</b> <script>alert(1)</script>");
	}

	@Test
	void saltosDeLineaEnCamposDeUnaLineaNoLleganALaCabecera() throws Exception {
		enviar(payload("Ana\r\nBcc: victima@example.com", "Ruiz\nX-Inyectada: si", "ana@example.com",
				"Asunto\r\nBcc: otra@example.com", "Mensaje"))
				.andExpect(status().isOk());

		ArgumentCaptor<String> asunto = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
		verify(emailService).sendEmail(eq(DESTINO), asunto.capture(), html.capture());

		assertThat(asunto.getValue())
				.doesNotContain("\r").doesNotContain("\n")
				.isEqualTo("Nuevo mensaje de Ana Bcc: victima@example.com Ruiz X-Inyectada: si");
		assertThat(html.getValue())
				.contains("<div>Asunto Bcc: otra@example.com</div>")
				.doesNotContain("Asunto\r\n");
	}

	@Test
	void camposDemasiadoLargosDevuelven400() throws Exception {
		enviar(payload("A".repeat(51), "Ruiz", "ana@example.com", "Asunto", "Mensaje"))
				.andExpect(status().isBadRequest());
		enviar(payload("Ana", "R".repeat(51), "ana@example.com", "Asunto", "Mensaje"))
				.andExpect(status().isBadRequest());
		enviar(payload("Ana", "Ruiz", "a".repeat(60) + "@" + "b".repeat(60) + "." + "c".repeat(60) + ".com", "Asunto", "Mensaje"))
				.andExpect(status().isBadRequest());
		enviar(payload("Ana", "Ruiz", "ana@example.com", "S".repeat(101), "Mensaje"))
				.andExpect(status().isBadRequest());
		enviar(payload("Ana", "Ruiz", "ana@example.com", "Asunto", "M".repeat(501)))
				.andExpect(status().isBadRequest());
		enviar(payload("Ana", "Ruiz", "no-es-un-email", "Asunto", "Mensaje"))
				.andExpect(status().isBadRequest());

		verifyNoInteractions(emailService);
	}

	@Test
	void sigueSiendoPublico() throws Exception {
		enviar(payload("Ana", "Ruiz", "ana@example.com", "Asunto", "Mensaje"))
				.andExpect(status().isOk());
		verify(emailService).sendEmail(eq(DESTINO), anyString(), anyString());
	}

	private org.springframework.test.web.servlet.ResultActions enviar(Map<String, String> cuerpo) throws Exception {
		return mockMvc.perform(post("/api/mail/enviar")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(cuerpo)));
	}

	private static Map<String, String> payload(String nombre, String apellidos, String email, String asunto,
			String mensaje) {
		Map<String, String> cuerpo = new LinkedHashMap<>();
		cuerpo.put("nombre", nombre);
		cuerpo.put("apellidos", apellidos);
		cuerpo.put("email", email);
		cuerpo.put("asunto", asunto);
		cuerpo.put("mensaje", mensaje);
		return cuerpo;
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
