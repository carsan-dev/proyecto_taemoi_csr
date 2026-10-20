package com.taemoi.project.plataforma;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sun.net.httpserver.HttpServer;

/**
 * Pieza E7-7a (decisión 212): validación y conversión del logo. Un servidor HTTP local cuenta cualquier petición: un
 * SVG malicioso nunca debe llegar a él (ni por la revisión ni por Batik).
 */
class LogoEscuelaProcesadorTest {

	private final LogoEscuelaProcesador procesador = new LogoEscuelaProcesador();
	private HttpServer servidor;
	private final AtomicInteger peticiones = new AtomicInteger();
	private String url;

	@TempDir
	Path carpeta;

	@BeforeEach
	void arrancarServidor() throws IOException {
		servidor = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		servidor.createContext("/", intercambio -> {
			peticiones.incrementAndGet();
			byte[] cuerpo = png(rellena(20, 20, Color.RED));
			intercambio.getResponseHeaders().add("Content-Type", "image/png");
			intercambio.sendResponseHeaders(200, cuerpo.length);
			intercambio.getResponseBody().write(cuerpo);
			intercambio.close();
		});
		servidor.start();
		url = "http://127.0.0.1:" + servidor.getAddress().getPort() + "/x.png";
	}

	@AfterEach
	void pararServidor() {
		servidor.stop(0);
	}

	// ------------------------------------------------------------------ válidos

	@Test
	void pngJpegYSvgSalenComoPngQueCabeEnElMaximo() throws Exception {
		LogoEscuelaProcesador.LogoProcesado png = procesador.procesar(png(rellena(1200, 300, Color.BLUE)));
		assertEquals(600, png.ancho());
		assertEquals(150, png.alto());
		assertEsPng(png.png(), 600, 150);

		LogoEscuelaProcesador.LogoProcesado jpeg = procesador.procesar(jpeg(rellena(64, 48, Color.GREEN)));
		assertEsPng(jpeg.png(), 64, 48);

		String svg = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
				+ "<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" "
				+ "width=\"200\" height=\"100\" viewBox=\"0 0 200 100\"><defs><linearGradient id=\"g\">"
				+ "<stop offset=\"0\" stop-color=\"#f00\"/><stop offset=\"1\" stop-color=\"#00f\"/></linearGradient>"
				+ "<rect id=\"r\" width=\"50\" height=\"50\"/></defs>"
				+ "<rect width=\"200\" height=\"100\" style=\"fill:url(#g)\"/><use xlink:href=\"#r\" fill=\"#fff\"/></svg>";
		LogoEscuelaProcesador.LogoProcesado deSvg = procesador.procesar(svg.getBytes(StandardCharsets.UTF_8));
		assertEsPng(deSvg.png(), 600, 300);
		assertEquals(0, peticiones.get());
	}

	// ------------------------------------------------------------------ SVG maliciosos

