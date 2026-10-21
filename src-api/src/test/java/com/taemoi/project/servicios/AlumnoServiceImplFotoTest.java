package com.taemoi.project.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.taemoi.project.acceso.GestionPorSede;
import com.taemoi.project.dtos.AlumnoDTO;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.Imagen;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.ImagenRepository;
import com.taemoi.project.services.ImagenService;
import com.taemoi.project.services.TutorService;
import com.taemoi.project.services.impl.AlumnoServiceImpl;
import com.taemoi.project.utils.DocumentoSecurityUtils;

/**
 * Cambiar la foto al editar un alumno: la foto anterior solo se borra cuando la nueva ya se ha guardado. Si la nueva
 * no vale (HEIC, no es una imagen), el alumno conserva la suya, en disco y en base de datos.
 */
@ExtendWith(MockitoExtension.class)
class AlumnoServiceImplFotoTest {

	private static final long ALUMNO_ID = 41L;

	@Mock
	private AlumnoRepository alumnoRepository;

	@Mock
	private ImagenRepository imagenRepository;

	@Mock
	private ImagenService imagenService;

	@Mock
	private TutorService tutorService;

	@Mock
	private GestionPorSede gestionPorSede;

	@InjectMocks
	private AlumnoServiceImpl alumnoService;

	@Test
	void fotoNuevaNoValidaNoBorraLaAnterior() throws Exception {
		Imagen anterior = new Imagen("vieja.webp", "image/webp", "/imagenes/org-1/alumnos/vieja.webp",
				"org-1/alumnos/vieja.webp");
		Alumno alumno = alumnoConFoto(anterior);
		MockMultipartFile heic = new MockMultipartFile("file", "IMG_0001.HEIC", "image/heic", new byte[] { 1, 2 });
		when(imagenService.guardarImagen(heic))
				.thenThrow(new IllegalArgumentException(DocumentoSecurityUtils.MENSAJE_HEIC));

		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> alumnoService.actualizarAlumno(ALUMNO_ID, new AlumnoDTO(), new Date(), heic));

		assertEquals(DocumentoSecurityUtils.MENSAJE_HEIC, error.getMessage());
		verify(imagenService, never()).eliminarImagenDeSistema(any());
		verify(imagenRepository, never()).delete(any());
		assertSame(anterior, alumno.getFotoAlumno());
	}

	@Test
	void fotoNuevaValidaSustituyeALaAnteriorDespuesDeGuardarla() throws Exception {
		Imagen anterior = new Imagen("vieja.webp", "image/webp", "/imagenes/org-1/alumnos/vieja.webp",
				"org-1/alumnos/vieja.webp");
		Imagen nueva = new Imagen("nueva.webp", "image/webp", "/imagenes/org-1/alumnos/nueva.webp",
				"org-1/alumnos/nueva.webp");
		Alumno alumno = alumnoConFoto(anterior);
		MockMultipartFile jpg = new MockMultipartFile("file", "foto.jpg", "image/jpeg", new byte[] { 1, 2 });
		when(imagenService.guardarImagen(jpg)).thenReturn(nueva);
		when(alumnoRepository.save(alumno)).thenReturn(alumno);

		alumnoService.actualizarAlumno(ALUMNO_ID, new AlumnoDTO(), new Date(), jpg);

		InOrder orden = inOrder(imagenService, imagenRepository);
		orden.verify(imagenService).guardarImagen(jpg);
		orden.verify(imagenService).eliminarImagenDeSistema(anterior);
		orden.verify(imagenRepository).delete(anterior);
		assertSame(nueva, alumno.getFotoAlumno());
	}

	@Test
	void siNoSePuedeBorrarLaAnteriorSeBorraLaNuevaYSeRelanza() throws Exception {
		Imagen anterior = new Imagen("vieja.webp", "image/webp", "/imagenes/org-1/alumnos/vieja.webp",
				"org-1/alumnos/vieja.webp");
		Imagen nueva = new Imagen("nueva.webp", "image/webp", "/imagenes/org-1/alumnos/nueva.webp",
				"org-1/alumnos/nueva.webp");
		Alumno alumno = alumnoConFoto(anterior);
		MockMultipartFile jpg = new MockMultipartFile("file", "foto.jpg", "image/jpeg", new byte[] { 1, 2 });
		when(imagenService.guardarImagen(jpg)).thenReturn(nueva);
		RuntimeException fallo = new RuntimeException("Error al eliminar el archivo de imagen");
		doThrow(fallo).when(imagenService).eliminarImagenDeSistema(anterior);

		RuntimeException error = assertThrows(RuntimeException.class,
				() -> alumnoService.actualizarAlumno(ALUMNO_ID, new AlumnoDTO(), new Date(), jpg));

		assertSame(fallo, error);
		verify(imagenService).eliminarImagenDeSistema(nueva);
		verify(imagenRepository, never()).delete(any());
		verify(alumnoRepository, never()).save(any(Alumno.class));
		assertSame(anterior, alumno.getFotoAlumno());
	}

	@Test
	void siFallaBorrarLaFilaAnteriorTambienSeBorraLaNuevaYSeConservaElPrimerError() throws Exception {
		Imagen anterior = new Imagen("vieja.webp", "image/webp", "/imagenes/org-1/alumnos/vieja.webp",
				"org-1/alumnos/vieja.webp");
		Imagen nueva = new Imagen("nueva.webp", "image/webp", "/imagenes/org-1/alumnos/nueva.webp",
				"org-1/alumnos/nueva.webp");
		nueva.setId(77L);
		Alumno alumno = alumnoConFoto(anterior);
		MockMultipartFile jpg = new MockMultipartFile("file", "foto.jpg", "image/jpeg", new byte[] { 1, 2 });
		when(imagenService.guardarImagen(jpg)).thenReturn(nueva);
		RuntimeException fallo = new RuntimeException("fila bloqueada");
		// lenient: el mismo método se llama luego con la nueva (sin stub), y eso es lo que se comprueba.
		lenient().doThrow(fallo).when(imagenRepository).delete(anterior);
		RuntimeException falloAlLimpiar = new RuntimeException("tampoco");
		lenient().doThrow(falloAlLimpiar).when(imagenService).eliminarImagenDeSistema(nueva);

		RuntimeException error = assertThrows(RuntimeException.class,
				() -> alumnoService.actualizarAlumno(ALUMNO_ID, new AlumnoDTO(), new Date(), jpg));

		assertSame(fallo, error);
		assertSame(falloAlLimpiar, error.getSuppressed()[0]);
		verify(imagenRepository).delete(nueva);
		assertSame(anterior, alumno.getFotoAlumno());
	}

	private Alumno alumnoConFoto(Imagen foto) {
		Alumno alumno = new Alumno();
		alumno.setId(ALUMNO_ID);
		alumno.setNombre("Prueba");
		alumno.setFotoAlumno(foto);
		when(alumnoRepository.findById(ALUMNO_ID)).thenReturn(Optional.of(alumno));
		return alumno;
	}
}
