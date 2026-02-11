package com.taemoi.project.services;

import java.util.List;

import org.springframework.data.domain.Page;

import com.taemoi.project.dtos.response.TesoreriaMovimientoDTO;
import com.taemoi.project.dtos.response.TesoreriaResumenDTO;

public interface TesoreriaService {

	TesoreriaResumenDTO obtenerResumen(Integer mes, Integer ano, String deporte);

	Page<TesoreriaMovimientoDTO> obtenerMovimientos(
			Integer mes,
			Integer ano,
			String deporte,
			Boolean pagado,
			String texto,
			Integer page,
			Integer size);

	List<Integer> obtenerAniosDisponibles();

	byte[] exportarMovimientosCSV(Integer mes, Integer ano, String deporte, Boolean pagado, String texto);

	byte[] exportarMovimientosPDF(Integer mes, Integer ano, String deporte, Boolean pagado, String texto);
}
