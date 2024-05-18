package com.taemoi.project.controladores;

import java.util.List;
import java.util.Optional;

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
import com.taemoi.project.dtos.response.GrupoConAlumnosDTO;
import com.taemoi.project.errores.grupo.GrupoNoEncontradoException;
import com.taemoi.project.errores.turno.TurnoNoEncontradoException;
import com.taemoi.project.servicios.GrupoService;

/**
 * Controlador REST que gestiona las operaciones relacionadas con los grupos en
 * el sistema. Proporciona endpoints para recuperar, crear, actualizar y
 * eliminar información de los grupos, a la par que añadir a alumnos a dichos
 * grupos. Se requiere que el usuario tenga el rol
 * ROLE_USER o ROLE_ADMIN para acceder a estos endpoints.
 */
@RestController
@RequestMapping("/api/grupos")
public class GrupoController {

	/**
     * Inyección del servicio de grupo.
     */
    @Autowired
    private GrupoService grupoService;
    
    /**
     * Obtiene todos los grupos con sus respectivos alumnos.
     *
     * @return Una lista de objetos GrupoConAlumnosDTO que representan todos los grupos y sus alumnos.
     */
    @GetMapping
    @PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
    public List<GrupoConAlumnosDTO> obtenerTodosLosGrupos() {
        return grupoService.obtenerTodosLosGrupos();
    }

    /**
     * Obtiene un grupo con sus alumnos por su ID.
     *
     * @param id El ID del grupo a obtener.
     * @return ResponseEntity que contiene el objeto GrupoConAlumnosDTO si se encuentra el grupo; de lo contrario, un ResponseEntity con estado NOT_FOUND.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
    public ResponseEntity<GrupoConAlumnosDTO> obtenerGrupoConAlumnosPorId(@PathVariable @NonNull Long id) {
        Optional<GrupoConAlumnosDTO> grupoDTO = grupoService.obtenerGrupoConAlumnosPorId(id);
        return grupoDTO.map(value -> new ResponseEntity<>(value, HttpStatus.OK))
                .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }
    
    /**
     * Obtiene los turnos asignados a un grupo por su ID.
     *
     * @param grupoId El ID del grupo.
     * @return ResponseEntity que contiene una lista de objetos TurnoDTO si se encuentran turnos; de lo contrario, un ResponseEntity con estado NOT_FOUND.
     */
	@GetMapping("/{grupoId}/turnos")
	@PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN') || hasRole('ROLE_USER')")
	public ResponseEntity<?> obtenerTurnosDelGrupo(@PathVariable @NonNull Long grupoId) {
	    List<TurnoDTO> turnosDTO = grupoService.obtenerTurnosDelGrupo(grupoId);
	    if (!turnosDTO.isEmpty()) {
	        return ResponseEntity.ok(turnosDTO);
	    } else {
	        return ResponseEntity.notFound().build();
	    }
	}

	/**
	 * Crea un nuevo grupo.
	 *
	 * @param grupoDTO El objeto GrupoConAlumnosDTO con los datos del nuevo grupo.
	 * @return ResponseEntity que contiene el objeto GrupoConAlumnosDTO que representa el grupo creado, con estado CREATED.
	 */
    @PostMapping
    @PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
    public ResponseEntity<GrupoConAlumnosDTO> crearGrupo(@RequestBody GrupoConAlumnosDTO grupoDTO) {
        grupoDTO.setAlumnos(null);
        GrupoConAlumnosDTO nuevoGrupoDTO = grupoService.crearGrupo(grupoDTO);
        return new ResponseEntity<>(nuevoGrupoDTO, HttpStatus.CREATED);
    }
    
