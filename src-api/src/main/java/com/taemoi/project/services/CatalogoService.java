package com.taemoi.project.services;

import com.taemoi.project.dtos.response.CatalogoDTO;
import com.taemoi.project.dtos.response.CatalogoPublicoDTO;

/** Lectura del catálogo de la organización activa para el frontend (catalogo-frontend.md §1). */
public interface CatalogoService {

	/**
	 * @param incluirTarifas false para quien no gestiona (D14): {@code tarifas} vacía.
	 */
	CatalogoDTO obtener(boolean incluirTarifas);

	CatalogoPublicoDTO obtenerPublico();
}
