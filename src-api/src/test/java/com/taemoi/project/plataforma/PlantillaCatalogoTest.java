package com.taemoi.project.plataforma;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.taemoi.project.entities.AplicaGrado;
import com.taemoi.project.entities.TipoProducto;

/**
 * Fase 4, pieza F4-2 (decisión 392): la regla de qué se copia por disciplina, con una plantilla como la de Moiskimdo
 * (datos inventados): taekwondo y kickboxing con sistema propio (kickboxing sin el grado PUM), pilates sin grados y una
 * disciplina inactiva; productos comunes, propios y compartidos de examen.
 */
class PlantillaCatalogoTest {

	private static final long TKD = 1, KB = 2, PIL = 3, INACTIVA = 4;
	private static final long S_TKD = 10, S_KB = 20;

	private final PlantillaCatalogo plantilla = new PlantillaCatalogo(
			List.of(disciplina(TKD, "TAEKWONDO", S_TKD, true), disciplina(KB, "KICKBOXING", S_KB, true),
					disciplina(PIL, "PILATES", null, true), disciplina(INACTIVA, "BOXEO", null, false)),
			List.of(new PlantillaCatalogo.Sistema(S_TKD, "Taekwondo", "TAEKWONDO"),
					new PlantillaCatalogo.Sistema(S_KB, "Kickboxing", null)),
			List.of(grado(100, S_TKD, "BLANCO"), grado(101, S_TKD, "AMARILLO"), grado(102, S_TKD, "ROJO_NEGRO_1_PUM"),
					grado(200, S_KB, "BLANCO"), grado(201, S_KB, "AMARILLO")),
			List.of(categoria(300, TKD), categoria(301, null), categoria(302, KB)),
			List.of(tarifa(400, TKD), tarifa(401, TKD), tarifa(402, KB), tarifa(403, PIL), tarifa(404, null),
					tarifa(405, INACTIVA)),
			List.of(producto(500, TipoProducto.MENSUALIDAD, null, null),
					producto(501, TipoProducto.LICENCIA, TKD, null),
					producto(502, TipoProducto.COMPETIDOR, KB, null),
					producto(503, TipoProducto.EXAMEN, null, 101L),
					producto(504, TipoProducto.EXAMEN, null, 102L),
					producto(505, TipoProducto.OTRO, PIL, null),
					producto(506, TipoProducto.OTRO, INACTIVA, null)));

	@Test
	void resumen_soloActivas_sinLoComun_conLosCompartidosDeSuSistema() {
		assertEquals(List.of(new DisciplinaPlantillaDTO("TAEKWONDO", "D1", true, 2, 3),
				new DisciplinaPlantillaDTO("KICKBOXING", "D2", true, 1, 2),
				new DisciplinaPlantillaDTO("PILATES", "D3", false, 1, 1)), plantilla.resumen());
	}

	@Test
	void taekwondo_suSistemaSusFilasYLoComun() {
		PlantillaCatalogo.Seleccion s = plantilla.seleccionar(List.of("TAEKWONDO"));
		assertEquals(List.of(TKD), s.disciplinas().stream().map(PlantillaCatalogo.Disciplina::id).toList());
		assertEquals(List.of(S_TKD), s.sistemas().stream().map(PlantillaCatalogo.Sistema::id).toList());
		assertEquals(List.of(100L, 101L, 102L), s.grados().stream().map(PlantillaCatalogo.Grado::id).toList());
		assertEquals(List.of(300L, 301L), s.categorias().stream().map(PlantillaCatalogo.Categoria::id).toList());
		assertEquals(List.of(400L, 401L, 404L), s.tarifas().stream().map(PlantillaCatalogo.Tarifa::id).toList());
		assertEquals(List.of(500L, 501L, 503L, 504L), s.productos().stream().map(PlantillaCatalogo.Producto::id).toList());
	}

