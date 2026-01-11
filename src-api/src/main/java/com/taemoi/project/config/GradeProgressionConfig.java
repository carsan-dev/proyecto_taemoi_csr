package com.taemoi.project.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.taemoi.project.entities.Deporte;
import com.taemoi.project.entities.TipoGrado;

import jakarta.annotation.PostConstruct;

/**
 * Configuración centralizada para la progresión de grados en diferentes deportes.
 * Maneja los mapas de progresión para Taekwondo (menores y mayores) y Kickboxing.
 *
 * Esta clase reemplaza los métodos mapaGradosMenoresTaekwondo(),
 * mapaGradosMayoresTaekwondo() y mapaGradosKickboxing() que estaban
 * dispersos en AlumnoServiceImpl.
 */
@Component
public class GradeProgressionConfig {

	/**
	 * Estructura de mapas: Deporte -> EsMenor -> (GradoActual -> GradoSiguiente)
	 */
	private Map<Deporte, Map<Boolean, Map<TipoGrado, TipoGrado>>> progressionMaps;

	/**
	 * Inicializa los mapas de progresión al arrancar la aplicación.
	 */
	@PostConstruct
	public void init() {
		progressionMaps = new LinkedHashMap<>();

		// Inicializar progresión para Taekwondo
		Map<Boolean, Map<TipoGrado, TipoGrado>> taekwondoMaps = new LinkedHashMap<>();
		taekwondoMaps.put(true, buildMapaGradosMenoresTaekwondo());  // Menores
		taekwondoMaps.put(false, buildMapaGradosMayoresTaekwondo()); // Mayores
		progressionMaps.put(Deporte.TAEKWONDO, taekwondoMaps);

		// Inicializar progresión para Kickboxing (mismo mapa para menores y mayores)
		Map<Boolean, Map<TipoGrado, TipoGrado>> kickboxingMaps = new LinkedHashMap<>();
		Map<TipoGrado, TipoGrado> kickboxingMap = buildMapaGradosKickboxing();
		kickboxingMaps.put(true, kickboxingMap);   // Menores
		kickboxingMaps.put(false, kickboxingMap);  // Mayores
		progressionMaps.put(Deporte.KICKBOXING, kickboxingMaps);

		// Hacer los mapas inmutables para seguridad
		makeImmutable();
	}

	/**
	 * Obtiene el siguiente grado según el deporte, la edad del alumno y su grado actual.
	 *
	 * @param deporte El deporte del alumno (TAEKWONDO o KICKBOXING).
	 * @param esMenor true si el alumno es menor de edad (< 13 años o 13 años sin cumplir 14 este año).
	 * @param gradoActual El grado actual del alumno.
	 * @return El siguiente grado, o null si ya está en el grado máximo o no hay progresión disponible.
	 * @throws IllegalArgumentException si el deporte no está soportado.
	 */
	public TipoGrado obtenerSiguienteGrado(Deporte deporte, boolean esMenor, TipoGrado gradoActual) {
		if (deporte == null || gradoActual == null) {
			return null;
		}

		Map<Boolean, Map<TipoGrado, TipoGrado>> deporteMaps = progressionMaps.get(deporte);
		if (deporteMaps == null) {
			throw new IllegalArgumentException("Deporte no soportado para progresión de grados: " + deporte);
		}

		Map<TipoGrado, TipoGrado> gradosMap = deporteMaps.get(esMenor);
		if (gradosMap == null) {
			return null;
		}

		return gradosMap.get(gradoActual);
	}

	/**
	 * Construye el mapa de progresión de grados para menores en Taekwondo.
	 * Incluye grados intermedios (BLANCO_AMARILLO, AMARILLO_NARANJA, etc.) y PUM.
	 */
	private Map<TipoGrado, TipoGrado> buildMapaGradosMenoresTaekwondo() {
		Map<TipoGrado, TipoGrado> mapa = new LinkedHashMap<>();
		mapa.put(TipoGrado.BLANCO, TipoGrado.BLANCO_AMARILLO);
		mapa.put(TipoGrado.BLANCO_AMARILLO, TipoGrado.AMARILLO);
		mapa.put(TipoGrado.AMARILLO, TipoGrado.AMARILLO_NARANJA);
		mapa.put(TipoGrado.AMARILLO_NARANJA, TipoGrado.NARANJA);
		mapa.put(TipoGrado.NARANJA, TipoGrado.NARANJA_VERDE);
		mapa.put(TipoGrado.NARANJA_VERDE, TipoGrado.VERDE);
		mapa.put(TipoGrado.VERDE, TipoGrado.VERDE_AZUL);
		mapa.put(TipoGrado.VERDE_AZUL, TipoGrado.AZUL);
		mapa.put(TipoGrado.AZUL, TipoGrado.AZUL_ROJO);
		mapa.put(TipoGrado.AZUL_ROJO, TipoGrado.ROJO);
		mapa.put(TipoGrado.ROJO, TipoGrado.ROJO_NEGRO_1_PUM);
		mapa.put(TipoGrado.ROJO_NEGRO_1_PUM, TipoGrado.ROJO_NEGRO_2_PUM);
		mapa.put(TipoGrado.ROJO_NEGRO_2_PUM, TipoGrado.ROJO_NEGRO_3_PUM);
		// ROJO_NEGRO_3_PUM es el grado máximo para menores, no tiene siguiente
		return mapa;
	}

