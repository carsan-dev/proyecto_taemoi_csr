package com.taemoi.project.controllers;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.taemoi.project.dtos.request.AsignarProfesorRequest;
import com.taemoi.project.dtos.response.GrupoProfesoresDTOs;
import com.taemoi.project.services.AsignacionProfesorService;

/**
 * Profesores de un grupo (pieza E7-4; decisión 208, D7-19 a D7-21): propietario y gestor los ven, asignan una cuenta
 * existente por email como titular o ayudante (o le cambian el rol) y la quitan.
 *
 * <p>Solo {@code ROLE_ADMIN} y {@code ROLE_MANAGER}: la regla de URL explícita de {@code SecurityConfiguration} (antes
 * de las del responsable de sede) y el {@code @PreAuthorize} de cada método. RESPONSABLE_SEDE (hasta la E9), PROFESOR y
 * FAMILIA, 403; fuera de la lista blanca de la sesión solo-profesor. El servicio lee el grupo acotado por escuela (de
 * otra escuela o inexistente, 404) y filtra {@code asignacion_profesor} y {@code membresia} por la escuela activa.
 */
@RestController
@RequestMapping("/api/grupos/{grupoId}/profesores")
public class GrupoProfesoresController {

	private final AsignacionProfesorService asignaciones;

	public GrupoProfesoresController(AsignacionProfesorService asignaciones) {
		this.asignaciones = asignaciones;
	}

	/** Profesores del grupo, titulares primero; {@code []} si no tiene. */
	@GetMapping
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER')")
	public ResponseEntity<List<GrupoProfesoresDTOs.Profesor>> listar(@PathVariable Long grupoId) {
		return ResponseEntity.ok(asignaciones.listar(grupoId));
	}

	/**
	 * Asigna la cuenta del email con ese rol o le cambia el rol (200 con el profesor). 400: email vacío o rol que no es
	 * TITULAR/AYUDANTE; 404: grupo ajeno o email sin cuenta ({@link AsignacionProfesorService#EMAIL_SIN_CUENTA}); 409:
	 * membresía PROFESOR desactivada ({@link AsignacionProfesorService#PROFESOR_DESACTIVADO}).
	 */
	@PostMapping
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER')")
	public ResponseEntity<GrupoProfesoresDTOs.Profesor> asignar(@PathVariable Long grupoId,
			@RequestBody AsignarProfesorRequest cuerpo) {
		return ResponseEntity.ok(asignaciones.asignar(grupoId, cuerpo == null ? null : cuerpo.email(),
				cuerpo == null ? null : cuerpo.rol()));
	}

	/** Quita la asignación (204); la membresía PROFESOR queda. 404 si el grupo es ajeno o la cuenta no está asignada. */
	@DeleteMapping("/{usuarioId}")
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER')")
	public ResponseEntity<Void> quitar(@PathVariable Long grupoId, @PathVariable Long usuarioId) {
		asignaciones.quitar(grupoId, usuarioId);
		return ResponseEntity.noContent().build();
	}
}
