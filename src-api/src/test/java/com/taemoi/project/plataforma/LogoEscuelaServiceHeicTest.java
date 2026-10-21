package com.taemoi.project.plataforma;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.taemoi.project.organizacion.TransaccionEnEscuela;
import com.taemoi.project.repositories.ImagenRepository;
import com.taemoi.project.repositories.OrganizacionRepository;
import com.taemoi.project.services.AuditoriaService;
import com.taemoi.project.services.ImagenService;
import com.taemoi.project.services.MarcaEscuelaService;
import com.taemoi.project.utils.DocumentoSecurityUtils;

/** Logo de escuela en HEIC/HEIF (decisión 227): se rechaza con el mensaje de convertir a JPG, sin procesarlo. */
@ExtendWith(MockitoExtension.class)
class LogoEscuelaServiceHeicTest {

	@Mock
	private OrganizacionRepository organizaciones;
	@Mock
	private ImagenRepository imagenes;
	@Mock
	private ImagenService imagenService;
	@Mock
	private LogoEscuelaProcesador procesador;
	@Mock
	private MarcaEscuelaService marcaEscuela;
	@Mock
	private AuditoriaService auditoria;
	@Mock
	private TransaccionEnEscuela enEscuela;

	@InjectMocks
	private LogoEscuelaService servicio;

	@ParameterizedTest(name = "{0} + {1}")
	@CsvSource(delimiter = '|', value = {
			"logo.heic | image/heic",
			"LOGO.HEIF | application/octet-stream",
			"logo.jpg  | image/heif",
			"logo.png  | image/heic-sequence" })
	void heicSeRechazaSinProcesarNiGuardar(String nombre, String contentType) {
		MockMultipartFile archivo = new MockMultipartFile("archivo", nombre, contentType, new byte[] { 1, 2, 3 });

		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> servicio.subir(7L, archivo));

		assertEquals(DocumentoSecurityUtils.MENSAJE_HEIC, error.getMessage());
		verifyNoInteractions(procesador, imagenService, organizaciones, imagenes, auditoria, enEscuela);
	}
}
