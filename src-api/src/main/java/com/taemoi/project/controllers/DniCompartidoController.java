package com.taemoi.project.controllers;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.taemoi.project.dtos.request.AplicarDniCompartidoRequest;
import com.taemoi.project.dtos.response.DniCompartidoDTO;
import com.taemoi.project.dtos.response.DniCompartidoResultadoDTO;
import com.taemoi.project.services.DniCompartidoService;

import jakarta.validation.Valid;

/** Informe y corrección asistida de DNIs compartidos por varios alumnos (fase 2, multi-escuela). */
@RestController
@RequestMapping("/api/admin/dni-compartidos")
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class DniCompartidoController {

	private final DniCompartidoService servicio;

	public DniCompartidoController(DniCompartidoService servicio) {
		this.servicio = servicio;
	}

	@GetMapping
	public ResponseEntity<List<DniCompartidoDTO>> listar() {
		return ResponseEntity.ok().header("Cache-Control", "no-store").body(servicio.listar());
	}

	@PostMapping("/aplicar")
	public ResponseEntity<DniCompartidoResultadoDTO> aplicar(@Valid @RequestBody AplicarDniCompartidoRequest datos) {
		return ResponseEntity.ok().header("Cache-Control", "no-store").body(servicio.aplicar(datos));
	}
}
