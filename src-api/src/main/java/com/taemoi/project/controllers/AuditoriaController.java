package com.taemoi.project.controllers;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.taemoi.project.dtos.response.AuditoriaEventoDTO;
import com.taemoi.project.dtos.response.AuditoriaEventoDetalleDTO;
import com.taemoi.project.services.AuditoriaService;

@RestController
@RequestMapping("/api/admin/auditoria")
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class AuditoriaController {

	private final AuditoriaService auditoriaService;

	public AuditoriaController(AuditoriaService auditoriaService) {
		this.auditoriaService = auditoriaService;
	}

	@GetMapping("/eventos")
	public ResponseEntity<Page<AuditoriaEventoDTO>> obtenerEventos(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
			@RequestParam(required = false) String resultado,
			@RequestParam(required = false) String accion,
			@RequestParam(required = false) String modulo,
			@RequestParam(required = false) String usuario,
			@RequestParam(required = false) String endpoint,
			@RequestParam(required = false) String texto,
			@RequestParam(defaultValue = "1") Integer page,
			@RequestParam(defaultValue = "25") Integer size) {
		return ResponseEntity.ok(auditoriaService.obtenerEventos(
				desde,
				hasta,
				resultado,
				accion,
				modulo,
				usuario,
				endpoint,
				texto,
				page,
				size));
	}

	@GetMapping("/eventos/{id}")
	public ResponseEntity<AuditoriaEventoDetalleDTO> obtenerEventoPorId(@PathVariable Long id) {
		return ResponseEntity.ok(auditoriaService.obtenerEventoPorId(id));
	}

	@GetMapping("/modulos")
	public ResponseEntity<List<String>> obtenerModulos() {
		return ResponseEntity.ok(auditoriaService.obtenerModulosDisponibles());
	}
}
