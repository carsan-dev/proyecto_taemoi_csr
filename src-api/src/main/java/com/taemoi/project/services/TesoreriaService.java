package com.taemoi.project.services;

import java.util.List;

import org.springframework.data.domain.Page;

import com.taemoi.project.dtos.response.TesoreriaMovimientoDTO;
import com.taemoi.project.dtos.response.TesoreriaResumenDTO;

/**
 * Tesorería. Entrega 6-3 (D6-12): los métodos con {@code sedeId} filtran por la sede del cargo; sin {@code sedeId}, la
 * gestión ve toda la escuela (las consultas de siempre) y el responsable de sede, sus sedes. Las variantes sin
 * {@code sedeId} son las de antes (sin filtro de sede solicitado).
 */
public interface TesoreriaService {

	default TesoreriaResumenDTO obtenerResumen(Integer mes, Integer ano, String deporte, Boolean soloActivos) {
		return obtenerResumen(mes, ano, deporte, soloActivos, null);
	}

	TesoreriaResumenDTO obtenerResumen(Integer mes, Integer ano, String deporte, Boolean soloActivos, Long sedeId);

	default Page<TesoreriaMovimientoDTO> obtenerMovimientos(
			Integer mes,
			Integer ano,
			String deporte,
			Boolean pagado,
			String texto,
			Boolean soloActivos,
			Integer page,
			Integer size) {
		return obtenerMovimientos(mes, ano, deporte, pagado, texto, soloActivos, page, size, null);
	}

	Page<TesoreriaMovimientoDTO> obtenerMovimientos(
			Integer mes,
			Integer ano,
			String deporte,
			Boolean pagado,
			String texto,
			Boolean soloActivos,
			Integer page,
			Integer size,
			Long sedeId);

	List<Integer> obtenerAniosDisponibles();

	default byte[] exportarMovimientosCSV(
			Integer mes,
			Integer ano,
			String deporte,
			Boolean pagado,
			String texto,
			Boolean soloActivos) {
		return exportarMovimientosCSV(mes, ano, deporte, pagado, texto, soloActivos, null);
	}

	byte[] exportarMovimientosCSV(
			Integer mes,
			Integer ano,
			String deporte,
			Boolean pagado,
			String texto,
			Boolean soloActivos,
			Long sedeId);

	default byte[] exportarMovimientosPDF(
			Integer mes,
			Integer ano,
			String deporte,
			Boolean pagado,
			String texto,
			Boolean soloActivos) {
		return exportarMovimientosPDF(mes, ano, deporte, pagado, texto, soloActivos, null);
	}

	byte[] exportarMovimientosPDF(
			Integer mes,
			Integer ano,
			String deporte,
			Boolean pagado,
			String texto,
			Boolean soloActivos,
			Long sedeId);

	byte[] generarCertificadoCobrosAlumno(Long alumnoId, Integer ano);

	byte[] generarCertificadosCobrosZip(List<Long> alumnoIds, Integer ano);
}
