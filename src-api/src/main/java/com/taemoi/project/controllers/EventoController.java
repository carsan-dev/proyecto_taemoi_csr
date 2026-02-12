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
import java.util.List;
import java.util.Map;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.entities.Documento;
import com.taemoi.project.entities.Evento;
import com.taemoi.project.entities.Imagen;
import com.taemoi.project.entities.Roles;
import com.taemoi.project.exceptions.evento.EventoNoEncontradoException;
import com.taemoi.project.exceptions.turno.TurnoNoEncontradoException;
import com.taemoi.project.services.DocumentoService;
import com.taemoi.project.services.EventoService;
import com.taemoi.project.services.ImagenService;

@RestController
@RequestMapping("/api/eventos")
public class EventoController {

	private static final int MIN_ANCHO_IMAGEN = 160;
	private static final int MAX_ANCHO_IMAGEN = 2200;
	private static final float CALIDAD_WEBP_REDIMENSION = 0.78f;
	private static final long MAX_TAMANO_ORIGEN_RESIZE_BYTES = 8L * 1024 * 1024;
	private static final long MAX_PIXELES_ORIGEN_RESIZE = 24_000_000L;

	@Value("${app.base.url}")
	private String baseUrl;

	@Autowired
	private EventoService eventoService;

	@Autowired
	private ImagenService imagenService;

	@Autowired
	private DocumentoService documentoService;

	@GetMapping
	public List<Evento> obtenerEventosVisibles() {
		List<Evento> eventos = eventoService.obtenerEventosVisibles();
		aplicarUrlImagenPublica(eventos);
		return eventos;
	}

	@GetMapping("/admin/todos")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public List<Evento> obtenerTodosLosEventos() {
		List<Evento> eventos = eventoService.obtenerTodosLosEventos();
		aplicarUrlImagenPublica(eventos);
		return eventos;
	}

	@GetMapping("/{eventoId}")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<Evento> obtenerEventoPorId(@PathVariable @NonNull Long eventoId) {
		try {
			Evento evento = eventoService.obtenerEventoPorId(eventoId);
			aplicarUrlImagenPublica(evento);
			return ResponseEntity.ok(evento);
		} catch (TurnoNoEncontradoException e) {
			return ResponseEntity.notFound().build();
		}
	}

	@PostMapping(value = "/crear", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> crearEvento(@RequestParam("nuevo") String eventoJson,
			@RequestParam(required = false) MultipartFile file) {
		try {
			ObjectMapper objectMapper = new ObjectMapper();
			Evento nuevoEvento = objectMapper.readValue(eventoJson, Evento.class);

			// Guardar el evento y la imagen (si existe)
			Evento eventoCreado = eventoService.guardarEvento(nuevoEvento, file);
			aplicarUrlImagenPublica(eventoCreado);
			return new ResponseEntity<>(eventoCreado, HttpStatus.CREATED);

		} catch (IOException e) {
			return new ResponseEntity<>("Error al procesar la solicitud", HttpStatus.BAD_REQUEST);
		}
	}

	@PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> actualizarEvento(@PathVariable @NonNull Long id,
			@RequestParam(required = false) MultipartFile file,
			@RequestParam("eventoEditado") String eventoJson) {
		try {
			ObjectMapper objectMapper = new ObjectMapper();
			Evento eventoActualizado = objectMapper.readValue(eventoJson, Evento.class);

			Imagen nuevaImagen = null;

			// Si se proporciona una nueva imagen, guardar la imagen y eliminar la anterior
			if (file != null && !file.isEmpty()) {
				nuevaImagen = imagenService.guardarImagenEvento(file); // Guardar la nueva imagen
			}

			// Actualizar el evento en la base de datos, incluyendo la nueva imagen
			Evento evento = eventoService.actualizarEvento(id, eventoActualizado, nuevaImagen);
			aplicarUrlImagenPublica(evento);
			return new ResponseEntity<>(evento, HttpStatus.OK);

		} catch (IOException e) {
			return new ResponseEntity<>("Error al procesar la solicitud", HttpStatus.BAD_REQUEST);
		}
	}

	@GetMapping("/{eventoId}/imagen")
	public ResponseEntity<Resource> obtenerImagenEvento(
			@PathVariable @NonNull Long eventoId,
			@RequestParam(name = "w", required = false) Integer anchoSolicitado) {
		try {
			Evento evento = eventoService.obtenerEventoPorId(eventoId);
			Imagen imagen = evento.getFotoEvento();

			if (imagen == null || imagen.getRuta() == null) {
				return ResponseEntity.notFound().build();
			}

			Path rutaArchivo = Path.of(imagen.getRuta());
			Resource recurso = new UrlResource(rutaArchivo.toUri());

			if (!recurso.exists()) {
				return ResponseEntity.notFound().build();
			}

			MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
			if (imagen.getTipo() != null) {
				try {
					mediaType = MediaType.parseMediaType(imagen.getTipo());
				} catch (Exception e) {
					mediaType = MediaType.APPLICATION_OCTET_STREAM;
				}
			}

			int anchoNormalizado = normalizarAnchoImagen(anchoSolicitado);
			if (anchoNormalizado > 0) {
				ResponseEntity<Resource> respuestaRedimensionada =
						intentarConstruirRespuestaRedimensionada(rutaArchivo, mediaType, imagen.getNombre(), anchoNormalizado);
				if (respuestaRedimensionada != null) {
					return respuestaRedimensionada;
				}
			}

			return construirRespuestaImagenOriginal(recurso, mediaType, imagen.getNombre(), rutaArchivo, anchoNormalizado);
		} catch (EventoNoEncontradoException e) {
			return ResponseEntity.notFound().build();
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}
	}

	@DeleteMapping("/{id}/imagen")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> eliminarImagenEvento(@PathVariable @NonNull Long id) {
		try {
			eventoService.eliminarImagenEvento(id);
			return ResponseEntity.ok().build();
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body("Error al eliminar la imagen del evento.");
		}
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> eliminarEvento(@PathVariable @NonNull Long id) {
		try {
			eventoService.eliminarEvento(id);
			return ResponseEntity.ok().build();
		} catch (EventoNoEncontradoException e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "El evento no fue encontrado."));
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(Map.of("error", "Error al eliminar el evento: " + e.getMessage()));
		}
	}

