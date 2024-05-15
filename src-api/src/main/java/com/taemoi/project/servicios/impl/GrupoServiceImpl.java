package com.taemoi.project.servicios.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.taemoi.project.dtos.TurnoDTO;
import com.taemoi.project.dtos.response.AlumnoParaGrupoDTO;
import com.taemoi.project.dtos.response.GrupoConAlumnosDTO;
import com.taemoi.project.dtos.response.GrupoResponseDTO;
import com.taemoi.project.entidades.Alumno;
import com.taemoi.project.entidades.Grupo;
import com.taemoi.project.entidades.Turno;
import com.taemoi.project.errores.grupo.GrupoNoEncontradoException;
import com.taemoi.project.repositorios.AlumnoRepository;
import com.taemoi.project.repositorios.GrupoRepository;
import com.taemoi.project.servicios.GrupoService;

@Service
public class GrupoServiceImpl implements GrupoService {
	
    @Autowired
    private GrupoRepository grupoRepository;
    
    @Autowired
    private AlumnoRepository alumnoRepository;

    @Override
    public List<GrupoConAlumnosDTO> obtenerTodosLosGrupos() {
        List<Grupo> grupos = grupoRepository.findAll();
        return grupos.stream()
                .map(this::convertirEntidadADTO)
                .collect(Collectors.toList());
    }
    
    @Override
    public Optional<GrupoConAlumnosDTO> obtenerGrupoConAlumnosPorId(Long id) {
        Optional<Grupo> grupoOptional = grupoRepository.findById(id);
        return grupoOptional.map(this::convertirEntidadADTO);
    }

    @Override
    public GrupoConAlumnosDTO crearGrupo(GrupoConAlumnosDTO grupoDTO) {
        Grupo grupo = convertirDTOAEntidad(grupoDTO);
        grupo = grupoRepository.save(grupo);
        return convertirEntidadADTO(grupo);
    }

    @Override
    public GrupoConAlumnosDTO actualizarGrupo(Long id, GrupoConAlumnosDTO grupoDTO) {
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


	@Override
	public void eliminarGrupo(Long id) {
        grupoRepository.deleteById(id);
	}
	
	@Override
	public void agregarAlumnoAGrupo(Long grupoId, Long alumnoId) {
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
	
	@Override
	public void eliminarAlumnoDeGrupo(Long grupoId, Long alumnoId) {
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
	
	@Override
	public List<TurnoDTO> obtenerTurnosDelGrupo(Long grupoId) {
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

	@Override
	public List<GrupoResponseDTO> obtenerGruposDelAlumno(Long alumnoId) {
	    Alumno alumno = alumnoRepository.findById(alumnoId).orElse(null);
	    if (alumno != null) {
	        return alumno.getGrupos().stream()
	                .map(this::convertirGrupoADTO)
	                .collect(Collectors.toList());
	    }
	    return Collections.emptyList();
	}
    
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
	
	private GrupoResponseDTO convertirGrupoADTO(Grupo grupo) {
	    GrupoResponseDTO grupoDTO = new GrupoResponseDTO();
	    grupoDTO.setNombre(grupo.getNombre());
	    return grupoDTO;
	}
}
