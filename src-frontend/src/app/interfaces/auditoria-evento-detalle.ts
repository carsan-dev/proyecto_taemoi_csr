export interface AuditoriaEventoDetalle {
  id: number;
  fechaEvento: string | Date;
  accion: 'READ' | 'CREATE' | 'UPDATE' | 'DELETE' | string;
  metodoHttp: string;
  endpoint: string;
  modulo: string | null;
  recursoId: number | null;
  estadoHttp: number;
  usuarioId: number | null;
  usuarioEmail: string | null;
  usuarioNombre: string | null;
  ipCliente: string | null;
  userAgent: string | null;
  queryParamsJson: string | null;
  payloadJson: string | null;
  payloadTruncado: boolean;
  resumen: string | null;
}
