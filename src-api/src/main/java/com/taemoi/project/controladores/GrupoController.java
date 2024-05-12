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
import org.springframework.web.bind.annotation.RestController;

import com.taemoi.project.dtos.AlumnoGrupoDTO;
import com.taemoi.project.dtos.GrupoDTO;
import com.taemoi.project.entidades.Alumno;
import com.taemoi.project.repositorios.AlumnoRepository;
import com.taemoi.project.repositorios.GrupoRepository;
import com.taemoi.project.servicios.GrupoService;

@RestController
@RequestMapping("/api/grupos")
public class GrupoController {

    @Autowired
    private GrupoService grupoService;
    
    @Autowired
    private AlumnoRepository alumnoRepository;
    
    @Autowired
    private GrupoRepository grupoRepository;
    
    @GetMapping
    public List<GrupoDTO> obtenerTodosLosGrupos() {
        return grupoService.obtenerTodosLosGrupos();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<GrupoDTO> obtenerGrupoPorId(@PathVariable Long id) {
        Optional<GrupoDTO> grupoDTO = grupoService.obtenerGrupoPorId(id);
        return grupoDTO.map(value -> new ResponseEntity<>(value, HttpStatus.OK))
                .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @PostMapping
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<GrupoDTO> crearGrupo(@RequestBody GrupoDTO grupoDTO) {
        grupoDTO.setAlumnos(null);
        GrupoDTO nuevoGrupoDTO = grupoService.crearGrupo(grupoDTO);
        return new ResponseEntity<>(nuevoGrupoDTO, HttpStatus.CREATED);
    }
    
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<GrupoDTO> actualizarGrupo(@PathVariable Long id, @RequestBody GrupoDTO grupoDTO) {
        GrupoDTO grupoActualizadoDTO = grupoService.actualizarGrupo(id, grupoDTO);
        if (grupoActualizadoDTO != null) {
            return new ResponseEntity<>(grupoActualizadoDTO, HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<Void> eliminarGrupo(@PathVariable Long id) {
        grupoService.eliminarGrupo(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
    
    @PostMapping("/{grupoId}/alumnos/{alumnoId}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<?> agregarAlumnoAGrupo(@PathVariable Long grupoId, @PathVariable Long alumnoId) {
        Optional<GrupoDTO> grupoOptional = grupoService.obtenerGrupoPorId(grupoId);
        if (grupoOptional.isPresent()) {
            grupoService.agregarAlumnoAGrupo(grupoId, alumnoId);
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{grupoId}/alumnos/{alumnoId}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<?> eliminarAlumnoDeGrupo(@PathVariable Long grupoId, @PathVariable Long alumnoId) {
        Optional<GrupoDTO> grupoOptional = grupoService.obtenerGrupoPorId(grupoId);
        if (grupoOptional.isPresent()) {
            grupoService.eliminarAlumnoDeGrupo(grupoId, alumnoId);
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}