	@PutMapping("/{id}/toggle-visibilidad")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> toggleVisibilidad(@PathVariable @NonNull Long id) {
		try {
			eventoService.toggleVisibilidad(id);
			return ResponseEntity.ok().build();
		} catch (EventoNoEncontradoException e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "El evento no fue encontrado."));
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(Map.of("error", "Error al cambiar la visibilidad del evento: " + e.getMessage()));
		}
	}

	@PutMapping("/orden")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> actualizarOrdenEventos(@RequestBody List<Long> ordenIds) {
		try {
			eventoService.actualizarOrdenEventos(ordenIds);
			return ResponseEntity.ok().build();
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
		} catch (EventoNoEncontradoException e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(Map.of("error", "Error al actualizar el orden de eventos: " + e.getMessage()));
		}
	}

	// ==================== ENDPOINTS DE DOCUMENTOS ====================

	@GetMapping("/{eventoId}/documentos")
	public ResponseEntity<List<Documento>> obtenerDocumentosDelEvento(@PathVariable @NonNull Long eventoId) {
		try {
			List<Documento> documentos = eventoService.obtenerDocumentosEvento(eventoId);
			return ResponseEntity.ok(documentos);
		} catch (EventoNoEncontradoException e) {
			return ResponseEntity.notFound().build();
		}
	}

	@GetMapping("/{eventoId}/documentos/{documentoId}/descargar")
	public ResponseEntity<Resource> descargarDocumento(
			@PathVariable @NonNull Long eventoId,
			@PathVariable @NonNull Long documentoId) {
		try {
			if (!tieneAccesoPrivilegiadoEventos()) {
				Evento evento = eventoService.obtenerEventoPorId(eventoId);
				if (!Boolean.TRUE.equals(evento.getVisible())) {
					return ResponseEntity.notFound().build();
				}
			}

			Documento documento = eventoService.obtenerDocumentoDeEvento(eventoId, documentoId);
			Resource recurso = documentoService.obtenerRecursoDocumento(documento);

			return ResponseEntity.ok()
					.contentType(MediaType.parseMediaType(documento.getTipo()))
					.header(HttpHeaders.CONTENT_DISPOSITION,
							"attachment; filename=\"" + documento.getNombre() + "\"")
					.body(recurso);
		} catch (EventoNoEncontradoException e) {
			return ResponseEntity.notFound().build();
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}
	}

	private boolean tieneAccesoPrivilegiadoEventos() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()
				|| authentication instanceof AnonymousAuthenticationToken) {
			return false;
		}

		return authentication.getAuthorities().stream()
				.map(authority -> authority.getAuthority())
				.anyMatch(authority -> Roles.ROLE_ADMIN.toString().equals(authority)
						|| Roles.ROLE_MANAGER.toString().equals(authority));
	}

	@PostMapping("/{eventoId}/documentos")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> subirDocumento(
			@PathVariable @NonNull Long eventoId,
			@RequestParam("archivo") MultipartFile archivo) {
		try {
			Documento documento = eventoService.agregarDocumentoAEvento(eventoId, archivo);
			return new ResponseEntity<>(documento, HttpStatus.CREATED);
		} catch (EventoNoEncontradoException e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(Map.of("error", "El evento no fue encontrado."));
		} catch (IOException e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(Map.of("error", "Error al guardar el documento: " + e.getMessage()));
		}
	}

	@DeleteMapping("/{eventoId}/documentos/{documentoId}")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> eliminarDocumento(
			@PathVariable @NonNull Long eventoId,
			@PathVariable @NonNull Long documentoId) {
		try {
			eventoService.eliminarDocumentoDeEvento(eventoId, documentoId);
			return ResponseEntity.ok().build();
		} catch (EventoNoEncontradoException e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(Map.of("error", "El evento no fue encontrado."));
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(Map.of("error", "Error al eliminar el documento: " + e.getMessage()));
		}
	}

	private void aplicarUrlImagenPublica(Evento evento) {
		if (evento == null || evento.getId() == null) {
			return;
		}
		Imagen imagen = evento.getFotoEvento();
		if (imagen != null) {
			imagen.setUrl(baseUrl + "/api/eventos/" + evento.getId() + "/imagen");
		}
	}

	private void aplicarUrlImagenPublica(List<Evento> eventos) {
		if (eventos == null) {
			return;
		}
		for (Evento evento : eventos) {
			aplicarUrlImagenPublica(evento);
		}
	}

	private ResponseEntity<Resource> construirRespuestaImagenOriginal(
			Resource recurso,
			MediaType mediaType,
			String nombreImagen,
			Path rutaArchivo,
			int anchoNormalizado) throws IOException {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.maxAge(Duration.ofHours(8)).cachePublic().mustRevalidate())
				.lastModified(Files.getLastModifiedTime(rutaArchivo).toMillis())
				.header(HttpHeaders.VARY, "Accept-Encoding")
				.contentType(mediaType)
				.header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + nombreImagen + "\"")
				.header(HttpHeaders.ETAG, generarEtagImagen(rutaArchivo, anchoNormalizado, mediaType.toString()))
				.body(recurso);
	}

	private ResponseEntity<Resource> intentarConstruirRespuestaRedimensionada(
			Path rutaArchivo,
			MediaType mediaTypeOriginal,
			String nombreImagen,
			int anchoObjetivo) {
		if (!esImagenRasterRedimensionable(mediaTypeOriginal)) {
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
					.cacheControl(CacheControl.maxAge(Duration.ofHours(8)).cachePublic().mustRevalidate())
					.lastModified(lastModified)
					.header(HttpHeaders.VARY, "Accept-Encoding")
					.contentType(mediaTypeSalida)
					.contentLength(imagenBytes.length)
					.header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + nombreImagen + "\"")
					.header(HttpHeaders.ETAG, generarEtagImagen(rutaArchivo, anchoObjetivo, mediaTypeSalida.toString()))
					.body(recurso);
		} catch (Throwable e) {
			return null;
		}
	}

	private int[] obtenerDimensionesImagen(Path rutaArchivo) {
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

	private byte[] codificarWebp(BufferedImage imagen, float quality) throws IOException {
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

	private String seleccionarTipoCompresion(String[] compressionTypes) {
		for (String type : compressionTypes) {
			if (type != null && type.equalsIgnoreCase("Lossy")) {
				return type;
			}
		}
		return compressionTypes[0];
	}

	private boolean esImagenRasterRedimensionable(MediaType mediaType) {
		if (mediaType == null || !mediaType.getType().equalsIgnoreCase("image")) {
			return false;
		}
		String subtype = mediaType.getSubtype() != null ? mediaType.getSubtype().toLowerCase() : "";
		return !subtype.contains("svg");
	}

	private int normalizarAnchoImagen(Integer anchoSolicitado) {
		if (anchoSolicitado == null || anchoSolicitado <= 0) {
			return 0;
		}
		if (anchoSolicitado < MIN_ANCHO_IMAGEN) {
			return MIN_ANCHO_IMAGEN;
		}
		return Math.min(anchoSolicitado, MAX_ANCHO_IMAGEN);
	}

	private String generarEtagImagen(Path rutaArchivo, int ancho, String formato) throws IOException {
		long tamano = Files.size(rutaArchivo);
		long modificado = Files.getLastModifiedTime(rutaArchivo).toMillis();
		String hash = Long.toHexString(tamano) + "-" + Long.toHexString(modificado) + "-" + ancho + "-" + formato;
		return "\"" + hash + "\"";
	}

}
