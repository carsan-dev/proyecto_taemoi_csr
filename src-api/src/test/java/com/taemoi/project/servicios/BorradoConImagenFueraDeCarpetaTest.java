package com.taemoi.project.servicios;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.Evento;
import com.taemoi.project.entities.Imagen;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.EventoRepository;
import com.taemoi.project.repositories.ImagenRepository;
import com.taemoi.project.services.impl.AlumnoServiceImpl;
import com.taemoi.project.services.impl.EventoServiceImpl;
import com.taemoi.project.services.impl.ImagenServiceImpl;

/**
 * Una foto cuya fila apunta fuera de las carpetas de imágenes no corta el borrado de su alumno o evento: la fila se
 * quita y el fichero de fuera se queda donde está.
 */
@ExtendWith(MockitoExtension.class)
class BorradoConImagenFueraDeCarpetaTest {

	@TempDir
	Path base;

	@TempDir
	Path fuera;

	@Mock
	private AlumnoRepository alumnoRepository;

	@Mock
	private EventoRepository eventoRepository;

	@Mock
	private ImagenRepository imagenRepository;

	@InjectMocks
	private AlumnoServiceImpl alumnoService;

	@InjectMocks
	private EventoServiceImpl eventoService;

	private Path secreto;
	private Imagen imagen;

	@BeforeEach
	void setUp() throws IOException {
		ImagenServiceImpl imagenService = new ImagenServiceImpl();
		ReflectionTestUtils.setField(imagenService, "directorioImagenesLinux", base.toString());
		ReflectionTestUtils.setField(imagenService, "directorioImagenesWindows", base.toString());
		ReflectionTestUtils.setField(alumnoService, "imagenService", imagenService);
		ReflectionTestUtils.setField(eventoService, "imagenService", imagenService);
		secreto = Files.write(fuera.resolve("secreto.webp"), new byte[] { 1, 2, 3 });
		imagen = new Imagen("secreto.webp", "image/webp", null, secreto.toString());
		imagen.setId(501L);
	}

	@Test
	void quitarLaFotoDelAlumno_terminaSinBorrarElFicheroDeFuera() {
		Alumno alumno = new Alumno();
		alumno.setId(41L);
		alumno.setFotoAlumno(imagen);
		when(alumnoRepository.findById(41L)).thenReturn(Optional.of(alumno));

		assertDoesNotThrow(() -> alumnoService.eliminarImagenAlumno(41L));

		assertNull(alumno.getFotoAlumno());
		verify(alumnoRepository).save(alumno);
		verify(imagenRepository).delete(imagen);
		assertTrue(Files.exists(secreto));
	}

	@Test
	void quitarLaImagenDelEvento_terminaSinBorrarElFicheroDeFuera() {
		Evento evento = new Evento();
		evento.setId(42L);
		evento.setFotoEvento(imagen);
		when(eventoRepository.findById(42L)).thenReturn(Optional.of(evento));

		assertDoesNotThrow(() -> eventoService.eliminarImagenEvento(42L));

		assertNull(evento.getFotoEvento());
		verify(imagenRepository).delete(imagen);
		assertTrue(Files.exists(secreto));
	}
}
