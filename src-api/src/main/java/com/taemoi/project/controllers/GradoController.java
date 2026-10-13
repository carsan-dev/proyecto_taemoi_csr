package com.taemoi.project.controllers;

import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.taemoi.project.entities.Disciplina;
import com.taemoi.project.entities.Grado;
import com.taemoi.project.services.CatalogoDisciplinasService;
import com.taemoi.project.services.GradoService;
import com.taemoi.project.services.ProgresionGradosService;

@RestController
@RequestMapping("/api/grados")
public class GradoController {

	@Autowired
	private GradoService gradoService;

	@Autowired
	private CatalogoDisciplinasService catalogo;

	@Autowired
	private ProgresionGradosService progresion;

	/**
	 * Grados de la disciplina ({@code codigo}); sin parámetro, los de taekwondo (la respuesta de
	 * siempre). Disciplina inexistente o sin grados: lista vacía.
	 */
	@GetMapping
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<List<Grado>> obtenerTodosLosGrados(
			@RequestParam(required = false) String disciplina) {
		List<Grado> grados = disciplina == null || disciplina.isBlank()
				? gradoService.obtenerTodosLosGrados()
				: gradoService.obtenerGradosDeDisciplina(disciplina.trim().toUpperCase());
		return ResponseEntity.ok(grados);
	}

	/**
	 * Códigos de los grados que puede tener un alumno según su fecha de nacimiento y disciplina
	 * (grupo de edad por {@code disciplina.edad_adulto}; grados con {@code aplica_a} TODOS o de
	 * ese grupo, por orden). Contrato heredado: una disciplina que no existe o no tiene grados
	 * usa los grados de taekwondo.
	 */
	@GetMapping("/disponibles/{fechaNacimiento}")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<List<String>> obtenerGradosDisponiblesPorFechaNacimiento(
			@PathVariable String fechaNacimiento,
			@RequestParam(required = false, defaultValue = GradoService.DISCIPLINA_POR_DEFECTO) String deporte) {
		LocalDate fechaNac = LocalDate.parse(fechaNacimiento); // Formato YYYY-MM-DD

		String codigoSolicitado = catalogo.buscarPorCodigo(deporte.toUpperCase())
				.map(Disciplina::getCodigo)
				.orElse(GradoService.DISCIPLINA_POR_DEFECTO);
		boolean esMenor = catalogo.esMenorPorAnioNatural(fechaNac, codigoSolicitado);
		String codigoConGrados = progresion.tieneProgresion(codigoSolicitado)
				? codigoSolicitado
				: GradoService.DISCIPLINA_POR_DEFECTO;

		List<String> gradosDisponibles = progresion.listarGradosAplicables(codigoConGrados, esMenor).stream()
				.map(Grado::getCodigo)
				.toList();
		return ResponseEntity.ok(gradosDisponibles);
	}
}
