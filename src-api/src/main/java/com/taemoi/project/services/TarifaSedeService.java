package com.taemoi.project.services;

import java.util.List;

import com.taemoi.project.dtos.request.TarifaSedeRequest;
import com.taemoi.project.dtos.response.TarifaSedeDTO;

/**
 * Tarifa por sede (entrega 6-4, docs/multi-escuela/entrega6-plan.md §4.4, D6-27 a D6-36): excepciones del catálogo por
 * sede y mes, solo para las mensualidades.
 */
public interface TarifaSedeService {

	/**
	 * Resolutor de la cuantía de las mensualidades con las tarifas por sede de la escuela activa, cargadas UNA vez (una
	 * consulta). Sin filas, {@link ResolutorCuantiaMensualidad#identidad()}.
	 */
	ResolutorCuantiaMensualidad preparar();

	/** Las de la escuela; para el responsable de sede, solo las de sus sedes (D6-31). */
	List<TarifaSedeDTO> listar();

	/**
	 * Crea una excepción (D6-30).
	 *
	 * @throws IllegalArgumentException (400) tarifa o sede de otra escuela, mes no válido, importes no válidos o sin los
	 *                                  importes por rol que tiene la tarifa base
	 * @throws IllegalStateException    (409) ya existe para esa tarifa, sede y mes
	 */
	TarifaSedeDTO crear(TarifaSedeRequest solicitud);

	/**
	 * Borra una excepción de un mes FUTURO (posterior al mes en curso, Europe/Madrid).
	 *
	 * @throws java.util.NoSuchElementException (404) no existe en la escuela
	 * @throws IllegalStateException            (409) es del mes en curso o de uno pasado
	 */
	void borrar(Long id);
}
