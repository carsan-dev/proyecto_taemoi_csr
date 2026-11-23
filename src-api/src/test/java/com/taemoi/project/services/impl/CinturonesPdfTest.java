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
				assertEquals(dorado(CinturonesPdfLegado.inline(tipo.name(), t[0], t[1])), CinturonesPdf.inline(c, t[0], t[1]),
						tipo + " " + t[0] + "x" + t[1]);
			}
			assertEquals(CinturonesPdfLegado.encabezado(tipo.name()), CinturonesPdf.encabezado(c), tipo.name());
		}
	}

	/**
	 * Kickboxing: igual salvo el rojo, que es marrón #8B4513: el ROJO, de un solo color (D4, V30), y la franja roja de
	 * AZUL_ROJO y de los ROJO_NEGRO_n_PUM (V31_19).
	 */
	@Test
	void kickboxingConColoresDeV30_igualSalvoRojoMarron() {
		for (TipoGrado tipo : TipoGrado.values()) {
			Cinturon c = ColoresGrado.de(catalogo.grado("KICKBOXING", tipo.name()));
			if (tipo == TipoGrado.ROJO) {
				assertEquals(new Cinturon("#8B4513", "#8B4513", false, 0), c);
				assertEquals(dorado(CinturonesPdfLegado.inline("ROJO", 70, 16)).replace("#FF0000", "#8B4513"),
						CinturonesPdf.inline(c, 70, 16));
				assertEquals(CinturonesPdfLegado.encabezado("ROJO").replace("#FF0000", "#8B4513"),
						CinturonesPdf.encabezado(c));
				continue;
			}
			assertEquals(dorado(CinturonesPdfLegado.inline(tipo.name(), 60, 12)).replace("#FF0000", "#8B4513"),
					CinturonesPdf.inline(c, 60, 12), tipo.name());
			assertEquals(CinturonesPdfLegado.encabezado(tipo.name()).replace("#FF0000", "#8B4513"), CinturonesPdf.encabezado(c),
					tipo.name());
			assertFalse(CinturonesPdf.encabezado(c).contains("#FF0000"), tipo.name());
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
				assertEquals(dorado(CinturonesPdfLegado.inline(codigo, t[0], t[1])), CinturonesPdf.inline(c, t[0], t[1]),
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
		String dan = celda("TAEKWONDO", "NEGRO_3_DAN");
		assertTrue(dan.startsWith("<td class='cinturon-negro' style='background: #000000;'>"), dan);
		assertTrue(dan.endsWith("3º</div></td>"), dan);
		assertEquals(3, cuenta(dan, "background-color: #D9A12A;"), dan);
		String pum = celda("TAEKWONDO", "ROJO_NEGRO_2_PUM");
		assertTrue(pum.contains("cinturon-half-superior' style='background-color: #000000;"), pum);
		assertTrue(pum.contains("cinturon-half-inferior' style='background-color: #FF0000;"), pum);
		assertTrue(pum.contains(">2º</span>"), pum);
		assertEquals(2, cuenta(pum, "background-color: #D9A12A;"), pum);
		// La forma (división, número de PUM) es la de antes; solo cambian los colores.
		assertEquals(CinturonesPdfLegado.celdaAsistencia("ROJO_NEGRO_2_PUM").replace("#212529", "#000000")
				.replace("#f44336", "#FF0000").replace("<div style='position: absolute; top: 0; left: 0; width: 100%; height: 100%; display: flex;",
						rallasEsperadas(2) + "<div style='position: absolute; top: 0; left: 0; width: 100%; height: 100%; display: flex;"),
				pum);
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

	/** Rayas de PUM/DAN: doradas #D9A12A, con la forma de las rallas de examen (0,9 mm, paso 1,6 mm, margen 0,8 mm). */
	@Test
	void rayasDoradas_2PumY3Dan_numeroColorYForma() {
		String pum = celda("TAEKWONDO", "ROJO_NEGRO_2_PUM");
		assertEquals(2, cuenta(pum, "class='raya-dorada'"), pum);
		assertEquals(2, cuenta(pum, "background-color: #D9A12A;"), pum);
		assertTrue(pum.contains(rallasEsperadas(2)), pum);
		String dan = celda("TAEKWONDO", "NEGRO_3_DAN");
		assertEquals(3, cuenta(dan, "class='raya-dorada'"), dan);
		assertTrue(dan.contains(rallasEsperadas(3)), dan);
		assertFalse(dan.contains("#FFD700"), dan);
		// En línea y en el encabezado (clase raya, color en el CSS de la plantilla).
		Cinturon c2 = ColoresGrado.de(catalogo.grado("TAEKWONDO", "ROJO_NEGRO_2_PUM"));
		Cinturon c3 = ColoresGrado.de(catalogo.grado("TAEKWONDO", "NEGRO_3_DAN"));
		assertEquals(2, cuenta(CinturonesPdf.inline(c2, 70, 16), "background-color: #D9A12A;"));
		assertEquals(3, cuenta(CinturonesPdf.inline(c3, 70, 16), "background-color: #D9A12A;"));
		assertFalse(CinturonesPdf.inline(c3, 70, 16).contains("#FFD700"));
		assertEquals(2, cuenta(CinturonesPdf.encabezado(c2), "class='raya'"));
		assertEquals(3, cuenta(CinturonesPdf.encabezado(c3), "class='raya'"));
	}

	private static String rallasEsperadas(int n) {
		StringBuilder s = new StringBuilder();
		for (int k = 0; k < n; k++) {
			s.append("<div class='raya-dorada' style='position: absolute; background-color: #D9A12A; right: ")
					.append(String.format(java.util.Locale.ROOT, "%.2fmm", 0.8 + 1.6 * k))
					.append("; width: 0.90mm; top: 0; height: 100%;'></div>");
		}
		return s.toString();
	}

	private static String dorado(String legado) {
		return legado.replace("height: 80%; background-color: #FFD700;", "height: 80%; background-color: #D9A12A;");
	}

	private static int cuenta(String texto, String trozo) {
		return texto.split(java.util.regex.Pattern.quote(trozo), -1).length - 1;
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
