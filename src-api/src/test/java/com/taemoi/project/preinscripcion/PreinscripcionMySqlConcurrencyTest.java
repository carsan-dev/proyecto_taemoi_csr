package com.taemoi.project.preinscripcion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.controllers.PreinscripcionController;
import com.taemoi.project.entities.Deporte;
import com.taemoi.project.exceptions.GlobalExceptionHandler;
import com.taemoi.project.services.AforoPreinscripcionService;
import com.taemoi.project.services.ConfiguracionSistemaService;
import com.taemoi.project.services.EmailService;
import com.taemoi.project.services.PDFService;
import com.taemoi.project.services.PreinscripcionService;
import com.taemoi.project.services.TemporadaService;

@DataJpaTest(properties = {
		"spring.flyway.enabled=true",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.jpa.show-sql=false",
		"spring.datasource.hikari.maximum-pool-size=8"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ PreinscripcionService.class, AforoPreinscripcionService.class,
		PreinscripcionMySqlConcurrencyTest.Config.class })
@EnabledIfSystemProperty(named = "preinscripcion.mysql-it", matches = "true")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PreinscripcionMySqlConcurrencyTest {
	private static final String TEMPORADA = "2026-2027";

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
	long turnoTaekwondo;
	long turnoPilates;
	String firma;

	@BeforeEach
	void setUp() throws Exception {
		jdbc.update("DELETE FROM preinscripcion_turno");
		jdbc.update("DELETE FROM preinscripcion");
		turnoTaekwondo = crearTurnoSiFalta("IT Taekwondo", Deporte.TAEKWONDO);
		turnoPilates = crearTurnoSiFalta("IT Pilates", Deporte.PILATES);
		when(temporadaMock.actual()).thenReturn(TEMPORADA);
		when(temporadaMock.edadCohorte(any(LocalDate.class), anyString())).thenReturn(36);
		when(configuracionMock.obtenerLimiteTurno()).thenReturn(100);
		when(pdfMock.generarPreinscripcionFirmada(any())).thenReturn("pdf-test".getBytes(StandardCharsets.UTF_8));
		firma = firmaValida();
		mvc = MockMvcBuilders.standaloneSetup(new PreinscripcionController(service, temporadas))
				.setControllerAdvice(new GlobalExceptionHandler())
				.setValidator(validator)
				.build();
	}

	@Test
	void dosPeticionesConcurrentesIgualesDevuelvenCreatedYConflict() throws Exception {
		String cuerpo = solicitud("11111111H", Deporte.TAEKWONDO, turnoTaekwondo);

		List<Integer> estados = enviarEnParalelo(
				peticion(cuerpo, "concurrente-identidad-a"),
				peticion(cuerpo, "concurrente-identidad-b"));

		assertEquals(List.of(201, 409), estados);
		assertEquals(1, contarPreinscripciones("11111111H"));
	}

	@Test
	void mismaIdentidadEnDeportesDistintosCreaAmbasPreinscripciones() throws Exception {
		List<Integer> estados = enviarEnParalelo(
				peticion(solicitud("22222222J", Deporte.TAEKWONDO, turnoTaekwondo), "multideporte-a"),
				peticion(solicitud("22222222J", Deporte.PILATES, turnoPilates), "multideporte-b"));

		assertEquals(List.of(201, 201), estados);
		assertEquals(2, contarPreinscripciones("22222222J"));
	}

	@Test
	void reusarIdempotencyKeyDevuelveConflictYGuardaSoloHash() throws Exception {
		String cuerpo = solicitud("33333333P", Deporte.TAEKWONDO, turnoTaekwondo);
		String clave = "reintento-cliente-1";

		assertEquals(201, ejecutar(peticion(cuerpo, clave)));
		assertEquals(409, ejecutar(peticion(cuerpo, clave)));
		String hashGuardado = jdbc.queryForObject(
				"SELECT idempotency_key_hash FROM preinscripcion WHERE dni = ?", String.class, "33333333P");

		assertEquals(sha256(clave), hashGuardado);
		assertNotEquals(clave, hashGuardado);
		assertEquals(1, contarPreinscripciones("33333333P"));
	}

	@Test
	void idempotencyKeyConcurrentePermiteSoloUnaPeticion() throws Exception {
		String clave = "concurrente-idempotencia";

		List<Integer> estados = enviarEnParalelo(
				peticion(solicitud("44444444A", Deporte.TAEKWONDO, turnoTaekwondo), clave),
				peticion(solicitud("55555555K", Deporte.PILATES, turnoPilates), clave));

		assertEquals(List.of(201, 409), estados);
		assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM preinscripcion", Integer.class));
	}

	@Test
	void flywayAplicaMigracionesYActivaLasNormasCompletasDeKickboxing() {
		assertEquals(1, migracionAplicada("8"));
		assertEquals(1, migracionAplicada("16"));
		assertEquals(1, migracionAplicada("17"));
		assertTrue(indiceUnicoPresente("uk_preinscripcion_activa"));
		assertTrue(indiceUnicoPresente("uk_preinscripcion_idempotencia"));
		assertEquals(1, jdbc.queryForObject(
				"SELECT COUNT(*) FROM plantilla_preinscripcion WHERE deporte='KICKBOXING' AND activa=1",
				Integer.class));
		assertEquals(11, jdbc.queryForObject("""
				SELECT JSON_LENGTH(JSON_EXTRACT(contenido, '$.normas'))
				FROM plantilla_preinscripcion
				WHERE deporte='KICKBOXING' AND activa=1
				""", Integer.class));
		String contenidoKickboxing = jdbc.queryForObject("""
				SELECT contenido FROM plantilla_preinscripcion
				WHERE deporte='KICKBOXING' AND activa=1
				""", String.class);
		assertTrue(contenidoKickboxing.contains("54,95 €"));
		assertTrue(contenidoKickboxing.contains("71,95 €"));
		assertTrue(contenidoKickboxing.contains("mantenimiento de 10 €"));
		assertTrue(contenidoKickboxing.contains("recargo de 5 €"));
		assertTrue(contenidoKickboxing.contains("matrícula de 20 €"));
	}

	private long crearTurnoSiFalta(String nombreGrupo, Deporte deporte) {
		List<Long> existentes = jdbc.queryForList(
				"SELECT t.id FROM turno t JOIN grupo g ON g.id=t.grupo_id WHERE g.nombre=? ORDER BY t.id LIMIT 1",
				Long.class, nombreGrupo);
		if (!existentes.isEmpty()) return existentes.get(0);
		jdbc.update("INSERT INTO grupo(nombre,tipo,deporte,rango_edad_min,rango_edad_max) VALUES (?,NULL,?,0,99)",
				nombreGrupo, deporte.name());
		Long grupoId = jdbc.queryForObject("SELECT id FROM grupo WHERE nombre=?", Long.class, nombreGrupo);
		jdbc.update("INSERT INTO turno(dia_semana,hora_inicio,hora_fin,tipo,grupo_id) VALUES ('Lunes','18:00','19:00',NULL,?)",
				grupoId);
		return jdbc.queryForObject("SELECT id FROM turno WHERE grupo_id=?", Long.class, grupoId);
	}

	private List<Integer> enviarEnParalelo(Peticion primera, Peticion segunda) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch preparados = new CountDownLatch(2);
		CountDownLatch salida = new CountDownLatch(1);
		try {
			List<Future<Integer>> futuros = new ArrayList<>();
			for (Peticion peticion : List.of(primera, segunda)) {
				futuros.add(executor.submit(() -> {
					preparados.countDown();
					if (!salida.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("No arrancaron ambas peticiones.");
					return ejecutar(peticion);
				}));
			}
			assertTrue(preparados.await(10, TimeUnit.SECONDS));
			salida.countDown();
			List<Integer> estados = new ArrayList<>();
			for (Future<Integer> futuro : futuros) estados.add(futuro.get(30, TimeUnit.SECONDS));
			estados.sort(Integer::compareTo);
			return estados;
		} finally {
			salida.countDown();
			executor.shutdownNow();
		}
	}

	private int ejecutar(Peticion peticion) throws Exception {
		MvcResult resultado = mvc.perform(post("/api/preinscripciones")
				.header("Idempotency-Key", peticion.idempotencyKey())
				.contentType("application/json")
				.content(peticion.cuerpo()))
				.andReturn();
		return resultado.getResponse().getStatus();
	}

	private Peticion peticion(String cuerpo, String idempotencyKey) {
		return new Peticion(cuerpo, idempotencyKey);
	}

	private String solicitud(String dni, Deporte deporte, long turnoId) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("deporte", deporte.name());
		body.put("turnoIds", List.of(turnoId));
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
		body.put("firmaBase64", firma);
		return json.writeValueAsString(body);
	}

	private String firmaValida() throws Exception {
		BufferedImage imagen = new BufferedImage(320, 100, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = imagen.createGraphics();
		graphics.setColor(Color.WHITE);
		graphics.fillRect(0, 0, imagen.getWidth(), imagen.getHeight());
		graphics.setColor(Color.BLACK);
		graphics.drawString("Firma de prueba de integración", 20, 50);
		graphics.dispose();
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		ImageIO.write(imagen, "png", bytes);
		return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes.toByteArray());
	}

	private int contarPreinscripciones(String dni) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM preinscripcion WHERE dni=?", Integer.class, dni);
	}

	private int migracionAplicada(String version) {
		return jdbc.queryForObject(
				"SELECT COUNT(*) FROM flyway_schema_history WHERE version=? AND success=1", Integer.class, version);
	}

	private boolean indiceUnicoPresente(String nombre) {
		Integer total = jdbc.queryForObject("""
				SELECT COUNT(*) FROM information_schema.statistics
				WHERE table_schema=DATABASE() AND table_name='preinscripcion' AND index_name=? AND non_unique=0
				""", Integer.class, nombre);
		return total != null && total > 0;
	}

	private String sha256(String value) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
				.digest(value.getBytes(StandardCharsets.UTF_8)));
	}

	private record Peticion(String cuerpo, String idempotencyKey) {}

	@TestConfiguration
	static class Config {
		@Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }
		@Bean LocalValidatorFactoryBean validator() { return new LocalValidatorFactoryBean(); }
	}
}
