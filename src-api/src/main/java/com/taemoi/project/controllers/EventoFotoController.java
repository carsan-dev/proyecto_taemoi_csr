package com.taemoi.project.controllers;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.taemoi.project.dtos.response.EventoFotoDTO;
import com.taemoi.project.entities.Evento;
import com.taemoi.project.entities.EventoFoto;
import com.taemoi.project.entities.Imagen;
import com.taemoi.project.exceptions.ArchivoNoValidoException;
import com.taemoi.project.exceptions.evento.EventoNoEncontradoException;
import com.taemoi.project.exceptions.evento.FotoEventoNoEncontradaException;
import com.taemoi.project.exceptions.evento.ServidorOcupadoException;
import com.taemoi.project.exceptions.evento.TopeFotosEventoException;
import com.taemoi.project.services.EventoFotoService;
import com.taemoi.project.services.EventoService;
import com.taemoi.project.services.ImagenService;

/**
 * Decisión 342: galería de fotos de un evento. Pueden mostrar menores: en un evento oculto, a quien no es de gestión se le
 * responde 404 sin cuerpo, como en {@code GET /api/eventos/{id}/imagen}, por la lista y por cada imagen. La API solo da la
 * URL de estos endpoints, nunca la del fichero; la carpeta del fichero es privada.
 */
@RestController
@RequestMapping("/api/eventos/{eventoId}/fotos")
public class EventoFotoController {

	private static final Logger logger = LoggerFactory.getLogger(EventoFotoController.class);
	/** Menos que el cartel: ocultar un evento debe surtir efecto antes en las fotos, que pueden mostrar menores. */
	private static final Duration CACHE_FOTO_VISIBLE = Duration.ofHours(1);

	@Value("${app.base.url}")
	private String baseUrl;

	private final EventoService eventoService;
	private final EventoFotoService fotos;
	private final ImagenService imagenService;

	public EventoFotoController(EventoService eventoService, EventoFotoService fotos, ImagenService imagenService) {
		this.eventoService = eventoService;
		this.fotos = fotos;
		this.imagenService = imagenService;
	}

	@GetMapping
	public ResponseEntity<List<EventoFotoDTO>> listar(@PathVariable @NonNull Long eventoId) {
		try {
			if (!sePuedeVer(eventoService.obtenerEventoPorId(eventoId))) {
				return ResponseEntity.notFound().build();
			}
			return ResponseEntity.ok(dtos(eventoId, fotos.listar(eventoId)));
		} catch (EventoNoEncontradoException e) {
			return ResponseEntity.notFound().build();
		}
	}

