package com.taemoi.project.services;

import java.util.List;

import com.taemoi.project.entities.Grado;

public interface GradoService {

	/** Disciplina de {@code /api/grados} sin parámetro: la respuesta de siempre (taekwondo). */
	String DISCIPLINA_POR_DEFECTO = "TAEKWONDO";

	/** Grados de la disciplina por defecto ({@link #DISCIPLINA_POR_DEFECTO}). */
	List<Grado> obtenerTodosLosGrados();

	/**
	 * Grados del sistema de la disciplina, por id. Vacío si la organización activa no tiene esa
	 * disciplina o la disciplina no tiene grados.
	 */
	List<Grado> obtenerGradosDeDisciplina(String codigoDisciplina);
}
