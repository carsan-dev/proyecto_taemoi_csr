package com.taemoi.project.services;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.web.multipart.MultipartFile;

import com.taemoi.project.dtos.AlumnoDTO;
import com.taemoi.project.dtos.TurnoDTO;
import com.taemoi.project.dtos.response.AlumnoConGruposDTO;
import com.taemoi.project.dtos.response.AlumnoConvocatoriaDTO;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.Categoria;
import com.taemoi.project.entities.Documento;
import com.taemoi.project.entities.Grado;
import com.taemoi.project.entities.RolFamiliar;
import com.taemoi.project.entities.TipoGrado;
import com.taemoi.project.entities.TipoTarifa;

import jakarta.validation.Valid;

public interface AlumnoService {
	Page<Alumno> obtenerTodosLosAlumnos(@NonNull Pageable pageable);

	List<Alumno> obtenerTodosLosAlumnos();

	Optional<Alumno> obtenerAlumnoPorId(@NonNull Long id);

	Optional<AlumnoDTO> obtenerAlumnoPorIdDTO(@NonNull Long id);

	Page<Alumno> obtenerAlumnosFiltrados(String nombre, Long gradoId, Long categoriaId, boolean incluirInactivos,
			@NonNull Pageable pageable);

	List<Alumno> obtenerAlumnosFiltrados(String nombre, Long gradoId, Long categoriaId, boolean incluirInactivos);

	Alumno crearAlumno(@Valid @NonNull Alumno alumno);

	Alumno crearAlumnoDesdeDTO(@Valid @NonNull AlumnoDTO nuevoAlumnoDTO);

	Alumno actualizarAlumno(@Valid @NonNull Long id, AlumnoDTO alumnoActualizado, Date nuevaFechaNacimiento,
			MultipartFile nuevaImagen);

	void eliminarImagenAlumno(@NonNull Long id);

	boolean eliminarAlumno(@Valid @NonNull Long id);

	List<TurnoDTO> obtenerTurnosDelAlumno(Long alumnoId);

	void asignarAlumnoATurno(Long alumnoId, Long turnoId);

	void removerAlumnoDeTurno(Long alumnoId, Long turnoId);

	double asignarCuantiaTarifa(TipoTarifa tipoTarifa);

	double asignarCuantiaTarifa(TipoTarifa tipoTarifa, RolFamiliar rolFamiliar);

	Categoria asignarCategoriaSegunEdad(int edad);

	Grado asignarGradoSegunEdad(AlumnoDTO nuevoAlumnoDTO);

	TipoGrado calcularSiguienteGrado(Alumno alumno);

	boolean fechaNacimientoValida(Date fechaNacimiento);

	boolean datosAlumnoValidos(AlumnoDTO alumnoDTO);

	String generarContrasena(String nombre, String apellidos);

	Alumno darDeBajaAlumno(Long id);

	Alumno darDeAltaAlumno(Long id);

	List<AlumnoConGruposDTO> obtenerAlumnosAptosConGruposDTO();

	List<AlumnoConGruposDTO> obtenerAlumnosAptosPorDeporte(String deporte, String exclusion);

	Optional<AlumnoConGruposDTO> obtenerAlumnoAptoPorId(Long id);

	AlumnoConvocatoriaDTO agregarAlumnoAConvocatoria(Long alumnoId, Long convocatoriaId, boolean porRecompensa);

	void eliminarAlumnoDeConvocatoria(Long alumnoId, Long convocatoriaId);

	boolean esAptoParaExamen(Alumno alumno);
	
	long countAlumnos();

	Documento agregarDocumentoAAlumno(Long alumnoId, MultipartFile archivo);

	List<Documento> obtenerDocumentosAlumno(Long alumnoId);

	void eliminarDocumentoDeAlumno(Long alumnoId, Long documentoId);
}
