package com.taemoi.project.services;

import java.util.List;
import java.util.Optional;

import com.taemoi.project.dtos.ConvocatoriaDTO;
import com.taemoi.project.dtos.response.AlumnoConvocatoriaDTO;
import com.taemoi.project.dtos.response.AlumnoConvocatoriaReporteDTO;

public interface ConvocatoriaService {

	ConvocatoriaDTO crearConvocatoria(ConvocatoriaDTO convocatoriaDTO);

	List<ConvocatoriaDTO> obtenerConvocatorias();

	ConvocatoriaDTO obtenerConvocatoriaPorId(Long id);

	List<ConvocatoriaDTO> obtenerConvocatoriasDeAlumno(Long alumnoId);

	Optional<ConvocatoriaDTO> obtenerConvocatoriaActualPorDeporte(String deporte);

	List<ConvocatoriaDTO> obtenerConvocatoriasPorDeporte(String deporte);

	List<AlumnoConvocatoriaDTO> obtenerAlumnosDeConvocatoria(Long convocatoriaId);

	/**
	 * Pieza 9-6 (decisión 376): los inscritos con esa sede principal, por AND con el alcance. La convocatoria es de toda
	 * la escuela y no tiene sede: el filtro va por el alumno, la misma sede de su cargo de examen (D6-9).
	 */
	List<AlumnoConvocatoriaDTO> obtenerAlumnosDeConvocatoria(Long convocatoriaId, Long sedeId);

	List<AlumnoConvocatoriaReporteDTO> obtenerReporteDeConvocatoria(Long convocatoriaId);

	void eliminarConvocatoria(Long convocatoriaId);

	void actualizarGradosDeConvocatoria(Long convocatoriaId);

	void actualizarAlumnoConvocatoria(Long alumnoConvocatoriaId, AlumnoConvocatoriaDTO alumnoConvocatoriaDTO);
}
