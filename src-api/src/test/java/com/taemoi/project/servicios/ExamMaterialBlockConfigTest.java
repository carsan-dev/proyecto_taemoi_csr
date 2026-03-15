package com.taemoi.project.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import com.taemoi.project.config.ExamMaterialBlockConfig;
import com.taemoi.project.entities.Deporte;
import com.taemoi.project.entities.TipoGrado;

class ExamMaterialBlockConfigTest {

	private final ExamMaterialBlockConfig config = new ExamMaterialBlockConfig();

	@Test
	void shouldMapSharedInitialBlockForWhiteAndWhiteYellow() {
		assertEquals("b01_inicio_a_amarillo", config.obtenerBloque(Deporte.TAEKWONDO, TipoGrado.BLANCO));
		assertEquals("b01_inicio_a_amarillo", config.obtenerBloque(Deporte.TAEKWONDO, TipoGrado.BLANCO_AMARILLO));
	}

	@Test
	void shouldMapYellowToSecondBlock() {
		assertEquals("b02_amarillo_a_naranja", config.obtenerBloque(Deporte.TAEKWONDO, TipoGrado.AMARILLO));
		assertEquals("b02_amarillo_a_naranja", config.obtenerBloque(Deporte.KICKBOXING, TipoGrado.AMARILLO));
	}

	@Test
	void shouldMapPumToSameBlockAsEquivalentDan() {
		assertEquals(
				config.obtenerBloque(Deporte.TAEKWONDO, TipoGrado.NEGRO_1_DAN),
				config.obtenerBloque(Deporte.TAEKWONDO, TipoGrado.ROJO_NEGRO_1_PUM));
		assertEquals(
				config.obtenerBloque(Deporte.TAEKWONDO, TipoGrado.NEGRO_2_DAN),
				config.obtenerBloque(Deporte.TAEKWONDO, TipoGrado.ROJO_NEGRO_2_PUM));
		assertEquals(
				config.obtenerBloque(Deporte.TAEKWONDO, TipoGrado.NEGRO_3_DAN),
				config.obtenerBloque(Deporte.TAEKWONDO, TipoGrado.ROJO_NEGRO_3_PUM));
	}

	@Test
	void shouldMapDan4AndDan5ToBlocks10And11() {
		assertEquals("b10_negro_4_a_negro_5", config.obtenerBloque(Deporte.TAEKWONDO, TipoGrado.NEGRO_4_DAN));
		assertEquals("b11_negro_5_a_maximo", config.obtenerBloque(Deporte.TAEKWONDO, TipoGrado.NEGRO_5_DAN));
	}

	@Test
	void shouldReturnNullForSportsWithoutConfiguredExamBlocks() {
		assertNull(config.obtenerBloque(Deporte.PILATES, TipoGrado.BLANCO));
		assertNull(config.obtenerBloque(Deporte.DEFENSA_PERSONAL_FEMENINA, TipoGrado.BLANCO_AMARILLO));
	}
}
