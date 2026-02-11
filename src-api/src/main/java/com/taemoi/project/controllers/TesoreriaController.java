package com.taemoi.project.controllers;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.taemoi.project.dtos.response.TesoreriaMovimientoDTO;
import com.taemoi.project.dtos.response.TesoreriaResumenDTO;
import com.taemoi.project.services.TesoreriaService;

@RestController
@RequestMapping("/api/tesoreria")
public class TesoreriaController {

	@Autowired
	private TesoreriaService tesoreriaService;

	@GetMapping("/resumen")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> obtenerResumen(
			@RequestParam(required = false) Integer mes,
			@RequestParam(required = false) Integer ano,
			@RequestParam(required = false) String deporte) {
		try {
			TesoreriaResumenDTO resumen = tesoreriaService.obtenerResumen(mes, ano, deporte);
			return ResponseEntity.ok(resumen);
		} catch (IllegalArgumentException ex) {
			return ResponseEntity.badRequest().body(java.util.Map.of("mensaje", ex.getMessage()));
		}
	}

	@GetMapping("/movimientos")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> obtenerMovimientos(
			@RequestParam(required = false) Integer mes,
			@RequestParam(required = false) Integer ano,
			@RequestParam(required = false) String deporte,
			@RequestParam(required = false) Boolean pagado,
			@RequestParam(required = false) String texto,
			@RequestParam(defaultValue = "1") Integer page,
			@RequestParam(defaultValue = "25") Integer size) {
		try {
			Page<TesoreriaMovimientoDTO> movimientos = tesoreriaService.obtenerMovimientos(
					mes,
					ano,
					deporte,
					pagado,
					texto,
					page,
					size);
			return ResponseEntity.ok(movimientos);
		} catch (IllegalArgumentException ex) {
			return ResponseEntity.badRequest().body(java.util.Map.of("mensaje", ex.getMessage()));
		}
	}

	@GetMapping("/anios-disponibles")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<List<Integer>> obtenerAniosDisponibles() {
		return ResponseEntity.ok(tesoreriaService.obtenerAniosDisponibles());
	}

	@GetMapping("/export/csv")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> exportarCSV(
			@RequestParam(required = false) Integer mes,
			@RequestParam(required = false) Integer ano,
			@RequestParam(required = false) String deporte,
			@RequestParam(required = false) Boolean pagado,
			@RequestParam(required = false) String texto) {
		try {
			byte[] csvBytes = tesoreriaService.exportarMovimientosCSV(mes, ano, deporte, pagado, texto);
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
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> exportarPDF(
			@RequestParam(required = false) Integer mes,
			@RequestParam(required = false) Integer ano,
			@RequestParam(required = false) String deporte,
			@RequestParam(required = false) Boolean pagado,
			@RequestParam(required = false) String texto) {
		try {
			byte[] pdfBytes = tesoreriaService.exportarMovimientosPDF(mes, ano, deporte, pagado, texto);
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

	private String construirNombreArchivo(String extension, Integer mes, Integer ano, String deporte, Boolean pagado) {
		String periodo = ano == null ? "total" : (mes == null ? "ano_" + ano : String.format("%02d_%d", mes, ano));
		String deporteSegment = (deporte == null || deporte.isBlank()) ? "todos" : deporte.toLowerCase();
		String estadoSegment = pagado == null ? "todos" : (Boolean.TRUE.equals(pagado) ? "pagados" : "pendientes");
		return "tesoreria_" + periodo + "_" + deporteSegment + "_" + estadoSegment + "." + extension;
	}
}
