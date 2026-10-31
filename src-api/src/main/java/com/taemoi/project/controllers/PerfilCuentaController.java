package com.taemoi.project.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.taemoi.project.dtos.request.PerfilCuentaRequest;
import com.taemoi.project.dtos.response.PerfilCuentaDTO;
import com.taemoi.project.services.PerfilCuentaService;

/**
 * Perfil de la cuenta de la sesión (decisión 322): sin ningún id en la ruta ni en el cuerpo.
 *
 * <p>Regla de URL propia ({@code hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER','ROLE_RESPONSABLE_SEDE')}, pieza 9-5; fuera
 * de la lista blanca de la sesión solo-profesor) y {@code @PreAuthorize} por método. Errores con {@code {"mensaje"}}: 400 datos, 403 sin
 * permiso.
 */
@RestController
@RequestMapping("/api/panel/mi-perfil")
public class PerfilCuentaController {

	private final PerfilCuentaService perfil;

	public PerfilCuentaController(PerfilCuentaService perfil) {
		this.perfil = perfil;
	}

	@GetMapping
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER','ROLE_RESPONSABLE_SEDE')")
	public ResponseEntity<PerfilCuentaDTO> obtener() {
		return ResponseEntity.ok(perfil.obtener());
	}

	/** Sustituye {@code {nombrePreferido, cumpleDia, cumpleMes}} y devuelve lo mismo que el GET. */
	@PutMapping
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER','ROLE_RESPONSABLE_SEDE')")
	public ResponseEntity<PerfilCuentaDTO> guardar(@RequestBody(required = false) PerfilCuentaRequest solicitud) {
		return ResponseEntity.ok(perfil.guardar(solicitud));
	}
}
