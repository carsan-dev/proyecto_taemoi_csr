package com.taemoi.project.controllers;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
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
import com.taemoi.project.exceptions.ArchivoNoValidoException;
import com.taemoi.project.exceptions.evento.EventoNoEncontradoException;
import com.taemoi.project.exceptions.turno.TurnoNoEncontradoException;
import com.taemoi.project.services.DocumentoService;
import com.taemoi.project.services.EventoService;
import com.taemoi.project.services.ImagenService;
import com.taemoi.project.utils.DocumentoSecurityUtils;

@RestController
@RequestMapping("/api/eventos")
public class EventoController {

	private static final Logger logger = LoggerFactory.getLogger(EventoController.class);

	@Value("${app.base.url}")
	private String baseUrl;

	@Autowired
	private EventoService eventoService;

	@Autowired
	private ImagenService imagenService;

	@Autowired
	private DocumentoService documentoService;

	@Autowired
	private ObjectMapper objectMapper;

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
	public ResponseEntity<Evento> obtenerEventoPorId(@PathVariable @NonNull Long eventoId) {
		try {
			Evento evento = eventoService.obtenerEventoPorId(eventoId);
			if (!Boolean.TRUE.equals(evento.getVisible()) && !tieneAccesoPrivilegiadoEventos()) {
				return ResponseEntity.notFound().build();
			}
			aplicarUrlImagenPublica(evento);
			return ResponseEntity.ok(evento);
		} catch (EventoNoEncontradoException | TurnoNoEncontradoException e) {
			return ResponseEntity.notFound().build();
		}
	}

