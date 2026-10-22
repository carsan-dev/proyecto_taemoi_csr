package com.taemoi.project.dtos.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * Pieza 8b-2 (decisiones 242 y 267): lo no pagado con el plazo vencido, de cualquier mes, solo mensualidades y derechos
 * de examen. {@code total} es la suma de los importes; los tres desgloses suman exactamente {@code total} y
 * {@code cargos}. {@code masAntiguos}: los cinco que vencieron antes. {@code tipo} es {@code MENSUALIDAD} o
 * {@code EXAMEN}; {@code mes}, «AAAA-MM» (el de la mensualidad o el del vencimiento del examen). Sin datos personales más
 * allá del nombre del alumno de los cinco más antiguos (la gestión lo ve en la tesorería). {@code vencidoDesde}: el
 * primer día en que cuenta como vencido, «AAAA-MM-DD».
 */
public record PendienteVencidoDTO(
		BigDecimal total,
		long cargos,
		long alumnos,
		int diaLimiteMensualidad,
		List<PorTipo> porTipo,
		List<PorMes> porMes,
		List<PorSede> porSede,
		List<Cargo> masAntiguos) {

	public record PorTipo(String tipo, BigDecimal importe, long cargos) {
	}

	public record PorMes(String mes, BigDecimal importe, long cargos) {
	}

	/** {@code sedeId} y {@code sede} nulos: cargos sin sede («Sin sede»). */
	public record PorSede(Long sedeId, String sede, BigDecimal importe, long cargos) {
	}

	public record Cargo(
			Long productoAlumnoId,
			Long alumnoId,
			String alumnoNombre,
			String concepto,
			String tipo,
			BigDecimal importe,
			String mes,
			String vencidoDesde,
			Long sedeId) {
	}
}
