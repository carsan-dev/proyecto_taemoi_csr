/**
 * Caja de sede (pieza 6b-3). Espejo de CajaDTOs.java y CajaRequests.java de la API (`/api/caja`). Los importes
 * (BigDecimal con dos decimales) llegan como número; las fechas, como texto ISO (LocalDateTime sin zona, Date con zona).
 */

export type TipoMovimientoCaja = 'DEVOLUCION' | 'ERROR' | 'SOBRANTE' | 'FALTANTE' | 'OTRO';
export type EstadoEntregaCaja = 'ACTIVA' | 'ANULADA';
export type EstadoMovimientoCaja = 'ACTIVO' | 'ANULADO';

/** Sede cuya caja puede gestionar la sesión (gestión: todas; responsable: las suyas). */
export interface SedeCaja {
  id: number;
  nombre: string;
}

/** CajaDTOs.CobroCaja */
export interface CobroCaja {
  cobroId: number;
  cargoId: number | null;
  alumnoId: number | null;
  alumnoNombre: string | null;
  alumnoApellidos: string | null;
  concepto: string;
  importe: number;
  fechaCobro: string | null;
  sedeId: number | null;
  /** Venta del catálogo hecha por quien lo cobró (6b-3b-1): solo así se puede deshacer con NO_HUBO_VENTA. */
  venta?: boolean;
}

/** CajaDTOs.ResumenCajaSede */
export interface ResumenCajaSede {
  sedeId: number;
  desde: string;
  hasta: string;
  pendienteDeEntregar: number;
  recibidoEnPeriodo: number;
  diferenciasEnPeriodo: number;
  saldoCaja: number;
}

/** CajaDTOs.PendienteCobrador */
export interface PendienteCobrador {
  usuarioId: number;
  nombre: string;
  numeroCobros: number;
  importe: number;
  cobroMasAntiguo: string | null;
  cobros: CobroCaja[];
}

/** CajaDTOs.EntregaCaja */
export interface EntregaCaja {
  id: number;
  sedeId: number;
  entregadoPorUsuarioId: number | null;
  entregadoPorNombre: string | null;
  recibidoPorUsuarioId: number | null;
  recibidoPorNombre: string | null;
  importeDeclarado: number;
  importeContado: number;
  diferencia: number;
  fecha: string;
  nota: string | null;
  estado: EstadoEntregaCaja;
  cobroIds: number[];
  movimientoId: number | null;
  anuladaPorUsuarioId: number | null;
  anuladaEn: string | null;
  motivoAnulacion: string | null;
  autoentrega: boolean;
  /**
   * 6c-6: los demás movimientos de la entrega enlazados a un cobro (el ERROR +importe de un cobro deshecho ya entregado,
   * decisión 193, y la DEVOLUCION de la devolución del profesor, D6c-10), para que lo declarado cuadre a la vista. La API
   * solo lo manda si hay alguno; el servicio lo deja en `[]`.
   */
  movimientosDeCobro?: MovimientoDeEntrega[];
}

/** CajaDTOs.MovimientoDeEntrega (importe con signo). */
export interface MovimientoDeEntrega {
  id: number;
  tipo: TipoMovimientoCaja;
  importe: number;
  cobroId: number | null;
  estado: EstadoMovimientoCaja;
}

/** CajaDTOs.MovimientoCaja (importe con signo: positivo entra en caja, negativo sale). */
export interface MovimientoCaja {
  id: number;
  sedeId: number;
  tipo: TipoMovimientoCaja;
  importe: number;
  alumnoId: number | null;
  entregaCajaId: number | null;
  nota: string | null;
  creadoPorUsuarioId: number | null;
  fecha: string;
  estado: EstadoMovimientoCaja;
  anuladoPorUsuarioId: number | null;
  anuladoEn: string | null;
  motivoAnulacion: string | null;
}

/** Página de Spring Data (`number` empieza en 0; la API recibe `page` desde 1). */
export interface PaginaCaja<T> {
  content: T[];
  totalPages: number;
  totalElements: number;
  number: number;
  size: number;
}

/** CajaRequests.RegistrarEntrega */
export interface RegistrarEntregaCaja {
  entregadoPorUsuarioId: number;
  cobroIds: number[];
  importeContado: number;
  nota: string | null;
}

/** CajaRequests.CrearMovimiento */
export interface CrearMovimientoCaja {
  tipo: TipoMovimientoCaja;
  importe: number;
  alumnoId: number | null;
  nota: string;
}

// ------------------------------------------------------------------ Mi caja (profesor, pieza 6b-3b)

/** Por qué se deshace un cobro del profesor: sigue debiéndose, o la venta no llegó a hacerse (cancela el cargo). */
export type MotivoDeshacerCobro = 'AUN_NO_PAGADO' | 'NO_HUBO_VENTA';

/** CajaDTOs.CargoPendienteProfesor: un cargo pendiente de un alumno de los grupos del profesor. */
export interface CargoPendienteProfesor {
  cargoId: number;
  alumnoId: number;
  alumnoNombre: string | null;
  alumnoApellidos: string | null;
  concepto: string;
  importe: number;
  fechaAsignacion: string | null;
  sedeId: number | null;
  sedeNombre: string | null;
}

/** CajaDTOs.ProductoVendible: producto del catálogo que el profesor puede vender (precio fijo). */
export interface ProductoVendible {
  id: number;
  concepto: string;
  precio: number;
}

/** CajaDTOs.MisCobrosPendientes: lo que el profesor lleva encima sin entregar. */
export interface MisCobrosPendientes {
  numeroCobros: number;
  importe: number;
  cobros: CobroCaja[];
}

/** CajaDTOs.AlumnoDeMisGrupos: alumno activo de los grupos del profesor (sin datos sensibles). */
export interface AlumnoDeMisGrupos {
  alumnoId: number;
  nombre: string | null;
  apellidos: string | null;
  grupos: string[];
  /** 6c-8 (D6c-9): sedes distintas de sus grupos en los que está el alumno; no viene si no hay ninguna. */
  sedes?: SedeMiCaja[];
}

/** CajaDTOs.SedeDeGrupo: una sede de los grupos del profesor en los que está el alumno. */
export interface SedeMiCaja {
  id: number;
  nombre: string;
}

/**
 * CajaDTOs.CobroEntregadoReciente (6c-4, D6c-14): cobro suyo ya entregado a la sede en los últimos 30 días.
 * `solicitudPendienteId`: su solicitud de deshacer PENDIENTE; `noSePuedePedir`: por qué ya no se puede pedir.
 */
export interface CobroEntregadoReciente {
  cobroId: number;
  cargoId: number | null;
  alumnoId: number | null;
  alumnoNombre: string | null;
  alumnoApellidos: string | null;
  concepto: string;
  importe: number;
  fechaCobro: string | null;
  entregaId: number;
  fechaEntrega: string | null;
  sedeId: number | null;
  sedeNombre: string | null;
  venta: boolean;
  solicitudPendienteId: number | null;
  noSePuedePedir: string | null;
}

/** SolicitudRequests.PedirEncargo: `sedeId` solo si el alumno está con él en grupos de varias sedes (D6c-9). */
export interface PedirEncargo {
  productoId: number;
  nota: string;
  sedeId?: number;
}

/** SolicitudRequests.PedirDeshacer (decisión 193). */
export interface PedirDeshacer {
  motivo: string;
  motivoDeshacer: MotivoDeshacerCobro;
}
