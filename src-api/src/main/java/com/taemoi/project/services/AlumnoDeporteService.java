package com.taemoi.project.services;

import java.util.List;
import java.util.Optional;

import com.taemoi.project.entities.AlumnoDeporte;
import com.taemoi.project.entities.Deporte;
import com.taemoi.project.entities.TipoGrado;

public interface AlumnoDeporteService {

	/**
	 * Agrega un deporte a un alumno existente
	 *
	 * @param alumnoId ID del alumno
	 * @param deporte Deporte a agregar
	 * @param gradoInicial Grado inicial del alumno en ese deporte (puede ser null para deportes sin grado)
	 * @param fechaAlta Fecha de alta en el deporte (puede ser null, se asigna fecha actual)
	 * @param fechaGrado Fecha de grado (puede ser null, se asigna fecha actual)
	 * @return AlumnoDeporte creado
	 */
	AlumnoDeporte agregarDeporteAAlumno(Long alumnoId, Deporte deporte, TipoGrado gradoInicial, java.util.Date fechaAlta, java.util.Date fechaGrado);

	/**
	 * Desactiva un deporte de un alumno (marca como inactivo pero mantiene todos los datos)
	 *
	 * @param alumnoId ID del alumno
	 * @param deporte Deporte a desactivar
	 */
	void desactivarDeporteDeAlumno(Long alumnoId, Deporte deporte);

	/**
	 * Activa un deporte de un alumno que estaba inactivo (preserva todos los datos)
	 *
	 * @param alumnoId ID del alumno
	 * @param deporte Deporte a activar
	 */
	void activarDeporteDeAlumno(Long alumnoId, Deporte deporte);

	/**
	 * Remueve completamente un deporte de un alumno (eliminación física del registro)
	 *
	 * @param alumnoId ID del alumno
	 * @param deporte Deporte a remover
	 */
	void removerDeporteDeAlumno(Long alumnoId, Deporte deporte);

	/**
	 * Obtiene todos los deportes de un alumno (activos e inactivos)
	 *
	 * @param alumnoId ID del alumno
	 * @return Lista de AlumnoDeporte
	 */
	List<AlumnoDeporte> obtenerDeportesDelAlumno(Long alumnoId);

	/**
	 * Obtiene solo los deportes activos de un alumno
	 *
	 * @param alumnoId ID del alumno
	 * @return Lista de AlumnoDeporte activos
	 */
	List<AlumnoDeporte> obtenerDeportesActivosDelAlumno(Long alumnoId);

	/**
	 * Obtiene un deporte específico de un alumno
	 *
	 * @param alumnoId ID del alumno
	 * @param deporte Deporte a buscar
	 * @return Optional con AlumnoDeporte si existe
	 */
	Optional<AlumnoDeporte> obtenerAlumnoDeporte(Long alumnoId, Deporte deporte);

	/**
	 * Actualiza el grado de un alumno en un deporte específico
	 *
	 * @param alumnoId ID del alumno
	 * @param deporte Deporte a actualizar
	 * @param nuevoGrado Nuevo grado
	 * @return AlumnoDeporte actualizado
	 */
	AlumnoDeporte actualizarGradoPorDeporte(Long alumnoId, Deporte deporte, TipoGrado nuevoGrado);

	/**
	 * Actualiza el estado de aptitud para examen de un alumno en un deporte específico
	 *
	 * @param alumnoId ID del alumno
	 * @param deporte Deporte a actualizar
	 * @param aptoParaExamen Nuevo estado de aptitud
	 * @return AlumnoDeporte actualizado
	 */
	AlumnoDeporte actualizarAptoParaExamen(Long alumnoId, Deporte deporte, Boolean aptoParaExamen);

	/**
	 * Actualiza la fecha de grado de un alumno en un deporte específico
	 *
	 * @param alumnoId ID del alumno
	 * @param deporte Deporte a actualizar
	 * @param fechaGrado Nueva fecha de grado
	 * @return AlumnoDeporte actualizado
	 */
	AlumnoDeporte actualizarFechaGrado(Long alumnoId, Deporte deporte, java.util.Date fechaGrado);

	/**
	 * Calcula el siguiente grado para un alumno en un deporte específico
	 *
	 * @param alumnoDeporte AlumnoDeporte para calcular siguiente grado
	 * @return TipoGrado siguiente o null si no hay progresión
	 */
	TipoGrado calcularSiguienteGrado(AlumnoDeporte alumnoDeporte);

	/**
	 * Verifica si un alumno es apto para examen en un deporte específico
	 *
	 * @param alumnoDeporte AlumnoDeporte a verificar
	 * @return true si es apto para examen
	 */
	boolean esAptoParaExamen(AlumnoDeporte alumnoDeporte);

	/**
	 * Actualiza el estado de aptitud para examen de todos los deportes de un alumno
	 *
	 * @param alumnoId ID del alumno
	 */
	void actualizarAptitudParaExamen(Long alumnoId);

	/**
	 * Obtiene todos los alumnos aptos para examen en un deporte específico
	 *
	 * @param deporte Deporte a filtrar
	 * @return Lista de AlumnoDeporte aptos
	 */
	List<AlumnoDeporte> obtenerAptosParaExamenPorDeporte(Deporte deporte);

	/**
	 * Verifica si un alumno ya tiene asignado un deporte
	 *
	 * @param alumnoId ID del alumno
	 * @param deporte Deporte a verificar
	 * @return true si el alumno ya tiene ese deporte
	 */
	boolean alumnoTieneDeporte(Long alumnoId, Deporte deporte);

	/**
	 * Cuenta cuántos deportes activos tiene un alumno
	 *
	 * @param alumnoId ID del alumno
	 * @return Número de deportes activos
	 */
	long contarDeportesActivos(Long alumnoId);

	/**
	 * Obtiene un AlumnoDeporte por ID con todas sus relaciones cargadas
	 *
	 * @param id ID del AlumnoDeporte
	 * @return Optional con AlumnoDeporte si existe
	 */
	Optional<AlumnoDeporte> obtenerPorIdConRelaciones(Long id);
}
