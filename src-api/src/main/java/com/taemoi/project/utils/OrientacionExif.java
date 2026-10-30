package com.taemoi.project.utils;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Orientación EXIF de un JPEG. ImageIO decodifica los píxeles tal como están en el fichero y la recodificación pierde los
 * metadatos: sin aplicarla, una foto de móvil se queda girada. No hay librería de EXIF en el proyecto y solo hace falta esta
 * etiqueta, así que se lee del segmento APP1.
 *
 * <p>Una etiqueta ausente o corrupta nunca rompe una subida: la foto se guarda tal cual ({@link #NORMAL}).
 */
public final class OrientacionExif {

	public static final int NORMAL = 1;

	private static final int MARCA = 0xFF;
	private static final int INICIO_JPEG = 0xD8;
	private static final int APP1 = 0xE1;
	private static final int INICIO_IMAGEN = 0xDA;
	private static final int FIN_JPEG = 0xD9;
	private static final byte[] CABECERA_EXIF = "Exif\0\0".getBytes(StandardCharsets.ISO_8859_1);
	private static final int NUMERO_MAGICO_TIFF = 42;
	private static final int ETIQUETA_ORIENTACION = 0x0112;
	private static final int TIPO_SHORT = 3;
	private static final int BYTES_ENTRADA = 12;

	private OrientacionExif() {
	}

	/** @return de 1 a 8, o {@link #NORMAL} si no es un JPEG o no hay una etiqueta válida */
	public static int leer(InputStream jpeg) {
		try {
			if (jpeg.read() != MARCA || jpeg.read() != INICIO_JPEG) {
				return NORMAL;
			}
			while (true) {
				if (jpeg.read() != MARCA) {
					return NORMAL;
				}
				int marca = jpeg.read();
				while (marca == MARCA) {
					marca = jpeg.read();
				}
				if (marca < 0 || marca == INICIO_IMAGEN || marca == FIN_JPEG) {
					return NORMAL;
				}
				int longitud = (jpeg.read() << 8) | jpeg.read();
				if (longitud < 2) {
					return NORMAL;
				}
				int bytes = longitud - 2;
				if (marca != APP1) {
					jpeg.skipNBytes(bytes);
					continue;
				}
				byte[] segmento = jpeg.readNBytes(bytes);
				if (segmento.length < bytes) {
					return NORMAL;
				}
				if (empiezaPorCabeceraExif(segmento)) {
					return orientacionDeTiff(ByteBuffer.wrap(segmento, CABECERA_EXIF.length, bytes - CABECERA_EXIF.length).slice());
				}
			}
		} catch (IOException | RuntimeException e) {
			return NORMAL;
		}
	}

	/**
	 * @return la imagen como se debe ver; la misma instancia si no hay nada que girar. Si no, una copia con los píxeles
	 *         exactos (sin interpolar), que ocupa lo mismo que el original mientras viven las dos.
	 */
	public static BufferedImage aplicar(BufferedImage imagen, int orientacion) {
		if (orientacion <= NORMAL || orientacion > 8) {
			return imagen;
		}
		int ancho = imagen.getWidth();
		int alto = imagen.getHeight();
		boolean intercambiaLados = orientacion >= 5;
		BufferedImage salida = new BufferedImage(intercambiaLados ? alto : ancho, intercambiaLados ? ancho : alto,
				BufferedImage.TYPE_3BYTE_BGR);
		Graphics2D graphics = salida.createGraphics();
		try {
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			graphics.drawImage(imagen, transformacion(orientacion, ancho, alto), null);
		} finally {
			graphics.dispose();
		}
		return salida;
	}

	private static boolean empiezaPorCabeceraExif(byte[] segmento) {
		return segmento.length > CABECERA_EXIF.length
				&& Arrays.equals(segmento, 0, CABECERA_EXIF.length, CABECERA_EXIF, 0, CABECERA_EXIF.length);
	}

	/** {@code tiff}: el bloque TIFF, cuyos desplazamientos cuentan desde su primer byte. Un dato fuera de rango lanza. */
	private static int orientacionDeTiff(ByteBuffer tiff) {
		if (tiff.get(0) == 'I' && tiff.get(1) == 'I') {
			tiff.order(ByteOrder.LITTLE_ENDIAN);
		} else if (tiff.get(0) == 'M' && tiff.get(1) == 'M') {
			tiff.order(ByteOrder.BIG_ENDIAN);
		} else {
			return NORMAL;
		}
		if (Short.toUnsignedInt(tiff.getShort(2)) != NUMERO_MAGICO_TIFF) {
			return NORMAL;
		}
		int directorio = tiff.getInt(4);
		int entradas = Short.toUnsignedInt(tiff.getShort(directorio));
		int primera = directorio + 2;
		if ((long) primera + (long) entradas * BYTES_ENTRADA > tiff.limit()) {
			return NORMAL;
		}
		for (int i = 0; i < entradas; i++) {
			int entrada = primera + i * BYTES_ENTRADA;
			if (Short.toUnsignedInt(tiff.getShort(entrada)) != ETIQUETA_ORIENTACION) {
				continue;
			}
			if (Short.toUnsignedInt(tiff.getShort(entrada + 2)) != TIPO_SHORT || tiff.getInt(entrada + 4) != 1) {
				return NORMAL;
			}
			int valor = Short.toUnsignedInt(tiff.getShort(entrada + 8));
			return valor >= NORMAL && valor <= 8 ? valor : NORMAL;
		}
		return NORMAL;
	}

	/** Lleva el píxel guardado (x, y) a donde se ve: x' = m00·x + m01·y + m02 e y' = m10·x + m11·y + m12. */
	private static AffineTransform transformacion(int orientacion, int ancho, int alto) {
		return switch (orientacion) {
		case 2 -> new AffineTransform(-1, 0, 0, 1, ancho, 0); // espejo horizontal
		case 3 -> new AffineTransform(-1, 0, 0, -1, ancho, alto); // 180°
		case 4 -> new AffineTransform(1, 0, 0, -1, 0, alto); // espejo vertical
		case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0); // traspuesta
		case 6 -> new AffineTransform(0, 1, -1, 0, alto, 0); // 90° a la derecha
		case 7 -> new AffineTransform(0, -1, -1, 0, alto, ancho); // traspuesta inversa
		case 8 -> new AffineTransform(0, -1, 1, 0, 0, ancho); // 90° a la izquierda
		default -> throw new IllegalArgumentException("Orientación no válida: " + orientacion);
		};
	}
}
