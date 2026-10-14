package com.taemoi.project.services;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
 *
 * <p>Desde V28 ese comportamiento se limita a las organizaciones con
 * {@code organizacion.comportamiento_legado_taemoi} (solo Taemoi): los métodos reciben {@code legado}
 * ({@link ComportamientoLegadoService#activo()}). Sin la marca, un producto con un código de V24 se trata como
 * cualquier código propio (concepto = nombre del producto, tipo del catálogo, precio del producto).
 *
 * <p>También guarda los literales que el código tomaba de los enums borrados en V28 ({@code Deporte},
 * {@code TipoGrado}): códigos de disciplina con identidad propia en Taemoi (informes, colores), los nombres
 * de los grados, los productos de examen y recompensa con los que se promociona por nombre contenido y el
 * texto de error de {@code Enum.valueOf} que la API devolvía con un valor no válido.
 */
public final class CatalogoLegadoTaemoi {

	// --- Disciplinas de Taemoi (antes enum Deporte) ------------------------------------------------

	public static final String TAEKWONDO = "TAEKWONDO";
	public static final String KICKBOXING = "KICKBOXING";
	public static final String PILATES = "PILATES";
	public static final String DEFENSA_PERSONAL_FEMENINA = "DEFENSA_PERSONAL_FEMENINA";

	/** Los cuatro códigos de disciplina de Taemoi en el orden del antiguo enum {@code Deporte}. */
	public static final List<String> DISCIPLINAS = List.of(TAEKWONDO, KICKBOXING, PILATES, DEFENSA_PERSONAL_FEMENINA);

	// --- Grados de Taemoi (antes enum TipoGrado) ---------------------------------------------------

	public static final String GRADO_ROJO = "ROJO";

	/**
	 * Código de grado -> nombre del antiguo {@code TipoGrado.getNombre()}, en el orden del enum (el del
	 * sistema de taekwondo). En kickboxing el catálogo llama «MARRÓN» al ROJO; estos son los nombres que el
	 * código mostraba sin distinguir disciplina.
	 */
	private static final Map<String, String> NOMBRE_GRADO;

	static {
		Map<String, String> g = new LinkedHashMap<>();
		g.put("BLANCO", "Blanco");
		g.put("BLANCO_AMARILLO", "Blanco-Amarillo");
		g.put("AMARILLO", "Amarillo");
		g.put("AMARILLO_NARANJA", "Amarillo-Naranja");
		g.put("NARANJA", "Naranja");
		g.put("NARANJA_VERDE", "Naranja-Verde");
		g.put("VERDE", "Verde");
		g.put("VERDE_AZUL", "Verde-Azul");
		g.put("AZUL", "Azul");
		g.put("AZUL_ROJO", "Azul-Rojo");
		g.put("ROJO", "Rojo");
		g.put("ROJO_NEGRO_1_PUM", "Rojo-Negro-1-Pum");
		g.put("ROJO_NEGRO_2_PUM", "Rojo-Negro-2-Pum");
		g.put("ROJO_NEGRO_3_PUM", "Rojo-Negro-3-Pum");
		g.put("NEGRO_1_DAN", "Negro-1-Dan");
		g.put("NEGRO_2_DAN", "Negro-2-Dan");
		g.put("NEGRO_3_DAN", "Negro-3-Dan");
		g.put("NEGRO_4_DAN", "Negro-4-Dan");
		g.put("NEGRO_5_DAN", "Negro-5-Dan");
		NOMBRE_GRADO = Collections.unmodifiableMap(g);
	}

	/** Códigos de grado de Taemoi en el orden del antiguo {@code TipoGrado}. */
	public static List<String> codigosGrado() {
		return List.copyOf(NOMBRE_GRADO.keySet());
	}

	/** Nombre del antiguo {@code TipoGrado.getNombre()}; null si el código no es de Taemoi. */
	public static String nombreGradoLegado(String codigoGrado) {
		return codigoGrado == null ? null : NOMBRE_GRADO.get(codigoGrado);
	}

	/**
	 * Concepto del antiguo {@code TipoGrado.obtenerNombreProducto}: derecho a examen ({@code recompensa}
	 * false) o cambio por recompensa. Null si el grado no tenía ese producto.
	 */
	public static String productoGradoLegado(String codigoGrado, boolean recompensa) {
		if (codigoGrado == null || !NOMBRE_GRADO.containsKey(codigoGrado)) {
			return null;
		}
		return CONCEPTO_POR_CODIGO.get((recompensa ? "RECOMPENSA_" : "EXAMEN_") + codigoGrado);
	}

	// --- Categorías de competición de Taemoi (antes enum TipoCategoria) -------------------------------

	/** Categoría de competición por edad (años cumplidos; extremos nulos = abiertos). */
	public record CategoriaLegado(String nombre, Integer edadMin, Integer edadMax) {
	}

	/**
	 * Las categorías que el código asignaba por edad a los competidores de taekwondo, en orden (los mismos
	 * rangos que V28 da a las categorías existentes con estos nombres).
	 */
	public static final List<CategoriaLegado> CATEGORIAS_TAEKWONDO = List.of(
			new CategoriaLegado("Infantil", null, 9),
			new CategoriaLegado("Precadete", 10, 11),
			new CategoriaLegado("Cadete", 12, 13),
			new CategoriaLegado("Junior", 14, 16),
			new CategoriaLegado("Senior", 17, null));

	// --- Mensajes de valores no válidos ------------------------------------------------------------

	/**
	 * Texto que devolvía {@code Enum.valueOf} con un valor no válido de los enums borrados ({@code Deporte},
	 * {@code TipoGrado}, {@code TipoTarifa}): la API lo reenviaba tal cual en algunas respuestas 400.
	 *
	 * @throws NullPointerException «Name is null» con un valor nulo, como {@code Enum.valueOf}.
	 */
	public static IllegalArgumentException valorNoValido(String enumHeredado, String valor) {
		Objects.requireNonNull(valor, "Name is null");
		return new IllegalArgumentException("No enum constant com.taemoi.project.entities." + enumHeredado + "." + valor);
	}

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

	/** {@link #esCodigoLegado(String)} solo en una organización con comportamiento heredado. */
	public static boolean esCodigoLegado(String codigo, boolean legado) {
		return legado && esCodigoLegado(codigo);
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
	 * Nombre con el que se construye el concepto de un cargo del producto. Con comportamiento heredado
	 * ({@code legado}): el literal heredado para los códigos de V24; el nombre del producto para los códigos
	 * propios; para un producto sin clasificar, {@code legadoSinCodigo} (el nombre que usaba el código
	 * anterior) o, si es null, su nombre. Sin comportamiento heredado: siempre el nombre del producto (o
	 * {@code legadoSinCodigo} si no tiene nombre).
	 */
	public static String conceptoBase(Producto producto, String legadoSinCodigo, boolean legado) {
		if (producto == null) {
			return legadoSinCodigo;
		}
		String codigo = producto.getCodigo();
		if (esCodigoLegado(codigo, legado)) {
			return CONCEPTO_POR_CODIGO.get(codigo);
		}
		boolean conNombre = producto.getConcepto() != null && !producto.getConcepto().isBlank();
		if ((codigo != null || !legado) && conNombre) {
			return producto.getConcepto().trim();
		}
		return legadoSinCodigo != null ? legadoSinCodigo : producto.getConcepto();
	}

	/**
	 * Tipo del catálogo que puede decidir por encima del concepto: el del producto con un código PROPIO;
	 * null para productos sin clasificar o sin producto y, con comportamiento heredado ({@code legado}), para
	 * los de códigos de V24.
	 */
	public static TipoProducto tipoDeCatalogo(Producto producto, boolean legado) {
		return producto == null ? null : tipoDeCatalogo(producto.getCodigo(), producto.getTipo(), legado);
	}

	public static TipoProducto tipoDeCatalogo(String codigo, TipoProducto tipo, boolean legado) {
		if (codigo == null || esCodigoLegado(codigo, legado)) {
			return null;
		}
		return tipo;
	}
}
