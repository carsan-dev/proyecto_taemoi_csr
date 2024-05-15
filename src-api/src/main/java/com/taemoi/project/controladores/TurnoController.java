package com.taemoi.project.controladores;

import java.util.List;
import java.util.NoSuchElementException;

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

import com.taemoi.project.dtos.TurnoDTO;
import com.taemoi.project.entidades.Turno;
import com.taemoi.project.errores.grupo.GrupoNoEncontradoException;
import com.taemoi.project.errores.turno.TurnoNoEncontradoException;
import com.taemoi.project.servicios.TurnoService;

@RestController
@RequestMapping("/api/turnos")
public class TurnoController {
	
	@Autowired
	private TurnoService turnoService;
	
    @GetMapping
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<List<Turno>> obtenerTurnos() {
        List<Turno> turnos = turnoService.listarTurnos();
        return new ResponseEntity<>(turnos, HttpStatus.OK);
    }
    
    @GetMapping("/{turnoId}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<TurnoDTO> obtenerTurnoPorId(@PathVariable Long turnoId) {
        try {
            Turno turno = turnoService.obtenerTurnoPorId(turnoId);
            return ResponseEntity.ok(TurnoDTO.deTurno(turno));
        } catch (TurnoNoEncontradoException e) {
            return ResponseEntity.notFound().build();
        }
    }
	
    @GetMapping("/dto")
    public ResponseEntity<List<TurnoDTO>> obtenerTurnosDTO() {
        List<TurnoDTO> turnosDTO = turnoService.listarTurnosDTO();
        return new ResponseEntity<>(turnosDTO, HttpStatus.OK);
    }
    
    @PostMapping("/crear")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<?> crearTurnoSinGrupo(@RequestBody TurnoDTO turnoDTO) {
        turnoService.crearTurnoSinGrupo(turnoDTO);
        return new ResponseEntity<>(HttpStatus.CREATED);
    }
    
    @PostMapping("/crear-asignando-grupo")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<?> crearTurnoConGrupo(@RequestBody TurnoDTO turnoDTO) {
        turnoService.crearTurnoYAsignarAGrupo(turnoDTO);
        return new ResponseEntity<>(HttpStatus.CREATED);
    }
    
    @PutMapping("/{turnoId}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<TurnoDTO> actualizarTurno(@PathVariable Long turnoId, @RequestBody TurnoDTO turnoDTO) {
        TurnoDTO turnoActualizadoDTO = turnoService.actualizarTurno(turnoId, turnoDTO);
        if (turnoActualizadoDTO != null) {
            return ResponseEntity.ok(turnoActualizadoDTO);
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    
    @DeleteMapping("/{turnoId}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<?> eliminarTurno(@PathVariable Long turnoId) {
        try {
            turnoService.eliminarTurno(turnoId);
            return ResponseEntity.ok("Turno eliminado correctamente");
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
