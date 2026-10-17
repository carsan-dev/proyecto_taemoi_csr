package com.taemoi.project.controllers;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.taemoi.project.dtos.request.CertificadosCobrosRequest;
import com.taemoi.project.dtos.response.TesoreriaMovimientoDTO;
import com.taemoi.project.dtos.response.TesoreriaResumenDTO;
import com.taemoi.project.services.TesoreriaService;

import jakarta.validation.Valid;

/**
 * Tesorería. Entrega 6-3 (D4, D6-12, entrega6-plan.md §3.4): abierta al responsable de sede, que solo ve los cargos de
 * sus sedes (filtro en el servicio, por la sede del cargo). {@code sedeId} (opcional) filtra por una sede: la gestión,
 * cualquiera de la escuela (otra → 400); el responsable, una de las suyas (otra → 403). Sin {@code sedeId} la gestión ve
 * lo de siempre. Certificados: el responsable, solo de alumnos de su sede principal (D6-3).
 */
@RestController
@RequestMapping("/api/tesoreria")
@Validated
public class TesoreriaController {

	@Autowired
	private TesoreriaService tesoreriaService;

	@GetMapping("/resumen")
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER','ROLE_RESPONSABLE_SEDE')")
	public ResponseEntity<?> obtenerResumen(
			@RequestParam(required = false) Integer mes,
			@RequestParam(required = false) Integer ano,
			@RequestParam(required = false) String deporte,
			@RequestParam(required = false) Boolean soloActivos,
			@RequestParam(required = false) Long sedeId) {
		try {
			TesoreriaResumenDTO resumen = tesoreriaService.obtenerResumen(mes, ano, deporte, soloActivos, sedeId);
			return ResponseEntity.ok(resumen);
		} catch (IllegalArgumentException ex) {
			return ResponseEntity.badRequest().body(java.util.Map.of("mensaje", ex.getMessage()));
		}
	}

	@GetMapping("/movimientos")
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER','ROLE_RESPONSABLE_SEDE')")
	public ResponseEntity<?> obtenerMovimientos(
			@RequestParam(required = false) Integer mes,
			@RequestParam(required = false) Integer ano,
			@RequestParam(required = false) String deporte,
			@RequestParam(required = false) Boolean pagado,
			@RequestParam(required = false) String texto,
			@RequestParam(required = false) Boolean soloActivos,
			@RequestParam(defaultValue = "1") Integer page,
			@RequestParam(defaultValue = "25") Integer size,
			@RequestParam(required = false) Long sedeId) {
		try {
			Page<TesoreriaMovimientoDTO> movimientos = tesoreriaService.obtenerMovimientos(
					mes,
					ano,
					deporte,
					pagado,
					texto,
					soloActivos,
					page,
					size,
					sedeId);
			return ResponseEntity.ok(movimientos);
		} catch (IllegalArgumentException ex) {
			return ResponseEntity.badRequest().body(java.util.Map.of("mensaje", ex.getMessage()));
		}
	}

	@GetMapping("/anios-disponibles")
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER','ROLE_RESPONSABLE_SEDE')")
	public ResponseEntity<List<Integer>> obtenerAniosDisponibles() {
		return ResponseEntity.ok(tesoreriaService.obtenerAniosDisponibles());
	}

	@GetMapping("/export/csv")
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER','ROLE_RESPONSABLE_SEDE')")
	public ResponseEntity<?> exportarCSV(
			@RequestParam(required = false) Integer mes,
			@RequestParam(required = false) Integer ano,
			@RequestParam(required = false) String deporte,
			@RequestParam(required = false) Boolean pagado,
			@RequestParam(required = false) String texto,
			@RequestParam(required = false) Boolean soloActivos,
			@RequestParam(required = false) Long sedeId) {
		try {
			byte[] csvBytes = tesoreriaService.exportarMovimientosCSV(mes, ano, deporte, pagado, texto, soloActivos,
					sedeId);
			String filename = construirNombreArchivo("csv", mes, ano, deporte, pagado);
			return ResponseEntity.ok()
					.contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
					.header(HttpHeaders.CONTENT_DISPOSITION,
							ContentDisposition.builder("attachment").filename(filename).build().toString())
					.body(csvBytes);
		} catch (IllegalArgumentException ex) {
			return ResponseEntity.badRequest().body(java.util.Map.of("mensaje", ex.getMessage()));
		}
	}

