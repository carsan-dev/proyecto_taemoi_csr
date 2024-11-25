package com.taemoi.project.servicios;

import java.util.List;

import com.taemoi.project.dtos.ProductoAlumnoDTO;

public interface ProductoAlumnoService {
	
	ProductoAlumnoDTO asignarProductoAAlumno(Long alumnoId, Long productoId, ProductoAlumnoDTO detallesDTO);

	List<ProductoAlumnoDTO> obtenerProductosDeAlumno(Long alumnoId);

	ProductoAlumnoDTO actualizarProductoAlumno(Long id, ProductoAlumnoDTO detallesDTO);

	void eliminarProductoAlumno(Long id);
}
