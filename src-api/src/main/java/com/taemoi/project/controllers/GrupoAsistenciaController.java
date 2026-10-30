package com.taemoi.project.controllers;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.taemoi.project.dtos.request.GuardarAsistenciaRequest;
import com.taemoi.project.dtos.response.AsistenciaDTOs;
import com.taemoi.project.services.AsistenciaService;
import com.taemoi.project.services.AsistenciaService.Via;

/**
 * Pasar lista desde gestión (decisión 324, fase3-pasar-lista.md): las mismas rutas y el mismo servicio que el profesor
 * bajo {@code /api/profesor/grupos/{grupoId}}, sin límite de días hacia atrás al guardar.
 *
 * <p>Propietario y gestor, y el responsable en los grupos de sus sedes: las reglas de URL de {@code SecurityConfiguration}
 * (constantes {@code RESPONSABLE_SEDE_*}) y {@code @acceso.grupoEnSede} en cada método, como el resto de
 * {@code GrupoController}. Con {@code grupoEnSede} y no con {@code grupo}, que además concede {@code LEER} por los grupos
 * del profesor: un responsable que da clase en otra sede no entra por aquí. La sesión solo-profesor queda fuera en la URL.
 * Un grupo de otra escuela o inexistente: 404 para quien tiene alcance total; 403 para el responsable.
 */
@RestController
@RequestMapping("/api/grupos/{grupoId}")
public class GrupoAsistenciaController {

	private final AsistenciaService asistencia;

	public GrupoAsistenciaController(AsistenciaService asistencia) {
		this.asistencia = asistencia;
	}

	/** Clases de hoy, recientes y próxima del grupo. */
	@GetMapping("/clases")
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER','ROLE_RESPONSABLE_SEDE') and @acceso.grupoEnSede(#grupoId,'LEER')")
	public ResponseEntity<AsistenciaDTOs.Clases> clases(@PathVariable @NonNull Long grupoId) {
		return ResponseEntity.ok(asistencia.clases(Via.GESTION, grupoId));
	}

	/** Lista de una clase. Turno de otro grupo: 404. */
	@GetMapping("/clases/{turnoId}/{fecha}")
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER','ROLE_RESPONSABLE_SEDE') and @acceso.grupoEnSede(#grupoId,'LEER')")
	public ResponseEntity<AsistenciaDTOs.ListaDeClase> lista(@PathVariable @NonNull Long grupoId,
			@PathVariable Long turnoId, @PathVariable String fecha) {
		return ResponseEntity.ok(asistencia.lista(Via.GESTION, grupoId, turnoId, fecha));
	}

	/** Guarda la lista de una clase. 400: fecha o alumnos no válidos; 404: turno de otro grupo. */
	@PutMapping("/clases/{turnoId}/{fecha}")
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER','ROLE_RESPONSABLE_SEDE') and @acceso.grupoEnSede(#grupoId,'EDITAR')")
	public ResponseEntity<AsistenciaDTOs.Clase> guardar(@PathVariable @NonNull Long grupoId, @PathVariable Long turnoId,
			@PathVariable String fecha, @RequestBody GuardarAsistenciaRequest cuerpo) {
		return ResponseEntity
				.ok(asistencia.guardar(Via.GESTION, grupoId, turnoId, fecha, cuerpo == null ? null : cuerpo.presentes()));
	}

	/** Presentes y clases del mes por alumno. Año o mes no válidos: 400. */
	@GetMapping("/asistencia/resumen")
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER','ROLE_RESPONSABLE_SEDE') and @acceso.grupoEnSede(#grupoId,'LEER')")
	public ResponseEntity<List<AsistenciaDTOs.ResumenAlumno>> resumen(@PathVariable @NonNull Long grupoId,
			@RequestParam int year, @RequestParam int month) {
		return ResponseEntity.ok(asistencia.resumen(Via.GESTION, grupoId, year, month));
	}

	/** Detalle del mes de un alumno del grupo. Alumno de fuera del grupo: 404. */
	@GetMapping("/alumnos/{alumnoId}/asistencia")
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER','ROLE_RESPONSABLE_SEDE') and @acceso.grupoEnSede(#grupoId,'LEER')")
	public ResponseEntity<AsistenciaDTOs.AsistenciaDeAlumno> asistenciaDeAlumno(@PathVariable @NonNull Long grupoId,
			@PathVariable Long alumnoId, @RequestParam int year, @RequestParam int month) {
		return ResponseEntity.ok(asistencia.asistenciaDeAlumno(Via.GESTION, grupoId, alumnoId, year, month));
	}
}
