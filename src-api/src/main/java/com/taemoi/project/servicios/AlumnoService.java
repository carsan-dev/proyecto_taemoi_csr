package com.taemoi.project.servicios;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.taemoi.project.dtos.AlumnoDTO;
import com.taemoi.project.dtos.response.GrupoConAlumnosDTO;
import com.taemoi.project.entidades.Alumno;
import com.taemoi.project.entidades.Categoria;
import com.taemoi.project.entidades.Grado;
import com.taemoi.project.entidades.Grupo;
import com.taemoi.project.entidades.Imagen;
import com.taemoi.project.entidades.TipoTarifa;

import jakarta.validation.Valid;

public interface AlumnoService {
	Page<Alumno> obtenerTodosLosAlumnos(Pageable pageable);
	
    List<Alumno> obtenerTodosLosAlumnos();

	Optional<Alumno> obtenerAlumnoPorId(Long id);
	
	Optional<AlumnoDTO> obtenerAlumnoDTOPorId(Long id);
	
	Page<Alumno> obtenerAlumnosFiltrados(String nombre, Long gradoId, Long categoriaId, Pageable pageable);
	
	List<Alumno> obtenerAlumnosFiltrados(String nombre, Long gradoId, Long categoriaId);
	
	Alumno crearAlumno(@Valid Alumno alumno);

	Alumno actualizarAlumno(@Valid Long id, AlumnoDTO alumnoActualizado, Date nuevaFechaNacimiento, Imagen imagen);

	void eliminarImagenAlumno(Long id);
	
	boolean eliminarAlumno(@Valid Long id);

	double asignarCuantiaTarifa(TipoTarifa tipoTarifa);

	Categoria asignarCategoriaSegunEdad(int edad);

	Grado asignarGradoSegunEdad(AlumnoDTO nuevoAlumnoDTO);

	int calcularEdad(Date fechaNacimiento);

	boolean fechaNacimientoValida(Date fechaNacimiento);

	boolean datosAlumnoValidos(AlumnoDTO alumnoDTO);
	
	String generarContrasena(String nombre, String apellidos);
}
