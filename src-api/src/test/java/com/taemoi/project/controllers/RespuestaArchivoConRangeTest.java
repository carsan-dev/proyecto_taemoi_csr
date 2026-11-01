package com.taemoi.project.controllers;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.taemoi.project.services.MaterialExamenService.MaterialExamenArchivo;

/** Comportamiento de las rutas de familias que se extrajo de AlumnoController: lo comparten con el panel (pieza R1). */
class RespuestaArchivoConRangeTest {

	private static final byte[] CONTENIDO = "0123456789".getBytes(StandardCharsets.US_ASCII);

	@TempDir
	Path carpeta;

	private MaterialExamenArchivo archivo(String mime) throws IOException {
		Path fichero = Files.write(carpeta.resolve("01_saludo.mp4"), CONTENIDO);
		return new MaterialExamenArchivo(fichero, "01_saludo.mp4", mime, CONTENIDO.length);
	}

	private static HttpHeaders conRange(String valor) {
		HttpHeaders cabeceras = new HttpHeaders();
		cabeceras.set(HttpHeaders.RANGE, valor);
		return cabeceras;
	}

	private static byte[] cuerpo(ResponseEntity<Resource> respuesta) throws IOException {
		try (InputStream entrada = respuesta.getBody().getInputStream()) {
			return entrada.readAllBytes();
		}
	}

	@Test
	void sinRange_elFicheroEntero() throws Exception {
		ResponseEntity<Resource> respuesta = RespuestaArchivoConRange.servir(archivo("video/mp4"), new HttpHeaders(), false);

		assertEquals(HttpStatus.OK, respuesta.getStatusCode());
		assertEquals(MediaType.parseMediaType("video/mp4"), respuesta.getHeaders().getContentType());
		assertEquals(10L, respuesta.getHeaders().getContentLength());
		assertEquals("bytes", respuesta.getHeaders().getFirst(HttpHeaders.ACCEPT_RANGES));
		assertTrue(respuesta.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).startsWith("inline"));
		assertArrayEquals(CONTENIDO, cuerpo(respuesta));
	}

	@Test
	void conRange_elTrozoPedido() throws Exception {
		ResponseEntity<Resource> respuesta = RespuestaArchivoConRange.servir(archivo("video/mp4"), conRange("bytes=2-5"), false);

		assertEquals(HttpStatus.PARTIAL_CONTENT, respuesta.getStatusCode());
		assertEquals("bytes 2-5/10", respuesta.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE));
		assertEquals(4L, respuesta.getHeaders().getContentLength());
		assertArrayEquals("2345".getBytes(StandardCharsets.US_ASCII), cuerpo(respuesta));
	}

	@Test
	void conRangeAbierto_hastaElFinal() throws Exception {
		ResponseEntity<Resource> respuesta = RespuestaArchivoConRange.servir(archivo("video/mp4"), conRange("bytes=7-"), false);

		assertEquals("bytes 7-9/10", respuesta.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE));
		assertArrayEquals("789".getBytes(StandardCharsets.US_ASCII), cuerpo(respuesta));
	}

	@Test
	void rangoFueraDelFichero_416() throws Exception {
		ResponseEntity<Resource> respuesta = RespuestaArchivoConRange.servir(archivo("video/mp4"), conRange("bytes=50-60"), false);

		assertEquals(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE, respuesta.getStatusCode());
		assertEquals("bytes */10", respuesta.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE));
		assertNull(respuesta.getBody());
	}

	@Test
	void descarga_comoAdjunto_yTipoRaroComoBinario() throws Exception {
		ResponseEntity<Resource> respuesta = RespuestaArchivoConRange.servir(archivo("no es un tipo"), new HttpHeaders(), true);

		assertEquals(MediaType.APPLICATION_OCTET_STREAM, respuesta.getHeaders().getContentType());
		assertTrue(respuesta.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).startsWith("attachment"));
		respuesta.getBody().getInputStream().close();
	}
}
