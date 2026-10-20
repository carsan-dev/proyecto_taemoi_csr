package com.taemoi.project.utils;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.http.MediaType;

import com.taemoi.project.exceptions.ArchivoNoValidoException;

public final class DocumentoSecurityUtils {

	private static final Map<String, String> EXTENSION_TO_MIME = Map.ofEntries(
			Map.entry("pdf", "application/pdf"),
			Map.entry("txt", "text/plain"),
			Map.entry("csv", "text/csv"),
			Map.entry("jpg", "image/jpeg"),
			Map.entry("jpeg", "image/jpeg"),
			Map.entry("png", "image/png"),
			Map.entry("webp", "image/webp"),
			Map.entry("gif", "image/gif"),
			Map.entry("doc", "application/msword"),
			Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
			Map.entry("xls", "application/vnd.ms-excel"),
			Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

	private static final Map<String, Set<String>> EXTENSION_TO_ALLOWED_MIME_ALIASES = Map.ofEntries(
			Map.entry("pdf", Set.of("application/pdf")),
			Map.entry("txt", Set.of("text/plain")),
			Map.entry("csv", Set.of("text/csv", "application/csv", "application/vnd.ms-excel")),
			Map.entry("jpg", Set.of("image/jpeg", "image/pjpeg")),
			Map.entry("jpeg", Set.of("image/jpeg", "image/pjpeg")),
			Map.entry("png", Set.of("image/png")),
			Map.entry("webp", Set.of("image/webp")),
			Map.entry("gif", Set.of("image/gif")),
			Map.entry("doc", Set.of("application/msword", "application/vnd.ms-word", "application/x-msword")),
			Map.entry("docx", Set.of(
					"application/vnd.openxmlformats-officedocument.wordprocessingml.document",
					"application/zip",
					"application/x-zip-compressed")),
			Map.entry("xls", Set.of("application/vnd.ms-excel", "application/xls", "application/x-excel")),
			Map.entry("xlsx", Set.of(
					"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
					"application/zip",
					"application/x-zip-compressed")));

	private static final Set<String> PREVIEWABLE_MIME_TYPES = Set.of(
			"application/pdf",
			"text/plain",
			"text/csv",
			"image/jpeg",
			"image/png",
			"image/webp",
			"image/gif");

	private static final Set<String> HEIC_EXTENSIONS = Set.of("heic", "heif");

	private static final Set<String> HEIC_MIME_TYPES = Set.of(
			"image/heic",
			"image/heif",
			"image/heic-sequence",
			"image/heif-sequence");

	private static final Pattern EXTENSION_LOG = Pattern.compile("[a-z0-9]{1,10}");

	private static final Pattern MIME_LOG = Pattern.compile("[a-z0-9][a-z0-9.+-]{0,63}/[a-z0-9][a-z0-9.+-]{0,63}");

	public static final String MENSAJE_HEIC = "Convierte la foto a JPG o desactiva HEIF en la cámara.";

	private DocumentoSecurityUtils() {
	}

	/**
	 * Decisión 227: las fotos HEIC/HEIF (por defecto en muchas cámaras de Android e iPhone) se RECHAZAN, no se
	 * convierten. Vale para documentos y para las rutas de imagen (foto de alumno, imagen de evento y logo).
	 *
	 * @throws ArchivoNoValidoException con {@link #MENSAJE_HEIC} si la extensión o el tipo declarado son HEIC/HEIF
	 */
	public static void rechazarHeic(String fileName, String providedContentType) {
		String extension = extensionSinValidar(fileName);
		String mimeType = normalizarMime(providedContentType);
		if ((extension != null && HEIC_EXTENSIONS.contains(extension))
				|| (mimeType != null && HEIC_MIME_TYPES.contains(mimeType))) {
			throw new ArchivoNoValidoException(MENSAJE_HEIC);
		}
	}

	/**
	 * Extensión del fichero apta para el log: nunca el nombre. {@code -} si no tiene; {@code ?} si no es una
	 * extensión corta y alfanumérica.
	 */
	public static String extensionParaLog(String fileName) {
		String extension = extensionSinValidar(fileName);
		if (extension == null) {
			return "-";
		}
		return EXTENSION_LOG.matcher(extension).matches() ? extension : "?";
	}

	/** Tipo declarado apto para el log (sin parámetros): {@code -} si no viene; {@code ?} si no es un tipo MIME. */
	public static String contentTypeParaLog(String contentType) {
		String normalizado = normalizarMime(contentType);
		if (normalizado == null) {
			return "-";
		}
		return MIME_LOG.matcher(normalizado).matches() ? normalizado : "?";
	}

	public static String detectarMimePermitido(String fileName, String providedContentType) {
		rechazarHeic(fileName, providedContentType);
		String extension = obtenerExtension(fileName);
		String canonicalMimeType = EXTENSION_TO_MIME.get(extension);
		Set<String> allowedMimeAliases = EXTENSION_TO_ALLOWED_MIME_ALIASES.get(extension);
		if (canonicalMimeType == null) {
			throw new ArchivoNoValidoException("Tipo de documento no permitido.");
		}

		String normalizedProvidedContentType = normalizarMime(providedContentType);
		if (normalizedProvidedContentType == null || "application/octet-stream".equals(normalizedProvidedContentType)) {
			return canonicalMimeType;
		}

		if (canonicalMimeType.equals(normalizedProvidedContentType)) {
			return canonicalMimeType;
		}

		if (allowedMimeAliases != null && allowedMimeAliases.contains(normalizedProvidedContentType)) {
			return canonicalMimeType;
		}

		throw new ArchivoNoValidoException("Tipo de documento no permitido.");
	}

	public static MediaType resolverMediaTypeRespuesta(String mimeType, boolean forceDownload) {
		if (forceDownload || !esMimePrevisualizable(mimeType)) {
			return MediaType.APPLICATION_OCTET_STREAM;
		}

		try {
			return MediaType.parseMediaType(normalizarMime(mimeType));
		} catch (Exception ignored) {
			return MediaType.APPLICATION_OCTET_STREAM;
		}
	}

	public static String resolverDispositionType(String mimeType, boolean forceDownload) {
		return forceDownload || !esMimePrevisualizable(mimeType) ? "attachment" : "inline";
	}

	public static boolean esMimePrevisualizable(String mimeType) {
		String normalizedMimeType = normalizarMime(mimeType);
		return normalizedMimeType != null && PREVIEWABLE_MIME_TYPES.contains(normalizedMimeType);
	}

	private static String obtenerExtension(String fileName) {
		if (fileName == null || fileName.isBlank()) {
			throw new ArchivoNoValidoException("El nombre del archivo es obligatorio.");
		}

		int lastDot = fileName.lastIndexOf('.');
		if (lastDot < 0 || lastDot == fileName.length() - 1) {
			throw new ArchivoNoValidoException("El documento debe incluir una extension permitida.");
		}

		return fileName.substring(lastDot + 1).toLowerCase(Locale.ROOT);
	}

	/** Extensión en minúsculas, o {@code null} si el nombre no tiene; sin lanzar (para HEIC y para el log). */
	private static String extensionSinValidar(String fileName) {
		if (fileName == null) {
			return null;
		}
		int lastDot = fileName.lastIndexOf('.');
		if (lastDot < 0 || lastDot == fileName.length() - 1) {
			return null;
		}
		return fileName.substring(lastDot + 1).toLowerCase(Locale.ROOT);
	}

	private static String normalizarMime(String mimeType) {
		if (mimeType == null || mimeType.isBlank()) {
			return null;
		}
		return mimeType.split(";")[0].trim().toLowerCase(Locale.ROOT);
	}
}
