package com.taemoi.project.caracterizacion;

import static com.taemoi.project.legado.TipoGrado.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.taemoi.project.legado.ExamMaterialBlockConfig;
import com.taemoi.project.legado.Deporte;
import com.taemoi.project.legado.TipoGrado;

/**
 * Completa servicios/ExamMaterialBlockConfigTest con la tabla completa
 * Deporte x TipoGrado y las entradas nulas (huecos del test existente).
 */
class ExamMaterialBlockConfigCaracterizacionTest {

	private final ExamMaterialBlockConfig config = new ExamMaterialBlockConfig();

	private static Map<TipoGrado, String> esperadoTaekwondoYKickboxing() {
		Map<TipoGrado, String> m = new EnumMap<>(TipoGrado.class);
		m.put(BLANCO, "b01_inicio_a_amarillo");
		m.put(BLANCO_AMARILLO, "b01_inicio_a_amarillo");
		m.put(AMARILLO, "b02_amarillo_a_naranja");
		m.put(AMARILLO_NARANJA, "b02_amarillo_a_naranja");
		m.put(NARANJA, "b03_naranja_a_verde");
		m.put(NARANJA_VERDE, "b03_naranja_a_verde");
		m.put(VERDE, "b04_verde_a_azul");
		m.put(VERDE_AZUL, "b04_verde_a_azul");
		m.put(AZUL, "b05_azul_a_rojo");
		m.put(AZUL_ROJO, "b05_azul_a_rojo");
		m.put(ROJO, "b06_rojo_a_negro_1");
		m.put(ROJO_NEGRO_1_PUM, "b07_negro_1_a_negro_2");
		m.put(ROJO_NEGRO_2_PUM, "b08_negro_2_a_negro_3");
		m.put(ROJO_NEGRO_3_PUM, "b09_negro_3_a_negro_4");
		m.put(NEGRO_1_DAN, "b07_negro_1_a_negro_2");
		m.put(NEGRO_2_DAN, "b08_negro_2_a_negro_3");
		m.put(NEGRO_3_DAN, "b09_negro_3_a_negro_4");
		m.put(NEGRO_4_DAN, "b10_negro_4_a_negro_5");
		m.put(NEGRO_5_DAN, "b11_negro_5_a_maximo");
		return m;
	}

	@Test
	void tablaCompleta_porDeporteYGrado() {
		Map<TipoGrado, String> conBloques = esperadoTaekwondoYKickboxing();
		assertEquals(TipoGrado.values().length, conBloques.size());
		for (TipoGrado grado : TipoGrado.values()) {
			assertEquals(conBloques.get(grado), config.obtenerBloque(Deporte.TAEKWONDO, grado), "TKD " + grado);
			assertEquals(conBloques.get(grado), config.obtenerBloque(Deporte.KICKBOXING, grado), "KB " + grado);
			assertNull(config.obtenerBloque(Deporte.PILATES, grado), "PILATES " + grado);
			assertNull(config.obtenerBloque(Deporte.DEFENSA_PERSONAL_FEMENINA, grado), "DPF " + grado);
		}
	}

	@Test
	void entradasNulas() {
		assertNull(config.obtenerBloque(null, BLANCO));
		assertNull(config.obtenerBloque(Deporte.TAEKWONDO, null));
		assertNull(config.obtenerBloque(null, null));
	}

	@Test
	void bloquesDistintos_once() {
		assertEquals(List.of("b01_inicio_a_amarillo", "b02_amarillo_a_naranja", "b03_naranja_a_verde",
				"b04_verde_a_azul", "b05_azul_a_rojo", "b06_rojo_a_negro_1", "b07_negro_1_a_negro_2",
				"b08_negro_2_a_negro_3", "b09_negro_3_a_negro_4", "b10_negro_4_a_negro_5", "b11_negro_5_a_maximo"),
				esperadoTaekwondoYKickboxing().values().stream().distinct().sorted().toList());
	}
}
