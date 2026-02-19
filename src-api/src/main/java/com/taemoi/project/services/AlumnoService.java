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
import com.taemoi.project.dtos.response.RetoDiarioRankingSemanalResponse;
import com.taemoi.project.dtos.response.RetoDiarioEstadoDTO;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.Deporte;
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

	Page<Alumno> obtenerAlumnosFiltrados(String nombre, Long gradoId, Long categoriaId, boolean incluirInactivos,
			boolean aptoParaExamen, @NonNull Pageable pageable);

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

	boolean datosAlumnoValidos(AlumnoDTO alumnoDTO, boolean requiereTarifaYFechaAlta);

	String generarContrasena(String nombre, String apellidos);

	Alumno actualizarObservaciones(@NonNull Long id, String observaciones);

	Alumno darDeBajaAlumno(Long id);

	Alumno darDeAltaAlumno(Long id);

	List<AlumnoConGruposDTO> obtenerAlumnosAptosConGruposDTO();

	List<AlumnoConGruposDTO> obtenerAlumnosAptosPorDeporte(String deporte, String exclusion);

	Optional<AlumnoConGruposDTO> obtenerAlumnoAptoPorId(Long id);

	List<AlumnoConGruposDTO> obtenerAlumnosElegiblesParaConvocatoria(com.taemoi.project.entities.Deporte deporte);

	AlumnoConvocatoriaDTO agregarAlumnoAConvocatoria(Long alumnoId, Long convocatoriaId, boolean porRecompensa,
			boolean rojoBordado);

	void eliminarAlumnoDeConvocatoria(Long alumnoId, Long convocatoriaId);

	boolean esAptoParaExamen(Alumno alumno);
	
	long countAlumnos();

	Documento agregarDocumentoAAlumno(Long alumnoId, MultipartFile archivo);

	List<Documento> obtenerDocumentosAlumno(Long alumnoId);

	void eliminarDocumentoDeAlumno(Long alumnoId, Long documentoId);

	Documento obtenerDocumentoDeAlumno(Long alumnoId, Long documentoId);

	RetoDiarioEstadoDTO obtenerEstadoRetoDiario(Long alumnoId);

	RetoDiarioEstadoDTO completarRetoDiario(Long alumnoId);

	RetoDiarioRankingSemanalResponse obtenerRankingSemanalRetoDiario(Long alumnoId, Deporte deporte, Integer limit);

	// ==================== MÉTODOS MULTI-DEPORTE ====================

	/**
	 * Obtiene todos los deportes de un alumno
	 */
	List<com.taemoi.project.entities.AlumnoDeporte> obtenerDeportesDelAlumno(Long alumnoId);

	/**
	 * Obtiene solo los deportes activos de un alumno
	 */
	List<com.taemoi.project.entities.AlumnoDeporte> obtenerDeportesActivosDelAlumno(Long alumnoId);

	/**
	 * Agrega un deporte a un alumno
	 */
	com.taemoi.project.entities.AlumnoDeporte agregarDeporteAAlumno(Long alumnoId,
			com.taemoi.project.entities.Deporte deporte,
			com.taemoi.project.entities.TipoGrado gradoInicial,
			java.util.Date fechaAlta,
			java.util.Date fechaAltaInicial,
			java.util.Date fechaGrado);

	/**
	 * Remueve un deporte de un alumno
	 */
	void removerDeporteDeAlumno(Long alumnoId, com.taemoi.project.entities.Deporte deporte);

	/**
	 * Actualiza el grado de un alumno en un deporte específico
	 */
	com.taemoi.project.entities.AlumnoDeporte actualizarGradoPorDeporte(Long alumnoId,
			com.taemoi.project.entities.Deporte deporte,
			com.taemoi.project.entities.TipoGrado nuevoGrado);

	/**
	 * Pasa de grado por recompensa en un deporte específico y asigna el producto correspondiente
	 */
	com.taemoi.project.entities.AlumnoDeporte pasarGradoPorRecompensa(Long alumnoId,
			com.taemoi.project.entities.Deporte deporte, boolean rojoBordado);

	/**
	 * Pasa de grado con derecho de examen (no recompensa) y asigna el producto correspondiente.
	 * Cuando el producto se marque como pagado, se actualizará el grado.
	 */
	com.taemoi.project.entities.AlumnoDeporte pasarGradoConDerechoExamen(Long alumnoId,
			com.taemoi.project.entities.Deporte deporte, boolean rojoBordado);

	/**
	 * Actualiza la fecha de alta inicial del alumno
	 * Esta fecha afecta el cálculo de antigüedad para todos los deportes
	 *
	 * @param id                    ID del alumno
	 * @param nuevaFechaAltaInicial Nueva fecha de alta inicial
	 * @return Alumno actualizado
	 */
	Alumno actualizarFechaAltaInicial(@NonNull Long id, Date nuevaFechaAltaInicial);

	/**
	 * Busca un alumno por ID (alias de obtenerAlumnoPorId para compatibilidad)
	 *
	 * @param id ID del alumno
	 * @return Alumno encontrado
	 * @throws com.taemoi.project.exceptions.alumno.AlumnoNoEncontradoException si no se encuentra
	 */
	Alumno buscarAlumno(@NonNull Long id);
}
