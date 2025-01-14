package com.taemoi.project.controladores;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
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

import com.taemoi.project.dtos.ConvocatoriaDTO;
import com.taemoi.project.dtos.response.AlumnoConvocatoriaDTO;
import com.taemoi.project.entidades.Deporte;
import com.taemoi.project.servicios.ConvocatoriaService;

@RestController
@RequestMapping("/api/convocatorias")
public class ConvocatoriaController {

	@Autowired
	private ConvocatoriaService convocatoriaService;

	@GetMapping
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<List<ConvocatoriaDTO>> obtenerConvocatorias(@RequestParam(required = false) Deporte deporte) {
		List<ConvocatoriaDTO> convocatorias;
		if (deporte != null) {
			convocatorias = convocatoriaService.obtenerConvocatoriasPorDeporte(deporte);
		} else {
			convocatorias = convocatoriaService.obtenerConvocatorias();
		}
		convocatorias.sort((c1, c2) -> c2.getFechaConvocatoria().compareTo(c1.getFechaConvocatoria()));
		return ResponseEntity.ok(convocatorias);
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<ConvocatoriaDTO> obtenerConvocatoriaPorId(@PathVariable Long id) {
		ConvocatoriaDTO convocatoria = convocatoriaService.obtenerConvocatoriaPorId(id);
		return ResponseEntity.ok(convocatoria);
	}

	@GetMapping("/alumnos/{alumnoId}")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<List<ConvocatoriaDTO>> obtenerConvocatoriasDeAlumno(@PathVariable Long alumnoId) {
		List<ConvocatoriaDTO> convocatorias = convocatoriaService.obtenerConvocatoriasDeAlumno(alumnoId);
		return ResponseEntity.ok(convocatorias);
	}

	@PostMapping
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<ConvocatoriaDTO> crearConvocatoria(@RequestBody ConvocatoriaDTO convocatoriaDTO) {
		ConvocatoriaDTO nuevaConvocatoria = convocatoriaService.crearConvocatoria(convocatoriaDTO);
		return ResponseEntity.status(HttpStatus.CREATED).body(nuevaConvocatoria);
	}

	@GetMapping("/{id}/alumnos")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<List<AlumnoConvocatoriaDTO>> obtenerAlumnosDeConvocatoria(@PathVariable Long id) {
		List<AlumnoConvocatoriaDTO> alumnos = convocatoriaService.obtenerAlumnosDeConvocatoria(id);
		return ResponseEntity.ok(alumnos);
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<Void> eliminarConvocatoria(@PathVariable Long id) {
		convocatoriaService.eliminarConvocatoria(id);
		return ResponseEntity.noContent().build();
	}

	@PutMapping("/{convocatoriaId}/actualizar-grados")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<Void> actualizarGrados(@PathVariable Long convocatoriaId) {
		convocatoriaService.actualizarGradosDeConvocatoria(convocatoriaId);
		return ResponseEntity.ok().build();
	}

	@PutMapping("/alumno/{alumnoConvocatoriaId}")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<Void> actualizarAlumnoConvocatoria(@PathVariable Long alumnoConvocatoriaId,
			@RequestBody AlumnoConvocatoriaDTO alumnoConvocatoriaDTO) {
		convocatoriaService.actualizarAlumnoConvocatoria(alumnoConvocatoriaId, alumnoConvocatoriaDTO);
		return ResponseEntity.ok().build();
	}
}
