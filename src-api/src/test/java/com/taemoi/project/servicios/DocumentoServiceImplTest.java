package com.taemoi.project.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.mock.web.MockMultipartFile;

import com.taemoi.project.entities.Evento;
import com.taemoi.project.entities.Documento;
import com.taemoi.project.repositories.DocumentoRepository;
import com.taemoi.project.services.impl.DocumentoServiceImpl;

@ExtendWith(MockitoExtension.class)
class DocumentoServiceImplTest {

	@Mock
	private DocumentoRepository documentoRepository;

	@InjectMocks
	private DocumentoServiceImpl documentoService;

	@TempDir
	Path tempDir;

	@Test
	void obtenerRecursoDocumento_debeResolverRutaAbsolutaCaseInsensitive() throws Exception {
		ReflectionTestUtils.setField(documentoService, "directorioDocumentosLinux", tempDir.toString());
		ReflectionTestUtils.setField(documentoService, "directorioDocumentosWindows", tempDir.toString());

		Path carpetaAlumno = Files.createDirectories(
				tempDir.resolve("Documentos_Alumnos_Moiskimdo").resolve("134_LOLA_ROMAN_RUIZ"));
		Path archivoReal = Files.createFile(carpetaAlumno.resolve("AL134_delantera_dni_lola.jpg"));

		Documento documento = crearDocumento(
				carpetaAlumno.resolve("AL134_DELANTERA_DNI_LOLA.jpg").toString(),
				"AL134_DELANTERA_DNI_LOLA");

		Resource recurso = documentoService.obtenerRecursoDocumento(documento);

		assertTrue(recurso.exists());
		assertEquals(archivoReal.toRealPath(), Path.of(recurso.getURI()).toRealPath());
	}

	@Test
	void obtenerRecursoDocumento_debeMantenerCompatibilidadConRutaRelativa() throws Exception {
		ReflectionTestUtils.setField(documentoService, "directorioDocumentosLinux", tempDir.toString());
		ReflectionTestUtils.setField(documentoService, "directorioDocumentosWindows", tempDir.toString());

		Path carpetaAlumno = Files.createDirectories(
				tempDir.resolve("Documentos_Alumnos_Moiskimdo").resolve("134_LOLA_ROMAN_RUIZ"));
		Path archivoReal = Files.createFile(carpetaAlumno.resolve("AL134_firma.jpg"));

		Documento documento = crearDocumento(
				"Documentos_Alumnos_Moiskimdo/134_LOLA_ROMAN_RUIZ/AL134_FIRMA.jpg",
				"AL134_FIRMA");

		Resource recurso = documentoService.obtenerRecursoDocumento(documento);

		assertTrue(recurso.exists());
		assertEquals(archivoReal.toRealPath(), Path.of(recurso.getURI()).toRealPath());
	}

	@Test
	void obtenerRecursoDocumento_debeResolverRutaWindowsLegacyHaciaDirectorioActual() throws Exception {
		ReflectionTestUtils.setField(documentoService, "directorioDocumentosLinux", tempDir.toString());
		ReflectionTestUtils.setField(documentoService, "directorioDocumentosWindows", tempDir.toString());

		Path carpetaAlumno = Files.createDirectories(
				tempDir.resolve("Documentos_Alumnos_Moiskimdo").resolve("733_MOISKIMDO_TAEKWONDO"));
		Path archivoReal = Files.createFile(carpetaAlumno.resolve("dni-frontal.pdf"));

		Documento documento = crearDocumento(
				"C:\\Users\\Carlos\\Desktop\\Documentos_Alumnos_Moiskimdo\\733_MOISKIMDO_TAEKWONDO\\dni-frontal.pdf",
				"dni-frontal.pdf");

		Resource recurso = documentoService.obtenerRecursoDocumento(documento);

		assertTrue(recurso.exists());
		assertEquals(archivoReal.toRealPath(), Path.of(recurso.getURI()).toRealPath());
	}

	@Test
	void obtenerRecursoDocumento_debeResolverRutaRelativaConBackslashes() throws Exception {
		ReflectionTestUtils.setField(documentoService, "directorioDocumentosLinux", tempDir.toString());
		ReflectionTestUtils.setField(documentoService, "directorioDocumentosWindows", tempDir.toString());

		Path carpetaAlumno = Files.createDirectories(
				tempDir.resolve("Documentos_Alumnos_Moiskimdo").resolve("733_MOISKIMDO_TAEKWONDO"));
		Path archivoReal = Files.createFile(carpetaAlumno.resolve("dni-trasera.pdf"));

		Documento documento = crearDocumento(
				"Documentos_Alumnos_Moiskimdo\\733_MOISKIMDO_TAEKWONDO\\dni-trasera.pdf",
				"dni-trasera.pdf");

		Resource recurso = documentoService.obtenerRecursoDocumento(documento);

		assertTrue(recurso.exists());
		assertEquals(archivoReal.toRealPath(), Path.of(recurso.getURI()).toRealPath());
	}