	/**
	 * Construye el mapa de progresión de grados para mayores en Taekwondo.
	 * No incluye grados intermedios, progresa directamente entre cinturones principales.
	 */
	private Map<TipoGrado, TipoGrado> buildMapaGradosMayoresTaekwondo() {
		Map<TipoGrado, TipoGrado> mapa = new LinkedHashMap<>();
		mapa.put(TipoGrado.BLANCO, TipoGrado.AMARILLO);
		mapa.put(TipoGrado.AMARILLO, TipoGrado.NARANJA);
		mapa.put(TipoGrado.NARANJA, TipoGrado.VERDE);
		mapa.put(TipoGrado.VERDE, TipoGrado.AZUL);
		mapa.put(TipoGrado.AZUL, TipoGrado.ROJO);
		mapa.put(TipoGrado.ROJO, TipoGrado.NEGRO_1_DAN);
		mapa.put(TipoGrado.NEGRO_1_DAN, TipoGrado.NEGRO_2_DAN);
		mapa.put(TipoGrado.NEGRO_2_DAN, TipoGrado.NEGRO_3_DAN);
		mapa.put(TipoGrado.NEGRO_3_DAN, TipoGrado.NEGRO_4_DAN);
		mapa.put(TipoGrado.NEGRO_4_DAN, TipoGrado.NEGRO_5_DAN);
		// NEGRO_5_DAN es el grado máximo, no tiene siguiente
		return mapa;
	}

	/**
	 * Construye el mapa de progresión de grados para Kickboxing.
	 * Utiliza la misma progresión que mayores en Taekwondo (sin grados intermedios).
	 */
	private Map<TipoGrado, TipoGrado> buildMapaGradosKickboxing() {
		Map<TipoGrado, TipoGrado> mapa = new LinkedHashMap<>();
		mapa.put(TipoGrado.BLANCO, TipoGrado.AMARILLO);
		mapa.put(TipoGrado.BLANCO_AMARILLO, TipoGrado.AMARILLO);
		mapa.put(TipoGrado.AMARILLO, TipoGrado.NARANJA);
		mapa.put(TipoGrado.AMARILLO_NARANJA, TipoGrado.NARANJA);
		mapa.put(TipoGrado.NARANJA, TipoGrado.VERDE);
		mapa.put(TipoGrado.NARANJA_VERDE, TipoGrado.VERDE);
		mapa.put(TipoGrado.VERDE, TipoGrado.AZUL);
		mapa.put(TipoGrado.VERDE_AZUL, TipoGrado.AZUL);
		mapa.put(TipoGrado.AZUL, TipoGrado.ROJO);
		mapa.put(TipoGrado.AZUL_ROJO, TipoGrado.ROJO);
		mapa.put(TipoGrado.ROJO, TipoGrado.NEGRO_1_DAN);
		mapa.put(TipoGrado.NEGRO_1_DAN, TipoGrado.NEGRO_2_DAN);
		mapa.put(TipoGrado.NEGRO_2_DAN, TipoGrado.NEGRO_3_DAN);
		mapa.put(TipoGrado.NEGRO_3_DAN, TipoGrado.NEGRO_4_DAN);
		mapa.put(TipoGrado.NEGRO_4_DAN, TipoGrado.NEGRO_5_DAN);
		// NEGRO_5_DAN es el grado máximo, no tiene siguiente
		return mapa;
	}

	/**
	 * Convierte todos los mapas en inmutables para prevenir modificaciones accidentales.
	 */
	private void makeImmutable() {
		progressionMaps.replaceAll((deporte, esMenorMap) -> {
			esMenorMap.replaceAll((esMenor, gradoMap) ->
				Collections.unmodifiableMap(gradoMap)
			);
			return Collections.unmodifiableMap(esMenorMap);
		});
		progressionMaps = Collections.unmodifiableMap(progressionMaps);
	}

	/**
	 * Verifica si existe una progresión definida para el deporte especificado.
	 *
	 * @param deporte El deporte a verificar.
	 * @return true si hay progresión definida, false en caso contrario.
	 */
	public boolean tieneProgresion(Deporte deporte) {
		return progressionMaps.containsKey(deporte);
	}
}
