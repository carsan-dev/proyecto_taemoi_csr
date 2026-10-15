package com.taemoi.project.caracterizacion;

import static com.taemoi.project.legado.TipoGrado.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.taemoi.project.legado.ExamEligibilityConfig;
import com.taemoi.project.legado.TipoGrado;

/**
 * Caracterizacion de ExamEligibilityConfig: meses minimos por grado y umbral de edad
 * (edad &lt; 13 = tabla de menores). Long.MAX_VALUE = no apto / sin periodo.
 */
class ExamEligibilityConfigCaracterizacionTest {

	private static final long NO_APTO = Long.MAX_VALUE;

	private ExamEligibilityConfig config;

	@BeforeEach
	void setUp() {
		config = new ExamEligibilityConfig();
		config.init();
	}

	private static Map<TipoGrado, Long> esperadoMenores() {
		Map<TipoGrado, Long> m = new EnumMap<>(TipoGrado.class);
		m.put(BLANCO, 2L);
		m.put(BLANCO_AMARILLO, 2L);
		m.put(AMARILLO, 3L);
		m.put(AMARILLO_NARANJA, 3L);
		m.put(NARANJA, 4L);
		m.put(NARANJA_VERDE, 4L);
		m.put(VERDE, 6L);
		m.put(VERDE_AZUL, 6L);
		m.put(AZUL, 8L);
		m.put(AZUL_ROJO, 10L);
		m.put(ROJO, 12L);
		m.put(ROJO_NEGRO_1_PUM, 24L);
		m.put(ROJO_NEGRO_2_PUM, 36L);
		return m;
	}

	private static Map<TipoGrado, Long> esperadoAdultos() {
		Map<TipoGrado, Long> m = new EnumMap<>(TipoGrado.class);
		m.put(BLANCO, 3L);
		m.put(AMARILLO, 5L);
		m.put(NARANJA, 6L);
		m.put(VERDE, 8L);
		m.put(AZUL, 10L);
		m.put(ROJO, 12L);
		m.put(NEGRO_1_DAN, 24L);
		m.put(NEGRO_2_DAN, 36L);
		m.put(NEGRO_3_DAN, 48L);
		m.put(NEGRO_4_DAN, 60L);
		return m;
	}

	@Test
	void menores_todosLosGrados_porEdad() {
		Map<TipoGrado, Long> esperado = esperadoMenores();
		for (int edad : new int[] { 0, 5, 12 }) {
			for (TipoGrado grado : TipoGrado.values()) {
				assertEquals(esperado.getOrDefault(grado, NO_APTO), config.obtenerMesesRequeridos(edad, grado),
						"edad=" + edad + " grado=" + grado);
			}
		}
	}

	@Test
	void adultos_todosLosGrados_porEdad() {
		Map<TipoGrado, Long> esperado = esperadoAdultos();
		for (int edad : new int[] { 13, 14, 15, 40 }) {
			for (TipoGrado grado : TipoGrado.values()) {
				assertEquals(esperado.getOrDefault(grado, NO_APTO), config.obtenerMesesRequeridos(edad, grado),
						"edad=" + edad + " grado=" + grado);
			}
		}
	}

	@Test
	void umbralEdad13_ambosLados() {
		// 12 -> menores, 13 -> adultos (el umbral es edad < 13, no la regla 14/15 de FechaUtils)
		assertEquals(2L, config.obtenerMesesRequeridos(12, BLANCO));
		assertEquals(3L, config.obtenerMesesRequeridos(13, BLANCO));
		assertEquals(3L, config.obtenerMesesRequeridos(12, AMARILLO));
		assertEquals(5L, config.obtenerMesesRequeridos(13, AMARILLO));
		assertEquals(2L, config.obtenerMesesRequeridos(12, BLANCO_AMARILLO));
		assertEquals(NO_APTO, config.obtenerMesesRequeridos(13, BLANCO_AMARILLO));
		assertEquals(24L, config.obtenerMesesRequeridos(12, ROJO_NEGRO_1_PUM));
		assertEquals(NO_APTO, config.obtenerMesesRequeridos(13, ROJO_NEGRO_1_PUM));
		assertEquals(NO_APTO, config.obtenerMesesRequeridos(12, NEGRO_1_DAN));
		assertEquals(24L, config.obtenerMesesRequeridos(13, NEGRO_1_DAN));
		// Edad negativa tambien cae en menores
		assertEquals(2L, config.obtenerMesesRequeridos(-1, BLANCO));
	}

	@Test
	void gradoNulo_noApto() {
		assertEquals(NO_APTO, config.obtenerMesesRequeridos(10, null));
		assertEquals(NO_APTO, config.obtenerMesesRequeridos(30, null));
	}

	@Test
	void mapasExpuestos_exactos_eInmutables() {
		assertEquals(esperadoMenores(), config.obtenerMesesRequeridosMenores());
		assertEquals(esperadoAdultos(), config.obtenerMesesRequeridosMayores());
		assertEquals(List.of(BLANCO, BLANCO_AMARILLO, AMARILLO, AMARILLO_NARANJA, NARANJA, NARANJA_VERDE, VERDE,
				VERDE_AZUL, AZUL, AZUL_ROJO, ROJO, ROJO_NEGRO_1_PUM, ROJO_NEGRO_2_PUM),
				List.copyOf(config.obtenerMesesRequeridosMenores().keySet()));
		assertThrows(UnsupportedOperationException.class,
				() -> config.obtenerMesesRequeridosMenores().put(NEGRO_5_DAN, 1L));
	}

	@Test
	void tienePeriodoDefinido() {
		assertTrue(config.tienePeriodoDefinido(BLANCO_AMARILLO, true));
		assertFalse(config.tienePeriodoDefinido(BLANCO_AMARILLO, false));
		assertFalse(config.tienePeriodoDefinido(ROJO_NEGRO_3_PUM, true));
		assertFalse(config.tienePeriodoDefinido(NEGRO_5_DAN, false));
		assertTrue(config.tienePeriodoDefinido(NEGRO_4_DAN, false));
		assertFalse(config.tienePeriodoDefinido(NEGRO_1_DAN, true));
		assertFalse(config.tienePeriodoDefinido(null, true));
	}
}
