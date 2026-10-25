package com.taemoi.project.utils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Decisión 297 (ronda 2): el precio de un cargo o de un producto, con 2 decimales como mucho y no negativo. */
class ImporteCargoTest {

	@Test
	void precioValido_nuloYHastaDosDecimales_tambienLosInexactosEnBinario() {
		for (Double precio : new Double[] { null, 0.0, -0.0, 0.07, 0.3, 1.1, 3.3, 12.5,
				37.5, 1234.56, 99999999.99, 1e12 }) {
			assertDoesNotThrow(() -> ImporteCargo.exigirPrecioValido(precio), String.valueOf(precio));
		}
	}

	@Test
	void precioNoValido_masDeDosDecimales_negativoONoNumero() {
		assertMensaje(ImporteCargo.PRECIO_CON_MAS_DE_DOS_DECIMALES, 3.335);
		assertMensaje(ImporteCargo.PRECIO_CON_MAS_DE_DOS_DECIMALES, 0.001);
		assertMensaje(ImporteCargo.PRECIO_CON_MAS_DE_DOS_DECIMALES, 0.1 + 0.2);
		assertMensaje(ImporteCargo.PRECIO_NEGATIVO, -0.01);
		assertMensaje(ImporteCargo.PRECIO_NEGATIVO, -3.335);
		assertMensaje(ImporteCargo.PRECIO_NO_VALIDO, Double.NaN);
		assertMensaje(ImporteCargo.PRECIO_NO_VALIDO, Double.POSITIVE_INFINITY);
		assertMensaje(ImporteCargo.PRECIO_NO_VALIDO, Double.NEGATIVE_INFINITY);
	}

	@Test
	void siCambia_soloCompruebaElPrecioNuevo() {
		assertDoesNotThrow(() -> ImporteCargo.exigirPrecioValidoSiCambia(null, 3.335));
		assertDoesNotThrow(() -> ImporteCargo.exigirPrecioValidoSiCambia(3.335, 3.335));
		assertThrows(IllegalArgumentException.class, () -> ImporteCargo.exigirPrecioValidoSiCambia(3.335, null));
		assertThrows(IllegalArgumentException.class, () -> ImporteCargo.exigirPrecioValidoSiCambia(3.336, 3.335));
		assertDoesNotThrow(() -> ImporteCargo.exigirPrecioValidoSiCambia(3.34, 3.335));
	}

	private static void assertMensaje(String esperado, double precio) {
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
				() -> ImporteCargo.exigirPrecioValido(precio), String.valueOf(precio));
		assertEquals(esperado, e.getMessage());
	}
}
