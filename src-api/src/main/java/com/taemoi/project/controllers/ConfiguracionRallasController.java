package com.taemoi.project.controllers;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.taemoi.project.dtos.response.RallasDTOs;
import com.taemoi.project.services.RallasService;

/**
 * Configuración de las rallas hacia el examen (decisión 510): un interruptor por disciplina de la escuela, apagado por
 * defecto. Como el resto de la configuración de la escuela ({@code /api/admin/configuracion/limite-turno}), solo el
 * propietario ({@code ROLE_ADMIN}, regla de URL de {@code /api/admin/**} y {@code @PreAuthorize}). Escuela activa por
 * {@code @TenantId}.
 */
@RestController
@RequestMapping("/api/admin/configuracion/rallas")
public class ConfiguracionRallasController {

	private final RallasService rallas;

	public ConfiguracionRallasController(RallasService rallas) {
		this.rallas = rallas;
	}

	/** Las disciplinas activas con sistema de grados, por orden, con su interruptor. */
	@GetMapping
	@PreAuthorize("hasAuthority('ROLE_ADMIN')")
	public ResponseEntity<List<RallasDTOs.Disciplina>> configuracion() {
		return ResponseEntity.ok(rallas.configuracion());
	}

	/**
	 * Cambia los interruptores indicados ({@code {"TAEKWONDO": true, "KICKBOXING": false}}); los demás no cambian.
	 * Todo o nada: vacío, una disciplina que no está en la lista o un valor nulo, 400. Desactivar no borra las rallas de
	 * nadie. Devuelve la lista completa.
	 */
	@PutMapping
	@PreAuthorize("hasAuthority('ROLE_ADMIN')")
	public ResponseEntity<List<RallasDTOs.Disciplina>> cambiar(@RequestBody(required = false) Map<String, Boolean> cambios) {
		return ResponseEntity.ok(rallas.cambiarConfiguracion(cambios));
	}
}
