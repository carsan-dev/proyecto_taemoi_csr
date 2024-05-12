package com.taemoi.project.servicios.impl;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.taemoi.project.entidades.Horario;
import com.taemoi.project.repositorios.HorarioRepository;
import com.taemoi.project.servicios.HorarioService;

@Service
public class HorarioServiceImpl implements HorarioService {

	@Autowired
	private HorarioRepository horarioRepository;
	
	@Override
	public List<Horario> obtenerTodosLosHorarios() {
		return horarioRepository.findAll();
	}

	@Override
	public Optional<Horario> obtenerHorarioPorId(Long id) {
		return horarioRepository.findById(id);
	}

	@Override
	public Horario crearHorario(Horario horario) {
		return horarioRepository.save(horario);
	}

	@Override
	public Horario actualizarHorario(Long id, Horario horario) {
		if (horarioRepository.existsById(id)) {
			horario.setId(id);
			return horarioRepository.save(horario);
		} else {
			return null;
		}
	}

	@Override
	public void eliminarHorario(Long id) {
		horarioRepository.deleteById(id);
	}
}
