package com.taemoi.project.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class EstadoAlumnosTest {

	@Test
	void desdeParametro_aceptaLosTresValoresSinDistinguirMayusculas() {
		assertEquals(EstadoAlumnos.ACTIVOS, EstadoAlumnos.desdeParametro("activos"));
		assertEquals(EstadoAlumnos.INACTIVOS, EstadoAlumnos.desdeParametro("inactivos"));
		assertEquals(EstadoAlumnos.TODOS, EstadoAlumnos.desdeParametro("todos"));
		assertEquals(EstadoAlumnos.INACTIVOS, EstadoAlumnos.desdeParametro("Inactivos"));
		assertEquals(EstadoAlumnos.TODOS, EstadoAlumnos.desdeParametro("TODOS"));
	}

	@Test
	void desdeParametro_otroValor_falla() {
		for (String valor : new String[] { "bajas", "activo", "1", "", null }) {
			assertThrows(IllegalArgumentException.class, () -> EstadoAlumnos.desdeParametro(valor), "" + valor);
		}
	}

	@Test
	void desdeIncluirInactivos_comoElParametroDeSiempre() {
		assertEquals(EstadoAlumnos.ACTIVOS, EstadoAlumnos.desdeIncluirInactivos(false));
		assertEquals(EstadoAlumnos.TODOS, EstadoAlumnos.desdeIncluirInactivos(true));
	}
}
