package com.taemoi.project.organizacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** Alcance de la sesión por sede y grupo (entrega 5b-2, entrega5-plan.md §3.2). */
class AlcanceSesionTest {

	@Test
	void totalLoVeTodo() {
		assertTrue(AlcanceSesion.TOTAL.total());
		assertTrue(AlcanceSesion.TOTAL.veSede(1L));
		assertTrue(AlcanceSesion.TOTAL.veGrupo(99L));
		assertFalse(AlcanceSesion.TOTAL.esVacio());
	}

	@Test
	void ningunoNoVeNada() {
		assertFalse(AlcanceSesion.NINGUNO.total());
		assertFalse(AlcanceSesion.NINGUNO.veSede(1L));
		assertFalse(AlcanceSesion.NINGUNO.veGrupo(1L));
		assertTrue(AlcanceSesion.NINGUNO.esVacio());
	}

	@Test
	void limitadoVeSoloSusSedesYSusGruposPorSeparado() {
		AlcanceSesion alcance = AlcanceSesion.limitado(Set.of(10L), Set.of(20L));

		assertFalse(alcance.total());
		assertTrue(alcance.veSede(10L));
		assertFalse(alcance.veSede(20L), "un grupo no da su id como sede");
		assertTrue(alcance.veGrupo(20L));
		assertFalse(alcance.veGrupo(10L), "una sede no da su id como grupo");
		assertFalse(alcance.esVacio());
		assertTrue(AlcanceSesion.limitado(Set.of(), Set.of()).esVacio());
	}

	@Test
	void idNuloNoSeVeSalvoConAlcanceTotal() {
		AlcanceSesion alcance = AlcanceSesion.limitado(Set.of(10L), Set.of(20L));

		assertFalse(alcance.veSede(null));
		assertFalse(alcance.veGrupo(null));
		assertFalse(AlcanceSesion.NINGUNO.veSede(null));
		assertTrue(AlcanceSesion.TOTAL.veSede(null));
	}

	@Test
	void copiasInmutablesYConjuntosNulosVacios() {
		Set<Long> sedes = new HashSet<>(Set.of(1L));
		AlcanceSesion alcance = new AlcanceSesion(false, sedes, null);
		sedes.add(2L);

		assertEquals(Set.of(1L), alcance.sedes(), "no le afecta cambiar el conjunto original");
		assertEquals(Set.of(), alcance.grupos());
		assertThrows(UnsupportedOperationException.class, () -> alcance.sedes().add(3L));
		assertThrows(UnsupportedOperationException.class, () -> alcance.grupos().add(3L));
	}
}
