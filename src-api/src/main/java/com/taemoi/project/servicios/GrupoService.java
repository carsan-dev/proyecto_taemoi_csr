package com.taemoi.project.servicios;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.lang.NonNull;

import com.taemoi.project.dtos.TurnoDTO;
import com.taemoi.project.dtos.response.GrupoConAlumnosDTO;
import com.taemoi.project.dtos.response.GrupoResponseDTO;
import com.taemoi.project.entidades.Grupo;

import jakarta.validation.Valid;

public interface GrupoService {
	List<GrupoConAlumnosDTO> obtenerTodosLosGrupos();
	
	Optional<GrupoConAlumnosDTO> obtenerGrupoConAlumnosPorId(@NonNull Long id);
	
	GrupoConAlumnosDTO crearGrupo(@Valid GrupoConAlumnosDTO grupoDTO);
	
	GrupoConAlumnosDTO actualizarGrupo(@Valid @NonNull Long id, GrupoConAlumnosDTO grupoDTO);
	
	void eliminarGrupo(@NonNull Long id);
	
	void agregarAlumnoAGrupo(@NonNull Long grupoId, @NonNull Long alumnoId);
	
	void eliminarAlumnoDeGrupo(@NonNull Long grupoId, @NonNull Long alumnoId);
	
	void agregarTurnoAGrupo(@NonNull Long grupoId, @NonNull Long turnoId);
	
	void eliminarTurnoDeGrupo(@NonNull Long grupoId, @NonNull Long turnoId);
	
	List<TurnoDTO> obtenerTurnosDelGrupo(@NonNull Long grupoId);
	
	GrupoConAlumnosDTO convertirEntidadADTO(Grupo grupo);
	
	Grupo convertirDTOAEntidad(GrupoConAlumnosDTO grupoDTO);

	List<GrupoResponseDTO> obtenerGruposDelAlumno(@NonNull Long alumnoId);

	void agregarAlumnosAGrupo(@NonNull Long grupoId, List<Long> alumnosIds);

	Map<String, Long> contarAlumnosPorGrupo();
}
