package com.taemoi.project.services.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.taemoi.project.catalogo.CatalogoTaemoiEnMemoria;
import com.taemoi.project.entities.Grado;
import com.taemoi.project.legado.CinturonesPdfLegado;
import com.taemoi.project.legado.TipoGrado;
import com.taemoi.project.utils.ColoresGrado;
import com.taemoi.project.utils.ColoresGrado.Cinturon;

/**
 * Cinturones de los PDF desde los colores del catálogo (V30). Oráculo: {@link CinturonesPdfLegado}, el dibujo
 * anterior derivado del código.
 */
class CinturonesPdfTest {

	private static final int[][] TAMANOS = { { 70, 16 }, { 60, 12 }, { 55, 12 }, { 50, 15 } };

	private final CatalogoTaemoiEnMemoria catalogo = new CatalogoTaemoiEnMemoria();

	/** Taekwondo con los colores de V30: el mismo HTML que antes en los cinturones en línea y de encabezado. */
	@Test
	void taekwondoConColoresDeV30_mismoDibujoQueAntes() {
		for (TipoGrado tipo : TipoGrado.values()) {
			Grado grado = catalogo.grado("TAEKWONDO", tipo.name());
			Cinturon c = ColoresGrado.de(grado);
			for (int[] t : TAMANOS) {
				assertEquals(CinturonesPdfLegado.inline(tipo.name(), t[0], t[1]), CinturonesPdf.inline(c, t[0], t[1]),
						tipo + " " + t[0] + "x" + t[1]);
			}
			assertEquals(CinturonesPdfLegado.encabezado(tipo.name()), CinturonesPdf.encabezado(c), tipo.name());
		}
	}

	/** Kickboxing: igual salvo el ROJO, que pasa a marrón #8B4513 de un solo color (D4). */
	@Test
	void kickboxingConColoresDeV30_igualSalvoRojoMarron() {
		for (TipoGrado tipo : TipoGrado.values()) {
			Cinturon c = ColoresGrado.de(catalogo.grado("KICKBOXING", tipo.name()));
			if (tipo == TipoGrado.ROJO) {
				assertEquals(new Cinturon("#8B4513", "#8B4513", false, 0), c);
				assertEquals(CinturonesPdfLegado.inline("ROJO", 70, 16).replace("#FF0000", "#8B4513"),
						CinturonesPdf.inline(c, 70, 16));
				assertEquals(CinturonesPdfLegado.encabezado("ROJO").replace("#FF0000", "#8B4513"),
						CinturonesPdf.encabezado(c));
				continue;
			}
			assertEquals(CinturonesPdfLegado.inline(tipo.name(), 60, 12), CinturonesPdf.inline(c, 60, 12), tipo.name());
			assertEquals(CinturonesPdfLegado.encabezado(tipo.name()), CinturonesPdf.encabezado(c), tipo.name());
		}
	}

	/** Grado sin colores en el catálogo (otras escuelas): exactamente el dibujo anterior, también en asistencia. */
	@Test
	void gradoSinColores_mismoDibujoQueAntes() {
		List<String> codigos = new java.util.ArrayList<>();
		for (TipoGrado tipo : TipoGrado.values()) {
			codigos.add(tipo.name());
		}
		codigos.addAll(List.of("NEGRO", "CINTO", "CINTO_VERDE", "GUP_10", "MARRON", "VERDE_NEGRO", "NEGRO_X_DAN",
				"KYU_1_PUM", "DAN"));
		for (String codigo : codigos) {
			Grado grado = sinColores(codigo);
			Cinturon c = ColoresGrado.de(grado);
			for (int[] t : TAMANOS) {
				assertEquals(CinturonesPdfLegado.inline(codigo, t[0], t[1]), CinturonesPdf.inline(c, t[0], t[1]),
						codigo + " " + t[0] + "x" + t[1]);
			}
			assertEquals(CinturonesPdfLegado.encabezado(codigo), CinturonesPdf.encabezado(c), codigo);
			assertEquals(CinturonesPdfLegado.celdaAsistencia(codigo), CinturonesPdf.celdaAsistencia(grado), codigo);
		}
		assertEquals(CinturonesPdfLegado.celdaAsistencia(null), CinturonesPdf.celdaAsistencia(null));
		assertEquals("", CinturonesPdf.inline(null, 70, 16));
		assertNull(ColoresGrado.de((Grado) null));
	}

