package com.taemoi.project.services;

import java.util.List;
import java.util.Optional;

import com.taemoi.project.entities.AlumnoDeporte;
import com.taemoi.project.entities.Deporte;
import com.taemoi.project.entities.TipoGrado;
import com.taemoi.project.entities.TipoTarifa;

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
	 * Agrega un deporte a un alumno existente con todos los campos per-sport
	 *
	 * @param alumnoId ID del alumno
	 * @param deporte Deporte a agregar
	 * @param gradoInicial Grado inicial del alumno en ese deporte (puede ser null para deportes sin grado)
	 * @param fechaAlta Fecha de alta en el deporte (puede ser null, se asigna fecha actual)
	 * @param fechaAltaInicial Fecha de alta inicial para calcular antigüedad (puede ser null, se usa fechaAlta)
	 * @param fechaGrado Fecha de grado (puede ser null, se asigna fecha actual)
	 * @param tipoTarifa Tipo de tarifa para este deporte (puede ser null)
	 * @param cuantiaTarifa Cuantía de la tarifa (puede ser null)
	 * @param rolFamiliar Rol familiar para tarifas PADRES_HIJOS (puede ser null)
	 * @param grupoFamiliar Grupo familiar para tarifa HERMANOS (puede ser null)
	 * @param competidor Si es competidor en este deporte (puede ser null, default false)
	 * @param peso Peso del alumno si es competidor (puede ser null)
	 * @param fechaPeso Fecha de medición del peso (puede ser null)
	 * @param tieneLicencia Si tiene licencia federativa para este deporte (puede ser null, default false)
	 * @param numeroLicencia Número de licencia (puede ser null)
	 * @param fechaLicencia Fecha de la licencia (puede ser null)
	 * @return AlumnoDeporte creado
	 */
	AlumnoDeporte agregarDeporteAAlumnoCompleto(Long alumnoId, Deporte deporte, TipoGrado gradoInicial,
		java.util.Date fechaAlta, java.util.Date fechaAltaInicial, java.util.Date fechaGrado, TipoTarifa tipoTarifa, Double cuantiaTarifa,
		String rolFamiliar, String grupoFamiliar, Boolean competidor,
		Double peso, java.util.Date fechaPeso, Boolean tieneLicencia, Integer numeroLicencia, java.util.Date fechaLicencia);

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
	 * Actualiza la fecha de alta inicial de un alumno en un deporte específico
	 * Esta fecha se usa para calcular la antigüedad en el deporte
	 *
	 * @param alumnoId ID del alumno
	 * @param deporte Deporte a actualizar
	 * @param fechaAltaInicial Nueva fecha de alta inicial
	 * @return AlumnoDeporte actualizado
	 */
	AlumnoDeporte actualizarFechaAltaInicial(Long alumnoId, Deporte deporte, java.util.Date fechaAltaInicial);

	/**
	 * Actualiza el tipo de tarifa de un alumno en un deporte específico
	 *
	 * @param alumnoId ID del alumno
	 * @param deporte Deporte a actualizar
	 * @param tipoTarifa Nuevo tipo de tarifa
	 * @return AlumnoDeporte actualizado
	 */
	AlumnoDeporte actualizarTipoTarifa(Long alumnoId, Deporte deporte, TipoTarifa tipoTarifa);

	/**
	 * Actualiza la cuantía de tarifa de un alumno en un deporte específico
	 *
	 * @param alumnoId ID del alumno
	 * @param deporte Deporte a actualizar
	 * @param cuantiaTarifa Nueva cuantía de tarifa
	 * @return AlumnoDeporte actualizado
	 */
	AlumnoDeporte actualizarCuantiaTarifa(Long alumnoId, Deporte deporte, Double cuantiaTarifa);

	/**
	 * Actualiza el rol familiar de un alumno en un deporte específico
	 * Usado para tarifas tipo PADRES_HIJOS
	 *
	 * @param alumnoId ID del alumno
	 * @param deporte Deporte a actualizar
	 * @param rolFamiliar Nuevo rol familiar (PADRE, HIJO, NINGUNO)
	 * @return AlumnoDeporte actualizado
	 */
	AlumnoDeporte actualizarRolFamiliar(Long alumnoId, Deporte deporte, String rolFamiliar);

	/**
	 * Actualiza el grupo familiar de un alumno en un deporte específico
	 * Usado para tarifas tipo HERMANOS
	 *
	 * @param alumnoId ID del alumno
	 * @param deporte Deporte a actualizar
	 * @param grupoFamiliar Nuevo grupo familiar
	 * @return AlumnoDeporte actualizado
	 */
	AlumnoDeporte actualizarGrupoFamiliar(Long alumnoId, Deporte deporte, String grupoFamiliar);

	/**
	 * Actualiza si el alumno tiene licencia federativa en un deporte específico
	 *
	 * @param alumnoId ID del alumno
	 * @param deporte Deporte a actualizar
	 * @param tieneLicencia Si tiene licencia o no
	 * @return AlumnoDeporte actualizado
	 */
	AlumnoDeporte actualizarTieneLicencia(Long alumnoId, Deporte deporte, Boolean tieneLicencia);

	/**
	 * Actualiza el número de licencia de un alumno en un deporte específico
	 *
	 * @param alumnoId ID del alumno
	 * @param deporte Deporte a actualizar
	 * @param numeroLicencia Nuevo número de licencia
	 * @return AlumnoDeporte actualizado
	 */
	AlumnoDeporte actualizarNumeroLicencia(Long alumnoId, Deporte deporte, Integer numeroLicencia);

	/**
	 * Actualiza la fecha de licencia de un alumno en un deporte específico
	 *
	 * @param alumnoId ID del alumno
	 * @param deporte Deporte a actualizar
	 * @param fechaLicencia Nueva fecha de licencia
	 * @return AlumnoDeporte actualizado
	 */
	AlumnoDeporte actualizarFechaLicencia(Long alumnoId, Deporte deporte, java.util.Date fechaLicencia);

	/**
	 * Actualiza si el alumno es competidor en un deporte específico
	 *
	 * @param alumnoId ID del alumno
	 * @param deporte Deporte a actualizar
	 * @param competidor Si es competidor o no
	 * @return AlumnoDeporte actualizado
	 */
	AlumnoDeporte actualizarCompetidor(Long alumnoId, Deporte deporte, Boolean competidor);

	/**
	 * Actualiza el peso de un alumno en un deporte específico
	 * Usado para competidores
	 *
	 * @param alumnoId ID del alumno
	 * @param deporte Deporte a actualizar
	 * @param peso Nuevo peso
	 * @return AlumnoDeporte actualizado
	 */
	AlumnoDeporte actualizarPeso(Long alumnoId, Deporte deporte, Double peso);

	/**
	 * Actualiza la fecha de peso de un alumno en un deporte específico
	 *
	 * @param alumnoId ID del alumno
	 * @param deporte Deporte a actualizar
	 * @param fechaPeso Nueva fecha de peso
	 * @return AlumnoDeporte actualizado
	 */
	AlumnoDeporte actualizarFechaPeso(Long alumnoId, Deporte deporte, java.util.Date fechaPeso);

	/**
	 * Actualiza la fecha de alta como competidor de un alumno en un deporte específico
	 *
	 * @param alumnoId ID del alumno
	 * @param deporte Deporte a actualizar
	 * @param fechaAltaCompeticion Nueva fecha de alta como competidor
	 * @return AlumnoDeporte actualizado
	 */
	AlumnoDeporte actualizarFechaAltaCompeticion(Long alumnoId, Deporte deporte, java.util.Date fechaAltaCompeticion);

	/**
	 * Actualiza la fecha de alta inicial como competidor de un alumno en un deporte específico
	 *
	 * @param alumnoId ID del alumno
	 * @param deporte Deporte a actualizar
	 * @param fechaAltaCompetidorInicial Nueva fecha de alta inicial como competidor
	 * @return AlumnoDeporte actualizado
	 */
	AlumnoDeporte actualizarFechaAltaCompetidorInicial(Long alumnoId, Deporte deporte, java.util.Date fechaAltaCompetidorInicial);

	/**
	 * Actualiza la categoría de un alumno competidor en un deporte específico
	 *
	 * @param alumnoId ID del alumno
	 * @param deporte Deporte a actualizar
	 * @param categoriaNombre Nombre de la nueva categoría (INFANTIL, PRECADETE, CADETE, JUNIOR, SENIOR)
	 * @return AlumnoDeporte actualizado
	 */
	AlumnoDeporte actualizarCategoria(Long alumnoId, Deporte deporte, String categoriaNombre);

	/**
	 * Actualiza todos los datos de competidor en una sola transacción.
	 * Evita condiciones de carrera cuando se actualizan múltiples campos.
	 *
	 * @param alumnoId ID del alumno
	 * @param deporte Deporte a actualizar
	 * @param competidor Si es competidor o no (puede ser null para no cambiar)
	 * @param fechaAltaCompeticion Fecha de alta como competidor (puede ser null)
	 * @param fechaAltaCompetidorInicial Fecha de alta inicial como competidor (puede ser null)
	 * @param categoriaNombre Nombre de la categoría (puede ser null)
	 * @param peso Peso del competidor (puede ser null)
	 * @param fechaPeso Fecha de medición del peso (puede ser null)
	 * @return AlumnoDeporte actualizado
	 */
	AlumnoDeporte actualizarDatosCompetidor(Long alumnoId, Deporte deporte, Boolean competidor,
			java.util.Date fechaAltaCompeticion, java.util.Date fechaAltaCompetidorInicial,
			String categoriaNombre, Double peso, java.util.Date fechaPeso);

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
