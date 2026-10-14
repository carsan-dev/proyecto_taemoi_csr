package com.taemoi.project.services;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.taemoi.project.entities.Producto;
import com.taemoi.project.entities.TipoProducto;

/**
 * Códigos de producto de Taemoi clasificados por V24 ({@code db/resync/resync_catalogo_v24.sql}) con el
 * concepto EXACTO que el código buscaba y escribía hasta V27.
 *
 * <p>Para estos códigos se conserva el comportamiento anterior a V27, aunque el producto se renombre:
 * <ul>
 * <li>el concepto de los cargos (mensualidad, competidor, licencia, examen) es el literal heredado, no el
 * nombre editable del producto (si no, renombrarlo duplicaría los cargos del mes, que se comprueban por
 * concepto);</li>
 * <li>tesorería y el derecho a examen clasifican sus cargos por el concepto, como antes; el tipo del
 * catálogo solo se usa para productos con códigos propios (otras escuelas).</li>
 * </ul>
 * Se podrá pasar al comportamiento del catálogo si el dueño lo decide.
 */
public final class CatalogoLegadoTaemoi {

	private static final Map<String, String> CONCEPTO_POR_CODIGO;

	static {
		Map<String, String> m = new LinkedHashMap<>();
		m.put("MENSUALIDAD", "MENSUALIDAD");
		m.put("RESERVA_PLAZA", "RESERVA DE PLAZA");
		m.put("LICENCIA_FEDERATIVA", "LICENCIA FEDERATIVA");
		m.put("LICENCIA_TAEKWONDO_ADULTO", "LICENCIA FEDERATIVA TAEKWONDO ADULTO");
		m.put("LICENCIA_TAEKWONDO_INFANTIL", "LICENCIA FEDERATIVA TAEKWONDO INFANTIL");
		m.put("LICENCIA_TAEKWONDO_DISCAPACIDAD", "LICENCIA FEDERATIVA TAEKWONDO DISCAPACIDAD");
		m.put("LICENCIA_TAEKWONDO_ADULTO_PROPORCIONAL", "PARTE PROPORCIONAL LICENCIA FEDERATIVA TAEKWONDO ADULTO");
		m.put("LICENCIA_TAEKWONDO_INFANTIL_PROPORCIONAL", "PARTE PROPORCIONAL LICENCIA FEDERATIVA TAEKWONDO INFANTIL");
		m.put("LICENCIA_TAEKWONDO_DISCAPACIDAD_PROPORCIONAL", "PARTE PROPORCIONAL LICENCIA FEDERATIVA TAEKWONDO DISCAPACIDAD");
		m.put("LICENCIA_KICKBOXING_ADULTO", "LICENCIA FEDERATIVA KICKBOXING ADULTO");
		m.put("LICENCIA_KICKBOXING_INFANTIL", "LICENCIA FEDERATIVA KICKBOXING INFANTIL");
		m.put("COMPETIDOR_TAEKWONDO", "TARIFA COMPETIDOR TAEKWONDO");
		m.put("COMPETIDOR_KICKBOXING", "TARIFA COMPETIDOR KICKBOXING");
		m.put("EXAMEN_BLANCO_AMARILLO", "DERECHO A EXAMEN BLANCO/AMARILLO");
		m.put("EXAMEN_AMARILLO", "DERECHO A EXAMEN AMARILLO");
		m.put("EXAMEN_AMARILLO_NARANJA", "DERECHO A EXAMEN AMARILLO/NARANJA");
		m.put("EXAMEN_NARANJA", "DERECHO A EXAMEN NARANJA");
		m.put("EXAMEN_NARANJA_VERDE", "DERECHO A EXAMEN NARANJA/VERDE");
		m.put("EXAMEN_VERDE", "DERECHO A EXAMEN VERDE");
		m.put("EXAMEN_VERDE_AZUL", "DERECHO A EXAMEN VERDE/AZUL");
		m.put("EXAMEN_AZUL", "DERECHO A EXAMEN AZUL");
		m.put("EXAMEN_AZUL_ROJO", "DERECHO A EXAMEN AZUL/ROJO");
		m.put("EXAMEN_ROJO", "DERECHO A EXAMEN ROJO");
		m.put("EXAMEN_ROJO_NEGRO_1_PUM", "DERECHO A EXAMEN ROJO/NEGRO 1º PUM");
		m.put("EXAMEN_ROJO_NEGRO_2_PUM", "DERECHO A EXAMEN ROJO/NEGRO 2º PUM");
		m.put("EXAMEN_NEGRO_1_DAN", "DERECHO A EXAMEN NEGRO 1º DAN");
		m.put("EXAMEN_NEGRO_2_DAN", "DERECHO A EXAMEN NEGRO 2º DAN");
		m.put("EXAMEN_NEGRO_3_DAN", "DERECHO A EXAMEN NEGRO 3º DAN");
		m.put("EXAMEN_ROJO_BORDADO", "DERECHO A EXAMEN ROJO BORDADO");
		m.put("RECOMPENSA_BLANCO_AMARILLO", "DERECHO CAMBIO A BLANCO-AMARILLO RECOMPENSA");
		m.put("RECOMPENSA_AMARILLO", "DERECHO DE CAMBIO A AMARILLO POR RECOMPENSA");
		m.put("RECOMPENSA_AMARILLO_NARANJA", "DERECHO DE CAMBIO A AMARILLO-NARANJA POR RECOMPENSA");
		m.put("RECOMPENSA_NARANJA", "DERECHO DE CAMBIO A NARANJA POR RECOMPENSA");
		m.put("RECOMPENSA_NARANJA_VERDE", "DERECHO DE CAMBIO A NARANJA-VERDE POR RECOMPENSA");
		m.put("RECOMPENSA_VERDE", "DERECHO DE CAMBIO A VERDE RECOMPENSA");
		m.put("RECOMPENSA_VERDE_AZUL", "DERECHO DE CAMBIO A VERDE-AZUL POR RECOMPENSA");
		m.put("RECOMPENSA_AZUL", "DERECHO DE CAMBIO A AZUL POR RECOMPENSA");
		m.put("RECOMPENSA_AZUL_ROJO", "DERECHO DE CAMBIO A AZUL-ROJO POR RECOMPENSA");
		m.put("RECOMPENSA_ROJO", "DERECHO DE CAMBIO A ROJO POR RECOMPENSA");
		m.put("RECOMPENSA_ROJO_BORDADO", "DERECHO DE CAMBIO A ROJO BORDADO POR RECOMPENSA");
		CONCEPTO_POR_CODIGO = Collections.unmodifiableMap(m);
	}

