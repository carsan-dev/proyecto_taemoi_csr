package com.taemoi.project.controllers;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;

import com.taemoi.project.dtos.request.VendibleProfesorRequests;
import com.taemoi.project.entities.Producto;
import com.taemoi.project.entities.TipoProducto;
import com.taemoi.project.services.ProductoService;

@RestController
@RequestMapping("/api/productos")
public class ProductoController {

	@Autowired
	private ProductoService productoService;

	@GetMapping
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER','ROLE_RESPONSABLE_SEDE')")
	public ResponseEntity<?> obtenerProductos(@RequestParam(required = false) String concepto,
			@RequestParam(required = false) String tipo, @RequestParam(required = false) Boolean vendibleProfesor,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int size,
			@RequestParam(defaultValue = "id") String orderBy, @RequestParam(defaultValue = "asc") String order) {

		Sort sort = order.equalsIgnoreCase("asc") ? Sort.by(orderBy).ascending() : Sort.by(orderBy).descending();
		// Restar 1 a la página para ajustarla al índice basado en cero
		int pageIndex = page - 1;
		if (pageIndex < 0) {
			pageIndex = 0; // Asegurarse de que el índice no sea negativo
		}
		PageRequest pageRequest = PageRequest.of(pageIndex, size, sort);

		// Pieza 6b-3d: tipo y vendibleProfesor son opcionales; sin ellos, el comportamiento de siempre.
		TipoProducto tipoFiltro = null;
		if (tipo != null && !tipo.isBlank()) {
			try {
				tipoFiltro = TipoProducto.valueOf(tipo.trim().toUpperCase(java.util.Locale.ROOT));
			} catch (IllegalArgumentException e) {
				return ResponseEntity.badRequest().body(Map.of("mensaje", "El tipo de producto «" + tipo
						+ "» no es válido. Los tipos son: " + java.util.Arrays.toString(TipoProducto.values()) + "."));
			}
		}

		Page<Producto> productos;
		if (tipoFiltro != null || vendibleProfesor != null) {
			productos = productoService.buscarProductosFiltrados(concepto, tipoFiltro, vendibleProfesor, pageRequest);
		} else if (concepto != null && !concepto.isEmpty()) {
			productos = productoService.buscarProductosPorConcepto(concepto, pageRequest);
		} else {
			productos = productoService.obtenerProductosPaginados(pageRequest);
		}

		return ResponseEntity.ok(productos);
	}

	@GetMapping("/todos")
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER','ROLE_RESPONSABLE_SEDE')")
	public ResponseEntity<List<Producto>> obtenerTodosLosProductos() {
		List<Producto> productos = productoService.obtenerTodosLosProductos();
		return ResponseEntity.ok(productos);
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER','ROLE_RESPONSABLE_SEDE')")
	public ResponseEntity<Producto> obtenerProductoPorId(@PathVariable Long id) {
		Optional<Producto> productoOpt = productoService.obtenerProductoPorId(id);
		if (productoOpt.isPresent()) {
			return ResponseEntity.ok(productoOpt.get());
		} else {
			return ResponseEntity.notFound().build();
		}
	}

	@PostMapping
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<Producto> guardarProducto(@Valid @RequestBody Producto producto) {
		Producto nuevoProducto = productoService.guardarProducto(producto);
		return ResponseEntity.ok(nuevoProducto);
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> actualizarProducto(@PathVariable Long id, @Valid @RequestBody Producto productoDetalles) {
		try {
			Producto productoActualizado = productoService.actualizarProducto(id, productoDetalles);
			return ResponseEntity.ok(productoActualizado);
		} catch (org.springframework.web.server.ResponseStatusException e) {
			return ResponseEntity.status(e.getStatusCode()).body(java.util.Map.of("mensaje", e.getReason()));
		} catch (RuntimeException e) {
			return ResponseEntity.notFound().build();
		}
	}

	/**
	 * Pieza 6b-3c (decisión 185): marca o desmarca «Lo puede vender el profesor» sin reenviar la ficha. Devuelve el
	 * producto como {@code GET /api/productos/{id}}. 400 sin marca; 404 si no es de la escuela activa.
	 */
	@PatchMapping("/{id}/vendible-profesor")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> marcarVendibleProfesor(@PathVariable Long id,
			@RequestBody(required = false) VendibleProfesorRequests.Uno peticion) {
		try {
			return ResponseEntity.ok(productoService.marcarVendibleProfesor(id,
					peticion == null ? null : peticion.vendibleProfesor()));
		} catch (ResponseStatusException e) {
			return ResponseEntity.status(e.getStatusCode()).body(Map.of("mensaje", e.getReason()));
		}
	}

	/**
	 * Pieza 6b-3c (decisión 185): la misma marca a varios productos, todo o nada. Devuelve {@code {"actualizados": n}}
	 * (n = ids distintos). 400 por ids o marca no válidos; 404 si alguno no es de la escuela activa.
	 */
	@PatchMapping("/vendible-profesor")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<?> marcarVendibleProfesorEnBloque(
			@RequestBody(required = false) VendibleProfesorRequests.Bloque peticion) {
		try {
			int actualizados = productoService.marcarVendibleProfesor(peticion == null ? null : peticion.ids(),
					peticion == null ? null : peticion.vendibleProfesor());
			return ResponseEntity.ok(Map.of("actualizados", actualizados));
		} catch (ResponseStatusException e) {
			return ResponseEntity.status(e.getStatusCode()).body(Map.of("mensaje", e.getReason()));
		}
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<Void> eliminarProducto(@PathVariable Long id) {
		productoService.eliminarProducto(id);
		return ResponseEntity.noContent().build();
	}
}
