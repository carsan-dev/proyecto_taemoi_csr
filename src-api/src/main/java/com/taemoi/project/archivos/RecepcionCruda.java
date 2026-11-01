package com.taemoi.project.archivos;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import org.springframework.http.HttpStatus;

import com.taemoi.project.exceptions.MaterialExamenException;

/**
 * Recibe el cuerpo de una subida en un temporal oculto de la carpeta de destino: comprueba la cabecera real antes de
 * escribir, cuenta los bytes al leer y nunca retiene el fichero en memoria. El temporal queda en la misma carpeta para que
 * el movimiento final sea atómico; los lectores lo ignoran porque empieza por punto y no tiene extensión de material.
 */
public final class RecepcionCruda {

	public static final String PREFIJO_TEMPORAL = ".subida-";

	private static final int BYTES_CABECERA = 1024;
	private static final int BYTES_BUFFER = 64 * 1024;
	private static final Set<String> CAJAS_ISO = Set.of("ftyp", "moov", "mdat", "wide", "free");
	private static final byte[] EBML = { 0x1A, 0x45, (byte) 0xDF, (byte) 0xA3 };
	private static final byte[] FIRMA_PDF = "%PDF-".getBytes(StandardCharsets.US_ASCII);

	private RecepcionCruda() {
	}

	public static Path temporalEn(Path carpeta) {
		return carpeta.resolve(PREFIJO_TEMPORAL + UUID.randomUUID());
	}

	/**
	 * Vuelca el cuerpo en {@code temporal}.
	 *
	 * @throws MaterialExamenException 415 si la cabecera no pasa {@code cabeceraValida}, 413 si se leen más de {@code
	 *         maximo} bytes, 400 si el cuerpo no coincide con su longitud (el cliente cortó) y 500 si no se puede escribir.
	 */
	public static void guardar(CuerpoCrudo cuerpo, Path temporal, long maximo, Predicate<byte[]> cabeceraValida,
			String mensajeCabecera) {
		InputStream entrada = cuerpo.contenido();
		byte[] cabecera;
		try {
			cabecera = entrada.readNBytes(BYTES_CABECERA);
		} catch (IOException e) {
			throw cortada();
		}
		if (!cabeceraValida.test(cabecera)) {
			throw new MaterialExamenException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, mensajeCabecera);
		}

		long total = cabecera.length;
		try (OutputStream salida = Files.newOutputStream(temporal, StandardOpenOption.CREATE_NEW,
				StandardOpenOption.WRITE)) {
			salida.write(cabecera);
			byte[] buffer = new byte[BYTES_BUFFER];
			int leidos;
			while ((leidos = leer(entrada, buffer)) >= 0) {
				total += leidos;
				if (total > maximo) {
					throw new MaterialExamenException(HttpStatus.PAYLOAD_TOO_LARGE, "El fichero supera el tamaño máximo.");
				}
				salida.write(buffer, 0, leidos);
			}
		} catch (IOException e) {
			throw new MaterialExamenException(HttpStatus.INTERNAL_SERVER_ERROR,
					"No se ha podido guardar el fichero en el servidor.");
		}
		if (total != cuerpo.longitud()) {
			throw cortada();
		}
	}

	/** {@code ftyp}, {@code moov}, {@code mdat}, {@code wide} o {@code free} en el byte 4; EBML en webm. */
	public static boolean cabeceraDeVideo(String extension, byte[] cabecera) {
		if ("webm".equals(extension.toLowerCase(Locale.ROOT))) {
			return cabecera.length >= EBML.length && Arrays.equals(cabecera, 0, EBML.length, EBML, 0, EBML.length);
		}
		return cabecera.length >= 8
				&& CAJAS_ISO.contains(new String(cabecera, 4, 4, StandardCharsets.ISO_8859_1));
	}

	/** {@code %PDF-} dentro del primer KB, donde los lectores de PDF lo admiten. */
	public static boolean cabeceraDePdf(byte[] cabecera) {
		for (int i = 0; i + FIRMA_PDF.length <= cabecera.length; i++) {
			if (Arrays.equals(cabecera, i, i + FIRMA_PDF.length, FIRMA_PDF, 0, FIRMA_PDF.length)) {
				return true;
			}
		}
		return false;
	}

	/** Un fallo al leer es del cliente, no del disco. */
	private static int leer(InputStream entrada, byte[] buffer) {
		try {
			return entrada.read(buffer);
		} catch (IOException e) {
			throw cortada();
		}
	}

	private static MaterialExamenException cortada() {
		return new MaterialExamenException(HttpStatus.BAD_REQUEST, "La subida se ha cortado. Vuelve a intentarlo.");
	}
}
