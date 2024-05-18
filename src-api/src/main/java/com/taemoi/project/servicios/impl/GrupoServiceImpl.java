package com.taemoi.project.servicios.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import com.taemoi.project.dtos.TurnoDTO;
import com.taemoi.project.dtos.response.AlumnoParaGrupoDTO;
import com.taemoi.project.dtos.response.GrupoConAlumnosDTO;
import com.taemoi.project.dtos.response.GrupoResponseDTO;
import com.taemoi.project.entidades.Alumno;
import com.taemoi.project.entidades.Grupo;
import com.taemoi.project.entidades.Turno;
import com.taemoi.project.errores.grupo.GrupoNoEncontradoException;
import com.taemoi.project.errores.turno.TurnoNoEncontradoException;
import com.taemoi.project.repositorios.AlumnoRepository;
import com.taemoi.project.repositorios.GrupoRepository;
import com.taemoi.project.repositorios.TurnoRepository;
import com.taemoi.project.servicios.GrupoService;

/**
 * Implementación del servicio para operaciones relacionadas con los grupos de alumnos.
 */
@Service
public class GrupoServiceImpl implements GrupoService {
	
	/**
     * Inyección del repositorio de grupo.
     */
    @Autowired
    private GrupoRepository grupoRepository;
    
	/**
     * Inyección del repositorio de alumno.
     */
    @Autowired
    private AlumnoRepository alumnoRepository;
    
	/**
     * Inyección del repositorio de turno.
     */
    @Autowired
    private TurnoRepository turnoRepository;

    /**
     * Obtiene todos los grupos.
     *
     * @return Una lista de objetos GrupoConAlumnosDTO que representan todos los grupos.
     */
    @Override
    public List<GrupoConAlumnosDTO> obtenerTodosLosGrupos() {
        List<Grupo> grupos = grupoRepository.findAll();
        return grupos.stream()
                .map(this::convertirEntidadADTO)
                .collect(Collectors.toList());
    }
    
    /**
     * Obtiene un grupo con sus alumnos por su ID.
     *
     * @param id El ID del grupo a obtener.
     * @return Un Optional que contiene el objeto GrupoConAlumnosDTO si se encuentra el grupo; de lo contrario, un Optional vacío.
     */
    @Override
    public Optional<GrupoConAlumnosDTO> obtenerGrupoConAlumnosPorId(@NonNull Long id) {
        Optional<Grupo> grupoOptional = grupoRepository.findById(id);
        return grupoOptional.map(this::convertirEntidadADTO);
    }

    /**
     * Crea un nuevo grupo.
     *
     * @param grupoDTO El objeto GrupoConAlumnosDTO con los datos del nuevo grupo.
     * @return El objeto GrupoConAlumnosDTO que representa el grupo creado.
     */
    @SuppressWarnings("null")
	@Override
    public GrupoConAlumnosDTO crearGrupo(GrupoConAlumnosDTO grupoDTO) {
        Grupo grupo = convertirDTOAEntidad(grupoDTO);
        grupo = grupoRepository.save(grupo);
        return convertirEntidadADTO(grupo);
    }

    /**
     * Actualiza un grupo existente.
     *
     * @param id El ID del grupo a actualizar.
     * @param grupoDTO El objeto GrupoConAlumnosDTO con los datos actualizados del grupo.
     * @return El objeto GrupoConAlumnosDTO que representa el grupo actualizado, o null si no se encuentra el grupo.
     */
    @Override
    public GrupoConAlumnosDTO actualizarGrupo(@NonNull Long id, GrupoConAlumnosDTO grupoDTO) {
        Optional<Grupo> grupoOptional = grupoRepository.findById(id);
        if (grupoOptional.isPresent()) {
            Grupo grupo = grupoOptional.get();
            grupo.setNombre(grupoDTO.getNombre());
            grupo = grupoRepository.save(grupo);
            
            return convertirEntidadADTO(grupo);
        } else {
            return null;
        }
    }

    /**
     * Elimina un grupo por su ID.
     *
     * @param id El ID del grupo a eliminar.
     */
	@Override
	public void eliminarGrupo(@NonNull Long id) {
        grupoRepository.deleteById(id);
	}
	
