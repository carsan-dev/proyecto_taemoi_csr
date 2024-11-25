package com.taemoi.project.servicios;

import java.util.List;
import java.util.Optional;

import com.taemoi.project.entidades.Producto;

public interface ProductoService {

    List<Producto> obtenerTodosLosProductos();

    Optional<Producto> obtenerProductoPorId(Long id);
    
    Producto guardarProducto(Producto producto);
    
    void eliminarProducto(Long id);

	Producto actualizarProducto(Long id, Producto productoDetalles);
}
