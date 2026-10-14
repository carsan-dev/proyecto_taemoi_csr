package com.taemoi.project.services.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.taemoi.project.entities.Grado;
import com.taemoi.project.services.CatalogoDisciplinasService;
import com.taemoi.project.services.GradoService;
import com.taemoi.project.services.ProgresionGradosService;

@Service
public class GradoServiceImpl implements GradoService {

	private final CatalogoDisciplinasService catalogo;
	private final ProgresionGradosService progresion;

	public GradoServiceImpl(CatalogoDisciplinasService catalogo, ProgresionGradosService progresion) {
		this.catalogo = catalogo;
		this.progresion = progresion;
	}

	@Override
	public List<Grado> obtenerTodosLosGrados() {
		return obtenerGradosDeDisciplina(DISCIPLINA_POR_DEFECTO);
	}

	@Override
	public List<Grado> obtenerGradosDeDisciplina(String codigoDisciplina) {
		if (catalogo.buscarPorCodigo(codigoDisciplina).isEmpty()) {
			return List.of();
		}
		return progresion.listarGrados(codigoDisciplina);
	}
}
