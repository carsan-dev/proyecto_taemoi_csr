package com.taemoi.project.controladores;

import java.time.LocalDate;
import java.time.Period;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.taemoi.project.entidades.Grado;
import com.taemoi.project.entidades.TipoGrado;
import com.taemoi.project.servicios.GradoService;

@RestController
@RequestMapping("/api/grados")
public class GradoController {

	@Autowired
	private GradoService gradoService;
	
	@GetMapping
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<List<Grado>> obtenerTodosLosGrados() {
		List<Grado> grados = gradoService.obtenerTodosLosGrados();
		return ResponseEntity.ok(grados);
	}
	
    @GetMapping("/disponibles/{fechaNacimiento}")
    @PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
    public ResponseEntity<List<String>> obtenerGradosDisponiblesPorFechaNacimiento(@PathVariable String fechaNacimiento) {
        LocalDate fechaNac = LocalDate.parse(fechaNacimiento); // Asegúrate de que la fecha viene en formato YYYY-MM-DD
        LocalDate fechaActual = LocalDate.now();
        int edad = Period.between(fechaNac, fechaActual).getYears();

        boolean cumpleCatorceEsteAno = fechaNac.plusYears(14).getYear() == fechaActual.getYear();
        boolean esMenor = edad < 13 || (edad == 13 && !cumpleCatorceEsteAno);

        List<String> gradosDisponibles;

        if (esMenor) {
            // Grados para menores
            gradosDisponibles = Arrays.asList(
                TipoGrado.BLANCO.name(), TipoGrado.BLANCO_AMARILLO.name(), TipoGrado.AMARILLO.name(),
                TipoGrado.AMARILLO_NARANJA.name(), TipoGrado.NARANJA.name(), TipoGrado.NARANJA_VERDE.name(),
                TipoGrado.VERDE.name(), TipoGrado.VERDE_AZUL.name(), TipoGrado.AZUL.name(),
                TipoGrado.AZUL_ROJO.name(), TipoGrado.ROJO.name(), TipoGrado.ROJO_NEGRO.name()
            );
        } else {
            // Grados para adultos
            gradosDisponibles = Arrays.asList(
                TipoGrado.BLANCO.name(), TipoGrado.AMARILLO.name(), TipoGrado.NARANJA.name(),
                TipoGrado.VERDE.name(), TipoGrado.AZUL.name(), TipoGrado.ROJO.name(), TipoGrado.NEGRO.name()
            );
        }

        return ResponseEntity.ok(gradosDisponibles);
    }
}
