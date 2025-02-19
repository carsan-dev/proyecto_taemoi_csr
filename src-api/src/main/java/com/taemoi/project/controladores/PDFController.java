package com.taemoi.project.controladores;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.taemoi.project.servicios.PDFService;

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
        headers.setContentDisposition(ContentDisposition
                .builder("inline")
                .filename("informe_alumnos_taekwondo_por_grado.pdf")
                .build());
		return ResponseEntity.ok().headers(headers).body(pdfBytes);
    }
    
    @GetMapping("/kickboxingPorGrado")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
    public ResponseEntity<byte[]> generarInformeKickboxingPorGrado() {
        byte[] pdfBytes = pdfService.generarInformeKickboxingPorGrado();
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition
                .builder("inline")
                .filename("informe_alumnos_kickboxing_por_grado.pdf")
                .build());
		return ResponseEntity.ok().headers(headers).body(pdfBytes);
    }
}
