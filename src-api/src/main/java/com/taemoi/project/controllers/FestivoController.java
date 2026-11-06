package com.taemoi.project.controllers;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.taemoi.project.dtos.request.FestivoRequest;
import com.taemoi.project.dtos.response.FestivoDTOs;
import com.taemoi.project.services.FestivoService;

/**
 * Festivos por sede (decisiones 455-463, fase3-pasar-lista.md, «Motivos y festivos»): días sin clase en una o varias
 * sedes de la escuela activa. Propietario y gestor, todos; el responsable de sede ve los que tocan sus sedes y cambia o
 * quita solo los que tienen todas sus sedes entre las suyas (en {@link FestivoService}). El profesor, la familia y la
 * sesión solo-profesor, 403 (regla de URL de {@code SecurityConfiguration} y {@code @PreAuthorize}). Errores con
 * {@code {"mensaje"}}: 400 datos, 403 sedes fuera de su alcance o de otra escuela, 404 festivo de otra escuela o
 * inexistente. Las escrituras quedan en {@code auditoria_evento} por el filtro de auditoría, como el resto.
 */
@RestController
@RequestMapping("/api/festivos")
public class FestivoController {

	private final FestivoService festivos;

	public FestivoController(FestivoService festivos) {
		this.festivos = festivos;
	}

	/** Los de hoy en adelante y los anuales, o ({@code pasados=true}) los ya pasados; con {@code sedeId}, de esa sede. */
	@GetMapping
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER','ROLE_RESPONSABLE_SEDE')")
	public ResponseEntity<List<FestivoDTOs.Festivo>> listar(@RequestParam(required = false) Long sedeId,
			@RequestParam(defaultValue = "false") boolean pasados) {
		return ResponseEntity.ok(festivos.listar(sedeId, pasados));
	}

	@PostMapping
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER','ROLE_RESPONSABLE_SEDE')")
	public ResponseEntity<FestivoDTOs.Festivo> crear(@RequestBody(required = false) FestivoRequest cuerpo) {
		return ResponseEntity.status(HttpStatus.CREATED).body(festivos.crear(cuerpo));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER','ROLE_RESPONSABLE_SEDE')")
	public ResponseEntity<FestivoDTOs.Festivo> editar(@PathVariable Long id,
			@RequestBody(required = false) FestivoRequest cuerpo) {
		return ResponseEntity.ok(festivos.editar(id, cuerpo));
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER','ROLE_RESPONSABLE_SEDE')")
	public ResponseEntity<Void> borrar(@PathVariable Long id) {
		festivos.borrar(id);
		return ResponseEntity.noContent().build();
	}

	/** Clases afectadas por ese festivo, sin guardar nada. Mismo cuerpo que el alta, más {@code excluirFestivoId}. */
	@PostMapping("/impacto")
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER','ROLE_RESPONSABLE_SEDE')")
	public ResponseEntity<FestivoDTOs.Impacto> impacto(@RequestBody(required = false) FestivoRequest cuerpo) {
		return ResponseEntity.ok(festivos.impacto(cuerpo));
	}

	/** JSON mal formado: 400 con {@code {"mensaje"}} en español, como el resto de errores del controlador. */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<Map<String, String>> cuerpoIlegible(HttpMessageNotReadableException e) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("mensaje", "La petición no es válida."));
	}
}
