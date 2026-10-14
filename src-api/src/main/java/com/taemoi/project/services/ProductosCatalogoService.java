package com.taemoi.project.services;

import java.util.List;
import java.util.Optional;

import com.taemoi.project.entities.Grado;
import com.taemoi.project.entities.Producto;
import com.taemoi.project.entities.TipoProducto;

/**
 * Catálogo de productos de la organización activa ({@code producto.codigo}/{@code tipo},
 * {@code disciplina.producto_*_id}, {@code grado.producto_*_id}).
 *
 * <p>Sustituirá a las búsquedas por nombre ({@code findFirstByConcepto("MENSUALIDAD")}, las
 * constantes de {@code ProductoAlumnoServiceImpl} y {@code AlumnoServiceImpl},
 * {@code TipoGrado.obtenerNombreProducto}) y a {@code TipoGrado.fromProductoExamen} /
 * {@code fromProductoRecompensa}. Desde V26 el pago de un producto de examen o recompensa promueve
 * por {@link #buscarCodigoGradoConcedido}; el resto de búsquedas por nombre cambian con las tarifas.
 *
 * <p>El grado que concede un producto se resuelve por ENLACE ({@code producto.grado_id}), nunca
 * por el nombre: a diferencia de {@code TipoGrado.fromProductoExamen} (que usa {@code contains} y
 * devuelve el grado de color anterior para los intermedios y los PUM), devuelve el grado correcto.
 * Las dos variantes «rojo bordado» conceden ROJO (decisión del usuario: el bordado es solo el
 * cinturón elegido). {@code grado.producto_*_id} es el producto que se cobra por defecto.
 *
 * <p>Los códigos de disciplina y de grado son los del catálogo. Un código de disciplina que no
 * existe en la organización activa lanza {@link IllegalArgumentException}; disciplina nula o sin
 * sistema de grados devuelve vacío.
 */
public interface ProductosCatalogoService {

	Optional<Producto> buscarPorCodigo(String codigo);

	/**
	 * Productos de ese tipo, por id. Con {@code codigoDisciplina} nulo, los generales (sin
	 * disciplina: mensualidad, reserva de plaza, exámenes y recompensas en Taemoi).
	 */
	List<Producto> listarPorTipo(TipoProducto tipo, String codigoDisciplina);

	/** {@code disciplina.producto_competidor_id}. */
	Optional<Producto> buscarProductoCompetidor(String codigoDisciplina);

	/** {@code disciplina.producto_licencia_id} (vacío en Taemoi: sus licencias se buscan por código). */
	Optional<Producto> buscarProductoLicencia(String codigoDisciplina);

	/** Producto de derecho a examen que se cobra por defecto para llegar a ese grado. */
	Optional<Producto> buscarProductoExamen(String codigoDisciplina, String codigoGrado);

	/** Producto de cambio por recompensa que se cobra por defecto para llegar a ese grado. */
	Optional<Producto> buscarProductoRecompensa(String codigoDisciplina, String codigoGrado);

	/** Grado que concede ese producto si es de tipo EXAMEN, en el sistema de la disciplina. */
	Optional<Grado> buscarGradoPorProductoExamen(Long productoId, String codigoDisciplina);

	/** Grado que concede ese producto si es de tipo RECOMPENSA, en el sistema de la disciplina. */
	Optional<Grado> buscarGradoPorProductoRecompensa(Long productoId, String codigoDisciplina);

	/**
	 * Grado que activa el pago del producto ({@code producto.grado_id}), en el sistema de la
	 * disciplina. Sustituye a {@code ProductoAlumnoServiceImpl.activarGradoPorRecompensaPagada}.
	 */
	Optional<Grado> buscarGradoPorProducto(Long productoId, String codigoDisciplina);

	/**
	 * Código del grado que concede el pago de un cargo ({@code ProductoAlumno}), por este orden:
	 * <ol>
	 * <li>el {@code producto.grado_id} del producto cobrado (gana siempre sobre su nombre);</li>
	 * <li>si el producto ya no existe (cargos de productos borrados) o no concede grado (sin
	 * clasificar), el del producto que concede grado cuyo concepto es exactamente el del cargo, con o
	 * sin el sufijo {@code " - <DISCIPLINA>"} que añade el alta del cargo;</li>
	 * <li>si tampoco, el nombre de producto de examen o recompensa CONTENIDO en el concepto del cargo
	 * (sin distinguir mayúsculas), como hacía {@code TipoGrado.fromProductoRecompensa} /
	 * {@code fromProductoExamen}: primero recompensas y después exámenes, pero gana la coincidencia más
	 * LARGA («…AMARILLO/NARANJA…» concede AMARILLO_NARANJA, no AMARILLO) y las dos variantes de rojo
	 * bordado conceden ROJO. Nombres: los de los productos clasificados de la organización y los que
	 * usaba el código hasta V26 (Taemoi). Los administradores nombran productos libremente («DERECHO A
	 * EXAMEN VERDE MARZO 2026»).</li>
	 * </ol>
	 * Vacío si nada concede grado. El código es el del sistema del producto (taekwondo en Taemoi):
	 * cada disciplina lo resuelve en su propio sistema.
	 */
	Optional<String> buscarCodigoGradoConcedido(Long productoId, String conceptoCargo);

