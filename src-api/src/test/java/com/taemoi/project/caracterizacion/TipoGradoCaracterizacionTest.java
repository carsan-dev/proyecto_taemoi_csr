package com.taemoi.project.caracterizacion;

import static com.taemoi.project.entities.TipoGrado.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.taemoi.project.entities.TipoGrado;

/**
 * Caracterizacion del enum TipoGrado: orden, nombres, productos y busquedas.
 */
class TipoGradoCaracterizacionTest {

	/** {grado, nombre, productoExamen, productoRecompensa} en orden de declaracion. */
	private static final Object[][] TABLA = {
			{ BLANCO, "Blanco", null, null },
			{ BLANCO_AMARILLO, "Blanco-Amarillo", "DERECHO A EXAMEN BLANCO/AMARILLO", "DERECHO CAMBIO A BLANCO-AMARILLO RECOMPENSA" },
			{ AMARILLO, "Amarillo", "DERECHO A EXAMEN AMARILLO", "DERECHO DE CAMBIO A AMARILLO POR RECOMPENSA" },
			{ AMARILLO_NARANJA, "Amarillo-Naranja", "DERECHO A EXAMEN AMARILLO/NARANJA", "DERECHO DE CAMBIO A AMARILLO-NARANJA POR RECOMPENSA" },
			{ NARANJA, "Naranja", "DERECHO A EXAMEN NARANJA", "DERECHO DE CAMBIO A NARANJA POR RECOMPENSA" },
			{ NARANJA_VERDE, "Naranja-Verde", "DERECHO A EXAMEN NARANJA/VERDE", "DERECHO DE CAMBIO A NARANJA-VERDE POR RECOMPENSA" },
			{ VERDE, "Verde", "DERECHO A EXAMEN VERDE", "DERECHO DE CAMBIO A VERDE RECOMPENSA" },
			{ VERDE_AZUL, "Verde-Azul", "DERECHO A EXAMEN VERDE/AZUL", "DERECHO DE CAMBIO A VERDE-AZUL POR RECOMPENSA" },
			{ AZUL, "Azul", "DERECHO A EXAMEN AZUL", "DERECHO DE CAMBIO A AZUL POR RECOMPENSA" },
			{ AZUL_ROJO, "Azul-Rojo", "DERECHO A EXAMEN AZUL/ROJO", "DERECHO DE CAMBIO A AZUL-ROJO POR RECOMPENSA" },
			{ ROJO, "Rojo", "DERECHO A EXAMEN ROJO", "DERECHO DE CAMBIO A ROJO POR RECOMPENSA" },
			{ ROJO_NEGRO_1_PUM, "Rojo-Negro-1-Pum", "DERECHO A EXAMEN ROJO/NEGRO 1º PUM", null },
			{ ROJO_NEGRO_2_PUM, "Rojo-Negro-2-Pum", "DERECHO A EXAMEN ROJO/NEGRO 2º PUM", null },
			{ ROJO_NEGRO_3_PUM, "Rojo-Negro-3-Pum", null, null },
			{ NEGRO_1_DAN, "Negro-1-Dan", "DERECHO A EXAMEN NEGRO 1º DAN", null },
			{ NEGRO_2_DAN, "Negro-2-Dan", "DERECHO A EXAMEN NEGRO 2º DAN", null },
			{ NEGRO_3_DAN, "Negro-3-Dan", "DERECHO A EXAMEN NEGRO 3º DAN", null },
			{ NEGRO_4_DAN, "Negro-4-Dan", null, null },
			{ NEGRO_5_DAN, "Negro-5-Dan", null, null },
	};

	@Test
	void ordenDeDeclaracion() {
		assertEquals(List.of(BLANCO, BLANCO_AMARILLO, AMARILLO, AMARILLO_NARANJA, NARANJA, NARANJA_VERDE, VERDE,
				VERDE_AZUL, AZUL, AZUL_ROJO, ROJO, ROJO_NEGRO_1_PUM, ROJO_NEGRO_2_PUM, ROJO_NEGRO_3_PUM,
				NEGRO_1_DAN, NEGRO_2_DAN, NEGRO_3_DAN, NEGRO_4_DAN, NEGRO_5_DAN), Arrays.asList(TipoGrado.values()));
	}

	@Test
	void nombreYProductos_porValor() {
		assertEquals(TipoGrado.values().length, TABLA.length);
		for (Object[] fila : TABLA) {
			TipoGrado g = (TipoGrado) fila[0];
			assertEquals(fila[1], g.getNombre(), g.name());
			assertEquals(fila[2] != null, g.tieneProductoExamen(), g.name());
			assertEquals(fila[3] != null, g.tieneProductoRecompensa(), g.name());
			if (fila[2] != null) {
				assertEquals(fila[2], g.obtenerNombreProducto(false), g.name());
			} else {
				assertThrows(IllegalArgumentException.class, () -> g.obtenerNombreProducto(false), g.name());
			}
			if (fila[3] != null) {
				assertEquals(fila[3], g.obtenerNombreProducto(true), g.name());
			} else {
				assertThrows(IllegalArgumentException.class, () -> g.obtenerNombreProducto(true), g.name());
			}
		}
	}

