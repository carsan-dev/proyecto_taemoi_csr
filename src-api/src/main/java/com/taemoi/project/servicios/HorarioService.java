package com.taemoi.project.servicios;

import java.util.List;
import java.util.Optional;

import com.taemoi.project.entidades.Horario;

public interface HorarioService {
	List<Horario> obtenerTodosLosHorarios();
	
	Optional<Horario> obtenerHorarioPorId(Long id);
	
	Horario crearHorario(Horario horario);
	
	Horario actualizarHorario(Long id, Horario horario);
	
	void eliminarHorario(Long id);
}
