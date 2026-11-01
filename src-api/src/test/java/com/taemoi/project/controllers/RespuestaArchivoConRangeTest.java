package com.taemoi.project.controllers;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.abort;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

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

	/** Lo que vería una lectura si otra petición sustituye el fichero entre medir y servir: tamaño viejo, contenido nuevo. */
	private MaterialExamenArchivo sustituidoTrasMedir(byte[] contenidoNuevo) throws IOException {
		MaterialExamenArchivo medido = archivo("video/mp4");
		Files.write(medido.getPath(), contenidoNuevo);
		return medido;
	}

	@Test
	void sinRange_laLongitudEsLaDelFicheroAbierto_noLaMedidaAntes() throws Exception {
		byte[] nuevo = "abcdefghijklmnopqrst".getBytes(StandardCharsets.US_ASCII);

		ResponseEntity<Resource> respuesta = RespuestaArchivoConRange.servir(sustituidoTrasMedir(nuevo), new HttpHeaders(), false);

		assertEquals(20L, respuesta.getHeaders().getContentLength());
		assertArrayEquals(nuevo, cuerpo(respuesta));
	}

	@Test
	void conRange_elRangoSeCalculaSobreElFicheroAbierto() throws Exception {
		byte[] nuevo = "abcdefghijklmnopqrst".getBytes(StandardCharsets.US_ASCII);

		ResponseEntity<Resource> respuesta = RespuestaArchivoConRange.servir(sustituidoTrasMedir(nuevo), conRange("bytes=12-"), false);

		assertEquals(HttpStatus.PARTIAL_CONTENT, respuesta.getStatusCode());
		assertEquals("bytes 12-19/20", respuesta.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE));
		assertEquals(8L, respuesta.getHeaders().getContentLength());
		assertArrayEquals("mnopqrst".getBytes(StandardCharsets.US_ASCII), cuerpo(respuesta));
	}

	@Test
	void unFicheroMasCorto_416ConSuTamanoReal() throws Exception {
		MaterialExamenArchivo medido = sustituidoTrasMedir("abc".getBytes(StandardCharsets.US_ASCII));

		ResponseEntity<Resource> respuesta = RespuestaArchivoConRange.servir(medido, conRange("bytes=5-"), false);

		assertEquals(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE, respuesta.getStatusCode());
		assertEquals("bytes */3", respuesta.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE));
	}

	/** Una sustitución es un movimiento atómico: quien ya abrió el fichero sigue con el contenido viejo, entero. */
	@Test
	void unaSustitucionConElFicheroAbierto_noMezclaContenidos() throws Exception {
		MaterialExamenArchivo abierto = archivo("video/mp4");
		ResponseEntity<Resource> respuesta = RespuestaArchivoConRange.servir(abierto, new HttpHeaders(), false);
		Path nuevo = Files.write(carpeta.resolve(".subida-x"), "abcdefghijklmnopqrst".getBytes(StandardCharsets.US_ASCII));
		try {
			Files.move(nuevo, abierto.getPath(), StandardCopyOption.ATOMIC_MOVE);
		} catch (AccessDeniedException e) {
			respuesta.getBody().getInputStream().close();
			abort("Windows no deja sustituir un fichero abierto");
		}

		assertEquals(10L, respuesta.getHeaders().getContentLength());
		assertArrayEquals(CONTENIDO, cuerpo(respuesta));
	}

	@Test
	void unFicheroQueYaNoEsta_fallaComoAntes() {
		MaterialExamenArchivo borrado = new MaterialExamenArchivo(carpeta.resolve("no.mp4"), "no.mp4", "video/mp4", 10);

		assertThrows(IOException.class, () -> RespuestaArchivoConRange.servir(borrado, new HttpHeaders(), false));
	}

	@Test
	void descarga_comoAdjunto_yTipoRaroComoBinario() throws Exception {
		ResponseEntity<Resource> respuesta = RespuestaArchivoConRange.servir(archivo("no es un tipo"), new HttpHeaders(), true);

		assertEquals(MediaType.APPLICATION_OCTET_STREAM, respuesta.getHeaders().getContentType());
		assertTrue(respuesta.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).startsWith("attachment"));
		respuesta.getBody().getInputStream().close();
	}
}