    /**
     * Actualiza un grupo existente por su ID.
     *
     * @param id El ID del grupo a actualizar.
     * @param grupoDTO El objeto GrupoConAlumnosDTO con los datos actualizados del grupo.
     * @return ResponseEntity que contiene el objeto GrupoConAlumnosDTO que representa el grupo actualizado, con estado OK; o un ResponseEntity con estado NOT_FOUND si no se encuentra el grupo.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
    public ResponseEntity<GrupoConAlumnosDTO> actualizarGrupo(@PathVariable @NonNull Long id, @RequestBody GrupoConAlumnosDTO grupoDTO) {
        GrupoConAlumnosDTO grupoActualizadoDTO = grupoService.actualizarGrupo(id, grupoDTO);
        if (grupoActualizadoDTO != null) {
            return new ResponseEntity<>(grupoActualizadoDTO, HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    /**
     * Elimina un grupo por su ID.
     *
     * @param id El ID del grupo a eliminar.
     * @return ResponseEntity con estado NO_CONTENT.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
    public ResponseEntity<Void> eliminarGrupo(@PathVariable @NonNull Long id) {
        grupoService.eliminarGrupo(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
    
    /**
     * Agrega un alumno a un grupo.
     *
     * @param grupoId El ID del grupo.
     * @param alumnoId El ID del alumno.
     * @return ResponseEntity con estado OK si se agrega el alumno al grupo; de lo contrario, un ResponseEntity con estado NOT_FOUND.
     */
    @PostMapping("/{grupoId}/alumnos/{alumnoId}")
    @PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
    public ResponseEntity<?> agregarAlumnoAGrupo(@PathVariable @NonNull Long grupoId, @PathVariable @NonNull Long alumnoId) {
        Optional<GrupoConAlumnosDTO> grupoOptional = grupoService.obtenerGrupoConAlumnosPorId(grupoId);
        if (grupoOptional.isPresent()) {
            grupoService.agregarAlumnoAGrupo(grupoId, alumnoId);
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Elimina un alumno de un grupo.
     *
     * @param grupoId El ID del grupo.
     * @param alumnoId El ID del alumno.
     * @return ResponseEntity con estado OK si se elimina el alumno del grupo; de lo contrario, un ResponseEntity con estado NOT_FOUND.
     */
    @DeleteMapping("/{grupoId}/alumnos/{alumnoId}")
    @PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
    public ResponseEntity<?> eliminarAlumnoDeGrupo(@PathVariable @NonNull Long grupoId, @PathVariable @NonNull Long alumnoId) {
        Optional<GrupoConAlumnosDTO> grupoOptional = grupoService.obtenerGrupoConAlumnosPorId(grupoId);
        if (grupoOptional.isPresent()) {
            grupoService.eliminarAlumnoDeGrupo(grupoId, alumnoId);
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * Agrega un turno a un grupo.
     *
     * @param grupoId El ID del grupo.
     * @param turnoId El ID del turno.
     * @return ResponseEntity con estado OK si se agrega el turno al grupo; de lo contrario, un ResponseEntity con estado NOT_FOUND.
     */
    @PostMapping("/{grupoId}/turnos/{turnoId}")
    @PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
    public ResponseEntity<?> agregarTurnoAGrupo(@PathVariable @NonNull Long grupoId, @PathVariable @NonNull Long turnoId) {
        try {
            grupoService.agregarTurnoAGrupo(grupoId, turnoId);
            return ResponseEntity.ok().build();
        } catch (GrupoNoEncontradoException | TurnoNoEncontradoException e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * Elimina un turno de un grupo.
     *
     * @param grupoId El ID del grupo.
     * @param turnoId El ID del turno.
     * @return ResponseEntity con estado OK si se elimina el turno del grupo; de lo contrario, un ResponseEntity con estado NOT_FOUND.
     */
    @DeleteMapping("/{grupoId}/turnos/{turnoId}")
    @PreAuthorize("hasRole('ROLE_MANAGER') || hasRole('ROLE_ADMIN')")
    public ResponseEntity<?> eliminarTurnoDeGrupo(@PathVariable @NonNull Long grupoId, @PathVariable @NonNull Long turnoId) {
        try {
        	grupoService.eliminarTurnoDeGrupo(grupoId, turnoId);
            return ResponseEntity.ok().build();
        } catch (GrupoNoEncontradoException | TurnoNoEncontradoException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
