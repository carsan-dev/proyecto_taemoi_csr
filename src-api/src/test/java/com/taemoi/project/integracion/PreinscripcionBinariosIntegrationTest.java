package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.entities.AuthProvider;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.PreinscripcionDocumentosRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.EmailService;
import com.taemoi.project.services.JwtService;

import jakarta.servlet.http.Cookie;

/**
 * R2-P7c: la firma, el PDF firmado y la plantilla congelada de una preinscripción se escriben con el alta pública y se
 * leen SOLO donde hacen falta. Contra MySQL real y con el alta de verdad ({@code POST /api/preinscripciones}):
 * <ul>
 * <li>el alta guarda los tres (columnas NOT NULL) y el correo a la familia adjunta exactamente el PDF guardado;</li>
 * <li>{@code /firma}, {@code /documento-firmado}, {@code /documento?token=} y el reenvío devuelven/adjuntan exactamente
 * los bytes guardados, y cada uno lee solo su columna;</li>
 * <li>el listado, el detalle (salvo su plantilla), cancelar (con la cola de espera) no leen ningún binario; guardar la
 * solicitud no los toca;</li>
 * <li>eliminar borra la fila con sus binarios;</li>
 * <li>las lecturas de binarios se restringen a la escuela activa.</li>
 * </ul>
 * Datos sintéticos: un grupo y un turno propios (ids 8.260.000 / 8.360.000) en la organización 1; las altas cuelgan de
 * ese grupo y se borran al terminar cada test. Mismas propiedades y simulaciones que
 * {@link AforoPreinscripcionesIntegrationTest} (comparten contexto de Spring).
 */
