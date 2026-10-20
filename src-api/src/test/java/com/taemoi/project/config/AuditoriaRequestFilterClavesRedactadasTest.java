package com.taemoi.project.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Claves del cuerpo que el filtro HTTP no guarda en las filas genéricas (las 4xx de las escrituras con registro
 * propio y las demás). Pieza E8-2: el alta y la edición de una sede llevan datos de contacto (D8-16).
 */
class AuditoriaRequestFilterClavesRedactadasTest {

	private static final Set<String> DATOS_DE_SEDE = Set.of("nombre", "direccion", "localidad", "codigoPostal",
			"telefono", "email", "codigoClubFederacion");

	@Test
	void sedes_altaYEdicion_redactanSusDatos() {
		for (String ruta : List.of("/api/sedes", "/api/sedes/", "/API/SEDES", "/api/sedes/5", "/api/sedes/5/",
				"/api//sedes/5", "/api/%73edes/5")) {
			assertEquals(DATOS_DE_SEDE, AuditoriaRequestFilter.clavesRedactadas(ruta), ruta);
		}
	}

	@Test
	void sedes_activa_noRedacta() {
		assertEquals(Set.of(), AuditoriaRequestFilter.clavesRedactadas("/api/sedes/5/activa"));
		assertEquals(Set.of(), AuditoriaRequestFilter.clavesRedactadas("/api/sedes-otra"));
	}

	@Test
	void rutasDeSiempre_igual() {
		assertEquals(Set.of("email"), AuditoriaRequestFilter.clavesRedactadas("/api/grupos/3/profesores"));
		assertEquals(Set.of("grupo"), AuditoriaRequestFilter.clavesRedactadas("/api/admin/dni-compartidos/aplicar"));
		assertEquals(Set.of(), AuditoriaRequestFilter.clavesRedactadas("/api/alumnos/3"));
	}
}
