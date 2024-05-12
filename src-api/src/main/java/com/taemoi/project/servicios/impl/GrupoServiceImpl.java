package com.taemoi.project.servicios.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.taemoi.project.dtos.AlumnoGrupoDTO;
import com.taemoi.project.dtos.GrupoDTO;
import com.taemoi.project.entidades.Alumno;
import com.taemoi.project.entidades.Grupo;
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
    public List<GrupoDTO> obtenerTodosLosGrupos() {
        List<Grupo> grupos = grupoRepository.findAll();
        return grupos.stream()
                .map(this::convertirEntidadADTO)
                .collect(Collectors.toList());
    }
    
    @Override
    public Optional<GrupoDTO> obtenerGrupoPorId(Long id) {
        Optional<Grupo> grupoOptional = grupoRepository.findById(id);
        return grupoOptional.map(this::convertirEntidadADTO);
    }

    @Override
    public GrupoDTO crearGrupo(GrupoDTO grupoDTO) {
        Grupo grupo = convertirDTOAEntidad(grupoDTO);
        grupo = grupoRepository.save(grupo);
        return convertirEntidadADTO(grupo);
    }

    @Override
    public GrupoDTO actualizarGrupo(Long id, GrupoDTO grupoDTO) {
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
	public GrupoDTO convertirEntidadADTO(Grupo grupo) {
	    GrupoDTO grupoDTO = new GrupoDTO();
	    grupoDTO.setId(grupo.getId());
	    grupoDTO.setNombre(grupo.getNombre());

	    List<AlumnoGrupoDTO> alumnosDTO = grupo.getAlumnos().stream()
	        .map(AlumnoGrupoDTO::deAlumno)
	        .collect(Collectors.toList());
	    grupoDTO.setAlumnos(alumnosDTO);

	    return grupoDTO;
	}

	@Override
	public Grupo convertirDTOAEntidad(GrupoDTO grupoDTO) {
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


}
