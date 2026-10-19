package com.taemoi.project.services;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.taemoi.project.entities.Producto;

public interface ProductoService {

	List<Producto> obtenerTodosLosProductos();

	Page<Producto> obtenerProductosPaginados(Pageable pageable);

	Optional<Producto> obtenerProductoPorId(Long id);

	Page<Producto> buscarProductosPorConcepto(String concepto, Pageable pageable);

	Producto guardarProducto(Producto producto);

	void eliminarProducto(Long id);

	Producto actualizarProducto(Long id, Producto productoDetalles);

	/** Máximo de productos distintos que se marcan o desmarcan en un bloque (pieza 6b-3c). */
	int MAX_IDS_VENDIBLE_PROFESOR = 200;

	/**
	 * Pieza 6b-3c: pone solo la marca «Lo puede vender el profesor» de un producto de la escuela activa.
	 *
	 * @throws org.springframework.web.server.ResponseStatusException 400 sin marca; 404 si no existe en la escuela
	 */
	Producto marcarVendibleProfesor(Long id, Boolean vendibleProfesor);

	/**
	 * Pieza 6b-3c: pone solo la marca a varios productos de la escuela activa, todo o nada.
	 *
	 * @return cuántos productos distintos se han actualizado
	 * @throws org.springframework.web.server.ResponseStatusException 400 por ids o marca no válidos; 404 si alguno
	 *                                                                no existe en la escuela (y entonces no cambia
	 *                                                                ninguno)
	 */
	int marcarVendibleProfesor(List<Long> ids, Boolean vendibleProfesor);
}
