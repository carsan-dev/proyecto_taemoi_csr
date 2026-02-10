export interface RetoDiarioEstado {
  racha: number;
  completadoHoy: boolean;
  fechaCompletado: string | null;
  nextResetAtEpochMs?: number | null;
}
