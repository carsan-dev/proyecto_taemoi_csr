package com.taemoi.project.controllers;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.taemoi.project.entities.Deporte;
import com.taemoi.project.entities.Grado;
import com.taemoi.project.entities.TipoGrado;
import com.taemoi.project.services.GradoService;
import com.taemoi.project.utils.FechaUtils;

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
	public ResponseEntity<List<String>> obtenerGradosDisponiblesPorFechaNacimiento(
			@PathVariable String fechaNacimiento,
			@RequestParam(required = false, defaultValue = "TAEKWONDO") String deporte) {
		LocalDate fechaNac = LocalDate.parse(fechaNacimiento); // Asegúrate de que la fecha viene en formato YYYY-MM-DD

		// Parsear el deporte (por defecto TAEKWONDO para compatibilidad)
		Deporte deporteEnum;
		try {
			deporteEnum = Deporte.valueOf(deporte.toUpperCase());
		} catch (IllegalArgumentException e) {
			deporteEnum = Deporte.TAEKWONDO;
		}

		// Usar FechaUtils.esMenor para aplicar la regla correcta según el deporte
		java.util.Date fechaNacDate = java.sql.Date.valueOf(fechaNac);
		boolean esMenor = FechaUtils.esMenor(fechaNacDate, deporteEnum);

		List<String> gradosDisponibles;

		// Kickboxing solo tiene grados de adulto (sin grados intermedios como BLANCO_AMARILLO)
		if (deporteEnum == Deporte.KICKBOXING) {
			gradosDisponibles = Arrays.asList(TipoGrado.BLANCO.name(), TipoGrado.AMARILLO.name(),
					TipoGrado.NARANJA.name(), TipoGrado.VERDE.name(), TipoGrado.AZUL.name(), TipoGrado.ROJO.name(),
					TipoGrado.NEGRO_1_DAN.name(), TipoGrado.NEGRO_2_DAN.name(), TipoGrado.NEGRO_3_DAN.name(),
					TipoGrado.NEGRO_4_DAN.name(), TipoGrado.NEGRO_5_DAN.name());
		} else if (esMenor) {
			// Grados para menores (Taekwondo)
			gradosDisponibles = Arrays.asList(TipoGrado.BLANCO.name(), TipoGrado.BLANCO_AMARILLO.name(),
					TipoGrado.AMARILLO.name(), TipoGrado.AMARILLO_NARANJA.name(), TipoGrado.NARANJA.name(),
					TipoGrado.NARANJA_VERDE.name(), TipoGrado.VERDE.name(), TipoGrado.VERDE_AZUL.name(),
					TipoGrado.AZUL.name(), TipoGrado.AZUL_ROJO.name(), TipoGrado.ROJO.name(),
					TipoGrado.ROJO_NEGRO_1_PUM.name(), TipoGrado.ROJO_NEGRO_2_PUM.name(),
					TipoGrado.ROJO_NEGRO_3_PUM.name());
		} else {
			// Grados para adultos (Taekwondo)
			gradosDisponibles = Arrays.asList(TipoGrado.BLANCO.name(), TipoGrado.AMARILLO.name(),
					TipoGrado.NARANJA.name(), TipoGrado.VERDE.name(), TipoGrado.AZUL.name(), TipoGrado.ROJO.name(),
					TipoGrado.NEGRO_1_DAN.name(), TipoGrado.NEGRO_2_DAN.name(), TipoGrado.NEGRO_3_DAN.name(),
					TipoGrado.NEGRO_4_DAN.name(), TipoGrado.NEGRO_5_DAN.name());
		}

		return ResponseEntity.ok(gradosDisponibles);
	}
}
