package com.taemoi.project.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.config.ExamMaterialBlockConfig;
import com.taemoi.project.dtos.response.MaterialExamenDTO;
import com.taemoi.project.entities.AlumnoDeporte;
import com.taemoi.project.entities.Deporte;
import com.taemoi.project.entities.Grado;
import com.taemoi.project.entities.TipoGrado;
import com.taemoi.project.services.AlumnoDeporteService;
import com.taemoi.project.services.impl.MaterialExamenServiceImpl;

@ExtendWith(MockitoExtension.class)
class MaterialExamenServiceImplTest {

	@Mock
	private AlumnoDeporteService alumnoDeporteService;

	@TempDir
	Path tempDir;

	private MaterialExamenServiceImpl service;

	@BeforeEach
	void setUp() {
		service = new MaterialExamenServiceImpl();
		ReflectionTestUtils.setField(service, "alumnoDeporteService", alumnoDeporteService);
		ReflectionTestUtils.setField(service, "examMaterialBlockConfig", new ExamMaterialBlockConfig());
		ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
		ReflectionTestUtils.setField(service, "directorioDocumentosWindows", tempDir.toString());
		ReflectionTestUtils.setField(service, "directorioDocumentosLinux", tempDir.toString());
	}

	@Test
	void shouldResolveTemarioAndOrderedVideosWithoutIndex() throws Exception {
		mockAlumnoConDeporte(10L, Deporte.TAEKWONDO, TipoGrado.BLANCO_AMARILLO);

		Path bloque = crearBloque("taekwondo", "b01_inicio_a_amarillo");
		Files.createDirectories(bloque.resolve("temario"));
		Files.writeString(bloque.resolve("temario").resolve("temario.pdf"), "temario");
		Files.createDirectories(bloque.resolve("videos"));
		Files.writeString(bloque.resolve("videos").resolve("02_tecnicas_base.mp4"), "v2");
		Files.writeString(bloque.resolve("videos").resolve("01_presentacion.mp4"), "v1");

		MaterialExamenDTO material = service.obtenerMaterialExamen(10L, Deporte.TAEKWONDO);

		assertEquals("b01_inicio_a_amarillo", material.getBloqueId());
		assertEquals("BLANCO_AMARILLO", material.getGradoActual());
		assertNotNull(material.getTemario());
		assertEquals("temario.pdf", material.getTemario().getFileName());
		assertEquals(2, material.getVideos().size());
		assertEquals("01_presentacion.mp4", material.getVideos().get(0).getId());
		assertEquals("presentacion", material.getVideos().get(0).getTitle());
		assertEquals(1, material.getVideos().get(0).getOrder());
		assertEquals("02_tecnicas_base.mp4", material.getVideos().get(1).getId());
		assertEquals(2, material.getVideos().get(1).getOrder());
		assertNotNull(material.getDocumentos());
		assertEquals(1, material.getDocumentos().size());
		assertEquals("temario.pdf", material.getDocumentos().get(0).getFileName());
		assertEquals(true, material.getDocumentos().get(0).isPreviewable());
	}

	@Test
	void shouldStripCompSuffixFromGeneratedVideoTitles() throws Exception {
		mockAlumnoConDeporte(14L, Deporte.TAEKWONDO, TipoGrado.BLANCO_AMARILLO);

		Path bloque = crearBloque("taekwondo", "b01_inicio_a_amarillo");
		Files.createDirectories(bloque.resolve("videos"));
		Files.writeString(bloque.resolve("videos").resolve("01_SOGUIS_COMP.mp4"), "v1");

		MaterialExamenDTO material = service.obtenerMaterialExamen(14L, Deporte.TAEKWONDO);

		assertEquals(1, material.getVideos().size());
		assertEquals("SOGUIS", material.getVideos().get(0).getTitle());
	}

