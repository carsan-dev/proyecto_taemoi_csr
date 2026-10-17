package com.taemoi.project.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

class OrdenAlfabeticoTest {

	record Persona(Long id, String nombre, String apellidos) {
	}

	record Nombrado(Long id, String nombre) {
	}

	@Test
	void textoIgnoraMayusculasYTildes() {
		assertEquals(0, OrdenAlfabetico.TEXTO.compare("Álvaro", "alvaro"));
		assertEquals(0, OrdenAlfabetico.TEXTO.compare("JOSÉ", "jose"));
		assertTrue(OrdenAlfabetico.TEXTO.compare("Ángel", "Beatriz") < 0, "la tilde no manda «Á» al final");
		assertTrue(OrdenAlfabetico.TEXTO.compare("zoe", "Ana") > 0, "la minúscula no va antes que la mayúscula");
	}

	@Test
	void laEneVaDespuesDeLaN() {
		assertTrue(OrdenAlfabetico.TEXTO.compare("Nuria", "Ñoño") < 0);
		assertTrue(OrdenAlfabetico.TEXTO.compare("Ñoño", "Oscar") < 0);
		assertTrue(OrdenAlfabetico.TEXTO.compare("Muñoz", "Munoz") > 0);
	}

	@Test
	void textoNuloVaAlFinal() {
		assertTrue(OrdenAlfabetico.TEXTO.compare(null, "Zoe") > 0);
		assertTrue(OrdenAlfabetico.TEXTO.compare("Zoe", null) < 0);
		assertEquals(0, OrdenAlfabetico.TEXTO.compare(null, null));
	}

	@Test
	void personasPorNombreLuegoApellidosYDesempatePorId() {
		Persona ana2 = new Persona(2L, "Ana", "Pérez");
		Persona ana1 = new Persona(1L, "ana", "perez"); // igual a la anterior salvo mayúsculas y tildes: manda el id
		Persona anaAbad = new Persona(9L, "Ana", "Abad");
		Persona alvaro = new Persona(5L, "Álvaro", "Ruiz");
		Persona sinNombre = new Persona(3L, null, "Abad");
		Persona beaSinApellidos = new Persona(4L, "Bea", null);
		Persona beaRuiz = new Persona(8L, "Bea", "Ruiz");
		Persona sinId = new Persona(null, "Ana", "Abad");

		List<Persona> lista = new ArrayList<>(
				List.of(sinNombre, beaSinApellidos, ana2, alvaro, sinId, ana1, beaRuiz, anaAbad));
		for (int i = 0; i < 5; i++) {
			Collections.shuffle(lista);
			lista.sort(OrdenAlfabetico.porNombreYApellidos(Persona::nombre, Persona::apellidos, Persona::id));
			assertEquals(List.of(alvaro, anaAbad, sinId, ana1, ana2, beaRuiz, beaSinApellidos, sinNombre), lista);
		}
	}

	@Test
	void nombradosPorNombreYDesempatePorId() {
		Nombrado kb = new Nombrado(3L, "Kickboxing Iniciación");
		Nombrado tkd2 = new Nombrado(2L, "taekwondo adultos");
		Nombrado tkd1 = new Nombrado(7L, "Taekwondo Adultos");
		Nombrado tkdInf = new Nombrado(1L, "Taekwondo Infantil");
		Nombrado sinNombre = new Nombrado(0L, null);

		List<Nombrado> lista = new ArrayList<>(List.of(sinNombre, tkdInf, tkd1, kb, tkd2));
		for (int i = 0; i < 5; i++) {
			Collections.shuffle(lista);
			lista.sort(OrdenAlfabetico.porNombre(Nombrado::nombre, Nombrado::id));
			assertEquals(List.of(kb, tkd2, tkd1, tkdInf, sinNombre), lista);
		}
	}
}
