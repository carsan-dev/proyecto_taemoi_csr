package com.taemoi.project.controllers;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.taemoi.project.archivos.CuerpoCrudo;
import com.taemoi.project.dtos.response.MaterialGestionDTO;
import com.taemoi.project.exceptions.MaterialExamenException;
import com.taemoi.project.services.MaterialExamenGestionService;
import com.taemoi.project.services.MaterialExamenService.MaterialExamenArchivo;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Pieza R1 (fase3-e8c-plan.md): temario, vídeos y documentación por disciplina y bloque, para el propietario y el gestor de
 * la escuela activa. Los ficheros llegan como cuerpo crudo, sin multipart: el límite de multipart es global y aquí un vídeo
 * llega a 500 MB (decisión 403). Las rutas de las familias siguen en {@link AlumnoController}.
 *
 * <p>Los errores responden {@code {"mensaje": …}}, en español y aptos para la pantalla. El 404 y el 400 de argumento los da
 * {@code GlobalExceptionHandler}.
 */
@RestController
@RequestMapping("/api/material-examen")
@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
public class MaterialExamenController {

	private static final Logger logger = LoggerFactory.getLogger(MaterialExamenController.class);

	private final MaterialExamenGestionService gestion;

	public MaterialExamenController(MaterialExamenGestionService gestion) {
		this.gestion = gestion;
	}

	public record CambioDeTitulo(String titulo) {
	}

	// ------------------------------------------------------------------ lecturas

	@GetMapping
	public MaterialGestionDTO.Listado listar() {
		return gestion.listar();
	}

	@GetMapping("/{disciplina}/{bloque}")
	public MaterialGestionDTO.Detalle detalle(@PathVariable String disciplina, @PathVariable String bloque) {
		return gestion.detalle(disciplina, bloque);
	}

	@GetMapping("/{disciplina}/{bloque}/temario")
	public ResponseEntity<Resource> temario(@PathVariable String disciplina, @PathVariable String bloque,
			@RequestParam(name = "download", required = false, defaultValue = "false") boolean download,
			@RequestHeader HttpHeaders cabeceras) throws IOException {
		return RespuestaArchivoConRange.servir(gestion.temario(disciplina, bloque), cabeceras, download);
	}

	@GetMapping("/{disciplina}/{bloque}/videos/{fichero}")
	public ResponseEntity<Resource> video(@PathVariable String disciplina, @PathVariable String bloque,
			@PathVariable String fichero, @RequestHeader HttpHeaders cabeceras) throws IOException {
		return RespuestaArchivoConRange.servir(gestion.video(disciplina, bloque, fichero), cabeceras, false);
	}

	/** Como en las familias: solo el PDF se abre en el navegador; el resto se descarga. */
	@GetMapping("/{disciplina}/{bloque}/documentacion/{fichero}")
	public ResponseEntity<Resource> documento(@PathVariable String disciplina, @PathVariable String bloque,
			@PathVariable String fichero,
			@RequestParam(name = "download", required = false, defaultValue = "false") boolean download,
			@RequestHeader HttpHeaders cabeceras) throws IOException {
		MaterialExamenArchivo documento = gestion.documento(disciplina, bloque, fichero);
		String mime = documento.getMimeType();
		boolean esPdf = mime != null && mime.toLowerCase(Locale.ROOT).startsWith("application/pdf");
		return RespuestaArchivoConRange.servir(documento, cabeceras, download || !esPdf);
	}

	// ------------------------------------------------------------------ vídeos

	@PostMapping("/{disciplina}/{bloque}/videos")
	public ResponseEntity<MaterialGestionDTO.Subida> subirVideo(@PathVariable String disciplina,
			@PathVariable String bloque, @RequestParam(name = "nombre", required = false) String nombre,
			HttpServletRequest peticion) throws IOException {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(gestion.subirVideo(disciplina, bloque, nombre, cuerpo(peticion)));
	}

	@PutMapping("/{disciplina}/{bloque}/videos/{fichero}/contenido")
	public MaterialGestionDTO.Detalle sustituirVideo(@PathVariable String disciplina, @PathVariable String bloque,
			@PathVariable String fichero, HttpServletRequest peticion) throws IOException {
		return gestion.sustituirVideo(disciplina, bloque, fichero, cuerpo(peticion));
	}

