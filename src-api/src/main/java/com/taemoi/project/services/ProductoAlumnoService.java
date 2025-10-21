package com.taemoi.project.services;

import java.util.List;

import com.taemoi.project.dtos.ProductoAlumnoDTO;
import com.taemoi.project.entities.Alumno;

public interface ProductoAlumnoService {

	ProductoAlumnoDTO asignarProductoAAlumno(Long alumnoId, Long productoId, ProductoAlumnoDTO detallesDTO);

	List<ProductoAlumnoDTO> obtenerProductosDeAlumno(Long alumnoId);

	ProductoAlumnoDTO actualizarProductoAlumno(Long id, ProductoAlumnoDTO detallesDTO);

	void eliminarProductoAlumno(Long id);

	ProductoAlumnoDTO reservarPlaza(Long alumnoId, String concepto, boolean pagado, boolean forzar);

	void cargarMensualidadesGenerales(String mesAno);

	void cargarMensualidadIndividual(Long alumnoId, String mesAno, boolean forzar);
	
	void crearAltaLicenciaFederativa(Alumno alumno);

	ProductoAlumnoDTO renovarLicencia(Long alumnoId);
}
