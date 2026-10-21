package com.taemoi.project.marca;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Pieza 8b-T (fase3-e8b-plan.md §4, punto de revisión 4): la regla de contraste del color de la escuela. Pura, sin
 * Spring. La tabla de §4 y una rejilla de 4.096 colores (16 niveles por canal): el texto sobre el primario, el
 * secundario de la cabecera y la tinta sobre blanco llegan siempre a 4,5:1.
 */
class ReglaContrasteTest {

	private static final String HEX = "^#[0-9A-F]{6}$";

	// ------------------------------------------------------------------ tabla de §4

	@Test
	void moiskimdo_textoBlanco_14_67() {
		TemaEscuela tema = ReglaContraste.tema("#1B2B2E");
		assertEquals("#1B2B2E", tema.primario());
		assertEquals(ReglaContraste.BLANCO, tema.sobrePrimario());
		assertEquals(14.67, ReglaContraste.contraste(tema.sobrePrimario(), tema.primario()), 0.005);
		assertEquals("#1B2B2E", tema.primarioTinta(), "ya llega sobre blanco: él mismo");
		assertFalse(tema.ajustado());
	}

	/**
	 * Verde de §4: el blanco no llega (3,50:1) y la tinta sí. El plan decía 4,70:1; la fórmula de WCAG 2.2 con la tinta
	 * {@code #1A2230} da 4,57:1 (luminancias 0,2503 y 0,0158).
	 */
	@Test
	void verde_textoTinta() {
		TemaEscuela tema = ReglaContraste.tema("#3E9B4F");
		assertEquals("#3E9B4F", tema.primario());
		assertEquals(ReglaContraste.TINTA, tema.sobrePrimario());
		assertEquals(4.57, ReglaContraste.contraste(tema.sobrePrimario(), tema.primario()), 0.005);
		assertTrue(ReglaContraste.contraste(ReglaContraste.BLANCO, tema.primario()) < 4.5);
		assertFalse(tema.ajustado());
		// Como tinta (botón, enlaces) no llega sobre blanco: se oscurece.
		assertNotEquals("#3E9B4F", tema.primarioTinta());
		assertTrue(ReglaContraste.contraste(tema.primarioTinta(), ReglaContraste.BLANCO) >= 4.5);
	}

	@ParameterizedTest
	@ValueSource(strings = { "#F2C230", "#FFD400", "#FFEB3B" })
	void amarillo_textoTinta(String amarillo) {
		TemaEscuela tema = ReglaContraste.tema(amarillo);
		assertEquals(amarillo, tema.primario());
		assertEquals(ReglaContraste.TINTA, tema.sobrePrimario());
		assertFalse(tema.ajustado());
	}

	/** Gris medio (y dos colores con su misma luz): ni el blanco ni la tinta llegan; se oscurece el primario. */
	@ParameterizedTest
	@ValueSource(strings = { "#808080", "#D9534F", "#E0457B", "#4F81BD" })
	void ningunoLlega_primarioOscurecido(String color) {
		assertTrue(ReglaContraste.contraste(ReglaContraste.BLANCO, color) < 4.5);
		assertTrue(ReglaContraste.contraste(ReglaContraste.TINTA, color) < 4.5);
		TemaEscuela tema = ReglaContraste.tema(color);
		assertTrue(tema.ajustado());
		assertNotEquals(color, tema.primario());
		assertEquals(ReglaContraste.BLANCO, tema.sobrePrimario());
		assertTrue(ReglaContraste.contraste(tema.sobrePrimario(), tema.primario()) >= 4.5);
		// Solo lo justo: un paso menos de oscurecimiento no llegaría.
		assertTrue(ReglaContraste.contraste(ReglaContraste.BLANCO, tema.primario()) < 5.2,
				"oscurecido de más: " + tema.primario());
	}

	@Test
	void grisMedio_sigueSiendoGris() {
		TemaEscuela tema = ReglaContraste.tema("#808080");
		String p = tema.primario();
		assertEquals(p.substring(1, 3), p.substring(3, 5), p);
		assertEquals(p.substring(3, 5), p.substring(5, 7), p);
	}

	/** Misma tonalidad en OKLCH tras oscurecer (sin recorte de gama en este rojo): la marca no cambia de color. */
	@Test
	void oscurecer_conservaLaTonalidad() {
		double antes = ReglaContraste.tonoOklch("#D9534F");
		double despues = ReglaContraste.tonoOklch(ReglaContraste.tema("#D9534F").primario());
		assertEquals(antes, despues, 2.0);
	}

	@Test
	void sinColor_porDefecto_1A2230() {
		TemaEscuela tema = ReglaContraste.tema(null);
		assertEquals("#1A2230", ReglaContraste.POR_DEFECTO);
		assertEquals("#1A2230", tema.primario());
		assertEquals(ReglaContraste.BLANCO, tema.sobrePrimario());
		assertEquals("#1A2230", tema.primarioTinta());
		assertFalse(tema.ajustado());
	}

