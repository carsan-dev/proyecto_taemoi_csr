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
