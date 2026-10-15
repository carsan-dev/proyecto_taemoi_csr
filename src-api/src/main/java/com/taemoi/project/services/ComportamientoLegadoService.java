package com.taemoi.project.services;

/**
 * Si la organización activa conserva el comportamiento heredado de Taemoi
 * ({@code organizacion.comportamiento_legado_taemoi}, V28): conceptos literales de los cargos de los
 * productos con códigos de V24, competidor a 20 €, tesorería por concepto y búsqueda previa de variantes sin
 * clasificar ({@link CatalogoLegadoTaemoi}). Solo Taemoi (organización 1) la tiene; las demás usan siempre el
 * comportamiento del catálogo.
 */
public interface ComportamientoLegadoService {

	/**
	 * @return true si la organización activa tiene la marca.
	 * @throws IllegalStateException si no hay organización activa en el hilo (como cualquier acceso a datos).
	 */
	boolean activo();
}
