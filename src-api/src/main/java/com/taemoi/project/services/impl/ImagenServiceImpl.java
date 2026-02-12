package com.taemoi.project.services.impl;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.UUID;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.taemoi.project.config.FileUtils;
import com.taemoi.project.entities.Imagen;
import com.taemoi.project.services.ImagenService;

@Service
public class ImagenServiceImpl implements ImagenService {

	private static final int MAX_ALUMNO_WIDTH = 1280;
	private static final int MAX_ALUMNO_HEIGHT = 1280;
	private static final float ALUMNO_WEBP_QUALITY = 0.82f;
	private static final int MAX_EVENTO_WIDTH = 1920;
	private static final int MAX_EVENTO_HEIGHT = 1920;
	private static final float EVENTO_WEBP_QUALITY = 0.80f;

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
		String nombreArchivoFinal = UUID.randomUUID() + "_" + nombreBase + ".webp";
		Path rutaArchivo = rutaImagenesAlumnos.resolve(nombreArchivoFinal);

		guardarComoWebp(redimensionada, rutaArchivo, ALUMNO_WEBP_QUALITY);

		String urlAcceso = baseUrl + "/imagenes/alumnos/" + nombreArchivoFinal;
		return new Imagen(nombreArchivoFinal, "image/webp", urlAcceso, rutaArchivo.toString());
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
		String nombreArchivoFinal = UUID.randomUUID() + "_" + nombreBase + ".webp";
		Path rutaArchivo = rutaImagenesEventos.resolve(nombreArchivoFinal);

		guardarComoWebp(redimensionada, rutaArchivo, EVENTO_WEBP_QUALITY);

		String urlAcceso = baseUrl + "/imagenes/eventos/" + nombreArchivoFinal;
		return new Imagen(nombreArchivoFinal, "image/webp", urlAcceso, rutaArchivo.toString());
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
				param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
				String[] compressionTypes = param.getCompressionTypes();
				if (compressionTypes != null && compressionTypes.length > 0) {
					param.setCompressionType(seleccionarTipoCompresion(compressionTypes));
				}
				param.setCompressionQuality(quality);
			}
			writer.write(null, new IIOImage(imagen, null, null), param);
		} finally {
			writer.dispose();
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
