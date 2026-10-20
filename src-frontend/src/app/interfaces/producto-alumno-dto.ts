/** Método con el que se cobra un cargo (pieza 6b-1). Solo de entrada en el PUT; no sale en las respuestas. */
export type MetodoPago = 'EFECTIVO' | 'TRANSFERENCIA' | 'TARJETA' | 'BIZUM' | 'SIN_INDICAR';

export interface ProductoAlumnoDTO {
    id: number;
    productoId: number | null;
    alumnoId: number;
    concepto: string;
    fechaAsignacion: Date;
    cantidad: number;
    precio: number;
    pagado: boolean;
    fechaPago: Date | null;
    notas: string;
    motivoCambio?: string | null;
    /** Decisión 148: método del cobro al pasarlo a pagado; ausente = sin indicar. */
    metodoPago?: MetodoPago;
    /** Pieza 6b-2c/6b-3, solo de salida y solo si está cancelado. */
    /** LocalDateTime: llega como [año, mes, día, hora, …] (ver utils/fecha-api). */
    canceladoEn?: string | number[] | null;
    canceladoPorUsuarioId?: number | null;
    canceladoPorNombre?: string | null;
    motivoCancelacion?: string | null;
    /** Pieza 6c-9 (153), solo de salida: su cobro activo tiene una devolución enlazada. Sigue pagado. */
    devuelto?: boolean;
  }
