package com.taemoi.project.controllers;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import com.taemoi.project.dtos.ProductoAlumnoDTO;
import com.taemoi.project.services.ProductoAlumnoService;

@RestController
@RequestMapping("/api/productos-alumno")
public class ProductoAlumnoController {

	@Autowired
	private ProductoAlumnoService productoAlumnoService;

	@PostMapping("/alumno/{alumnoId}/producto/{productoId}")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<ProductoAlumnoDTO> asignarProductoAAlumno(@PathVariable Long alumnoId,
			@PathVariable Long productoId, @Valid @RequestBody ProductoAlumnoDTO detallesDTO) {

		ProductoAlumnoDTO productoAlumnoDTO = productoAlumnoService.asignarProductoAAlumno(alumnoId, productoId,
				detallesDTO);
		return ResponseEntity.ok(productoAlumnoDTO);
	}

	@GetMapping("/alumno/{alumnoId}")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<List<ProductoAlumnoDTO>> obtenerProductosDeAlumno(@PathVariable Long alumnoId) {
		List<ProductoAlumnoDTO> productosAlumnoDTO = productoAlumnoService.obtenerProductosDeAlumno(alumnoId);
		return ResponseEntity.ok(productosAlumnoDTO);
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<ProductoAlumnoDTO> actualizarProductoAlumno(@PathVariable Long id,
			@Valid @RequestBody ProductoAlumnoDTO detallesDTO) {

		ProductoAlumnoDTO actualizado = productoAlumnoService.actualizarProductoAlumno(id, detallesDTO);
		return ResponseEntity.ok(actualizado);
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<Void> eliminarProductoAlumno(@PathVariable Long id) {
		productoAlumnoService.eliminarProductoAlumno(id);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{alumnoId}/reservar-plaza")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> reservarPlaza(@PathVariable Long alumnoId, @RequestParam boolean pagado,
			@RequestParam(required = false, defaultValue = "false") boolean forzar) {
		int anoActual = LocalDate.now().getYear();
		int proximoAno = anoActual + 1;
		String concepto = "RESERVA DE PLAZA " + anoActual + "/" + proximoAno;

		try {
			ProductoAlumnoDTO reserva = productoAlumnoService.reservarPlaza(alumnoId, concepto, pagado, forzar);
			return ResponseEntity.ok(reserva);
		} catch (IllegalStateException e) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
		}
	}

	/**
	 * Reserva plaza para un deporte específico de un alumno
	 * POST /api/productos-alumno/{alumnoId}/reservar-plaza-deporte
	 */
	@PostMapping("/{alumnoId}/reservar-plaza-deporte")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> reservarPlazaPorDeporte(
			@PathVariable Long alumnoId,
			@RequestParam String deporte,
			@RequestParam boolean pagado,
			@RequestParam(required = false, defaultValue = "false") boolean forzar) {
		int anoActual = LocalDate.now().getYear();
		int proximoAno = anoActual + 1;
		String concepto = "RESERVA DE PLAZA " + anoActual + "/" + proximoAno + " - " + deporte;

		try {
			ProductoAlumnoDTO reserva = productoAlumnoService.reservarPlazaPorDeporte(alumnoId, deporte, concepto, pagado, forzar);
			return ResponseEntity.ok(reserva);
		} catch (IllegalStateException e) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
		} catch (IllegalArgumentException e) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("mensaje", e.getMessage()));
		}
	}
	
	@PostMapping("/mensualidades/general")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> cargarMensualidadesGenerales(@RequestBody String nombreMensualidad) {
		productoAlumnoService.cargarMensualidadesGenerales(nombreMensualidad);
		return ResponseEntity.ok(Map.of("mensaje", "Mensualidades creadas correctamente."));
	}

	@PostMapping("/mensualidades/deporte")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> cargarMensualidadesPorDeporte(@RequestBody String nombreMensualidad,
			@RequestParam String deporte) {
		try {
			productoAlumnoService.cargarMensualidadesPorDeporte(nombreMensualidad, deporte);
			return ResponseEntity.ok(Map.of("mensaje",
					"Mensualidades creadas correctamente para alumnos de " + deporte + "."));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("mensaje", e.getMessage()));
		}
	}

	@PostMapping("/mensualidades/individual")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> cargarMensualidadIndividual(@RequestParam Long alumnoId,
			@RequestBody String nombreMensualidad, @RequestParam(defaultValue = "false") boolean forzar) {
		try {
			productoAlumnoService.cargarMensualidadIndividual(alumnoId, nombreMensualidad, forzar);
			return ResponseEntity.ok(Map.of("mensaje", "Mensualidad individual creada correctamente."));
		} catch (IllegalStateException ex) {
			// Devuelve un código 409 con un mensaje específico
			return ResponseEntity.status(HttpStatus.CONFLICT)
					.body(Map.of("mensaje", ex.getMessage(), "accion", "confirmar" // Indica que es necesario confirmar
					));
		} catch (Exception ex) {
			// Manejo genérico de errores
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(Map.of("mensaje", "Ocurrió un error al crear la mensualidad.", "detalle", ex.getMessage()));
		}
	}

	@PostMapping("/licencias/general")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> cargarLicenciasGenerales(
			@RequestParam int ano,
			@RequestParam(required = false, defaultValue = "TODOS") String deporte) {
		try {
			productoAlumnoService.cargarLicenciasGenerales(ano, deporte);
			return ResponseEntity.ok(Map.of("mensaje", "Licencias creadas correctamente."));
		} catch (IllegalArgumentException ex) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("mensaje", ex.getMessage()));
		}
	}

	@PostMapping("/licencias/individual")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> cargarLicenciaIndividual(
			@RequestParam Long alumnoId,
			@RequestParam int ano,
			@RequestParam(required = false, defaultValue = "TODOS") String deporte,
			@RequestParam(defaultValue = "false") boolean forzar) {
		try {
			productoAlumnoService.cargarLicenciaIndividual(alumnoId, ano, deporte, forzar);
			return ResponseEntity.ok(Map.of("mensaje", "Licencia individual creada correctamente."));
		} catch (IllegalStateException ex) {
			return ResponseEntity.status(HttpStatus.CONFLICT)
					.body(Map.of("mensaje", ex.getMessage(), "accion", "confirmar"));
		} catch (IllegalArgumentException ex) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("mensaje", ex.getMessage()));
		} catch (Exception ex) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(Map.of("mensaje", "Ocurrió un error al crear la licencia.", "detalle", ex.getMessage()));
		}
	}

	
	@PostMapping("{alumnoId}/renovar-licencia")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
    public ResponseEntity<ProductoAlumnoDTO> renovarLicencia(@PathVariable Long alumnoId) {
        ProductoAlumnoDTO productoAlumnoDTO = productoAlumnoService.renovarLicencia(alumnoId);
        return ResponseEntity.ok(productoAlumnoDTO);
    }

	// ===== ENDPOINTS MULTI-DEPORTE =====

	/**
	 * Asigna un producto a un alumno para un deporte específico
	 * POST /api/productos-alumno/alumno/{alumnoId}/producto/{productoId}/deporte/{deporte}
	 */
	@PostMapping("/alumno/{alumnoId}/producto/{productoId}/deporte/{deporte}")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<ProductoAlumnoDTO> asignarProductoAAlumnoDeporte(
			@PathVariable Long alumnoId,
			@PathVariable Long productoId,
			@PathVariable String deporte,
			@Valid @RequestBody ProductoAlumnoDTO detallesDTO) {

		try {
			ProductoAlumnoDTO productoAlumnoDTO = productoAlumnoService.asignarProductoAAlumnoDeporte(
				alumnoId, productoId, deporte, detallesDTO);
			return ResponseEntity.ok(productoAlumnoDTO);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
		}
	}

	/**
	 * Carga mensualidades para todos los alumnos, una por cada deporte que practican
	 * POST /api/productos-alumno/mensualidades/multi-deporte
	 */
	@PostMapping("/mensualidades/multi-deporte")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> cargarMensualidadesMultiDeporte(@RequestBody String nombreMensualidad) {
		try {
			productoAlumnoService.cargarMensualidadesMultiDeporte(nombreMensualidad);
			return ResponseEntity.ok(Map.of("mensaje",
				"Mensualidades multi-deporte creadas correctamente para todos los alumnos."));
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of("mensaje", "Error al crear mensualidades: " + e.getMessage()));
		}
	}

	/**
	 * Carga mensualidad individual para un deporte específico de un alumno
	 * POST /api/productos-alumno/mensualidades/individual-deporte
	 */
	@PostMapping("/mensualidades/individual-deporte")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> cargarMensualidadIndividualPorDeporte(
			@RequestParam Long alumnoId,
			@RequestParam String deporte,
			@RequestBody String nombreMensualidad,
			@RequestParam(defaultValue = "false") boolean forzar) {
		try {
			productoAlumnoService.cargarMensualidadIndividualPorDeporte(alumnoId, deporte, nombreMensualidad, forzar);
			return ResponseEntity.ok(Map.of("mensaje",
				"Mensualidad individual creada correctamente para " + deporte + "."));
		} catch (IllegalStateException ex) {
			return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(Map.of("mensaje", ex.getMessage(), "accion", "confirmar"));
		} catch (IllegalArgumentException ex) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(Map.of("mensaje", ex.getMessage()));
		} catch (Exception ex) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of("mensaje", "Ocurrió un error al crear la mensualidad.", "detalle", ex.getMessage()));
		}
	}
}
