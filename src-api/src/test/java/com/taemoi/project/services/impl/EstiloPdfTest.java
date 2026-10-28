package com.taemoi.project.services.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.taemoi.project.services.MarcaEscuela;

/** Decisiones 309, 314 y 315: piezas comunes de los PDF (fuentes embebidas, pie, cabecera y color de la escuela). */
class EstiloPdfTest {

	@Test
	void lasFuentesDelPanelVanEmbebidas() throws Exception {
		String html = EstiloPdf.documento("Escuela Ficticia", "Informe de prueba", null,
				EstiloPdf.cabecera("#c0392b", "", "Escuela Ficticia", null, "Informe de prueba", "Contexto", "Fecha")
						+ "<p>Texto normal. <strong>Negrita</strong> <span style='font-weight: 600'>Semi</span></p>");
		byte[] pdf = renderizar(html);
		List<String> fuentes = new ArrayList<>();
		try (PDDocument documento = PDDocument.load(pdf)) {
			PDResources recursos = documento.getPage(0).getResources();
			for (COSName nombre : recursos.getFontNames()) {
				PDFont fuente = recursos.getFont(nombre);
				assertTrue(fuente.isEmbedded(), fuente.getName() + " no está embebida");
				fuentes.add(fuente.getName().substring(fuente.getName().indexOf('+') + 1));
			}
		}
		assertTrue(fuentes.containsAll(
				List.of("Montserrat-Bold", "PublicSans-Regular", "PublicSans-SemiBold", "PublicSans-Bold")),
				fuentes.toString());
	}

	@Test
	void pieConEscuelaInformeYPaginas() throws Exception {
		String cuerpo = "<p>Una</p><p style='page-break-before: always'>Dos</p>";
		byte[] pdf = renderizar(EstiloPdf.documento("Club O'Neill & Sur", "Informe «uno»", null, cuerpo));
		try (PDDocument documento = PDDocument.load(pdf)) {
			assertEquals(2, documento.getNumberOfPages());
			String texto = new PDFTextStripper().getText(documento);
			assertTrue(texto.contains("Club O'Neill & Sur · Informe «uno»"), texto);
			assertTrue(texto.contains("Página 1 de 2") && texto.contains("Página 2 de 2"), texto);
		}
	}

	@Test
	void cadenaCssEscapaLoQueRompeElXhtmlYElCss() {
		String css = EstiloPdf.cadenaCss("A & B's \"C\" <d> \\ é");
		assertTrue(css.startsWith("\"") && css.endsWith("\""));
		String dentro = css.substring(1, css.length() - 1);
		for (String peligroso : List.of("&", "'", "\"", "<", ">")) {
			assertFalse(dentro.contains(peligroso), peligroso + " en " + css);
		}
		assertEquals("\"A \\26  B\\27 s \\22 C\\22  \\3c d\\3e  \\5c  \\e9 \"", css);
	}

	@Test
	void colorDeLaEscuelaValidadoYNormalizado() {
		assertEquals("#c0392b", marca("#C0392B").colorPrimario());
		assertEquals("#c0392b", marca(" #c0392b ").colorPrimario());
		for (String malo : new String[] { null, "", "rojo", "#c0392", "#c0392bb", "c0392b", "#gggggg" }) {
			assertEquals(MarcaEscuela.COLOR_POR_DEFECTO, marca(malo).colorPrimario(), String.valueOf(malo));
		}
		// Constructor anterior (sin color): el de por defecto.
		assertEquals(MarcaEscuela.COLOR_POR_DEFECTO,
				new MarcaEscuela(1L, false, "Escuela", "", "", null, null, null).colorPrimario());
	}

	@Test
	void laCabeceraPintaElFileteConElColorDeLaEscuela() {
		String html = EstiloPdf.cabecera("#c0392b", "", "Escuela", null, "Título", null, null);
		assertTrue(html.contains("border-bottom-color: #c0392b;"), html);
	}

	private static MarcaEscuela marca(String color) {
		return new MarcaEscuela(2L, false, "Escuela", "", "", null, null, null, color);
	}

	private static byte[] renderizar(String html) throws Exception {
		ByteArrayOutputStream salida = new ByteArrayOutputStream();
		PdfRendererBuilder builder = EstiloPdf.builder();
		builder.withHtmlContent(html, null);
		builder.toStream(salida);
		builder.run();
		return salida.toByteArray();
	}
}