	/**
	 * Agrega un alumno a un grupo.
	 *
	 * @param grupoId El ID del grupo.
	 * @param alumnoId El ID del alumno.
	 * @throws GrupoNoEncontradoException Si el grupo o el alumno no existen.
	 */
	@Override
	public void agregarAlumnoAGrupo(@NonNull Long grupoId, @NonNull Long alumnoId) {
	    Optional<Grupo> grupoOptional = grupoRepository.findById(grupoId);
	    Optional<Alumno> alumnoOptional = alumnoRepository.findById(alumnoId);
	    
	    if (grupoOptional.isPresent() && alumnoOptional.isPresent()) {
	        Grupo grupo = grupoOptional.get();
	        Alumno alumno = alumnoOptional.get();
	        grupo.addAlumno(alumno);
	        grupoRepository.save(grupo);
	    } else {
	        throw new GrupoNoEncontradoException("El grupo con ID " + grupoId + " o el alumno con ID " + alumnoId + " no existe.");
	    }
	}
	
	/**
	 * Elimina un alumno de un grupo.
	 *
	 * @param grupoId El ID del grupo.
	 * @param alumnoId El ID del alumno.
	 * @throws GrupoNoEncontradoException Si el grupo o el alumno no existen.
	 */
	@Override
	public void eliminarAlumnoDeGrupo(@NonNull Long grupoId, @NonNull Long alumnoId) {
	    Optional<Grupo> grupoOptional = grupoRepository.findById(grupoId);
	    Optional<Alumno> alumnoOptional = alumnoRepository.findById(alumnoId);
	    
	    if (grupoOptional.isPresent() && alumnoOptional.isPresent()) {
	        Grupo grupo = grupoOptional.get();
	        Alumno alumno = alumnoOptional.get();
	        
	        grupo.removeAlumno(alumno);
	        grupoRepository.save(grupo);
	    } else {
	        throw new GrupoNoEncontradoException("El grupo o el alumno no existen.");
	    }
	}
	
	/**
	 * Agrega un turno a un grupo.
	 *
	 * @param grupoId El ID del grupo.
	 * @param turnoId El ID del turno.
	 * @throws GrupoNoEncontradoException Si el grupo o el turno no existen.
	 */
	@Override
	public void agregarTurnoAGrupo(@NonNull Long grupoId, @NonNull Long turnoId) {
	    Optional<Grupo> grupoOptional = grupoRepository.findById(grupoId);
	    Optional<Turno> turnoOptional = turnoRepository.findById(turnoId);
	    
	    if (grupoOptional.isPresent() && turnoOptional.isPresent()) {
	        Grupo grupo = grupoOptional.get();
	        Turno turno = turnoOptional.get();
	        grupo.getTurnos().add(turno);
	        turno.setGrupo(grupo);
	        grupoRepository.save(grupo);
	    } else {
	        throw new GrupoNoEncontradoException("El grupo con ID " + grupoId + " o el turno con ID " + turnoId + " no existe.");
	    }
	}
	
	/**
	 * Elimina un turno de un grupo.
	 *
	 * @param grupoId El ID del grupo.
	 * @param turnoId El ID del turno.
	 * @throws GrupoNoEncontradoException Si el grupo no existe.
	 * @throws TurnoNoEncontradoException Si el turno no está asignado al grupo.
	 */
	@Override
	public void eliminarTurnoDeGrupo(@NonNull Long grupoId, @NonNull Long turnoId) {
	    Optional<Grupo> grupoOptional = grupoRepository.findById(grupoId);
	    
	    if (grupoOptional.isPresent()) {
	        Grupo grupo = grupoOptional.get();
	        Optional<Turno> turnoOptional = grupo.getTurnos().stream()
	                                            .filter(turno -> turno.getId().equals(turnoId))
	                                            .findFirst();
	        
	        if (turnoOptional.isPresent()) {
	            Turno turno = turnoOptional.get();
	            grupo.getTurnos().remove(turno);
	            turno.setGrupo(null);
	            grupoRepository.save(grupo);
	        } else {
	            throw new TurnoNoEncontradoException("El turno con ID " + turnoId + " no está asignado al grupo con ID " + grupoId);
	        }
	    } else {
	        throw new GrupoNoEncontradoException("El grupo con ID " + grupoId + " no existe.");
	    }
	}
	
