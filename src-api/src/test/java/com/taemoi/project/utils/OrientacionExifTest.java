package com.taemoi.project.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Orientación EXIF de las fotos de la galería. Lo que falla aquí acaba en una foto girada o en una subida rechazada: por eso
 * cada orientación se comprueba con su matriz de píxeles esperada y cada corrupción de la etiqueta, con que no rompa.
 */
class OrientacionExifTest {

	private static final int A = 0x110000;
	private static final int B = 0x220000;
	private static final int C = 0x330000;
	private static final int D = 0x440000;
	private static final int E = 0x550000;
	private static final int F = 0x660000;

	/** Tal como queda guardada: «A B C» es la fila 0 del fichero. */
	private static final int[][] GUARDADA = { { A, B, C }, { D, E, F } };

	/** Lo que ve quien mira la foto, según la tabla de la etiqueta 0x0112 del estándar EXIF. */
	private static final Map<Integer, int[][]> VISTA = Map.of(
			1, GUARDADA,
			2, new int[][] { { C, B, A }, { F, E, D } },
			3, new int[][] { { F, E, D }, { C, B, A } },
			4, new int[][] { { D, E, F }, { A, B, C } },
			5, new int[][] { { A, D }, { B, E }, { C, F } },
			6, new int[][] { { D, A }, { E, B }, { F, C } },
			7, new int[][] { { F, C }, { E, B }, { D, A } },
			8, new int[][] { { C, F }, { B, E }, { A, D } });

	// ------------------------------------------------------------------ leer

	@Test
	void leeLasOchoOrientaciones_enLosDosOrdenesDeBytes() {
		for (ByteOrder orden : List.of(ByteOrder.LITTLE_ENDIAN, ByteOrder.BIG_ENDIAN)) {
			for (int orientacion = 1; orientacion <= 8; orientacion++) {
				assertEquals(orientacion, leer(jpeg(app1(tiff(orden, orientacion)))), orden + " " + orientacion);
			}
		}
	}

	@Test
	void encuentraLaEtiquetaDetrasDeOtrosSegmentos() {
		byte[] xmp = segmento(0xE1, "http://ns.adobe.com/xap/1.0/\0<x/>".getBytes(StandardCharsets.UTF_8));
		byte[] app0 = segmento(0xE0, new byte[14]);

		assertEquals(6, leer(jpeg(app0, xmp, app1(tiff(ByteOrder.BIG_ENDIAN, 6)))));
	}

