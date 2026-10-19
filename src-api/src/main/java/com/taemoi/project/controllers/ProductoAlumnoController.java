package com.taemoi.project.controllers;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
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

import com.taemoi.project.acceso.SedeInactivaException;
import com.taemoi.project.dtos.ProductoAlumnoDTO;
import com.taemoi.project.dtos.response.CuantiaPersonalizadaSuperiorDTO;
import com.taemoi.project.exceptions.producto.MensualidadIncompletaException;
import com.taemoi.project.services.ProductoAlumnoService;
import com.taemoi.project.services.ResultadoGeneracionMensualidades;

/**
 * Cargos y cobros. Entrega 6-3 (D4, entrega6-plan.md §3.4): el responsable de sede crea cargos sueltos, ve y cobra los
 * de los alumnos de SU sede principal ({@code alumnoEnSede EDITAR}, D6-3) y cobra, cancela o reactiva los cargos de sus
 * sedes ({@code productoAlumnoEnSede}, por la sede del cargo). Desde la 6b-2c, borrar un cargo a mano es solo del
 * propietario (y solo si nunca se cobró). Las generaciones masivas siguen siendo solo de gestión
 * (D6-10). En una sede inactiva no se crean cargos (400, D6-17), pero se cobran los existentes.
 */
@RestController
@RequestMapping("/api/productos-alumno")
public class ProductoAlumnoController {

	@Autowired
	private ProductoAlumnoService productoAlumnoService;

	@PostMapping("/alumno/{alumnoId}/producto/{productoId}")
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER','ROLE_RESPONSABLE_SEDE') and @acceso.alumnoEnSede(#alumnoId,'EDITAR')")
	public ResponseEntity<ProductoAlumnoDTO> asignarProductoAAlumno(@PathVariable Long alumnoId,
			@PathVariable Long productoId, @Valid @RequestBody ProductoAlumnoDTO detallesDTO) {

		ProductoAlumnoDTO productoAlumnoDTO = productoAlumnoService.asignarProductoAAlumno(alumnoId, productoId,
				detallesDTO);
		return ResponseEntity.ok(productoAlumnoDTO);
	}

	@GetMapping("/alumno/{alumnoId}")
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER','ROLE_RESPONSABLE_SEDE') and @acceso.alumnoEnSede(#alumnoId,'EDITAR')")
	public ResponseEntity<List<ProductoAlumnoDTO>> obtenerProductosDeAlumno(@PathVariable Long alumnoId) {
		List<ProductoAlumnoDTO> productosAlumnoDTO = productoAlumnoService.obtenerProductosDeAlumno(alumnoId);
		return ResponseEntity.ok(productosAlumnoDTO);
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER','ROLE_RESPONSABLE_SEDE') and @acceso.productoAlumnoEnSede(#id,'EDITAR')")
	public ResponseEntity<ProductoAlumnoDTO> actualizarProductoAlumno(@PathVariable Long id,
			@Valid @RequestBody ProductoAlumnoDTO detallesDTO) {

		ProductoAlumnoDTO actualizado = productoAlumnoService.actualizarProductoAlumno(id, detallesDTO);
		return ResponseEntity.ok(actualizado);
	}

	/**
	 * Pieza 6b-2c (decisión del propietario, cambio deliberado): borrar un cargo a mano es solo del propietario y solo
	 * si nunca se cobró (409 si no; el servicio). La gestión y el responsable cancelan ({@code POST .../cancelar}).
	 */
	@DeleteMapping("/{id}")
	@PreAuthorize("hasAuthority('ROLE_ADMIN')")
	public ResponseEntity<Void> eliminarProductoAlumno(@PathVariable Long id) {
		productoAlumnoService.eliminarProductoAlumno(id);
		return ResponseEntity.noContent().build();
	}

	/**
	 * Pieza 6b-2c: cancela un cargo pendiente ({@code {"motivo"}} obligatorio). Propietario, gestor y responsable de la
	 * sede del cargo. Pagado o ya cancelado → 409; cargo de una inscripción a convocatoria → 400.
	 */
	@PostMapping("/{id}/cancelar")
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER','ROLE_RESPONSABLE_SEDE') and @acceso.productoAlumnoEnSede(#id,'EDITAR')")
	public ResponseEntity<ProductoAlumnoDTO> cancelarProductoAlumno(@PathVariable Long id,
			@RequestBody(required = false) com.taemoi.project.dtos.request.CancelarCargoRequest peticion) {
		return ResponseEntity.ok(productoAlumnoService.cancelarProductoAlumno(id,
				peticion == null ? null : peticion.motivo()));
	}

	/** Pieza 6b-2c: reactiva un cargo cancelado (vuelve a pendiente). Mismos permisos que cancelar. No cancelado → 409. */
	@PostMapping("/{id}/reactivar")
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER','ROLE_RESPONSABLE_SEDE') and @acceso.productoAlumnoEnSede(#id,'EDITAR')")
	public ResponseEntity<ProductoAlumnoDTO> reactivarProductoAlumno(@PathVariable Long id) {
		return ResponseEntity.ok(productoAlumnoService.reactivarProductoAlumno(id));
	}

	@PostMapping("/{alumnoId}/reservar-plaza")
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER','ROLE_RESPONSABLE_SEDE') and @acceso.alumnoEnSede(#alumnoId,'EDITAR')")
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
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER','ROLE_RESPONSABLE_SEDE') and @acceso.alumnoEnSede(#alumnoId,'EDITAR')")
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
	public ResponseEntity<?> cargarMensualidadesGenerales(
			@RequestBody String nombreMensualidad,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaAsignacion) {
		ResultadoGeneracionMensualidades resultado = productoAlumnoService.cargarMensualidadesGenerales(nombreMensualidad,
				fechaAsignacion);
		return ResponseEntity.ok(respuestaMasiva("Mensualidades creadas correctamente.", resultado));
	}

	@PostMapping("/mensualidades/deporte")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> cargarMensualidadesPorDeporte(@RequestBody String nombreMensualidad,
			@RequestParam String deporte,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaAsignacion) {
		try {
			ResultadoGeneracionMensualidades resultado = productoAlumnoService.cargarMensualidadesPorDeporte(
					nombreMensualidad, deporte, fechaAsignacion);
			return ResponseEntity.ok(respuestaMasiva(
					"Mensualidades creadas correctamente para alumnos de " + deporte + ".", resultado));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("mensaje", e.getMessage()));
		}
	}

