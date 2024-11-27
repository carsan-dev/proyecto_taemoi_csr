package com.taemoi.project.servicios;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.taemoi.project.entidades.Producto;

public interface ProductoService {

    List<Producto> obtenerTodosLosProductos();
    
    Page<Producto> obtenerProductosPaginados(Pageable pageable);

    Optional<Producto> obtenerProductoPorId(Long id); 

	Page<Producto> buscarProductosPorConcepto(String concepto, Pageable pageable);
    
    Producto guardarProducto(Producto producto);
    
    void eliminarProducto(Long id);

	Producto actualizarProducto(Long id, Producto productoDetalles);
}
