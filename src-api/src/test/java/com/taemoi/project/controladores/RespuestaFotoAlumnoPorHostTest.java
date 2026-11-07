package com.taemoi.project.controladores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.taemoi.project.controllers.RespuestaFotoAlumno;
import com.taemoi.project.entities.Imagen;
import com.taemoi.project.organizacion.HostDeLaPeticion;
import com.taemoi.project.services.ImagenService;

/**
 * Fase 4, pieza 3: sin fichero, la redirección de la foto del alumno en el Host X de {@code organizacion_dominio} va a
 * su propio origen, y ese origen cuenta como propio. En moiskimdo.es, igual que antes: la base es {@code app.base.url}
 * y la lista de orígenes no se amplía. Un Host ajeno nunca.
 */
class RespuestaFotoAlumnoPorHostTest {

	private static final String PROPIO = "https://moiskimdo.es";

	@TempDir
	Path imagenes;

	private ImagenService imagenService;

	@BeforeEach
	void setUp() {
		imagenService = mock(ImagenService.class);
		when(imagenService.resolverRutaArchivo(any(Imagen.class)))
				.thenAnswer(i -> imagenes.resolve(((Imagen) i.getArgument(0)).getRuta()));
	}

	@AfterEach
	void limpiar() {
		RequestContextHolder.resetRequestAttributes();
	}

	@Test
	void enHostX_urlRelativa_redirigeASuOrigen() throws Exception {
		enHost("x.example", 7L, false);
		ResponseEntity<Resource> r = respuesta(PROPIO).paraFamiliaOGestion(foto("/imagenes/org-7/alumnos/x.webp"), null);
		assertEquals(302, r.getStatusCode().value());
		assertEquals(URI.create("https://x.example/imagenes/org-7/alumnos/x.webp"), r.getHeaders().getLocation());
	}

	@Test
	void enHostX_suOrigenCuentaComoPropio_yUnHostAjenoNo() throws Exception {
		enHost("x.example", 7L, false);
		ResponseEntity<Resource> r = respuesta(PROPIO).paraFamiliaOGestion(foto("https://x.example/imagenes/x.webp"),
				null);
		assertEquals(302, r.getStatusCode().value());
		assertEquals(URI.create("https://x.example/imagenes/x.webp"), r.getHeaders().getLocation());

		sinFoto(respuesta(PROPIO), "https://evil.example/imagenes/x.webp");
	}

	@Test
	void enMoiskimdo_comoAntes() throws Exception {
		enHost("moiskimdo.es", 1L, true);
		ResponseEntity<Resource> r = respuesta(PROPIO).paraFamiliaOGestion(foto("/imagenes/org-1/alumnos/x.webp"), null);
		assertEquals(URI.create(PROPIO + "/imagenes/org-1/alumnos/x.webp"), r.getHeaders().getLocation());

		sinFoto(respuesta(PROPIO), "https://x.example/imagenes/x.webp");
		// Con una lista configurada sin app.base.url, la base no se cuela como origen propio (como antes).
		sinFoto(new RespuestaFotoAlumno(imagenService, PROPIO, imagenes.toString(), imagenes.toString(),
				"https://otro-propio.example"), PROPIO + "/imagenes/x.webp");
	}

	private void enHost(String host, Long escuela, boolean porDefecto) {
		MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/api/alumnos/9/imagen");
		peticion.setScheme("https");
		peticion.setServerName(host);
		peticion.setServerPort(443);
		peticion.setAttribute(HostDeLaPeticion.ATRIBUTO, new HostDeLaPeticion.Registrado(host, escuela, porDefecto));
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(peticion));
	}

	private RespuestaFotoAlumno respuesta(String baseUrl) {
		return new RespuestaFotoAlumno(imagenService, baseUrl, imagenes.toString(), imagenes.toString(), baseUrl);
	}

	private static Imagen foto(String url) {
		Imagen imagen = new Imagen("x.webp", "image/webp", url, "org-1/alumnos/no-existe.webp");
		imagen.setId(9L);
		return imagen;
	}

	private void sinFoto(RespuestaFotoAlumno respuesta, String url) throws Exception {
		ResponseEntity<Resource> r = respuesta.paraFamiliaOGestion(foto(url), null);
		assertEquals(404, r.getStatusCode().value(), url);
		assertNull(r.getHeaders().getLocation(), url);
	}
}
