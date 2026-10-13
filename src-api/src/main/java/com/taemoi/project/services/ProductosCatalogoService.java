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
 * <p>El grado de un producto de examen o recompensa se resuelve por ENLACE
 * ({@code grado.producto_*_id}), nunca por el nombre: a diferencia de
 * {@code TipoGrado.fromProductoExamen} (que usa {@code contains} y devuelve el grado de color
 * anterior para los intermedios y los PUM), devuelve el grado correcto. Las variantes «rojo
 * bordado» no están enlazadas a ningún grado (el modelo tiene un solo producto de examen y uno de
 * recompensa por grado): para ellas no se resuelve grado.
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

	/** Producto de derecho a examen para llegar a ese grado de la disciplina. */
	Optional<Producto> buscarProductoExamen(String codigoDisciplina, String codigoGrado);

	/** Producto de cambio por recompensa para llegar a ese grado de la disciplina. */
	Optional<Producto> buscarProductoRecompensa(String codigoDisciplina, String codigoGrado);

	/** Grado del sistema de la disciplina enlazado a ese producto de examen. */
	Optional<Grado> buscarGradoPorProductoExamen(Long productoId, String codigoDisciplina);

	/** Grado del sistema de la disciplina enlazado a ese producto de recompensa. */
	Optional<Grado> buscarGradoPorProductoRecompensa(Long productoId, String codigoDisciplina);

	/**
	 * Grado que activa el pago del producto: primero como recompensa y después como examen, el
	 * orden de {@code ProductoAlumnoServiceImpl.activarGradoPorRecompensaPagada}.
	 */
	Optional<Grado> buscarGradoPorProducto(Long productoId, String codigoDisciplina);
}
