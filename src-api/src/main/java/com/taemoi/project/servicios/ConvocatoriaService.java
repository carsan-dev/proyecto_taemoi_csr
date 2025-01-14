package com.taemoi.project.servicios;

import java.util.List;
import java.util.Optional;

import com.taemoi.project.dtos.ConvocatoriaDTO;
import com.taemoi.project.dtos.response.AlumnoConvocatoriaDTO;
import com.taemoi.project.entidades.Deporte;

public interface ConvocatoriaService {

	ConvocatoriaDTO crearConvocatoria(ConvocatoriaDTO convocatoriaDTO);

	List<ConvocatoriaDTO> obtenerConvocatorias();

	ConvocatoriaDTO obtenerConvocatoriaPorId(Long id);

	List<ConvocatoriaDTO> obtenerConvocatoriasDeAlumno(Long alumnoId);

	Optional<ConvocatoriaDTO> obtenerConvocatoriaActualPorDeporte(Deporte deporte);

	List<ConvocatoriaDTO> obtenerConvocatoriasPorDeporte(Deporte deporte);

	List<AlumnoConvocatoriaDTO> obtenerAlumnosDeConvocatoria(Long convocatoriaId);

	void eliminarConvocatoria(Long convocatoriaId);

	void actualizarGradosDeConvocatoria(Long convocatoriaId);

	void actualizarAlumnoConvocatoria(Long alumnoConvocatoriaId, AlumnoConvocatoriaDTO alumnoConvocatoriaDTO);
}