	@GetMapping("/{fotoId}")
	public ResponseEntity<Resource> obtenerImagen(
			@PathVariable @NonNull Long eventoId,
			@PathVariable @NonNull Long fotoId,
			@RequestParam(name = "w", required = false) Integer anchoSolicitado) {
		try {
			Evento evento = eventoService.obtenerEventoPorId(eventoId);
			if (!sePuedeVer(evento)) {
				return ResponseEntity.notFound().build();
			}
			EventoFoto foto = fotos.obtenerConImagen(eventoId, fotoId);
			Imagen imagen = pideMiniatura(anchoSolicitado) ? foto.getMiniatura() : foto.getImagen();
			Path rutaArchivo = imagenService.resolverRutaArchivo(imagen);
			if (rutaArchivo == null) {
				return ResponseEntity.notFound().build();
			}
			return RespuestaImagen.servir(rutaArchivo, imagen.getTipo(), nombreDeDescarga(fotoId, imagen), anchoSolicitado,
					Boolean.TRUE.equals(evento.getVisible()), CACHE_FOTO_VISIBLE);
		} catch (EventoNoEncontradoException | FotoEventoNoEncontradaException e) {
			return ResponseEntity.notFound().build();
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> subir(
			@PathVariable @NonNull Long eventoId,
			@RequestParam("archivo") MultipartFile archivo) {
		try {
			EventoFoto foto = fotos.subir(eventoId, archivo);
			return new ResponseEntity<>(dto(eventoId, foto), HttpStatus.CREATED);
		} catch (EventoNoEncontradoException e) {
			return error(HttpStatus.NOT_FOUND, "El evento no fue encontrado.");
		} catch (ArchivoNoValidoException e) {
			// HEIC, no es una imagen o está dañada: 400 con el motivo. Sin nombre del fichero en el log.
			EventoController.avisarArchivoRechazado("Foto de evento rechazada", eventoId, archivo, e);
			return ResponseEntity.badRequest().body(Map.of("mensaje", EventoController.motivo(e)));
		} catch (TopeFotosEventoException e) {
			return error(HttpStatus.CONFLICT, e.getMessage());
		} catch (ServidorOcupadoException e) {
			return error(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
		} catch (IOException | RuntimeException e) {
			// Sin el mensaje: puede llevar la ruta del fichero.
			logger.error("Error al guardar una foto de evento: eventoId={} ext={} contentType={} causa={}", eventoId,
					EventoController.extensionParaLog(archivo), EventoController.contentTypeParaLog(archivo),
					EventoController.causa(e));
			return error(HttpStatus.INTERNAL_SERVER_ERROR, "Error al guardar la foto.");
		}
	}

	@PutMapping("/orden")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> reordenar(@PathVariable @NonNull Long eventoId,
			@RequestBody(required = false) List<Long> ids) {
		try {
			return ResponseEntity.ok(dtos(eventoId, fotos.reordenar(eventoId, ids)));
		} catch (EventoNoEncontradoException e) {
			return error(HttpStatus.NOT_FOUND, "El evento no fue encontrado.");
		} catch (IllegalArgumentException e) {
			return error(HttpStatus.BAD_REQUEST, e.getMessage());
		} catch (RuntimeException e) {
			logger.error("Error al ordenar las fotos de un evento: eventoId={} causa={}", eventoId,
					EventoController.causa(e));
			return error(HttpStatus.INTERNAL_SERVER_ERROR, "Error al guardar el orden de las fotos.");
		}
	}

	@DeleteMapping("/{fotoId}")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> eliminar(@PathVariable @NonNull Long eventoId, @PathVariable @NonNull Long fotoId) {
		try {
			fotos.eliminar(eventoId, fotoId);
			return ResponseEntity.ok().build();
		} catch (EventoNoEncontradoException e) {
			return error(HttpStatus.NOT_FOUND, "El evento no fue encontrado.");
		} catch (FotoEventoNoEncontradaException e) {
			return error(HttpStatus.NOT_FOUND, "La foto no fue encontrada.");
		} catch (RuntimeException e) {
			logger.error("Error al eliminar una foto de evento: eventoId={} fotoId={} causa={}", eventoId, fotoId,
					EventoController.causa(e));
			return error(HttpStatus.INTERNAL_SERVER_ERROR, "Error al eliminar la foto.");
		}
	}

	/** Evento visible, o quien pregunta es de gestión. Sin visibilidad guardada cuenta como oculto. */
	private static boolean sePuedeVer(Evento evento) {
		return Boolean.TRUE.equals(evento.getVisible()) || EventoController.tieneAccesoPrivilegiadoEventos();
	}

	/** Un {@code ?w=} sin valor útil (0 o negativo) cuenta como no pedido, igual que en {@code RespuestaImagen}. */
	private static boolean pideMiniatura(Integer anchoSolicitado) {
		return anchoSolicitado != null && anchoSolicitado > 0
				&& anchoSolicitado <= ImagenService.LADO_MINIATURA_FOTO_EVENTO;
	}

	private List<EventoFotoDTO> dtos(Long eventoId, List<EventoFoto> lista) {
		return lista.stream().map(foto -> dto(eventoId, foto)).toList();
	}

	private EventoFotoDTO dto(Long eventoId, EventoFoto foto) {
		return new EventoFotoDTO(foto.getId(), baseUrl + "/api/eventos/" + eventoId + "/fotos/" + foto.getId(),
				foto.getOrden());
	}

	/** Sin el nombre real del fichero, que es el último tramo de su ruta. */
	private static String nombreDeDescarga(Long fotoId, Imagen imagen) {
		String nombre = imagen.getNombre();
		int punto = nombre == null ? -1 : nombre.lastIndexOf('.');
		return "foto-" + fotoId + (punto >= 0 ? nombre.substring(punto) : "");
	}

	private static ResponseEntity<Map<String, String>> error(HttpStatus estado, String mensaje) {
		return ResponseEntity.status(estado).body(Map.of("mensaje", mensaje));
	}
}
