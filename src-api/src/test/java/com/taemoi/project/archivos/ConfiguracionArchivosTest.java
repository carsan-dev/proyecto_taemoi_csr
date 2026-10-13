package com.taemoi.project.archivos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.InputStream;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.entities.Documento;

class ConfiguracionArchivosTest {

	@Test
	void lasRaicesDeImagenesPorDefectoNoApuntanALaCarpetaDeAlumnos() throws Exception {
		Properties propiedades = new Properties();
		try (InputStream entrada = getClass().getClassLoader().getResourceAsStream("application.properties")) {
			assertNotNull(entrada);
			propiedades.load(entrada);
		}
		for (String clave : new String[] { "app.imagenes.directorio.linux", "app.imagenes.directorio.windows" }) {
			String valor = propiedades.getProperty(clave);
			assertNotNull(valor, clave);
			// Con la raíz en .../alumnos/, cualquier fichero de la raíz caería fuera de la carpeta privada.
			assertFalse(valor.replace('\\', '/').toLowerCase().contains("/alumnos"), clave + "=" + valor);
		}
	}

	@Test
	void urlSinHostSeCompletaConLaBase() {
		assertEquals("http://h:8080/imagenes/org-2/alumnos/a.webp",
				UrlArchivoSerializer.absoluta("/imagenes/org-2/alumnos/a.webp", "http://h:8080/"));
		assertEquals("https://x/api/alumnos/1/imagen", UrlArchivoSerializer.absoluta("https://x/api/alumnos/1/imagen", "http://h"));
		assertEquals("//cdn/a.webp", UrlArchivoSerializer.absoluta("//cdn/a.webp", "http://h"));
		assertEquals("/documentos/a.pdf", UrlArchivoSerializer.absoluta("/documentos/a.pdf", ""));
		assertNull(UrlArchivoSerializer.absoluta(null, "http://h"));
	}

	@Test
	void sinSpringLaUrlSaleSinCambios() throws Exception {
		Documento documento = new Documento();
		documento.setUrl("/documentos/org-2/alumnos/5/a.pdf");
		assertEquals("/documentos/org-2/alumnos/5/a.pdf",
				new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(documento)).get("url").asText());
	}
}
