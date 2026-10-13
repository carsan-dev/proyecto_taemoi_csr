package com.taemoi.project.services.impl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.taemoi.project.entities.Grado;
import com.taemoi.project.repositories.GradoRepository;
import com.taemoi.project.services.GradoService;

@Service
public class GradoServiceImpl implements GradoService {

	@Autowired
	private GradoRepository gradoRepository;

	@Override
	public List<Grado> obtenerTodosLosGrados() {
		// Solo los grados heredados: desde V23 hay una copia por sistema (taekwondo y kickboxing)
		// y esta lista debe seguir siendo la de antes (un grado por TipoGrado).
		return gradoRepository.findGradosHeredados();
	}

}
