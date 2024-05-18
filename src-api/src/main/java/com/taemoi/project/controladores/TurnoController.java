package com.taemoi.project.controladores;

import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
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
import com.taemoi.project.errores.turno.TurnoNoEncontradoException;
import com.taemoi.project.servicios.TurnoService;

/**
 * Controlador REST que gestiona las operaciones relacionadas con los turnos en
 * el sistema. Proporciona endpoints para recuperar, crear, actualizar y
 * eliminar información de los turnos, a la par que añadir dichos turnos a sus correspondientes grupos. 
 * Se requiere que el usuario tenga el rol ROLE_USER o ROLE_ADMIN para acceder a estos endpoints.
 */
@RestController
@RequestMapping("/api/turnos")
public class TurnoController {
	
	/**
     * Inyección del servicio de turno.
     */
	@Autowired
	private TurnoService turnoService;
	
	/**
	 * Obtiene todos los turnos disponibles.
	 *
	 * @return ResponseEntity con la lista de objetos Turno y estado HTTP OK si tiene éxito.
	 */
    @GetMapping
    @PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
    public ResponseEntity<List<Turno>> obtenerTurnos() {
        List<Turno> turnos = turnoService.listarTurnos();
        return new ResponseEntity<>(turnos, HttpStatus.OK);
    }
    
    /**
     * Obtiene un turno por su ID.
     *
     * @param turnoId El ID del turno a obtener.
     * @return ResponseEntity con el objeto TurnoDTO correspondiente al ID especificado y estado HTTP OK si tiene éxito,
     *         o estado HTTP NOT FOUND si el turno no se encuentra.
     */
    @GetMapping("/{turnoId}")
    @PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
    public ResponseEntity<TurnoDTO> obtenerTurnoPorId(@PathVariable @NonNull Long turnoId) {
        try {
            Turno turno = turnoService.obtenerTurnoPorId(turnoId);
            return ResponseEntity.ok(TurnoDTO.deTurno(turno));
        } catch (TurnoNoEncontradoException e) {
            return ResponseEntity.notFound().build();
        }
    }
	
    /**
     * Obtiene todos los turnos disponibles en forma de objetos DTO.
     *
     * @return ResponseEntity con la lista de objetos TurnoDTO y estado HTTP OK si tiene éxito.
     */
    @GetMapping("/dto")
    public ResponseEntity<List<TurnoDTO>> obtenerTurnosDTO() {
        List<TurnoDTO> turnosDTO = turnoService.listarTurnosDTO();
        return new ResponseEntity<>(turnosDTO, HttpStatus.OK);
    }
    
    /**
     * Crea un nuevo turno sin asignarlo a ningún grupo.
     *
     * @param turnoDTO Los datos del nuevo turno.
     * @return ResponseEntity con estado HTTP CREATED si el turno se crea correctamente.
     */
    @PostMapping("/crear")
    @PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
    public ResponseEntity<?> crearTurnoSinGrupo(@RequestBody TurnoDTO turnoDTO) {
        turnoService.crearTurnoSinGrupo(turnoDTO);
        return new ResponseEntity<>(HttpStatus.CREATED);
    }
    
    /**
     * Crea un nuevo turno y lo asigna a un grupo según el día de la semana.
     *
     * @param turnoDTO Los datos del nuevo turno.
     * @return ResponseEntity con estado HTTP CREATED si el turno se crea correctamente.
     */
    @PostMapping("/crear-asignando-grupo")
    @PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
    public ResponseEntity<?> crearTurnoConGrupo(@RequestBody TurnoDTO turnoDTO) {
        turnoService.crearTurnoYAsignarAGrupo(turnoDTO);
        return new ResponseEntity<>(HttpStatus.CREATED);
    }
    
    /**
     * Actualiza los detalles de un turno existente.
     *
     * @param turnoId  El ID del turno a actualizar.
     * @param turnoDTO Los nuevos datos del turno.
     * @return ResponseEntity con el objeto TurnoDTO actualizado y estado HTTP OK si tiene éxito,
     *         o estado HTTP NOT FOUND si el turno no se encuentra.
     */
    @PutMapping("/{turnoId}")
    @PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
    public ResponseEntity<TurnoDTO> actualizarTurno(@PathVariable @NonNull Long turnoId, @RequestBody TurnoDTO turnoDTO) {
        TurnoDTO turnoActualizadoDTO = turnoService.actualizarTurno(turnoId, turnoDTO);
        if (turnoActualizadoDTO != null) {
            return ResponseEntity.ok(turnoActualizadoDTO);
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * Elimina un turno por su ID.
     *
     * @param turnoId El ID del turno a eliminar.
     * @return ResponseEntity con mensaje de confirmación y estado HTTP OK si el turno se elimina correctamente,
     *         o estado HTTP NOT FOUND si el turno no se encuentra, o estado HTTP INTERNAL SERVER ERROR si hay un error interno.
     */
    @DeleteMapping("/{turnoId}")
    @PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
    public ResponseEntity<?> eliminarTurno(@PathVariable @NonNull Long turnoId) {
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
