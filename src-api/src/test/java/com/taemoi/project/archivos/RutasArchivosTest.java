package com.taemoi.project.archivos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.taemoi.project.organizacion.TenantContext;

class RutasArchivosTest {

	@TempDir
	Path tempDir;

	@AfterEach
	void limpiar() {
		TenantContext.clear();
	}

	@Test
	void carpetaDeOrganizacion() {
		assertEquals("org-2", RutasArchivos.carpetaOrganizacion(2L));
		assertThrows(IllegalArgumentException.class, () -> RutasArchivos.carpetaOrganizacion(null));
		assertThrows(IllegalArgumentException.class, () -> RutasArchivos.carpetaOrganizacion(0L));
	}

	@Test
	void organizacionDeLaRuta() {
		assertEquals(2L, RutasArchivos.organizacionDeRuta("org-2/alumnos/5/a.pdf"));
		assertEquals(17L, RutasArchivos.organizacionDeRuta("ORG-17/eventos/x.webp"));
		assertNull(RutasArchivos.organizacionDeRuta("Documentos_Alumnos_Moiskimdo/1_A_B/a.pdf"));
		assertNull(RutasArchivos.organizacionDeRuta("org-/a.pdf"));
		assertNull(RutasArchivos.organizacionDeRuta("org-2x/a.pdf"));
		assertNull(RutasArchivos.organizacionDeRuta(null));
	}

	@Test
	void normalizaRutasValidasDecodificandoSegmentos() {
		assertEquals(Optional.of("org-2/alumnos/5/informe medico.pdf"),
				RutasArchivos.normalizarRutaSolicitada("org-2/alumnos/5/informe%20medico.pdf"));
		assertEquals(Optional.of("eventos/cartel.webp"), RutasArchivos.normalizarRutaSolicitada("eventos/cartel.webp"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "/", "../secreto.pdf", "a/../../b.pdf", "a/./b.pdf", "a//b.pdf", "/etc/passwd",
			"%2e%2e/secreto.pdf", "%2E%2E%2Fsecreto.pdf", "a%2fb.pdf", "a%5cb.pdf", "..%5c..%5cwin.ini",
			"a\\b.pdf", "C:/Windows/win.ini", "c%3a/x.pdf", "a%00.pdf", "a/b.pdf::$DATA", "alumnos./x.webp",
			"alumnos%20/x.webp", "a;jsessionid=1/b.pdf", "%zz.pdf", "a/%0a/b.pdf" })
	void rechazaRecorridosYRutasAbsolutas(String ruta) {
		assertTrue(RutasArchivos.normalizarRutaSolicitada(ruta).isEmpty(), ruta);
	}

	@Test
	void rechazaNulo() {
		assertTrue(RutasArchivos.normalizarRutaSolicitada(null).isEmpty());
	}

	@Test
	void detectaRutasAbsolutas() {
		assertTrue(RutasArchivos.esRutaAbsoluta("/var/www/app/imagenes/alumnos/a.webp"));
		assertTrue(RutasArchivos.esRutaAbsoluta("C:\\Users\\x\\a.webp"));
		assertTrue(RutasArchivos.esRutaAbsoluta("c:/x/a.webp"));
		assertTrue(RutasArchivos.esRutaAbsoluta("\\\\servidor\\a.webp"));
		assertFalse(RutasArchivos.esRutaAbsoluta("org-2/alumnos/a.webp"));
		assertFalse(RutasArchivos.esRutaAbsoluta("Documentos_Eventos/1_x/a.pdf"));
	}

	@Test
	void resolverDentroDeLaBaseNoPermiteSalir() {
		Path base = tempDir.toAbsolutePath().normalize();
		assertEquals(base.resolve("org-2").resolve("a.pdf"), RutasArchivos.resolverDentroDe(base, "org-2/a.pdf"));
		assertThrows(IllegalArgumentException.class, () -> RutasArchivos.resolverDentroDe(base, "../fuera.pdf"));
		assertThrows(IllegalArgumentException.class,
				() -> RutasArchivos.resolverDentroDe(base, base.getRoot().resolve("fuera.pdf").toString()));
	}

	@Test
	void organizacionActivaFallaCerrado() {
		assertThrows(IllegalStateException.class, RutasArchivos::organizacionActiva);
		TenantContext.set(3L);
		assertEquals(3L, RutasArchivos.organizacionActiva());
	}

	@Test
	void clasificaImagenes() {
		assertTrue(RutasArchivos.esImagenPublica("eventos/cartel.webp"));
		assertTrue(RutasArchivos.esImagenPublica("org-2/eventos/cartel.webp"));
		assertTrue(RutasArchivos.esImagenPublica("EVENTOS/cartel.webp"));
		// Lista blanca: fuera de eventos, todo es privado.
		assertFalse(RutasArchivos.esImagenPublica("logo.png"));
		assertFalse(RutasArchivos.esImagenPublica("eventos"));
		assertFalse(RutasArchivos.esImagenPublica("otra/carpeta.png"));
		assertFalse(RutasArchivos.esImagenPublica("org-2/logo.png"));
		assertFalse(RutasArchivos.esImagenPublica("org-2/otra/logo.png"));
		assertFalse(RutasArchivos.esImagenPublica("Documentos_Eventos/1/x.png"));
		assertFalse(RutasArchivos.esImagenPublica("x.webp/eventos/a.webp"));
		assertFalse(RutasArchivos.esImagenPublica("alumnos/foto.webp"));
		assertFalse(RutasArchivos.esImagenPublica("ALUMNOS/foto.webp"));
		assertFalse(RutasArchivos.esImagenPublica("org-2/alumnos/foto.webp"));
		assertFalse(RutasArchivos.esImagenPublica("org-2/Alumnos/foto.webp"));
		assertFalse(RutasArchivos.esImagenPublica("eventos/alumnos/foto.webp"));
	}

	@Test
	void clasificaDocumentos() {
		assertTrue(RutasArchivos.esDocumentoPublico("Documentos_Eventos/3_Torneo/bases.pdf"));
		assertTrue(RutasArchivos.esDocumentoPublico("org-2/eventos/3/bases.pdf"));
		assertFalse(RutasArchivos.esDocumentoPublico("eventos/3/bases.pdf"));
		assertFalse(RutasArchivos.esDocumentoPublico("org-2/Documentos_Eventos/3/bases.pdf"));
		assertFalse(RutasArchivos.esDocumentoPublico("Documentos_Eventos/bases.pdf.lnk/alumnos/x.pdf"));
		assertFalse(RutasArchivos.esDocumentoPublico("Documentos_Alumnos_Moiskimdo/1_A_B/dni.pdf"));
		assertFalse(RutasArchivos.esDocumentoPublico("org-2/alumnos/5/dni.pdf"));
		assertFalse(RutasArchivos.esDocumentoPublico("Materiales_Examen/taekwondo/b01/temario/t.pdf"));
		assertFalse(RutasArchivos.esDocumentoPublico("Documentos_Eventos"));
	}
}
