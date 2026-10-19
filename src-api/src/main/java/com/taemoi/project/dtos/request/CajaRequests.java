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

	/**
	 * Pieza 6b-2c: por qué el profesor deshace su cobro ({@code POST /api/caja/profesor/cobros/{cobroId}/deshacer}).
	 * <ul>
	 * <li>{@link #AUN_NO_PAGADO}: el alumno aún no ha pagado; el cargo vuelve a pendiente (el comportamiento de la 6b-2,
	 * también sin cuerpo).</li>
	 * <li>{@link #NO_HUBO_VENTA}: solo para un cargo nacido de SU venta del catálogo; anula el cobro y cancela el cargo
	 * (motivo «no hubo venta») en la misma transacción.</li>
	 * </ul>
	 */
	public enum MotivoDeshacer {
		AUN_NO_PAGADO, NO_HUBO_VENTA
	}

	/** Cuerpo opcional del deshacer del profesor; sin cuerpo o sin motivo, {@link MotivoDeshacer#AUN_NO_PAGADO}. */
	public record Deshacer(MotivoDeshacer motivo) {
	}
}
