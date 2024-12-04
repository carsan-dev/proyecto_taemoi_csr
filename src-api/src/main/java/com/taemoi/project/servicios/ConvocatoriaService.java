package com.taemoi.project.servicios;

import java.util.List;
import java.util.Optional;

import com.taemoi.project.entidades.Convocatoria;
import com.taemoi.project.entidades.Deporte;

public interface ConvocatoriaService {
	Convocatoria crearConvocatoria(Convocatoria convocatoria);

	List<Convocatoria> obtenerConvocatorias();

	Convocatoria obtenerConvocatoriaPorId(Long id);

	Optional<Convocatoria> obtenerConvocatoriaActualPorDeporte(Deporte deporte);
}
