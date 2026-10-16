package com.taemoi.project.preinscripcion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.controllers.PreinscripcionController;
import com.taemoi.project.exceptions.GlobalExceptionHandler;
import com.taemoi.project.integracion.MySqlIntegrationTest;
import com.taemoi.project.legado.Deporte;
import com.taemoi.project.organizacion.IdentificadorSesion;
import com.taemoi.project.organizacion.OrganizacionHibernateConfig;
import com.taemoi.project.organizacion.OrganizacionRequestFilter;
import com.taemoi.project.organizacion.ResolutorOrganizacion;
import com.taemoi.project.services.AforoPreinscripcionService;
import com.taemoi.project.services.ConfiguracionSistemaService;
import com.taemoi.project.services.ConsentimientoService;
import com.taemoi.project.services.ContadorService;
import com.taemoi.project.services.EmailService;
import com.taemoi.project.services.PDFService;
import com.taemoi.project.services.PreinscripcionCancelacionEmailListener;
import com.taemoi.project.services.PreinscripcionCancelacionEmailService;
import com.taemoi.project.services.PreinscripcionService;
import com.taemoi.project.services.TemporadaService;
import com.taemoi.project.services.TutorService;

/**
 * Cancelación con motivo contra MySQL real: V31_1 aplicada, el motivo se guarda y el aviso a la familia se envía
 * tras el commit (listener real); si el SMTP falla, la cancelación queda confirmada y el error registrado.
 */
