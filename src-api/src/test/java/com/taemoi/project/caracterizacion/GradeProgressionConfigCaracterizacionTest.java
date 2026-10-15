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

import com.taemoi.project.legado.GradeProgressionConfig;
import com.taemoi.project.legado.Deporte;
import com.taemoi.project.legado.TipoGrado;

/**
 * Caracterizacion de GradeProgressionConfig: fija el comportamiento actual antes de
 * sustituir los mapas de progresion por tablas. Las expectativas son literales.
 */
class GradeProgressionConfigCaracterizacionTest {

	private GradeProgressionConfig config;

	@BeforeEach
	void setUp() {
		config = new GradeProgressionConfig();
		config.init();
	}

	/** Taekwondo menores: incluye grados intermedios y PUM. Clave ausente = null. */
	private static Map<TipoGrado, TipoGrado> esperadoTaekwondoMenores() {
		Map<TipoGrado, TipoGrado> m = new EnumMap<>(TipoGrado.class);
		m.put(BLANCO, BLANCO_AMARILLO);
		m.put(BLANCO_AMARILLO, AMARILLO);
		m.put(AMARILLO, AMARILLO_NARANJA);
		m.put(AMARILLO_NARANJA, NARANJA);
		m.put(NARANJA, NARANJA_VERDE);
		m.put(NARANJA_VERDE, VERDE);
		m.put(VERDE, VERDE_AZUL);
		m.put(VERDE_AZUL, AZUL);
		m.put(AZUL, AZUL_ROJO);
		m.put(AZUL_ROJO, ROJO);
		m.put(ROJO, ROJO_NEGRO_1_PUM);
		m.put(ROJO_NEGRO_1_PUM, ROJO_NEGRO_2_PUM);
		m.put(ROJO_NEGRO_2_PUM, ROJO_NEGRO_3_PUM);
		// ROJO_NEGRO_3_PUM y todos los DAN -> null
		return m;
	}

	/** Taekwondo adultos y Kickboxing (menores y adultos): mismo mapa. */
	private static Map<TipoGrado, TipoGrado> esperadoAdultos() {
		Map<TipoGrado, TipoGrado> m = new EnumMap<>(TipoGrado.class);
		m.put(BLANCO, AMARILLO);
		m.put(BLANCO_AMARILLO, AMARILLO);
		m.put(AMARILLO, NARANJA);
		m.put(AMARILLO_NARANJA, NARANJA);
		m.put(NARANJA, VERDE);
		m.put(NARANJA_VERDE, VERDE);
		m.put(VERDE, AZUL);
		m.put(VERDE_AZUL, AZUL);
		m.put(AZUL, ROJO);
		m.put(AZUL_ROJO, ROJO);
		m.put(ROJO, NEGRO_1_DAN);
		m.put(NEGRO_1_DAN, NEGRO_2_DAN);
		m.put(NEGRO_2_DAN, NEGRO_3_DAN);
		m.put(NEGRO_3_DAN, NEGRO_4_DAN);
		m.put(NEGRO_4_DAN, NEGRO_5_DAN);
		// PUM y NEGRO_5_DAN -> null
		return m;
	}

	private void assertTablaCompleta(Deporte deporte, boolean esMenor, Map<TipoGrado, TipoGrado> esperado) {
		for (TipoGrado grado : TipoGrado.values()) {
			assertEquals(esperado.get(grado), config.obtenerSiguienteGrado(deporte, esMenor, grado),
					deporte + " esMenor=" + esMenor + " grado=" + grado);
		}
	}

	@Test
	void taekwondoMenores_todosLosGrados() {
		assertTablaCompleta(Deporte.TAEKWONDO, true, esperadoTaekwondoMenores());
	}

	@Test
	void taekwondoAdultos_todosLosGrados() {
		assertTablaCompleta(Deporte.TAEKWONDO, false, esperadoAdultos());
	}

	@Test
	void kickboxingMenores_todosLosGrados_igualQueAdultos() {
		assertTablaCompleta(Deporte.KICKBOXING, true, esperadoAdultos());
	}

	@Test
	void kickboxingAdultos_todosLosGrados() {
		assertTablaCompleta(Deporte.KICKBOXING, false, esperadoAdultos());
	}

	@Test
	void puntosSinSiguiente_explicitos() {
		assertEquals(null, config.obtenerSiguienteGrado(Deporte.TAEKWONDO, true, ROJO_NEGRO_3_PUM));
		assertEquals(null, config.obtenerSiguienteGrado(Deporte.TAEKWONDO, true, NEGRO_1_DAN));
		assertEquals(null, config.obtenerSiguienteGrado(Deporte.TAEKWONDO, false, NEGRO_5_DAN));
		assertEquals(null, config.obtenerSiguienteGrado(Deporte.TAEKWONDO, false, ROJO_NEGRO_1_PUM));
		assertEquals(null, config.obtenerSiguienteGrado(Deporte.KICKBOXING, true, ROJO_NEGRO_2_PUM));
		assertEquals(null, config.obtenerSiguienteGrado(Deporte.KICKBOXING, false, NEGRO_5_DAN));
	}

	@Test
	void deportesSinProgresion_lanzanIllegalArgument() {
		for (Deporte deporte : List.of(Deporte.PILATES, Deporte.DEFENSA_PERSONAL_FEMENINA)) {
			for (boolean esMenor : new boolean[] { true, false }) {
				for (TipoGrado grado : TipoGrado.values()) {
					assertThrows(IllegalArgumentException.class,
							() -> config.obtenerSiguienteGrado(deporte, esMenor, grado));
					assertThrows(IllegalArgumentException.class,
							() -> config.obtenerGradosAnteriores(deporte, esMenor, grado));
				}
			}
		}
	}

