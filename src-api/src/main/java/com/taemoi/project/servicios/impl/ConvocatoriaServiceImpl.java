package com.taemoi.project.servicios.impl;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.taemoi.project.entidades.Convocatoria;
import com.taemoi.project.entidades.Deporte;
import com.taemoi.project.repositorios.ConvocatoriaRepository;
import com.taemoi.project.servicios.ConvocatoriaService;

@Service
public class ConvocatoriaServiceImpl implements ConvocatoriaService {
	
	@Autowired
	private ConvocatoriaRepository convocatoriaRepository;

	@Override
	public Convocatoria crearConvocatoria(Convocatoria convocatoria) {
		return convocatoriaRepository.save(convocatoria);
	}

	@Override
	public List<Convocatoria> obtenerConvocatorias() {
		return convocatoriaRepository.findAll();
	}

	@Override
	public Convocatoria obtenerConvocatoriaPorId(Long id) {
		return convocatoriaRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Convocatoria no encontrada con ID: " + id));
	}

	@Override
	public Optional<Convocatoria> obtenerConvocatoriaActualPorDeporte(Deporte deporte) {
		return convocatoriaRepository.findConvocatoriaActualPorDeporte(deporte);
	}
}
