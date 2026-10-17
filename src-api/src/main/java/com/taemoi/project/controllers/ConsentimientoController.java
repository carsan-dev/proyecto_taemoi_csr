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
import org.springframework.web.bind.annotation.RestController;

import com.taemoi.project.dtos.request.ConsentimientoRequest;
import com.taemoi.project.dtos.response.ConsentimientoDTO;
import com.taemoi.project.services.ConsentimientoService;

import jakarta.validation.Valid;

/**
 * Consentimientos de un alumno: se listan y se registran (nunca se editan ni se borran).
 *
 * <p>Entrega 6-1c: cada método lleva su {@code @PreAuthorize} (gestión, o responsable de sede por
 * {@code @acceso.alumnoEnSede}: leer por D9, editar por la sede principal, D6-3). El de la clase queda como
 * valor por defecto (solo gestión) para un método nuevo sin anotación.
 */
@RestController
@RequestMapping("/api/alumnos/{alumnoId}/consentimientos")
@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
public class ConsentimientoController {

	private final ConsentimientoService consentimientos;

	public ConsentimientoController(ConsentimientoService consentimientos) {
		this.consentimientos = consentimientos;
	}

	@GetMapping
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER','ROLE_RESPONSABLE_SEDE') and @acceso.alumnoEnSede(#alumnoId,'LEER')")
	public List<ConsentimientoDTO> listar(@PathVariable Long alumnoId) {
		return consentimientos.listar(alumnoId);
	}

	@PostMapping
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER','ROLE_RESPONSABLE_SEDE') and @acceso.alumnoEnSede(#alumnoId,'EDITAR')")
	public ResponseEntity<ConsentimientoDTO> registrar(@PathVariable Long alumnoId,
			@Valid @RequestBody ConsentimientoRequest datos) {
		return ResponseEntity.status(HttpStatus.CREATED).body(consentimientos.registrar(alumnoId, datos));
	}
}
