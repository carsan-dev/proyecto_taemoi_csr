package com.taemoi.project.utils;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.http.MediaType;

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

	private DocumentoSecurityUtils() {
	}

	public static String detectarMimePermitido(String fileName, String providedContentType) {
		String extension = obtenerExtension(fileName);
		String canonicalMimeType = EXTENSION_TO_MIME.get(extension);
		Set<String> allowedMimeAliases = EXTENSION_TO_ALLOWED_MIME_ALIASES.get(extension);
		if (canonicalMimeType == null) {
			throw new IllegalArgumentException("Tipo de documento no permitido.");
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

		throw new IllegalArgumentException("Tipo de documento no permitido.");
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
			throw new IllegalArgumentException("El nombre del archivo es obligatorio.");
		}

		int lastDot = fileName.lastIndexOf('.');
		if (lastDot < 0 || lastDot == fileName.length() - 1) {
			throw new IllegalArgumentException("El documento debe incluir una extension permitida.");
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
