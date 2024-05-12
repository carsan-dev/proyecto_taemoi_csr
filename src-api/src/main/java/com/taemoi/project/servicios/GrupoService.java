package com.taemoi.project.servicios;

import java.util.List;
import java.util.Optional;

import com.taemoi.project.dtos.GrupoDTO;
import com.taemoi.project.entidades.Alumno;
import com.taemoi.project.entidades.Grupo;

import jakarta.validation.Valid;

public interface GrupoService {
	List<GrupoDTO> obtenerTodosLosGrupos();
	
	Optional<GrupoDTO> obtenerGrupoPorId(Long id);
	
	GrupoDTO crearGrupo(@Valid GrupoDTO grupoDTO);
	
	GrupoDTO actualizarGrupo(@Valid Long id, GrupoDTO grupoDTO);
	
	void eliminarGrupo(Long id);
	
	void agregarAlumnoAGrupo(Long grupoId, Long alumnoId);
	
	void eliminarAlumnoDeGrupo(Long grupoId, Long alumnoId);
	
	GrupoDTO convertirEntidadADTO(Grupo grupo);
	
	Grupo convertirDTOAEntidad(GrupoDTO grupoDTO);
}