	/**
	 * Obtiene los turnos asignados a un grupo.
	 *
	 * @param grupoId El ID del grupo.
	 * @return Una lista de objetos TurnoDTO que representan los turnos del grupo.
	 */
	@Override
	public List<TurnoDTO> obtenerTurnosDelGrupo(@NonNull Long grupoId) {
	    Optional<Grupo> grupoOptional = grupoRepository.findById(grupoId);
	    if (grupoOptional.isPresent()) {
	        Grupo grupo = grupoOptional.get();
	        List<Turno> turnos = grupo.getTurnos();
	        List<TurnoDTO> turnoDTOs = new ArrayList<>();
	        for (Turno turno : turnos) {
	            turnoDTOs.add(TurnoDTO.deTurno(turno));
	        }
	        return turnoDTOs;
	    }
	    return Collections.emptyList();
	}

	/**
	 * Obtiene los grupos a los que pertenece un alumno.
	 *
	 * @param alumnoId El ID del alumno.
	 * @return Una lista de objetos GrupoResponseDTO que representan los grupos del alumno.
	 */
	@Override
	public List<GrupoResponseDTO> obtenerGruposDelAlumno(@NonNull Long alumnoId) {
	    Alumno alumno = alumnoRepository.findById(alumnoId).orElse(null);
	    if (alumno != null) {
	        return alumno.getGrupos().stream()
	                .map(this::convertirGrupoADTO)
	                .collect(Collectors.toList());
	    }
	    return Collections.emptyList();
	}
    
	/**
	 * Convierte una entidad Grupo a un DTO GrupoConAlumnosDTO.
	 *
	 * @param grupo La entidad Grupo a convertir.
	 * @return El objeto GrupoConAlumnosDTO que representa la entidad Grupo.
	 */
	@Override
	public GrupoConAlumnosDTO convertirEntidadADTO(Grupo grupo) {
	    GrupoConAlumnosDTO grupoDTO = new GrupoConAlumnosDTO();
	    grupoDTO.setId(grupo.getId());
	    grupoDTO.setNombre(grupo.getNombre());

	    List<AlumnoParaGrupoDTO> alumnosDTO = grupo.getAlumnos().stream()
	        .map(AlumnoParaGrupoDTO::deAlumno)
	        .collect(Collectors.toList());
	    grupoDTO.setAlumnos(alumnosDTO);

	    return grupoDTO;
	}

	/**
	 * Convierte un DTO GrupoConAlumnosDTO a una entidad Grupo.
	 *
	 * @param grupoDTO El objeto GrupoConAlumnosDTO a convertir.
	 * @return La entidad Grupo que representa el objeto GrupoConAlumnosDTO.
	 */
	@Override
	public Grupo convertirDTOAEntidad(GrupoConAlumnosDTO grupoDTO) {
	    Grupo grupo = new Grupo();
	    grupo.setId(grupoDTO.getId());
	    grupo.setNombre(grupoDTO.getNombre());

	    List<Alumno> alumnos = new ArrayList<>();
	    if (grupoDTO.getAlumnos() != null) {
	        alumnos = grupoDTO.getAlumnos().stream()
	            .map(alumnoGrupoDTO -> {
	                Alumno alumno = new Alumno();
	                alumno.setNombre(alumnoGrupoDTO.getNombre());
	                alumno.setApellidos(alumnoGrupoDTO.getApellidos());
	                return alumno;
	            })
	            .collect(Collectors.toList());
	    }
	    grupo.setAlumnos(alumnos);

	    return grupo;
	}
	
	/**
	 * Convierte una entidad Grupo a un DTO GrupoResponseDTO.
	 *
	 * @param grupo La entidad Grupo a convertir.
	 * @return El objeto GrupoResponseDTO que representa la entidad Grupo.
	 */
	private GrupoResponseDTO convertirGrupoADTO(Grupo grupo) {
	    GrupoResponseDTO grupoDTO = new GrupoResponseDTO();
	    grupoDTO.setNombre(grupo.getNombre());
	    return grupoDTO;
	}
}