	@Test
	void entradasNulas_devuelvenNullOListaVacia_sinLanzar() {
		assertEquals(null, config.obtenerSiguienteGrado(null, true, BLANCO));
		assertEquals(null, config.obtenerSiguienteGrado(Deporte.TAEKWONDO, true, null));
		// deporte null con grado null tampoco lanza, aunque sea PILATES no se evalua
		assertEquals(null, config.obtenerSiguienteGrado(Deporte.PILATES, true, null));
		assertEquals(List.of(), config.obtenerGradosAnteriores(null, false, BLANCO));
		assertEquals(List.of(), config.obtenerGradosAnteriores(Deporte.PILATES, false, null));
	}

	@Test
	void tieneProgresion_porDeporte() {
		assertTrue(config.tieneProgresion(Deporte.TAEKWONDO));
		assertTrue(config.tieneProgresion(Deporte.KICKBOXING));
		assertFalse(config.tieneProgresion(Deporte.PILATES));
		assertFalse(config.tieneProgresion(Deporte.DEFENSA_PERSONAL_FEMENINA));
	}

	// --- obtenerGradosAnteriores: claves del mapa en orden de insercion, previas a gradoActual ---

	@Test
	void gradosAnteriores_taekwondoMenores() {
		assertEquals(List.of(), config.obtenerGradosAnteriores(Deporte.TAEKWONDO, true, BLANCO));
		assertEquals(List.of(BLANCO, BLANCO_AMARILLO, AMARILLO, AMARILLO_NARANJA, NARANJA, NARANJA_VERDE,
				VERDE, VERDE_AZUL, AZUL, AZUL_ROJO),
				config.obtenerGradosAnteriores(Deporte.TAEKWONDO, true, ROJO));
		List<TipoGrado> todasLasClavesMenores = List.of(BLANCO, BLANCO_AMARILLO, AMARILLO, AMARILLO_NARANJA,
				NARANJA, NARANJA_VERDE, VERDE, VERDE_AZUL, AZUL, AZUL_ROJO, ROJO, ROJO_NEGRO_1_PUM,
				ROJO_NEGRO_2_PUM);
		// Grado que no es clave (ultimo PUM, o un DAN): devuelve todas las claves
		assertEquals(todasLasClavesMenores,
				config.obtenerGradosAnteriores(Deporte.TAEKWONDO, true, ROJO_NEGRO_3_PUM));
		assertEquals(todasLasClavesMenores,
				config.obtenerGradosAnteriores(Deporte.TAEKWONDO, true, NEGRO_1_DAN));
	}

	@Test
	void gradosAnteriores_taekwondoAdultos_incluyeIntermedios() {
		assertEquals(List.of(), config.obtenerGradosAnteriores(Deporte.TAEKWONDO, false, BLANCO));
		assertEquals(List.of(BLANCO), config.obtenerGradosAnteriores(Deporte.TAEKWONDO, false, BLANCO_AMARILLO));
		assertEquals(List.of(BLANCO, BLANCO_AMARILLO, AMARILLO, AMARILLO_NARANJA, NARANJA, NARANJA_VERDE,
				VERDE, VERDE_AZUL, AZUL, AZUL_ROJO, ROJO),
				config.obtenerGradosAnteriores(Deporte.TAEKWONDO, false, NEGRO_1_DAN));
		List<TipoGrado> todasLasClavesAdultos = List.of(BLANCO, BLANCO_AMARILLO, AMARILLO, AMARILLO_NARANJA,
				NARANJA, NARANJA_VERDE, VERDE, VERDE_AZUL, AZUL, AZUL_ROJO, ROJO, NEGRO_1_DAN, NEGRO_2_DAN,
				NEGRO_3_DAN, NEGRO_4_DAN);
		assertEquals(todasLasClavesAdultos,
				config.obtenerGradosAnteriores(Deporte.TAEKWONDO, false, NEGRO_5_DAN));
		assertEquals(todasLasClavesAdultos,
				config.obtenerGradosAnteriores(Deporte.TAEKWONDO, false, ROJO_NEGRO_1_PUM));
	}

	@Test
	void gradosAnteriores_kickboxing_igualEnMenoresYAdultos() {
		List<TipoGrado> hastaAzul = List.of(BLANCO, BLANCO_AMARILLO, AMARILLO, AMARILLO_NARANJA, NARANJA,
				NARANJA_VERDE, VERDE, VERDE_AZUL);
		assertEquals(hastaAzul, config.obtenerGradosAnteriores(Deporte.KICKBOXING, true, AZUL));
		assertEquals(hastaAzul, config.obtenerGradosAnteriores(Deporte.KICKBOXING, false, AZUL));
		assertEquals(List.of(BLANCO, BLANCO_AMARILLO, AMARILLO, AMARILLO_NARANJA, NARANJA, NARANJA_VERDE,
				VERDE, VERDE_AZUL, AZUL, AZUL_ROJO, ROJO, NEGRO_1_DAN, NEGRO_2_DAN, NEGRO_3_DAN, NEGRO_4_DAN),
				config.obtenerGradosAnteriores(Deporte.KICKBOXING, true, NEGRO_5_DAN));
	}
}
