package com.taemoi.project.servicios.impl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.taemoi.project.entidades.Grado;
import com.taemoi.project.repositorios.GradoRepository;
import com.taemoi.project.servicios.GradoService;

@Service
public class GradoServiceImpl implements GradoService {
	
    @Autowired
    private GradoRepository gradoRepository;

	@Override
	public List<Grado> obtenerTodosLosGrados() {
		return gradoRepository.findAll();
	}

}
