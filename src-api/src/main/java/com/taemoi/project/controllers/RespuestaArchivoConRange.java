package com.taemoi.project.controllers;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
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
 *
 * <p>El fichero se abre una sola vez y se mide sobre ese descriptor, no con el tamaño de {@link MaterialExamenArchivo}: una
 * sustitución simultánea es un movimiento atómico, así que quien ya abrió sigue con el contenido viejo y su tamaño viejo.
 */
final class RespuestaArchivoConRange {

	private RespuestaArchivoConRange() {
	}

	static ResponseEntity<Resource> servir(MaterialExamenArchivo archivo, HttpHeaders requestHeaders, boolean download)
			throws IOException {
		MediaType mediaType = tipoSeguro(archivo.getMimeType());
		List<HttpRange> ranges = requestHeaders.getRange();
		String disposition = dispositionSegura(download, archivo.getFileName());

		SeekableByteChannel canal = Files.newByteChannel(archivo.getPath(), StandardOpenOption.READ);
		try {
			ResponseEntity<Resource> respuesta = responder(canal, mediaType, ranges, disposition);
			if (respuesta.getBody() == null) {
				canal.close();
			}
			return respuesta;
		} catch (IOException | RuntimeException e) {
			canal.close();
			throw e;
		}
	}

	private static ResponseEntity<Resource> responder(SeekableByteChannel canal, MediaType mediaType, List<HttpRange> ranges,
			String disposition) throws IOException {
		long fileSize = canal.size();

		if (ranges == null || ranges.isEmpty()) {
			InputStreamResource fullResource = new InputStreamResource(Channels.newInputStream(canal));
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
		canal.position(start);
		InputStreamResource partialResource = new InputStreamResource(
				new LimitedInputStream(Channels.newInputStream(canal), contentLength));

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
