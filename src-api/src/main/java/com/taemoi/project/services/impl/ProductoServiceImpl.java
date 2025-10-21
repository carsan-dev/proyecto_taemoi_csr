package com.taemoi.project.services.impl;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.taemoi.project.entities.Producto;
import com.taemoi.project.repositories.ProductoRepository;
import com.taemoi.project.services.ProductoService;

@Service
public class ProductoServiceImpl implements ProductoService {

	@Autowired
	private ProductoRepository productoRepository;

	@Override
	public List<Producto> obtenerTodosLosProductos() {
		return productoRepository.findAll();
	}

	@Override
	public Page<Producto> obtenerProductosPaginados(Pageable pageable) {
		return productoRepository.findAll(pageable);
	}

	@Override
	public Optional<Producto> obtenerProductoPorId(Long id) {
		return productoRepository.findById(id);
	}

	@Override
	public Page<Producto> buscarProductosPorConcepto(String concepto, Pageable pageable) {
		return productoRepository.findByConceptoContaining(concepto, pageable);
	}

	@Override
	public Producto guardarProducto(Producto producto) {
		return productoRepository.save(producto);
	}

	@Override
	public Producto actualizarProducto(Long id, Producto productoDetalles) {
		Optional<Producto> productoActual = productoRepository.findById(id);

		if (productoActual.isPresent()) {
			Producto producto = productoActual.get();
			// Actualiza los campos necesarios
			producto.setConcepto(productoDetalles.getConcepto());
			producto.setPrecio(productoDetalles.getPrecio());

			return productoRepository.save(producto);
		} else {
			// Maneja el caso cuando el producto no existe
			throw new RuntimeException("Producto no encontrado con id " + id);
		}
	}

	@Override
	public void eliminarProducto(Long id) {
		productoRepository.deleteById(id);
	}
}
