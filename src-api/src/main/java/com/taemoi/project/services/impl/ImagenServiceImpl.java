package com.taemoi.project.services.impl;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.UUID;

import javax.imageio.IIOException;
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

import com.taemoi.project.archivos.RutasArchivos;
import com.taemoi.project.config.FileUtils;
import com.taemoi.project.entities.Imagen;
import com.taemoi.project.exceptions.ArchivoNoValidoException;
import com.taemoi.project.services.ImagenService;
import com.taemoi.project.utils.DocumentoSecurityUtils;

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
	private static final String NOMBRE_BASE_FOTO_EVENTO = "foto";

	@Value("${app.imagenes.directorio.linux}")
	private String directorioImagenesLinux;

	@Value("${app.imagenes.directorio.windows}")
	private String directorioImagenesWindows;

	@Override
	public Imagen guardarImagen(MultipartFile archivo) throws IOException {
		DocumentoSecurityUtils.rechazarHeic(archivo.getOriginalFilename(), archivo.getContentType());
		String carpetaRelativa = carpetaDeEscuela(RutasArchivos.CARPETA_ALUMNOS);
		Path rutaImagenesAlumnos = RutasArchivos.resolverDentroDe(obtenerRutaImagenesBase(), carpetaRelativa);

		if (!Files.exists(rutaImagenesAlumnos)) {
			Files.createDirectories(rutaImagenesAlumnos);
		}

		BufferedImage original = leerImagen(archivo, "No se pudo leer la imagen del alumno.");

		BufferedImage redimensionada = redimensionarImagen(original, MAX_ALUMNO_WIDTH, MAX_ALUMNO_HEIGHT);
		String nombreBase = obtenerNombreBase(FileUtils.limpiarNombreArchivo(archivo.getOriginalFilename()));
		return guardarImagenProcesadaConFallback(redimensionada, rutaImagenesAlumnos, nombreBase, carpetaRelativa,
				ALUMNO_WEBP_QUALITY);
	}

	@Override
	public Imagen guardarImagenEvento(MultipartFile archivo) throws IOException {
		return guardarDeEvento(archivo, RutasArchivos.CARPETA_EVENTOS, "No se pudo leer la imagen del evento.", null);
	}

	@Override
	public Imagen guardarImagenFotoEvento(MultipartFile archivo) throws IOException {
		return guardarDeEvento(archivo, RutasArchivos.CARPETA_FOTOS_EVENTOS, "No se pudo leer la foto del evento.",
				NOMBRE_BASE_FOTO_EVENTO);
	}

	/** @param nombreFijo nombre base del fichero; {@code null} para derivarlo del nombre subido. */
	private Imagen guardarDeEvento(MultipartFile archivo, String categoria, String mensajeIlegible, String nombreFijo)
			throws IOException {
		DocumentoSecurityUtils.rechazarHeic(archivo.getOriginalFilename(), archivo.getContentType());
		String carpetaRelativa = carpetaDeEscuela(categoria);
		Path rutaImagenesEventos = RutasArchivos.resolverDentroDe(obtenerRutaImagenesBase(), carpetaRelativa);

		if (!Files.exists(rutaImagenesEventos)) {
			Files.createDirectories(rutaImagenesEventos);
		}

		BufferedImage original = leerImagen(archivo, mensajeIlegible);

		BufferedImage redimensionada = redimensionarParaEvento(original);
		String nombreBase = nombreFijo != null ? nombreFijo
				: obtenerNombreBase(FileUtils.limpiarNombreArchivo(archivo.getOriginalFilename()));
		return guardarImagenProcesadaConFallback(redimensionada, rutaImagenesEventos, nombreBase, carpetaRelativa,
				EVENTO_WEBP_QUALITY);
	}

	@Override
	public Imagen guardarLogoEscuela(Long organizacionId, byte[] png) throws IOException {
		if (png == null || png.length == 0) {
			throw new IllegalArgumentException("El logo está vacío.");
		}
		String carpetaRelativa = RutasArchivos.carpetaOrganizacion(organizacionId) + "/" + RutasArchivos.CARPETA_LOGO;
		Path directorio = RutasArchivos.resolverDentroDe(obtenerRutaImagenesBase(), carpetaRelativa);
		Files.createDirectories(directorio);
		String nombre = UUID.randomUUID() + "_logo.png";
		Path fichero = RutasArchivos.resolverDentroDe(directorio, nombre);
		Files.write(fichero, png, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
		return crearImagen(nombre, "image/png", carpetaRelativa);
	}

	@Override
	public void eliminarImagenDeSistema(Imagen imagen) {
		if (imagen == null || imagen.getRuta() == null) {
			throw new IllegalArgumentException("La imagen o su ruta no es valida.");
		}

		Path rutaArchivo = resolverRutaArchivo(imagen);
		if (rutaArchivo == null) {
			throw new IllegalArgumentException("La ruta de la imagen no es valida.");
		}
		try {
			if (Files.exists(rutaArchivo)) {
				Files.delete(rutaArchivo);
			}
		} catch (IOException e) {
			throw new RuntimeException("Error al eliminar el archivo de imagen: " + rutaArchivo, e);
		}
	}

	@Override
	public Path resolverRutaArchivo(Imagen imagen) {
		if (imagen == null || imagen.getRuta() == null || imagen.getRuta().isBlank()) {
			return null;
		}
		String ruta = imagen.getRuta().trim();
		try {
			if (RutasArchivos.esRutaAbsoluta(ruta)) {
				// Fila heredada: ruta absoluta guardada por versiones anteriores.
				return Path.of(ruta).toAbsolutePath().normalize();
			}
			return RutasArchivos.resolverDentroDe(obtenerRutaImagenesBase(), ruta);
		} catch (RuntimeException e) {
			logger.warn("Ruta de imagen no valida (id {}): {}", imagen.getId(), e.getMessage());
			return null;
		}
	}

	/** {@code org-<id>/<categoria>} de la escuela activa; falla cerrado si no hay ninguna. */
	private static String carpetaDeEscuela(String categoria) {
		return RutasArchivos.carpetaOrganizacion(RutasArchivos.organizacionActiva()) + "/" + categoria;
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

		return Path.of(directorioImagenes).toAbsolutePath().normalize();
	}

	/**
	 * Decodifica la imagen subida. Si el contenido no es una imagen legible (otro tipo de fichero, imagen dañada,
	 * truncada o formato sin lector), es un fallo de validación: {@link ArchivoNoValidoException} (400). Un fallo de
	 * E/S del servidor (no se puede leer el temporal, ni crear la caché de ImageIO) sigue siendo {@link IOException}.
	 */
	private BufferedImage leerImagen(MultipartFile archivo, String mensajeIlegible) throws IOException {
		BufferedImage original;
		try {
			original = decodificar(archivo.getInputStream());
		} catch (IIOException e) {
			if (esErrorDeEntradaSalidaDelServidor(e)) {
				throw e;
			}
			throw new ArchivoNoValidoException(mensajeIlegible);
		}
		if (original == null) {
			throw new ArchivoNoValidoException(mensajeIlegible);
		}
		return original;
	}

	/**
	 * ImageIO envuelve en {@link IIOException} tanto el contenido dañado (sin causa, o con otra {@code IIOException}
	 * o un {@link EOFException} si el fichero está truncado) como los fallos de E/S de verdad (causa
	 * {@link IOException} de otro tipo, p. ej. al crear su fichero de caché).
	 */
	private static boolean esErrorDeEntradaSalidaDelServidor(IIOException e) {
		Throwable causa = e.getCause();
		return causa instanceof IOException && !(causa instanceof IIOException) && !(causa instanceof EOFException);
	}

	/** Decodificación con ImageIO; separada para poder simular sus fallos en los tests. */
	protected BufferedImage decodificar(InputStream entrada) throws IOException {
		return ImageIO.read(entrada);
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

	private Path guardarComoWebp(BufferedImage imagen, Path directorioDestino, String nombreArchivo, float quality)
			throws IOException {
		Path directorioSeguro = directorioDestino.toAbsolutePath().normalize();
		Path rutaArchivo = directorioSeguro.resolve(nombreArchivo).normalize().toAbsolutePath();
		if (!rutaArchivo.startsWith(directorioSeguro) || !directorioSeguro.equals(rutaArchivo.getParent())) {
			throw new IllegalArgumentException("Ruta de imagen fuera del directorio permitido.");
		}

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
		return rutaArchivo;
	}

	private Path guardarComoJpeg(BufferedImage imagen, Path directorioDestino, String nombreArchivo, float quality)
			throws IOException {
		Path directorioSeguro = directorioDestino.toAbsolutePath().normalize();
		Path rutaArchivo = directorioSeguro.resolve(nombreArchivo).normalize().toAbsolutePath();
		if (!rutaArchivo.startsWith(directorioSeguro) || !directorioSeguro.equals(rutaArchivo.getParent())) {
			throw new IllegalArgumentException("Ruta de imagen fuera del directorio permitido.");
		}

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
		return rutaArchivo;
	}

	private Imagen guardarImagenProcesadaConFallback(BufferedImage imagen, Path directorioDestino, String nombreBase,
			String carpetaPublica, float webpQuality) throws IOException {
		String nombreBaseArchivo = UUID.randomUUID() + "_" + nombreBase;
		String nombreWebp = nombreBaseArchivo + ".webp";
		try {
			guardarComoWebp(imagen, directorioDestino, nombreWebp, webpQuality);
			return crearImagen(nombreWebp, "image/webp", carpetaPublica);
		} catch (Exception e) {
			logger.warn("Fallo al guardar imagen en WebP, se usara fallback JPEG. Carpeta: {}, nombreBase: {}. Causa: {}",
					carpetaPublica, nombreBase, e.getMessage());
			String nombreJpeg = nombreBaseArchivo + ".jpg";
			guardarComoJpeg(imagen, directorioDestino, nombreJpeg, JPEG_FALLBACK_QUALITY);
			return crearImagen(nombreJpeg, "image/jpeg", carpetaPublica);
		}
	}

	/**
	 * Solo se guarda la ruta RELATIVA a la raíz de imágenes ({@code org-<id>/<categoria>/<fichero>}) y una
	 * URL sin host ({@code /imagenes/…}); la URL absoluta se compone al leer con {@code app.base.url}.
	 */
	private static Imagen crearImagen(String nombreArchivo, String tipo, String carpetaRelativa) {
		String rutaRelativa = carpetaRelativa + "/" + nombreArchivo;
		return new Imagen(nombreArchivo, tipo, "/imagenes/" + rutaRelativa, rutaRelativa);
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
