package com.taemoi.project.archivos;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AccesoArchivosTest {

	@Test
	void ultimoSegmentoDeRutaEstiloWindows() {
		assertEquals("foto.jpg", AccesoArchivos.ultimoSegmento("C:\\a\\b\\foto.jpg"));
	}

	@Test
	void ultimoSegmentoDeRutaEstiloUnixYMixta() {
		assertEquals("x.webp", AccesoArchivos.ultimoSegmento("/opt/taemoi/static_resources/imagenes/alumnos/x.webp"));
		assertEquals("x.webp", AccesoArchivos.ultimoSegmento("  C:\\a/b\\x.webp  "));
		assertEquals("x.webp", AccesoArchivos.ultimoSegmento("x.webp"));
		assertEquals("", AccesoArchivos.ultimoSegmento("/opt/a/"));
	}
}
