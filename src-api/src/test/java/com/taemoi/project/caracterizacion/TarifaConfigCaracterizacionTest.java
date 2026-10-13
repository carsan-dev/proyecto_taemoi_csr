package com.taemoi.project.caracterizacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.taemoi.project.config.TarifaConfig;
import com.taemoi.project.entities.RolFamiliar;
import com.taemoi.project.entities.TipoTarifa;

/**
 * Caracterizacion de TarifaConfig: cuantia por tipo de tarifa y regla de rol familiar.
 */
class TarifaConfigCaracterizacionTest {

	private TarifaConfig config;

	@BeforeEach
	void setUp() {
		config = new TarifaConfig();
		config.init();
	}

	private static Map<TipoTarifa, Double> esperado() {
		Map<TipoTarifa, Double> m = new EnumMap<>(TipoTarifa.class);
		m.put(TipoTarifa.ADULTO, 30.0);
		m.put(TipoTarifa.ADULTO_GRUPO, 20.0);
		m.put(TipoTarifa.FAMILIAR, 0.0);
		m.put(TipoTarifa.INFANTIL, 28.0);
		m.put(TipoTarifa.INFANTIL_GRUPO, 20.0);
		m.put(TipoTarifa.HERMANOS, 26.0);
		m.put(TipoTarifa.PADRES_HIJOS, 0.0);
		m.put(TipoTarifa.KICKBOXING, 30.0);
		m.put(TipoTarifa.PILATES, 30.0);
		m.put(TipoTarifa.DEFENSA_PERSONAL_FEMENINA, 25.0);
		return m;
	}

	@Test
	void cuantiaPorTipo_todos() {
		Map<TipoTarifa, Double> esperado = esperado();
		assertEquals(TipoTarifa.values().length, esperado.size());
		for (TipoTarifa tipo : TipoTarifa.values()) {
			assertEquals(esperado.get(tipo), config.obtenerCuantia(tipo), 0.0, tipo.name());
			assertTrue(config.tieneCuantiaDefinida(tipo), tipo.name());
		}
		assertEquals(esperado(), Map.copyOf(config.obtenerTodasLasCuantias()));
		assertEquals(List.of(TipoTarifa.FAMILIAR, TipoTarifa.PADRES_HIJOS, TipoTarifa.ADULTO_GRUPO,
				TipoTarifa.INFANTIL_GRUPO, TipoTarifa.HERMANOS, TipoTarifa.INFANTIL, TipoTarifa.ADULTO,
				TipoTarifa.KICKBOXING, TipoTarifa.PILATES, TipoTarifa.DEFENSA_PERSONAL_FEMENINA),
				List.copyOf(config.obtenerTodasLasCuantias().keySet()));
		assertThrows(UnsupportedOperationException.class,
				() -> config.obtenerTodasLasCuantias().put(TipoTarifa.ADULTO, 1.0));
		assertEquals(0.0, config.obtenerCuantiaDefault(), 0.0);
	}

	@Test
	void padresHijos_dependeDelRol() {
		assertEquals(28.0, config.obtenerCuantiaConRol(TipoTarifa.PADRES_HIJOS, RolFamiliar.PADRE), 0.0);
		assertEquals(28.0, config.obtenerCuantiaConRol(TipoTarifa.PADRES_HIJOS, RolFamiliar.MADRE), 0.0);
		assertEquals(26.0, config.obtenerCuantiaConRol(TipoTarifa.PADRES_HIJOS, RolFamiliar.HIJO), 0.0);
		assertEquals(26.0, config.obtenerCuantiaConRol(TipoTarifa.PADRES_HIJOS, RolFamiliar.HIJA), 0.0);
		assertEquals(0.0, config.obtenerCuantiaConRol(TipoTarifa.PADRES_HIJOS, RolFamiliar.NINGUNO), 0.0);
		// rol nulo: cae en la cuantia base de PADRES_HIJOS (0.0)
		assertEquals(0.0, config.obtenerCuantiaConRol(TipoTarifa.PADRES_HIJOS, null), 0.0);
		// sin rol: obtenerCuantia(PADRES_HIJOS) = 0.0
		assertEquals(0.0, config.obtenerCuantia(TipoTarifa.PADRES_HIJOS), 0.0);
	}

	@Test
	void otrasTarifas_ignoranElRol() {
		Map<TipoTarifa, Double> esperado = esperado();
		for (TipoTarifa tipo : TipoTarifa.values()) {
			if (tipo == TipoTarifa.PADRES_HIJOS) {
				continue;
			}
			for (RolFamiliar rol : RolFamiliar.values()) {
				assertEquals(esperado.get(tipo), config.obtenerCuantiaConRol(tipo, rol), 0.0, tipo + "/" + rol);
			}
			assertEquals(esperado.get(tipo), config.obtenerCuantiaConRol(tipo, null), 0.0, tipo + "/null");
		}
		// HERMANOS: cuantia plana por miembro, sin depender del rol ni del numero de hermanos
		assertEquals(26.0, config.obtenerCuantiaConRol(TipoTarifa.HERMANOS, RolFamiliar.HIJO), 0.0);
		assertEquals(26.0, config.obtenerCuantiaConRol(TipoTarifa.HERMANOS, RolFamiliar.PADRE), 0.0);
	}

	@Test
	void tarifaNula_lanzaIllegalArgument() {
		assertThrows(IllegalArgumentException.class, () -> config.obtenerCuantia(null));
		assertThrows(IllegalArgumentException.class, () -> config.obtenerCuantiaConRol(null, RolFamiliar.PADRE));
		assertFalse(config.tieneCuantiaDefinida(null));
	}
}
