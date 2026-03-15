package com.taemoi.project.services.impl;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.UUID;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.taemoi.project.config.FileUtils;
import com.taemoi.project.entities.Imagen;
import com.taemoi.project.services.ImagenService;

@Service
public class ImagenServiceImpl implements ImagenService {
	private static final Logger logger = LoggerFactory.getLogger(ImagenServiceImpl.class);

	private static final int MAX_ALUMNO_WIDTH = 1280;
	private static final int MAX_ALUMNO_HEIGHT = 1280;
	private static final float ALUMNO_WEBP_QUALITY = 0.82f;
	private static final int MAX_EVENTO_WIDTH = 1920;
	private static final int MAX_EVENTO_HEIGHT = 1920;
	private static final float EVENTO_WEBP_QUALITY = 0.80f;
	private static final float JPEG_FALLBACK_QUALITY = 0.86f;

	@Value("${app.imagenes.directorio.linux}")
	private String directorioImagenesLinux;

	@Value("${app.imagenes.directorio.windows}")
	private String directorioImagenesWindows;

	@Value("${app.base.url}")
	private String baseUrl;

	@Override
	public Imagen guardarImagen(MultipartFile archivo) throws IOException {
		Path rutaImagenesBase = obtenerRutaImagenesBase();
		Path rutaImagenesAlumnos = rutaImagenesBase.resolve("alumnos");

		if (!Files.exists(rutaImagenesAlumnos)) {
			Files.createDirectories(rutaImagenesAlumnos);
		}

		BufferedImage original = ImageIO.read(archivo.getInputStream());
		if (original == null) {
			throw new IOException("No se pudo leer la imagen del alumno.");
		}

		BufferedImage redimensionada = redimensionarImagen(original, MAX_ALUMNO_WIDTH, MAX_ALUMNO_HEIGHT);
		String nombreBase = obtenerNombreBase(FileUtils.limpiarNombreArchivo(archivo.getOriginalFilename()));
		return guardarImagenProcesadaConFallback(redimensionada, rutaImagenesAlumnos, nombreBase, "alumnos",
				ALUMNO_WEBP_QUALITY);
	}

	@Override
	public Imagen guardarImagenEvento(MultipartFile archivo) throws IOException {
		Path rutaImagenesBase = obtenerRutaImagenesBase();
		Path rutaImagenesEventos = rutaImagenesBase.resolve("eventos");

		if (!Files.exists(rutaImagenesEventos)) {
			Files.createDirectories(rutaImagenesEventos);
		}

		BufferedImage original = ImageIO.read(archivo.getInputStream());
		if (original == null) {
			throw new IOException("No se pudo leer la imagen del evento.");
		}

		BufferedImage redimensionada = redimensionarParaEvento(original);
		String nombreBase = obtenerNombreBase(FileUtils.limpiarNombreArchivo(archivo.getOriginalFilename()));
		return guardarImagenProcesadaConFallback(redimensionada, rutaImagenesEventos, nombreBase, "eventos",
				EVENTO_WEBP_QUALITY);
	}

	@Override
	public void eliminarImagenDeSistema(Imagen imagen) {
		if (imagen == null || imagen.getRuta() == null) {
			throw new IllegalArgumentException("La imagen o su ruta no es valida.");
		}

		Path rutaArchivo = Path.of(imagen.getRuta());
		try {
			if (Files.exists(rutaArchivo)) {
				Files.delete(rutaArchivo);
			}
		} catch (IOException e) {
			throw new RuntimeException("Error al eliminar el archivo de imagen: " + rutaArchivo, e);
		}
	}

	private Path obtenerRutaImagenesBase() {
		String os = System.getProperty("os.name").toLowerCase();
		String directorioImagenes;

		if (os.contains("win")) {
			String userProfile = System.getenv("USERPROFILE");
			directorioImagenes = directorioImagenesWindows.replace("%USERPROFILE%", userProfile);
		} else if (os.contains("nix") || os.contains("nux") || os.contains("mac")) {
			directorioImagenes = directorioImagenesLinux;
		} else {
			throw new IllegalStateException("Sistema operativo no soportado: " + os);
		}

		return Path.of(directorioImagenes);
	}

	private BufferedImage redimensionarParaEvento(BufferedImage original) {
		return redimensionarImagen(original, MAX_EVENTO_WIDTH, MAX_EVENTO_HEIGHT);
	}

	private BufferedImage redimensionarImagen(BufferedImage original, int maxWidth, int maxHeight) {
		int width = Math.max(1, original.getWidth());
		int height = Math.max(1, original.getHeight());

		double scaleWidth = (double) maxWidth / width;
		double scaleHeight = (double) maxHeight / height;
		double scale = Math.min(1.0, Math.min(scaleWidth, scaleHeight));

		int nuevoAncho = Math.max(1, (int) Math.round(width * scale));
		int nuevoAlto = Math.max(1, (int) Math.round(height * scale));

		BufferedImage salida = new BufferedImage(nuevoAncho, nuevoAlto, BufferedImage.TYPE_3BYTE_BGR);
		Graphics2D graphics = salida.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.drawImage(original, 0, 0, nuevoAncho, nuevoAlto, null);
		graphics.dispose();

		return salida;
	}

