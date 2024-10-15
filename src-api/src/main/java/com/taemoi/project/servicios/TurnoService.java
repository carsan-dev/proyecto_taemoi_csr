package com.taemoi.project.servicios;

import java.util.List;
import org.springframework.lang.NonNull;

import com.taemoi.project.dtos.TurnoDTO;
import com.taemoi.project.dtos.response.TurnoCortoDTO;
import com.taemoi.project.entidades.Turno;

public interface TurnoService {
	
	List<Turno> listarTurnos();
	
	Turno obtenerTurnoPorId(@NonNull Long turnoId);
	
	List<TurnoCortoDTO> listarTurnosDTO();
	
	void crearTurnoSinGrupo(TurnoDTO turnoDTO);
	
	void crearTurnoYAsignarAGrupo(TurnoDTO turnoDTO);
	
	TurnoDTO actualizarTurno(@NonNull Long turnoId, TurnoDTO turnoDTO);
	
	boolean eliminarTurno(@NonNull Long turnoId);

	List<TurnoDTO> listarTurnosDTOCompleto();
}
