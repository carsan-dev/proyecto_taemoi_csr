package com.taemoi.project.utils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.taemoi.project.exceptions.ArchivoNoValidoException;

/**
 * Lista blanca de documentos ({@link DocumentoSecurityUtils#detectarMimePermitido}) y rechazo de HEIC/HEIF
 * (decisión 227): las fotos HEIC no se convierten; se pide JPG.
 */
class DocumentoSecurityUtilsTest {

	private static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

	@ParameterizedTest(name = "{0} + {1} -> {2}")
	@CsvSource(nullValues = "NULL", delimiter = '|', value = {
			"documento.pdf  | application/pdf                | application/pdf",
			"foto.jpg       | application/octet-stream       | image/jpeg",
			"FOTO.JPG       | image/jpeg                     | image/jpeg",
			"foto.jpeg      | image/pjpeg                    | image/jpeg",
			"informe.docx   | application/zip                | " + DOCX,
			"lista.csv      | application/vnd.ms-excel       | text/csv",
			"documento.pdf  | application/pdf; charset=UTF-8 | application/pdf",
			"foto.jpg       | NULL                           | image/jpeg",
			"foto.png       | IMAGE/PNG                      | image/png" })
	void tiposPermitidosDevuelvenElMimeCanonico(String nombre, String contentType, String esperado) {
		assertEquals(esperado, DocumentoSecurityUtils.detectarMimePermitido(nombre, contentType));
	}

	@ParameterizedTest(name = "[{0}] + {1} -> {2}")
	@CsvSource(nullValues = "NULL", delimiter = '|', value = {
			"documento         | application/pdf          | EXTENSION",
			"''                | application/pdf          | NOMBRE",
			"NULL              | application/pdf          | NOMBRE",
			"foto.             | image/jpeg               | EXTENSION",
			"programa.exe      | application/octet-stream | TIPO",
			"foto.jpg          | text/html                | TIPO",
			"foto.jpg          | text/html; charset=UTF-8 | TIPO",
			"foto.heic         | image/heic               | HEIC",
			"foto.heic         | application/octet-stream | HEIC",
			"foto.heic         | NULL                     | HEIC",
			"FOTO.HEIF         | image/heif               | HEIC",
			"foto.jpg          | image/heic               | HEIC",
			"foto.jpg          | image/heif               | HEIC",
			"foto.jpg          | image/heic-sequence      | HEIC",
			"foto.jpg          | image/heif-sequence      | HEIC",
			"foto.jpg          | image/HEIC; charset=x    | HEIC",
			"''                | image/heic               | HEIC" })
	void tiposNoPermitidosLanzanConSuMotivo(String nombre, String contentType, String motivo) {
		ArchivoNoValidoException error = assertThrows(ArchivoNoValidoException.class,
				() -> DocumentoSecurityUtils.detectarMimePermitido(nombre, contentType));
		assertEquals(mensaje(motivo), error.getMessage());
	}

	@Test
	void elMensajeHeicEsElAcordado() {
		assertEquals("Convierte la foto a JPG o desactiva HEIF en la cámara.", DocumentoSecurityUtils.MENSAJE_HEIC);
	}

	@ParameterizedTest(name = "{0} + {1} -> HEIC")
	@CsvSource(nullValues = "NULL", delimiter = '|', value = {
			"foto.heic | image/jpeg",
			"foto.HEIF | NULL",
			"foto.jpg  | image/heic",
			"NULL      | image/heif-sequence" })
	void rechazarHeicLanzaParaHeicPorNombreOTipo(String nombre, String contentType) {
		ArchivoNoValidoException error = assertThrows(ArchivoNoValidoException.class,
				() -> DocumentoSecurityUtils.rechazarHeic(nombre, contentType));
		assertEquals(DocumentoSecurityUtils.MENSAJE_HEIC, error.getMessage());
	}

	@ParameterizedTest(name = "{0} + {1} -> sin error")
	@CsvSource(nullValues = "NULL", delimiter = '|', value = {
			"foto.jpg  | image/jpeg",
			"foto.png  | NULL",
			"NULL      | NULL",
			"foto      | application/octet-stream",
			"heic.jpg  | image/jpeg" })
	void rechazarHeicNoAfectaAlResto(String nombre, String contentType) {
		assertDoesNotThrow(() -> DocumentoSecurityUtils.rechazarHeic(nombre, contentType));
	}

	@ParameterizedTest(name = "[{0}] -> {1}")
	@CsvSource(nullValues = "NULL", delimiter = '|', value = {
			"Informe médico de Ana.PDF | pdf",
			"sin_extension             | -",
			"NULL                      | -",
			"foto.                     | -",
			"'raro.ex\ne'              | ?",
			"largo.abcdefghijklmnop    | ?" })
	void extensionParaLogNoExponeElNombre(String nombre, String esperado) {
		assertEquals(esperado, DocumentoSecurityUtils.extensionParaLog(nombre));
	}

	@ParameterizedTest(name = "[{0}] -> {1}")
	@CsvSource(nullValues = "NULL", delimiter = '|', value = {
			"image/heic                | image/heic",
			"Application/PDF; charset=x | application/pdf",
			"NULL                      | -",
			"'text/html\r\nX: y'       | ?" })
	void contentTypeParaLogSaneaElValor(String contentType, String esperado) {
		assertEquals(esperado, DocumentoSecurityUtils.contentTypeParaLog(contentType));
	}

	private static String mensaje(String motivo) {
		return switch (motivo) {
			case "NOMBRE" -> "El nombre del archivo es obligatorio.";
			case "EXTENSION" -> "El documento debe incluir una extension permitida.";
			case "TIPO" -> "Tipo de documento no permitido.";
			case "HEIC" -> "Convierte la foto a JPG o desactiva HEIF en la cámara.";
			default -> throw new IllegalArgumentException(motivo);
		};
	}
}