	@Test
	void minusculas_seLeenComoMayusculas() {
		assertEquals(ReglaContraste.tema("#1B2B2E"), ReglaContraste.tema("#1b2b2e"));
	}

	@Test
	void moiskimdo_secundarioYSuave() {
		TemaEscuela tema = ReglaContraste.tema("#1B2B2E");
		assertNotEquals(tema.sobrePrimario(), tema.sobrePrimario2(), "el secundario se distingue del texto");
		assertTrue(ReglaContraste.contraste(tema.sobrePrimario2(), tema.primario()) >= 4.5);
		assertTrue(ReglaContraste.contraste(ReglaContraste.TINTA, tema.primarioSuave()) >= 4.5);
		assertNotEquals(ReglaContraste.BLANCO, tema.primarioSuave(), "el suave lleva algo del primario");
	}

	/**
	 * Vectores fijos que repite el spec del frontend ({@code regla-contraste.spec.ts}): la vista previa de «Logo y
	 * color» calcula lo mismo que el servidor.
	 */
	@ParameterizedTest
	@CsvSource({
			"#1B2B2E, #1B2B2E, #FFFFFF, #C6CACB, #1B2B2E, #E8EAEA, false",
			"#3E9B4F, #3E9B4F, #1A2230, #1A2230, #24853A, #ECF5ED, false",
			"#808080, #747474, #FFFFFF, #FFFFFF, #747474, #F1F1F1, true",
			"#FFD400, #FFD400, #1A2230, #534F24, #986E00, #FFFBE6, false" })
	void vectoresCompartidosConElFrontend(String guardado, String primario, String sobre, String sobre2, String tinta,
			String suave, boolean ajustado) {
		assertEquals(new TemaEscuela(primario, sobre, sobre2, tinta, suave, ajustado), ReglaContraste.tema(guardado));
	}

	// ------------------------------------------------------------------ rejilla

	@Test
	void tintaSiempreAlMenos4_5SobreBlanco() {
		List<String> fallos = new ArrayList<>();
		for (String color : rejilla()) {
			TemaEscuela tema = ReglaContraste.tema(color);
			if (ReglaContraste.contraste(tema.primarioTinta(), ReglaContraste.BLANCO) < 4.5) {
				fallos.add(color + " tinta " + tema.primarioTinta());
			}
		}
		assertEquals(List.of(), fallos);
	}

	@Test
	void textoSiempreAlMenos4_5SobreElPrimario() {
		List<String> fallos = new ArrayList<>();
		for (String color : rejilla()) {
			TemaEscuela tema = ReglaContraste.tema(color);
			if (ReglaContraste.contraste(tema.sobrePrimario(), tema.primario()) < 4.5) {
				fallos.add(color + " texto");
			}
			if (ReglaContraste.contraste(tema.sobrePrimario2(), tema.primario()) < 4.5) {
				fallos.add(color + " secundario");
			}
			if (ReglaContraste.contraste(ReglaContraste.TINTA, tema.primarioSuave()) < 4.5) {
				fallos.add(color + " suave");
			}
			if (!tema.ajustado() && !tema.primario().equals(color)) {
				fallos.add(color + " cambiado sin ajustar");
			}
			for (String valor : List.of(tema.primario(), tema.sobrePrimario(), tema.sobrePrimario2(),
					tema.primarioTinta(), tema.primarioSuave())) {
				if (!valor.matches(HEX)) {
					fallos.add(color + " formato " + valor);
				}
			}
		}
		assertEquals(List.of(), fallos);
	}

	@Test
	void rejilla_tiene4096Colores() {
		assertEquals(4096, rejilla().size());
		assertEquals(4096, rejilla().stream().distinct().count());
	}

	// ------------------------------------------------------------------ formato

	@ParameterizedTest
	@ValueSource(strings = { "#12345", "123456", "#GGGGGG", " #123456", "#1234567", "", "#12 456", "rojo",
			"#123456 " })
	void formatoInvalido(String valor) {
		assertFalse(ReglaContraste.esColorValido(valor));
		assertThrows(IllegalArgumentException.class, () -> ReglaContraste.normalizar(valor));
	}

	@Test
	void normalizar_aMayusculas() {
		assertEquals("#ABCDEF", ReglaContraste.normalizar("#abcdef"));
		assertTrue(ReglaContraste.esColorValido("#a1B2c3"));
		assertFalse(ReglaContraste.esColorValido(null));
	}

	@Test
	void contraste_extremos() {
		assertEquals(21.0, ReglaContraste.contraste("#000000", "#FFFFFF"), 1e-9);
		assertEquals(1.0, ReglaContraste.contraste("#777777", "#777777"), 1e-9);
	}

	private static List<String> rejilla() {
		List<String> colores = new ArrayList<>(4096);
		for (int r = 0; r < 16; r++) {
			for (int g = 0; g < 16; g++) {
				for (int b = 0; b < 16; b++) {
					colores.add(String.format("#%02X%02X%02X", r * 17, g * 17, b * 17));
				}
			}
		}
		return colores;
	}
}