	/** Listado de asistencia de Taemoi: pasa de su paleta propia a los colores del catálogo. */
	@Test
	void asistenciaConColoresDeV30_usaLosColoresDelCatalogo() {
		assertEquals("<td style='background: #FFFFFF;'></td>", celda("TAEKWONDO", "BLANCO"));
		assertEquals("<td style='background: #FFFF00;'></td>", celda("TAEKWONDO", "AMARILLO"));
		assertEquals("<td style='background: #FF0000;'></td>", celda("TAEKWONDO", "ROJO"));
		assertEquals("<td style='background: #8B4513;'></td>", celda("KICKBOXING", "ROJO"));
		assertEquals("<td><div class='cinturon-split'><div class='cinturon-half-superior' style='background-color: #FFFF00;'>"
				+ "</div><div class='cinturon-half-inferior' style='background-color: #FFFFFF;'></div></div></td>",
				celda("TAEKWONDO", "BLANCO_AMARILLO"));
		assertEquals("<td class='cinturon-negro' style='background: #000000;'>3º</td>", celda("TAEKWONDO", "NEGRO_3_DAN"));
		String pum = celda("TAEKWONDO", "ROJO_NEGRO_2_PUM");
		assertTrue(pum.contains("cinturon-half-superior' style='background-color: #000000;"), pum);
		assertTrue(pum.contains("cinturon-half-inferior' style='background-color: #FF0000;"), pum);
		assertTrue(pum.contains(">2º</span>"), pum);
		// La forma (división, número de PUM) es la de antes; solo cambian los colores.
		assertEquals(CinturonesPdfLegado.celdaAsistencia("ROJO_NEGRO_2_PUM").replace("#212529", "#000000")
				.replace("#f44336", "#FF0000"), pum);
	}

	/** Colores propios de otra escuela: color_1 abajo, color_2 arriba; franjas por el patrón del código. */
	@Test
	void coloresPropios_color1AbajoColor2Arriba() {
		Grado g = sinColores("CINTO_A");
		g.setColor1("#111111");
		g.setColor2("#222222");
		Cinturon c = ColoresGrado.de(g);
		assertEquals(new Cinturon("#111111", "#222222", true, 0), c);
		String html = CinturonesPdf.inline(c, 50, 15);
		assertTrue(html.indexOf("#222222; position: absolute; top: 0") > 0, html);
		assertTrue(html.indexOf("#111111; position: absolute; bottom: 0") > 0, html);

		Grado unColor = sinColores("AZUL"); // código de color, pero manda el catálogo
		unColor.setColor1("#ABCDEF");
		assertEquals(new Cinturon("#ABCDEF", "#ABCDEF", false, 0), ColoresGrado.de(unColor));
		assertFalse(CinturonesPdf.inline(ColoresGrado.de(unColor), 50, 15).contains("#0000FF"));

		Grado dan = sinColores("GRADO_4_DAN");
		dan.setColor1("#000000");
		assertEquals(4, ColoresGrado.de(dan).franjas());
	}

	@Test
	void franjasPorCodigo() {
		assertEquals(1, ColoresGrado.franjas("ROJO_NEGRO_1_PUM"));
		assertEquals(5, ColoresGrado.franjas("NEGRO_5_DAN"));
		assertEquals(0, ColoresGrado.franjas("ROJO"));
		assertEquals(0, ColoresGrado.franjas("DAN"));
		assertEquals(0, ColoresGrado.franjas(null));
	}

	private String celda(String disciplina, String codigo) {
		return CinturonesPdf.celdaAsistencia(catalogo.grado(disciplina, codigo));
	}

	private static Grado sinColores(String codigo) {
		Grado grado = new Grado();
		grado.setCodigo(codigo);
		return grado;
	}
}
