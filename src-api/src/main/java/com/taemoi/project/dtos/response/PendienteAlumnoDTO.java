package com.taemoi.project.dtos.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * Pieza 8b-3a: lo vencido de un alumno del listado nuevo, con la misma regla que {@link PendienteVencidoDTO}
 * (decisión 267). {@code importe} y {@code cargos} son la suma y el número de {@code detalle}; cada cargo trae lo que
 * «Cobrar» necesita para confirmar y cobrar como la tesorería de hoy. {@code vencidoDesde}: «AAAA-MM-DD».
 */
public record PendienteAlumnoDTO(Long alumnoId, BigDecimal importe, long cargos, List<Cargo> detalle) {

	public record Cargo(Long productoAlumnoId, String concepto, BigDecimal importe, String vencidoDesde) {
	}
}
