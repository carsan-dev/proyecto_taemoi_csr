package com.taemoi.project.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import com.taemoi.project.entities.Imagen;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.services.impl.ImagenServiceImpl;

class ImagenServiceImplTest {

	@TempDir
	Path tempDir;

	ImagenServiceImpl imagenService;

	@BeforeEach
	void setUp() {
		imagenService = new ImagenServiceImpl();
		ReflectionTestUtils.setField(imagenService, "directorioImagenesLinux", tempDir.toString());
		ReflectionTestUtils.setField(imagenService, "directorioImagenesWindows", tempDir.toString());
		TenantContext.set(1L);
	}

	@AfterEach
	void limpiar() {
		TenantContext.clear();
	}

	@Test
	void guardarImagenes_debeMantenerLasEscriturasDentroDeLosDirectoriosPermitidos() throws Exception {
		byte[] contenidoImagen = crearPng();
		Imagen imagenAlumno = imagenService.guardarImagen(new MockMultipartFile(
				"archivo", "../../foto.png", "image/png", contenidoImagen));
		Imagen imagenEvento = imagenService.guardarImagenEvento(new MockMultipartFile(
				"archivo", "..\\..\\cartel.png", "image/png", contenidoImagen));

		assertRutaDentroDelDirectorio(imagenAlumno, tempDir.resolve("org-1").resolve("alumnos"));
		assertRutaDentroDelDirectorio(imagenEvento, tempDir.resolve("org-1").resolve("eventos"));
	}

	@Test
	void guardarImagenes_enOtraEscuelaGuardaRutaRelativaConSuPrefijo() throws Exception {
		TenantContext.set(2L);
		Imagen alumno = imagenService.guardarImagen(new MockMultipartFile("archivo", "foto.png", "image/png", crearPng()));
		Imagen evento = imagenService.guardarImagenEvento(new MockMultipartFile("archivo", "cartel.png", "image/png", crearPng()));

		assertEquals("org-2/alumnos/" + alumno.getNombre(), alumno.getRuta());
		assertEquals("/imagenes/org-2/alumnos/" + alumno.getNombre(), alumno.getUrl());
		assertEquals("org-2/eventos/" + evento.getNombre(), evento.getRuta());
		assertEquals("/imagenes/org-2/eventos/" + evento.getNombre(), evento.getUrl());
		assertEquals(tempDir.resolve(alumno.getRuta()).toAbsolutePath().normalize(), imagenService.resolverRutaArchivo(alumno));
	}

	@Test
	void guardarImagen_sinEscuelaActivaFallaCerrado() {
		TenantContext.clear();
		assertThrows(IllegalStateException.class, () -> imagenService.guardarImagen(
				new MockMultipartFile("archivo", "foto.png", "image/png", crearPng())));
	}

	@Test
	void resolverYEliminar_mantienenLasRutasAbsolutasHeredadas() throws Exception {
		Path heredada = Files.createDirectories(tempDir.resolve("alumnos")).resolve("vieja.webp");
		Files.write(heredada, new byte[] { 1 });
		Imagen imagen = new Imagen("vieja.webp", "image/webp", "http://antiguo/imagenes/alumnos/vieja.webp",
				heredada.toString());

		assertEquals(heredada.toAbsolutePath().normalize(), imagenService.resolverRutaArchivo(imagen));
		imagenService.eliminarImagenDeSistema(imagen);
		assertFalse(Files.exists(heredada));
	}

	@Test
	void eliminarImagen_conRutaRelativaBorraDentroDeLaBaseYNoSaleDeElla() throws Exception {
		Imagen imagen = imagenService.guardarImagen(new MockMultipartFile("archivo", "foto.png", "image/png", crearPng()));
		Path archivo = tempDir.resolve(imagen.getRuta());
		assertTrue(Files.exists(archivo));

		imagenService.eliminarImagenDeSistema(imagen);
		assertFalse(Files.exists(archivo));

		Imagen maliciosa = new Imagen("x.webp", "image/webp", null, "../fuera.webp");
		assertNull(imagenService.resolverRutaArchivo(maliciosa));
		assertThrows(IllegalArgumentException.class, () -> imagenService.eliminarImagenDeSistema(maliciosa));
	}

	private byte[] crearPng() {
		try {
			BufferedImage imagen = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			ImageIO.write(imagen, "png", output);
			return output.toByteArray();
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private void assertRutaDentroDelDirectorio(Imagen imagen, Path directorioPermitido) {
		Path directorioSeguro = directorioPermitido.toAbsolutePath().normalize();
		assertFalse(Path.of(imagen.getRuta()).isAbsolute(), "La ruta guardada debe ser relativa");
		Path rutaGuardada = tempDir.resolve(imagen.getRuta()).toAbsolutePath().normalize();
		assertTrue(rutaGuardada.startsWith(directorioSeguro));
		assertEquals(directorioSeguro, rutaGuardada.getParent());
		assertTrue(Files.exists(rutaGuardada));
	}
}