	@Test
	void kickboxing_elCompartidoSoloSiSuSistemaTieneEseCodigo() {
		PlantillaCatalogo.Seleccion s = plantilla.seleccionar(List.of("KICKBOXING"));
		assertEquals(List.of(S_KB), s.sistemas().stream().map(PlantillaCatalogo.Sistema::id).toList());
		assertEquals(List.of(200L, 201L), s.grados().stream().map(PlantillaCatalogo.Grado::id).toList());
		assertEquals(List.of(301L, 302L), s.categorias().stream().map(PlantillaCatalogo.Categoria::id).toList());
		assertEquals(List.of(402L, 404L), s.tarifas().stream().map(PlantillaCatalogo.Tarifa::id).toList());
		// 503 (AMARILLO) sí; 504 (PUM) no: kickboxing no tiene ese grado.
		assertEquals(List.of(500L, 502L, 503L), s.productos().stream().map(PlantillaCatalogo.Producto::id).toList());
	}

	@Test
	void variasDisciplinas_sinDuplicados_enElOrdenDelCatalogo() {
		PlantillaCatalogo.Seleccion s = plantilla.seleccionar(List.of("PILATES", "TAEKWONDO", "KICKBOXING"));
		assertEquals(List.of("TAEKWONDO", "KICKBOXING", "PILATES"),
				s.disciplinas().stream().map(PlantillaCatalogo.Disciplina::codigo).toList());
		assertEquals(List.of(500L, 501L, 502L, 503L, 504L, 505L),
				s.productos().stream().map(PlantillaCatalogo.Producto::id).toList());
		assertEquals(List.of(400L, 401L, 402L, 403L, 404L), s.tarifas().stream().map(PlantillaCatalogo.Tarifa::id).toList());
	}

	@Test
	void pilates_sinGrados_soloLoSuyoYLoComun() {
		PlantillaCatalogo.Seleccion s = plantilla.seleccionar(List.of("PILATES"));
		assertEquals(List.of(), s.sistemas());
		assertEquals(List.of(), s.grados());
		assertEquals(List.of(500L, 505L), s.productos().stream().map(PlantillaCatalogo.Producto::id).toList());
	}

	@Test
	void errores_400() {
		assertEquals(PlantillaCatalogo.SIN_DISCIPLINAS,
				assertThrows(IllegalArgumentException.class, () -> plantilla.seleccionar(List.of())).getMessage());
		assertEquals(PlantillaCatalogo.SIN_DISCIPLINAS,
				assertThrows(IllegalArgumentException.class, () -> plantilla.seleccionar(null)).getMessage());
		assertEquals(PlantillaCatalogo.DISCIPLINAS_REPETIDAS, assertThrows(IllegalArgumentException.class,
				() -> plantilla.seleccionar(List.of("PILATES", "PILATES"))).getMessage());
		for (List<String> ajenas : List.of(List.of("AJEDREZ"), List.of("BOXEO"), List.of("taekwondo"))) {
			assertEquals(PlantillaCatalogo.DISCIPLINA_AJENA, assertThrows(IllegalArgumentException.class,
					() -> plantilla.seleccionar(ajenas)).getMessage(), ajenas.toString());
		}
		assertEquals(PlantillaCatalogo.DISCIPLINA_AJENA, assertThrows(IllegalArgumentException.class,
				() -> plantilla.seleccionar(java.util.Arrays.asList("PILATES", null))).getMessage());
	}

	// ------------------------------------------------------------------ datos

	private static PlantillaCatalogo.Disciplina disciplina(long id, String codigo, Long sistema, boolean activa) {
		return new PlantillaCatalogo.Disciplina(id, codigo, "D" + id, sistema, true, true, 14, (int) id, activa, null,
				null);
	}

	private static PlantillaCatalogo.Grado grado(long id, long sistema, String codigo) {
		return new PlantillaCatalogo.Grado(id, sistema, codigo, codigo, (int) id, null, null, AplicaGrado.TODOS, null, true,
				null, null, null, null, null);
	}

	private static PlantillaCatalogo.Categoria categoria(long id, Long disciplina) {
		return new PlantillaCatalogo.Categoria(id, "C" + id, "Categoría " + id, disciplina, null, null, 0);
	}

	private static PlantillaCatalogo.Tarifa tarifa(long id, Long disciplina) {
		return new PlantillaCatalogo.Tarifa(id, disciplina, "T" + id, "Tarifa " + id, new BigDecimal("30.00"), null, null,
				0);
	}

	private static PlantillaCatalogo.Producto producto(long id, TipoProducto tipo, Long disciplina, Long grado) {
		return new PlantillaCatalogo.Producto(id, "P" + id, 10.0, "P" + id, tipo, disciplina, grado, false);
	}
}
