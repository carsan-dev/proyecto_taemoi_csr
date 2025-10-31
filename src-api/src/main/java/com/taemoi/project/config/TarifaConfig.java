package com.taemoi.project.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.taemoi.project.entities.TipoTarifa;

import jakarta.annotation.PostConstruct;

/**
 * Configuración centralizada para las cuantías de tarifas.
 * Define los precios de cada tipo de tarifa de forma centralizada y configurable.
 *
 * Esta clase reemplaza el método asignarCuantiaTarifa() que tenía
 * los precios hardcodeados en un switch statement en AlumnoServiceImpl.
 *
 * NOTA: En el futuro, estos valores podrían cargarse desde:
 * - Un archivo de propiedades (application.properties)
 * - Una tabla de base de datos (para permitir cambios dinámicos)
 * - Un servicio de configuración externo
 */
@Component
public class TarifaConfig {

	/**
	 * Mapa de tipos de tarifa y sus cuantías correspondientes.
	 */
	private Map<TipoTarifa, Double> cuantias;

	/**
	 * Cuantía por defecto para tarifas no encontradas o sin configurar.
	 */
	private static final double CUANTIA_DEFAULT = 0.0;

	/**
	 * Inicializa las cuantías de tarifas al arrancar la aplicación.
	 */
	@PostConstruct
	public void init() {
		cuantias = new LinkedHashMap<>();

		// Tarifas especiales (sin costo o variables)
		cuantias.put(TipoTarifa.FAMILIAR, 0.0);
		cuantias.put(TipoTarifa.PADRES_HIJOS, 0.0);

		// Tarifas de grupo (reducidas)
		cuantias.put(TipoTarifa.ADULTO_GRUPO, 20.0);
		cuantias.put(TipoTarifa.INFANTIL_GRUPO, 20.0);

		// Tarifa para hermanos
		cuantias.put(TipoTarifa.HERMANOS, 23.0);

		// Tarifas individuales infantiles
		cuantias.put(TipoTarifa.INFANTIL, 25.0);

		// Tarifas individuales adultos y actividades
		cuantias.put(TipoTarifa.ADULTO, 30.0);
		cuantias.put(TipoTarifa.PILATES, 30.0);
		cuantias.put(TipoTarifa.DEFENSA_PERSONAL_FEMENINA, 30.0);

		// Hacer el mapa inmutable para prevenir modificaciones accidentales
		cuantias = Collections.unmodifiableMap(cuantias);
	}

	/**
	 * Obtiene la cuantía asociada a un tipo de tarifa.
	 *
	 * @param tipoTarifa El tipo de tarifa.
	 * @return La cuantía correspondiente, o CUANTIA_DEFAULT si no se encuentra.
	 * @throws IllegalArgumentException si tipoTarifa es null.
	 */
	public double obtenerCuantia(TipoTarifa tipoTarifa) {
		if (tipoTarifa == null) {
			throw new IllegalArgumentException("El tipo de tarifa no puede ser null");
		}

		return cuantias.getOrDefault(tipoTarifa, CUANTIA_DEFAULT);
	}

	/**
	 * Verifica si existe una cuantía definida para el tipo de tarifa especificado.
	 *
	 * @param tipoTarifa El tipo de tarifa a verificar.
	 * @return true si existe una cuantía definida, false en caso contrario.
	 */
	public boolean tieneCuantiaDefinida(TipoTarifa tipoTarifa) {
		return tipoTarifa != null && cuantias.containsKey(tipoTarifa);
	}

	/**
	 * Obtiene todas las tarifas configuradas (inmutable).
	 *
	 * @return Mapa inmutable de tipos de tarifa y sus cuantías.
	 */
	public Map<TipoTarifa, Double> obtenerTodasLasCuantias() {
		return cuantias;
	}

	/**
	 * Obtiene la cuantía por defecto utilizada cuando no se encuentra una tarifa.
	 *
	 * @return La cuantía por defecto.
	 */
	public double obtenerCuantiaDefault() {
		return CUANTIA_DEFAULT;
	}
}
