export interface AuditoriaEvento {
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
  resumen: string | null;
  payloadTruncado: boolean;
  ruidoEscaner?: boolean | null;
}
