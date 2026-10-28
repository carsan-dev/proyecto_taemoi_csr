package com.taemoi.project.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Importes de los PDF en es-ES («28,00 €», «1.234,50 €»), sin depender del idioma de la JVM. */
class FormatoImporteTest {

	private final Locale original = Locale.getDefault();

	@AfterEach
	void restaurarLocale() {
		Locale.setDefault(original);
	}

	@Test
	void eur_formatoEspanolConCualquierLocaleDeLaJvm() {
		for (Locale locale : List.of(Locale.ROOT, Locale.US, Locale.GERMANY, Locale.of("es", "ES"))) {
			Locale.setDefault(locale);
			assertEquals("0,00 €", FormatoImporte.eur(0.0), locale.toString());
			assertEquals("28,00 €", FormatoImporte.eur(28.0), locale.toString());
			assertEquals("1.234,50 €", FormatoImporte.eur(1234.5), locale.toString());
			assertEquals("1.234,56 €", FormatoImporte.eur(new BigDecimal("1234.56")), locale.toString());
		}
	}

	@Test
	void eur_nuloCuentaComoCero() {
		assertEquals("0,00 €", FormatoImporte.eur((Double) null));
		assertEquals("0,00 €", FormatoImporte.eur((BigDecimal) null));
	}

	@Test
	void eur_redondeaHalfUpADosDecimalesComoImporteCargo() {
		assertEquals("26,01 €", FormatoImporte.eur(new BigDecimal("26.005")));
		assertEquals("1,01 €", FormatoImporte.eur(1.005), "decimal más corto del double, como ImporteCargo.de");
		assertEquals("0,30 €", FormatoImporte.eur(0.1 + 0.2));
		assertEquals(ImporteCargo.de(12.345, 1).toPlainString().replace('.', ',') + " €", FormatoImporte.eur(12.345));
	}

	@Test
	void eur_negativosConSigno() {
		assertEquals("-28,00 €", FormatoImporte.eur(-28.0));
	}

	@Test
	void eur_espacioNormalAntesDelSimbolo() {
		assertEquals(' ', FormatoImporte.eur(28.0).charAt(5), "espacio U+0020, no NBSP");
	}
}
