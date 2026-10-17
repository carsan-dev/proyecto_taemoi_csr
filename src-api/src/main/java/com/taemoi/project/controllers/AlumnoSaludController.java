package com.taemoi.project.controllers;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.dtos.request.AlumnoSaludRequest;
import com.taemoi.project.dtos.response.AlumnoSaludDTO;
import com.taemoi.project.services.AlumnoSaludService;

/**
 * Datos de salud de un alumno. Por ahora solo propietario y administración (ROLE_ADMIN/ROLE_MANAGER);
 * profesores en la fase 3. Respuestas con {@code Cache-Control: no-store}.
 *
 * <p>Entrega 6-1c: cada método lleva su {@code @PreAuthorize} (gestión, o responsable de sede por
 * {@code @acceso.alumnoEnSede}: leer por D9, editar por la sede principal, D6-3). El de la clase queda como
 * valor por defecto (solo gestión) para un método nuevo sin anotación.
 */
@RestController
@RequestMapping("/api/alumnos/{alumnoId}/salud")
@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
public class AlumnoSaludController {

	private final AlumnoSaludService salud;
	private final ObjectMapper json;

	public AlumnoSaludController(AlumnoSaludService salud, ObjectMapper json) {
		this.salud = salud;
		this.json = json;
	}

	@GetMapping
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER','ROLE_RESPONSABLE_SEDE') and @acceso.alumnoEnSede(#alumnoId,'LEER')")
	public ResponseEntity<AlumnoSaludDTO> obtener(@PathVariable Long alumnoId) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(salud.obtener(alumnoId));
	}

	@PutMapping
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER','ROLE_RESPONSABLE_SEDE') and @acceso.alumnoEnSede(#alumnoId,'EDITAR')")
	public ResponseEntity<AlumnoSaludDTO> guardar(@PathVariable Long alumnoId, @RequestBody String cuerpo) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(salud.guardar(alumnoId, leer(cuerpo)));
	}

	/**
	 * Se lee el cuerpo como texto y se convierte aquí: un error de Jackson (p. ej. "asma" en un booleano)
	 * llevaría el valor rechazado al log de Spring (DefaultHandlerExceptionResolver). El error que sale de
	 * aquí no lo incluye ni encadena la causa.
	 */
	private AlumnoSaludRequest leer(String cuerpo) {
		try {
			AlumnoSaludRequest datos = json.readValue(cuerpo, AlumnoSaludRequest.class);
			if (datos == null) {
				throw new IllegalArgumentException("Faltan los datos de salud.");
			}
			return datos;
		} catch (JsonProcessingException e) {
			throw new IllegalArgumentException("Los datos de salud no tienen un formato válido.");
		}
	}
}
