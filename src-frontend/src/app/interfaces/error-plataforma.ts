/** Error técnico de la API visto por el administrador de plataforma. Sin datos personales ni contenido de la petición. */
export interface ErrorPlataforma {
  id: number;
  fechaEvento: string | Date;
  organizacionId: number | null;
  /** Nombre de la escuela; null si el evento no tiene organización. */
  organizacionNombre: string | null;
  metodoHttp: string;
  endpoint: string;
  modulo: string | null;
  estadoHttp: number;
  /** null en peticiones sin sesión. */
  usuarioId: number | null;
  resumen: string | null;
}

/** Escuela para el filtro de la pantalla de errores (GET /api/plataforma/escuelas). */
export interface EscuelaPlataforma {
  id: number;
  nombre: string;
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
