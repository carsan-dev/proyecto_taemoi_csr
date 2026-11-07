package com.taemoi.project.controllers;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.dtos.request.SedeActivaRequest;
import com.taemoi.project.dtos.request.SedeRequest;
import com.taemoi.project.dtos.response.SedeDTO;
import com.taemoi.project.dtos.response.SedeEditadaDTO;
import com.taemoi.project.services.GestionSedesService;
import com.taemoi.project.services.PreinscripcionService;
import com.taemoi.project.services.SedeFotoService;

/**
 * Sedes de la escuela activa (pieza E8-2; D8-1, D8-2): propietario y gestor las listan (también las inactivas), las
 * ven, las crean, las editan, las activan o desactivan y ponen o quitan sus dos fotos de la web. Sin borrado.
 *
 * <p>Solo {@code ROLE_ADMIN} y {@code ROLE_MANAGER}: la regla de URL explícita de {@code SecurityConfiguration} (antes
 * de las del responsable de sede) y el {@code @PreAuthorize} de cada método. RESPONSABLE_SEDE (decisión 370), PROFESOR,
 * FAMILIA y la sesión solo-plataforma, 403; fuera de la lista blanca de la sesión solo-profesor. Errores con
 * {@code {"mensaje"}}: 400 datos, 404 sede de otra escuela o inexistente, 409 nombre repetido o última sede activa.
 */
@RestController
@RequestMapping("/api/sedes")
public class SedeController {

	private static final Logger logger = LoggerFactory.getLogger(SedeController.class);

	private final GestionSedesService sedes;
	private final PreinscripcionService preinscripciones;
	private final SedeFotoService fotos;
	private final ObjectMapper json;

	public SedeController(GestionSedesService sedes, PreinscripcionService preinscripciones, SedeFotoService fotos,
			ObjectMapper json) {
		this.sedes = sedes;
		this.preinscripciones = preinscripciones;
		this.fotos = fotos;
		this.json = json;
	}

	/** Todas las sedes de la escuela, por id. */
	@GetMapping
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER')")
	public ResponseEntity<List<SedeDTO>> listar() {
		return ResponseEntity.ok(sedes.listar());
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER')")
	public ResponseEntity<SedeDTO> ver(@PathVariable Long id) {
		return ResponseEntity.ok(sedes.ver(id));
	}

	/** Alta de una sede (activa): 201. */
	@PostMapping
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER')")
	public ResponseEntity<SedeDTO> crear(@RequestBody SedeRequest solicitud) {
		return ResponseEntity.status(HttpStatus.CREATED).body(sedes.crear(solicitud));
	}

	/**
	 * Reemplaza los datos editables (no {@code activa}). Si cambia el aforo, promociona la lista de espera, como al
	 * cambiar el límite común ({@code AdminController#actualizarLimiteTurno}), y la respuesta dice si queda por debajo de
	 * la ocupación de algún turno: se guarda igual y nadie causa baja.
	 *
	 * <p>Web por plantilla (W2): el cuerpo se lee como árbol para saber qué campos de la web llegan; los que no llegan se
	 * conservan (el panel de hoy no los envía).
	 */
	@PutMapping("/{id}")
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER')")
	public ResponseEntity<SedeEditadaDTO> editar(@PathVariable Long id, @RequestBody JsonNode cuerpo) {
		SedeRequest solicitud = leer(cuerpo);
		Set<String> recibidos = SedeRequest.CAMPOS_WEB.stream().filter(cuerpo::has).collect(Collectors.toSet());
		GestionSedesService.Edicion edicion = sedes.editar(id, solicitud, recibidos);
		if (edicion.aforoCambiado()) {
			preinscripciones.promocionarTodas();
		}
		return ResponseEntity.ok(new SedeEditadaDTO(edicion.sede(), edicion.turnosSobreAforo()));
	}

	/** {@code {"activa": true | false}}; la última sede activa no se desactiva (409). */
	@PutMapping("/{id}/activa")
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER')")
	public ResponseEntity<SedeDTO> cambiarActiva(@PathVariable Long id, @RequestBody SedeActivaRequest solicitud) {
		return ResponseEntity.ok(sedes.cambiarActiva(id, solicitud));
	}

	/**
	 * Web por plantilla: pone o sustituye la foto 1 (la principal) o la 2 («cómo llegar») con la imagen del campo
	 * {@code archivo} (JPEG, PNG o WebP). Va a la carpeta pública de la web de la escuela, nunca a la de alumnos. 200 con
	 * la sede; 400 si la imagen no vale; 404 si la sede es de otra escuela, no existe o la foto no es 1 ni 2.
	 */
	@PutMapping(path = "/{id}/fotos/{numero}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER')")
	public ResponseEntity<?> cambiarFoto(@PathVariable Long id, @PathVariable int numero,
			@RequestParam("archivo") MultipartFile archivo) {
		try {
			return ResponseEntity.ok(fotos.cambiar(id, numero, archivo));
		} catch (IOException e) {
			// Sin el mensaje ni el nombre del fichero: pueden llevar la ruta o el nombre de una persona.
			logger.error("Error al guardar la foto de una sede: sedeId={} foto={} causa={}", id, numero,
					e.getClass().getSimpleName());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(Map.of("mensaje", "Error al guardar la foto."));
		}
	}

	/** Web por plantilla: quita la foto 1 o la 2. 200 con la sede, también si no tenía; 404 como al subirla. */
	@DeleteMapping("/{id}/fotos/{numero}")
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER')")
	public ResponseEntity<SedeDTO> quitarFoto(@PathVariable Long id, @PathVariable int numero) {
		return ResponseEntity.ok(fotos.quitar(id, numero));
	}

	/** Un objeto JSON con la forma de {@link SedeRequest}; otra cosa, 400. */
	private SedeRequest leer(JsonNode cuerpo) {
		if (cuerpo == null || !cuerpo.isObject()) {
			throw new IllegalArgumentException(GestionSedesService.SIN_DATOS);
		}
		try {
			return json.treeToValue(cuerpo, SedeRequest.class);
		} catch (JsonProcessingException | IllegalArgumentException error) {
			throw new IllegalArgumentException(GestionSedesService.SIN_DATOS);
		}
	}
}
