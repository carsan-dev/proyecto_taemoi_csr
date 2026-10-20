package com.taemoi.project.services;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Pieza E7-1: la regla de {@code pideRojoBordado} de la ficha del profesor es la de gestión
 * ({@code esSiguienteGradoRojo} + {@code requiereAvisoRojoBordado} en el frontend): solo con el comportamiento de
 * Taemoi y cuando el siguiente grado es {@code ROJO}. El camino completo lo prueba
 * {@code ProfesorGruposIntegrationTest}.
 */
class ProfesorServiceTest {

	@Test
	void pideRojoBordado_soloConLegadoYSiguienteRojo() {
		assertTrue(ProfesorService.pideRojoBordado(true, "ROJO"));
		assertFalse(ProfesorService.pideRojoBordado(false, "ROJO"), "escuela sin el comportamiento de Taemoi");
		assertFalse(ProfesorService.pideRojoBordado(true, "NEGRO_1_DAN"));
		assertFalse(ProfesorService.pideRojoBordado(true, "AZUL_ROJO"));
		assertFalse(ProfesorService.pideRojoBordado(true, null), "sin siguiente grado");
	}
}