	private void guardarComoWebp(BufferedImage imagen, Path rutaArchivo, float quality) throws IOException {
		Iterator<ImageWriter> writers = ImageIO.getImageWritersByMIMEType("image/webp");
		if (!writers.hasNext()) {
			writers = ImageIO.getImageWritersBySuffix("webp");
		}
		if (!writers.hasNext()) {
			throw new IOException("No hay codificador WebP disponible.");
		}

		ImageWriter writer = writers.next();
		try (ImageOutputStream outputStream = ImageIO.createImageOutputStream(
				Files.newOutputStream(rutaArchivo, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
			writer.setOutput(outputStream);
			ImageWriteParam param = writer.getDefaultWriteParam();
			if (param.canWriteCompressed()) {
				configurarCompresionWebp(param, quality);
			}
			writer.write(null, new IIOImage(imagen, null, null), param);
		} finally {
			writer.dispose();
		}
	}

	private void guardarComoJpeg(BufferedImage imagen, Path rutaArchivo, float quality) throws IOException {
		Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
		if (!writers.hasNext()) {
			writers = ImageIO.getImageWritersByMIMEType("image/jpeg");
		}
		if (!writers.hasNext()) {
			throw new IOException("No hay codificador JPEG disponible.");
		}

		ImageWriter writer = writers.next();
		try (ImageOutputStream outputStream = ImageIO.createImageOutputStream(
				Files.newOutputStream(rutaArchivo, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
			writer.setOutput(outputStream);
			ImageWriteParam param = writer.getDefaultWriteParam();
			if (param.canWriteCompressed()) {
				param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
				float qualitySegura = Math.max(0.1f, Math.min(1.0f, quality));
				param.setCompressionQuality(qualitySegura);
			}
			writer.write(null, new IIOImage(imagen, null, null), param);
		} finally {
			writer.dispose();
		}
	}

	private Imagen guardarImagenProcesadaConFallback(BufferedImage imagen, Path directorioDestino, String nombreBase,
			String carpetaPublica, float webpQuality) throws IOException {
		String nombreBaseArchivo = UUID.randomUUID() + "_" + nombreBase;
		String nombreWebp = nombreBaseArchivo + ".webp";
		Path rutaWebp = directorioDestino.resolve(nombreWebp);
		try {
			guardarComoWebp(imagen, rutaWebp, webpQuality);
			String urlAcceso = baseUrl + "/imagenes/" + carpetaPublica + "/" + nombreWebp;
			return new Imagen(nombreWebp, "image/webp", urlAcceso, rutaWebp.toString());
		} catch (Exception e) {
			logger.warn("Fallo al guardar imagen en WebP, se usara fallback JPEG. Carpeta: {}, nombreBase: {}. Causa: {}",
					carpetaPublica, nombreBase, e.getMessage());
			String nombreJpeg = nombreBaseArchivo + ".jpg";
			Path rutaJpeg = directorioDestino.resolve(nombreJpeg);
			guardarComoJpeg(imagen, rutaJpeg, JPEG_FALLBACK_QUALITY);
			String urlAcceso = baseUrl + "/imagenes/" + carpetaPublica + "/" + nombreJpeg;
			return new Imagen(nombreJpeg, "image/jpeg", urlAcceso, rutaJpeg.toString());
		}
	}

	private void configurarCompresionWebp(ImageWriteParam param, float quality) throws IOException {
		param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);

		boolean tipoCompresionEstablecido = false;
		String[] compressionTypes = param.getCompressionTypes();
		if (compressionTypes != null && compressionTypes.length > 0) {
			String preferido = seleccionarTipoCompresion(compressionTypes);
			tipoCompresionEstablecido = intentarSetCompressionType(param, preferido);

			if (!tipoCompresionEstablecido) {
				for (String type : compressionTypes) {
					if (intentarSetCompressionType(param, type)) {
						tipoCompresionEstablecido = true;
						break;
					}
				}
			}
		}

		if (!tipoCompresionEstablecido) {
			tipoCompresionEstablecido = intentarSetCompressionType(param, "Lossy")
					|| intentarSetCompressionType(param, "lossy");
		}

		try {
			param.setCompressionQuality(quality);
		} catch (RuntimeException e) {
			throw new IOException("No se pudo configurar la compresion WebP.", e);
		}
	}

	private boolean intentarSetCompressionType(ImageWriteParam param, String type) {
		if (type == null || type.isBlank()) {
			return false;
		}
		try {
			param.setCompressionType(type);
			return true;
		} catch (RuntimeException e) {
			return false;
		}
	}

	private String seleccionarTipoCompresion(String[] compressionTypes) {
		for (String type : compressionTypes) {
			if (type != null && type.equalsIgnoreCase("Lossy")) {
				return type;
			}
		}
		return compressionTypes[0];
	}

	private String obtenerNombreBase(String nombreArchivo) {
		if (nombreArchivo == null || nombreArchivo.isBlank()) {
			return "evento";
		}
		int ultimoPunto = nombreArchivo.lastIndexOf('.');
		String base = ultimoPunto > 0 ? nombreArchivo.substring(0, ultimoPunto) : nombreArchivo;
		base = base.replaceAll("[^a-zA-Z0-9_-]+", "_").replaceAll("_+", "_");
		if (base.isBlank()) {
			return "evento";
		}
		return base;
	}
}
