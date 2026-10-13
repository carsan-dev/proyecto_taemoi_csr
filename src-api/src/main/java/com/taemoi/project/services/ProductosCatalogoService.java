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
 * {@code fromProductoRecompensa}. Todavía no lo usa ningún llamador.
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
}
