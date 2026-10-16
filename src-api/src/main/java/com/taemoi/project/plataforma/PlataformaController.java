package com.taemoi.project.plataforma;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Configuración y consultas de plataforma (todas las escuelas). Protegido dos veces con la misma autoridad: la
 * regla de {@code /api/plataforma/**} de {@code SecurityConfiguration} y {@code @PreAuthorize} aquí.
 */
@RestController
@RequestMapping("/api/plataforma")
@PreAuthorize("hasAuthority('ROLE_ADMIN_PLATAFORMA')")
public class PlataformaController {

	private final ErroresPlataformaService errores;

	public PlataformaController(ErroresPlataformaService errores) {
		this.errores = errores;
	}

	/** Errores técnicos (4xx/5xx o sin sesión) de todas las escuelas, del más reciente al más antiguo. */
	@GetMapping("/errores")
	public ResponseEntity<Page<ErrorPlataformaDTO>> obtenerErrores(
			@RequestParam(required = false) Long organizacionId,
			@RequestParam(required = false) String estado,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
			@RequestParam(defaultValue = "1") Integer page,
			@RequestParam(defaultValue = "25") Integer size) {
		return ResponseEntity.ok(errores.buscar(organizacionId, estado, desde, hasta, page, size));
	}

	/** Todas las escuelas (id y nombre), ordenadas por nombre: el filtro de escuela de la pantalla de errores. */
	@GetMapping("/escuelas")
	public ResponseEntity<List<EscuelaPlataformaDTO>> obtenerEscuelas() {
		return ResponseEntity.ok(errores.escuelas());
	}
}
