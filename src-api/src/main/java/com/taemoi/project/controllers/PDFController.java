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

import com.taemoi.project.entities.Deporte;
import com.taemoi.project.services.PDFService;

import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/informes")
public class PDFController {

	@Autowired
	private PDFService pdfService;

	@GetMapping("/alumnosPorGrado")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<byte[]> generarInformeAlumnosPorGrado(
			@RequestParam(defaultValue = "true") boolean soloActivos) {
		byte[] pdfBytes = pdfService.generarInformeAlumnosPorGrado(soloActivos);

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_PDF);
		headers.setContentDisposition(
				ContentDisposition.builder("inline").filename("informe_alumnos_por_grado.pdf").build());

		return ResponseEntity.ok().headers(headers).body(pdfBytes);
	}

	@GetMapping("/taekwondoPorGrado")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<byte[]> generarInformeTaekwondoPorGrado(
			@RequestParam(defaultValue = "true") boolean soloActivos) {
		byte[] pdfBytes = pdfService.generarInformeTaekwondoPorGrado(soloActivos);

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_PDF);
		headers.setContentDisposition(
				ContentDisposition.builder("inline").filename("informe_alumnos_taekwondo_por_grado.pdf").build());
		return ResponseEntity.ok().headers(headers).body(pdfBytes);
	}

	@GetMapping("/kickboxingPorGrado")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<byte[]> generarInformeKickboxingPorGrado(
			@RequestParam(defaultValue = "true") boolean soloActivos) {
		byte[] pdfBytes = pdfService.generarInformeKickboxingPorGrado(soloActivos);

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_PDF);
		headers.setContentDisposition(
				ContentDisposition.builder("inline").filename("informe_alumnos_kickboxing_por_grado.pdf").build());
		return ResponseEntity.ok().headers(headers).body(pdfBytes);
	}

	@GetMapping("/licencias")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<byte[]> generarInformeLicencias(
			@RequestParam(defaultValue = "true") boolean soloActivos) {
		byte[] pdfBytes = pdfService.generarInformeLicencias(soloActivos);

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_PDF);
		headers.setContentDisposition(
				ContentDisposition.builder("inline").filename("informe_licencias_alumnos.pdf").build());

		return ResponseEntity.ok().headers(headers).body(pdfBytes);
	}

	@GetMapping("/infantilesAPromocionar")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<byte[]> generarInformeInfantilesAPromocionar(
			@RequestParam(defaultValue = "true") boolean soloActivos) {
		byte[] pdfBytes = pdfService.generarInformeInfantilesAPromocionar(soloActivos);

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_PDF);
		headers.setContentDisposition(
				ContentDisposition.builder("inline").filename("informe_alumnos_infantiles_promocion.pdf").build());

		return ResponseEntity.ok().headers(headers).body(pdfBytes);
	}

	@GetMapping("/adultosAPromocionar")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<byte[]> generarInformeAdultosAPromocionar(
			@RequestParam(defaultValue = "true") boolean soloActivos) {
		byte[] pdfBytes = pdfService.generarInformeAdultosAPromocionar(soloActivos);
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_PDF);
		headers.setContentDisposition(
				ContentDisposition.builder("inline").filename("informe_alumnos_adultos_promocion.pdf").build());
		return ResponseEntity.ok().headers(headers).body(pdfBytes);
	}

	@GetMapping("/deudas")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<byte[]> generarInformeDeudas(
			@RequestParam(defaultValue = "true") boolean soloActivos) {
		byte[] pdfBytes = pdfService.generarInformeDeudas(soloActivos);
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_PDF);
		headers.setContentDisposition(
				ContentDisposition.builder("inline").filename("informe_deudas_alumnos.pdf").build());
		return ResponseEntity.ok().headers(headers).body(pdfBytes);
	}

	@GetMapping("/deudas/csv")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<byte[]> generarInformeDeudasCSV(
			@RequestParam(defaultValue = "true") boolean soloActivos) {
		byte[] csvBytes = pdfService.generarInformeDeudasCSV(soloActivos);
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.parseMediaType("text/csv"));
		headers.setContentDisposition(
				ContentDisposition.builder("attachment").filename("informe_deudas_alumnos.csv").build());
		return ResponseEntity.ok().headers(headers).body(csvBytes);
	}

	@GetMapping("/mensualidades")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<byte[]> generarInformeMensualidades(
			@RequestParam(defaultValue = "true") boolean soloActivos) {
		byte[] pdfBytes = pdfService.generarInformeMensualidades(soloActivos);
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_PDF);
		headers.setContentDisposition(
				ContentDisposition.builder("inline").filename("informe_mensualidades_alumnos.pdf").build());
		return ResponseEntity.ok().headers(headers).body(pdfBytes);
	}

	@GetMapping("/mensualidades/taekwondo")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<byte[]> generarInformeMensualidadesTaekwondo(
			@RequestParam(defaultValue = "true") boolean soloActivos) {
		byte[] pdfBytes = pdfService.generarInformeMensualidadesTaekwondo(soloActivos);
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_PDF);
		headers.setContentDisposition(
				ContentDisposition.builder("inline").filename("informe_mensualidades_taekwondo.pdf").build());
		return ResponseEntity.ok().headers(headers).body(pdfBytes);
	}

	@GetMapping("/mensualidades/kickboxing")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<byte[]> generarInformeMensualidadesKickboxing(
			@RequestParam(defaultValue = "true") boolean soloActivos) {
		byte[] pdfBytes = pdfService.generarInformeMensualidadesKickboxing(soloActivos);
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_PDF);
		headers.setContentDisposition(
				ContentDisposition.builder("inline").filename("informe_mensualidades_kickboxing.pdf").build());
		return ResponseEntity.ok().headers(headers).body(pdfBytes);
	}

	@GetMapping("/asistencia")
	public void generarAsistencia(@RequestParam int year, @RequestParam int month, @RequestParam String grupo,
			@RequestParam(required = false) Deporte deporte,
			HttpServletResponse response) throws IOException {

		byte[] pdfBytes = pdfService.generarListadoAsistencia(year, month, grupo, deporte);

		String deporteSuffix = deporte != null ? "_" + deporte.name() : "";
		String filename = "Asistencia-%s%s-%d-%02d.pdf".formatted(grupo, deporteSuffix, year, month);

		response.setContentType(MediaType.APPLICATION_PDF_VALUE);
		response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

		response.getOutputStream().write(pdfBytes);
		response.getOutputStream().flush();
	}

}
