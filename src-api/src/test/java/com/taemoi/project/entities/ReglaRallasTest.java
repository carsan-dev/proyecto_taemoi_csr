package com.taemoi.project.entities;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Regla de las rallas (decisiones 510-513): PUM y DAN por código del grado, nunca por nombre; el blanco sí lleva. */
class ReglaRallasTest {

	@ParameterizedTest
	@ValueSource(strings = { "BLANCO", "BLANCO_AMARILLO", "AMARILLO", "NARANJA_VERDE", "AZUL_ROJO", "ROJO", "MARRON",
			"CINTO_NARANJA" })
	void gradosDeColor_llevan(String codigo) {
		assertTrue(ReglaRallas.gradoLlevaRallas(codigo));
		assertTrue(ReglaRallas.aplica(Boolean.TRUE, codigo));
	}

	@ParameterizedTest
	@ValueSource(strings = { "ROJO_NEGRO_1_PUM", "ROJO_NEGRO_3_PUM", "NEGRO_1_DAN", "NEGRO_5_DAN", "negro_2_dan",
			" NEGRO_1_DAN ", "PUM", "DAN", "NEGRO_DAN" })
	void pumYDan_noLlevan(String codigo) {
		assertFalse(ReglaRallas.gradoLlevaRallas(codigo));
		assertFalse(ReglaRallas.aplica(Boolean.TRUE, codigo));
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "  " })
	void sinGrado_noLleva(String codigo) {
		assertFalse(ReglaRallas.gradoLlevaRallas(codigo));
	}

	@ParameterizedTest
	@ValueSource(strings = { "BLANCO", "AMARILLO" })
	void disciplinaApagadaODesconocida_noAplica(String codigo) {
		assertFalse(ReglaRallas.aplica(Boolean.FALSE, codigo));
		assertFalse(ReglaRallas.aplica(null, codigo));
	}

	@ParameterizedTest
	@ValueSource(strings = { "DANZA", "PUMA", "AMARILLO_DANES" })
	void soloElSufijo_cuenta(String codigo) {
		assertTrue(ReglaRallas.gradoLlevaRallas(codigo));
	}
}
