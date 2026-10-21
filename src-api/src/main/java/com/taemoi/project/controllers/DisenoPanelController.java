package com.taemoi.project.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.taemoi.project.dtos.request.DisenoPanelRequests;
import com.taemoi.project.dtos.response.DisenoPanelDTO;
import com.taemoi.project.services.DisenoPanelService;

/**
 * Interruptor del diseño nuevo del panel (pieza 8b-I, fase3-e8b-plan.md §3; decisiones 236, 256 y 258), siempre en la
 * escuela activa de la sesión.
 *
 * <p>Regla de URL {@code /api/panel/**} ({@code hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER')}, fuera de la lista blanca
 * de la sesión solo-profesor) y {@code @PreAuthorize} por método: el propietario ({@code ROLE_ADMIN}) lee y asigna; cada
 * cuenta de gestión cambia solo el suyo. Errores con {@code {"mensaje"}}: 400 datos o cuenta sin gestión en la escuela,
 * 403 sin permiso, 404 cuenta de otra escuela o inexistente.
 */
@RestController
@RequestMapping("/api/panel")
public class DisenoPanelController {

	private final DisenoPanelService diseno;

	public DisenoPanelController(DisenoPanelService diseno) {
		this.diseno = diseno;
	}

	/** {@code {porDefecto, usuarios[{usuarioId, permitido, activo}]}} de la gestión de la escuela. */
	@GetMapping("/diseno-nuevo")
	@PreAuthorize("hasAuthority('ROLE_ADMIN')")
	public ResponseEntity<DisenoPanelDTO> listar() {
		return ResponseEntity.ok(diseno.listar());
	}

	/** Da o quita el permiso ({@code {permitido}}); al darlo, queda activo. */
	@PutMapping("/diseno-nuevo/{usuarioId}")
	@PreAuthorize("hasAuthority('ROLE_ADMIN')")
	public ResponseEntity<DisenoPanelDTO.Usuario> cambiarPermiso(@PathVariable Long usuarioId,
			@RequestBody(required = false) DisenoPanelRequests.Permiso solicitud) {
		return ResponseEntity.ok(diseno.cambiarPermiso(usuarioId, solicitud));
	}

	/** «Para todo el equipo» ({@code {nuevo}}): construido, oculto en la pantalla hasta R5 (decisión 258). */
	@PutMapping("/diseno-nuevo/por-defecto")
	@PreAuthorize("hasAuthority('ROLE_ADMIN')")
	public ResponseEntity<DisenoPanelDTO.PorDefecto> cambiarPorDefecto(
			@RequestBody(required = false) DisenoPanelRequests.PorDefecto solicitud) {
		return ResponseEntity.ok(diseno.cambiarPorDefecto(solicitud));
	}

	/** Enciende o apaga el diseño nuevo para la propia cuenta ({@code {activo}}); sin permiso, 403. */
	@PutMapping("/mi-diseno")
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER')")
	public ResponseEntity<DisenoPanelDTO.Propio> cambiarMiDiseno(
			@RequestBody(required = false) DisenoPanelRequests.MiDiseno solicitud) {
		return ResponseEntity.ok(diseno.cambiarMiDiseno(solicitud));
	}
}
