package com.taemoi.project.controladores;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.taemoi.project.dtos.ProductoAlumnoDTO;
import com.taemoi.project.servicios.ProductoAlumnoService;

@RestController
@RequestMapping("/api/productos-alumno")
public class ProductoAlumnoController {

    @Autowired
    private ProductoAlumnoService productoAlumnoService;
    
    @PostMapping("/alumno/{alumnoId}/producto/{productoId}")
    @PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
    public ResponseEntity<ProductoAlumnoDTO> asignarProductoAAlumno(
            @PathVariable Long alumnoId,
            @PathVariable Long productoId,
            @RequestBody ProductoAlumnoDTO detallesDTO) {

        ProductoAlumnoDTO productoAlumnoDTO = productoAlumnoService.asignarProductoAAlumno(alumnoId, productoId, detallesDTO);
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
    public ResponseEntity<ProductoAlumnoDTO> actualizarProductoAlumno(
            @PathVariable Long id,
            @RequestBody ProductoAlumnoDTO detallesDTO) {

        ProductoAlumnoDTO actualizado = productoAlumnoService.actualizarProductoAlumno(id, detallesDTO);
        return ResponseEntity.ok(actualizado);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
    public ResponseEntity<Void> eliminarProductoAlumno(@PathVariable Long id) {
        productoAlumnoService.eliminarProductoAlumno(id);
        return ResponseEntity.noContent().build();
    }
}
