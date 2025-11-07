package com.taemoi.project.controllers;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.taemoi.project.services.PDFService;

import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/informes")
public class PDFController {

	@Autowired
	private PDFService pdfService;

	@GetMapping("/alumnosPorGrado")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<byte[]> generarInformeAlumnosPorGrado() {
		byte[] pdfBytes = pdfService.generarInformeAlumnosPorGrado();

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_PDF);
		headers.setContentDisposition(
				ContentDisposition.builder("inline").filename("informe_alumnos_por_grado.pdf").build());

		return ResponseEntity.ok().headers(headers).body(pdfBytes);
	}

	@GetMapping("/taekwondoPorGrado")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<byte[]> generarInformeTaekwondoPorGrado() {
		byte[] pdfBytes = pdfService.generarInformeTaekwondoPorGrado();

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_PDF);
		headers.setContentDisposition(
				ContentDisposition.builder("inline").filename("informe_alumnos_taekwondo_por_grado.pdf").build());
		return ResponseEntity.ok().headers(headers).body(pdfBytes);
	}

	@GetMapping("/kickboxingPorGrado")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<byte[]> generarInformeKickboxingPorGrado() {
		byte[] pdfBytes = pdfService.generarInformeKickboxingPorGrado();

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_PDF);
		headers.setContentDisposition(
				ContentDisposition.builder("inline").filename("informe_alumnos_kickboxing_por_grado.pdf").build());
		return ResponseEntity.ok().headers(headers).body(pdfBytes);
	}

	@GetMapping("/licencias")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<byte[]> generarInformeLicencias() {
		byte[] pdfBytes = pdfService.generarInformeLicencias();

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_PDF);
		headers.setContentDisposition(
				ContentDisposition.builder("inline").filename("informe_licencias_alumnos.pdf").build());

		return ResponseEntity.ok().headers(headers).body(pdfBytes);
	}

	@GetMapping("/infantilesAPromocionar")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<byte[]> generarInformeInfantilesAPromocionar() {
		byte[] pdfBytes = pdfService.generarInformeInfantilesAPromocionar();

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_PDF);
		headers.setContentDisposition(
				ContentDisposition.builder("inline").filename("informe_alumnos_infantiles_promocion.pdf").build());

		return ResponseEntity.ok().headers(headers).body(pdfBytes);
	}

	@GetMapping("/adultosAPromocionar")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<byte[]> generarInformeAdultosAPromocionar() {
		byte[] pdfBytes = pdfService.generarInformeAdultosAPromocionar();
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_PDF);
		headers.setContentDisposition(
				ContentDisposition.builder("inline").filename("informe_alumnos_adultos_promocion.pdf").build());
		return ResponseEntity.ok().headers(headers).body(pdfBytes);
	}

	@GetMapping("/deudas")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<byte[]> generarInformeDeudas() {
		byte[] pdfBytes = pdfService.generarInformeDeudas();
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_PDF);
		headers.setContentDisposition(
				ContentDisposition.builder("inline").filename("informe_deudas_alumnos.pdf").build());
		return ResponseEntity.ok().headers(headers).body(pdfBytes);
	}

	@GetMapping("/deudas/csv")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<byte[]> generarInformeDeudasCSV() {
		byte[] csvBytes = pdfService.generarInformeDeudasCSV();
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.parseMediaType("text/csv"));
		headers.setContentDisposition(
				ContentDisposition.builder("attachment").filename("informe_deudas_alumnos.csv").build());
		return ResponseEntity.ok().headers(headers).body(csvBytes);
	}

	@GetMapping("/asistencia")
	public void generarAsistencia(@RequestParam int year, @RequestParam int month, @RequestParam String grupo,
			@RequestParam String turno, HttpServletResponse response) throws IOException {

		byte[] pdfBytes = pdfService.generarListadoAsistencia(year, month, grupo, turno);

		String safeTurno = turno.replace('–', '-');

		String filename = "Asistencia-%s-%s-%d-%02d.pdf".formatted(grupo, safeTurno, year, month);

		response.setContentType(MediaType.APPLICATION_PDF_VALUE);
		response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

		response.getOutputStream().write(pdfBytes);
		response.getOutputStream().flush();
	}

}
