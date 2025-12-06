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
	 * @return AlumnoDeporte creado
	 */
	AlumnoDeporte agregarDeporteAAlumno(Long alumnoId, Deporte deporte, TipoGrado gradoInicial);

	/**
	 * Remueve un deporte de un alumno (marca como inactivo)
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
