/** Error técnico de la API visto por el administrador de plataforma. Sin datos personales ni contenido de la petición. */
export interface ErrorPlataforma {
  id: number;
  fechaEvento: string | Date;
  organizacionId: number | null;
  metodoHttp: string;
  endpoint: string;
  modulo: string | null;
  estadoHttp: number;
  /** null en peticiones sin sesión. */
  usuarioId: number | null;
  resumen: string | null;
}

export interface FiltrosErroresPlataforma {
  organizacionId?: number | null;
  /** '4xx' | '5xx'; vacío = ambos (y los anónimos). */
  estado?: '4xx' | '5xx' | null;
  /** yyyy-MM-dd */
  desde?: string | null;
  /** yyyy-MM-dd */
  hasta?: string | null;
  page?: number;
  size?: number;
}