	@PatchMapping("/{disciplina}/{bloque}/videos/{fichero}")
	public MaterialGestionDTO.Detalle cambiarTitulo(@PathVariable String disciplina, @PathVariable String bloque,
			@PathVariable String fichero, @RequestBody(required = false) CambioDeTitulo cambio) {
		return gestion.cambiarTitulo(disciplina, bloque, fichero, cambio == null ? null : cambio.titulo());
	}

	@PutMapping("/{disciplina}/{bloque}/videos/orden")
	public MaterialGestionDTO.Detalle reordenarVideos(@PathVariable String disciplina, @PathVariable String bloque,
			@RequestBody(required = false) List<String> ficheros) {
		return gestion.reordenarVideos(disciplina, bloque, ficheros);
	}

	@DeleteMapping("/{disciplina}/{bloque}/videos/{fichero}")
	public MaterialGestionDTO.Detalle eliminarVideo(@PathVariable String disciplina, @PathVariable String bloque,
			@PathVariable String fichero) {
		return gestion.eliminarVideo(disciplina, bloque, fichero);
	}

	// ------------------------------------------------------------------ temario y documentación

	@PutMapping("/{disciplina}/{bloque}/temario")
	public MaterialGestionDTO.Detalle subirTemario(@PathVariable String disciplina, @PathVariable String bloque,
			HttpServletRequest peticion) throws IOException {
		return gestion.subirTemario(disciplina, bloque, cuerpo(peticion));
	}

	@PostMapping("/{disciplina}/{bloque}/documentacion")
	public ResponseEntity<MaterialGestionDTO.Subida> subirDocumento(@PathVariable String disciplina,
			@PathVariable String bloque, @RequestParam(name = "nombre", required = false) String nombre,
			HttpServletRequest peticion) throws IOException {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(gestion.subirDocumento(disciplina, bloque, nombre, cuerpo(peticion)));
	}

	@PutMapping("/{disciplina}/{bloque}/documentacion/{fichero}/contenido")
	public MaterialGestionDTO.Detalle sustituirDocumento(@PathVariable String disciplina, @PathVariable String bloque,
			@PathVariable String fichero, HttpServletRequest peticion) throws IOException {
		return gestion.sustituirDocumento(disciplina, bloque, fichero, cuerpo(peticion));
	}

	@DeleteMapping("/{disciplina}/{bloque}/documentacion/{fichero}")
	public MaterialGestionDTO.Detalle eliminarDocumento(@PathVariable String disciplina, @PathVariable String bloque,
			@PathVariable String fichero) {
		return gestion.eliminarDocumento(disciplina, bloque, fichero);
	}

	// ------------------------------------------------------------------ errores

	@ExceptionHandler(MaterialExamenException.class)
	public ResponseEntity<Map<String, String>> error(MaterialExamenException e) {
		return mensaje(e.getEstado(), e.getMessage());
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<Map<String, String>> cuerpoIlegible(HttpMessageNotReadableException e) {
		return mensaje(HttpStatus.BAD_REQUEST, "La petición no es válida.");
	}

	/** Sin el mensaje: puede llevar la ruta del fichero. */
	@ExceptionHandler(IOException.class)
	public ResponseEntity<Map<String, String>> errorDeFichero(IOException e) {
		logger.error("Error al servir material de examen: causa={}", e.getClass().getSimpleName());
		return mensaje(HttpStatus.INTERNAL_SERVER_ERROR, "No se ha podido acceder al fichero.");
	}

	private static ResponseEntity<Map<String, String>> mensaje(HttpStatus estado, String mensaje) {
		// Tipo explícito: una petición de fichero puede no aceptar JSON y el error debe salir igual.
		return ResponseEntity.status(estado).contentType(MediaType.APPLICATION_JSON).body(Map.of("mensaje", mensaje));
	}

	private static CuerpoCrudo cuerpo(HttpServletRequest peticion) throws IOException {
		return new CuerpoCrudo(peticion.getInputStream(), peticion.getContentLengthLong(), peticion.getContentType());
	}
}
