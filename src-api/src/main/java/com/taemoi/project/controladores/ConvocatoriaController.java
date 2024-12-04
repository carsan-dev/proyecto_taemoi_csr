package com.taemoi.project.controladores;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.taemoi.project.entidades.Convocatoria;
import com.taemoi.project.servicios.ConvocatoriaService;

@RestController
@RequestMapping("/api/convocatorias")
public class ConvocatoriaController {

    @Autowired
    private ConvocatoriaService convocatoriaService;

    @GetMapping
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
    public ResponseEntity<List<Convocatoria>> obtenerConvocatorias() {
        List<Convocatoria> convocatorias = convocatoriaService.obtenerConvocatorias();
        return ResponseEntity.ok(convocatorias);
    }

    @GetMapping("/{id}")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
    public ResponseEntity<Convocatoria> obtenerConvocatoriaPorId(@PathVariable Long id) {
        Convocatoria convocatoria = convocatoriaService.obtenerConvocatoriaPorId(id);
        return ResponseEntity.ok(convocatoria);
    }

    @PostMapping
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
    public ResponseEntity<Convocatoria> crearConvocatoria(@RequestBody Convocatoria convocatoria) {
        Convocatoria nuevaConvocatoria = convocatoriaService.crearConvocatoria(convocatoria);
        return ResponseEntity.status(HttpStatus.CREATED).body(nuevaConvocatoria);
    }
}
