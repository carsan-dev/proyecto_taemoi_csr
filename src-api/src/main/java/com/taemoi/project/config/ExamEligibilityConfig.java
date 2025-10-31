package com.taemoi.project.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.taemoi.project.entities.TipoGrado;

import jakarta.annotation.PostConstruct;

/**
 * Configuración centralizada para determinar la elegibilidad de examen.
 * Define los meses requeridos entre exámenes según el grado y la edad del alumno.
 *
 * Esta clase reemplaza el método obtenerMesesRequeridosParaExamen() que estaba
 * en AlumnoServiceImpl con lógica duplicada y difícil de mantener.
 */
@Component
public class ExamEligibilityConfig {

	/**
	 * Estructura de mapas: EsMenor -> (TipoGrado -> Meses Requeridos)
	 */
	private Map<Boolean, Map<TipoGrado, Long>> mesesRequeridos;

	/**
	 * Inicializa los mapas de elegibilidad al arrancar la aplicación.
	 */
	@PostConstruct
	public void init() {
		mesesRequeridos = new LinkedHashMap<>();
		mesesRequeridos.put(true, buildMesesRequeridosMenores());   // Menores de 13 años
		mesesRequeridos.put(false, buildMesesRequeridosMayores());  // 13 años o más

		// Hacer los mapas inmutables para seguridad
		makeImmutable();
	}

	/**
	 * Obtiene los meses requeridos para ser apto para examen según la edad y el grado actual.
	 *
	 * @param edad La edad del alumno en años.
	 * @param tipoGrado El grado actual del alumno.
	 * @return Los meses requeridos desde el último examen, o Long.MAX_VALUE si no es elegible.
	 */
	public long obtenerMesesRequeridos(int edad, TipoGrado tipoGrado) {
		if (tipoGrado == null) {
			return Long.MAX_VALUE;
		}

		boolean esMenor = edad < 13;
		Map<TipoGrado, Long> gradoMap = mesesRequeridos.get(esMenor);

		if (gradoMap == null) {
			return Long.MAX_VALUE;
		}

		// Si no existe el grado en el mapa, no es apto (retorna Long.MAX_VALUE)
		return gradoMap.getOrDefault(tipoGrado, Long.MAX_VALUE);
	}

	/**
	 * Construye el mapa de meses requeridos para alumnos menores de 13 años.
	 * Los menores tienen progresión más frecuente en los grados iniciales.
	 */
	private Map<TipoGrado, Long> buildMesesRequeridosMenores() {
		Map<TipoGrado, Long> mapa = new LinkedHashMap<>();

		// Grados blancos y amarillos: 2 meses
		mapa.put(TipoGrado.BLANCO, 2L);
		mapa.put(TipoGrado.BLANCO_AMARILLO, 2L);

		// Grados amarillos y naranjas: 3-4 meses
		mapa.put(TipoGrado.AMARILLO, 3L);
		mapa.put(TipoGrado.AMARILLO_NARANJA, 3L);
		mapa.put(TipoGrado.NARANJA, 4L);
		mapa.put(TipoGrado.NARANJA_VERDE, 4L);

		// Grados verdes y azules: 6-10 meses
		mapa.put(TipoGrado.VERDE, 6L);
		mapa.put(TipoGrado.VERDE_AZUL, 6L);
		mapa.put(TipoGrado.AZUL, 8L);
		mapa.put(TipoGrado.AZUL_ROJO, 10L);

		// Grado rojo: 12 meses
		mapa.put(TipoGrado.ROJO, 12L);

		// PUM (grados negros para menores): 24-36 meses
		mapa.put(TipoGrado.ROJO_NEGRO_1_PUM, 24L);
		mapa.put(TipoGrado.ROJO_NEGRO_2_PUM, 36L);
		// ROJO_NEGRO_3_PUM no tiene siguiente grado (no está en el mapa)

		return mapa;
	}

	/**
	 * Construye el mapa de meses requeridos para alumnos de 13 años o más.
	 * Los mayores tienen progresión más lenta, especialmente en cinturones negros.
	 */
	private Map<TipoGrado, Long> buildMesesRequeridosMayores() {
		Map<TipoGrado, Long> mapa = new LinkedHashMap<>();

		// Grados de color: 3-12 meses
		mapa.put(TipoGrado.BLANCO, 3L);
		mapa.put(TipoGrado.AMARILLO, 5L);
		mapa.put(TipoGrado.NARANJA, 6L);
		mapa.put(TipoGrado.VERDE, 8L);
		mapa.put(TipoGrado.AZUL, 10L);
		mapa.put(TipoGrado.ROJO, 12L);

		// DAN (grados negros para mayores): 24-60 meses
		mapa.put(TipoGrado.NEGRO_1_DAN, 24L);  // 2 años
		mapa.put(TipoGrado.NEGRO_2_DAN, 36L);  // 3 años
		mapa.put(TipoGrado.NEGRO_3_DAN, 48L);  // 4 años
		mapa.put(TipoGrado.NEGRO_4_DAN, 60L);  // 5 años
		// NEGRO_5_DAN no tiene siguiente grado (no está en el mapa)

		return mapa;
	}

	/**
	 * Convierte todos los mapas en inmutables para prevenir modificaciones accidentales.
	 */
	private void makeImmutable() {
		mesesRequeridos.replaceAll((esMenor, gradoMap) ->
			Collections.unmodifiableMap(gradoMap)
		);
		mesesRequeridos = Collections.unmodifiableMap(mesesRequeridos);
	}

	/**
	 * Verifica si un grado específico tiene un período de espera definido.
	 *
	 * @param tipoGrado El grado a verificar.
	 * @param esMenor true si es para menores, false para mayores.
	 * @return true si existe un período definido, false en caso contrario.
	 */
	public boolean tienePeriodoDefinido(TipoGrado tipoGrado, boolean esMenor) {
		Map<TipoGrado, Long> gradoMap = mesesRequeridos.get(esMenor);
		return gradoMap != null && gradoMap.containsKey(tipoGrado);
	}

	/**
	 * Obtiene todos los grados con períodos de espera definidos para menores.
	 *
	 * @return Mapa inmutable de grados y sus meses requeridos.
	 */
	public Map<TipoGrado, Long> obtenerMesesRequeridosMenores() {
		return mesesRequeridos.get(true);
	}

	/**
	 * Obtiene todos los grados con períodos de espera definidos para mayores.
	 *
	 * @return Mapa inmutable de grados y sus meses requeridos.
	 */
	public Map<TipoGrado, Long> obtenerMesesRequeridosMayores() {
		return mesesRequeridos.get(false);
	}
}
