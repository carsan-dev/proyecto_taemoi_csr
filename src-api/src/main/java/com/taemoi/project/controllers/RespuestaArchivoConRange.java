package com.taemoi.project.controllers;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.taemoi.project.services.MaterialExamenService.MaterialExamenArchivo;

/**
 * Respuesta de un fichero de disco con soporte de {@code Range}: la comparten las rutas de material de las familias
 * ({@link AlumnoController}) y las del panel ({@link MaterialExamenController}).
 */
final class RespuestaArchivoConRange {

	private RespuestaArchivoConRange() {
	}

	static ResponseEntity<Resource> servir(MaterialExamenArchivo archivo, HttpHeaders requestHeaders, boolean download)
			throws IOException {
		long fileSize = archivo.getSize();
		MediaType mediaType = tipoSeguro(archivo.getMimeType());
		List<HttpRange> ranges = requestHeaders.getRange();
		String disposition = dispositionSegura(download, archivo.getFileName());

		if (ranges == null || ranges.isEmpty()) {
			InputStreamResource fullResource = new InputStreamResource(Files.newInputStream(archivo.getPath()));
			return ResponseEntity.ok()
					.contentType(mediaType)
					.contentLength(fileSize)
					.header(HttpHeaders.ACCEPT_RANGES, "bytes")
					.header(HttpHeaders.CONTENT_DISPOSITION, disposition)
					.body(fullResource);
		}

		HttpRange range = ranges.get(0);
		long start = range.getRangeStart(fileSize);
		long end = range.getRangeEnd(fileSize);

		if (start >= fileSize || end >= fileSize || start > end) {
			return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
					.header(HttpHeaders.CONTENT_RANGE, "bytes */" + fileSize)
					.build();
		}

		long contentLength = end - start + 1;
		InputStream source = Files.newInputStream(archivo.getPath());
		skipFully(source, start);
		InputStreamResource partialResource = new InputStreamResource(new LimitedInputStream(source, contentLength));

		return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
				.contentType(mediaType)
				.contentLength(contentLength)
				.header(HttpHeaders.ACCEPT_RANGES, "bytes")
				.header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + fileSize)
				.header(HttpHeaders.CONTENT_DISPOSITION, disposition)
				.body(partialResource);
	}

	static String dispositionSegura(boolean download, String fileName) {
		String safeFileName = (fileName == null || fileName.isBlank()) ? "archivo" : fileName;
		return ContentDisposition.builder(download ? "attachment" : "inline")
				.filename(safeFileName, StandardCharsets.UTF_8)
				.build()
				.toString();
	}

	static MediaType tipoSeguro(String mediaTypeRaw) {
		if (mediaTypeRaw == null || mediaTypeRaw.isBlank()) {
			return MediaType.APPLICATION_OCTET_STREAM;
		}
		try {
			return MediaType.parseMediaType(mediaTypeRaw);
		} catch (Exception e) {
			return MediaType.APPLICATION_OCTET_STREAM;
		}
	}

	private static void skipFully(InputStream source, long bytesToSkip) throws IOException {
		long totalSkipped = 0;
		while (totalSkipped < bytesToSkip) {
			long skipped = source.skip(bytesToSkip - totalSkipped);
			if (skipped <= 0) {
				int read = source.read();
				if (read == -1) {
					throw new IOException("No se pudo posicionar el stream en el rango solicitado");
				}
				skipped = 1;
			}
			totalSkipped += skipped;
		}
	}

	private static final class LimitedInputStream extends FilterInputStream {
		private long remaining;

		private LimitedInputStream(InputStream in, long maxBytes) {
			super(in);
			this.remaining = Math.max(0, maxBytes);
		}

		@Override
		public int read() throws IOException {
			if (remaining <= 0) {
				return -1;
			}
			int read = super.read();
			if (read >= 0) {
				remaining--;
			}
			return read;
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			if (remaining <= 0) {
				return -1;
			}
			int bytesToRead = (int) Math.min(len, remaining);
			int read = super.read(b, off, bytesToRead);
			if (read > 0) {
				remaining -= read;
			}
			return read;
		}
	}
}
