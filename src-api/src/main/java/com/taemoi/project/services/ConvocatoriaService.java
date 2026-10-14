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

	List<AlumnoConvocatoriaReporteDTO> obtenerReporteDeConvocatoria(Long convocatoriaId);

	void eliminarConvocatoria(Long convocatoriaId);

	void actualizarGradosDeConvocatoria(Long convocatoriaId);

	void actualizarAlumnoConvocatoria(Long alumnoConvocatoriaId, AlumnoConvocatoriaDTO alumnoConvocatoriaDTO);
}
