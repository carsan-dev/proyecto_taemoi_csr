package com.taemoi.project.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import com.taemoi.project.entities.Imagen;
import com.taemoi.project.services.impl.ImagenServiceImpl;

class ImagenServiceImplTest {

	@TempDir
	Path tempDir;

	@Test
	void guardarImagenes_debeMantenerLasEscriturasDentroDeLosDirectoriosPermitidos() throws Exception {
		ImagenServiceImpl imagenService = new ImagenServiceImpl();
		ReflectionTestUtils.setField(imagenService, "directorioImagenesLinux", tempDir.toString());
		ReflectionTestUtils.setField(imagenService, "directorioImagenesWindows", tempDir.toString());
		ReflectionTestUtils.setField(imagenService, "baseUrl", "http://localhost:8080");

		byte[] contenidoImagen = crearPng();
		Imagen imagenAlumno = imagenService.guardarImagen(new MockMultipartFile(
				"archivo", "../../foto.png", "image/png", contenidoImagen));
		Imagen imagenEvento = imagenService.guardarImagenEvento(new MockMultipartFile(
				"archivo", "..\\..\\cartel.png", "image/png", contenidoImagen));

		assertRutaDentroDelDirectorio(imagenAlumno, tempDir.resolve("alumnos"));
		assertRutaDentroDelDirectorio(imagenEvento, tempDir.resolve("eventos"));
	}

	private byte[] crearPng() throws Exception {
		BufferedImage imagen = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ImageIO.write(imagen, "png", output);
		return output.toByteArray();
	}

	private void assertRutaDentroDelDirectorio(Imagen imagen, Path directorioPermitido) {
		Path directorioSeguro = directorioPermitido.toAbsolutePath().normalize();
		Path rutaGuardada = Path.of(imagen.getRuta()).toAbsolutePath().normalize();
		assertTrue(rutaGuardada.startsWith(directorioSeguro));
		assertEquals(directorioSeguro, rutaGuardada.getParent());
		assertTrue(Files.exists(rutaGuardada));
	}
}
