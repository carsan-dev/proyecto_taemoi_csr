package com.taemoi.project.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TelefonoUtilsTest {

	@Test
	void normalizaSeparadoresYPrefijoEspanol() {
		assertEquals("600111222", TelefonoUtils.normalizar("600111222"));
		assertEquals("600111222", TelefonoUtils.normalizar(" 600 11 12 22 "));
		assertEquals("955123456", TelefonoUtils.normalizar("955-12.34.56"));
		assertEquals("600111222", TelefonoUtils.normalizar("+34 600 111 222"));
		assertEquals("600111222", TelefonoUtils.normalizar("0034600111222"));
		assertEquals("+33612345678", TelefonoUtils.normalizar("+33 6 12 34 56 78"));
		assertNull(TelefonoUtils.normalizar(null));
		assertNull(TelefonoUtils.normalizar("   "));
	}

	@Test
	void validaLongitudYCaracteres() {
		assertTrue(TelefonoUtils.esValido("600111222"));
		assertTrue(TelefonoUtils.esValido("+33 6 12 34 56 78"));
		assertFalse(TelefonoUtils.esValido("12345"));
		assertFalse(TelefonoUtils.esValido("6001112ab"));
		assertFalse(TelefonoUtils.esValido("0"));
		assertFalse(TelefonoUtils.esValido(null));
		assertFalse(TelefonoUtils.esValido("1234567890123456"));
	}
}
