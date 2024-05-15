package com.taemoi.project.servicios;

import java.util.List;
import java.util.Optional;

import com.taemoi.project.dtos.TurnoDTO;
import com.taemoi.project.dtos.response.GrupoConAlumnosDTO;
import com.taemoi.project.dtos.response.GrupoConTurnosDTO;
import com.taemoi.project.dtos.response.GrupoResponseDTO;
import com.taemoi.project.entidades.Alumno;
import com.taemoi.project.entidades.Grupo;

import jakarta.validation.Valid;

public interface GrupoService {
	List<GrupoConAlumnosDTO> obtenerTodosLosGrupos();
	
	Optional<GrupoConAlumnosDTO> obtenerGrupoConAlumnosPorId(Long id);
	
	GrupoConAlumnosDTO crearGrupo(@Valid GrupoConAlumnosDTO grupoDTO);
	
	GrupoConAlumnosDTO actualizarGrupo(@Valid Long id, GrupoConAlumnosDTO grupoDTO);
	
	void eliminarGrupo(Long id);
	
	void agregarAlumnoAGrupo(Long grupoId, Long alumnoId);
	
	void eliminarAlumnoDeGrupo(Long grupoId, Long alumnoId);
	
	List<TurnoDTO> obtenerTurnosDelGrupo(Long grupoId);
	
	GrupoConAlumnosDTO convertirEntidadADTO(Grupo grupo);
	
	Grupo convertirDTOAEntidad(GrupoConAlumnosDTO grupoDTO);

	List<GrupoResponseDTO> obtenerGruposDelAlumno(Long alumnoId);
}
