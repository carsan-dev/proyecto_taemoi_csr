package com.taemoi.project.controladores;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.taemoi.project.config.AuditoriaRequestFilter;
import com.taemoi.project.config.JwtAuthenticationFilter;
import com.taemoi.project.config.MethodSecurityCompatibilityConfig;
import com.taemoi.project.config.OAuth2AuthenticationSuccessHandler;
import com.taemoi.project.config.SecurityConfiguration;
import com.taemoi.project.plataforma.AutoridadPlataforma;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.AuditoriaPayloadSanitizer;
import com.taemoi.project.services.AuditoriaService;
import com.taemoi.project.services.JwtService;
import com.taemoi.project.services.MembresiaService;
import com.taemoi.project.services.UsuarioService;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Spring MVC responde {@code HEAD} con el handler de {@code GET}; las reglas de URL de
 * {@link SecurityConfiguration} deben dar a {@code HEAD} exactamente la misma decisión que a {@code GET} en la
 * misma ruta. Un controlador comodín responde 200 a todo GET, así el estado solo depende de la autorización.
 */
@WebMvcTest(controllers = HeadComoGetSecurityWebMvcTest.ComodinController.class)
@Import({ SecurityConfiguration.class, MethodSecurityCompatibilityConfig.class,
		HeadComoGetSecurityWebMvcTest.FilterTestConfig.class, HeadComoGetSecurityWebMvcTest.ComodinController.class })
@TestPropertySource(properties = {
		"app.base.url=http://localhost:8080",
		"cors.allowed.origin=http://localhost:4200"
})
class HeadComoGetSecurityWebMvcTest {

	/** Ruta → estado esperado de GET para [anónimo, familia, manager, admin, plataforma]. */
	private static final Map<String, int[]> RUTAS = new LinkedHashMap<>();

	static {
		// solo ADMIN
		RUTAS.put("/api/admin/usuarios", new int[] { 401, 403, 403, 200, 403 });
		// ADMIN/MANAGER (regla GET)
		RUTAS.put("/api/alumnos/aptos", new int[] { 401, 403, 200, 200, 403 });
		RUTAS.put("/api/pagos/resumen", new int[] { 401, 403, 200, 200, 403 });
		// familia (regla GET con ROLE_USER)
		RUTAS.put("/api/alumnos/5/grupos", new int[] { 401, 200, 200, 200, 403 });
		RUTAS.put("/api/alumnos/5/imagen", new int[] { 401, 200, 200, 200, 403 });
		// públicas (regla GET)
		RUTAS.put("/api/eventos", new int[] { 200, 200, 200, 200, 200 });
		RUTAS.put("/api/eventos/7/imagen", new int[] { 200, 200, 200, 200, 200 });
		RUTAS.put("/api/turnos/dto", new int[] { 200, 200, 200, 200, 200 });
		RUTAS.put("/api/catalogo/publico", new int[] { 200, 200, 200, 200, 200 });
		RUTAS.put("/healthz", new int[] { 200, 200, 200, 200, 200 });
		// plataforma
		RUTAS.put("/api/plataforma/escuelas", new int[] { 401, 403, 403, 403, 200 });
		// archivos: documento público de evento, documentos protegidos, fotos de alumnos, imágenes públicas
		RUTAS.put("/documentos/Documentos_Eventos/1.pdf", new int[] { 200, 200, 200, 200, 200 });
		RUTAS.put("/documentos/org-1/eventos/1.pdf", new int[] { 200, 200, 200, 200, 200 });
		RUTAS.put("/documentos/Documentos_Alumnos/1.pdf", new int[] { 401, 403, 200, 200, 403 });
		RUTAS.put("/imagenes/alumnos/1.jpg", new int[] { 401, 403, 200, 200, 403 });
		RUTAS.put("/imagenes/org-1/alumnos/1.jpg", new int[] { 401, 403, 200, 200, 403 });
		RUTAS.put("/imagenes/eventos/1.jpg", new int[] { 200, 200, 200, 200, 200 });
		// cualquier otra ruta: solo autenticado
		RUTAS.put("/api/auth/roles", new int[] { 401, 200, 200, 200, 200 });
	}

	private static final String[] PERFILES = { "anonimo", "familia", "manager", "admin", "plataforma" };

	@Autowired
	private MockMvc mockMvc;

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
	void headRecibeLaMismaDecisionQueGet() throws Exception {
		List<String> fallos = new ArrayList<>();
		for (Map.Entry<String, int[]> ruta : RUTAS.entrySet()) {
			for (int i = 0; i < PERFILES.length; i++) {
				int estadoGet = estado(get(ruta.getKey()), i);
				int estadoHead = estado(head(ruta.getKey()), i);
				if (estadoGet != ruta.getValue()[i]) {
					fallos.add("GET " + ruta.getKey() + " [" + PERFILES[i] + "] = " + estadoGet + ", esperado "
							+ ruta.getValue()[i]);
				}
				if (estadoHead != estadoGet) {
					fallos.add("HEAD " + ruta.getKey() + " [" + PERFILES[i] + "] = " + estadoHead + ", GET = "
							+ estadoGet);
				}
			}
		}
		assertThat(fallos).isEmpty();
	}

	@Test
	void elHandlerSigueRecibiendoHead() throws Exception {
		// La reescritura a GET solo la ve la decisión de autorización; la petición que llega al handler es HEAD.
		MockHttpServletResponse respuestaHead = mockMvc.perform(head("/eco-metodo").with(sesion(3)))
				.andReturn().getResponse();
		assertThat(respuestaHead.getStatus()).isEqualTo(200);
		assertThat(respuestaHead.getHeader("X-Metodo")).isEqualTo("HEAD");

		MockHttpServletResponse respuestaGet = mockMvc.perform(get("/eco-metodo").with(sesion(3)))
				.andReturn().getResponse();
		assertThat(respuestaGet.getHeader("X-Metodo")).isEqualTo("GET");
	}

	private int estado(MockHttpServletRequestBuilder peticion, int perfil) throws Exception {
		RequestPostProcessor sesion = sesion(perfil);
		if (sesion != null) {
			peticion.with(sesion);
		}
		return mockMvc.perform(peticion).andReturn().getResponse().getStatus();
	}

	private static RequestPostProcessor sesion(int perfil) {
		return switch (perfil) {
		case 1 -> user("familia@example.com").authorities(() -> "ROLE_USER");
		case 2 -> user("manager@example.com").authorities(() -> "ROLE_MANAGER");
		case 3 -> user("admin@example.com").authorities(() -> "ROLE_ADMIN");
		case 4 -> user("plataforma@example.com").authorities(() -> AutoridadPlataforma.ADMIN_PLATAFORMA);
		default -> null;
		};
	}

	/** Responde 200 a cualquier GET (y por tanto a HEAD): el estado solo depende de la autorización. */
	@RestController
	static class ComodinController {

		@GetMapping("/**")
		String ok() {
			return "ok";
		}

		/** Devuelve en una cabecera el método con el que llega la petición al handler. */
		@GetMapping("/eco-metodo")
		ResponseEntity<String> ecoMetodo(HttpServletRequest request) {
			return ResponseEntity.ok().header("X-Metodo", request.getMethod()).body("ok");
		}
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
