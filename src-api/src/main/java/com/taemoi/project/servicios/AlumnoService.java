package com.taemoi.project.servicios;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.web.multipart.MultipartFile;

import com.taemoi.project.dtos.AlumnoDTO;
import com.taemoi.project.dtos.TurnoDTO;
import com.taemoi.project.entidades.Alumno;
import com.taemoi.project.entidades.Categoria;
import com.taemoi.project.entidades.Grado;
import com.taemoi.project.entidades.Imagen;
import com.taemoi.project.entidades.TipoTarifa;

import jakarta.validation.Valid;

public interface AlumnoService {
	Page<Alumno> obtenerTodosLosAlumnos(@NonNull Pageable pageable);
	
    List<Alumno> obtenerTodosLosAlumnos();

	Optional<Alumno> obtenerAlumnoPorId(@NonNull Long id);
	
	Optional<AlumnoDTO> obtenerAlumnoDTOPorId(@NonNull Long id);
	
	Page<Alumno> obtenerAlumnosFiltrados(String nombre, Long gradoId, Long categoriaId, boolean incluirInactivos, @NonNull Pageable pageable);
	
	List<Alumno> obtenerAlumnosFiltrados(String nombre, Long gradoId, Long categoriaId, boolean incluirInactivos);
	
	Alumno crearAlumno(@Valid @NonNull Alumno alumno);
	
	Alumno crearAlumnoDesdeDTO(@Valid @NonNull AlumnoDTO nuevoAlumnoDTO);

	Alumno actualizarAlumno(@Valid @NonNull Long id, AlumnoDTO alumnoActualizado, Date nuevaFechaNacimiento, MultipartFile nuevaImagen);

	void eliminarImagenAlumno(@NonNull Long id);
	
	boolean eliminarAlumno(@Valid @NonNull Long id);

	List<TurnoDTO> obtenerTurnosDelAlumno(Long alumnoId);

	void asignarAlumnoATurno(Long alumnoId, Long turnoId);

	void removerAlumnoDeTurno(Long alumnoId, Long turnoId);

	double asignarCuantiaTarifa(TipoTarifa tipoTarifa);

	Categoria asignarCategoriaSegunEdad(int edad);

	Grado asignarGradoSegunEdad(AlumnoDTO nuevoAlumnoDTO);

	int calcularEdad(Date fechaNacimiento);

	boolean fechaNacimientoValida(Date fechaNacimiento);

	boolean datosAlumnoValidos(AlumnoDTO alumnoDTO);
	
	String generarContrasena(String nombre, String apellidos);

	Alumno darDeBajaAlumno(Long id);

	Alumno darDeAltaAlumno(Long id);
}