@SpringBootTest(properties = {
		"spring.jpa.properties.hibernate.session_factory.statement_inspector=com.taemoi.project.integracion.ContadorSentencias",
		"app.preinscripciones.admin-email=" + AforoPreinscripcionesIntegrationTest.ADMIN })
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PreinscripcionBinariosIntegrationTest extends MySqlIntegrationTest {

	private static final long GRUPO = 8_260_000L;
	private static final long TURNO = 8_360_000L;
	private static final String CORREO = "binarios-r2p7c@example.test";
	/** Un SELECT que lee una de las columnas binarias (Hibernate las cualifica con el alias: {@code p1_0.firma}). */
	private static final Pattern FIRMA = Pattern.compile("\\.firma\\b");

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtService jwtService;
	@Autowired UsuarioRepository usuarios;
	@Autowired PreinscripcionDocumentosRepository documentos;

	@MockitoBean EmailService email;

	String token;
	Long gestor;
	int ipSecuencia = 1;
	int dniSecuencia = 47_100_000;

	@BeforeAll
	void preparar() {
		borrar();
		long tkd = jdbc.queryForObject("SELECT id FROM disciplina WHERE organizacion_id = 1 AND codigo = 'TAEKWONDO'",
				Long.class);
		jdbc.update("""
				INSERT INTO grupo (id, nombre, tipo, rango_edad_min, rango_edad_max, organizacion_id, disciplina_id, sede_id)
				VALUES (?, 'BINARIOS-R2P7C Adultos', NULL, NULL, NULL, 1, ?, NULL)""", GRUPO, tkd);
		jdbc.update("""
				INSERT INTO turno (id, dia_semana, hora_inicio, hora_fin, tipo, grupo_id, organizacion_id)
				VALUES (?, 'Viernes', '21:00', '22:00', NULL, ?, 1)""", TURNO, GRUPO);
		token = tokenPropietario();
	}

	@BeforeEach
	void limpiarCorreos() {
		Mockito.clearInvocations(email);
	}

	@AfterEach
	void borrarAltas() {
		jdbc.update("DELETE FROM preinscripcion_turno WHERE turno_id = ?", TURNO);
		jdbc.update("DELETE FROM preinscripcion WHERE grupo_id = ?", GRUPO);
	}

	@AfterAll
	void retirar() {
		borrar();
		if (gestor != null) {
			jdbc.update("UPDATE membresia SET activa = FALSE WHERE usuario_id = ?", gestor);
		}
	}

	// ------------------------------------------------------------------ alta

	@Test
	void altaPublicaGuardaFirmaPdfYPlantillaYAdjuntaElMismoPdf() throws Exception {
		ContadorSentencias.Conteo<MvcResult> conteo = ContadorSentencias.contar(this::alta);
		MvcResult r = conteo.resultado();
		assertEquals(201, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		JsonNode respuesta = json.readTree(r.getResponse().getContentAsString());
		String ref = respuesta.get("referencia").asText();
		Guardado g = guardado(ref);

		assertTrue(esPng(g.firma()), "la firma guardada es un PNG");
		assertTrue(new String(g.pdf(), 0, 5, StandardCharsets.ISO_8859_1).startsWith("%PDF-"), "el PDF guardado");
		assertTrue(g.plantilla().contains("\"version\""), g.plantilla());
		// Un solo INSERT en preinscripcion, con los tres binarios (no se reescriben después).
		List<String> escrituras = conteo.sentencias().stream().map(String::toLowerCase)
				.filter(s -> (s.startsWith("insert into preinscripcion ") || s.startsWith("insert into preinscripcion("))
						|| s.startsWith("update preinscripcion "))
				.toList();
		assertEquals(1, escrituras.stream().filter(s -> s.startsWith("insert")).count(), escrituras.toString());
		String insert = escrituras.stream().filter(s -> s.startsWith("insert")).findFirst().orElseThrow();
		assertTrue(insert.contains("firma") && insert.contains("pdf_firmado") && insert.contains("plantilla_snapshot"),
				insert);
		assertTrue(escrituras.stream().filter(s -> s.startsWith("update")).noneMatch(
				s -> FIRMA.matcher(s).find() || s.contains("firma=") || s.contains("pdf_firmado") || s.contains("plantilla_snapshot")),
				escrituras.toString());

		// El correo a la familia adjunta exactamente el PDF guardado.
		ArgumentCaptor<byte[]> adjunto = ArgumentCaptor.forClass(byte[].class);
		verify(email).sendEmailConAdjunto(eq(CORREO), anyString(), anyString(), eq("preinscripcion-" + ref + ".pdf"),
				adjunto.capture());
		assertArrayEquals(g.pdf(), adjunto.getValue());

		// El enlace público del documento (con el token de la respuesta) devuelve el mismo PDF.
		MvcResult doc = mvc.perform(get("/api/preinscripciones/" + ref + "/documento")
				.param("token", respuesta.get("tokenDocumento").asText())).andReturn();
		assertEquals(200, doc.getResponse().getStatus());
		assertEquals(MediaType.APPLICATION_PDF_VALUE, doc.getResponse().getContentType());
		assertEquals("attachment; filename=preinscripcion-" + ref + ".pdf",
				doc.getResponse().getHeader("Content-Disposition"));
		assertArrayEquals(g.pdf(), doc.getResponse().getContentAsByteArray());
		MvcResult malo = mvc.perform(get("/api/preinscripciones/" + ref + "/documento").param("token", "x".repeat(32)))
				.andReturn();
		assertEquals(403, malo.getResponse().getStatus());
		assertFalse(new String(malo.getResponse().getContentAsByteArray(), StandardCharsets.ISO_8859_1).startsWith("%PDF"));
	}

	// ------------------------------------------------------------------ lecturas de binarios

	@Test
	void firmaYDocumentoFirmadoDevuelvenLosBytesGuardadosYCadaUnoLeeSoloSuColumna() throws Exception {
		String ref = altaCorrecta();
		Guardado g = guardado(ref);

		ContadorSentencias.Conteo<MvcResult> firma = ContadorSentencias.contar(
				() -> mvc.perform(get("/api/preinscripciones/" + ref + "/firma").cookie(new Cookie("jwt", token))).andReturn());
		assertEquals(200, firma.resultado().getResponse().getStatus());
		assertEquals(MediaType.IMAGE_PNG_VALUE, firma.resultado().getResponse().getContentType());
		assertEquals("no-store", firma.resultado().getResponse().getHeader("Cache-Control"));
		assertArrayEquals(g.firma(), firma.resultado().getResponse().getContentAsByteArray());
		assertTrue(selects(firma).noneMatch(s -> s.contains("pdf_firmado") || s.contains("plantilla_snapshot")),
				"GET /firma no lee el PDF ni la plantilla: " + firma.sentencias());

		ContadorSentencias.Conteo<MvcResult> pdf = ContadorSentencias.contar(() -> mvc.perform(
				get("/api/preinscripciones/" + ref + "/documento-firmado").cookie(new Cookie("jwt", token))).andReturn());
		assertEquals(200, pdf.resultado().getResponse().getStatus());
		assertEquals(MediaType.APPLICATION_PDF_VALUE, pdf.resultado().getResponse().getContentType());
		assertEquals("attachment; filename=preinscripcion-" + ref + ".pdf",
				pdf.resultado().getResponse().getHeader("Content-Disposition"));
		assertArrayEquals(g.pdf(), pdf.resultado().getResponse().getContentAsByteArray());
		assertTrue(selects(pdf).noneMatch(s -> FIRMA.matcher(s).find() || s.contains("plantilla_snapshot")),
				"GET /documento-firmado no lee la firma ni la plantilla: " + pdf.sentencias());

		// Referencia inexistente: igual que antes (404).
		assertEquals(404, mvc.perform(get("/api/preinscripciones/NO-EXISTE-R2P7C/firma").cookie(new Cookie("jwt", token)))
				.andReturn().getResponse().getStatus());
		assertEquals(404, mvc.perform(get("/api/preinscripciones/NO-EXISTE-R2P7C/documento-firmado")
				.cookie(new Cookie("jwt", token))).andReturn().getResponse().getStatus());
	}

	@Test
	void reenviarAdjuntaElPdfOriginalGuardado() throws Exception {
		String ref = altaCorrecta();
		Guardado g = guardado(ref);
		Mockito.clearInvocations(email);

		MvcResult r = mvc.perform(post("/api/preinscripciones/" + ref + "/reenviar").cookie(new Cookie("jwt", token)))
				.andReturn();

		assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		ArgumentCaptor<byte[]> adjunto = ArgumentCaptor.forClass(byte[].class);
		verify(email).sendEmailConAdjunto(eq(CORREO), anyString(), anyString(), eq("solicitud-original-" + ref + ".pdf"),
				adjunto.capture());
		assertArrayEquals(g.pdf(), adjunto.getValue());
		assertEquals(g, guardado(ref), "reenviar no toca los binarios");
	}

	@Test
	void listadoDetalleYCancelarNoLeenLosBinariosNiLosReescriben() throws Exception {
		String ref = altaCorrecta();
		Guardado g = guardado(ref);

		ContadorSentencias.Conteo<MvcResult> listado = ContadorSentencias.contar(() -> mvc.perform(
				get("/api/preinscripciones").param("q", ref).cookie(new Cookie("jwt", token))).andReturn());
		assertEquals(200, listado.resultado().getResponse().getStatus());
		assertEquals(1, json.readTree(listado.resultado().getResponse().getContentAsString()).get("content").size());
		assertSinBinarios("listado", listado, true);

		ContadorSentencias.Conteo<MvcResult> detalle = ContadorSentencias.contar(() -> mvc.perform(
				get("/api/preinscripciones/" + ref).cookie(new Cookie("jwt", token))).andReturn());
		assertEquals(200, detalle.resultado().getResponse().getStatus());
		assertEquals(g.plantilla(),
				json.readTree(detalle.resultado().getResponse().getContentAsString()).get("plantillaSnapshot").asText());
		assertSinBinarios("detalle", detalle, false);

		// Cancelar (pendiente: libera plaza y recorre la cola de espera) guarda la solicitud sin leer ni tocar binarios.
		ContadorSentencias.Conteo<MvcResult> cancelar = ContadorSentencias.contar(() -> mvc.perform(
				post("/api/preinscripciones/" + ref + "/cancelar").cookie(new Cookie("jwt", token))
						.contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"\"}")).andReturn());
		assertEquals(200, cancelar.resultado().getResponse().getStatus(), cancelar.resultado().getResponse().getContentAsString());
		assertSinBinarios("cancelar", cancelar, true);
		assertTrue(cancelar.sentencias().stream().map(String::toLowerCase).filter(s -> s.startsWith("update preinscripcion "))
				.noneMatch(s -> FIRMA.matcher(s).find() || s.contains("firma=") || s.contains("pdf_firmado") || s.contains("plantilla_snapshot")),
				cancelar.sentencias().toString());
		assertEquals("CANCELADA", jdbc.queryForObject("SELECT estado FROM preinscripcion WHERE referencia = ?", String.class, ref));
		assertEquals(g, guardado(ref), "guardar la solicitud cancelada no toca los binarios");
	}

	@Test
	void eliminarBorraLaFilaConSusBinarios() throws Exception {
		String ref = altaCorrecta();
		assertEquals(200, mvc.perform(post("/api/preinscripciones/" + ref + "/cancelar").cookie(new Cookie("jwt", token))
				.contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"\"}")).andReturn().getResponse().getStatus());

		MvcResult r = mvc.perform(delete("/api/preinscripciones/" + ref).cookie(new Cookie("jwt", token))).andReturn();

		assertEquals(204, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM preinscripcion WHERE referencia = ?", Integer.class, ref));
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM preinscripcion_turno WHERE turno_id = ?", Integer.class,
				TURNO));
		assertEquals(404, mvc.perform(get("/api/preinscripciones/" + ref + "/firma").cookie(new Cookie("jwt", token)))
				.andReturn().getResponse().getStatus());
	}

	@Test
	void losBinariosSoloSeLeenEnSuEscuela() throws Exception {
		String ref = altaCorrecta();
		Guardado g = guardado(ref);
		long id = jdbc.queryForObject("SELECT id FROM preinscripcion WHERE referencia = ?", Long.class, ref);

		assertArrayEquals(g.firma(), TenantContext.calcularCon(1L, () -> documentos.findFirmaByReferencia(ref)));
		assertArrayEquals(g.pdf(), TenantContext.calcularCon(1L, () -> documentos.findPdfFirmadoByReferencia(ref)));
		assertArrayEquals(g.pdf(), TenantContext.calcularCon(1L, () -> documentos.findPdfFirmadoById(id)));
		assertEquals(g.plantilla(), TenantContext.calcularCon(1L, () -> documentos.findPlantillaSnapshotById(id)));

		long otra = 987_654L;
		assertNull(TenantContext.calcularCon(otra, () -> documentos.findFirmaByReferencia(ref)));
		assertNull(TenantContext.calcularCon(otra, () -> documentos.findPdfFirmadoByReferencia(ref)));
		assertNull(TenantContext.calcularCon(otra, () -> documentos.findPdfFirmadoById(id)));
		assertNull(TenantContext.calcularCon(otra, () -> documentos.findPlantillaSnapshotById(id)));
	}

	// ------------------------------------------------------------------ utilidades

	/** Los binarios tal como están en la base de datos. */
	record Guardado(byte[] firma, byte[] pdf, String plantilla) {

		@Override
		public boolean equals(Object o) {
			return o instanceof Guardado g && java.util.Arrays.equals(firma, g.firma) && java.util.Arrays.equals(pdf, g.pdf)
					&& plantilla.equals(g.plantilla);
		}

		@Override
		public int hashCode() {
			return java.util.Arrays.hashCode(firma) * 31 + java.util.Arrays.hashCode(pdf) + plantilla.hashCode();
		}

		@Override
		public String toString() {
			return "Guardado[firma=" + firma.length + " B, pdf=" + pdf.length + " B, plantilla=" + plantilla.length() + "]";
		}
	}

	private Guardado guardado(String ref) {
		return jdbc.queryForObject("SELECT firma, pdf_firmado, plantilla_snapshot FROM preinscripcion WHERE referencia = ?",
				(rs, i) -> new Guardado(rs.getBytes(1), rs.getBytes(2), rs.getString(3)), ref);
	}

	private static java.util.stream.Stream<String> selects(ContadorSentencias.Conteo<?> conteo) {
		return conteo.sentencias().stream().map(String::toLowerCase).filter(s -> s.startsWith("select"));
	}

	private static void assertSinBinarios(String nombre, ContadorSentencias.Conteo<?> conteo, boolean tampocoPlantilla) {
		List<String> conBinarios = selects(conteo).filter(s -> FIRMA.matcher(s).find() || s.contains("pdf_firmado")
				|| (tampocoPlantilla && s.contains("plantilla_snapshot"))).toList();
		assertTrue(conBinarios.isEmpty(), nombre + " lee columnas binarias: " + conBinarios);
		if (!tampocoPlantilla) {
			// El detalle solo lee la plantilla congelada, en una consulta propia.
			assertEquals(1, selects(conteo).filter(s -> s.contains("plantilla_snapshot")).count(), conteo.sentencias().toString());
		}
	}

	private String altaCorrecta() throws Exception {
		MvcResult r = alta();
		assertEquals(201, r.getResponse().getStatus(), r.getResponse().getContentAsString());
		return json.readTree(r.getResponse().getContentAsString()).get("referencia").asText();
	}

	private MvcResult alta() throws Exception {
		int n = dniSecuencia++;
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("deporte", "TAEKWONDO");
		body.put("turnoIds", List.of(TURNO));
		body.put("nombre", "Binarios");
		body.put("apellidos", "Sintetica R" + n);
		body.put("dni", n + String.valueOf("TRWAGMYFPDXBNJZSQVHLCKE".charAt(n % 23)));
		body.put("fechaNacimiento", "1990-01-01");
		body.put("direccion", "Calle Sintética 7");
		body.put("telefono", "612345678");
		body.put("telefono2", "");
		body.put("email", CORREO);
		body.put("observaciones", "");
		body.put("tutorNombre", "");
		body.put("tutorDni", "");
		body.put("tieneDiscapacidad", false);
		body.put("consentimientoFotografico", true);
		body.put("aceptacionNormas", true);
		body.put("firmanteNombre", "Firmante Binarios");
		body.put("firmaBase64", firmaPng());
		return mvc.perform(post("/api/preinscripciones").contentType(MediaType.APPLICATION_JSON)
				.header("X-Forwarded-For", "10.97.0." + (ipSecuencia++ % 250 + 1)).content(json.writeValueAsString(body)))
				.andReturn();
	}

	private static String firmaPng() throws IOException {
		BufferedImage imagen = new BufferedImage(400, 120, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = imagen.createGraphics();
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, imagen.getWidth(), imagen.getHeight());
		g.setColor(Color.BLACK);
		g.drawString("Firma sintetica R2-P7c", 20, 60);
		g.drawLine(20, 80, 380, 70);
		g.dispose();
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		ImageIO.write(imagen, "png", bytes);
		return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes.toByteArray());
	}

	private static boolean esPng(byte[] b) {
		return b != null && b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
	}

	private void borrar() {
		jdbc.update("DELETE FROM preinscripcion_turno WHERE turno_id = ?", TURNO);
		jdbc.update("DELETE FROM preinscripcion WHERE grupo_id = ?", GRUPO);
		jdbc.update("DELETE FROM turno WHERE id = ?", TURNO);
		jdbc.update("DELETE FROM grupo WHERE id = ?", GRUPO);
	}

	private String tokenPropietario() {
		Usuario usuario = TenantContext.calcularCon(1L, () -> {
			Usuario u = new Usuario();
			u.setNombre("Gestor");
			u.setApellidos("Binarios R2P7C");
			u.setEmail("binarios-r2p7c-gestor-" + System.nanoTime() + "@example.test");
			u.setContrasena("fake-Password-123");
			u.setAuthProvider(AuthProvider.LOCAL);
			return usuarios.save(u);
		});
		gestor = usuario.getId();
		jdbc.update("INSERT INTO membresia (usuario_id, organizacion_id, rol) VALUES (?, 1, 'PROPIETARIO')", gestor);
		assertNotNull(gestor);
		return jwtService.generateToken(usuario, 3_600_000, 1L);
	}
}
