package com.taemoi.project.integracion;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.taemoi.project.organizacion.ResolutorEscuelaPorHost;

/**
 * Ficheros de eventos que enlaza la web por plantilla (fase4-web-plan.md §5, pieza W5) pedidos sin sesión, con la
 * escuela del Host: cartel ({@code /api/eventos/{id}/imagen}), foto ({@code /fotos/{fotoId}?w=640}) y documento
 * ({@code /documentos/{docId}/descargar}). Lo de otra escuela, lo de un evento oculto y el documento que no es de ese
 * evento dan 404; lo visible de la propia escuela, 200 con su tipo. Ficheros reales en un directorio temporal.
 *
 * <pre>
 * X  visible (cartel, foto, documento), visible B (documento), oculto (cartel, foto, documento).
 * Y  visible (sin ficheros), para pedir con el Host de Y.
 * </pre>
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArchivosEventoPorHostIntegrationTest extends MySqlIntegrationTest {

	private static final Path IMAGENES;
	private static final Path DOCUMENTOS;
	private static final byte[] PDF = "%PDF-1.4\n% prueba W5\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);

	static {
		try {
			Path raiz = Files.createTempDirectory("taemoi-it-archivos-host-").toAbsolutePath().normalize();
			IMAGENES = Files.createDirectories(raiz.resolve("imagenes"));
			DOCUMENTOS = Files.createDirectories(raiz.resolve("documentos"));
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@DynamicPropertySource
	static void raicesDeFicheros(DynamicPropertyRegistry registro) {
		registro.add("app.imagenes.directorio.linux", () -> IMAGENES + "/");
		registro.add("app.imagenes.directorio.windows", () -> IMAGENES + "/");
		registro.add("app.documentos.directorio.linux", () -> DOCUMENTOS + "/");
		registro.add("app.documentos.directorio.windows", () -> DOCUMENTOS + "/");
	}

	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired ResolutorEscuelaPorHost porHost;

	final String marca = UUID.randomUUID().toString().substring(0, 8);
	final Map<Long, String> hosts = new HashMap<>();
	long x, y;
	long visibleX, visibleBX, ocultoX, visibleY;
	long fotoVisibleX, fotoOcultoX;
	long docVisibleX, docVisibleBX, docOcultoX;

	@BeforeAll
	void sembrar() throws IOException {
		x = organizacion("x");
		y = organizacion("y");

		visibleX = evento(x, "Visible X", true, imagen(x, "cartel-visible"));
		visibleBX = evento(x, "Visible B X", true, null);
		ocultoX = evento(x, "Oculto X", false, imagen(x, "cartel-oculto"));
		visibleY = evento(y, "Visible Y", true, null);

		fotoVisibleX = foto(x, visibleX);
		fotoOcultoX = foto(x, ocultoX);
		docVisibleX = documento(x, visibleX, "Circular X.pdf");
		docVisibleBX = documento(x, visibleBX, "Circular B X.pdf");
		docOcultoX = documento(x, ocultoX, "Secreto X.pdf");

		for (long org : List.of(x, y)) {
			String host = "w5-archivos-" + org + "-" + marca + ".test";
			jdbc.update("INSERT INTO organizacion_dominio (organizacion_id, host, principal) VALUES (?, ?, TRUE)", org,
					host);
			hosts.put(org, host);
		}
		porHost.recargar();
	}

	@AfterAll
	void retirarDominios() {
		hosts.values().forEach(host -> jdbc.update("DELETE FROM organizacion_dominio WHERE host = ?", host));
		porHost.recargar();
	}

	// ------------------------------------------------------------------ caso válido (control)

	@Test
	void visibleDeSuEscuela_200ConSuTipo() throws Exception {
		MockHttpServletResponse cartel = anonimo(x, get(imagenDe(visibleX)));
		assertEquals(200, cartel.getStatus());
		assertEquals("image/png", cartel.getContentType());

		MockHttpServletResponse foto = anonimo(x, get(fotoDe(visibleX, fotoVisibleX)).param("w", "640"));
		assertEquals(200, foto.getStatus());
		assertTrue(foto.getContentType().startsWith("image/"), foto.getContentType());

		MockHttpServletResponse documento = anonimo(x, get(documentoDe(visibleX, docVisibleX)));
		assertEquals(200, documento.getStatus());
		assertEquals("application/pdf", documento.getContentType());
		assertArrayEquals(PDF, documento.getContentAsByteArray());
	}

	// ------------------------------------------------------------------ otra escuela

	/** Con el Host de Y, lo de X no existe: ni cartel, ni foto, ni documento. */
	@Test
	void conElHostDeOtraEscuela_404() throws Exception {
		assertEquals(404, anonimo(y, get(imagenDe(visibleX))).getStatus(), "cartel");
		assertEquals(404, anonimo(y, get(fotoDe(visibleX, fotoVisibleX)).param("w", "640")).getStatus(), "foto");
		assertEquals(404, anonimo(y, get(documentoDe(visibleX, docVisibleX))).getStatus(), "documento");
	}

	/** El documento de X colgado de un evento visible de Y: no es de ese evento ni de esa escuela. */
	@Test
	void documentoDeOtraEscuelaBajoUnEventoPropio_404() throws Exception {
		assertEquals(404, anonimo(y, get(documentoDe(visibleY, docVisibleX))).getStatus());
	}

	// ------------------------------------------------------------------ evento oculto

	@Test
	void eventoOcultoDeSuEscuela_404() throws Exception {
		assertEquals(404, anonimo(x, get(imagenDe(ocultoX))).getStatus(), "cartel");
		assertEquals(404, anonimo(x, get(fotoDe(ocultoX, fotoOcultoX)).param("w", "640")).getStatus(), "foto");
		assertEquals(404, anonimo(x, get(documentoDe(ocultoX, docOcultoX))).getStatus(), "documento");
	}

	// ------------------------------------------------------------------ documento de otro evento

	/** Documento de otro evento visible de la misma escuela, o inexistente: 404, no 500. */
	@Test
	void documentoDeOtroEventoOInexistente_404() throws Exception {
		assertEquals(404, anonimo(x, get(documentoDe(visibleX, docVisibleBX))).getStatus(), "de otro evento");
		assertEquals(404, anonimo(x, get(documentoDe(visibleX, docOcultoX))).getStatus(), "del evento oculto");
		assertEquals(404, anonimo(x, get(documentoDe(visibleX, Long.MAX_VALUE))).getStatus(), "inexistente");
		assertEquals(200, anonimo(x, get(documentoDe(visibleBX, docVisibleBX))).getStatus(), "control: el suyo");
	}

	// ------------------------------------------------------------------ utilidades

	private MockHttpServletResponse anonimo(long org, MockHttpServletRequestBuilder peticion) throws Exception {
		return mvc.perform(peticion.header("Host", hosts.get(org))).andReturn().getResponse();
	}

	private static String imagenDe(long evento) {
		return "/api/eventos/" + evento + "/imagen";
	}

	private static String fotoDe(long evento, long foto) {
		return "/api/eventos/" + evento + "/fotos/" + foto;
	}

	private static String documentoDe(long evento, long documento) {
		return "/api/eventos/" + evento + "/documentos/" + documento + "/descargar";
	}

	private long organizacion(String sufijo) {
		String slug = "it-w5-archivos-" + sufijo + "-" + marca;
		jdbc.update("INSERT INTO organizacion (slug, nombre) VALUES (?, ?)", slug, "Escuela archivos " + sufijo);
		return jdbc.queryForObject("SELECT id FROM organizacion WHERE slug = ?", Long.class, slug);
	}

	/** Imagen PNG real en disco (1000×800, para que {@code w=640} tenga que reducir). */
	private long imagen(long org, String nombre) throws IOException {
		String ruta = "org-" + org + "/eventos/" + nombre + "-" + marca + ".png";
		Path fichero = IMAGENES.resolve(ruta);
		Files.createDirectories(fichero.getParent());
		Files.write(fichero, png());
		jdbc.update("INSERT INTO imagen (nombre, ruta, tipo, url, organizacion_id) VALUES (?, ?, 'image/png', ?, ?)",
				nombre + ".png", ruta, "/imagenes/" + ruta, org);
		return jdbc.queryForObject("SELECT id FROM imagen WHERE ruta = ?", Long.class, ruta);
	}

	private long evento(long org, String titulo, boolean visible, Long cartel) {
		jdbc.update("""
				INSERT INTO evento (titulo, descripcion, visible, orden, organizacion_id, foto_evento_id)
				VALUES (?, 'Descripción de prueba', ?, 1000, ?, ?)""", titulo + " " + marca, visible, org, cartel);
		return jdbc.queryForObject("SELECT id FROM evento WHERE titulo = ?", Long.class, titulo + " " + marca);
	}

	private long foto(long org, long evento) throws IOException {
		long imagen = imagen(org, "foto-" + evento);
		long miniatura = imagen(org, "mini-" + evento);
		jdbc.update("INSERT INTO evento_foto (organizacion_id, evento_id, imagen_id, miniatura_id, orden)"
				+ " VALUES (?, ?, ?, ?, 1)", org, evento, imagen, miniatura);
		return jdbc.queryForObject("SELECT id FROM evento_foto WHERE imagen_id = ?", Long.class, imagen);
	}

	private long documento(long org, long evento, String nombre) throws IOException {
		String ruta = "org-" + org + "/eventos/" + UUID.randomUUID() + ".pdf";
		Path fichero = DOCUMENTOS.resolve(ruta);
		Files.createDirectories(fichero.getParent());
		Files.write(fichero, PDF);
		jdbc.update("""
				INSERT INTO documento (nombre, ruta, tipo, url, evento_id, organizacion_id)
				VALUES (?, ?, 'application/pdf', ?, ?, ?)""", nombre, ruta, "/documentos/" + ruta, evento, org);
		return jdbc.queryForObject("SELECT id FROM documento WHERE ruta = ?", Long.class, ruta);
	}

	private static byte[] png() throws IOException {
		BufferedImage imagen = new BufferedImage(1000, 800, BufferedImage.TYPE_INT_RGB);
		ByteArrayOutputStream salida = new ByteArrayOutputStream();
		ImageIO.write(imagen, "png", salida);
		return salida.toByteArray();
	}
}
