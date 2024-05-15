package com.taemoi.project.servicios;

import java.util.List;
import java.util.Optional;

import com.taemoi.project.dtos.TurnoDTO;
import com.taemoi.project.dtos.response.GrupoConTurnosDTO;
import com.taemoi.project.entidades.Turno;

public interface TurnoService {
	
	List<Turno> listarTurnos();
	
	Turno obtenerTurnoPorId(Long turnoId);
	
	List<TurnoDTO> listarTurnosDTO();
	
	void crearTurnoSinGrupo(TurnoDTO turnoDTO);
	
	void crearTurnoYAsignarAGrupo(TurnoDTO turnoDTO);
	
	TurnoDTO actualizarTurno(Long turnoId, TurnoDTO turnoDTO);
	
	void eliminarTurno(Long turnoId);
}