	@Test
	void subirSvgConEntidadExterna_noLaResuelve() throws Exception {
		Path secreto = carpeta.resolve("secreto.txt");
		Files.writeString(secreto, "SECRETO-NO-DEBE-SALIR");
		String fichero = secreto.toUri().toString();
		List<String> maliciosos = List.of(
				// XXE: entidad externa a un fichero local y a una URL.
				"<?xml version=\"1.0\"?><!DOCTYPE svg [<!ENTITY x SYSTEM \"" + fichero + "\">]>"
						+ "<svg xmlns=\"http://www.w3.org/2000/svg\"><text>&x;</text></svg>",
				"<?xml version=\"1.0\"?><!DOCTYPE svg [<!ENTITY x SYSTEM \"" + url + "\">]>"
						+ "<svg xmlns=\"http://www.w3.org/2000/svg\"><text>&x;</text></svg>",
				// DTD externa (SYSTEM) y entidad de parámetro.
				"<?xml version=\"1.0\"?><!DOCTYPE svg SYSTEM \"" + fichero + "\">"
						+ "<svg xmlns=\"http://www.w3.org/2000/svg\"/>",
				"<?xml version=\"1.0\"?><!DOCTYPE svg [<!ENTITY % p SYSTEM \"" + url + "\"> %p;]>"
						+ "<svg xmlns=\"http://www.w3.org/2000/svg\"/>",
				// Billion laughs (entidades internas).
				"<?xml version=\"1.0\"?><!DOCTYPE svg [<!ENTITY a \"aaaaaaaaaa\"><!ENTITY b \"&a;&a;&a;&a;&a;\">]>"
						+ "<svg xmlns=\"http://www.w3.org/2000/svg\"><text>&b;</text></svg>",
				// Enlaces y recursos externos (SSRF): xlink:href, href, CSS url() y @import.
				"<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\">"
						+ "<image xlink:href=\"" + url + "\" width=\"10\" height=\"10\"/></svg>",
				"<svg xmlns=\"http://www.w3.org/2000/svg\"><image href=\"" + fichero + "\" width=\"10\" height=\"10\"/></svg>",
				"<svg xmlns=\"http://www.w3.org/2000/svg\"><rect width=\"9\" height=\"9\" style=\"fill:url('" + url
						+ "')\"/></svg>",
				"<svg xmlns=\"http://www.w3.org/2000/svg\"><style>@import url(" + url + ");</style></svg>",
				"<svg xmlns=\"http://www.w3.org/2000/svg\"><style>rect{fill:url(" + url + ")}</style></svg>",
				// Scripts y eventos.
				"<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>",
				"<svg xmlns=\"http://www.w3.org/2000/svg\" onload=\"alert(1)\"/>",
				"<svg xmlns=\"http://www.w3.org/2000/svg\"><foreignObject><p xmlns=\"http://www.w3.org/1999/xhtml\">x</p>"
						+ "</foreignObject></svg>",
				// XInclude y proceso.
				"<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xi=\"http://www.w3.org/2001/XInclude\">"
						+ "<xi:include href=\"" + fichero + "\" parse=\"text\"/></svg>",
				"<?xml version=\"1.0\"?><?xml-stylesheet href=\"" + url + "\"?><svg xmlns=\"http://www.w3.org/2000/svg\"/>");
		for (String svg : maliciosos) {
			IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
					() -> procesador.procesar(svg.getBytes(StandardCharsets.UTF_8)), svg);
			assertTrue(!error.getMessage().contains("SECRETO"), error.getMessage());
		}
		assertEquals(0, peticiones.get(), "ningún SVG debe hacer peticiones");
	}

	@Test
	void batikNoCargaRecursosExternosAunqueLlegaran() {
		// Segunda barrera: directamente al rasterizador, saltándose la revisión.
		String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" "
				+ "width=\"40\" height=\"40\"><image xlink:href=\"" + url + "\" width=\"40\" height=\"40\"/></svg>";
		try {
			LogoEscuelaProcesador.rasterizarSvg(svg.getBytes(StandardCharsets.UTF_8));
		} catch (IllegalArgumentException rechazado) {
			// Rechazar también vale; lo que importa es que no haya petición.
		}
		assertEquals(0, peticiones.get(), "Batik no debe pedir la imagen externa");
	}

	// ------------------------------------------------------------------ tipos, tamaño y dimensiones

	@Test
	void subirTipoNoPermitido_400() throws Exception {
		BufferedImage imagen = rellena(32, 32, Color.RED);
		ByteArrayOutputStream gif = new ByteArrayOutputStream();
		ImageIO.write(imagen, "gif", gif);
		ByteArrayOutputStream bmp = new ByteArrayOutputStream();
		ImageIO.write(imagen, "bmp", bmp);
		for (byte[] contenido : List.of(gif.toByteArray(), bmp.toByteArray(),
				"%PDF-1.4 no es una imagen".getBytes(StandardCharsets.UTF_8),
				"solo texto".getBytes(StandardCharsets.UTF_8),
				"<html><body>no</body></html>".getBytes(StandardCharsets.UTF_8),
				"<svg>sin espacio de nombres</svg>".getBytes(StandardCharsets.UTF_8),
				// Firma de PNG con basura detrás.
				new byte[] { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 1, 2, 3 })) {
			assertThrows(IllegalArgumentException.class, () -> procesador.procesar(contenido));
		}
		assertThrows(IllegalArgumentException.class, () -> procesador.procesar(new byte[0]));
		assertThrows(IllegalArgumentException.class, () -> procesador.procesar(null));
	}

	@Test
	void subirDemasiadoGrande_400() {
		byte[] grande = new byte[LogoEscuelaProcesador.TAMANO_MAXIMO + 1];
		grande[0] = (byte) 0x89;
		IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> procesador.procesar(grande));
		assertTrue(error.getMessage().contains("1024 KB"), error.getMessage());
	}

	@Test
	void svgDemasiadoComplejo_400() {
		String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" "
				+ "width=\"10\" height=\"10\">";
		// Demasiados <use>.
		StringBuilder muchos = new StringBuilder(svg + "<defs><rect id=\"r\" width=\"1\" height=\"1\"/></defs>");
		for (int i = 0; i <= LogoEscuelaProcesador.MAXIMO_USE; i++) {
			muchos.append("<use xlink:href=\"#r\"/>");
		}
		muchos.append("</svg>");
		// Cadena que se multiplica al dibujar: un <use> de un grupo que a su vez usa otro.
		String cadena = svg + "<defs><rect id=\"a\" width=\"1\" height=\"1\"/><g id=\"b\"><use xlink:href=\"#a\"/>"
				+ "<use xlink:href=\"#a\"/></g><g id=\"c\"><use xlink:href=\"#b\"/><use xlink:href=\"#b\"/></g></defs>"
				+ "<use xlink:href=\"#c\"/></svg>";
		// Anidamiento excesivo.
		String anidado = svg + "<g>".repeat(LogoEscuelaProcesador.MAXIMO_ANIDAMIENTO) + "<rect width=\"1\" height=\"1\"/>"
				+ "</g>".repeat(LogoEscuelaProcesador.MAXIMO_ANIDAMIENTO) + "</svg>";
		for (String complejo : List.of(muchos.toString(), cadena, anidado)) {
			IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
					() -> procesador.procesar(complejo.getBytes(StandardCharsets.UTF_8)));
			assertEquals(LogoEscuelaProcesador.SVG_COMPLEJO, error.getMessage());
		}
	}

	@Test
	void dimensionesFueraDeRango_400() throws Exception {
		assertThrows(IllegalArgumentException.class, () -> procesador.procesar(png(rellena(8, 8, Color.RED))));
		assertThrows(IllegalArgumentException.class, () -> procesador.procesar(png(rellena(4100, 20, Color.RED))));
	}

	// ------------------------------------------------------------------ utilidades

	private static void assertEsPng(byte[] contenido, int ancho, int alto) throws IOException {
		assertEquals((byte) 0x89, contenido[0]);
		assertEquals('P', contenido[1]);
		BufferedImage imagen = ImageIO.read(new ByteArrayInputStream(contenido));
		assertNotNull(imagen);
		assertEquals(ancho, imagen.getWidth());
		assertEquals(alto, imagen.getHeight());
	}

	static BufferedImage rellena(int ancho, int alto, Color color) {
		BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
		var g = imagen.createGraphics();
		g.setColor(color);
		g.fillRect(0, 0, ancho, alto);
		g.dispose();
		return imagen;
	}

	static byte[] png(BufferedImage imagen) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(imagen, "png", out);
		return out.toByteArray();
	}

	static byte[] jpeg(BufferedImage imagen) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(imagen, "jpg", out);
		return out.toByteArray();
	}
}
