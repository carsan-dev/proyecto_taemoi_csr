package com.taemoi.project.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EmailUtilsTest {

	@Test
	void unEmailEnBlancoNuncaVinculaAlumnos() {
		assertFalse(EmailUtils.puedeVincularAlumnos(null));
		assertFalse(EmailUtils.puedeVincularAlumnos(""));
		assertFalse(EmailUtils.puedeVincularAlumnos("   "));
		assertFalse(EmailUtils.puedeVincularAlumnos("\t\n"));
	}

	@Test
	void unEmailConContenidoPuedeVincularAlumnos() {
		assertTrue(EmailUtils.puedeVincularAlumnos("familia@example.test"));
		assertTrue(EmailUtils.puedeVincularAlumnos(" Familia@Example.test "));
	}
}
