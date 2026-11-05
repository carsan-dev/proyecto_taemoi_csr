package com.taemoi.project.plataforma;

import java.util.List;

/**
 * Fase 4, pieza F4-2 (decisiones 390 y 444): cuerpo de {@code POST /api/plataforma/escuelas}. Sin slug: sale del nombre.
 * Los valores se validan en {@link AltaEscuelaService} (400 con {@code mensaje}).
 */
public record AltaEscuelaRequest(String nombre, String emailContacto, Sede sede, Propietario propietario,
		List<String> disciplinas) {

	/** Primera sede de la escuela. */
	public record Sede(String nombre) {
	}

	/** {@code nombre} y {@code apellidos}: solo para invitar a un email sin cuenta (con cuenta, se ignoran). */
	public record Propietario(String nombre, String apellidos, String email) {
	}
}
