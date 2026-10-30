package com.taemoi.project.controllers;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Iterator;
import java.util.concurrent.Semaphore;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Respuesta HTTP de una imagen de evento, común al cartel ({@code GET /api/eventos/{id}/imagen}) y a las fotos de la
 * galería: {@code ?w=} con sus límites, ETag, Last-Modified y la caché según la visibilidad del evento.
 *
 * <p>Quien llama decide antes si la imagen se puede servir: aquí no se comprueba la visibilidad. El semáforo es uno
 * para toda la JVM: el cartel y las fotos comparten el tope de redimensiones simultáneas.
 */
final class RespuestaImagen {

	private static final int MIN_ANCHO_IMAGEN = 160;
	private static final int MAX_ANCHO_IMAGEN = 2200;
	private static final float CALIDAD_WEBP_REDIMENSION = 0.78f;
	private static final long MAX_TAMANO_ORIGEN_RESIZE_BYTES = 8L * 1024 * 1024;
	private static final long MAX_PIXELES_ORIGEN_RESIZE = 12_000_000L;
	private static final Semaphore RESIZE_SEMAPHORE = new Semaphore(2, true);

	private RespuestaImagen() {
	}

	/**
	 * @param rutaArchivo     fichero de la imagen, ya resuelto; si no existe, 404
	 * @param tipo            MIME guardado con la imagen
	 * @param nombre          nombre para {@code Content-Disposition}
	 * @param anchoSolicitado {@code ?w=} tal como llega
	 * @param eventoVisible   la de un evento oculto no debe quedar en cachés compartidas: solo la sirve con sesión de gestión
	 */
	static ResponseEntity<Resource> servir(Path rutaArchivo, String tipo, String nombre, Integer anchoSolicitado,
			boolean eventoVisible) throws IOException {
		CacheControl cacheControl = eventoVisible
				? CacheControl.maxAge(Duration.ofHours(8)).cachePublic().mustRevalidate()
				: CacheControl.maxAge(Duration.ofHours(8)).cachePrivate().mustRevalidate();

		Resource recurso = new UrlResource(rutaArchivo.toUri());

		if (!recurso.exists()) {
			return ResponseEntity.notFound().build();
		}

		MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
		if (tipo != null) {
			try {
				mediaType = MediaType.parseMediaType(tipo);
			} catch (Exception e) {
				mediaType = MediaType.APPLICATION_OCTET_STREAM;
			}
		}

		int anchoNormalizado = normalizarAnchoImagen(anchoSolicitado);
		if (anchoNormalizado > 0) {
			ResponseEntity<Resource> respuestaRedimensionada =
					intentarConstruirRespuestaRedimensionada(rutaArchivo, mediaType, nombre, anchoNormalizado,
							cacheControl);
			if (respuestaRedimensionada != null) {
				return respuestaRedimensionada;
			}
		}

		return construirRespuestaImagenOriginal(recurso, mediaType, nombre, rutaArchivo, anchoNormalizado, cacheControl);
	}