	@GetMapping("/export/pdf")
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER','ROLE_RESPONSABLE_SEDE')")
	public ResponseEntity<?> exportarPDF(
			@RequestParam(required = false) Integer mes,
			@RequestParam(required = false) Integer ano,
			@RequestParam(required = false) String deporte,
			@RequestParam(required = false) Boolean pagado,
			@RequestParam(required = false) String texto,
			@RequestParam(required = false) Boolean soloActivos,
			@RequestParam(required = false) Long sedeId) {
		try {
			byte[] pdfBytes = tesoreriaService.exportarMovimientosPDF(mes, ano, deporte, pagado, texto, soloActivos,
					sedeId);
			String filename = construirNombreArchivo("pdf", mes, ano, deporte, pagado);
			return ResponseEntity.ok()
					.contentType(MediaType.APPLICATION_PDF)
					.header(HttpHeaders.CONTENT_DISPOSITION,
							ContentDisposition.builder("inline").filename(filename).build().toString())
					.body(pdfBytes);
		} catch (IllegalArgumentException ex) {
			return ResponseEntity.badRequest().body(java.util.Map.of("mensaje", ex.getMessage()));
		}
	}

	@PostMapping("/certificados-cobros")
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER','ROLE_RESPONSABLE_SEDE')")
	public ResponseEntity<?> generarCertificadosCobros(@Valid @RequestBody CertificadosCobrosRequest request) {
		try {
			List<Long> alumnoIds = request.getAlumnoIds().stream()
					.filter(id -> id != null)
					.distinct()
					.toList();

			if (alumnoIds.isEmpty()) {
				return ResponseEntity.badRequest().body(java.util.Map.of("mensaje", "Debes seleccionar al menos un alumno."));
			}

			if (alumnoIds.size() == 1) {
				byte[] pdfBytes = tesoreriaService.generarCertificadoCobrosAlumno(alumnoIds.get(0), request.getAno());
				return ResponseEntity.ok()
						.contentType(MediaType.APPLICATION_PDF)
						.header(HttpHeaders.CONTENT_DISPOSITION,
								ContentDisposition.builder("attachment")
										.filename("certificado_cobros_" + request.getAno() + ".pdf")
										.build()
										.toString())
						.body(pdfBytes);
			}

			byte[] zipBytes = tesoreriaService.generarCertificadosCobrosZip(alumnoIds, request.getAno());
			return ResponseEntity.ok()
					.contentType(MediaType.parseMediaType("application/zip"))
					.header(HttpHeaders.CONTENT_DISPOSITION,
							ContentDisposition.builder("attachment")
									.filename("certificados_cobros_" + request.getAno() + ".zip")
									.build()
									.toString())
					.body(zipBytes);
		} catch (IllegalArgumentException ex) {
			return ResponseEntity.badRequest().body(java.util.Map.of("mensaje", ex.getMessage()));
		}
	}

	private String construirNombreArchivo(String extension, Integer mes, Integer ano, String deporte, Boolean pagado) {
		String periodo = ano == null ? "total" : (mes == null ? "ano_" + ano : String.format("%02d_%d", mes, ano));
		String deporteSegment = (deporte == null || deporte.isBlank()) ? "todos" : deporte.toLowerCase();
		String estadoSegment = pagado == null ? "todos" : (Boolean.TRUE.equals(pagado) ? "pagados" : "pendientes");
		return "tesoreria_" + periodo + "_" + deporteSegment + "_" + estadoSegment + "." + extension;
	}
}
