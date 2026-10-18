package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.dtos.request.PreinscripcionRequest;
import com.taemoi.project.entities.Preinscripcion;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.PreinscripcionRepository;
import com.taemoi.project.services.EmailService;
import com.taemoi.project.services.PreinscripcionService;

/**
 * R2-P7d: el correo inicial a la familia (con el PDF firmado adjunto) y el aviso interno del alta pública salen tras el
 * commit del alta, con el mismo contenido de siempre. Contra MySQL real y con el alta de verdad:
 * <ul>
 * <li>si la transacción del alta se deshace, no sale ningún correo (antes salían con la solicitud ya deshecha);</li>
 * <li>tras el commit salen los dos, en el orden de siempre (familia, administración), con el asunto y el cuerpo que
 * generaba el alta y el adjunto byte a byte igual al PDF guardado; la fila queda con {@code emailEnviado}, intentos y
 * último error como antes; la respuesta del alta es la misma salvo {@code emailEnviado:false} (aún pendiente);</li>
 * <li>un fallo del SMTP no deshace el alta y queda registrado en la fila, como antes;</li>
 * <li>una promoción que coincide con el envío no pisa el seguimiento que deja el listener.</li>
 * </ul>
 * Datos sintéticos: un grupo y un turno propios (ids 8.270.000 / 8.370.000) en la organización 1; las altas cuelgan de
 * ese grupo y se borran al terminar cada test. Mismas propiedades y simulaciones que
 * {@link PreinscripcionBinariosIntegrationTest} (comparten contexto de Spring).
 */
