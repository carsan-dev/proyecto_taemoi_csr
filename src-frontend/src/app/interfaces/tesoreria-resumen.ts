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
}
