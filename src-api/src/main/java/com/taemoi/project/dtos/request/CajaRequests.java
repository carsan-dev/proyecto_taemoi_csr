package com.taemoi.project.dtos.request;

import java.math.BigDecimal;
import java.util.List;

import com.taemoi.project.entities.TipoMovimientoCaja;

/** Peticiones de la API de caja (pieza 6b-2, {@code /api/caja}). */
public final class CajaRequests {

	private CajaRequests() {
	}

	/**
	 * Registrar una entrega en la sede: {@code POST /api/caja/sedes/{sedeId}/entregas}.
	 *
	 * @param entregadoPorUsuarioId profesor que entrega el dinero (cobrado_por de todos los cobros)
	 * @param cobroIds              cobros que entrega (ACTIVOS, EFECTIVO, origen PROFESOR, de esa sede y sin entrega)
	 * @param importeContado        lo que cuenta quien recibe (&gt;= 0, dos decimales)
	 * @param nota                  obligatoria si lo contado difiere de lo declarado
	 */
	public record RegistrarEntrega(Long entregadoPorUsuarioId, List<Long> cobroIds, BigDecimal importeContado,
			String nota) {
	}

	/**
	 * Apuntar un movimiento de caja: {@code POST /api/caja/sedes/{sedeId}/movimientos}. Reglas de signo y alumno por
	 * tipo en {@link TipoMovimientoCaja}; nota obligatoria.
	 */
	public record CrearMovimiento(TipoMovimientoCaja tipo, BigDecimal importe, Long alumnoId, String nota) {
	}

	/** Anular una entrega o un movimiento (solo el propietario); motivo obligatorio. */
	public record Anulacion(String motivo) {
	}
}