	@Test
	void sinEtiquetaEsNormal() throws IOException {
		assertEquals(1, leer(jpeg()), "JPEG sin APP1");
		assertEquals(1, leer(jpeg(segmento(0xE1, "http://ns.adobe.com/xap/1.0/\0".getBytes(StandardCharsets.UTF_8)))),
				"APP1 que no es EXIF");
		assertEquals(1, leer(jpeg(app1(tiffSinOrientacion()))), "EXIF sin la etiqueta");
		assertEquals(1, leer(new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A }), "No es un JPEG");
		assertEquals(1, leer(new byte[0]), "Fichero vacío");
	}

	@Test
	void unaEtiquetaFueraDeRangoEsNormal() {
		for (int valor : List.of(0, 9, 255, 65535)) {
			assertEquals(1, leer(jpeg(app1(tiff(ByteOrder.LITTLE_ENDIAN, valor)))), "Valor " + valor);
		}
	}

	@Test
	void unaEtiquetaCorruptaEsNormal_sinLanzarNada() {
		byte[] buena = tiff(ByteOrder.LITTLE_ENDIAN, 6);

		byte[] sinCabeceraTiff = buena.clone();
		sinCabeceraTiff[0] = 'X';
		byte[] sinNumeroMagico = buena.clone();
		sinNumeroMagico[2] = 0;
		byte[] directorioFuera = buena.clone();
		ByteBuffer.wrap(directorioFuera).order(ByteOrder.LITTLE_ENDIAN).putInt(4, Integer.MAX_VALUE);
		byte[] directorioNegativo = buena.clone();
		ByteBuffer.wrap(directorioNegativo).order(ByteOrder.LITTLE_ENDIAN).putInt(4, -1);
		byte[] demasiadasEntradas = buena.clone();
		ByteBuffer.wrap(demasiadasEntradas).order(ByteOrder.LITTLE_ENDIAN).putShort(8, (short) 0xFFFF);
		byte[] otroTipo = buena.clone();
		ByteBuffer.wrap(otroTipo).order(ByteOrder.LITTLE_ENDIAN).putShort(12, (short) 2);
		byte[] variosValores = buena.clone();
		ByteBuffer.wrap(variosValores).order(ByteOrder.LITTLE_ENDIAN).putInt(14, 2);

		for (byte[] corrupta : List.of(sinCabeceraTiff, sinNumeroMagico, directorioFuera, directorioNegativo,
				demasiadasEntradas, otroTipo, variosValores)) {
			assertEquals(1, leer(jpeg(app1(corrupta))));
		}
		int finDelDirectorio = buena.length - 4;
		for (int corte = 0; corte < finDelDirectorio; corte++) {
			byte[] truncada = new byte[corte];
			System.arraycopy(buena, 0, truncada, 0, corte);
			assertEquals(1, leer(jpeg(app1(truncada))), "TIFF cortado en " + corte);
		}
		byte[] entero = jpeg(app1(buena));
		int finDeApp1 = entero.length - 4;
		for (int corte = 0; corte < finDeApp1; corte++) {
			byte[] truncado = new byte[corte];
			System.arraycopy(entero, 0, truncado, 0, corte);
			assertEquals(1, leer(truncado), "JPEG cortado en " + corte);
		}
	}

	@Test
	void unSegmentoConLongitudImposibleEsNormal() {
		byte[] longitudCero = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE1, 0, 0 };
		byte[] longitudUno = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE1, 0, 1 };
		byte[] masLargoQueElFichero = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE1, (byte) 0xFF, (byte) 0xFF, 1 };
		byte[] sinMarca = { (byte) 0xFF, (byte) 0xD8, 0x00, 0x00, 0x00 };

		for (byte[] raro : List.of(longitudCero, longitudUno, masLargoQueElFichero, sinMarca)) {
			assertEquals(1, leer(raro));
		}
	}

	/** Lo que hay después del inicio de la imagen son datos comprimidos, no metadatos. */
	@Test
	void noBuscaDetrasDelInicioDeLaImagen() {
		byte[] inicioDeImagen = { (byte) 0xFF, (byte) 0xDA, 0, 2 };

		assertEquals(1, leer(jpeg(inicioDeImagen, app1(tiff(ByteOrder.BIG_ENDIAN, 6)))));
	}

	@Test
	void unFalloAlLeerEsNormal() {
		InputStream rota = new InputStream() {
			@Override
			public int read() throws IOException {
				throw new IOException("disco");
			}
		};

		assertEquals(1, OrientacionExif.leer(rota));
	}

	// ------------------------------------------------------------------ aplicar

	@Test
	void aplicaLasOchoOrientacionesPixelAPixel() {
		VISTA.forEach((orientacion, esperada) -> {
			BufferedImage girada = OrientacionExif.aplicar(imagen(GUARDADA), orientacion);
			assertEquals(esperada[0].length, girada.getWidth(), "Ancho, orientación " + orientacion);
			assertEquals(esperada.length, girada.getHeight(), "Alto, orientación " + orientacion);
			for (int y = 0; y < esperada.length; y++) {
				for (int x = 0; x < esperada[y].length; x++) {
					assertEquals(esperada[y][x], girada.getRGB(x, y) & 0xFFFFFF,
							"Orientación " + orientacion + ", píxel " + x + "," + y);
				}
			}
		});
	}

	/** Lo normal es no girar: sin una segunda copia de los píxeles. */
	@Test
	void sinGiroDevuelveLaMismaImagen() {
		BufferedImage imagen = imagen(GUARDADA);

		for (int orientacion : List.of(1, 0, 9, -1)) {
			assertSame(imagen, OrientacionExif.aplicar(imagen, orientacion), "Orientación " + orientacion);
		}
	}

	// ------------------------------------------------------------------ utilidades

	private static int leer(byte[] contenido) {
		return OrientacionExif.leer(new ByteArrayInputStream(contenido));
	}

	private static BufferedImage imagen(int[][] filas) {
		BufferedImage imagen = new BufferedImage(filas[0].length, filas.length, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < filas.length; y++) {
			for (int x = 0; x < filas[y].length; x++) {
				imagen.setRGB(x, y, filas[y][x]);
			}
		}
		return imagen;
	}

	/** SOI, los segmentos y SOS; basta para quien solo mira la cabecera. */
	private static byte[] jpeg(byte[]... segmentos) {
		ByteArrayOutputStream salida = new ByteArrayOutputStream();
		salida.writeBytes(new byte[] { (byte) 0xFF, (byte) 0xD8 });
		for (byte[] segmento : segmentos) {
			salida.writeBytes(segmento);
		}
		salida.writeBytes(new byte[] { (byte) 0xFF, (byte) 0xDA, 0, 2 });
		return salida.toByteArray();
	}

	private static byte[] app1(byte[] tiff) {
		ByteBuffer contenido = ByteBuffer.allocate(6 + tiff.length);
		contenido.put("Exif\0\0".getBytes(StandardCharsets.ISO_8859_1)).put(tiff);
		return segmento(0xE1, contenido.array());
	}

	private static byte[] segmento(int marca, byte[] contenido) {
		ByteBuffer segmento = ByteBuffer.allocate(4 + contenido.length);
		segmento.put((byte) 0xFF).put((byte) marca).putShort((short) (contenido.length + 2)).put(contenido);
		return segmento.array();
	}

	/** Directorio con una etiqueta ajena delante, para no depender de que Orientation sea la primera. */
	private static byte[] tiff(ByteOrder orden, int orientacion) {
		ByteBuffer tiff = cabeceraTiff(orden, 2);
		tiff.putShort((short) 0x0112).putShort((short) 3).putInt(1).putShort((short) orientacion).putShort((short) 0);
		tiff.putShort((short) 0x010F).putShort((short) 2).putInt(4).putInt(0);
		return tiff.array();
	}

	private static byte[] tiffSinOrientacion() {
		ByteBuffer tiff = cabeceraTiff(ByteOrder.BIG_ENDIAN, 1);
		tiff.putShort((short) 0x010F).putShort((short) 2).putInt(4).putInt(0);
		return tiff.array();
	}

	private static ByteBuffer cabeceraTiff(ByteOrder orden, int entradas) {
		ByteBuffer tiff = ByteBuffer.allocate(8 + 2 + 12 * entradas + 4).order(orden);
		tiff.put((byte) (orden == ByteOrder.LITTLE_ENDIAN ? 'I' : 'M'));
		tiff.put((byte) (orden == ByteOrder.LITTLE_ENDIAN ? 'I' : 'M'));
		tiff.putShort((short) 42).putInt(8).putShort((short) entradas);
		return tiff;
	}
}
