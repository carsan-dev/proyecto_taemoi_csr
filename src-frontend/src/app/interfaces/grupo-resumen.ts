/** Turno dentro de un grupo del resumen (`GET /api/grupos/resumen`): solo día y horas. */
export interface TurnoDeGrupoResumen {
  id: number;
  diaSemana: string;
  horaInicio: string;
  horaFin: string;
}

/**
 * Grupo en versión ligera (`GET /api/grupos/resumen`): sin los alumnos, con su número y sus turnos.
 * Para listados y selectores; el detalle con alumnos sigue siendo `GrupoDTO` (`GET /api/grupos/{id}`).
 */
export interface GrupoResumen {
  id: number;
  nombre: string;
  deporte?: string;
  rangoEdadMin?: number | null;
  rangoEdadMax?: number | null;
  numAlumnos: number;
  turnos: TurnoDeGrupoResumen[];
}
