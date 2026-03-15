package com.taemoi.project.services;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;

import com.taemoi.project.dtos.response.AuditoriaEventoDTO;
import com.taemoi.project.dtos.response.AuditoriaEventoDetalleDTO;
import com.taemoi.project.entities.AuditoriaAccion;

public interface AuditoriaService {

	void registrarEvento(
			AuditoriaAccion accion,
			String metodoHttp,
			String endpoint,
			String queryParamsJson,
			String payloadJson,
			boolean payloadTruncado,
			Integer estadoHttp,
			String ipCliente,
			String userAgent);

	Page<AuditoriaEventoDTO> obtenerEventos(
			LocalDate desde,
			LocalDate hasta,
			String resultado,
			String accion,
			String modulo,
			String usuario,
			String endpoint,
			String texto,
			Integer page,
			Integer size,
			Boolean incluirRuido);

	AuditoriaEventoDetalleDTO obtenerEventoPorId(Long id);

	List<String> obtenerModulosDisponibles();

	long eliminarEventosAnterioresA(LocalDate fechaLimite);
}