	@Test
	void obtenerRecursoDocumento_debeLanzarExcepcionSiArchivoNoExiste() {
		ReflectionTestUtils.setField(documentoService, "directorioDocumentosLinux", tempDir.toString());
		ReflectionTestUtils.setField(documentoService, "directorioDocumentosWindows", tempDir.toString());

		Documento documento = crearDocumento(
				tempDir.resolve("Documentos_Alumnos_Moiskimdo").resolve("inexistente.pdf").toString(),
				"inexistente");

		RuntimeException ex = assertThrows(RuntimeException.class, () -> documentoService.obtenerRecursoDocumento(documento));
		assertTrue(ex.getMessage().contains("No se pudo cargar el documento"));
	}

	@Test
	void eliminarDocumento_debeEliminarArchivoConRutaCaseInsensitive() throws Exception {
		ReflectionTestUtils.setField(documentoService, "directorioDocumentosLinux", tempDir.toString());
		ReflectionTestUtils.setField(documentoService, "directorioDocumentosWindows", tempDir.toString());

		Path carpetaAlumno = Files.createDirectories(
				tempDir.resolve("Documentos_Alumnos_Moiskimdo").resolve("134_LOLA_ROMAN_RUIZ"));
		Path archivoReal = Files.createFile(carpetaAlumno.resolve("AL134_firma.jpg"));

		Documento documento = crearDocumento(
				carpetaAlumno.resolve("AL134_FIRMA.jpg").toString(),
				"AL134_FIRMA");

		documentoService.eliminarDocumento(documento);

		assertFalse(Files.exists(archivoReal));
		verify(documentoRepository).delete(documento);
	}

	@Test
	void obtenerRecursoDocumento_debeRechazarRutaAbsolutaFueraDelDirectorioPermitido() {
		ReflectionTestUtils.setField(documentoService, "directorioDocumentosLinux", tempDir.toString());
		ReflectionTestUtils.setField(documentoService, "directorioDocumentosWindows", tempDir.toString());

		Documento documento = crearDocumento(Path.of("C:/fuera/permitido/secreto.pdf").toString(), "secreto.pdf");

		RuntimeException ex = assertThrows(RuntimeException.class, () -> documentoService.obtenerRecursoDocumento(documento));
		assertTrue(ex.getMessage().contains("No se pudo cargar el documento"));
	}

	@Test
	void guardarDocumentoEvento_debeRechazarTipoNoPermitido() {
		ReflectionTestUtils.setField(documentoService, "directorioDocumentosLinux", tempDir.toString());
		ReflectionTestUtils.setField(documentoService, "directorioDocumentosWindows", tempDir.toString());
		ReflectionTestUtils.setField(documentoService, "baseUrl", "http://localhost:8080");

		Evento evento = new Evento();
		evento.setId(9L);
		evento.setTitulo("Festival seguro");

		MultipartFile archivo = new MockMultipartFile(
				"archivo",
				"malicioso.html",
				"text/html",
				"<script>alert('xss')</script>".getBytes());

		assertThrows(IllegalArgumentException.class, () -> documentoService.guardarDocumentoEvento(evento, archivo));
	}

	@Test
	void guardarDocumentoEvento_debeAceptarDocxConMimeZipComun() throws Exception {
		ReflectionTestUtils.setField(documentoService, "directorioDocumentosLinux", tempDir.toString());
		ReflectionTestUtils.setField(documentoService, "directorioDocumentosWindows", tempDir.toString());
		ReflectionTestUtils.setField(documentoService, "baseUrl", "http://localhost:8080");
		when(documentoRepository.save(org.mockito.ArgumentMatchers.any(Documento.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		Evento evento = new Evento();
		evento.setId(11L);
		evento.setTitulo("Festival seguro");

		MultipartFile archivo = new MockMultipartFile(
				"archivo",
				"bases.docx",
				"application/zip",
				"contenido-docx".getBytes());

		Documento documento = documentoService.guardarDocumentoEvento(evento, archivo);

		assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", documento.getTipo());
		assertTrue(documento.getNombre().endsWith(".docx"));
	}

	private Documento crearDocumento(String ruta, String nombre) {
		Documento documento = new Documento();
		documento.setId(554L);
		documento.setRuta(ruta);
		documento.setNombre(nombre);
		documento.setTipo("image/jpeg");
		return documento;
	}
}
