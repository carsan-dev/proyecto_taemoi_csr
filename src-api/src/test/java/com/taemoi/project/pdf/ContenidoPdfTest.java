package com.taemoi.project.pdf;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** La regla de comparación de {@link ContenidoPdf} sobre líneas sintéticas (sin PDF). */
class ContenidoPdfTest {

	private static final List<String> DORADO = List.of(
			"# comentario",
			"~ MOI'S KIM DO TAEKWONDO",
			"~ jueves, 15 de octubre de 2026 Página 1 de 2",
			"Nombre y Apellidos Nº Expediente",
			"Ana Prueba 101 28.00 €",
			"Bruno Prueba 102 30.00 €",
			"@campo pagador=ANA PRUEBA");

	@Test
	void mismoContenidoConOtroCasoEspaciosYCortesDeLinea_pasa() {
		assertDoesNotThrow(() -> ContenidoPdf.comparar("prueba", DORADO,
				List.of("Página 1 / 2 · 15/10/2026", "Moi's Kim Do Taekwondo", "NOMBRE Y APELLIDOS", "Nº EXPEDIENTE",
						"Ana Prueba 101", "28.00 €", "Bruno Prueba 102 30.00 €"),
				Map.of("pagador", "ANA  PRUEBA")));
	}

	@Test
	void faltaUnDato_falla() {
		AssertionError error = assertThrows(AssertionError.class, () -> ContenidoPdf.comparar("prueba", DORADO,
				List.of("MOI'S KIM DO TAEKWONDO 15/10/2026 1 de 2", "Nombre y Apellidos Nº Expediente",
						"Ana Prueba 101 28.00 €"),
				Map.of("pagador", "ANA PRUEBA")));
		assertTrue(error.getMessage().contains("«Bruno Prueba 102 30.00 €»: no aparece"), error.getMessage());
	}

	@Test
	void datosFueraDeOrden_falla() {
		AssertionError error = assertThrows(AssertionError.class, () -> ContenidoPdf.comparar("prueba", DORADO,
				List.of("Bruno Prueba 102 30.00 €", "MOI'S KIM DO TAEKWONDO 15/10/2026 1 de 2",
						"Nombre y Apellidos Nº Expediente", "Ana Prueba 101 28.00 €"),
				Map.of("pagador", "ANA PRUEBA")));
		assertTrue(error.getMessage().contains("«Bruno Prueba 102 30.00 €»: fuera de orden"), error.getMessage());
	}

	/** Celdas partidas en varias líneas (orden por posición) que el rediseño deja en una: pasa. */
	@Test
	void celdasPartidasDeOtraForma_pasa() {
		List<String> dorado = List.of("NOMBRE Y GRADO A", "Nº EXPEDIENTE GRADO ACTUAL EDAD", "APELLIDOS PROMOCIONAR",
				"Ana Prueba Uno 101 AMARILLO- 11", "AMARILLO", "NARANJA", "Carla Prueba Tres 103 BLANCO- 13",
				"AMARILLO", "AMARILLO");
		assertDoesNotThrow(() -> ContenidoPdf.comparar("prueba", dorado,
				List.of("Nombre y apellidos Nº expediente Grado actual Grado a promocionar Edad",
						"Ana Prueba Uno 101 AMARILLO AMARILLO-NARANJA 11", "Carla Prueba", "Tres 103 BLANCO-AMARILLO",
						"AMARILLO 13"),
				Map.of()));
	}

	/** Un dato repetido en el dorado debe seguir repetido. */
	@Test
	void datoRepetidoQueQuedaUnaVez_falla() {
		assertThrows(AssertionError.class, () -> ContenidoPdf.comparar("prueba", List.of("Bruno 102 30.00 € 30.00 €"),
				List.of("Bruno 102 30.00 €"), Map.of()));
	}

	@Test
	void presentacionSinLaFechaLaPaginaOElNombreDeLaEscuela_falla() {
		List<String> datos = List.of("Nombre y Apellidos Nº Expediente", "Ana Prueba 101 28.00 €",
				"Bruno Prueba 102 30.00 €");
		Map<String, String> campos = Map.of("pagador", "ANA PRUEBA");
		assertTrue(assertThrows(AssertionError.class, () -> ContenidoPdf.comparar("prueba", DORADO,
				concatenar(List.of("MOI'S KIM DO TAEKWONDO 1 de 2"), datos), campos)).getMessage()
				.contains("la fecha 2026-10-15"));
		assertTrue(assertThrows(AssertionError.class, () -> ContenidoPdf.comparar("prueba", DORADO,
				concatenar(List.of("MOI'S KIM DO TAEKWONDO 15/10/2026"), datos), campos)).getMessage()
				.contains("la página 1 de 2"));
		assertTrue(assertThrows(AssertionError.class, () -> ContenidoPdf.comparar("prueba", DORADO,
				concatenar(List.of("TAEKWONDO 15/10/2026 1 de 2"), datos), campos)).getMessage().contains("«moi»"));
	}

	@Test
	void campoDelFormularioPerdidoOCambiado_falla() {
		List<String> lineas = List.of("MOI'S KIM DO TAEKWONDO 15/10/2026 1 de 2", "Nombre y Apellidos Nº Expediente",
				"Ana Prueba 101 28.00 €", "Bruno Prueba 102 30.00 €");
		assertTrue(assertThrows(AssertionError.class, () -> ContenidoPdf.comparar("prueba", DORADO, lineas, Map.of()))
				.getMessage().contains("falta el campo del formulario «pagador»"));
		assertTrue(assertThrows(AssertionError.class,
				() -> ContenidoPdf.comparar("prueba", DORADO, lineas, Map.of("pagador", "OTRA"))).getMessage()
				.contains("vale «OTRA»"));
	}

	private static List<String> concatenar(List<String> a, List<String> b) {
		return java.util.stream.Stream.concat(a.stream(), b.stream()).toList();
	}
}
