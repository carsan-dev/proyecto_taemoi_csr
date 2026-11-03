package com.taemoi.project.controladores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;

import com.taemoi.project.controllers.RespuestaFotoAlumno;
import com.taemoi.project.entities.Imagen;
import com.taemoi.project.services.ImagenService;

/**
 * Sin fichero, la foto del alumno redirige a su URL guardada solo si es de un origen propio
 * ({@code app.imagenes.redireccion.origenes}, por defecto {@code app.base.url} y {@code app.frontend.base-url}): mismo
 * esquema, host exacto (sin distinguir mayúsculas) y mismo puerto. Cualquier otro host, un usuario en la URL o barras
 * invertidas: como sin foto (404 sin {@code Location}).
 */
class RespuestaFotoAlumnoRedireccionTest {

	private static final String PROPIO = "https://moiskimdo.es";
	private static final String LOCAL = "http://localhost:8080";

	@TempDir
	Path imagenes;

	private ImagenService imagenService;

	@BeforeEach
	void setUp() {
		imagenService = mock(ImagenService.class);
		// Sin fichero en disco: la ruta resuelta no existe.
		when(imagenService.resolverRutaArchivo(any(Imagen.class)))
				.thenAnswer(i -> imagenes.resolve(((Imagen) i.getArgument(0)).getRuta()));
	}

	@Test
	void urlDeUnOrigenPropioHttps_redirige() throws Exception {
		RespuestaFotoAlumno respuesta = respuesta(PROPIO, PROPIO + "," + PROPIO);
		for (String url : List.of(PROPIO + "/imagenes/alumnos/x.webp", "https://MoisKimdo.ES/imagenes/alumnos/x.webp",
				"https://moiskimdo.es:443/imagenes/alumnos/x.webp")) {
			ResponseEntity<Resource> r = respuesta.paraFamiliaOGestion(foto(url), null);
			assertEquals(302, r.getStatusCode().value(), url);
			assertEquals(URI.create(url), r.getHeaders().getLocation(), url);
		}
	}

	@Test
	void urlRelativaDeImagenes_redirigeAlOrigenPropio() throws Exception {
		ResponseEntity<Resource> r = respuesta(PROPIO, PROPIO).paraFamiliaOGestion(foto("/imagenes/org-1/alumnos/x.webp"),
				null);
		assertEquals(302, r.getStatusCode().value());
		assertEquals(URI.create(PROPIO + "/imagenes/org-1/alumnos/x.webp"), r.getHeaders().getLocation());
	}

	@Test
	void httpSoloSiElOrigenPropioEsHttp() throws Exception {
		ResponseEntity<Resource> local = respuesta(LOCAL, LOCAL + ",http://localhost:4200")
				.paraFamiliaOGestion(foto("http://localhost:4200/imagenes/x.webp"), null);
		assertEquals(302, local.getStatusCode().value());

		sinFoto(respuesta(PROPIO, PROPIO), "http://moiskimdo.es/imagenes/x.webp");
	}

	@Test
	void urlDeOtroHostOConTrampas_comoSinFoto() throws Exception {
		RespuestaFotoAlumno respuesta = respuesta(PROPIO, PROPIO + "," + LOCAL);
		for (String url : List.of(
				"https://otro.example/x.webp",
				"http://otro.example/x.webp",
				"https://moiskimdo.es@otro.example/x.webp",
				"https://usuario@moiskimdo.es/x.webp",
				"https://usuario:clave@moiskimdo.es/x.webp",
				"https://moiskimdo.es.otro.example/x.webp",
				"https://otro-moiskimdo.es/x.webp",
				"https://xmoiskimdo.es/x.webp",
				"https://moiskimdo.es\\@otro.example/x.webp",
				"https:\\\\otro.example\\x.webp",
				"https://moiskimdo.es:8443/x.webp",
				"https://localhost:8080/x.webp",
				"ftp://moiskimdo.es/x.webp",
				"https:///x.webp",
				"https://moiskimdo.es%2eotro.example/x.webp",
				" https://otro.example/x.webp")) {
			sinFoto(respuesta, url);
		}
	}

	@Test
	void fotoHeredadaConUrlDeOtroHost_noRedirigeAEseHost() throws Exception {
		// Sin prefijo org-<id>: queda el respaldo por nombre, que va a /imagenes/alumnos/ del propio servidor.
		Imagen heredada = new Imagen("vieja.webp", "image/webp", "https://otro.example/imagenes/alumnos/vieja.webp",
				"alumnos/no-existe.webp");
		heredada.setId(3L);
		ResponseEntity<Resource> r = respuesta(PROPIO, PROPIO).paraFamiliaOGestion(heredada, null);
		URI destino = r.getHeaders().getLocation();
		if (destino != null) {
			assertFalse(destino.toString().contains("otro.example"), destino.toString());
		}
	}

	private RespuestaFotoAlumno respuesta(String baseUrl, String origenes) {
		return new RespuestaFotoAlumno(imagenService, baseUrl, imagenes.toString(), imagenes.toString(), origenes);
	}

	private static Imagen foto(String url) {
		Imagen imagen = new Imagen("x.webp", "image/webp", url, "org-1/alumnos/no-existe.webp");
		imagen.setId(9L);
		return imagen;
	}

	private void sinFoto(RespuestaFotoAlumno respuesta, String url) throws Exception {
		for (Integer ancho : new Integer[] { null, 300 }) {
			ResponseEntity<Resource> r = respuesta.paraFamiliaOGestion(foto(url), ancho);
			assertEquals(404, r.getStatusCode().value(), url);
			assertNull(r.getHeaders().getLocation(), url);
			assertNull(r.getBody(), url);
		}
	}
}
