package com.taemoi.project.controllers;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

import jakarta.validation.Valid;

import com.taemoi.project.dtos.ConvocatoriaDTO;
import com.taemoi.project.dtos.response.AlumnoConvocatoriaDTO;
import com.taemoi.project.dtos.response.AlumnoConvocatoriaReporteDTO;
import com.taemoi.project.services.ConvocatoriaService;
import com.taemoi.project.services.PDFService;

/**
 * Convocatorias de examen. Entrega 6-2 (D6-8): son comunes a la escuela; el responsable de sede ve la lista y, dentro,
 * solo a sus alumnos (D9, filtrado en el servicio), y marca la cuantía y el pago del examen de los alumnos de su sede
 * principal ({@code @acceso.alumnoConvocatoriaEnSede}). Crear, borrar y actualizar grados siguen siendo de gestión.
 */
@RestController
@RequestMapping("/api/convocatorias")
public class ConvocatoriaController {

	@Autowired
	private ConvocatoriaService convocatoriaService;

	@Autowired
	private com.taemoi.project.services.CatalogoDisciplinasService catalogoDisciplinas;

	@Autowired
	private PDFService pdfService;

	@GetMapping
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER','ROLE_RESPONSABLE_SEDE')")
	public ResponseEntity<List<ConvocatoriaDTO>> obtenerConvocatorias(
			@RequestParam(name = "deporte", required = false) String deporteParametro) {
		// Antes el enum Deporte (Spring respondía 400 a un valor no válido); ahora, el catálogo de la escuela.
		String deporte = catalogoDisciplinas.validarParametro(deporteParametro);
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
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER','ROLE_RESPONSABLE_SEDE')")
	public ResponseEntity<ConvocatoriaDTO> obtenerConvocatoriaPorId(@PathVariable Long id) {
		ConvocatoriaDTO convocatoria = convocatoriaService.obtenerConvocatoriaPorId(id);
		return ResponseEntity.ok(convocatoria);
	}

	@GetMapping("/alumnos/{alumnoId}")
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER','ROLE_USER','ROLE_RESPONSABLE_SEDE') and @acceso.alumnoComoFamiliaOSede(#alumnoId)")
	public ResponseEntity<List<ConvocatoriaDTO>> obtenerConvocatoriasDeAlumno(@PathVariable Long alumnoId) {
		List<ConvocatoriaDTO> convocatorias = convocatoriaService.obtenerConvocatoriasDeAlumno(alumnoId);
		return ResponseEntity.ok(convocatorias);
	}

	@PostMapping
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<ConvocatoriaDTO> crearConvocatoria(@Valid @RequestBody ConvocatoriaDTO convocatoriaDTO) {
		ConvocatoriaDTO nuevaConvocatoria = convocatoriaService.crearConvocatoria(convocatoriaDTO);
		return ResponseEntity.status(HttpStatus.CREATED).body(nuevaConvocatoria);
	}

	@GetMapping("/{id}/alumnos")
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER','ROLE_RESPONSABLE_SEDE')")
	public ResponseEntity<List<AlumnoConvocatoriaDTO>> obtenerAlumnosDeConvocatoria(@PathVariable Long id) {
		List<AlumnoConvocatoriaDTO> alumnos = convocatoriaService.obtenerAlumnosDeConvocatoria(id);
		return ResponseEntity.ok(alumnos);
	}

	@GetMapping("/{id}/reporte")
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER','ROLE_RESPONSABLE_SEDE')")
	public ResponseEntity<List<AlumnoConvocatoriaReporteDTO>> obtenerReporteDeConvocatoria(@PathVariable Long id) {
		List<AlumnoConvocatoriaReporteDTO> reporte = convocatoriaService.obtenerReporteDeConvocatoria(id);
		return ResponseEntity.ok(reporte);
	}

	@GetMapping("/{id}/informe-pdf")
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER','ROLE_RESPONSABLE_SEDE')")
	public ResponseEntity<byte[]> generarInformePDFConvocatoria(@PathVariable Long id) {
		byte[] pdfBytes = pdfService.generarInformeConvocatoria(id);

		// Get convocatoria to build filename
		ConvocatoriaDTO convocatoria = convocatoriaService.obtenerConvocatoriaPorId(id);
		String fechaStr = convocatoria.getFechaConvocatoria().toString().replaceAll("-", "_");
		String filename = String.format("informe_convocatoria_%s_%s.pdf",
				convocatoria.getDeporte(), fechaStr);

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_PDF);
		headers.setContentDisposition(
				ContentDisposition.builder("attachment").filename(filename).build());

		return ResponseEntity.ok().headers(headers).body(pdfBytes);
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
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER','ROLE_RESPONSABLE_SEDE') and @acceso.alumnoConvocatoriaEnSede(#alumnoConvocatoriaId,'EDITAR')")
	public ResponseEntity<Void> actualizarAlumnoConvocatoria(@PathVariable Long alumnoConvocatoriaId,
			@Valid @RequestBody AlumnoConvocatoriaDTO alumnoConvocatoriaDTO) {
		convocatoriaService.actualizarAlumnoConvocatoria(alumnoConvocatoriaId, alumnoConvocatoriaDTO);
		return ResponseEntity.ok().build();
	}
}