	@Test
	void siguiente_esElOrdinalSiguiente_inclusoEntrePumYDan() {
		assertEquals(BLANCO_AMARILLO, BLANCO.siguiente());
		assertEquals(ROJO_NEGRO_1_PUM, ROJO.siguiente());
		assertEquals(NEGRO_1_DAN, ROJO_NEGRO_3_PUM.siguiente());
		assertEquals(NEGRO_5_DAN, NEGRO_4_DAN.siguiente());
		assertNull(NEGRO_5_DAN.siguiente());
		TipoGrado[] v = TipoGrado.values();
		for (int i = 0; i < v.length - 1; i++) {
			assertEquals(v[i + 1], v[i].siguiente());
		}
	}

	@Test
	void fromNombre_insensibleAMayusculas() {
		for (Object[] fila : TABLA) {
			String nombre = (String) fila[1];
			assertEquals(fila[0], TipoGrado.fromNombre(nombre));
			assertEquals(fila[0], TipoGrado.fromNombre(nombre.toUpperCase()));
			assertEquals(fila[0], TipoGrado.fromNombre(nombre.toLowerCase()));
		}
		// El nombre del enum (con guion bajo) no sirve
		assertThrows(IllegalArgumentException.class, () -> TipoGrado.fromNombre("BLANCO_AMARILLO"));
		assertThrows(IllegalArgumentException.class, () -> TipoGrado.fromNombre("Marron"));
		assertThrows(IllegalArgumentException.class, () -> TipoGrado.fromNombre(null));
	}

	/**
	 * fromProductoExamen usa "contains" y recorre en orden de declaracion: los productos
	 * de grados intermedios resuelven al grado de color anterior (comportamiento actual).
	 */
	@Test
	void fromProductoExamen_tablaActual() {
		Object[][] casos = {
				{ "DERECHO A EXAMEN BLANCO/AMARILLO", BLANCO_AMARILLO },
				{ "DERECHO A EXAMEN AMARILLO", AMARILLO },
				{ "DERECHO A EXAMEN AMARILLO/NARANJA", AMARILLO },
				{ "DERECHO A EXAMEN NARANJA", NARANJA },
				{ "DERECHO A EXAMEN NARANJA/VERDE", NARANJA },
				{ "DERECHO A EXAMEN VERDE", VERDE },
				{ "DERECHO A EXAMEN VERDE/AZUL", VERDE },
				{ "DERECHO A EXAMEN AZUL", AZUL },
				{ "DERECHO A EXAMEN AZUL/ROJO", AZUL },
				{ "DERECHO A EXAMEN ROJO", ROJO },
				{ "DERECHO A EXAMEN ROJO BORDADO", ROJO },
				{ "DERECHO A EXAMEN ROJO/NEGRO 1º PUM", ROJO },
				{ "DERECHO A EXAMEN ROJO/NEGRO 2º PUM", ROJO },
				{ "DERECHO A EXAMEN NEGRO 1º DAN", NEGRO_1_DAN },
				{ "DERECHO A EXAMEN NEGRO 2º DAN", NEGRO_2_DAN },
				{ "DERECHO A EXAMEN NEGRO 3º DAN", NEGRO_3_DAN },
				{ "derecho a examen azul", AZUL },
				{ "DERECHO A EXAMEN VERDE MARZO 2026", VERDE },
				{ "MENSUALIDAD ENERO 2026", null },
				{ "PASE DE GRADO POR RECOMPENSA", null },
				{ "", null },
		};
		for (Object[] c : casos) {
			assertEquals(c[1], TipoGrado.fromProductoExamen((String) c[0]), (String) c[0]);
		}
		assertNull(TipoGrado.fromProductoExamen(null));
	}

	@Test
	void fromProductoRecompensa_tablaActual() {
		Object[][] casos = {
				{ "DERECHO CAMBIO A BLANCO-AMARILLO RECOMPENSA", BLANCO_AMARILLO },
				{ "DERECHO DE CAMBIO A AMARILLO POR RECOMPENSA", AMARILLO },
				{ "DERECHO DE CAMBIO A AMARILLO-NARANJA POR RECOMPENSA", AMARILLO_NARANJA },
				{ "DERECHO DE CAMBIO A NARANJA POR RECOMPENSA", NARANJA },
				{ "DERECHO DE CAMBIO A NARANJA-VERDE POR RECOMPENSA", NARANJA_VERDE },
				{ "DERECHO DE CAMBIO A VERDE RECOMPENSA", VERDE },
				{ "DERECHO DE CAMBIO A VERDE-AZUL POR RECOMPENSA", VERDE_AZUL },
				{ "DERECHO DE CAMBIO A AZUL POR RECOMPENSA", AZUL },
				{ "DERECHO DE CAMBIO A AZUL-ROJO POR RECOMPENSA", AZUL_ROJO },
				{ "DERECHO DE CAMBIO A ROJO POR RECOMPENSA", ROJO },
				// La variante bordada no contiene el literal de ROJO: no resuelve
				{ "DERECHO DE CAMBIO A ROJO BORDADO POR RECOMPENSA", null },
				// "VERDE POR RECOMPENSA" no coincide con el literal sin "POR"
				{ "DERECHO DE CAMBIO A VERDE POR RECOMPENSA", null },
				{ "derecho de cambio a azul por recompensa", AZUL },
				{ "PASE DE GRADO POR RECOMPENSA", null },
				{ "DERECHO A EXAMEN AZUL", null },
		};
		for (Object[] c : casos) {
			assertEquals(c[1], TipoGrado.fromProductoRecompensa((String) c[0]), (String) c[0]);
		}
		assertNull(TipoGrado.fromProductoRecompensa(null));
	}
}
