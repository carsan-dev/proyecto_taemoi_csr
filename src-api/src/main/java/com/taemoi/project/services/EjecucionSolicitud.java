package com.taemoi.project.services;

import com.taemoi.project.entities.ProductoAlumno;
import com.taemoi.project.entities.TipoMovimientoCaja;

/**
 * Pieza 6c-3: lo que deja la ejecución de una solicitud aceptada (fase3-6c-solicitudes.md §6.5). O bien
 * {@code noAplicable} con la razón (la revalidación falló: no se ha tocado nada y la solicitud se cierra
 * {@code NO_APLICABLE} con esa respuesta), o bien hecha; si cambió el pago de un cargo (tipos 1 y 2), el cargo y si
 * estaba pagado antes, para los efectos que van después de confirmar, como en A1
 * ({@link ProductoAlumnoService#aplicarEfectosTrasSolicitud}).
 *
 * <p>Las razones son textos fijos (van a la {@code respuesta} que ve quien pidió y al aviso del propietario).
 */
public record EjecucionSolicitud(String noAplicable, ProductoAlumno cargo, boolean pagadoAntes) {

	public static final String CARGO_NO_EXISTE = "El cargo ya no existe.";
	public static final String CARGO_NO_PAGADO = "El cargo ya no está pagado.";
	public static final String CARGO_CON_COBRO = "El cargo tiene ahora un cobro activo: pide revertir ese cobro.";
	public static final String COBRO_NO_ACTIVO = "El cobro ya no está activo.";
	public static final String COBRO_SIN_CARGO = "El cobro ya no está unido a su cargo.";
	public static final String COBRO_ENTREGADO = "El cobro ya va en una entrega de caja.";
	public static final String CON_DEVOLUCION = "El cobro tiene una devolución registrada.";
	public static final String DEVOLUCION_PENDIENTE = "El encargo tiene una devolución pendiente: regístrala antes.";
	public static final String COBRO_NO_ENTREGADO = "El cobro ya no va en una entrega: el profesor puede deshacerlo "
			+ "él mismo.";
	public static final String COBRO_OTRA_ENTREGA = "El cobro ha pasado a otra entrega mientras se aceptaba: vuelve a "
			+ "intentarlo.";
	public static final String NO_ES_SUYO = "El cobro no es del profesor que lo pidió.";
	public static final String NO_ES_VENTA = "No fue una venta suya: no se deshace como «no hubo venta».";
	public static final String ENTREGA_NO_EXISTE = "La entrega ya no existe.";
	public static final String ENTREGA_ANULADA = "La entrega ya está anulada.";
	public static final String MOVIMIENTO_NO_EXISTE = "El movimiento ya no existe.";
	public static final String MOVIMIENTO_ANULADO = "El movimiento ya está anulado.";
	public static final String MOVIMIENTO_AUTOMATICO = "Es la diferencia automática de una entrega: pide anular la "
			+ "entrega.";
	/** El ERROR +importe de un cobro deshecho ya entregado (193): va con su entrega, no es su diferencia. */
	public static final String MOVIMIENTO_ERROR_DE_ENTREGA = "Es el ERROR de un cobro deshecho en una entrega: se "
			+ "anula con la entrega; pide anular la entrega.";

	/** Por qué un movimiento que va en una entrega no se anula suelto: su diferencia automática o el ERROR de la 193. */
	public static String razonMovimientoDeEntrega(TipoMovimientoCaja tipo) {
		return tipo == TipoMovimientoCaja.ERROR ? MOVIMIENTO_ERROR_DE_ENTREGA : MOVIMIENTO_AUTOMATICO;
	}
	/** Respuesta si la ejecución falla sin mensaje (la red de {@code SolicitudService#aceptar}). */
	public static final String YA_NO_APLICA = "La acción ya no se puede hacer.";

	/** La revalidación falló: no se toca nada. */
	public static EjecucionSolicitud noAplica(String razon) {
		return new EjecucionSolicitud(razon, null, false);
	}

	/** Hecha, sin efectos posteriores (tipo 3). */
	public static EjecucionSolicitud hecha() {
		return new EjecucionSolicitud(null, null, false);
	}

	/** Hecha, cambiando el pago del cargo: sus efectos van después de confirmar. */
	public static EjecucionSolicitud hecha(ProductoAlumno cargo, boolean pagadoAntes) {
		return new EjecucionSolicitud(null, cargo, pagadoAntes);
	}

	public boolean aplica() {
		return noAplicable == null;
	}
}
