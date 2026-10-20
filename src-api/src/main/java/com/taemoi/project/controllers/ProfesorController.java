package com.taemoi.project.controllers;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.taemoi.project.dtos.response.ProfesorDTOs;
import com.taemoi.project.services.ProfesorService;

/**
 * Rutas propias del profesor (entrega 7, fase3-e7-profesor.md §6.2; pieza E7-1: solo lectura).
 *
 * <p>Tres capas (§6.3): la lista blanca de la sesión solo-profesor ({@code SesionSoloProfesorAuthorizationManager},
 * {@code /api/profesor/**}), la regla de URL ({@code /api/profesor/**} → {@code ROLE_PROFESOR}) y el
 * {@code @PreAuthorize} de cada método, que nunca admite la gestión (D7-10): {@code ROLE_PROFESOR} sin
 * {@code ROLE_ADMIN} ni {@code ROLE_MANAGER}, o {@code @acceso.*DeMisGrupos} (solo la rama de profesor, que ya exige
 * alcance no total). El servicio filtra por {@code alcance.grupos()} y nada más (D7-11). Ninguna ruta de gestión se
 * abre al profesor (D7-9).
 */
@RestController
@RequestMapping("/api/profesor")
public class ProfesorController {

	private final ProfesorService profesor;

	public ProfesorController(ProfesorService profesor) {
		this.profesor = profesor;
	}

	/** Sus grupos (§5.1). */
	@GetMapping("/grupos")
	@PreAuthorize("hasAuthority('ROLE_PROFESOR') and !hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGER')")
	public ResponseEntity<List<ProfesorDTOs.Grupo>> misGrupos() {
		return ResponseEntity.ok(profesor.misGrupos());
	}

	/** Alumnos activos de un grupo suyo (§5.2). Grupo ajeno, de otra escuela o inexistente: 403. */
	@GetMapping("/grupos/{grupoId}/alumnos")
	@PreAuthorize("@acceso.grupoDeMisGrupos(#grupoId)")
	public ResponseEntity<List<ProfesorDTOs.AlumnoDeGrupo>> alumnosDelGrupo(@PathVariable Long grupoId) {
		return ResponseEntity.ok(profesor.alumnosDelGrupo(grupoId));
	}

	/** Ficha reducida de un alumno de sus grupos (§5.3). Fuera de sus grupos: 403; de baja: 404 (D7-13). */
	@GetMapping("/alumnos/{alumnoId}")
	@PreAuthorize("@acceso.alumnoDeMisGrupos(#alumnoId)")
	public ResponseEntity<ProfesorDTOs.Ficha> ficha(@PathVariable Long alumnoId) {
		return ResponseEntity.ok(profesor.ficha(alumnoId));
	}
}
