package com.taemoi.project.plataforma.importacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Importador de alumnos (fase 4): conversión de los grados de la hoja maestra a los códigos del sistema. */
class ConversionGradosTest {

	@ParameterizedTest
	@CsvSource({
			"BLANCO, BLANCO",
			"blanco, BLANCO",
			"BLANCO/AMARILLO, BLANCO_AMARILLO",
			"Blanco / Amarillo, BLANCO_AMARILLO",
			"AMARILLO, AMARILLO",
			"AMARILLO/NARANJA, AMARILLO_NARANJA",
			"NARANJA, NARANJA",
			"NARANJA/VERDE, NARANJA_VERDE",
			"VERDE, VERDE",
			"VERDE/AZUL, VERDE_AZUL",
			"AZUL, AZUL",
			"AZUL/ROJO, AZUL_ROJO",
			"ROJO, ROJO",
			"1 PUM, ROJO_NEGRO_1_PUM",
			"2 PUM, ROJO_NEGRO_2_PUM",
			"3 pum, ROJO_NEGRO_3_PUM",
			"1 DAN, NEGRO_1_DAN",
			"2DAN, NEGRO_2_DAN",
			"3 DAN, NEGRO_3_DAN",
			"4 DAN, NEGRO_4_DAN",
			"5 DAN, NEGRO_5_DAN" })
	void adulto_codigoDirecto_sinAviso(String texto, String codigo) {
		ConversionGrados.Resultado r = ConversionGrados.convertir(texto, false);
		assertEquals(codigo, r.codigo());
		assertNull(r.aviso());
		assertNull(r.error());
	}

	@Test
	void sinGrado_blancoConAviso() {
		for (String texto : new String[] { "SIN GRADO", "sin  grado", "" }) {
			ConversionGrados.Resultado r = ConversionGrados.convertir(texto, true);
			assertEquals("BLANCO", r.codigo(), texto);
			assertEquals("Sin grado: entrará como BLANCO.", r.aviso(), texto);
			assertNull(r.error());
		}
	}

	@Test
	void menorConDan_pumDelMismoNumeroConAviso() {
		for (int n = 1; n <= 3; n++) {
			ConversionGrados.Resultado r = ConversionGrados.convertir(n + " DAN", true);
			assertEquals("ROJO_NEGRO_" + n + "_PUM", r.codigo());
			assertEquals("Menor con " + n + " DAN: entrará como " + n + " PUM.", r.aviso());
			assertNull(r.error());
		}
	}

	@Test
	void menorCon4o5Dan_error() {
		for (int n = 4; n <= 5; n++) {
			ConversionGrados.Resultado r = ConversionGrados.convertir(n + " DAN", true);
			assertNull(r.codigo());
			assertEquals("Menor con " + n + " DAN: no hay PUM equivalente; revisa el grado o la fecha de nacimiento.",
					r.error());
		}
	}

	@Test
	void menorConColorOPum_sinCambio() {
		assertEquals("VERDE_AZUL", ConversionGrados.convertir("VERDE/AZUL", true).codigo());
		ConversionGrados.Resultado pum = ConversionGrados.convertir("2 PUM", true);
		assertEquals("ROJO_NEGRO_2_PUM", pum.codigo());
		assertNull(pum.aviso());
	}

	@ParameterizedTest
	@CsvSource({ "NEGRO", "4 PUM", "6 DAN", "0 DAN", "ROJO/NEGRO", "MARRÓN", "BLANCO_AMARILLO", "AMARILLO/VERDE" })
	void noReconocido_error(String texto) {
		ConversionGrados.Resultado r = ConversionGrados.convertir(texto, false);
		assertNull(r.codigo());
		assertEquals("«" + texto + "» no es un grado válido.", r.error());
	}
}