	@Test
	void shouldUseIndexJsonTitlesAndOrderWhenPresent() throws Exception {
		mockAlumnoConDeporte(11L, Deporte.TAEKWONDO, TipoGrado.AMARILLO);

		Path bloque = crearBloque("taekwondo", "b02_amarillo_a_naranja");
		Files.createDirectories(bloque.resolve("videos"));
		Files.writeString(bloque.resolve("videos").resolve("01_presentacion.mp4"), "v1");
		Files.writeString(bloque.resolve("videos").resolve("02_tecnicas_base.mp4"), "v2");
		Files.writeString(
				bloque.resolve("index.json"),
				"""
				{
				  "videos": [
				    { "file": "02_tecnicas_base.mp4", "title": "Tecnicas base", "order": 1 },
				    { "file": "01_presentacion.mp4", "title": "Presentacion", "order": 2 }
				  ]
				}
				""");

		MaterialExamenDTO material = service.obtenerMaterialExamen(11L, Deporte.TAEKWONDO);

		assertEquals(2, material.getVideos().size());
		assertEquals("02_tecnicas_base.mp4", material.getVideos().get(0).getId());
		assertEquals("Tecnicas base", material.getVideos().get(0).getTitle());
		assertEquals(1, material.getVideos().get(0).getOrder());
		assertEquals("01_presentacion.mp4", material.getVideos().get(1).getId());
		assertEquals("Presentacion", material.getVideos().get(1).getTitle());
		assertEquals(2, material.getVideos().get(1).getOrder());
	}

	@Test
	void shouldReturnEmptyMaterialWhenBlockExistsWithoutFiles() throws Exception {
		mockAlumnoConDeporte(12L, Deporte.TAEKWONDO, TipoGrado.BLANCO);
		crearBloque("taekwondo", "b01_inicio_a_amarillo");

		MaterialExamenDTO material = service.obtenerMaterialExamen(12L, Deporte.TAEKWONDO);

		assertEquals("b01_inicio_a_amarillo", material.getBloqueId());
		assertNull(material.getTemario());
		assertNotNull(material.getVideos());
		assertEquals(0, material.getVideos().size());
		assertNotNull(material.getDocumentos());
		assertEquals(0, material.getDocumentos().size());
	}

	@Test
	void shouldResolveAdditionalDocumentsWithOrderAndPreviewable() throws Exception {
		mockAlumnoConDeporte(13L, Deporte.TAEKWONDO, TipoGrado.AMARILLO);

		Path bloque = crearBloque("taekwondo", "b02_amarillo_a_naranja");
		Files.createDirectories(bloque.resolve("temario"));
		Files.writeString(bloque.resolve("temario").resolve("temario.pdf"), "temario");
		Files.createDirectories(bloque.resolve("documentacion"));
		Files.writeString(bloque.resolve("documentacion").resolve("02_reglamento.pdf"), "pdf");
		Files.writeString(bloque.resolve("documentacion").resolve("01_glosario.docx"), "docx");
		Files.writeString(bloque.resolve("documentacion").resolve(".gitkeep"), "");

		MaterialExamenDTO material = service.obtenerMaterialExamen(13L, Deporte.TAEKWONDO);

		assertNotNull(material.getDocumentos());
		assertEquals(3, material.getDocumentos().size());
		assertEquals("temario.pdf", material.getDocumentos().get(0).getFileName());
		assertEquals("01_glosario.docx", material.getDocumentos().get(1).getFileName());
		assertEquals(false, material.getDocumentos().get(1).isPreviewable());
		assertEquals("02_reglamento.pdf", material.getDocumentos().get(2).getFileName());
		assertEquals(true, material.getDocumentos().get(2).isPreviewable());
	}

	@Test
	void shouldRejectInvalidVideoFileName() {
		assertThrows(
				IllegalArgumentException.class,
				() -> service.obtenerVideo(99L, Deporte.TAEKWONDO, "../secret.mp4"));
	}

	@Test
	void shouldRejectInvalidDocumentFileName() {
		assertThrows(
				IllegalArgumentException.class,
				() -> service.obtenerDocumento(99L, Deporte.TAEKWONDO, "../secret.pdf"));
	}

	private void mockAlumnoConDeporte(Long alumnoId, Deporte deporte, TipoGrado grado) {
		AlumnoDeporte alumnoDeporte = new AlumnoDeporte();
		alumnoDeporte.setDeporte(deporte);

		Grado entidadGrado = new Grado();
		entidadGrado.setTipoGrado(grado);
		alumnoDeporte.setGrado(entidadGrado);

		when(alumnoDeporteService.obtenerDeportesActivosDelAlumno(alumnoId)).thenReturn(List.of(alumnoDeporte));
	}

	private Path crearBloque(String deporteDir, String bloqueId) throws Exception {
		Path bloque = tempDir.resolve("Materiales_Examen").resolve(deporteDir).resolve(bloqueId);
		Files.createDirectories(bloque);
		return bloque;
	}
}
