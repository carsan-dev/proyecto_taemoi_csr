package com.taemoi.project.controladores;

import java.util.List;
import java.util.Optional;

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

import com.taemoi.project.entidades.Producto;
import com.taemoi.project.servicios.ProductoService;

@RestController
@RequestMapping("/api/productos")
public class ProductoController {

	@Autowired
	private ProductoService productoService;
	
    @GetMapping
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
	
    // Endpoint para agregar producto a un alumno
    @PostMapping("/alumno/{alumnoId}")
    @PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
    public ResponseEntity<?> agregarProductoAAlumno(@PathVariable Long alumnoId, @RequestBody Producto producto) {
        Producto nuevoProducto = productoService.agregarProductoAAlumno(alumnoId, producto);
        return new ResponseEntity<>(nuevoProducto, HttpStatus.CREATED);
    }

    // Endpoint para obtener todos los productos de un alumno
    @GetMapping("/alumno/{alumnoId}")
    @PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
    public ResponseEntity<?> obtenerProductosDeAlumno(@PathVariable Long alumnoId) {
        List<Producto> productos = productoService.obtenerProductosDeAlumno(alumnoId);
        return ResponseEntity.ok(productos);
    }

    // Endpoint para actualizar el estado de pago de un producto
    @PutMapping("/{productoId}/estado")
    @PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
    public ResponseEntity<?> actualizarEstadoPago(@PathVariable Long productoId, @RequestParam boolean pagado) {
        Producto producto = productoService.actualizarEstadoPago(productoId, pagado);
        return ResponseEntity.ok(producto);
    }
    
    @PostMapping("/reserva/{alumnoId}")
    @PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
    public ResponseEntity<Producto> crearReservaPlaza(@PathVariable Long alumnoId) {
        Producto reservaPlaza = productoService.crearReservaPlaza(alumnoId);
        return ResponseEntity.status(HttpStatus.CREATED).body(reservaPlaza);
    }
}
