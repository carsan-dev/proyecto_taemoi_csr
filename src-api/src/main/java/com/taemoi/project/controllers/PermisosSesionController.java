package com.taemoi.project.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.taemoi.project.dtos.response.PermisosSesionDTO;
import com.taemoi.project.services.PermisosSesionService;

/**
 * {@code GET /api/auth/permisos} (pieza E8-1, D8-14): roles, sedes visibles y capacidades de la sesión, la fuente
 * única de menús y guards del frontend. Las mismas sesiones que {@code /api/auth/roles} más la solo-plataforma (con
 * roles vacíos). Sin regla de URL propia (cae en {@code authenticated()}); en la lista blanca de la sesión
 * solo-profesor ({@code SesionSoloProfesorAuthorizationManager}). Como todo {@code /api/auth/**}, no se audita.
 */
@RestController
@RequestMapping("/api/auth")
public class PermisosSesionController {

	private final PermisosSesionService servicio;

	public PermisosSesionController(PermisosSesionService servicio) {
		this.servicio = servicio;
	}

	@GetMapping("/permisos")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN') || hasRole('ROLE_USER') || hasRole('ROLE_PROFESOR')"
			+ " || hasAuthority('ROLE_ADMIN_PLATAFORMA')")
	public ResponseEntity<PermisosSesionDTO> permisos() {
		return ResponseEntity.ok(servicio.permisos());
	}
}
