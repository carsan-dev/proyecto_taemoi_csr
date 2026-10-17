package com.taemoi.project.controllers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.taemoi.project.dtos.request.TarifaSedeRequest;
import com.taemoi.project.dtos.response.TarifaSedeDTO;
import com.taemoi.project.services.TarifaSedeService;

/**
 * Tarifa por sede (entrega 6-4, D6-30 y D6-31): API mínima sin pantalla (la pantalla es de la E9). Sin PUT: para
 * cambiar el precio desde un mes se añade otra fila. Escriben el propietario y el gestor; el responsable de sede lee las
 * de sus sedes (filtro del servicio); el resto, 403. Los errores, con el formato de {@code GlobalExceptionHandler}:
 * 400 {@code {"mensaje"}} (datos no válidos, tarifa o sede de otra escuela), 409 (duplicado o borrar una del mes en
 * curso o pasada) y 404 (no existe en la escuela).
 */
@RestController
@RequestMapping("/api/tarifas-sede")
public class TarifaSedeController {

	private final TarifaSedeService tarifaSede;

	public TarifaSedeController(TarifaSedeService tarifaSede) {
		this.tarifaSede = tarifaSede;
	}

	@GetMapping
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER','ROLE_RESPONSABLE_SEDE')")
	public ResponseEntity<List<TarifaSedeDTO>> listar() {
		return ResponseEntity.ok(tarifaSede.listar());
	}

	@PostMapping
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER')")
	public ResponseEntity<TarifaSedeDTO> crear(@RequestBody TarifaSedeRequest solicitud) {
		return ResponseEntity.status(HttpStatus.CREATED).body(tarifaSede.crear(solicitud));
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER')")
	public ResponseEntity<Void> borrar(@PathVariable Long id) {
		tarifaSede.borrar(id);
		return ResponseEntity.noContent().build();
	}
}
