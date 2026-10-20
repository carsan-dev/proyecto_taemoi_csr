package com.taemoi.project.controllers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.taemoi.project.dtos.request.SedeActivaRequest;
import com.taemoi.project.dtos.request.SedeRequest;
import com.taemoi.project.dtos.response.SedeDTO;
import com.taemoi.project.services.GestionSedesService;

/**
 * Sedes de la escuela activa (pieza E8-2; D8-1, D8-2): propietario y gestor las listan (también las inactivas), las
 * ven, las crean, las editan y las activan o desactivan. Sin borrado.
 *
 * <p>Solo {@code ROLE_ADMIN} y {@code ROLE_MANAGER}: la regla de URL explícita de {@code SecurityConfiguration} (antes
 * de las del responsable de sede) y el {@code @PreAuthorize} de cada método. RESPONSABLE_SEDE (hasta la E9), PROFESOR,
 * FAMILIA y la sesión solo-plataforma, 403; fuera de la lista blanca de la sesión solo-profesor. Errores con
 * {@code {"mensaje"}}: 400 datos, 404 sede de otra escuela o inexistente, 409 nombre repetido o última sede activa.
 */
@RestController
@RequestMapping("/api/sedes")
public class SedeController {

	private final GestionSedesService sedes;

	public SedeController(GestionSedesService sedes) {
		this.sedes = sedes;
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

	/** Reemplaza los datos editables (no {@code activa}). */
	@PutMapping("/{id}")
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER')")
	public ResponseEntity<SedeDTO> editar(@PathVariable Long id, @RequestBody SedeRequest solicitud) {
		return ResponseEntity.ok(sedes.editar(id, solicitud));
	}

	/** {@code {"activa": true | false}}; la última sede activa no se desactiva (409). */
	@PutMapping("/{id}/activa")
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER')")
	public ResponseEntity<SedeDTO> cambiarActiva(@PathVariable Long id, @RequestBody SedeActivaRequest solicitud) {
		return ResponseEntity.ok(sedes.cambiarActiva(id, solicitud));
	}
}
