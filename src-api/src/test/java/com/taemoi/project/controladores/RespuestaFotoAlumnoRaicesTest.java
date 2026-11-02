package com.taemoi.project.controladores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;

import com.taemoi.project.controllers.RespuestaFotoAlumno;
import com.taemoi.project.entities.Imagen;
import com.taemoi.project.services.ImagenService;

/**
 * Decisión 422: con las carpetas de imágenes configuradas inexistentes no hay ninguna raíz válida. Una foto con su
 * fichero en disco no se lee, la respuesta es 404 sin redirección, sin 500 y sin la ruta interna.
 */
class RespuestaFotoAlumnoRaicesTest {

	@TempDir
	Path fuera;

	@Test
	void raizConfiguradaInexistente_404SinRedireccionNiRutaInterna() throws Exception {
		Path fichero = Files.write(fuera.resolve("foto.png"), new byte[] { 1, 2, 3 });
		String inexistente = fuera.resolve("no-existe-" + UUID.randomUUID()).toString() + "/";
		ImagenService imagenService = mock(ImagenService.class);
		when(imagenService.resolverRutaArchivo(any(Imagen.class))).thenReturn(fichero);
		RespuestaFotoAlumno respuesta = new RespuestaFotoAlumno(imagenService, "http://localhost:8080", inexistente,
				inexistente);
		Imagen imagen = new Imagen("foto.png", "image/png", null, fichero.toString());
		imagen.setId(7L);

		for (Integer ancho : new Integer[] { null, 300 }) {
			for (ResponseEntity<Resource> r : List.of(respuesta.paraFamiliaOGestion(imagen, ancho),
					respuesta.paraProfesor(imagen, ancho))) {
				assertEquals(404, r.getStatusCode().value());
				assertNull(r.getHeaders().getLocation());
				assertNull(r.getBody());
				r.getHeaders().forEach((nombre, valores) -> valores
						.forEach(valor -> assertFalse(valor.contains(fuera.toString()), nombre)));
			}
		}
	}
}