	private static ResponseEntity<Resource> construirRespuestaImagenOriginal(
			Resource recurso,
			MediaType mediaType,
			String nombreImagen,
			Path rutaArchivo,
			int anchoNormalizado,
			CacheControl cacheControl) throws IOException {
		return ResponseEntity.ok()
				.cacheControl(cacheControl)
				.lastModified(Files.getLastModifiedTime(rutaArchivo).toMillis())
				.header(HttpHeaders.VARY, "Accept-Encoding")
				.contentType(mediaType)
				.header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + nombreImagen + "\"")
				.header(HttpHeaders.ETAG, generarEtagImagen(rutaArchivo, anchoNormalizado, mediaType.toString()))
				.body(recurso);
	}

	private static ResponseEntity<Resource> intentarConstruirRespuestaRedimensionada(
			Path rutaArchivo,
			MediaType mediaTypeOriginal,
			String nombreImagen,
			int anchoObjetivo,
			CacheControl cacheControl) {
		if (!esImagenRasterRedimensionable(mediaTypeOriginal)) {
			return null;
		}

		if (!RESIZE_SEMAPHORE.tryAcquire()) {
			return null;
		}

		try {
			long tamanoArchivo = Files.size(rutaArchivo);
			if (tamanoArchivo > MAX_TAMANO_ORIGEN_RESIZE_BYTES) {
				return null;
			}

			int[] dimensionesOriginales = obtenerDimensionesImagen(rutaArchivo);
			if (dimensionesOriginales == null) {
				return null;
			}

			int anchoOriginal = dimensionesOriginales[0];
			int altoOriginal = dimensionesOriginales[1];
			if (anchoOriginal <= 0 || altoOriginal <= 0) {
				return null;
			}
			if (anchoOriginal <= anchoObjetivo) {
				return null;
			}
			if ((long) anchoOriginal * altoOriginal > MAX_PIXELES_ORIGEN_RESIZE) {
				return null;
			}

			BufferedImage original = ImageIO.read(rutaArchivo.toFile());
			if (original == null || original.getWidth() <= 0 || original.getHeight() <= 0) {
				return null;
			}

			int altoObjetivo = Math.max(1, (int) Math.round((double) original.getHeight() * anchoObjetivo / original.getWidth()));
			BufferedImage redimensionada = new BufferedImage(anchoObjetivo, altoObjetivo, BufferedImage.TYPE_3BYTE_BGR);

			Graphics2D graphics = redimensionada.createGraphics();
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			graphics.drawImage(original, 0, 0, anchoObjetivo, altoObjetivo, null);
			graphics.dispose();

			byte[] imagenBytes = codificarWebp(redimensionada, CALIDAD_WEBP_REDIMENSION);
			MediaType mediaTypeSalida = MediaType.parseMediaType("image/webp");
			ByteArrayResource recurso = new ByteArrayResource(imagenBytes);
			long lastModified = Files.getLastModifiedTime(rutaArchivo).toMillis();

			return ResponseEntity.ok()
					.cacheControl(cacheControl)
					.lastModified(lastModified)
					.header(HttpHeaders.VARY, "Accept-Encoding")
					.contentType(mediaTypeSalida)
					.contentLength(imagenBytes.length)
					.header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + nombreImagen + "\"")
					.header(HttpHeaders.ETAG, generarEtagImagen(rutaArchivo, anchoObjetivo, mediaTypeSalida.toString()))
					.body(recurso);
		} catch (Throwable e) {
			return null;
		} finally {
			RESIZE_SEMAPHORE.release();
		}
	}

	private static int[] obtenerDimensionesImagen(Path rutaArchivo) {
		try (ImageInputStream input = ImageIO.createImageInputStream(rutaArchivo.toFile())) {
			if (input == null) {
				return null;
			}
			Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
			if (!readers.hasNext()) {
				return null;
			}

			ImageReader reader = readers.next();
			try {
				reader.setInput(input);
				int width = reader.getWidth(0);
				int height = reader.getHeight(0);
				return new int[] { width, height };
			} finally {
				reader.dispose();
			}
		} catch (Exception e) {
			return null;
		}
	}

	private static byte[] codificarWebp(BufferedImage imagen, float quality) throws IOException {
		Iterator<ImageWriter> writers = ImageIO.getImageWritersByMIMEType("image/webp");
		if (!writers.hasNext()) {
			writers = ImageIO.getImageWritersBySuffix("webp");
		}
		if (!writers.hasNext()) {
			throw new IOException("No hay codificador WebP disponible.");
		}

		ImageWriter writer = writers.next();
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		try (ImageOutputStream imageOutputStream = ImageIO.createImageOutputStream(outputStream)) {
			writer.setOutput(imageOutputStream);
			ImageWriteParam writeParam = writer.getDefaultWriteParam();
			if (writeParam.canWriteCompressed()) {
				writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
				String[] compressionTypes = writeParam.getCompressionTypes();
				if (compressionTypes != null && compressionTypes.length > 0) {
					writeParam.setCompressionType(seleccionarTipoCompresion(compressionTypes));
				}
				writeParam.setCompressionQuality(quality);
			}
			writer.write(null, new IIOImage(imagen, null, null), writeParam);
			imageOutputStream.flush();
			return outputStream.toByteArray();
		} finally {
			writer.dispose();
		}
	}

	private static String seleccionarTipoCompresion(String[] compressionTypes) {
		for (String type : compressionTypes) {
			if (type != null && type.equalsIgnoreCase("Lossy")) {
				return type;
			}
		}
		return compressionTypes[0];
	}

	private static boolean esImagenRasterRedimensionable(MediaType mediaType) {
		if (mediaType == null || !mediaType.getType().equalsIgnoreCase("image")) {
			return false;
		}
		String subtype = mediaType.getSubtype() != null ? mediaType.getSubtype().toLowerCase() : "";
		// Evitamos redimensionado runtime para WebP por inestabilidad del decoder nativo en prod (SIGSEGV).
		return !subtype.contains("svg") && !subtype.contains("webp");
	}

	private static int normalizarAnchoImagen(Integer anchoSolicitado) {
		if (anchoSolicitado == null || anchoSolicitado <= 0) {
			return 0;
		}
		if (anchoSolicitado < MIN_ANCHO_IMAGEN) {
			return MIN_ANCHO_IMAGEN;
		}
		return Math.min(anchoSolicitado, MAX_ANCHO_IMAGEN);
	}

	private static String generarEtagImagen(Path rutaArchivo, int ancho, String formato) throws IOException {
		long tamano = Files.size(rutaArchivo);
		long modificado = Files.getLastModifiedTime(rutaArchivo).toMillis();
		String hash = Long.toHexString(tamano) + "-" + Long.toHexString(modificado) + "-" + ancho + "-" + formato;
		return "\"" + hash + "\"";
	}
}