@SpringBootTest(properties = {
		"spring.jpa.properties.hibernate.session_factory.statement_inspector=com.taemoi.project.integracion.ContadorSentencias",
		"app.preinscripciones.admin-email=" + AforoPreinscripcionesIntegrationTest.ADMIN })
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PreinscripcionCorreosAltaIntegrationTest extends MySqlIntegrationTest {

	private static final long GRUPO = 8_270_000L;
	private static final long TURNO = 8_370_000L;
	private static final String CORREO = "correos-alta-r2p7d@example.test";
	private static final String ADMIN = AforoPreinscripcionesIntegrationTest.ADMIN;

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired PlatformTransactionManager txManager;
	@Autowired PreinscripcionService preinscripcionService;
	@Autowired PreinscripcionRepository preinscripciones;

	@MockitoBean EmailService email;

	int ipSecuencia = 1;
	int dniSecuencia = 47_300_000;

	@BeforeAll
	void preparar() {
		borrar();
		long tkd = jdbc.queryForObject("SELECT id FROM disciplina WHERE organizacion_id = 1 AND codigo = 'TAEKWONDO'",
				Long.class);
		jdbc.update("""
				INSERT INTO grupo (id, nombre, tipo, rango_edad_min, rango_edad_max, organizacion_id, disciplina_id, sede_id)
				VALUES (?, 'CORREOS-R2P7D Adultos', NULL, NULL, NULL, 1, ?, NULL)""", GRUPO, tkd);
		jdbc.update("""
				INSERT INTO turno (id, dia_semana, hora_inicio, hora_fin, tipo, grupo_id, organizacion_id)
				VALUES (?, 'Sábado', '12:00', '13:00', NULL, ?, 1)""", TURNO, GRUPO);
	}

	@BeforeEach
	void limpiarCorreos() {
		Mockito.reset(email);
	}

	@AfterEach
	void borrarAltas() {
		Mockito.reset(email);
		jdbc.update("DELETE FROM preinscripcion_turno WHERE turno_id = ?", TURNO);
		jdbc.update("DELETE FROM preinscripcion WHERE grupo_id = ?", GRUPO);
	}

	@AfterAll
	void retirar() {
		borrar();
	}

	@Test
	void siLaTransaccionDelAltaSeDeshaceNoSaleNingunCorreo() throws Exception {
		PreinscripcionRequest solicitud = solicitud(dniSecuencia++);

		TenantContext.ejecutarCon(1L, () -> new TransactionTemplate(txManager).executeWithoutResult(s -> {
			preinscripcionService.crear(solicitud);
			s.setRollbackOnly();
		}));

		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM preinscripcion WHERE grupo_id = ?", Integer.class, GRUPO));
		verify(email, never()).sendEmailConAdjunto(anyString(), anyString(), anyString(), anyString(), any());
		verify(email, never()).sendEmail(anyString(), anyString(), anyString());
		verify(email, never()).sendEmailSync(anyString(), anyString(), anyString());
	}

	@Test
	void trasElCommitSalenLosDosCorreosDeSiempreConElPdfGuardado() throws Exception {
		MvcResult r = alta(dniSecuencia++);

		assertEquals(201, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		JsonNode respuesta = json.readTree(r.getResponse().getContentAsString());
		String ref = respuesta.get("referencia").asText();
		List<String> campos = new java.util.ArrayList<>();
		respuesta.fieldNames().forEachRemaining(campos::add);
		assertEquals(List.of("referencia", "temporada", "tokenDocumento", "emailEnviado", "estado", "plazaAsignada",
				"posicionListaEspera", "mensaje"), campos);
		assertFalse(respuesta.get("emailEnviado").asBoolean(), "la respuesta se construye antes del envío: pendiente");
		assertEquals("PENDIENTE", respuesta.get("estado").asText());
		assertEquals("Preinscripción recibida con plaza asignada.", respuesta.get("mensaje").asText());

		// Mismo contenido que generaba el alta: se recalcula con los mismos métodos sobre la solicitud guardada.
		Esperado esperado = esperado(ref);
		byte[] pdf = jdbc.queryForObject("SELECT pdf_firmado FROM preinscripcion WHERE referencia = ?", byte[].class, ref);
		ArgumentCaptor<byte[]> adjunto = ArgumentCaptor.forClass(byte[].class);
		InOrder orden = inOrder(email);
		orden.verify(email).sendEmailConAdjunto(eq(CORREO), eq(esperado.asunto()), eq(esperado.html()),
				eq("preinscripcion-" + ref + ".pdf"), adjunto.capture());
		orden.verify(email).sendEmail(eq(ADMIN), eq("Nueva preinscripción recibida: " + ref), eq(esperado.htmlAdmin()));
		Mockito.verifyNoMoreInteractions(email);
		assertArrayEquals(pdf, adjunto.getValue());
		assertEquals("Plaza asignada · " + ref, esperado.asunto());

		Map<String, Object> fila = fila(ref);
		assertEquals(Boolean.TRUE, fila.get("enviado"));
		assertEquals(1, ((Number) fila.get("intentos")).intValue());
		assertNull(fila.get("error"));
	}

	@Test
	void unFalloDelSmtpNoDeshaceElAltaYQuedaRegistrado() throws Exception {
		doThrow(new IllegalStateException("No se pudo enviar el correo")).when(email)
				.sendEmailConAdjunto(anyString(), anyString(), anyString(), anyString(), any());

		MvcResult r = alta(dniSecuencia++);

		assertEquals(201, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		JsonNode respuesta = json.readTree(r.getResponse().getContentAsString());
		String ref = respuesta.get("referencia").asText();
		assertFalse(respuesta.get("emailEnviado").asBoolean());
		Map<String, Object> fila = fila(ref);
		assertNotNull(fila, "el alta se mantiene");
		assertEquals(Boolean.FALSE, fila.get("enviado"));
		assertEquals(1, ((Number) fila.get("intentos")).intValue());
		assertEquals("No se pudo enviar el correo", fila.get("error"));
		// El aviso interno sale igual, como antes.
		verify(email).sendEmail(eq(ADMIN), eq("Nueva preinscripción recibida: " + ref), anyString());
	}

	/**
	 * Un alta entra en lista de espera y, mientras su correo inicial está en el SMTP (el listener tiene la fila bloqueada),
	 * una cancelación libera la plaza y la promociona. La promoción leyó la fila sin bloqueo (0 intentos, no enviado) y su
	 * UPDATE espera al listener: no debe pisar lo que este acaba de registrar (correo enviado, 1 intento).
	 */
	@Test
	void unaPromocionMientrasSaleElCorreoInicialNoPisaSuSeguimiento() throws Exception {
		String limiteOriginal = jdbc.query("SELECT valor FROM configuracion_organizacion WHERE organizacion_id = 1 AND "
				+ "clave = 'limite_turno'", rs -> rs.next() ? rs.getString(1) : null);
		java.util.concurrent.CountDownLatch enSmtp = new java.util.concurrent.CountDownLatch(1);
		java.util.concurrent.CountDownLatch soltar = new java.util.concurrent.CountDownLatch(1);
		Mockito.doAnswer(inv -> {
			enSmtp.countDown();
			if (!soltar.await(30, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("SMTP retenido");
			return null;
		}).when(email).sendEmailConAdjunto(anyString(), org.mockito.ArgumentMatchers.startsWith("Lista de espera · "),
				anyString(), anyString(), any());
		java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);
		try {
			fijarLimiteTurno("1");
			MvcResult conPlaza = alta(dniSecuencia++);
			assertEquals(201, conPlaza.getResponse().getStatus(), conPlaza.getResponse().getContentAsString());
			String refPlaza = json.readTree(conPlaza.getResponse().getContentAsString()).get("referencia").asText();

			int dniEspera = dniSecuencia++;
			java.util.concurrent.Future<MvcResult> espera = executor.submit(() -> alta(dniEspera));
			assertTrue(enSmtp.await(30, java.util.concurrent.TimeUnit.SECONDS), "el correo inicial no llegó al SMTP");
			String refEspera = jdbc.queryForObject("SELECT referencia FROM preinscripcion WHERE dni = ?", String.class,
					dni(dniEspera));
			assertEquals("EN_LISTA_ESPERA", estado(refEspera));

			java.util.concurrent.Future<?> cancelacion = executor.submit(() -> TenantContext.ejecutarCon(1L,
					() -> preinscripcionService.cancelar(refPlaza, null)));
			esperarBloqueo(); // la promoción espera la fila que tiene el listener
			soltar.countDown();
			cancelacion.get(30, java.util.concurrent.TimeUnit.SECONDS);
			MvcResult r = espera.get(30, java.util.concurrent.TimeUnit.SECONDS);
			assertEquals(201, r.getResponse().getStatus(), r.getResponse().getContentAsString());

			assertEquals("CANCELADA", estado(refPlaza));
			assertEquals("PENDIENTE", estado(refEspera));
			Map<String, Object> fila = fila(refEspera);
			assertEquals(Boolean.TRUE, fila.get("enviado"), fila.toString());
			assertEquals(1, ((Number) fila.get("intentos")).intValue(), fila.toString());
			assertNull(fila.get("error"));
		} finally {
			soltar.countDown();
			executor.shutdownNow();
			fijarLimiteTurno(limiteOriginal);
		}
	}

	// ------------------------------------------------------------------ utilidades

	private String estado(String ref) {
		return jdbc.queryForObject("SELECT estado FROM preinscripcion WHERE referencia = ?", String.class, ref);
	}

	/** Espera (máx. 15 s) a que alguna transacción esté esperando un bloqueo de InnoDB. */
	private void esperarBloqueo() throws InterruptedException {
		long limite = System.currentTimeMillis() + 15_000;
		while (System.currentTimeMillis() < limite) {
			long n = Long.parseLong(String.valueOf(
					jdbc.queryForMap("SHOW GLOBAL STATUS LIKE 'Innodb_row_lock_current_waits'").get("Value")));
			if (n > 0) return;
			Thread.sleep(50);
		}
		throw new IllegalStateException("La promoción no llegó a esperar la fila del listener.");
	}

	private void fijarLimiteTurno(String valor) {
		jdbc.update("DELETE FROM configuracion_organizacion WHERE organizacion_id = 1 AND clave = 'limite_turno'");
		if (valor != null) {
			jdbc.update("INSERT INTO configuracion_organizacion (organizacion_id, clave, valor) VALUES (1, 'limite_turno', ?)",
					valor);
		}
	}

	record Esperado(String asunto, String html, String htmlAdmin) {
	}

	/** Asunto y cuerpos con los métodos de siempre del servicio, sobre la solicitud guardada. */
	private Esperado esperado(String ref) {
		Object servicio = AopTestUtils.getUltimateTargetObject(preinscripcionService);
		return TenantContext.calcularCon(1L, () -> new TransactionTemplate(txManager).execute(s -> {
			Preinscripcion p = preinscripciones.findByReferencia(ref).orElseThrow();
			return new Esperado(ReflectionTestUtils.invokeMethod(servicio, "asuntoCliente", p),
					ReflectionTestUtils.invokeMethod(servicio, "htmlConfirmacion", p),
					ReflectionTestUtils.invokeMethod(servicio, "htmlAdmin", p));
		}));
	}

	private Map<String, Object> fila(String ref) {
		return jdbc.queryForObject("""
				SELECT email_enviado, email_intentos, email_ultimo_error FROM preinscripcion WHERE referencia = ?""",
				(rs, i) -> {
					Map<String, Object> m = new LinkedHashMap<>();
					m.put("enviado", rs.getBoolean(1));
					m.put("intentos", rs.getInt(2));
					m.put("error", rs.getString(3));
					return m;
				}, ref);
	}

	private PreinscripcionRequest solicitud(int n) throws IOException {
		return new PreinscripcionRequest("TAEKWONDO", List.of(TURNO), "Correos", "Sintetica R" + n, dni(n),
				LocalDate.of(1990, 1, 1), "Calle Sintética 9", "612345678", "", CORREO, "", "", "", false, true, true,
				"Firmante Correos", firmaPng());
	}

	private MvcResult alta(int n) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("deporte", "TAEKWONDO");
		body.put("turnoIds", List.of(TURNO));
		body.put("nombre", "Correos");
		body.put("apellidos", "Sintetica R" + n);
		body.put("dni", dni(n));
		body.put("fechaNacimiento", "1990-01-01");
		body.put("direccion", "Calle Sintética 9");
		body.put("telefono", "612345678");
		body.put("telefono2", "");
		body.put("email", CORREO);
		body.put("observaciones", "");
		body.put("tutorNombre", "");
		body.put("tutorDni", "");
		body.put("tieneDiscapacidad", false);
		body.put("consentimientoFotografico", true);
		body.put("aceptacionNormas", true);
		body.put("firmanteNombre", "Firmante Correos");
		body.put("firmaBase64", firmaPng());
		return mvc.perform(post("/api/preinscripciones").contentType(MediaType.APPLICATION_JSON)
				.header("X-Forwarded-For", "10.98.0." + (ipSecuencia++ % 250 + 1)).content(json.writeValueAsString(body)))
				.andReturn();
	}

	private static String dni(int n) {
		return n + String.valueOf("TRWAGMYFPDXBNJZSQVHLCKE".charAt(n % 23));
	}

	private static String firmaPng() throws IOException {
		BufferedImage imagen = new BufferedImage(400, 120, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = imagen.createGraphics();
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, imagen.getWidth(), imagen.getHeight());
		g.setColor(Color.BLACK);
		g.drawString("Firma sintetica R2-P7d", 20, 60);
		g.drawLine(20, 80, 380, 70);
		g.dispose();
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		ImageIO.write(imagen, "png", bytes);
		return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes.toByteArray());
	}

	private void borrar() {
		jdbc.update("DELETE FROM preinscripcion_turno WHERE turno_id = ?", TURNO);
		jdbc.update("DELETE FROM preinscripcion WHERE grupo_id = ?", GRUPO);
		jdbc.update("DELETE FROM turno WHERE id = ?", TURNO);
		jdbc.update("DELETE FROM grupo WHERE id = ?", GRUPO);
	}
}
