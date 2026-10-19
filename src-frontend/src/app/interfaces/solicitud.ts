/** Solicitudes al propietario y encargos (fase3-6c-solicitudes.md §6.1, API `/api/solicitudes`). */

export type TipoSolicitud =
  | 'REVERTIR_COBRO'
  | 'DESHACER_COBRO_ENTREGADO'
  | 'ANULAR_ENTREGA'
  | 'ANULAR_MOVIMIENTO'
  | 'ENCARGO';

export type EstadoSolicitud =
  | 'PENDIENTE'
  | 'ACEPTADA'
  | 'DENEGADA'
  | 'RETIRADA'
  | 'NO_APLICABLE'
  | 'PEDIDO'
  | 'ENCARGADO'
  | 'RECIBIDO'
  | 'ENTREGADO'
  | 'RECHAZADO';

export type SegmentoSolicitudes = 'PENDIENTES' | 'ENCARGOS' | 'RESUELTAS';

/** Una fila de la bandeja. Fechas ya normalizadas a ISO (o `null`); importe a número (o `null`). */
export interface Solicitud {
  id: number;
  tipo: TipoSolicitud;
  estado: EstadoSolicitud;
  sedeId: number | null;
  sedeNombre: string | null;
  solicitadoPorUsuarioId: number | null;
  solicitadoPorNombre: string | null;
  solicitadoEn: string | null;
  motivo: string;
  resueltoPorUsuarioId: number | null;
  resueltoPorNombre: string | null;
  resueltoEn: string | null;
  respuesta: string | null;
  cobroId: number | null;
  entregaCajaId: number | null;
  movimientoCajaId: number | null;
  productoAlumnoId: number | null;
  productoId: number | null;
  alumnoId: number | null;
  alumnoNombre: string | null;
  alumnoApellidos: string | null;
  concepto: string | null;
  importe: number | null;
  motivoDeshacer: 'AUN_NO_PAGADO' | 'NO_HUBO_VENTA' | null;
  encargadoEn: string | null;
  recibidoEn: string | null;
  entregadoEn: string | null;
  devolucionPendiente: boolean;
  /** La pidió la sesión: puede retirarla mientras esté pendiente. */
  propia: boolean;
}

/** `POST /api/solicitudes`: un solo recurso, el de su tipo. */
export interface CrearSolicitud {
  tipo: Extract<TipoSolicitud, 'REVERTIR_COBRO' | 'ANULAR_ENTREGA' | 'ANULAR_MOVIMIENTO'>;
  cobroId?: number;
  productoAlumnoId?: number;
  entregaId?: number;
  movimientoId?: number;
  motivo: string;
}
