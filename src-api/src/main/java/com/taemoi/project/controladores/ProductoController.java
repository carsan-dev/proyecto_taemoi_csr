package com.taemoi.project.controladores;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

import com.taemoi.project.entidades.Producto;
import com.taemoi.project.servicios.ProductoService;

@RestController
@RequestMapping("/api/productos")
public class ProductoController {

	@Autowired
	private ProductoService productoService;

	@GetMapping
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<Page<Producto>> obtenerProductos(@RequestParam(required = false) String concepto,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int size,
			@RequestParam(defaultValue = "id") String orderBy, @RequestParam(defaultValue = "asc") String order) {

		Sort sort = order.equalsIgnoreCase("asc") ? Sort.by(orderBy).ascending() : Sort.by(orderBy).descending();
		// Restar 1 a la página para ajustarla al índice basado en cero
		int pageIndex = page - 1;
		if (pageIndex < 0) {
			pageIndex = 0; // Asegurarse de que el índice no sea negativo
		}
		PageRequest pageRequest = PageRequest.of(pageIndex, size, sort);

		Page<Producto> productos;
		if (concepto != null && !concepto.isEmpty()) {
			productos = productoService.buscarProductosPorConcepto(concepto, pageRequest);
		} else {
			productos = productoService.obtenerProductosPaginados(pageRequest);
		}

		return ResponseEntity.ok(productos);
	}

	@GetMapping("/todos")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<List<Producto>> obtenerTodosLosProductos() {
		List<Producto> productos = productoService.obtenerTodosLosProductos();
		return ResponseEntity.ok(productos);
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
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
	public ResponseEntity<Producto> guardarProducto(@RequestBody Producto producto) {
		Producto nuevoProducto = productoService.guardarProducto(producto);
		return ResponseEntity.ok(nuevoProducto);
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<Producto> actualizarProducto(@PathVariable Long id, @RequestBody Producto productoDetalles) {
		try {
			Producto productoActualizado = productoService.actualizarProducto(id, productoDetalles);
			return ResponseEntity.ok(productoActualizado);
		} catch (RuntimeException e) {
			return ResponseEntity.notFound().build();
		}
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
	public ResponseEntity<Void> eliminarProducto(@PathVariable Long id) {
		productoService.eliminarProducto(id);
		return ResponseEntity.noContent().build();
	}
}