	// ------------------------------------------------------------------------------------------
	// Resolución de los productos que cobra el código (V27). Orden: el producto SIN clasificar con el
	// concepto que buscaba el código hasta V27 (lo que elegía findFirstByConcepto: variantes de id menor
	// o con otras mayúsculas), después el catálogo (enlace, tipo o codigo) y, si no hay nada, el producto
	// con ese concepto aunque esté clasificado. WARN en los respaldos por concepto.
	// Nunca lanzan: vacío si tampoco hay producto con ese concepto.
	// ------------------------------------------------------------------------------------------

	/** Concepto que buscaba el código hasta V27 para la mensualidad. */
	String CONCEPTO_LEGADO_MENSUALIDAD = "MENSUALIDAD";

	/** Concepto que buscaba el código hasta V27 para la reserva de plaza. */
	String CONCEPTO_LEGADO_RESERVA_PLAZA = "RESERVA DE PLAZA";

	/** Precio con el que el código creaba (y cobraba) la tarifa de competidor hasta V27. */
	double PRECIO_LEGADO_COMPETIDOR = 20.0;

	/**
	 * Producto de mensualidad: tipo {@code MENSUALIDAD} de la disciplina; si no hay, el general (sin
	 * disciplina); si no, el de concepto «MENSUALIDAD». El de menor id en cada paso.
	 */
	Optional<Producto> resolverProductoMensualidad(String codigoDisciplina);

	/** Como {@link #resolverProductoMensualidad} con el tipo {@code RESERVA_PLAZA} («RESERVA DE PLAZA»). */
	Optional<Producto> resolverProductoReservaPlaza(String codigoDisciplina);

	/**
	 * Producto de competidor de la disciplina: {@code disciplina.producto_competidor_id}; si no, el de
	 * codigo {@code COMPETIDOR_<disciplina>}; si no, el de concepto «TARIFA COMPETIDOR <disciplina>».
	 */
	Optional<Producto> resolverProductoCompetidor(String codigoDisciplina);

	/**
	 * {@link #resolverProductoCompetidor} y, si no hay ninguno, lo crea como hacía el código hasta V27
	 * (concepto «TARIFA COMPETIDOR <disciplina>», 20 €), ya clasificado ({@code COMPETIDOR_<disciplina>})
	 * y enlazado a la disciplina. Vacío si la disciplina no existe o no admite competidores.
	 */
	Optional<Producto> asegurarProductoCompetidor(String codigoDisciplina);

	/**
	 * Producto de licencia federativa por codigo {@code LICENCIA_<disciplina>_<variante>[_PROPORCIONAL]},
	 * variante {@code DISCAPACIDAD}, {@code INFANTIL} o {@code ADULTO}. La disciplina usa la variante
	 * de discapacidad o la parte proporcional solo si tiene algún producto de licencia con ella
	 * (Taemoi: taekwondo sí, kickboxing no). Si el codigo no existe, el concepto que buscaba el código
	 * hasta V27 ({@link #conceptoLegadoLicencia}).
	 */
	Optional<Producto> resolverProductoLicencia(String codigoDisciplina, boolean discapacidad, boolean menor,
			boolean proporcional);

	/**
	 * Concepto de licencia que buscaba el código hasta V27: kickboxing sin discapacidad ni parte
	 * proporcional; cualquier otra disciplina (o ninguna), los nombres de taekwondo.
	 */
	String conceptoLegadoLicencia(String codigoDisciplina, boolean discapacidad, boolean menor, boolean proporcional);

	/**
	 * Producto que se cobra para llegar a un grado: con {@code bordado}, el de codigo
	 * {@code EXAMEN_<grado>_BORDADO} / {@code RECOMPENSA_<grado>_BORDADO}; si no, el enlace
	 * {@code grado.producto_*_id} del grado en el sistema de la disciplina y después el codigo
	 * {@code EXAMEN_<grado>} / {@code RECOMPENSA_<grado>}. Si nada de eso existe, el producto con el
	 * {@code conceptoLegado} exacto y después el que coincide sin distinguir mayúsculas, acentos ni
	 * espacios repetidos (la búsqueda de {@code AlumnoServiceImpl} hasta V27).
	 */
	Optional<Producto> resolverProductoGrado(String codigoDisciplina, String codigoGrado, boolean recompensa,
			boolean bordado, String conceptoLegado);

	/** Tipo del producto con ese id; vacío si el id es nulo o el producto ya no existe. */
	Optional<TipoProducto> tipoDeProducto(Long productoId);
}
