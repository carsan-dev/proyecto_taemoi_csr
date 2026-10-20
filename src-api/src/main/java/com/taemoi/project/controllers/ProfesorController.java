package com.taemoi.project.controllers;

import java.io.IOException;
import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.taemoi.project.dtos.request.ProfesorRequests;
import com.taemoi.project.dtos.response.AlumnoSaludDTO;
import com.taemoi.project.dtos.response.ProfesorDTOs;
import com.taemoi.project.services.ProfesorService;

/**
 * Rutas propias del profesor (entrega 7, fase3-e7-profesor.md §6.2; E7-1 y E7-2: lectura; E7-3: apto, fecha de grado
 * y pases).
 *
 * <p>Tres capas (§6.3): la lista blanca de la sesión solo-profesor ({@code SesionSoloProfesorAuthorizationManager},
 * {@code /api/profesor/**}), la regla de URL ({@code /api/profesor/**} → {@code ROLE_PROFESOR}) y el
 * {@code @PreAuthorize} de cada método, que nunca admite la gestión (D7-10): {@code ROLE_PROFESOR} sin
 * {@code ROLE_ADMIN} ni {@code ROLE_MANAGER}, o {@code @acceso.*DeMisGrupos} (solo la rama de profesor, que ya exige
 * alcance no total). El servicio filtra por {@code alcance.grupos()} y nada más (D7-11). Ninguna ruta de gestión se
 * abre al profesor (D7-9).
 */
@RestController
@RequestMapping("/api/profesor")
public class ProfesorController {

	private final ProfesorService profesor;

	public ProfesorController(ProfesorService profesor) {
		this.profesor = profesor;
	}

	/** Sus grupos (§5.1). */
	@GetMapping("/grupos")
	@PreAuthorize("hasAuthority('ROLE_PROFESOR') and !hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGER')")
	public ResponseEntity<List<ProfesorDTOs.Grupo>> misGrupos() {
		return ResponseEntity.ok(profesor.misGrupos());
	}

	/** Alumnos activos de un grupo suyo (§5.2). Grupo ajeno, de otra escuela o inexistente: 403. */
	@GetMapping("/grupos/{grupoId}/alumnos")
	@PreAuthorize("@acceso.grupoDeMisGrupos(#grupoId)")
	public ResponseEntity<List<ProfesorDTOs.AlumnoDeGrupo>> alumnosDelGrupo(@PathVariable Long grupoId) {
		return ResponseEntity.ok(profesor.alumnosDelGrupo(grupoId));
	}

	/** Ficha reducida de un alumno de sus grupos (§5.3). Fuera de sus grupos: 403; de baja: 404 (D7-13). */
	@GetMapping("/alumnos/{alumnoId}")
	@PreAuthorize("@acceso.alumnoDeMisGrupos(#alumnoId)")
	public ResponseEntity<ProfesorDTOs.Ficha> ficha(@PathVariable Long alumnoId) {
		return ResponseEntity.ok(profesor.ficha(alumnoId));
	}

	/**
	 * Salud de un alumno de sus grupos, a petición (E7-2, D7-14): mismo DTO que gestión, sin caché ({@code no-store});
	 * cada lectura queda registrada (módulo {@code salud}). Fuera de sus grupos: 403; de baja: 404. Sin edición.
	 */
	@GetMapping("/alumnos/{alumnoId}/salud")
	@PreAuthorize("@acceso.alumnoDeMisGrupos(#alumnoId)")
	public ResponseEntity<AlumnoSaludDTO> salud(@PathVariable Long alumnoId) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(profesor.salud(alumnoId));
	}

	/**
	 * Hoja de asistencia de un grupo suyo para un mes (E7-2, D7-15): el PDF de siempre, solo con los turnos y los alumnos
	 * activos de ese grupo. Grupo ajeno: 403; año o mes no válidos: 400. Como el PDF de gestión, en adjunto.
	 */
	@GetMapping("/grupos/{grupoId}/asistencia")
	@PreAuthorize("@acceso.grupoDeMisGrupos(#grupoId)")
	public ResponseEntity<byte[]> asistencia(@PathVariable Long grupoId, @RequestParam int year,
			@RequestParam int month) throws IOException {
		byte[] pdf = profesor.asistencia(grupoId, year, month);
		String fichero = "Asistencia-grupo-%d-%d-%02d.pdf".formatted(grupoId, year, month);
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fichero + "\"").body(pdf);
	}

	// ------------------------------------------------------------------ grados (E7-3, decisión 207)
	// Solo apto, fecha de grado y pases, en las disciplinas de los grupos que comparte con el alumno; sin «Cambiar
	// grado» (no hay PUT .../grado). Fuera de sus grupos o de esas disciplinas: 403; de baja: 404; validaciones de
	// gestión: 400 con su mensaje; segundo pase pendiente: 409 (D7-17).

	/** Marca o quita el apto (D7-16). Devuelve el deporte como lo pinta la ficha. */
	@PutMapping("/alumnos/{alumnoId}/deportes/{deporte}/apto")
	@PreAuthorize("@acceso.alumnoDeMisGrupos(#alumnoId)")
	public ResponseEntity<ProfesorDTOs.DeporteDelAlumno> actualizarApto(@PathVariable Long alumnoId,
			@PathVariable String deporte, @RequestBody ProfesorRequests.Apto peticion) {
		return ResponseEntity.ok(profesor.actualizarApto(alumnoId, deporte, peticion.aptoParaExamen()));
	}

	/** Corrige la fecha de grado ({@code yyyy-MM-dd}); recalcula el apto como gestión. */
	@PutMapping("/alumnos/{alumnoId}/deportes/{deporte}/fecha-grado")
	@PreAuthorize("@acceso.alumnoDeMisGrupos(#alumnoId)")
	public ResponseEntity<ProfesorDTOs.DeporteDelAlumno> actualizarFechaGrado(@PathVariable Long alumnoId,
			@PathVariable String deporte, @RequestBody ProfesorRequests.FechaGrado peticion) {
		return ResponseEntity.ok(profesor.actualizarFechaGrado(alumnoId, deporte, peticion.fechaGrado()));
	}

	/** Pase con derecho de examen: crea el cargo pendiente; el grado sube al cobrarlo en Mi caja (D7-6). */
	@PostMapping("/alumnos/{alumnoId}/deportes/{deporte}/pase-examen")
	@PreAuthorize("@acceso.alumnoDeMisGrupos(#alumnoId)")
	public ResponseEntity<ProfesorDTOs.PaseCreado> paseExamen(@PathVariable Long alumnoId, @PathVariable String deporte,
			@RequestBody(required = false) ProfesorRequests.Pase peticion) {
		return ResponseEntity.ok(profesor.pasarGrado(alumnoId, deporte, rojoBordado(peticion), false));
	}

	/** Pase por recompensa: crea el cargo pendiente; el grado sube al cobrarlo en Mi caja (D7-6). */
	@PostMapping("/alumnos/{alumnoId}/deportes/{deporte}/pase-recompensa")
	@PreAuthorize("@acceso.alumnoDeMisGrupos(#alumnoId)")
	public ResponseEntity<ProfesorDTOs.PaseCreado> paseRecompensa(@PathVariable Long alumnoId,
			@PathVariable String deporte, @RequestBody(required = false) ProfesorRequests.Pase peticion) {
		return ResponseEntity.ok(profesor.pasarGrado(alumnoId, deporte, rojoBordado(peticion), true));
	}

	private static boolean rojoBordado(ProfesorRequests.Pase peticion) {
		return peticion != null && Boolean.TRUE.equals(peticion.rojoBordado());
	}
}
