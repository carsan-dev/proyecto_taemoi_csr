package com.taemoi.project.controllers;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.taemoi.project.dtos.request.CajaRequests;
import com.taemoi.project.dtos.response.CajaDTOs;
import com.taemoi.project.services.CajaService;

/**
 * Caja de sede (pieza 6b-2). Sin pantalla todavía (la 6b-3).
 *
 * <ul>
 * <li>{@code /api/caja/profesor/**}: solo {@code ROLE_PROFESOR}, por sus grupos ({@code @acceso.cargoDeMisGrupos}) o
 * por ser suyo el cobro ({@code @acceso.cobroPropioDelProfesor}), o por su alumno ({@code @acceso.alumnoDeMisGrupos}).
 * Filas y solo SUS propios totales (decisión 69).</li>
 * <li>{@code /api/caja/sedes/{sedeId}/**}: responsable de esa sede, gestor y propietario ({@code @acceso.cajaDeSede}).</li>
 * <li>Anular una entrega o un movimiento: solo el propietario ({@code ROLE_ADMIN}).</li>
 * </ul>
 * Errores con el formato de {@code GlobalExceptionHandler}: 400 {@code {"mensaje"}} (datos no válidos), 404 (no existe en
 * la escuela), 409 (estado: ya cobrado, ya entregado, ya anulado, concurrencia) y 403.
 */
@RestController
@RequestMapping("/api/caja")
public class CajaController {

	private final CajaService caja;

	public CajaController(CajaService caja) {
		this.caja = caja;
	}

	// ------------------------------------------------------------------ profesor

	@GetMapping("/profesor/cargos-pendientes")
	@PreAuthorize("hasAuthority('ROLE_PROFESOR')")
	public ResponseEntity<List<CajaDTOs.CargoPendienteProfesor>> cargosPendientes() {
		return ResponseEntity.ok(caja.cargosPendientesDelProfesor());
	}

	@PostMapping("/profesor/cargos/{cargoId}/cobrar")
	@PreAuthorize("@acceso.cargoDeMisGrupos(#cargoId)")
	public ResponseEntity<CajaDTOs.CobroCaja> cobrar(@PathVariable Long cargoId) {
		return ResponseEntity.status(HttpStatus.CREATED).body(caja.cobrarComoProfesor(cargoId));
	}

	@PostMapping("/profesor/cobros/{cobroId}/deshacer")
	@PreAuthorize("@acceso.cobroPropioDelProfesor(#cobroId)")
	public ResponseEntity<Void> deshacer(@PathVariable Long cobroId,
			@RequestBody(required = false) CajaRequests.Deshacer peticion) {
		caja.deshacerCobroDelProfesor(cobroId, peticion == null ? null : peticion.motivo());
		return ResponseEntity.noContent().build();
	}

	/** Productos del catálogo que el profesor puede apuntar y cobrar (lista blanca de la 6b-2). */
	@GetMapping("/profesor/productos")
	@PreAuthorize("hasAuthority('ROLE_PROFESOR')")
	public ResponseEntity<List<CajaDTOs.ProductoVendible>> productosVendibles() {
		return ResponseEntity.ok(caja.productosVendiblesPorProfesor());
	}

	/**
	 * Apunta y cobra en efectivo un producto del catálogo. Sin cuerpo: precio y concepto son siempre los del catálogo
	 * (lo que se envíe no se lee).
	 */
	@PostMapping("/profesor/alumnos/{alumnoId}/productos/{productoId}/cobrar")
	@PreAuthorize("@acceso.alumnoDeMisGrupos(#alumnoId)")
	public ResponseEntity<CajaDTOs.CobroCaja> apuntarYCobrarProducto(@PathVariable Long alumnoId,
			@PathVariable Long productoId) {
		return ResponseEntity.status(HttpStatus.CREATED).body(caja.apuntarYCobrarProductoComoProfesor(alumnoId,
				productoId));
	}

	@GetMapping("/profesor/cobros-pendientes-entrega")
	@PreAuthorize("hasAuthority('ROLE_PROFESOR')")
	public ResponseEntity<CajaDTOs.MisCobrosPendientes> cobrosPendientesDeEntrega() {
		return ResponseEntity.ok(caja.cobrosPendientesDeEntregaDelProfesor());
	}

	// ------------------------------------------------------------------ caja de sede

	@GetMapping("/sedes/{sedeId}/resumen")
	@PreAuthorize("@acceso.cajaDeSede(#sedeId)")
	public ResponseEntity<CajaDTOs.ResumenCajaSede> resumen(@PathVariable Long sedeId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
		return ResponseEntity.ok(caja.resumen(sedeId, desde, hasta));
	}

	@GetMapping("/sedes/{sedeId}/pendiente-entrega")
	@PreAuthorize("@acceso.cajaDeSede(#sedeId)")
	public ResponseEntity<List<CajaDTOs.PendienteCobrador>> pendientePorCobrador(@PathVariable Long sedeId) {
		return ResponseEntity.ok(caja.pendientePorCobrador(sedeId));
	}

	@PostMapping("/sedes/{sedeId}/entregas")
	@PreAuthorize("@acceso.cajaDeSede(#sedeId)")
	public ResponseEntity<CajaDTOs.EntregaCaja> registrarEntrega(@PathVariable Long sedeId,
			@RequestBody CajaRequests.RegistrarEntrega peticion) {
		return ResponseEntity.status(HttpStatus.CREATED).body(caja.registrarEntrega(sedeId, peticion));
	}

	@GetMapping("/sedes/{sedeId}/entregas")
	@PreAuthorize("@acceso.cajaDeSede(#sedeId)")
	public ResponseEntity<Page<CajaDTOs.EntregaCaja>> historialDeEntregas(@PathVariable Long sedeId,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "25") int size) {
		return ResponseEntity.ok(caja.historialDeEntregas(sedeId, page, size));
	}

	@PostMapping("/entregas/{entregaId}/anular")
	@PreAuthorize("hasAuthority('ROLE_ADMIN')")
	public ResponseEntity<CajaDTOs.EntregaCaja> anularEntrega(@PathVariable Long entregaId,
			@RequestBody(required = false) CajaRequests.Anulacion peticion) {
		return ResponseEntity.ok(caja.anularEntrega(entregaId, peticion));
	}

	@PostMapping("/sedes/{sedeId}/movimientos")
	@PreAuthorize("@acceso.cajaDeSede(#sedeId)")
	public ResponseEntity<CajaDTOs.MovimientoCaja> crearMovimiento(@PathVariable Long sedeId,
			@RequestBody CajaRequests.CrearMovimiento peticion) {
		return ResponseEntity.status(HttpStatus.CREATED).body(caja.crearMovimiento(sedeId, peticion));
	}

	@GetMapping("/sedes/{sedeId}/movimientos")
	@PreAuthorize("@acceso.cajaDeSede(#sedeId)")
	public ResponseEntity<List<CajaDTOs.MovimientoCaja>> movimientos(@PathVariable Long sedeId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
		return ResponseEntity.ok(caja.movimientosDeSede(sedeId, desde, hasta));
	}

	@PostMapping("/movimientos/{movimientoId}/anular")
	@PreAuthorize("hasAuthority('ROLE_ADMIN')")
	public ResponseEntity<CajaDTOs.MovimientoCaja> anularMovimiento(@PathVariable Long movimientoId,
			@RequestBody(required = false) CajaRequests.Anulacion peticion) {
		return ResponseEntity.ok(caja.anularMovimiento(movimientoId, peticion));
	}
}
