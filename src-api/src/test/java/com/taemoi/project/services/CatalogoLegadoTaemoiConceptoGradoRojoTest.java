package com.taemoi.project.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.taemoi.project.entities.Producto;

/**
 * Concepto de los cargos de examen y recompensa a ROJO con el nombre del grado ROJO de la disciplina: en kickboxing ese
 * cinturón es marrón («DERECHO A EXAMEN MARRÓN BORDADO»); en taekwondo no cambia. Solo los cuatro códigos heredados de
 * ROJO y solo con comportamiento heredado.
 */
class CatalogoLegadoTaemoiConceptoGradoRojoTest {

	@Test
	void losCuatroCodigosDeRojo_conElNombreDelGradoRojoDeLaDisciplina() {
		assertEquals("DERECHO A EXAMEN MARRÓN", concepto("EXAMEN_ROJO", "MARRÓN"));
		assertEquals("DERECHO A EXAMEN MARRÓN BORDADO", concepto("EXAMEN_ROJO_BORDADO", "MARRÓN"));
		assertEquals("DERECHO DE CAMBIO A MARRÓN POR RECOMPENSA", concepto("RECOMPENSA_ROJO", "MARRÓN"));
		assertEquals("DERECHO DE CAMBIO A MARRÓN BORDADO POR RECOMPENSA", concepto("RECOMPENSA_ROJO_BORDADO", "MARRÓN"));
		// El nombre del catálogo, en mayúsculas.
		assertEquals("DERECHO A EXAMEN MARRÓN BORDADO", concepto("EXAMEN_ROJO_BORDADO", " Marrón "));
	}

	@Test
	void taekwondo_sinNombre_oEnBlanco_comoSiempre() {
		assertEquals("DERECHO A EXAMEN ROJO", concepto("EXAMEN_ROJO", "ROJO"));
		assertEquals("DERECHO A EXAMEN ROJO BORDADO", concepto("EXAMEN_ROJO_BORDADO", "ROJO"));
		assertEquals("DERECHO DE CAMBIO A ROJO POR RECOMPENSA", concepto("RECOMPENSA_ROJO", null));
		assertEquals("DERECHO DE CAMBIO A ROJO BORDADO POR RECOMPENSA", concepto("RECOMPENSA_ROJO_BORDADO", " "));
	}

	@Test
	void otrosCodigos_noCambian_aunqueDiganRojo() {
		assertEquals("DERECHO A EXAMEN AZUL/ROJO", concepto("EXAMEN_AZUL_ROJO", "MARRÓN"));
		assertEquals("DERECHO A EXAMEN ROJO/NEGRO 1º PUM", concepto("EXAMEN_ROJO_NEGRO_1_PUM", "MARRÓN"));
		assertEquals("DERECHO DE CAMBIO A AZUL-ROJO POR RECOMPENSA", concepto("RECOMPENSA_AZUL_ROJO", "MARRÓN"));
		assertFalse(CatalogoLegadoTaemoi.conceptoNombraGradoRojo(producto("EXAMEN_AZUL_ROJO", null), true));
		assertTrue(CatalogoLegadoTaemoi.conceptoNombraGradoRojo(producto("EXAMEN_ROJO_BORDADO", null), true));
	}

	/** Sin comportamiento heredado el concepto es el nombre del producto, también para esos códigos: no se toca. */
	@Test
	void sinComportamientoHeredado_elNombreDelProducto_sinCambios() {
		Producto propio = producto("EXAMEN_ROJO_BORDADO", "Examen rojo bordado");
		assertFalse(CatalogoLegadoTaemoi.conceptoNombraGradoRojo(propio, false));
		assertEquals("Examen rojo bordado", CatalogoLegadoTaemoi.conceptoBase(propio, null, false, "MARRÓN"));
		assertNull(CatalogoLegadoTaemoi.conceptoBase(null, null, true, "MARRÓN"));
		assertFalse(CatalogoLegadoTaemoi.conceptoNombraGradoRojo(null, true));
	}

	/**
	 * Producto heredado SIN código que el código anterior elegía por concepto (sin distinguir mayúsculas, acentos ni
	 * espacios): en kickboxing también dice MARRÓN; en taekwondo, su nombre tal cual. Otros nombres no cambian.
	 */
	@Test
	void heredadoSinCodigo_conElConceptoDeRojo_tambienConElNombreDeLaDisciplina() {
		assertEquals("DERECHO A EXAMEN MARRÓN", sinCodigo("DERECHO A EXAMEN ROJO", "MARRÓN"));
		assertEquals("DERECHO A EXAMEN MARRÓN BORDADO", sinCodigo("DERECHO A EXAMEN ROJO BORDADO", "MARRÓN"));
		assertEquals("DERECHO DE CAMBIO A MARRÓN POR RECOMPENSA", sinCodigo("DERECHO DE CAMBIO A ROJO POR RECOMPENSA", "MARRÓN"));
		assertEquals("DERECHO DE CAMBIO A MARRÓN BORDADO POR RECOMPENSA",
				sinCodigo("DERECHO DE CAMBIO A ROJO BORDADO POR RECOMPENSA", "MARRÓN"));
		assertEquals("Derecho a examen MARRÓN bordado", sinCodigo("Derecho a examen rojo bordado", "MARRÓN"));
		// Taekwondo: tal cual, también con otras mayúsculas.
		assertEquals("DERECHO A EXAMEN ROJO BORDADO", sinCodigo("DERECHO A EXAMEN ROJO BORDADO", "ROJO"));
		assertEquals("Derecho a examen rojo bordado", sinCodigo("Derecho a examen rojo bordado", "Rojo"));
		// Otros productos sin código no cambian.
		assertEquals("DERECHO A EXAMEN AZUL/ROJO", sinCodigo("DERECHO A EXAMEN AZUL/ROJO", "MARRÓN"));
		assertEquals("DERECHO A EXAMEN ROJO BORDADO (ESPECIAL)", sinCodigo("DERECHO A EXAMEN ROJO BORDADO (ESPECIAL)", "MARRÓN"));
		// Sin comportamiento heredado, tampoco.
		Producto sinCodigo = producto(null, "DERECHO A EXAMEN ROJO BORDADO");
		assertFalse(CatalogoLegadoTaemoi.conceptoNombraGradoRojo(sinCodigo, false));
		assertEquals("DERECHO A EXAMEN ROJO BORDADO", CatalogoLegadoTaemoi.conceptoBase(sinCodigo, null, false, "MARRÓN"));
	}

	@Test
	void conceptosConGradoRojo_examenYRecompensa_normalYBordado() {
		assertEquals(java.util.List.of("DERECHO A EXAMEN ROJO", "DERECHO A EXAMEN ROJO BORDADO"),
				CatalogoLegadoTaemoi.conceptosConGradoRojo(false));
		assertEquals(java.util.List.of("DERECHO DE CAMBIO A ROJO POR RECOMPENSA",
				"DERECHO DE CAMBIO A ROJO BORDADO POR RECOMPENSA"), CatalogoLegadoTaemoi.conceptosConGradoRojo(true));
	}

	private static String sinCodigo(String nombre, String nombreGradoRojo) {
		return CatalogoLegadoTaemoi.conceptoBase(producto(null, nombre), null, true, nombreGradoRojo);
	}

	private static String concepto(String codigo, String nombreGradoRojo) {
		return CatalogoLegadoTaemoi.conceptoBase(producto(codigo, "otro nombre"), null, true, nombreGradoRojo);
	}

	private static Producto producto(String codigo, String concepto) {
		Producto p = new Producto();
		p.setCodigo(codigo);
		p.setConcepto(concepto);
		return p;
	}
}
