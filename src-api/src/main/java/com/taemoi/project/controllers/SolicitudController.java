package com.taemoi.project.controllers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.taemoi.project.dtos.request.SolicitudRequests;
import com.taemoi.project.dtos.response.SolicitudDTOs;
import com.taemoi.project.services.EncargoService;
import com.taemoi.project.services.SolicitudService;

/**
 * Solicitudes al propietario, rutas de gestión (pieza 6c-2, fase3-6c-solicitudes.md §7.2). Sin pantalla todavía (6c-7).
 *
 * <ul>
 * <li>Bandeja, contador, pedir y retirar: propietario, gestor y responsable de sede (regla de URL
 * {@code /api/solicitudes/**} y {@code @PreAuthorize}). El servicio filtra lo que ve cada uno, comprueba la sede del
 * recurso del responsable (403) y que solo retira quien pidió (403). El propietario no pide (400: lo hace directo).</li>
 * <li>Aceptar (6c-3) y denegar: solo el propietario.</li>
 * <li>Pieza 6c-5, encargos: marcar encargado o recibido y rechazar (con motivo). Propietario y gestor; el responsable,
 * solo los de sus sedes ({@code @acceso.solicitudEnSede}; el servicio lo vuelve a exigir).</li>
 * </ul>
 * Fuera de la lista blanca de la sesión solo-profesor: las rutas del profesor van bajo {@code /api/caja/profesor/**}.
 * Errores con el formato de {@code GlobalExceptionHandler}: 400, 403, 404 y 409.
 */
@RestController
@RequestMapping("/api/solicitudes")
public class SolicitudController {

	private static final String GESTION = "hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_RESPONSABLE_SEDE')";

	private final SolicitudService solicitudes;
	private final EncargoService encargos;

	public SolicitudController(SolicitudService solicitudes, EncargoService encargos) {
		this.solicitudes = solicitudes;
		this.encargos = encargos;
	}

	/** Bandeja por segmento: {@code PENDIENTES}, {@code ENCARGOS} o {@code RESUELTAS}. */
	@GetMapping
	@PreAuthorize(GESTION)
	public ResponseEntity<List<SolicitudDTOs.Solicitud>> listar(@RequestParam(required = false) String segmento) {
		return ResponseEntity.ok(solicitudes.listar(SolicitudService.Segmento.de(segmento)));
	}

	@GetMapping("/contador")
	@PreAuthorize(GESTION)
	public ResponseEntity<SolicitudDTOs.Contador> contador() {
		return ResponseEntity.ok(new SolicitudDTOs.Contador(solicitudes.contador()));
	}

	@PostMapping
	@PreAuthorize(GESTION)
	public ResponseEntity<SolicitudDTOs.Solicitud> crear(
			@RequestBody(required = false) SolicitudRequests.Crear peticion) {
		return ResponseEntity.status(HttpStatus.CREATED).body(solicitudes.crear(peticion));
	}

	@PostMapping("/{id}/retirar")
	@PreAuthorize(GESTION)
	public ResponseEntity<SolicitudDTOs.Solicitud> retirar(@PathVariable Long id) {
		return ResponseEntity.ok(solicitudes.retirar(id));
	}

	/**
	 * Pieza 6c-3: acepta y ejecuta (solo el propietario). 200 con la solicitud ACEPTADA, o NO_APLICABLE con la razón en
	 * {@code respuesta} si el estado cambió desde que se pidió.
	 */
	@PostMapping("/{id}/aceptar")
	@PreAuthorize("hasAuthority('ROLE_ADMIN')")
	public ResponseEntity<SolicitudDTOs.Solicitud> aceptar(@PathVariable Long id) {
		return ResponseEntity.ok(solicitudes.aceptar(id));
	}

	@PostMapping("/{id}/denegar")
	@PreAuthorize("hasAuthority('ROLE_ADMIN')")
	public ResponseEntity<SolicitudDTOs.Solicitud> denegar(@PathVariable Long id,
			@RequestBody(required = false) SolicitudRequests.Denegar peticion) {
		return ResponseEntity.ok(solicitudes.denegar(id, peticion == null ? null : peticion.respuesta()));
	}

	// ------------------------------------------------------------------ encargos (6c-5)

	@PostMapping("/{id}/encargado")
	@PreAuthorize("@acceso.solicitudEnSede(#id)")
	public ResponseEntity<SolicitudDTOs.Solicitud> marcarEncargado(@PathVariable Long id) {
		return ResponseEntity.ok(encargos.marcarEncargado(id));
	}

	@PostMapping("/{id}/recibido")
	@PreAuthorize("@acceso.solicitudEnSede(#id)")
	public ResponseEntity<SolicitudDTOs.Solicitud> marcarRecibido(@PathVariable Long id) {
		return ResponseEntity.ok(encargos.marcarRecibido(id));
	}

	@PostMapping("/{id}/rechazar")
	@PreAuthorize("@acceso.solicitudEnSede(#id)")
	public ResponseEntity<SolicitudDTOs.Solicitud> rechazar(@PathVariable Long id,
			@RequestBody(required = false) SolicitudRequests.Rechazar peticion) {
		return ResponseEntity.ok(encargos.rechazar(id, peticion == null ? null : peticion.motivo()));
	}
}
