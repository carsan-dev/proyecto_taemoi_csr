package com.taemoi.project.dtos.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * Catálogo de la organización activa ({@code GET /api/catalogo}, docs/multi-escuela/catalogo-frontend.md §1).
 * Solo DTO: nunca se serializan entidades. {@code tarifas} va vacía para quien no es gestor (D14).
 */
public record CatalogoDTO(
		Organizacion organizacion,
		List<Disciplina> disciplinas,
		List<SistemaGrados> sistemasGrados,
		List<Categoria> categorias,
		List<Tarifa> tarifas) {

	public record Organizacion(Long id, boolean comportamientoLegadoTaemoi) {
	}

	/** Todas las disciplinas (también inactivas, para etiquetar inscripciones antiguas), por orden, id. */
	public record Disciplina(
			String codigo,
			String nombre,
			Integer orden,
			boolean activa,
			Integer edadAdulto,
			boolean permiteCompeticion,
			boolean requiereLicencia,
			Long sistemaGradosId,
			boolean tieneMaterialExamen) {
	}

	public record SistemaGrados(Long id, String nombre, List<Grado> grados) {
	}

	/** Grado con orden (los de orden nulo no se envían), por orden, id. color_1 abajo, color_2 arriba. */
	public record Grado(
			String codigo,
			String nombre,
			Integer orden,
			String aplicaA,
			String color1,
			String color2,
			boolean requiereExamen) {
	}

	/** {@code disciplina}: código de la disciplina, o nulo si es general. */
	public record Categoria(String codigo, String nombre, String disciplina, Integer edadMin, Integer edadMax,
			Integer orden) {
	}

	/** {@code disciplina}: la única disciplina que admite la tarifa, o nulo si admite cualquiera. */
	public record Tarifa(
			String codigo,
			String nombre,
			String disciplina,
			BigDecimal importe,
			BigDecimal importeAdulto,
			BigDecimal importeMenor,
			Integer orden,
			boolean activa,
			boolean requiereRolFamiliar,
			boolean requiereGrupoFamiliar) {
	}
}