	private CatalogoLegadoTaemoi() {
	}

	/** Si el código es uno de los clasificados por V24 (nombres heredados de Taemoi). */
	public static boolean esCodigoLegado(String codigo) {
		return codigo != null && CONCEPTO_POR_CODIGO.containsKey(codigo);
	}

	/** Concepto heredado del código (null si no es un código heredado). */
	public static String conceptoLegado(String codigo) {
		return codigo == null ? null : CONCEPTO_POR_CODIGO.get(codigo);
	}

	/** Códigos heredados con su concepto, en el orden de V24. */
	public static Map<String, String> conceptosPorCodigo() {
		return CONCEPTO_POR_CODIGO;
	}

	/**
	 * Nombre con el que se construye el concepto de un cargo del producto: el literal heredado para los
	 * códigos de V24; el nombre del producto para los códigos propios; para un producto sin clasificar,
	 * {@code legadoSinCodigo} (el nombre que usaba el código anterior) o, si es null, su nombre.
	 */
	public static String conceptoBase(Producto producto, String legadoSinCodigo) {
		if (producto == null) {
			return legadoSinCodigo;
		}
		String codigo = producto.getCodigo();
		if (esCodigoLegado(codigo)) {
			return CONCEPTO_POR_CODIGO.get(codigo);
		}
		if (codigo != null && producto.getConcepto() != null && !producto.getConcepto().isBlank()) {
			return producto.getConcepto().trim();
		}
		return legadoSinCodigo != null ? legadoSinCodigo : producto.getConcepto();
	}

	/**
	 * Tipo del catálogo que puede decidir por encima del concepto: el del producto con un código PROPIO
	 * (no heredado); null para productos de códigos heredados, sin clasificar o sin producto.
	 */
	public static TipoProducto tipoDeCatalogo(Producto producto) {
		return producto == null ? null : tipoDeCatalogo(producto.getCodigo(), producto.getTipo());
	}

	public static TipoProducto tipoDeCatalogo(String codigo, TipoProducto tipo) {
		if (codigo == null || esCodigoLegado(codigo)) {
			return null;
		}
		return tipo;
	}
}
