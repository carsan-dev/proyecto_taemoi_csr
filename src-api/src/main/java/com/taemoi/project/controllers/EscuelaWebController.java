package com.taemoi.project.controllers;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.web.ContenidoWebDTO;
import com.taemoi.project.web.ContenidoWebEnConflictoException;
import com.taemoi.project.web.ContenidoWebNoValidoException;
import com.taemoi.project.web.ContenidoWebRequest;
import com.taemoi.project.web.ContenidoWebService;
import com.taemoi.project.web.EstadoWebDTO;
import com.taemoi.project.web.PublicacionWebService;
import com.taemoi.project.web.TemasWeb;

/**
 * Pieza W3 de la web por plantilla (fase4-web-plan.md §5): «Web · Tema» del panel, de la escuela ACTIVA. Solo el
 * propietario (ROLE_ADMIN; §9, n.º 3): la regla de URL de {@code /api/escuela/**} deja pasar a la gestión y
 * {@code @PreAuthorize} la estrecha. El superadmin publica por {@code /api/plataforma/escuelas/{id}/web/publicacion}.
 * Moiskimdo (web propia) responde 409.
 *
 * <p>Pieza W12a: el contenido de la web para el editor ({@code /contenido}) y la subida de sus fotos ({@code /imagenes}),
 * con la misma regla: solo el propietario y nunca Moiskimdo.
 */
@RestController
@RequestMapping("/api/escuela/web")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class EscuelaWebController {

	static final String RUTA_PUBLICAR = "/api/escuela/web/publicacion";
	static final String CUERPO_NO_VALIDO = "El cuerpo de la petición no es válido.";

	private static final Logger logger = LoggerFactory.getLogger(EscuelaWebController.class);

	private final PublicacionWebService publicacion;
	private final ContenidoWebService contenido;

	public EscuelaWebController(PublicacionWebService publicacion, ContenidoWebService contenido) {
		this.publicacion = publicacion;
		this.contenido = contenido;
	}

	/** Tema publicado, si está publicada, cuándo y los temas disponibles. */
	@GetMapping
	public ResponseEntity<EstadoWebDTO> estado() {
		return ResponseEntity.ok(publicacion.estado(TenantContext.get()));
	}

	/**
	 * Publica la web con el tema del cuerpo ({@code {"tema": "chandal"}}). 200 con el estado; 400 tema que no vale;
	 * 409 Moiskimdo o publicación a la vez. Idempotente con el mismo tema.
	 */
	@PutMapping("/publicacion")
	public ResponseEntity<EstadoWebDTO> publicar(@RequestBody(required = false) JsonNode cuerpo) {
		return ResponseEntity.ok(publicacion.publicar(TenantContext.get(), TemasWeb.temaDe(cuerpo), RUTA_PUBLICAR));
	}

	/** Pieza W12a: el contenido de la web con su versión, también las secciones ocultas. */
	@GetMapping("/contenido")
	public ResponseEntity<ContenidoWebDTO> contenido() {
		return ResponseEntity.ok(contenido.leer(TenantContext.get()));
	}

	/**
	 * Pieza W12a: guarda el contenido ENTERO ({@code {version, bloques}}) y sale en la web al momento. 200 con el
	 * contenido y la versión nueva; 400 {@code {mensaje, errores}}; 409 si otra persona guardó antes (nada escrito) o
	 * Moiskimdo.
	 */
	@PutMapping("/contenido")
	public ResponseEntity<ContenidoWebDTO> guardarContenido(@RequestBody(required = false) ContenidoWebRequest cuerpo) {
		return ResponseEntity.ok(contenido.guardar(TenantContext.get(), cuerpo));
	}

	/**
	 * Pieza W12a: sube una foto para un bloque (campo {@code archivo}: JPEG, PNG o WebP) a la carpeta pública de la web
	 * de la escuela. 201 {@code {imagenId, url}}; 400 si la imagen no vale; 409 Moiskimdo.
	 */
	@PostMapping(path = "/imagenes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<?> subirImagen(@RequestParam(name = "archivo", required = false) MultipartFile archivo) {
		try {
			return ResponseEntity.status(HttpStatus.CREATED).body(contenido.subirImagen(TenantContext.get(), archivo));
		} catch (IOException e) {
			// Sin el mensaje ni el nombre del fichero: pueden llevar la ruta o el nombre de una persona.
			logger.error("Error al guardar una foto de la web: causa={}", e.getClass().getSimpleName());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(Map.of("mensaje", "Error al guardar la foto."));
		}
	}

	/** 400 del contenido: {@code {mensaje, errores: {clave: texto}}}. */
	@ExceptionHandler(ContenidoWebNoValidoException.class)
	public ResponseEntity<Map<String, Object>> contenidoNoValido(ContenidoWebNoValidoException e) {
		Map<String, Object> cuerpo = new LinkedHashMap<>();
		cuerpo.put("mensaje", e.getMessage());
		cuerpo.put("errores", e.getErrores());
		return ResponseEntity.badRequest().body(cuerpo);
	}

	/** 409 del contenido (d522): solo el mensaje, sin quién ni cuándo (d530). */
	@ExceptionHandler(ContenidoWebEnConflictoException.class)
	public ResponseEntity<Map<String, String>> contenidoEnConflicto(ContenidoWebEnConflictoException e) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("mensaje", e.getMessage(), "motivo", "version"));
	}

	/** Cuerpo que no se puede leer: 400 con {@code {"mensaje"}}, como {@code PlataformaController}. */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<Map<String, String>> cuerpoNoLegible() {
		return ResponseEntity.badRequest().body(Map.of("mensaje", CUERPO_NO_VALIDO));
	}
}
