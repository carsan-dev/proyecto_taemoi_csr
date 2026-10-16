package com.taemoi.project.controllers;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.taemoi.project.dtos.response.AuditoriaEventoDTO;
import com.taemoi.project.dtos.response.AuditoriaEventoDetalleDTO;
import com.taemoi.project.plataforma.AutoridadPlataforma;
import com.taemoi.project.services.AuditoriaService;
import com.taemoi.project.services.VistaAuditoria;

/**
 * Registro de la escuela activa. El propietario ({@code ROLE_ADMIN}) ve su «registro de acciones», filtrado en el
 * servidor (D2 de docs/multi-escuela/fase3-sedes-permisos.md, {@link VistaAuditoria#PROPIETARIO}); si la sesión
 * es además de administrador de plataforma, el registro completo de la escuela, como antes.
 */
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
			@RequestParam(defaultValue = "25") Integer size,
			@RequestParam(defaultValue = "false") Boolean incluirRuido,
			Authentication autenticacion) {
		return ResponseEntity.ok(auditoriaService.obtenerEventos(
				vista(autenticacion),
				desde,
				hasta,
				resultado,
				accion,
				modulo,
				usuario,
				endpoint,
				texto,
				page,
				size,
				incluirRuido));
	}

	@GetMapping("/eventos/{id}")
	public ResponseEntity<AuditoriaEventoDetalleDTO> obtenerEventoPorId(@PathVariable Long id,
			Authentication autenticacion) {
		return ResponseEntity.ok(auditoriaService.obtenerEventoPorId(vista(autenticacion), id));
	}

	@GetMapping("/modulos")
	public ResponseEntity<List<String>> obtenerModulos(Authentication autenticacion) {
		return ResponseEntity.ok(auditoriaService.obtenerModulosDisponibles(vista(autenticacion)));
	}

	/** Registro completo solo con la autoridad de plataforma; en cualquier otro caso, el del propietario. */
	private static VistaAuditoria vista(Authentication autenticacion) {
		if (autenticacion != null && autenticacion.getAuthorities() != null) {
			for (GrantedAuthority autoridad : autenticacion.getAuthorities()) {
				if (AutoridadPlataforma.ADMIN_PLATAFORMA.equals(autoridad.getAuthority())) {
					return VistaAuditoria.COMPLETA;
				}
			}
		}
		return VistaAuditoria.PROPIETARIO;
	}
}
