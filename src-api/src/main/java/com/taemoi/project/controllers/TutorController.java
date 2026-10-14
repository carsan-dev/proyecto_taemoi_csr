package com.taemoi.project.controllers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.taemoi.project.dtos.request.TutorRequest;
import com.taemoi.project.dtos.response.TutorDTO;
import com.taemoi.project.services.TutorService;

import jakarta.validation.Valid;

/** Tutores de un alumno de la escuela activa (administración). */
@RestController
@RequestMapping("/api/alumnos/{alumnoId}/tutores")
@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
public class TutorController {

	private final TutorService tutores;

	public TutorController(TutorService tutores) {
		this.tutores = tutores;
	}

	@GetMapping
	public List<TutorDTO> listar(@PathVariable Long alumnoId) {
		return tutores.listar(alumnoId);
	}

	@PostMapping
	public ResponseEntity<TutorDTO> crear(@PathVariable Long alumnoId, @Valid @RequestBody TutorRequest datos) {
		return ResponseEntity.status(HttpStatus.CREATED).body(tutores.crear(alumnoId, datos));
	}

	@PutMapping("/{tutorId}")
	public TutorDTO actualizar(@PathVariable Long alumnoId, @PathVariable Long tutorId,
			@Valid @RequestBody TutorRequest datos) {
		return tutores.actualizar(alumnoId, tutorId, datos);
	}

	@DeleteMapping("/{tutorId}")
	public ResponseEntity<Void> eliminar(@PathVariable Long alumnoId, @PathVariable Long tutorId) {
		tutores.eliminar(alumnoId, tutorId);
		return ResponseEntity.noContent().build();
	}
}
