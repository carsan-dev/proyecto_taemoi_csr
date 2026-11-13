package com.taemoi.project.plataforma.importacion;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Lo que la validación necesita de la escuela destino, leído DENTRO de su transacción (filtrado por {@code @TenantId}):
 * nada de otra escuela cuenta como existente.
 *
 * @param disciplina          código de la disciplina de la carga
 * @param sedes               sedes ACTIVAS de la escuela
 * @param grupos              grupos de la escuela (con su sede)
 * @param tarifas             tarifas de la escuela
 * @param gradoExiste         si un código de grado existe en el sistema de grados de la disciplina
 * @param nifsExistentes      NIF ya guardados en la escuela, en forma canónica ({@code NifUtils#normalizeForComparison})
 * @param licenciasExistentes números de licencia de la disciplina ya guardados en la escuela
 * @param esEmail             la misma regla {@code @Email} que la ficha del alumno
 */
record ContextoEscuela(String disciplina, List<SedeRef> sedes, List<GrupoRef> grupos, List<TarifaRef> tarifas,
		Predicate<String> gradoExiste, Set<String> nifsExistentes, Set<Integer> licenciasExistentes,
		Predicate<String> esEmail) {

	record SedeRef(Long id, String nombre) {
	}

	record GrupoRef(Long id, String nombre, Long sedeId) {
	}

	/** {@code disciplina}: código de la disciplina de la tarifa, o {@code null} si vale para cualquiera. */
	record TarifaRef(String codigo, String nombre, String disciplina) {
	}

	/**
	 * Clave para comparar nombres de catálogo: sin distinguir mayúsculas ni espacios sobrantes (extremos y repetidos).
	 * Las tildes SÍ cuentan: sin aproximaciones.
	 */
	static String clave(String valor) {
		return valor == null ? "" : CeldaExcel.limpiar(valor).replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
	}
}