	@PostMapping("/mensualidades/individual")
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER','ROLE_RESPONSABLE_SEDE') and @acceso.alumnoEnSede(#alumnoId,'EDITAR')")
	public ResponseEntity<?> cargarMensualidadIndividual(@RequestParam Long alumnoId,
			@RequestBody String nombreMensualidad,
			@RequestParam(defaultValue = "false") boolean forzar,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaAsignacion) {
		try {
			List<CuantiaPersonalizadaSuperiorDTO> superiores = productoAlumnoService.cargarMensualidadIndividual(alumnoId,
					nombreMensualidad, forzar, fechaAsignacion);
			return ResponseEntity.ok(conCuantiasSuperiores(
					Map.of("mensaje", "Mensualidad individual creada correctamente."), superiores));
		} catch (SedeInactivaException ex) {
			// D6-17 (entrega 6-3): 400 como en el resto de altas; antes que el catch genérico (500).
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("mensaje", ex.getMessage()));
		} catch (MensualidadIncompletaException ex) {
			return ResponseEntity.status(HttpStatus.CONFLICT)
					.body(Map.of(
							"mensaje", ex.getMessage(),
							"accion", "completar",
							"existentes", Map.of(
									"mensualidades", ex.getMensualidadesExistentes(),
									"tarifasCompetidor", ex.getTarifasCompetidorExistentes()
							),
							"faltantes", Map.of(
									"mensualidades", ex.getMensualidadesFaltantes(),
									"tarifasCompetidor", ex.getTarifasCompetidorFaltantes()
							)
					));
		} catch (IllegalStateException ex) {
			// Devuelve un código 409 con un mensaje específico
			return ResponseEntity.status(HttpStatus.CONFLICT)
					.body(Map.of("mensaje", ex.getMessage()));
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
			@RequestParam(required = false, defaultValue = "TODOS") String deporte,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaAsignacion) {
		try {
			int omitidos = productoAlumnoService.cargarLicenciasGenerales(ano, deporte, fechaAsignacion);
			return ResponseEntity.ok(respuestaMasiva("Licencias creadas correctamente.", omitidos));
		} catch (IllegalArgumentException ex) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("mensaje", ex.getMessage()));
		}
	}

	@PostMapping("/licencias/individual")
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER','ROLE_RESPONSABLE_SEDE') and @acceso.alumnoEnSede(#alumnoId,'EDITAR')")
	public ResponseEntity<?> cargarLicenciaIndividual(
			@RequestParam Long alumnoId,
			@RequestParam int ano,
			@RequestParam(required = false, defaultValue = "TODOS") String deporte,
			@RequestParam(defaultValue = "false") boolean forzar,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaAsignacion) {
		try {
			productoAlumnoService.cargarLicenciaIndividual(alumnoId, ano, deporte, forzar, fechaAsignacion);
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
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER','ROLE_RESPONSABLE_SEDE') and @acceso.alumnoEnSede(#alumnoId,'EDITAR')")
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
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER','ROLE_RESPONSABLE_SEDE') and @acceso.alumnoEnSede(#alumnoId,'EDITAR')")
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
			ResultadoGeneracionMensualidades resultado = productoAlumnoService
					.cargarMensualidadesMultiDeporte(nombreMensualidad);
			return ResponseEntity.ok(respuestaMasiva(
				"Mensualidades multi-deporte creadas correctamente para todos los alumnos.", resultado));
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
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER','ROLE_RESPONSABLE_SEDE') and @acceso.alumnoEnSede(#alumnoId,'EDITAR')")
	public ResponseEntity<?> cargarMensualidadIndividualPorDeporte(
			@RequestParam Long alumnoId,
			@RequestParam String deporte,
			@RequestBody String nombreMensualidad,
			@RequestParam(defaultValue = "false") boolean forzar,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaAsignacion) {
		try {
			List<CuantiaPersonalizadaSuperiorDTO> superiores = productoAlumnoService
					.cargarMensualidadIndividualPorDeporte(alumnoId, deporte, nombreMensualidad, forzar, fechaAsignacion);
			return ResponseEntity.ok(conCuantiasSuperiores(Map.of("mensaje",
				"Mensualidad individual creada correctamente para " + deporte + "."), superiores));
		} catch (MensualidadIncompletaException ex) {
			return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(Map.of(
						"mensaje", ex.getMessage(),
						"accion", "completar",
						"existentes", Map.of(
								"mensualidades", ex.getMensualidadesExistentes(),
								"tarifasCompetidor", ex.getTarifasCompetidorExistentes()
						),
						"faltantes", Map.of(
								"mensualidades", ex.getMensualidadesFaltantes(),
								"tarifasCompetidor", ex.getTarifasCompetidorFaltantes()
						)
				));
		} catch (IllegalStateException ex) {
			return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(Map.of("mensaje", ex.getMessage()));
		} catch (IllegalArgumentException ex) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(Map.of("mensaje", ex.getMessage()));
		} catch (Exception ex) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of("mensaje", "Ocurrió un error al crear la mensualidad.", "detalle", ex.getMessage()));
		}
	}

	/**
	 * Respuesta de una generación masiva: el mensaje de siempre y, desde la entrega 6-3 (D6-23, aditivo),
	 * {@code alumnosOmitidosSedeInactiva}: alumnos saltados por ser de una sede inactiva (0 si todas están activas).
	 */
	private static Map<String, Object> respuestaMasiva(String mensaje, int omitidos) {
		Map<String, Object> respuesta = new java.util.LinkedHashMap<>();
		respuesta.put("mensaje", mensaje);
		respuesta.put("alumnosOmitidosSedeInactiva", omitidos);
		return respuesta;
	}

	/** Respuesta de una generación masiva de mensualidades: la de siempre más la lista de la 6-4 si no está vacía. */
	private static Map<String, ?> respuestaMasiva(String mensaje, ResultadoGeneracionMensualidades resultado) {
		return conCuantiasSuperiores(respuestaMasiva(mensaje, resultado.alumnosOmitidosSedeInactiva()),
				resultado.alumnosCuantiaPersonalizadaSuperior());
	}

	/**
	 * Entrega 6-4 (D6-28, aditivo): {@code alumnosCuantiaPersonalizadaSuperior}, mensualidades creadas con una cuantía
	 * personalizada mayor que la tarifa de la sede (se cobra la personalizada), para revisarlas. SOLO si hay alguna: sin
	 * tarifas por sede (Moiskimdo) la lista siempre está vacía y la respuesta es la de antes, sin el campo.
	 */
	private static Map<String, ?> conCuantiasSuperiores(Map<String, ?> respuesta,
			List<CuantiaPersonalizadaSuperiorDTO> superiores) {
		if (superiores == null || superiores.isEmpty()) {
			return respuesta;
		}
		Map<String, Object> ampliada = new java.util.LinkedHashMap<>(respuesta);
		ampliada.put("alumnosCuantiaPersonalizadaSuperior", superiores);
		return ampliada;
	}
}
