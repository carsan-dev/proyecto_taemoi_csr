package com.taemoi.project.caracterizacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.taemoi.project.legado.Deporte;
import com.taemoi.project.legado.TipoCategoria;
import com.taemoi.project.legado.TipoGrado;
import com.taemoi.project.legado.TipoTarifa;
import com.taemoi.project.services.CatalogoLegadoTaemoi;
import com.taemoi.project.services.CatalogoLegadoTaemoi.CategoriaLegado;

/**
 * Compara, en los dos sentidos (nada de menos, nada de más), los literales de {@link CatalogoLegadoTaemoi}
 * con el oráculo congelado ({@code com.taemoi.project.legado}, copia de los enums borrados en V28).
 *
 * <p>Sin contrapartida en el oráculo (solo los fija {@code ResyncCatalogoV24Test} contra el SQL de V24):
 * los conceptos MENSUALIDAD, RESERVA_PLAZA, LICENCIA_*, COMPETIDOR_* y los dos *_ROJO_BORDADO (el código
 * anterior los tenía como literales sueltos en los servicios, no en ningún enum), la constante
 * {@code GRADO_ROJO} y la lógica de {@code conceptoBase}/{@code tipoDeCatalogo}.
 */
class CatalogoLegadoTaemoiOraculoTest {

	@Test
	void disciplinas_sonLasDelEnumDeporte_enSuOrden() {
		assertEquals(Arrays.stream(Deporte.values()).map(Deporte::name).toList(), CatalogoLegadoTaemoi.DISCIPLINAS);
		assertEquals(Deporte.TAEKWONDO.name(), CatalogoLegadoTaemoi.TAEKWONDO);
		assertEquals(Deporte.KICKBOXING.name(), CatalogoLegadoTaemoi.KICKBOXING);
		assertEquals(Deporte.PILATES.name(), CatalogoLegadoTaemoi.PILATES);
		assertEquals(Deporte.DEFENSA_PERSONAL_FEMENINA.name(), CatalogoLegadoTaemoi.DEFENSA_PERSONAL_FEMENINA);
	}

	@Test
	void codigosYNombresDeGrado_sonLosDeTipoGrado_enSuOrden() {
		assertEquals(Arrays.stream(TipoGrado.values()).map(TipoGrado::name).toList(),
				CatalogoLegadoTaemoi.codigosGrado());
		for (TipoGrado grado : TipoGrado.values()) {
			assertEquals(grado.getNombre(), CatalogoLegadoTaemoi.nombreGradoLegado(grado.name()), grado.name());
		}
		assertEquals(TipoGrado.ROJO.name(), CatalogoLegadoTaemoi.GRADO_ROJO);
	}

	@Test
	void nombreGradoLegado_codigoAjenoONulo_esNulo() {
		assertNull(CatalogoLegadoTaemoi.nombreGradoLegado("MARRON"));
		assertNull(CatalogoLegadoTaemoi.nombreGradoLegado(null));
	}

	@Test
	void productosDeGrado_coincidenConTipoGrado_incluidoLoQueNoTiene() {
		for (TipoGrado grado : TipoGrado.values()) {
			assertEquals(grado.tieneProductoExamen() ? grado.obtenerNombreProducto(false) : null,
					CatalogoLegadoTaemoi.productoGradoLegado(grado.name(), false), "examen " + grado.name());
			assertEquals(grado.tieneProductoRecompensa() ? grado.obtenerNombreProducto(true) : null,
					CatalogoLegadoTaemoi.productoGradoLegado(grado.name(), true), "recompensa " + grado.name());
		}
		assertNull(CatalogoLegadoTaemoi.productoGradoLegado("MARRON", false));
		assertNull(CatalogoLegadoTaemoi.productoGradoLegado(null, true));
	}

	@Test
	void conceptosDeExamenYRecompensa_sonExactamenteLosDeTipoGrado_salvoElRojoBordado() {
		Map<String, String> esperados = new LinkedHashMap<>();
		for (TipoGrado grado : TipoGrado.values()) {
			if (grado.tieneProductoExamen()) {
				esperados.put("EXAMEN_" + grado.name(), grado.obtenerNombreProducto(false));
			}
			if (grado.tieneProductoRecompensa()) {
				esperados.put("RECOMPENSA_" + grado.name(), grado.obtenerNombreProducto(true));
			}
		}
		Map<String, String> reales = new LinkedHashMap<>();
		CatalogoLegadoTaemoi.conceptosPorCodigo().forEach((codigo, concepto) -> {
			if (codigo.startsWith("EXAMEN_") || codigo.startsWith("RECOMPENSA_")) {
				reales.put(codigo, concepto);
			}
		});
		// Rojo bordado no es un TipoGrado: no tiene contrapartida en el oráculo.
		assertEquals("DERECHO A EXAMEN ROJO BORDADO", reales.remove("EXAMEN_ROJO_BORDADO"));
		assertEquals("DERECHO DE CAMBIO A ROJO BORDADO POR RECOMPENSA", reales.remove("RECOMPENSA_ROJO_BORDADO"));
		assertEquals(esperados, reales);
	}

	@Test
	void categoriasTaekwondo_sonLasDeTipoCategoria() {
		TipoCategoria[] oraculo = TipoCategoria.values();
		List<CategoriaLegado> reales = CatalogoLegadoTaemoi.CATEGORIAS_TAEKWONDO;
		assertEquals(oraculo.length, reales.size());
		for (int i = 0; i < oraculo.length; i++) {
			TipoCategoria esperada = oraculo[i];
			CategoriaLegado real = reales.get(i);
			assertEquals(esperada.getNombre(), real.nombre());
			// Extremo abierto: Integer.MAX_VALUE del enum = null aquí; y Infantil (mínimo 8 en el enum)
			// es abierta por abajo (V28 le da edad_min NULL).
			Integer maxEsperado = esperada.getEdadMaxima() == Integer.MAX_VALUE ? null : esperada.getEdadMaxima();
			assertEquals(maxEsperado, real.edadMax(), esperada.name());
			Integer minEsperado = esperada == TipoCategoria.INFANTIL ? null : esperada.getEdadMinima();
			assertEquals(minEsperado, real.edadMin(), esperada.name());
		}
	}

	@Test
	void valorNoValido_reproduceElMensajeDeEnumValueOf() {
		Map<String, Function<String, ?>> enums = new LinkedHashMap<>();
		enums.put("Deporte", Deporte::valueOf);
		enums.put("TipoGrado", TipoGrado::valueOf);
		enums.put("TipoTarifa", TipoTarifa::valueOf);
		for (Map.Entry<String, Function<String, ?>> e : enums.entrySet()) {
			for (String valor : List.of("INEXISTENTE", "", "taekwondo", "Blanco")) {
				IllegalArgumentException esperada = assertThrows(IllegalArgumentException.class,
						() -> e.getValue().apply(valor));
				// El oráculo vive en otro paquete: el mensaje original llevaba el de las entidades borradas.
				String mensajeOriginal = esperada.getMessage().replace("com.taemoi.project.legado.",
						"com.taemoi.project.entities.");
				assertEquals(mensajeOriginal, CatalogoLegadoTaemoi.valorNoValido(e.getKey(), valor).getMessage(),
						e.getKey() + "/" + valor);
			}
			NullPointerException esperadaNula = assertThrows(NullPointerException.class,
					() -> e.getValue().apply(null));
			NullPointerException real = assertThrows(NullPointerException.class,
					() -> CatalogoLegadoTaemoi.valorNoValido(e.getKey(), null));
			assertEquals(esperadaNula.getMessage(), real.getMessage(), e.getKey());
		}
	}
}
