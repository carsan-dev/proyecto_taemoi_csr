package com.taemoi.project.controladores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.taemoi.project.controllers.EventoController;
import com.taemoi.project.entities.Evento;
import com.taemoi.project.entities.Imagen;
import com.taemoi.project.services.DocumentoService;
import com.taemoi.project.services.EventoService;
import com.taemoi.project.services.ImagenService;

/**
 * Caracteriza la respuesta de {@code GET /api/eventos/{id}/imagen}: cabeceras exactas, {@code ?w=} y sus límites. Escrito
 * sobre el código anterior a la galería (decisión 342), antes de extraer lo común; debe seguir igual después.
 */
@ExtendWith(MockitoExtension.class)
class EventoImagenRespuestaTest {

	private static final long EVENTO = 7L;
	private static final String CACHE_VISIBLE = "max-age=28800, must-revalidate, public";
	private static final String CACHE_OCULTO = "max-age=28800, must-revalidate, private";

	@TempDir
	Path directorio;

	@Mock
	private EventoService eventoService;

	@Mock
	private ImagenService imagenService;

	@Mock
	private DocumentoService documentoService;

	@InjectMocks
	private EventoController eventoController;

	private Path archivo;
	private Evento evento;

	@BeforeEach
	void preparar() {
		evento = new Evento();
		evento.setId(EVENTO);
		evento.setVisible(true);
		evento.setFotoEvento(new Imagen("evento.png", "image/png", null, "evento.png"));
		lenient().when(eventoService.obtenerEventoPorId(EVENTO)).thenReturn(evento);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void sinAncho_sirveElOriginalConSusCabeceras() throws Exception {
		conPng(200, 100);

		ResponseEntity<Resource> respuesta = eventoController.obtenerImagenEvento(EVENTO, null);

		assertEquals(HttpStatus.OK, respuesta.getStatusCode());
		HttpHeaders cabeceras = respuesta.getHeaders();
		assertEquals(MediaType.IMAGE_PNG, cabeceras.getContentType());
		assertEquals(CACHE_VISIBLE, cabeceras.getCacheControl());
		assertEquals(etag(0, "image/png"), cabeceras.getETag());
		assertEquals(Files.getLastModifiedTime(archivo).toMillis() / 1000, cabeceras.getLastModified() / 1000);
		assertEquals("inline; filename=\"evento.png\"", cabeceras.getFirst(HttpHeaders.CONTENT_DISPOSITION));
		assertEquals(List.of("Accept-Encoding"), cabeceras.get(HttpHeaders.VARY));
		assertEquals(Files.size(archivo), respuesta.getBody().contentLength());
	}

	@Test
	void conAncho_redimensionaAWebp() throws Exception {
		conPng(1000, 500);

		ResponseEntity<Resource> respuesta = eventoController.obtenerImagenEvento(EVENTO, 480);

		assertEquals(HttpStatus.OK, respuesta.getStatusCode());
		if (hayCodificadorWebp()) {
			assertEquals(MediaType.parseMediaType("image/webp"), respuesta.getHeaders().getContentType());
			assertEquals(CACHE_VISIBLE, respuesta.getHeaders().getCacheControl());
			assertEquals(etag(480, "image/webp"), respuesta.getHeaders().getETag());
			assertEquals("inline; filename=\"evento.png\"",
					respuesta.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
			assertTrue(respuesta.getHeaders().getContentLength() > 0);
		} else {
			assertEquals(etag(480, "image/png"), respuesta.getHeaders().getETag());
		}
	}

	@Test
	void anchoPorDebajoDelMinimo_seSubeA160() throws Exception {
		conPng(1000, 500);

		ResponseEntity<Resource> respuesta = eventoController.obtenerImagenEvento(EVENTO, 50);

		String formato = hayCodificadorWebp() ? "image/webp" : "image/png";
		assertEquals(etag(160, formato), respuesta.getHeaders().getETag());
	}

	@Test
	void anchoPorEncimaDelMaximo_seBajaA2200_ySinAmpliarSirveElOriginal() throws Exception {
		conPng(1000, 500);

		ResponseEntity<Resource> respuesta = eventoController.obtenerImagenEvento(EVENTO, 9000);

		assertEquals(MediaType.IMAGE_PNG, respuesta.getHeaders().getContentType());
		assertEquals(etag(2200, "image/png"), respuesta.getHeaders().getETag());
	}

	@Test
	void anchoCeroONegativo_esElOriginal() throws Exception {
		conPng(1000, 500);

		assertEquals(etag(0, "image/png"), eventoController.obtenerImagenEvento(EVENTO, 0).getHeaders().getETag());
		assertEquals(etag(0, "image/png"), eventoController.obtenerImagenEvento(EVENTO, -5).getHeaders().getETag());
	}

	/** Por encima de 12 Mpx no se decodifica en caliente: el original, con el ancho pedido en el ETag. */
	@Test
	void origenDemasiadoGrande_sirveElOriginal() throws Exception {
		conPng(4000, 3001);

		ResponseEntity<Resource> respuesta = eventoController.obtenerImagenEvento(EVENTO, 480);

		assertEquals(MediaType.IMAGE_PNG, respuesta.getHeaders().getContentType());
		assertEquals(etag(480, "image/png"), respuesta.getHeaders().getETag());
	}

	/** Decoder nativo inestable en producción: un WebP nunca se redimensiona en caliente. */
	@Test
	void webpNuncaSeRedimensiona() throws Exception {
		conPng(1000, 500);
		evento.setFotoEvento(new Imagen("evento.webp", "image/webp", null, "evento.webp"));

		ResponseEntity<Resource> respuesta = eventoController.obtenerImagenEvento(EVENTO, 480);

		assertEquals(MediaType.parseMediaType("image/webp"), respuesta.getHeaders().getContentType());
		assertEquals(etag(480, "image/webp"), respuesta.getHeaders().getETag());
		assertEquals(Files.size(archivo), respuesta.getBody().contentLength());
	}

	@Test
	void eventoOculto_paraGestion_esPrivada() throws Exception {
		conPng(200, 100);
		evento.setVisible(false);
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("gestor", "n/a",
				List.of(new SimpleGrantedAuthority("ROLE_MANAGER"))));

		ResponseEntity<Resource> respuesta = eventoController.obtenerImagenEvento(EVENTO, null);

		assertEquals(CACHE_OCULTO, respuesta.getHeaders().getCacheControl());
	}

	@Test
	void tipoIlegible_esBinario() throws Exception {
		conPng(200, 100);
		evento.setFotoEvento(new Imagen("evento.png", "no-es-un-tipo", null, "evento.png"));

		assertEquals(MediaType.APPLICATION_OCTET_STREAM,
				eventoController.obtenerImagenEvento(EVENTO, null).getHeaders().getContentType());
	}

	@Test
	void sinFicheroOSinFoto_es404() throws Exception {
		when(imagenService.resolverRutaArchivo(any(Imagen.class))).thenReturn(directorio.resolve("no-existe.png"));
		assertEquals(HttpStatus.NOT_FOUND, eventoController.obtenerImagenEvento(EVENTO, null).getStatusCode());

		evento.setFotoEvento(null);
		assertEquals(HttpStatus.NOT_FOUND, eventoController.obtenerImagenEvento(EVENTO, null).getStatusCode());
	}

	private void conPng(int ancho, int alto) throws Exception {
		archivo = directorio.resolve("evento.png");
		assertTrue(ImageIO.write(new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB), "png", archivo.toFile()));
		lenient().when(imagenService.resolverRutaArchivo(any(Imagen.class))).thenReturn(archivo);
	}

	private String etag(int ancho, String formato) throws Exception {
		return "\"" + Long.toHexString(Files.size(archivo)) + "-"
				+ Long.toHexString(Files.getLastModifiedTime(archivo).toMillis()) + "-" + ancho + "-" + formato + "\"";
	}

	private static boolean hayCodificadorWebp() {
		Iterator<ImageWriter> escritores = ImageIO.getImageWritersByMIMEType("image/webp");
		return escritores.hasNext() || ImageIO.getImageWritersBySuffix("webp").hasNext();
	}
}
