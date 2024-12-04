package com.taemoi.project.servicios;

import java.util.List;
import java.util.Optional;

import com.taemoi.project.dtos.ConvocatoriaDTO;
import com.taemoi.project.entidades.Deporte;

public interface ConvocatoriaService {

	ConvocatoriaDTO crearConvocatoria(ConvocatoriaDTO convocatoriaDTO);

	List<ConvocatoriaDTO> obtenerConvocatorias();

	ConvocatoriaDTO obtenerConvocatoriaPorId(Long id);

	Optional<ConvocatoriaDTO> obtenerConvocatoriaActualPorDeporte(Deporte deporte);
	
	List<ConvocatoriaDTO> obtenerConvocatoriasPorDeporte(Deporte deporte);

}
