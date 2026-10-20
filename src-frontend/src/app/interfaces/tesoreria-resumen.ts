export interface TesoreriaResumen {
  mes: number | null;
  ano: number | null;
  deporte: string;
  totalMovimientos: number;
  totalPagados: number;
  totalPendientes: number;
  importeTotal: number;
  importePagado: number;
  importePendiente: number;
  alumnosConPendientes: number;
  /** 6c-6 (D6c-12): lo devuelto de los cargos del filtro. Va aparte: el importe cobrado lo sigue incluyendo. */
  importeDevuelto?: number;
}
