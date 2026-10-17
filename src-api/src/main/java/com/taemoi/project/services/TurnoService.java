package com.taemoi.project.services;

import java.util.List;
import org.springframework.lang.NonNull;

import com.taemoi.project.dtos.TurnoDTO;
import com.taemoi.project.dtos.response.TurnoPublicoDTO;
import com.taemoi.project.entities.Turno;

public interface TurnoService {

	List<Turno> listarTurnos();

	Turno obtenerTurnoPorId(@NonNull Long turnoId);

	/** Turnos de la página pública /horarios: sin alumnos (ver {@link TurnoPublicoDTO}). */
	List<TurnoPublicoDTO> listarTurnosDTO();

	void crearTurnoSinGrupo(TurnoDTO turnoDTO);

	void crearTurnoYAsignarAGrupo(TurnoDTO turnoDTO);

	TurnoDTO actualizarTurno(@NonNull Long turnoId, TurnoDTO turnoDTO);

	boolean eliminarTurno(@NonNull Long turnoId);

	List<TurnoDTO> listarTurnosDTOCompleto();
}
