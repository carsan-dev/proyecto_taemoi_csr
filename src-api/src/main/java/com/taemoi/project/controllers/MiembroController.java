package com.taemoi.project.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.taemoi.project.dtos.request.MiembrosRequests;
import com.taemoi.project.dtos.response.MiembrosDTOs;
import com.taemoi.project.services.AltaDeMiembro;
import com.taemoi.project.services.GestionMiembrosService;

/**
 * Miembros de la escuela activa (pieza E8-3; D8-1, D8-4 a D8-11): propietario y gestor listan el personal (y, si se
 * pide, las familias), ven una ficha, dan de alta cuentas existentes con un rol, dan, reactivan y quitan roles con su
 * cargo, y fijan las sedes de un responsable y los grupos de un profesor. Pieza E8-4: el alta de un email sin cuenta
 * (con nombre y apellidos) es una invitación por correo, que se puede reenviar.
 *
 * <p>Solo {@code ROLE_ADMIN} y {@code ROLE_MANAGER}: la regla de URL explícita de {@code SecurityConfiguration} (junto
 * a la de {@code /api/sedes}) y el {@code @PreAuthorize} de cada método; la D8 sobre PROPIETARIO y ADMINISTRADOR la
 * aplica el servicio (403). RESPONSABLE_SEDE (decisión 370), PROFESOR, FAMILIA y la sesión solo-plataforma, 403; fuera
 * de la lista blanca de la sesión solo-profesor. Errores con {@code {"mensaje"}}: 400 datos, 403 D8, 404 cuenta o grupo
 * de otra escuela o inexistente, 409 estado (rol desactivado, último propietario, sin el rol que hace falta).
 */
@RestController
@RequestMapping("/api/miembros")
public class MiembroController {

	private final GestionMiembrosService miembros;
	private final AltaDeMiembro altas;

	public MiembroController(GestionMiembrosService miembros, AltaDeMiembro altas) {
		this.miembros = miembros;
		this.altas = altas;
	}

	/** Una página del personal (con {@code incluirFamilias}, también las familias); {@code page} desde 0. */
	@GetMapping
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER')")
	public ResponseEntity<MiembrosDTOs.Pagina> listar(@RequestParam(required = false) String q,
			@RequestParam(required = false) String rol,
			@RequestParam(required = false, defaultValue = "false") boolean incluirFamilias,
			@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
		return ResponseEntity.ok(miembros.listar(q, rol, incluirFamilias, page, size));
	}

	@GetMapping("/{usuarioId}")
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER')")
	public ResponseEntity<MiembrosDTOs.Ficha> ficha(@PathVariable Long usuarioId) {
		return ResponseEntity.ok(miembros.ficha(usuarioId));
	}

	/**
	 * Alta de una cuenta existente (por email) con un rol; sin cuenta, con {@code nombre} y {@code apellidos}, la
	 * invitación (E8-4); sin ellos, 404.
	 */
	@PostMapping
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER')")
	public ResponseEntity<MiembrosDTOs.Ficha> alta(@RequestBody MiembrosRequests.Alta solicitud) {
		return ResponseEntity.ok(altas.alta(solicitud));
	}

	/** Reenvía la invitación pendiente (token nuevo; el anterior deja de valer). Sin cuerpo. */
	@PostMapping("/{usuarioId}/invitacion")
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER')")
	public ResponseEntity<MiembrosDTOs.Ficha> reenviarInvitacion(@PathVariable Long usuarioId) {
		return ResponseEntity.ok(miembros.reenviarInvitacion(usuarioId));
	}

	/** Da o reactiva el rol y fija su cargo ({@code {cargo?, sedeIds?}}, cuerpo opcional). */
	@PutMapping("/{usuarioId}/roles/{rol}")
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER')")
	public ResponseEntity<MiembrosDTOs.Ficha> darRol(@PathVariable Long usuarioId, @PathVariable String rol,
			@RequestBody(required = false) MiembrosRequests.Rol solicitud) {
		return ResponseEntity.ok(miembros.darRol(usuarioId, rol, solicitud));
	}

	/** Desactiva el rol (nunca borra la fila). */
	@DeleteMapping("/{usuarioId}/roles/{rol}")
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER')")
	public ResponseEntity<MiembrosDTOs.Ficha> quitarRol(@PathVariable Long usuarioId, @PathVariable String rol) {
		return ResponseEntity.ok(miembros.quitarRol(usuarioId, rol));
	}

	/** Reemplaza las sedes del responsable ({@code {sedeIds}}). */
	@PutMapping("/{usuarioId}/sedes")
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER')")
	public ResponseEntity<MiembrosDTOs.Ficha> reemplazarSedes(@PathVariable Long usuarioId,
			@RequestBody(required = false) MiembrosRequests.Sedes solicitud) {
		return ResponseEntity.ok(miembros.reemplazarSedes(usuarioId, solicitud));
	}

	/** Reemplaza los grupos del profesor en la escuela ({@code {asignaciones[{grupoId, rol}]}}). */
	@PutMapping("/{usuarioId}/grupos")
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER')")
	public ResponseEntity<MiembrosDTOs.Ficha> reemplazarGrupos(@PathVariable Long usuarioId,
			@RequestBody(required = false) MiembrosRequests.Grupos solicitud) {
		return ResponseEntity.ok(miembros.reemplazarGrupos(usuarioId, solicitud));
	}
}
