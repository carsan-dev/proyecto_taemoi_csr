package com.taemoi.project.services.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.taemoi.project.catalogo.CatalogoTaemoiEnMemoria;

/** Rallas hacia el examen en la celda «Grado» del listado de asistencia (decisiones 510, 511 y 513). */
class CinturonesPdfRallasTest {

	private final CatalogoTaemoiEnMemoria catalogo = new CatalogoTaemoiEnMemoria();

	private String celda(String codigo, Integer medias) {
		return CinturonesPdf.celdaAsistencia(catalogo.grado("TAEKWONDO", codigo), medias);
	}

	private static long cuenta(String html, String clase) {
		return html.split("class='" + clase + "'", -1).length - 1L;
	}

	@Test
	void sinRallasLaCeldaEsLaDeSiempre() {
		for (String codigo : new String[] { "BLANCO", "AMARILLO", "BLANCO_AMARILLO", "AZUL", "ROJO_NEGRO_2_PUM",
				"NEGRO_1_DAN" }) {
			String antes = CinturonesPdf.celdaAsistencia(catalogo.grado("TAEKWONDO", codigo));
			assertEquals(antes, celda(codigo, null), codigo);
			assertEquals(antes, celda(codigo, 0), codigo);
		}
		assertEquals(CinturonesPdf.celdaAsistencia(null), CinturonesPdf.celdaAsistencia(null, 5));
	}

	@Test
	void medias_1_5_9_10() {
		// medias -> rayas negras: 1 media; 5 = 2 rallas y media; 9 = 4 y media; 10 = 5 rallas.
		assertEquals(1, cuenta(celda("AMARILLO", 1), "ralla"));
		assertEquals(3, cuenta(celda("AMARILLO", 5), "ralla"));
		assertEquals(5, cuenta(celda("AMARILLO", 9), "ralla"));
		assertEquals(5, cuenta(celda("AMARILLO", 10), "ralla"));
		assertEquals(5, cuenta(celda("AMARILLO", 14), "ralla"), "nunca pasa de 5 rallas");
	}

	@Test
	void medidasDelBoceto() {
		String uno = celda("AMARILLO", 2);
		assertTrue(uno.contains("right: 0.80mm; width: 0.90mm; top: 0; height: 100%"), uno);
		String dos = celda("AMARILLO", 4);
		assertTrue(dos.contains("right: 2.40mm; width: 0.90mm"), dos);
		// La media ralla es la mitad de abajo.
		String media = celda("AMARILLO", 1);
		assertTrue(media.contains("right: 0.80mm; width: 0.90mm; top: 50%; height: 50%"), media);
	}

	@Test
	void intermedioConDosFranjasSuperiorArribaInferiorAbajo() {
		String html = celda("BLANCO_AMARILLO", 2);
		assertTrue(html.indexOf("cinturon-half-superior' style='background-color: #FFFF00") > 0, html);
		assertTrue(html.indexOf("cinturon-half-inferior' style='background-color: #FFFFFF") > 0, html);
	}

	@Test
	void haloSoloDondeElNegroNoLlegaATresAUno() {
		assertTrue(CinturonesPdf.negroNoLlegaAContraste("#0000FF"));
		assertFalse(CinturonesPdf.negroNoLlegaAContraste("#FFFF00"));
		assertFalse(CinturonesPdf.negroNoLlegaAContraste("#FFFFFF"));
		assertFalse(CinturonesPdf.negroNoLlegaAContraste("#FF0000"));
		assertEquals(0, cuenta(celda("AMARILLO", 10), "ralla-halo"));
		// Azul: halo en la ralla entera (arriba y abajo) y en la media (solo abajo).
		assertEquals(2, cuenta(celda("AZUL", 2), "ralla-halo"));
		assertEquals(1, cuenta(celda("AZUL", 1), "ralla-halo"));
		assertTrue(celda("AZUL", 2).contains("right: 0.50mm; width: 1.50mm"));
	}
}