	@PostMapping(value = "/crear", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> crearEvento(@RequestParam("nuevo") String eventoJson,
			@RequestParam(required = false) MultipartFile file) {
		try {
			Evento nuevoEvento = objectMapper.readValue(eventoJson, Evento.class);

			// Guardar el evento y la imagen (si existe)
			Evento eventoCreado = eventoService.guardarEvento(nuevoEvento, file);
			aplicarUrlImagenPublica(eventoCreado);
			return new ResponseEntity<>(eventoCreado, HttpStatus.CREATED);

		} catch (IOException e) {
			return new ResponseEntity<>("Error al procesar la solicitud", HttpStatus.BAD_REQUEST);
		} catch (ArchivoNoValidoException e) {
			// Imagen no válida (HEIC, no es una imagen): 400 con el motivo.
			avisarArchivoRechazado("Imagen de evento rechazada (alta)", null, file, e);
			return ResponseEntity.badRequest().body(Map.of("mensaje", motivo(e)));
		} catch (Exception e) {
			logger.error("Error al crear evento: ext={} contentType={} causa={}", extensionParaLog(file),
					contentTypeParaLog(file), causa(e));
			return new ResponseEntity<>("Error interno al guardar el evento.", HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> actualizarEvento(@PathVariable @NonNull Long id,
			@RequestParam(required = false) MultipartFile file,
			@RequestParam("eventoEditado") String eventoJson) {
		try {
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
		} catch (ArchivoNoValidoException e) {
			// Imagen no válida (HEIC, no es una imagen): 400 con el motivo.
			avisarArchivoRechazado("Imagen de evento rechazada (edición)", id, file, e);
			return ResponseEntity.badRequest().body(Map.of("mensaje", motivo(e)));
		} catch (Exception e) {
			logger.error("Error al actualizar evento: eventoId={} ext={} contentType={} causa={}", id,
					extensionParaLog(file), contentTypeParaLog(file), causa(e));
			return new ResponseEntity<>("Error interno al actualizar el evento.", HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@GetMapping("/{eventoId}/imagen")
	public ResponseEntity<Resource> obtenerImagenEvento(
			@PathVariable @NonNull Long eventoId,
			@RequestParam(name = "w", required = false) Integer anchoSolicitado) {
		try {
			Evento evento = eventoService.obtenerEventoPorId(eventoId);
			// Evento oculto: solo gestión (como el detalle); al resto, 404 para no revelar que existe.
			boolean eventoVisible = Boolean.TRUE.equals(evento.getVisible());
			if (!eventoVisible && !tieneAccesoPrivilegiadoEventos()) {
				return ResponseEntity.notFound().build();
			}
			Imagen imagen = evento.getFotoEvento();

			if (imagen == null || imagen.getRuta() == null) {
				return ResponseEntity.notFound().build();
			}

			Path rutaArchivo = imagenService.resolverRutaArchivo(imagen);
			if (rutaArchivo == null) {
				return ResponseEntity.notFound().build();
			}
			return RespuestaImagen.servir(rutaArchivo, imagen.getTipo(), imagen.getNombre(), anchoSolicitado, eventoVisible);
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
			@PathVariable @NonNull Long documentoId,
			@RequestParam(name = "download", required = false, defaultValue = "false") boolean forceDownload) {
		try {
			if (!tieneAccesoPrivilegiadoEventos()) {
				Evento evento = eventoService.obtenerEventoPorId(eventoId);
				if (!Boolean.TRUE.equals(evento.getVisible())) {
					return ResponseEntity.notFound().build();
				}
			}

			Documento documento = eventoService.obtenerDocumentoDeEvento(eventoId, documentoId);
			Resource recurso = documentoService.obtenerRecursoDocumento(documento);
			MediaType mediaType = DocumentoSecurityUtils.resolverMediaTypeRespuesta(documento.getTipo(), forceDownload);
			String nombreDocumento = asegurarNombreConExtension(documento.getNombre(), documento.getTipo());
			String dispositionType = DocumentoSecurityUtils.resolverDispositionType(documento.getTipo(), forceDownload);

			return ResponseEntity.ok()
					.contentType(mediaType)
					.header(HttpHeaders.CONTENT_DISPOSITION,
							dispositionType + "; filename=\"" + nombreDocumento + "\"")
					.body(recurso);
		} catch (EventoNoEncontradoException e) {
			return ResponseEntity.notFound().build();
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}
	}

	static boolean tieneAccesoPrivilegiadoEventos() {
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

	private String asegurarNombreConExtension(String nombreDocumento, String mimeType) {
		String nombreBase = (nombreDocumento == null || nombreDocumento.isBlank()) ? "documento" : nombreDocumento;
		if (tieneExtension(nombreBase)) {
			return nombreBase;
		}

		String extension = obtenerExtensionDesdeMime(mimeType);
		if (extension == null) {
			return nombreBase;
		}
		return nombreBase + "." + extension;
	}

	private boolean tieneExtension(String nombreArchivo) {
		int indiceUltimoPunto = nombreArchivo.lastIndexOf('.');
		return indiceUltimoPunto > 0 && indiceUltimoPunto < nombreArchivo.length() - 1;
	}

	private String obtenerExtensionDesdeMime(String mimeType) {
		if (mimeType == null || mimeType.isBlank()) {
			return null;
		}

		String normalizado = mimeType.split(";")[0].trim().toLowerCase();
		return switch (normalizado) {
			case "application/pdf" -> "pdf";
			case "text/csv" -> "csv";
			case "text/plain" -> "txt";
			case "image/jpeg" -> "jpg";
			case "image/png" -> "png";
			case "image/webp" -> "webp";
			case "image/gif" -> "gif";
			case "application/msword" -> "doc";
			case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx";
			case "application/vnd.ms-excel" -> "xls";
			case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "xlsx";
			default -> null;
		};
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
		} catch (ArchivoNoValidoException e) {
			// Fichero no válido (tipo, extensión, HEIC, vacío): 400 con el motivo. Sin nombre del fichero en el log.
			// Otro argumento inválido sigue a GlobalExceptionHandler (el mismo 400 con «mensaje», sin este aviso).
			avisarArchivoRechazado("Documento de evento rechazado", eventoId, archivo, e);
			return ResponseEntity.badRequest().body(Map.of("mensaje", motivo(e)));
		} catch (IOException e) {
			// Sin el mensaje: puede llevar la ruta y el nombre del fichero.
			logger.error("Error al guardar documento de evento: eventoId={} ext={} contentType={} causa={}", eventoId,
					extensionParaLog(archivo), contentTypeParaLog(archivo), causa(e));
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(Map.of("error", "Error al guardar el documento."));
		}
	}

	static void avisarArchivoRechazado(String que, Long eventoId, MultipartFile archivo,
			IllegalArgumentException e) {
		logger.warn("{}: eventoId={} ext={} contentType={} motivo={}", que, eventoId, extensionParaLog(archivo),
				contentTypeParaLog(archivo), e.getMessage());
	}

	static String extensionParaLog(MultipartFile archivo) {
		return DocumentoSecurityUtils.extensionParaLog(archivo != null ? archivo.getOriginalFilename() : null);
	}

	static String contentTypeParaLog(MultipartFile archivo) {
		return DocumentoSecurityUtils.contentTypeParaLog(archivo != null ? archivo.getContentType() : null);
	}

	/** Motivo del rechazo para el cuerpo {@code {"mensaje": …}}; nunca nulo. */
	static String motivo(IllegalArgumentException e) {
		return e.getMessage() != null ? e.getMessage() : "Solicitud no válida.";
	}

	/** Clase de la excepción y de su causa raíz, para el log sin mensajes (pueden llevar rutas o nombres). */
	static String causa(Throwable e) {
		Throwable raiz = e;
		while (raiz.getCause() != null && raiz.getCause() != raiz) {
			raiz = raiz.getCause();
		}
		return raiz == e ? e.getClass().getSimpleName()
				: e.getClass().getSimpleName() + " <- " + raiz.getClass().getSimpleName();
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

}