@DataJpaTest(properties = {
		"spring.flyway.enabled=true",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ PreinscripcionService.class, AforoPreinscripcionService.class, ContadorService.class,
		TutorService.class, ConsentimientoService.class,
		PreinscripcionCancelacionEmailService.class, PreinscripcionCancelacionEmailListener.class,
		com.taemoi.project.services.impl.CatalogoDisciplinasServiceImpl.class,
		com.taemoi.project.services.impl.TarifasServiceImpl.class,
		com.taemoi.project.services.impl.ProgresionGradosServiceImpl.class,
		OrganizacionHibernateConfig.class, PreinscripcionCancelacionMySqlIntegrationTest.Config.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PreinscripcionCancelacionMySqlIntegrationTest extends MySqlIntegrationTest {
	private static final String TEMPORADA = "2026-2027";
	private static final String ASUNTO = "Tu preinscripción %s ha sido cancelada";

	@Autowired PreinscripcionService service;
	@Autowired TemporadaService temporadas;
	@Autowired ObjectMapper json;
	@Autowired LocalValidatorFactoryBean validator;
	@Autowired JdbcTemplate jdbc;

	@MockitoBean TemporadaService temporadaMock;
	@MockitoBean ConfiguracionSistemaService configuracionMock;
	@MockitoBean PDFService pdfMock;
	@MockitoBean EmailService emailMock;

	MockMvc mvc;
	long turno;

	@BeforeEach
	void setUp() throws Exception {
		jdbc.update("DELETE FROM preinscripcion_turno");
		jdbc.update("DELETE FROM preinscripcion");
		turno = crearTurnoSiFalta("IT Cancelacion Taekwondo");
		when(temporadaMock.actual()).thenReturn(TEMPORADA);
		when(temporadaMock.edadCohorte(any(LocalDate.class), anyString())).thenReturn(36);
		when(configuracionMock.obtenerLimiteTurno()).thenReturn(100);
		when(pdfMock.generarPreinscripcionFirmada(any())).thenReturn("pdf-test".getBytes(StandardCharsets.UTF_8));
		mvc = MockMvcBuilders.standaloneSetup(new PreinscripcionController(service, temporadas))
				.addFilters(new OrganizacionRequestFilter(new ResolutorOrganizacion(1L), IdentificadorSesion.ANONIMO))
				.setControllerAdvice(new GlobalExceptionHandler())
				.setValidator(validator)
				.build();
	}

	@Test
	void migracionV31_1AnadeColumnasNulablesTrasV31() {
		assertEquals(1, jdbc.queryForObject(
				"SELECT COUNT(*) FROM flyway_schema_history WHERE version='31.1' AND success=1", Integer.class));
		Integer orden31 = jdbc.queryForObject(
				"SELECT installed_rank FROM flyway_schema_history WHERE version='31'", Integer.class);
		Integer orden311 = jdbc.queryForObject(
				"SELECT installed_rank FROM flyway_schema_history WHERE version='31.1'", Integer.class);
		assertTrue(orden311 > orden31, "V31_1 debe aplicarse después de V31");
		List<Map<String, Object>> columnas = jdbc.queryForList("""
				SELECT column_name AS nombre, column_type AS tipo, is_nullable AS nulable, column_default AS defecto
				FROM information_schema.columns
				WHERE table_schema=DATABASE() AND table_name='preinscripcion'
				  AND column_name IN ('motivo_cancelacion','email_cancelacion_estado','email_cancelacion_intentos',
				                      'email_cancelacion_enviado_en','email_cancelacion_ultimo_error')
				ORDER BY column_name
				""");
		assertEquals(5, columnas.size());
		Map<String, String> tipos = new LinkedHashMap<>();
		for (Map<String, Object> c : columnas) {
			assertEquals("YES", c.get("nulable"), "nullable: " + c.get("nombre"));
			assertNull(c.get("defecto"), "sin valor por defecto: " + c.get("nombre"));
			tipos.put((String) c.get("nombre"), (String) c.get("tipo"));
		}
		assertEquals("varchar(500)", tipos.get("motivo_cancelacion"));
		assertEquals("varchar(20)", tipos.get("email_cancelacion_estado"));
		assertEquals("int", tipos.get("email_cancelacion_intentos"));
		assertEquals("datetime(6)", tipos.get("email_cancelacion_enviado_en"));
		assertEquals("varchar(500)", tipos.get("email_cancelacion_ultimo_error"));
	}

	@Test
	void cancelarConMotivoLoGuardaYEnviaElAvisoTrasElCommit() throws Exception {
		String referencia = crearPreinscripcion("66666666Q");

		mvc.perform(post("/api/preinscripciones/" + referencia + "/cancelar")
				.contentType("application/json").content("{\"motivo\":\"El grupo se ha cerrado. <b>Gracias</b>\"}"))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());

		Map<String, Object> fila = fila(referencia);
		assertEquals("CANCELADA", fila.get("estado"));
		assertNotNull(fila.get("cancelada_en"));
		assertEquals("El grupo se ha cerrado. <b>Gracias</b>", fila.get("motivo_cancelacion"));
		assertEquals("ENVIADO", fila.get("email_cancelacion_estado"));
		assertEquals(1, ((Number) fila.get("email_cancelacion_intentos")).intValue());
		assertNotNull(fila.get("email_cancelacion_enviado_en"));
		assertNull(fila.get("email_cancelacion_ultimo_error"));
		verify(emailMock).sendEmailSync(eq("ana@example.com"), eq(ASUNTO.formatted(referencia)),
				contains("El grupo se ha cerrado. &lt;b&gt;Gracias&lt;/b&gt;"));
	}

	@Test
	void cancelarSinCuerpoGuardaElMotivoPorDefecto() throws Exception {
		String referencia = crearPreinscripcion("77777777B");

		mvc.perform(post("/api/preinscripciones/" + referencia + "/cancelar"))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());

		assertEquals(PreinscripcionService.MOTIVO_CANCELACION_POR_DEFECTO, fila(referencia).get("motivo_cancelacion"));
		verify(emailMock).sendEmailSync(eq("ana@example.com"), eq(ASUNTO.formatted(referencia)),
				contains(PreinscripcionService.MOTIVO_CANCELACION_POR_DEFECTO));
	}

	@Test
	void falloDelCorreoNoDeshaceLaCancelacionYQuedaRegistrado() throws Exception {
		String referencia = crearPreinscripcion("88888888Y");
		doThrow(new IllegalStateException("SMTP caído")).when(emailMock)
				.sendEmailSync(anyString(), eq(ASUNTO.formatted(referencia)), anyString());

		mvc.perform(post("/api/preinscripciones/" + referencia + "/cancelar")
				.contentType("application/json").content("{\"motivo\":\"Sin plaza\"}"))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());

		Map<String, Object> fila = fila(referencia);
		assertEquals("CANCELADA", fila.get("estado"));
		assertEquals("Sin plaza", fila.get("motivo_cancelacion"));
		assertEquals("ERROR", fila.get("email_cancelacion_estado"));
		assertEquals(1, ((Number) fila.get("email_cancelacion_intentos")).intValue());
		assertNull(fila.get("email_cancelacion_enviado_en"));
		assertEquals("SMTP caído", fila.get("email_cancelacion_ultimo_error"));
	}

	@Test
	void motivoDemasiadoLargoDevuelve400YNoCancela() throws Exception {
		String referencia = crearPreinscripcion("99999999R");

		mvc.perform(post("/api/preinscripciones/" + referencia + "/cancelar")
				.contentType("application/json").content("{\"motivo\":\"" + "x".repeat(501) + "\"}"))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());

		Map<String, Object> fila = fila(referencia);
		assertEquals("PENDIENTE", fila.get("estado"));
		assertNull(fila.get("motivo_cancelacion"));
		assertEquals("NO_ENVIADO", fila.get("email_cancelacion_estado"));
		assertEquals(0, ((Number) fila.get("email_cancelacion_intentos")).intValue());
	}

	private Map<String, Object> fila(String referencia) {
		return jdbc.queryForMap("""
				SELECT estado, cancelada_en, motivo_cancelacion, email_cancelacion_estado, email_cancelacion_intentos,
				       email_cancelacion_enviado_en, email_cancelacion_ultimo_error
				FROM preinscripcion WHERE referencia=?
				""", referencia);
	}

	private String crearPreinscripcion(String dni) throws Exception {
		String respuesta = mvc.perform(post("/api/preinscripciones")
				.header("Idempotency-Key", "cancelacion-" + dni)
				.contentType("application/json")
				.content(solicitud(dni)))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return json.readTree(respuesta).get("referencia").asText();
	}

	private long crearTurnoSiFalta(String nombreGrupo) {
		List<Long> existentes = jdbc.queryForList(
				"SELECT t.id FROM turno t JOIN grupo g ON g.id=t.grupo_id WHERE g.nombre=? ORDER BY t.id LIMIT 1",
				Long.class, nombreGrupo);
		if (!existentes.isEmpty()) return existentes.get(0);
		jdbc.update("INSERT INTO grupo(nombre,tipo,rango_edad_min,rango_edad_max,organizacion_id,disciplina_id) VALUES (?,NULL,0,99,1,"
				+ "(SELECT d.id FROM disciplina d WHERE d.organizacion_id=1 AND d.codigo=?))",
				nombreGrupo, Deporte.TAEKWONDO.name());
		Long grupoId = jdbc.queryForObject("SELECT id FROM grupo WHERE nombre=?", Long.class, nombreGrupo);
		jdbc.update("INSERT INTO turno(dia_semana,hora_inicio,hora_fin,tipo,grupo_id,organizacion_id) VALUES ('Martes','18:00','19:00',NULL,?,1)",
				grupoId);
		return jdbc.queryForObject("SELECT id FROM turno WHERE grupo_id=?", Long.class, grupoId);
	}

	private String solicitud(String dni) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("deporte", Deporte.TAEKWONDO.name());
		body.put("turnoIds", List.of(turno));
		body.put("nombre", "Ana");
		body.put("apellidos", "García López");
		body.put("dni", dni);
		body.put("fechaNacimiento", "1990-01-01");
		body.put("direccion", "Calle Mayor 1");
		body.put("telefono", "612345678");
		body.put("telefono2", "");
		body.put("email", "ana@example.com");
		body.put("observaciones", "");
		body.put("tutorNombre", "");
		body.put("tutorDni", "");
		body.put("tieneDiscapacidad", false);
		body.put("consentimientoFotografico", false);
		body.put("aceptacionNormas", true);
		body.put("firmanteNombre", "Ana García");
		body.put("firmaBase64", firmaValida());
		return json.writeValueAsString(body);
	}

	private String firmaValida() throws Exception {
		BufferedImage imagen = new BufferedImage(320, 100, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = imagen.createGraphics();
		graphics.setColor(Color.WHITE);
		graphics.fillRect(0, 0, imagen.getWidth(), imagen.getHeight());
		graphics.setColor(Color.BLACK);
		graphics.drawString("Firma de prueba de cancelación", 20, 50);
		graphics.dispose();
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		ImageIO.write(imagen, "png", bytes);
		return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes.toByteArray());
	}

	@TestConfiguration
	static class Config {
		@Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }
		@Bean LocalValidatorFactoryBean validator() { return new LocalValidatorFactoryBean(); }
	}
}
